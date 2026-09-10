-- Private, verified-phone contact discovery for Myndora Connect.
--
-- Supabase owns and manages auth.users. This migration deliberately never alters, updates, or
-- indexes that table. App-owned verification reservations and rate limits live in public; the
-- SECURITY DEFINER matcher only reads confirmed phone numbers from auth.users.

begin;

create extension if not exists pgcrypto with schema extensions;

create table if not exists public.phone_verification_reservations (
    user_id uuid primary key references auth.users (id) on delete cascade,
    phone_hash text not null unique
        check (phone_hash ~ '^[0-9a-f]{64}$'),
    reserved_at timestamptz not null default now(),
    expires_at timestamptz not null
);

alter table public.phone_verification_reservations enable row level security;
revoke all on table public.phone_verification_reservations from public, anon, authenticated;

-- Reserve a hash before asking Supabase Auth to send a phone-change OTP. The raw phone number is
-- never stored here. Expired reservations are disposable; the verified value remains owned by
-- Supabase Auth.
create or replace function public.prepare_phone_verification(p_phone_hash text)
returns void
language plpgsql
security definer
set search_path = ''
as $function$
declare
    caller_id uuid := auth.uid();
    normalized_hash text := lower(trim(p_phone_hash));
begin
    if caller_id is null then
        raise exception 'Authentication required' using errcode = '28000';
    end if;

    if normalized_hash !~ '^[0-9a-f]{64}$' then
        raise exception 'Invalid phone hash' using errcode = '22023';
    end if;

    if exists (
        select 1
        from auth.users as account
        where account.id <> caller_id
          and account.phone_confirmed_at is not null
          and account.phone is not null
          and encode(extensions.digest(account.phone, 'sha256'), 'hex') = normalized_hash
    ) then
        raise exception 'That phone number is already connected to another Myndora account.'
            using errcode = '23505';
    end if;

    delete from public.phone_verification_reservations
    where expires_at <= now();

    begin
        insert into public.phone_verification_reservations (
            user_id,
            phone_hash,
            reserved_at,
            expires_at
        ) values (
            caller_id,
            normalized_hash,
            now(),
            now() + interval '1 hour'
        )
        on conflict (user_id) do update
        set phone_hash = excluded.phone_hash,
            reserved_at = excluded.reserved_at,
            expires_at = excluded.expires_at;
    exception
        when unique_violation then
            raise exception 'That phone number is already being verified. Try again later.'
                using errcode = '23505';
    end;
end;
$function$;

revoke all on function public.prepare_phone_verification(text) from public, anon;
grant execute on function public.prepare_phone_verification(text) to authenticated;

-- Called after Auth has accepted the OTP. It validates the signed-in identity against the
-- confirmed Auth phone and then releases the short-lived reservation. auth.users is read-only.
create or replace function public.complete_phone_verification(p_phone_hash text)
returns void
language plpgsql
security definer
set search_path = ''
as $function$
declare
    caller_id uuid := auth.uid();
    normalized_hash text := lower(trim(p_phone_hash));
begin
    if caller_id is null then
        raise exception 'Authentication required' using errcode = '28000';
    end if;

    if normalized_hash !~ '^[0-9a-f]{64}$' then
        raise exception 'Invalid phone hash' using errcode = '22023';
    end if;

    if not exists (
        select 1
        from auth.users as account
        where account.id = caller_id
          and account.phone_confirmed_at is not null
          and account.phone is not null
          and encode(extensions.digest(account.phone, 'sha256'), 'hex') = normalized_hash
    ) then
        raise exception 'The phone number is not verified for this account'
            using errcode = '22023';
    end if;

    delete from public.phone_verification_reservations
    where user_id = caller_id
      and phone_hash = normalized_hash;
end;
$function$;

revoke all on function public.complete_phone_verification(text) from public, anon;
grant execute on function public.complete_phone_verification(text) to authenticated;

create table if not exists public.contact_discovery_rate_limits (
    user_id uuid primary key references auth.users (id) on delete cascade,
    window_started_at timestamptz not null default now(),
    request_count integer not null default 0 check (request_count >= 0)
);

alter table public.contact_discovery_rate_limits enable row level security;
revoke all on table public.contact_discovery_rate_limits from public, anon, authenticated;

-- Match only SHA-256 hashes supplied by the caller against confirmed Auth phone numbers. Contact
-- names and raw phone numbers remain on-device. Results intentionally contain no public username,
-- email address, phone number, or display name.
create or replace function public.match_myndora_contacts(p_phone_hashes text[])
returns table (
    user_id uuid,
    phone_hash text,
    avatar_url text
)
language plpgsql
security definer
set search_path = ''
as $function$
declare
    caller_id uuid := auth.uid();
    clean_hashes text[];
    current_count integer;
begin
    if caller_id is null then
        raise exception 'Authentication required' using errcode = '28000';
    end if;

    if not exists (
        select 1
        from auth.users as account
        where account.id = caller_id
          and account.phone is not null
          and account.phone_confirmed_at is not null
    ) then
        raise exception 'Verify your phone before matching contacts'
            using errcode = '28000';
    end if;

    if coalesce(cardinality(p_phone_hashes), 0) = 0 then
        return;
    end if;

    if cardinality(p_phone_hashes) > 2000 then
        raise exception 'A maximum of 2000 contacts can be checked at once'
            using errcode = '22023';
    end if;

    if exists (
        select 1
        from unnest(p_phone_hashes) as supplied(value)
        where lower(trim(supplied.value)) !~ '^[0-9a-f]{64}$'
    ) then
        raise exception 'Invalid phone hash' using errcode = '22023';
    end if;

    select array_agg(distinct lower(trim(supplied.value)))
    into clean_hashes
    from unnest(p_phone_hashes) as supplied(value);

    insert into public.contact_discovery_rate_limits (
        user_id,
        window_started_at,
        request_count
    ) values (
        caller_id,
        now(),
        1
    )
    on conflict (user_id) do update
    set window_started_at = case
            when public.contact_discovery_rate_limits.window_started_at <= now() - interval '15 minutes'
                then now()
            else public.contact_discovery_rate_limits.window_started_at
        end,
        request_count = case
            when public.contact_discovery_rate_limits.window_started_at <= now() - interval '15 minutes'
                then 1
            else public.contact_discovery_rate_limits.request_count + 1
        end
    returning request_count into current_count;

    if current_count > 12 then
        raise exception 'Too many contact discovery requests. Try again later.'
            using errcode = 'P0001';
    end if;

    return query
    select distinct on (matched.phone_hash)
        matched.id as user_id,
        matched.phone_hash,
        profile.avatar_url
    from (
        select
            account.id,
            encode(extensions.digest(account.phone, 'sha256'), 'hex') as phone_hash
        from auth.users as account
        where account.id <> caller_id
          and account.phone is not null
          and account.phone_confirmed_at is not null
    ) as matched
    left join public.profiles as profile on profile.id = matched.id
    where matched.phone_hash = any(clean_hashes)
    order by matched.phone_hash, matched.id;
end;
$function$;

revoke all on function public.match_myndora_contacts(text[]) from public, anon;
grant execute on function public.match_myndora_contacts(text[]) to authenticated;

commit;
