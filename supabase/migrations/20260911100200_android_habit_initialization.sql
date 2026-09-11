-- Ported from brainbuddy-backend (verified against PGlite smoke test, see that repo's
-- scripts/check-command-sql.mjs). This file is the single source of truth for this schema —
-- do not maintain a second copy in the backend repo.
--
-- Opt-in only, after 20260911100000 and 20260911100100. Not a legacy Android writer
-- synchronization mechanism.
BEGIN;
CREATE OR REPLACE FUNCTION public.bb_initialize_habits(
  p_from_local text, p_expected_history jsonb, p_statistics jsonb
) RETURNS text LANGUAGE plpgsql SECURITY INVOKER SET search_path='' AS $$
DECLARE actor uuid:=auth.uid(); actual jsonb; row jsonb;
BEGIN
  IF actor IS NULL THEN RAISE SQLSTATE 'PT404' USING MESSAGE='Actor required'; END IF;
  PERFORM pg_advisory_xact_lock(hashtextextended(actor::text,0));
  IF EXISTS(SELECT 1 FROM brainbuddy_private.habit_initialization WHERE user_id=actor) THEN
    RETURN 'already-initialized';
  END IF;
  -- Android skips backfill when remote statistics already exist.
  IF EXISTS(SELECT 1 FROM public.activity_time_stats WHERE user_id=actor) THEN
    INSERT INTO brainbuddy_private.habit_initialization(user_id) VALUES(actor);
    RETURN 'existing-statistics';
  END IF;
  IF p_from_local IS NULL OR p_from_local !~ '^\d{4}-\d{2}-\d{2}T00:00$'
    OR jsonb_typeof(p_expected_history) IS DISTINCT FROM 'array'
    OR jsonb_typeof(p_statistics) IS DISTINCT FROM 'array' THEN
    RAISE SQLSTATE 'PT400' USING MESSAGE='Invalid initialization';
  END IF;
  PERFORM p_from_local::timestamp;
  IF jsonb_array_length(p_expected_history)>2000 OR jsonb_array_length(p_statistics)>12000 THEN
    RAISE SQLSTATE 'PT400' USING MESSAGE='Initialization too large';
  END IF;
  -- Exact snapshot of the bounded history. id is an explicit tie-break for equal start times.
  SELECT coalesce(jsonb_agg(to_jsonb(e) ORDER BY e.start_time,e.id),'[]'::jsonb) INTO actual FROM (
    SELECT id,title,description,start_time,end_time,completed FROM public.calendar_events
    WHERE user_id=actor AND start_time>=p_from_local ORDER BY start_time,id LIMIT 2000
  ) e;
  IF actual IS DISTINCT FROM p_expected_history THEN RAISE SQLSTATE 'PT409' USING MESSAGE='History changed'; END IF;
  FOR row IN SELECT value FROM jsonb_array_elements(p_statistics) LOOP
    IF row->>'id' IS DISTINCT FROM actor::text||'|'||(row->>'habit_key')||'|'||(row->>'day_type') THEN
      RAISE SQLSTATE 'PT400' USING MESSAGE='Invalid statistic identity';
    END IF;
    INSERT INTO public.activity_time_stats(id,user_id,habit_key,day_type,sin_sum,cos_sum,weight_sum,duration_weighted_sum,sample_count,last_observed_at)
    VALUES(row->>'id',actor,row->>'habit_key',row->>'day_type',(row->>'sin_sum')::float8,(row->>'cos_sum')::float8,
      (row->>'weight_sum')::float8,(row->>'duration_weighted_sum')::float8,(row->>'sample_count')::int,(row->>'last_observed_at')::bigint);
  END LOOP;
  INSERT INTO brainbuddy_private.habit_initialization(user_id) VALUES(actor);
  RETURN 'history';
END $$;
REVOKE ALL ON FUNCTION public.bb_initialize_habits(text,jsonb,jsonb) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.bb_initialize_habits(text,jsonb,jsonb) TO authenticated;
COMMIT;
