// ============================================================================
// notify-together-change — "sync now" for someone a connection just wrote to
// ============================================================================
// Called by the app right after it adds or removes an event or todo on another
// user's calendar or list. Without it the owner only sees the change the next
// time their app starts or reconnects.
//
// WHAT IT SENDS
// Nothing about the item. The push says "calendar changed" or "todo changed" and
// the owner's app pulls through RLS like any other sync, so no title or time ever
// travels through FCM.
//
// WHAT IT WILL NOT DO
// Push to arbitrary users. The recipient has to be one of the caller's
// connections, checked by calling list_my_connections() as the caller.
//
// DEPLOY
//   supabase functions deploy notify-together-change
// Reuses the FIREBASE_SERVICE_ACCOUNT secret already set for send-focus-invite.
// ============================================================================

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { create, getNumericDate } from "https://deno.land/x/djwt@v3.0.2/mod.ts";

const FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
const KINDS = new Set(["calendar", "todo"]);

interface ServiceAccount {
  project_id: string;
  client_email: string;
  private_key: string;
}

/** Mints a short-lived Google access token from the service account. */
async function getAccessToken(sa: ServiceAccount): Promise<string> {
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

    const { ownerId, kind } = await req.json();
    if (!ownerId || !KINDS.has(kind)) {
      return new Response("ownerId and kind required", { status: 400 });
    }

    // `caller` runs as the user, so the connection check happens under their own
    // RLS. `admin` only reads device tokens, which no user may see.
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
    if (ownerId === user.id) return Response.json({ sent: 0 });

    const { data: connections, error } = await caller.rpc("list_my_connections");
    if (error) throw error;
    const connected = (connections ?? []).some(
      (c: { user_id: string }) => c.user_id === ownerId,
    );
    if (!connected) return new Response("forbidden", { status: 403 });

    const { data: tokens } = await admin
      .from("device_tokens").select("token").eq("user_id", ownerId);
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
              data: { type: "together_changed", kind },
              // a burst of edits collapses into one sync on a phone that was offline
              android: { priority: "HIGH", collapse_key: `together_changed_${kind}` },
            },
          }),
        },
      );
      if (res.ok) sent++;
      else if (res.status === 404) stale.push(token);
    }

    if (stale.length) {
      await admin.from("device_tokens").delete().in("token", stale);
    }

    return Response.json({ sent });
  } catch (e) {
    console.error("notify-together-change failed", e);
    return new Response("internal error", { status: 500 });
  }
});
