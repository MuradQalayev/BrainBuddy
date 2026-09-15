-- Myndora Plus, beta. One row per account that has a plan; no row means Free.
-- The app can read its own row and nothing else. It can never write the table directly:
-- joining goes through join_plus_beta(), and later real billing will write it server-side,
-- so a client that edits its own requests can't grant itself Plus.

begin;

create table if not exists public.user_plans (
    user_id    uuid primary key references auth.users (id) on delete cascade,
    plan       text not null default 'free',
    -- where the plan came from: 'beta' for the free beta join, later 'play' for Google Play billing
    source     text not null default 'beta',
    started_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint user_plans_plan_known check (plan in ('free', 'plus')),
    constraint user_plans_source_known check (source in ('beta', 'play', 'manual'))
);

comment on table public.user_plans iswhat what happened ??
    'Paid-plan entitlement per account. Absent row = free. Written only by server-side functions.';

alter table public.user_plans enable row level security;

drop policy if exists "user_plans_select_own" on public.user_plans;
create policy "user_plans_select_own"
    on public.user_plans
    for select
    to authenticated
    using (user_id = auth.uid());

-- no insert, update or delete policy on purpose, and no table privileges beyond select
revoke all on public.user_plans from anon, authenticated;
grant select on public.user_plans to authenticated;

-- the beta's only way in. free while the beta runs: it upgrades the caller and nobody else,
-- and never downgrades a plan that came from somewhere other than the beta
create or replace function public.join_plus_beta()
returns table (plan text, source text, started_at timestamptz)
language plpgsql
security definer
set search_path = public
as $$
declare
    caller uuid := auth.uid();
begin
    if caller is null then
        raise exception 'not authenticated' using errcode = '28000';
    end if;

    insert into public.user_plans as up (user_id, plan, source)
    values (caller, 'plus', 'beta')
    on conflict (user_id) do update
        set plan = 'plus',
            updated_at = now()
        where up.plan <> 'plus';

    return query
        select up.plan, up.source, up.started_at
        from public.user_plans up
        where up.user_id = caller;
end;
$$;

revoke all on function public.join_plus_beta() from public, anon;
grant execute on function public.join_plus_beta() to authenticated;

commit;
