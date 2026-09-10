// ============================================================================
// send-focus-invite — the only thing allowed to push to someone else's phone
// ============================================================================
// Called by the app right after create_focus_session() succeeds. It looks up the
// invitees' device tokens and asks FCM to deliver.
//
// WHY THIS EXISTS AS A SERVER FUNCTION
// Sending a push needs the Firebase service account, and anything shipped in the
// APK is readable by anyone holding the APK. A key that can notify any device in
// the project is not a thing to hand out. It lives here as a secret instead.
//
// WHAT IT WILL NOT DO
// It does not take a recipient list from the caller. The caller names a session;
// this reads who is actually in it, from the database. Otherwise the endpoint is
// a way to push an arbitrary message to an arbitrary user id — which is exactly
// the abuse the RLS on focus_participants exists to prevent, undone by an API in
// front of it.
//
// DEPLOY
//   supabase secrets set FIREBASE_SERVICE_ACCOUNT="$(cat service-account.json)"
//   supabase functions deploy send-focus-invite
// The service account JSON comes from Firebase console → Project settings →
// Service accounts → Generate new private key.
// ============================================================================

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { create, getNumericDate } from "https://deno.land/x/djwt@v3.0.2/mod.ts";

const FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";

interface ServiceAccount {
  project_id: string;
  client_email: string;
  private_key: string;
}

/** Mints a short-lived Google access token from the service account. */
async function getAccessToken(sa: ServiceAccount): Promise<string> {
  // The PEM has to become a CryptoKey before djwt will sign with it.
  const pem = sa.private_key
    .replace(/-----BEGIN PRIVATE KEY-----/, "")
    .replace(/-----END PRIVATE KEY-----/, "")
    .replace(/\s/g, "");
  const der = Uint8Array.from(atob(pem), (c) => c.charCodeAt(0));
  const key = await crypto.subtle.importKey(
    "pkcs8",
    der,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );

  const assertion = await create(
    { alg: "RS256", typ: "JWT" },
    {
      iss: sa.client_email,
      scope: FCM_SCOPE,
      aud: "https://oauth2.googleapis.com/token",
      exp: getNumericDate(3600),
      iat: getNumericDate(0),
    },
    key,
  );

  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  if (!res.ok) throw new Error(`token exchange failed: ${await res.text()}`);
  return (await res.json()).access_token;
}

Deno.serve(async (req) => {
  try {
    const authHeader = req.headers.get("Authorization");
    if (!authHeader) return new Response("unauthorized", { status: 401 });

    const { sessionId } = await req.json();
    if (!sessionId) return new Response("sessionId required", { status: 400 });

    // Two clients on purpose. `caller` runs as the user and answers "are you
    // actually in this session?" under RLS; `admin` then reads the tokens, which
    // no user is allowed to see. Using the admin client for both would make the
    // first check meaningless.
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

    const { data: session } = await admin
      .from("focus_sessions")
      .select("id, host_id, focus_minutes")
      .eq("id", sessionId)
      .single();
    if (!session || session.host_id !== user.id) {
      // Only the host announces their own session.
      return new Response("forbidden", { status: 403 });
    }

    const { data: hostProfile } = await admin
      .from("profiles").select("display_name, username").eq("id", user.id).single();
    const hostName = hostProfile?.display_name || hostProfile?.username || "Someone";

    // Recipients come from the database, never from the request body.
    const { data: participants } = await admin
      .from("focus_participants")
      .select("user_id")
      .eq("session_id", sessionId)
      .eq("state", "INVITED")
      .neq("user_id", user.id);

    const userIds = (participants ?? []).map((p) => p.user_id);
    if (userIds.length === 0) return Response.json({ sent: 0 });

    const { data: tokens } = await admin
      .from("device_tokens").select("token").in("user_id", userIds);
    if (!tokens?.length) return Response.json({ sent: 0 });

    const sa: ServiceAccount = JSON.parse(
      Deno.env.get("FIREBASE_SERVICE_ACCOUNT")!,
    );
    const accessToken = await getAccessToken(sa);

    let sent = 0;
    const stale: string[] = [];
    for (const { token } of tokens) {
      const res = await fetch(
        `https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`,
        {
          method: "POST",
          headers: {
            Authorization: `Bearer ${accessToken}`,
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            message: {
              token,
              // Data-only: the app builds the notification itself so it matches
              // the in-app wording and can be cancelled when the invite is
              // answered. A `notification` payload would be drawn by the system
              // and left stranded in the tray after the fact.
              data: {
                type: "focus_invite",
                session_id: sessionId,
                host_name: hostName,
                minutes: String(session.focus_minutes),
              },
              android: { priority: "HIGH" },
            },
          }),
        },
      );
      if (res.ok) sent++;
      // 404/UNREGISTERED means the app was uninstalled or the token rotated.
      else if (res.status === 404) stale.push(token);
    }

    // Tokens rot constantly. Left in place they turn every future send into a
    // slow walk through dead devices.
    if (stale.length) {
      await admin.from("device_tokens").delete().in("token", stale);
    }

    return Response.json({ sent });
  } catch (e) {
    console.error("send-focus-invite failed", e);
    return new Response("internal error", { status: 500 });
  }
});
