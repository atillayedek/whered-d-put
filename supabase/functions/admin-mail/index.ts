// Sends announcement emails from the admin panel (website /admin) through
// Resend. Only accounts listed in public.admins may call it. The Resend key is
// read server-side (RESEND_API_KEY secret, or Supabase Vault) and never leaves
// this function.
import { createClient } from "npm:@supabase/supabase-js@2";

const FROM = "Nereye Koydum? <noreply@kapinda.site>";
const REPLY_TO = "atilla12339@gmail.com";
const SITE = "https://wheredidiputit-ochre.vercel.app";
const BATCH = 100;
const AUDIENCES = new Set(["all", "premium", "free"]);

const CORS = {
  "Access-Control-Allow-Origin": SITE,
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Vary": "Origin",
};

function json(status: number, body: Record<string, unknown>): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...CORS, "Content-Type": "application/json" },
  });
}

function escapeHtml(text: string): string {
  return text.replace(/[&<>"']/g, (c) =>
    ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]!
  );
}

/** Plain text in, simple readable HTML out: paragraphs, line breaks and links. */
function renderHtml(body: string, unsubscribeUrl: string | null): string {
  const paragraphs = escapeHtml(body.trim())
    .split(/\n{2,}/)
    .map((p) =>
      p.replace(/\n/g, "<br>").replace(
        /(https:\/\/[^\s<]+)/g,
        '<a href="$1" style="color:#0C5F6E">$1</a>',
      )
    )
    .map((p) => `<p style="margin:0 0 16px">${p}</p>`)
    .join("");
  const footer = unsubscribeUrl
    ? `<p style="margin:24px 0 0;font-size:13px;color:#6b6f68">Bu e-postayı Nereye Koydum? hesabın olduğu için aldın. Duyuru almak istemiyorsan <a href="${unsubscribeUrl}" style="color:#6b6f68">abonelikten çık</a>.<br>You received this because you have a Where Did I Put It? account. <a href="${unsubscribeUrl}" style="color:#6b6f68">Unsubscribe</a>.</p>`
    : `<p style="margin:24px 0 0;font-size:13px;color:#6b6f68">Test e-postası · Test email</p>`;
  return `<!doctype html><html><body style="margin:0;background:#F6F4EF;padding:24px 12px;font-family:-apple-system,Segoe UI,Roboto,sans-serif;color:#1E1F1C;font-size:16px;line-height:1.55"><div style="max-width:560px;margin:0 auto;background:#FEFDFA;border:1px solid #DAD7CE;border-radius:16px;padding:28px 24px">${paragraphs}${footer}</div></body></html>`;
}

function renderText(body: string, unsubscribeUrl: string | null): string {
  return unsubscribeUrl
    ? `${body.trim()}\n\n—\nAbonelikten çık / Unsubscribe: ${unsubscribeUrl}`
    : `${body.trim()}\n\n— Test`;
}

type Mail = { to: string; subject: string; body: string; unsubscribeUrl: string | null };

async function sendBatch(apiKey: string, mails: Mail[]): Promise<boolean> {
  const payload = mails.map((m) => ({
    from: FROM,
    to: [m.to],
    reply_to: REPLY_TO,
    subject: m.subject,
    html: renderHtml(m.body, m.unsubscribeUrl),
    text: renderText(m.body, m.unsubscribeUrl),
    headers: m.unsubscribeUrl
      ? { "List-Unsubscribe": `<${m.unsubscribeUrl}>` }
      : undefined,
  }));
  const response = await fetch("https://api.resend.com/emails/batch", {
    method: "POST",
    headers: { Authorization: `Bearer ${apiKey}`, "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  return response.ok;
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });
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

  const { data: userData, error: userError } = await admin.auth.getUser(token);
  if (userError || !userData?.user) return json(401, { error: "unauthorized" });
  const user = userData.user;

  const { data: adminRow } = await admin.from("admins").select("user_id").eq("user_id", user.id).maybeSingle();
  if (!adminRow) return json(403, { error: "forbidden" });

  let input: { action?: string; subject?: string; body?: string; audience?: string };
  try {
    input = await req.json();
  } catch {
    return json(400, { error: "bad_request" });
  }
  const subject = (input.subject ?? "").trim();
  const body = (input.body ?? "").trim();
  const audience = input.audience ?? "all";
  if (!subject || subject.length > 200 || !body || body.length > 20000 || !AUDIENCES.has(audience)) {
    return json(400, { error: "bad_request" });
  }

  let apiKey = Deno.env.get("RESEND_API_KEY") ?? "";
  if (!apiKey) {
    const { data } = await admin.rpc("admin_resend_key");
    apiKey = typeof data === "string" ? data : "";
  }
  if (!apiKey) return json(500, { error: "email_not_configured" });

  if (input.action === "test") {
    if (!user.email) return json(400, { error: "no_admin_email" });
    const ok = await sendBatch(apiKey, [{ to: user.email, subject, body, unsubscribeUrl: null }]);
    return ok ? json(200, { sent: 1 }) : json(502, { error: "send_failed" });
  }

  if (input.action !== "send") return json(400, { error: "bad_request" });

  const { data: recipients, error: audienceError } = await admin.rpc("admin_audience", { p_audience: audience });
  if (audienceError) return json(500, { error: "audience_failed" });
  const list = (recipients ?? []) as { email: string; unsubscribe_token: string }[];

  const { data: campaign, error: campaignError } = await admin
    .from("email_campaigns")
    .insert({ subject, body, audience, recipients: list.length, created_by: user.id })
    .select("id")
    .single();
  if (campaignError || !campaign) return json(500, { error: "log_failed" });

  let sent = 0;
  let failed = 0;
  for (let i = 0; i < list.length; i += BATCH) {
    const chunk = list.slice(i, i + BATCH);
    const ok = await sendBatch(
      apiKey,
      chunk.map((r) => ({
        to: r.email,
        subject,
        body,
        unsubscribeUrl: `${SITE}/unsubscribe?t=${r.unsubscribe_token}`,
      })),
    );
    if (ok) sent += chunk.length;
    else failed += chunk.length;
    // Stay under Resend's request rate limit.
    if (i + BATCH < list.length) await new Promise((resolve) => setTimeout(resolve, 600));
  }

  await admin
    .from("email_campaigns")
    .update({
      sent,
      failed,
      status: failed > 0 && sent === 0 && list.length > 0 ? "failed" : "sent",
      finished_at: new Date().toISOString(),
    })
    .eq("id", campaign.id);

  return json(200, { recipients: list.length, sent, failed });
});
