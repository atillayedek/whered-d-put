// Permanently deletes the calling user's account and everything it owns:
// storage photos, items, categories, profile and finally the auth user.
//
// The service-role key is only available here, server-side, as an Edge
// Function secret. It is never shipped in the Android app.
import { createClient } from "npm:@supabase/supabase-js@2";

const BUCKET = "item-photos";
const PAGE = 1000;

function json(status: number, body: Record<string, unknown>): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return json(405, { error: "method_not_allowed" });

  const authHeader = req.headers.get("Authorization") ?? "";
  const token = authHeader.startsWith("Bearer ") ? authHeader.slice(7) : "";
  if (!token) return json(401, { error: "unauthorized" });

  const url = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !serviceKey) return json(500, { error: "server_misconfigured" });

  const admin = createClient(url, serviceKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });

  // Identify the caller from their own JWT; never trust a user id in the body.
  const { data: userData, error: userError } = await admin.auth.getUser(token);
  if (userError || !userData?.user) return json(401, { error: "unauthorized" });
  const userId = userData.user.id;

  try {
    await deleteUserPhotos(admin, userId);

    const items = await admin.from("items").delete().eq("user_id", userId);
    if (items.error) throw items.error;
    const categories = await admin.from("categories").delete().eq("user_id", userId);
    if (categories.error) throw categories.error;
    const profile = await admin.from("profiles").delete().eq("id", userId);
    if (profile.error) throw profile.error;

    const { error: deleteError } = await admin.auth.admin.deleteUser(userId);
    if (deleteError) throw deleteError;
  } catch (_error) {
    // Details are intentionally not returned or logged: they may contain personal data.
    return json(500, { error: "delete_failed" });
  }

  return json(200, { deleted: true });
});

// deno-lint-ignore no-explicit-any
async function deleteUserPhotos(admin: any, userId: string): Promise<void> {
  const bucket = admin.storage.from(BUCKET);
  const root = `users/${userId}/items`;
  const paths: string[] = [];

  for (let offset = 0; ; offset += PAGE) {
    const { data: folders, error } = await bucket.list(root, { limit: PAGE, offset });
    if (error) throw error;
    if (!folders || folders.length === 0) break;

    for (const folder of folders) {
      const prefix = `${root}/${folder.name}`;
      for (let fileOffset = 0; ; fileOffset += PAGE) {
        const { data: files, error: filesError } = await bucket.list(prefix, { limit: PAGE, offset: fileOffset });
        if (filesError) throw filesError;
        if (!files || files.length === 0) break;
        for (const file of files) paths.push(`${prefix}/${file.name}`);
        if (files.length < PAGE) break;
      }
    }
    if (folders.length < PAGE) break;
  }

  for (let i = 0; i < paths.length; i += 100) {
    const { error } = await bucket.remove(paths.slice(i, i + 100));
    if (error) throw error;
  }
}
