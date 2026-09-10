-- Optional Instagram-style link sticker for dashboard-published stories.
-- The database and Android client both enforce web-only destinations.

begin;

alter table public.stories
    add column if not exists link_url text,
    add column if not exists link_label text;

do $block$
begin
    if not exists (
        select 1
        from pg_constraint
        where conrelid = 'public.stories'::regclass
          and conname = 'stories_link_url_http_only'
    ) then
        alter table public.stories
            add constraint stories_link_url_http_only
            check (
                link_url is null
                or (
                    length(link_url) between 1 and 2048
                    and link_url ~* '^https?://[^[:space:]/]+(/|$)'
                    and link_url !~ '^[^:]+://[^/]*@'
                )
            );
    end if;
end
$block$;

comment on column public.stories.link_url is
    'Optional http(s) destination opened by the story link sticker.';
comment on column public.stories.link_label is
    'Optional short label for the story link sticker; hostname is used when null.';

commit;
