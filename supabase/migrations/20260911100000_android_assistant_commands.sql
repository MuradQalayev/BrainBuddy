-- Ported from brainbuddy-backend (verified against PGlite smoke test, see that repo's
-- scripts/check-command-sql.mjs). This file is the single source of truth for this schema —
-- do not maintain a second copy in the backend repo.
--
-- Android server-command slice. Apply ONLY after schema review, never automatically to a hosted DB.
-- Requires Android profiles(id,display_name,username,updated_at) and effective owner RLS.
-- This profile-only migration has no calendar dependency. Android calendar times are text.
-- SECURITY INVOKER deliberately preserves the caller's RLS on application tables.
BEGIN;
CREATE SCHEMA IF NOT EXISTS brainbuddy_private;
-- Never add this schema to the PostgREST exposed schemas.
REVOKE ALL ON SCHEMA brainbuddy_private FROM PUBLIC, anon;
GRANT USAGE ON SCHEMA brainbuddy_private TO authenticated;
CREATE TABLE IF NOT EXISTS brainbuddy_private.assistant_commands (
  user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  command_id uuid NOT NULL,
  request jsonb NOT NULL,
  operation text NOT NULL CHECK (operation = 'update_profile'),
  payload jsonb NOT NULL,
  expected_version timestamptz,
  state text NOT NULL DEFAULT 'prepared' CHECK (state IN ('prepared','executed')),
  expires_at timestamptz NOT NULL DEFAULT (now() + interval '10 minutes'),
  result jsonb,
  PRIMARY KEY(user_id,command_id)
);
ALTER TABLE brainbuddy_private.assistant_commands ENABLE ROW LEVEL SECURITY;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='brainbuddy_private' AND tablename='assistant_commands' AND policyname='owner_only') THEN
    CREATE POLICY owner_only ON brainbuddy_private.assistant_commands TO authenticated
      USING (user_id=auth.uid()) WITH CHECK (user_id=auth.uid());
  END IF;
END $$;
REVOKE ALL ON brainbuddy_private.assistant_commands FROM PUBLIC, anon;
GRANT SELECT, INSERT, UPDATE ON brainbuddy_private.assistant_commands TO authenticated;

CREATE OR REPLACE FUNCTION brainbuddy_private.command_json(c brainbuddy_private.assistant_commands)
RETURNS jsonb LANGUAGE sql STABLE SET search_path='' AS $$
  SELECT jsonb_build_object('commandId',c.command_id,'state',c.state,
    'command',jsonb_build_object('operation',c.operation,'payload',c.payload),'expiresAt',c.expires_at,'result',c.result)
$$;

CREATE OR REPLACE FUNCTION public.bb_find_command(p_command_id uuid,p_request jsonb)
RETURNS jsonb LANGUAGE plpgsql SECURITY INVOKER SET search_path='' AS $$
DECLARE c brainbuddy_private.assistant_commands;
BEGIN
  IF auth.uid() IS NULL THEN RAISE SQLSTATE 'PT404' USING MESSAGE='Command not found'; END IF;
  SELECT * INTO c FROM brainbuddy_private.assistant_commands WHERE user_id=auth.uid() AND command_id=p_command_id;
  IF NOT FOUND THEN RETURN NULL; END IF;
  IF c.request <> p_request THEN RAISE SQLSTATE 'PT409' USING MESSAGE='Command key reused'; END IF;
  RETURN brainbuddy_private.command_json(c);
END $$;

