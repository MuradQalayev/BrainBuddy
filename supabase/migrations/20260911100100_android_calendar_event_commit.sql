-- Ported from brainbuddy-backend (verified against PGlite smoke test, see that repo's
-- scripts/check-command-sql.mjs). This file is the single source of truth for this schema —
-- do not maintain a second copy in the backend repo. Requires 20260911100000 applied first.
--
-- Opt-in staging migration; not yet enabled in the assistant runtime.
-- Backend computes habit rows; this boundary atomically commits an event and those rows.
-- Call with the user's JWT. Never expose brainbuddy_private through PostgREST.
BEGIN;
CREATE TABLE IF NOT EXISTS brainbuddy_private.habit_initialization (
  user_id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  initialized_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE brainbuddy_private.habit_initialization ENABLE ROW LEVEL SECURITY;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='brainbuddy_private' AND tablename='habit_initialization' AND policyname='owner') THEN
    CREATE POLICY owner ON brainbuddy_private.habit_initialization TO authenticated
      USING(user_id=auth.uid()) WITH CHECK(user_id=auth.uid());
  END IF;
END $$;
REVOKE ALL ON brainbuddy_private.habit_initialization FROM PUBLIC,anon;
GRANT SELECT,INSERT ON brainbuddy_private.habit_initialization TO authenticated;
CREATE TABLE IF NOT EXISTS brainbuddy_private.event_receipts (
  user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  command_id uuid NOT NULL,
  input jsonb NOT NULL,
  result jsonb NOT NULL,
  PRIMARY KEY(user_id,command_id)
);
ALTER TABLE brainbuddy_private.event_receipts ENABLE ROW LEVEL SECURITY;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='brainbuddy_private' AND tablename='event_receipts' AND policyname='owner') THEN
    CREATE POLICY owner ON brainbuddy_private.event_receipts TO authenticated
      USING(user_id=auth.uid()) WITH CHECK(user_id=auth.uid());
  END IF;
END $$;
REVOKE ALL ON brainbuddy_private.event_receipts FROM PUBLIC,anon;
GRANT SELECT,INSERT ON brainbuddy_private.event_receipts TO authenticated;

CREATE OR REPLACE FUNCTION public.bb_commit_calendar_event(
  p_command_id uuid, p_event jsonb, p_expected_stats jsonb, p_next_stats jsonb
) RETURNS jsonb LANGUAGE plpgsql SECURITY INVOKER SET search_path='' AS $$
DECLARE actor uuid := auth.uid(); prior brainbuddy_private.event_receipts;
  actual jsonb; input jsonb; receipt jsonb; row jsonb;
