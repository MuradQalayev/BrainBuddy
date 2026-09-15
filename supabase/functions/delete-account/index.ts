// ============================================================================
// delete-account — removes the caller's own account, and nobody else's
// ============================================================================
// Called from Settings → Edit profile → Delete profile, after the user has typed
// the confirmation phrase.
//
// WHY THIS EXISTS AS A SERVER FUNCTION
// Deleting an auth user needs the service role key, which must never ship in the
// APK. The app only proves who it is with its own JWT; this function does the
// deleting.
//
// WHAT IT WILL NOT DO
// It takes no user id from the request. The account removed is the one the JWT
// belongs to, so the endpoint can't be pointed at someone else.
//
// WHAT GOES
// Profile photos are removed from storage first: storage objects don't cascade
// from auth.users. Then the auth user is deleted, and every table that references
// auth.users with ON DELETE CASCADE goes with it. A table that references it
// without a cascade makes the delete fail rather than leave half an account; the
// app shows the error and nothing is removed.
//
// DEPLOY
//   supabase functions deploy delete-account
// ============================================================================

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const AVATAR_BUCKET = "profile_photos";

Deno.serve(async (req) => {
  try {
    const authHeader = req.headers.get("Authorization");
    if (!authHeader) return new Response("unauthorized", { status: 401 });

    // Two clients on purpose, as in send-focus-invite: `caller` establishes who
    // is asking, `admin` does what no user is allowed to do for themselves.
    const caller = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_ANON_KEY")!,
      { global: { headers: { Authorization: authHeader } } },
    );
    const admin = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    const { data: { user } } = await caller.auth.getUser();
    if (!user) return new Response("unauthorized", { status: 401 });

    // Photos live under `<user id>/` in the bucket.
    const { data: files } = await admin.storage
      .from(AVATAR_BUCKET)
      .list(user.id, { limit: 1000 });
    if (files?.length) {
      await admin.storage
        .from(AVATAR_BUCKET)
        .remove(files.map((f) => `${user.id}/${f.name}`));
    }

    const { error } = await admin.auth.admin.deleteUser(user.id);
    if (error) {
      console.error("delete-account: deleteUser failed", error);
      return new Response("delete failed", { status: 500 });
    }

    return Response.json({ deleted: true });
  } catch (e) {
    console.error("delete-account failed", e);
    return new Response("internal error", { status: 500 });
  }
});
