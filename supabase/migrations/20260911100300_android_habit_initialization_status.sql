-- Ported from brainbuddy-backend (verified against PGlite smoke test, see that repo's
-- scripts/check-command-sql.mjs). This file is the single source of truth for this schema —
-- do not maintain a second copy in the backend repo.
--
-- Opt-in only, after 20260911100100/20260911100200. Fills a gap left by those migrations:
-- brainbuddy_private is never exposed through PostgREST, so callers need a SECURITY INVOKER
-- read to check initialization status before deciding whether to run habit backfill.
BEGIN;
CREATE OR REPLACE FUNCTION public.bb_habit_initialized()
RETURNS boolean LANGUAGE sql STABLE SECURITY INVOKER SET search_path='' AS $$
  SELECT EXISTS(SELECT 1 FROM brainbuddy_private.habit_initialization WHERE user_id = auth.uid())
$$;
REVOKE ALL ON FUNCTION public.bb_habit_initialized() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.bb_habit_initialized() TO authenticated;
NOTIFY pgrst, 'reload schema';
COMMIT;
