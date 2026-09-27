// Account actions from the admin panel: delete a user and everything they
// own, or resend the confirmation email. (Password resets stay in the app:
// its sign-in flow needs the reset to start on the person's own phone.) Only
// callers for whom public.is_admin() is true (with two-step verification when
// the admin has it set up) may use it.
import { createClient, type SupabaseClient } from "npm:@supabase/supabase-js@2";

const SITE = "https://wheredidiputit-ochre.vercel.app";
const BUCKET = "item-photos";
const PAGE = 1000;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const CORS = {
  "Access-Control-Allow-Origin": SITE,
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Vary": "Origin",
};

function json(status: number, body: Record<string, unknown>): Response {
  return new Response(JSON.stringify(body), { status, headers: { ...CORS, "Content-Type": "application/json" } });
}

/** The caller's user id if they are an admin right now, otherwise null. */
async function adminId(req: Request, url: string, anonKey: string): Promise<string | null> {
  const auth = req.headers.get("Authorization") ?? "";
  if (!auth.startsWith("Bearer ")) return null;
  const asCaller = createClient(url, anonKey, {
    global: { headers: { Authorization: auth } },
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { data: isAdmin, error } = await asCaller.rpc("is_admin");
  if (error || isAdmin !== true) return null;
  const { data } = await asCaller.auth.getUser(auth.slice(7));
  return data?.user?.id ?? null;
}

async function deleteUserPhotos(admin: SupabaseClient, userId: string): Promise<void> {
  const bucket = admin.storage.from(BUCKET);
  const root = `users/${userId}/items`;
  const paths: string[] = [];
  for (let offset = 0; ; offset += PAGE) {
    const { data: entries, error } = await bucket.list(root, { limit: PAGE, offset });
    if (error) throw error;
    if (!entries || entries.length === 0) break;
    for (const entry of entries) {
      const prefix = `${root}/${entry.name}`;
      if (entry.id) {
        paths.push(prefix);
        continue;
      }
      for (let fileOffset = 0; ; fileOffset += PAGE) {
        const { data: files, error: filesError } = await bucket.list(prefix, { limit: PAGE, offset: fileOffset });
        if (filesError) throw filesError;
        if (!files || files.length === 0) break;
        for (const file of files) paths.push(`${prefix}/${file.name}`);
        if (files.length < PAGE) break;
      }
    }
    if (entries.length < PAGE) break;
  }
  for (let i = 0; i < paths.length; i += 100) {
    const { error } = await bucket.remove(paths.slice(i, i + 100));
    if (error) throw error;
  }
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });
  if (req.method !== "POST") return json(405, { error: "method_not_allowed" });

  const url = Deno.env.get("SUPABASE_URL");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !anonKey || !serviceKey) return json(500, { error: "server_misconfigured" });

  const callerId = await adminId(req, url, anonKey);
  if (!callerId) return json(403, { error: "forbidden" });

  let input: { action?: string; user_id?: string };
  try {
    input = await req.json();
  } catch {
    return json(400, { error: "bad_request" });
  }
  const userId = input.user_id ?? "";
  if (!UUID.test(userId)) return json(400, { error: "bad_request" });

  const admin = createClient(url, serviceKey, { auth: { persistSession: false, autoRefreshToken: false } });
  const { data: target, error: targetError } = await admin.auth.admin.getUserById(userId);
  if (targetError || !target?.user) return json(404, { error: "not_found" });
  const email = target.user.email ?? "";

  if (input.action === "delete_user") {
    const { data: isTargetAdmin } = await admin.from("admins").select("user_id").eq("user_id", userId).maybeSingle();
    if (isTargetAdmin || userId === callerId) return json(409, { error: "cannot_delete_admin" });
    try {
      await deleteUserPhotos(admin, userId);
      for (const [table, column] of [["items", "user_id"], ["categories", "user_id"], ["profiles", "id"]]) {
        const { error } = await admin.from(table).delete().eq(column, userId);
        if (error) throw error;
      }
      const { error } = await admin.auth.admin.deleteUser(userId);
      if (error) throw error;
    } catch (_error) {
      // Details are not returned or logged: they may contain personal data.
      return json(500, { error: "delete_failed" });
    }
    return json(200, { deleted: true });
  }

  if (!email) return json(400, { error: "no_email" });

  if (input.action === "resend_confirmation") {
    if (target.user.email_confirmed_at) return json(409, { error: "already_confirmed" });
    const { error } = await admin.auth.resend({
      type: "signup",
      email,
      options: { emailRedirectTo: `${SITE}/auth/callback` },
    });
    return error ? json(502, { error: "send_failed" }) : json(200, { sent: true });
  }

  return json(400, { error: "bad_request" });
});
