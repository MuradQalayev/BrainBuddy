-- Myndora Connect is an invite-only relationship feature, not a public directory.
--
-- The Android client no longer exposes username/name discovery or targeted requests, but
-- client-side removal is not a security boundary: an authenticated user could still call an old
-- PostgREST RPC manually. Revoke every overload of the legacy discovery/request functions here.
-- Existing connections, incoming requests, and private single-use invite links keep working.

begin;

do $block$
declare
    function_to_lock record;
begin
    for function_to_lock in
        select p.oid::regprocedure as signature
        from pg_proc as p
        join pg_namespace as n on n.oid = p.pronamespace
        where n.nspname = 'public'
          and p.proname in (
              'myndora_search_users',
              'search_profile_recipients',
              'send_connection_request'
          )
    loop
        execute format(
            'revoke execute on function %s from public, anon, authenticated',
            function_to_lock.signature
        );
    end loop;
end
$block$;

commit;