CREATE OR REPLACE FUNCTION public.bb_prepare_command(p_command_id uuid,p_request jsonb,p_operation text,p_payload jsonb)
RETURNS jsonb LANGUAGE plpgsql SECURITY INVOKER SET search_path='' SET timezone='UTC' AS $$
DECLARE c brainbuddy_private.assistant_commands; v timestamptz; existing_name text; prior jsonb;
BEGIN
  IF auth.uid() IS NULL THEN RAISE SQLSTATE 'PT404' USING MESSAGE='Actor required'; END IF;
  IF p_command_id IS NULL OR p_request IS NULL OR jsonb_typeof(p_request) <> 'object'
    OR p_request->>'turnId' IS DISTINCT FROM p_command_id::text
    OR jsonb_typeof(p_request->'message') IS DISTINCT FROM 'string'
    OR length(p_request->>'message') NOT BETWEEN 1 AND 8000
    OR jsonb_typeof(p_request->'timeZone') IS DISTINCT FROM 'string'
    OR NOT EXISTS(SELECT 1 FROM pg_timezone_names WHERE name=p_request->>'timeZone')
    OR p_request - ARRAY['turnId','message','timeZone','history'] <> '{}'::jsonb THEN
    RAISE SQLSTATE 'PT400' USING MESSAGE='Invalid request';
  END IF;
  IF p_request ? 'history' THEN
    IF jsonb_typeof(p_request->'history') IS DISTINCT FROM 'array' THEN RAISE SQLSTATE 'PT400' USING MESSAGE='Invalid history'; END IF;
    IF jsonb_array_length(p_request->'history') > 20 OR EXISTS(
      SELECT 1 FROM jsonb_array_elements(p_request->'history') AS h
      WHERE jsonb_typeof(h) <> 'object' OR h - ARRAY['role','text'] <> '{}'::jsonb
        OR h->>'role' IS NULL OR h->>'role' NOT IN ('USER','ASSISTANT')
        OR jsonb_typeof(h->'text') IS DISTINCT FROM 'string' OR length(h->>'text') > 4000
    ) THEN RAISE SQLSTATE 'PT400' USING MESSAGE='Invalid history'; END IF;
  END IF;
  prior := public.bb_find_command(p_command_id,p_request);
  IF prior IS NOT NULL THEN RETURN prior; END IF;
  IF p_payload IS NULL OR jsonb_typeof(p_payload) <> 'object' THEN RAISE SQLSTATE 'PT400' USING MESSAGE='Invalid payload'; END IF;
  IF p_operation='update_profile' THEN
    IF p_payload='{}'::jsonb OR p_payload - ARRAY['display_name','username'] <> '{}'::jsonb
      OR (p_payload ? 'display_name' AND (jsonb_typeof(p_payload->'display_name') IS DISTINCT FROM 'string' OR length(btrim(p_payload->>'display_name')) NOT BETWEEN 1 AND 200))
      OR (p_payload ? 'username' AND (jsonb_typeof(p_payload->'username') IS DISTINCT FROM 'string' OR p_payload->>'username' !~ '^[a-zA-Z0-9_]{3,20}$')) THEN
      RAISE SQLSTATE 'PT400' USING MESSAGE='Invalid profile change';
    END IF;
    SELECT updated_at,display_name INTO v,existing_name FROM public.profiles WHERE id=auth.uid();
    IF NOT FOUND OR v IS NULL THEN RAISE SQLSTATE 'PT404' USING MESSAGE='Profile not provisioned'; END IF;
    IF NOT p_payload ? 'display_name' AND length(btrim(coalesce(existing_name,'')))=0 THEN
      RAISE SQLSTATE 'PT400' USING MESSAGE='Display name required';
    END IF;
  ELSE RAISE SQLSTATE 'PT400' USING MESSAGE='Unsupported command'; END IF;
  INSERT INTO brainbuddy_private.assistant_commands(user_id,command_id,request,operation,payload,expected_version)
    VALUES(auth.uid(),p_command_id,p_request,p_operation,p_payload,v) ON CONFLICT DO NOTHING;
  RETURN public.bb_find_command(p_command_id,p_request);
END $$;

CREATE OR REPLACE FUNCTION public.bb_execute_command(p_command_id uuid)
RETURNS jsonb LANGUAGE plpgsql SECURITY INVOKER SET search_path='' SET timezone='UTC' AS $$
DECLARE c brainbuddy_private.assistant_commands; receipt jsonb;
BEGIN
  SELECT * INTO c FROM brainbuddy_private.assistant_commands
    WHERE user_id=auth.uid() AND command_id=p_command_id FOR UPDATE;
  IF NOT FOUND THEN RAISE SQLSTATE 'PT404' USING MESSAGE='Command not found'; END IF;
  IF c.state='executed' THEN RETURN brainbuddy_private.command_json(c); END IF;
  IF c.expires_at <= now() THEN RAISE SQLSTATE 'PT409' USING MESSAGE='Command expired'; END IF;
  IF c.operation='update_profile' THEN
    UPDATE public.profiles SET
      display_name=CASE WHEN c.payload ? 'display_name' THEN btrim(c.payload->>'display_name') ELSE display_name END,
      username=CASE WHEN c.payload ? 'username' THEN c.payload->>'username' ELSE username END
      WHERE id=auth.uid() AND updated_at=c.expected_version
      RETURNING jsonb_build_object('id',id,'display_name',display_name,'username',username,'updated_at',updated_at) INTO receipt;
    IF NOT FOUND THEN RAISE SQLSTATE 'PT409' USING MESSAGE='Profile changed or access denied'; END IF;
  ELSE RAISE SQLSTATE 'PT400' USING MESSAGE='Unsupported command'; END IF;
  UPDATE brainbuddy_private.assistant_commands SET state='executed',result=receipt
    WHERE user_id=auth.uid() AND command_id=p_command_id RETURNING * INTO c;
  RETURN brainbuddy_private.command_json(c);
END $$;
REVOKE ALL ON FUNCTION public.bb_find_command(uuid,jsonb), public.bb_prepare_command(uuid,jsonb,text,jsonb), public.bb_execute_command(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.bb_find_command(uuid,jsonb), public.bb_prepare_command(uuid,jsonb,text,jsonb), public.bb_execute_command(uuid) TO authenticated;
REVOKE ALL ON FUNCTION brainbuddy_private.command_json(brainbuddy_private.assistant_commands) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION brainbuddy_private.command_json(brainbuddy_private.assistant_commands) TO authenticated;
NOTIFY pgrst, 'reload schema';
COMMIT;