BEGIN
  IF actor IS NULL THEN RAISE SQLSTATE 'PT404' USING MESSAGE='Actor required'; END IF;
  IF p_command_id IS NULL THEN RAISE SQLSTATE 'PT400' USING MESSAGE='Command required'; END IF;
  -- Serializes BE commits per actor, not legacy Android upserts (cutover required).
  PERFORM pg_advisory_xact_lock(hashtextextended(actor::text, 0));
  input := jsonb_build_object('event',p_event,'expected',p_expected_stats,'next',p_next_stats);
  SELECT * INTO prior FROM brainbuddy_private.event_receipts WHERE user_id=actor AND command_id=p_command_id;
  IF FOUND THEN
    IF prior.input IS DISTINCT FROM input THEN RAISE SQLSTATE 'PT409' USING MESSAGE='Command key reused'; END IF;
    RETURN prior.result;
  END IF;
  IF jsonb_typeof(p_event) IS DISTINCT FROM 'object'
    OR p_event - ARRAY['title','description','start_time','end_time','location','color','link'] <> '{}'::jsonb
    OR jsonb_typeof(p_event->'title') IS DISTINCT FROM 'string'
    OR length(btrim(p_event->>'title')) NOT BETWEEN 1 AND 200
    OR jsonb_typeof(p_event->'start_time') IS DISTINCT FROM 'string'
    OR jsonb_typeof(p_event->'end_time') IS DISTINCT FROM 'string'
    OR p_event->>'start_time' !~ '^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:00$'
    OR p_event->>'end_time' !~ '^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:00$'
    OR EXISTS(SELECT 1 FROM jsonb_each(p_event) e WHERE jsonb_typeof(e.value)<>'string' OR length(e.value#>>'{}')>2000)
    THEN RAISE SQLSTATE 'PT400' USING MESSAGE='Invalid event'; END IF;
  -- Validate actual calendar values, but do not invent next-day rollover.
  PERFORM (p_event->>'start_time')::timestamp, (p_event->>'end_time')::timestamp;
  IF jsonb_typeof(p_expected_stats) IS DISTINCT FROM 'array' OR jsonb_typeof(p_next_stats) IS DISTINCT FROM 'array'
    THEN RAISE SQLSTATE 'PT400' USING MESSAGE='Invalid statistics'; END IF;
  IF jsonb_array_length(p_next_stats)>6 OR jsonb_array_length(p_expected_stats)>6
    THEN RAISE SQLSTATE 'PT400' USING MESSAGE='Too many statistics'; END IF;
  -- Initialization is a separate prerequisite, never silently skipped for a new account.
  IF NOT EXISTS(SELECT 1 FROM brainbuddy_private.habit_initialization WHERE user_id=actor)
    THEN RAISE SQLSTATE 'PT409' USING MESSAGE='Habit initialization required'; END IF;
  IF EXISTS(SELECT 1 FROM jsonb_array_elements(p_next_stats) s
    WHERE s->>'id' IS DISTINCT FROM actor::text||'|'||(s->>'habit_key')||'|'||(s->>'day_type'))
    OR (SELECT count(DISTINCT s->>'id') FROM jsonb_array_elements(p_next_stats) s) <> jsonb_array_length(p_next_stats)
    THEN RAISE SQLSTATE 'PT400' USING MESSAGE='Invalid statistic identity'; END IF;
  PERFORM 1 FROM public.activity_time_stats WHERE user_id=actor
    AND id IN (SELECT s->>'id' FROM jsonb_array_elements(p_next_stats) s) ORDER BY id FOR UPDATE;
  SELECT coalesce(jsonb_agg(to_jsonb(s) ORDER BY s.id),'[]'::jsonb) INTO actual
    FROM public.activity_time_stats s WHERE user_id=actor
    AND id IN (SELECT n->>'id' FROM jsonb_array_elements(p_next_stats) n);
  IF actual IS DISTINCT FROM p_expected_stats THEN RAISE SQLSTATE 'PT409' USING MESSAGE='Habits changed'; END IF;
  -- CalendarEventMapper.toEntity/toDto assigns the owner as author for own events.
  INSERT INTO public.calendar_events(id,user_id,created_by,title,description,start_time,end_time,location,color,link)
    VALUES(p_command_id::text,actor,actor,btrim(p_event->>'title'),coalesce(p_event->>'description',''),
      p_event->>'start_time',p_event->>'end_time',coalesce(p_event->>'location',''),coalesce(p_event->>'color','blue'),coalesce(p_event->>'link',''))
    RETURNING to_jsonb(calendar_events) INTO receipt;
  FOR row IN SELECT value FROM jsonb_array_elements(p_next_stats) LOOP
    INSERT INTO public.activity_time_stats(id,user_id,habit_key,day_type,sin_sum,cos_sum,weight_sum,duration_weighted_sum,sample_count,last_observed_at)
    VALUES(row->>'id',actor,row->>'habit_key',row->>'day_type',(row->>'sin_sum')::float8,(row->>'cos_sum')::float8,
      (row->>'weight_sum')::float8,(row->>'duration_weighted_sum')::float8,(row->>'sample_count')::int,(row->>'last_observed_at')::bigint)
    ON CONFLICT(id) DO UPDATE SET sin_sum=excluded.sin_sum,cos_sum=excluded.cos_sum,weight_sum=excluded.weight_sum,
      duration_weighted_sum=excluded.duration_weighted_sum,sample_count=excluded.sample_count,last_observed_at=excluded.last_observed_at;
  END LOOP;
  INSERT INTO brainbuddy_private.event_receipts VALUES(actor,p_command_id,input,receipt);
  RETURN receipt;
END $$;
REVOKE ALL ON FUNCTION public.bb_commit_calendar_event(uuid,jsonb,jsonb,jsonb) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.bb_commit_calendar_event(uuid,jsonb,jsonb,jsonb) TO authenticated;
COMMIT;
