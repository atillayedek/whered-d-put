// Emails the admins a short summary of the last 7 days. Called every Monday
// by a pg_cron job (with a shared secret kept in Supabase Vault), or on demand
// from the admin panel by a signed-in admin.
import { createClient } from "npm:@supabase/supabase-js@2";

const FROM = "Nereye Koydum? <noreply@kapinda.site>";
const SITE = "https://wheredidiputit-ochre.vercel.app";

const CORS = {
  "Access-Control-Allow-Origin": SITE,
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Vary": "Origin",
};

function json(status: number, body: Record<string, unknown>): Response {
  return new Response(JSON.stringify(body), { status, headers: { ...CORS, "Content-Type": "application/json" } });
}

function sameSecret(a: string, b: string): boolean {
  if (!a || !b || a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

type Summary = {
  users_total: number;
  users_new: number;
  users_new_prev: number;
  active: number;
  items_new: number;
  orders: number;
  premium_active: number;
  revenue: { currency: string; gross: number }[];
  recipients: string[];
};

const nf = new Intl.NumberFormat("tr-TR");

function money(amount: number, currency: string): string {
  try {
    return new Intl.NumberFormat("tr-TR", { style: "currency", currency }).format(amount);
  } catch {
    return `${nf.format(amount)} ${currency}`;
  }
}

function render(s: Summary): { html: string; text: string } {
  const diff = s.users_new - s.users_new_prev;
  const trend = diff === 0 ? "geçen haftayla aynı" : diff > 0 ? `geçen haftadan ${nf.format(diff)} fazla` : `geçen haftadan ${nf.format(-diff)} az`;
  const revenue = s.revenue.length ? s.revenue.map((r) => money(Number(r.gross), r.currency)).join(" · ") : money(0, "TRY");
  const rows: [string, string][] = [
    ["Yeni kullanıcı", `${nf.format(s.users_new)} (${trend})`],
    ["Toplam kullanıcı", nf.format(s.users_total)],
    ["Aktif kullanıcı", nf.format(s.active)],
    ["Yeni kayıt", nf.format(s.items_new)],
    ["Sipariş", nf.format(s.orders)],
    ["Brüt gelir", revenue],
    ["Premium (aktif)", nf.format(s.premium_active)],
  ];
  const html = `<!doctype html><html><body style="margin:0;background:#F3F5F7;padding:24px 12px;font-family:-apple-system,Segoe UI,Roboto,sans-serif;color:#14202B">
<div style="max-width:520px;margin:0 auto;background:#FFFFFF;border:1px solid #E1E6EA;border-radius:16px;padding:24px">
<p style="margin:0 0 4px;font-size:13px;color:#5A6772">Nereye Koydum? · haftalık özet</p>
<h1 style="margin:0 0 16px;font-size:20px">Son 7 gün</h1>
<table style="width:100%;border-collapse:collapse;font-size:15px">${
    rows.map(([k, v]) =>
      `<tr><td style="padding:9px 0;border-bottom:1px solid #EEF1F3;color:#5A6772">${k}</td><td style="padding:9px 0;border-bottom:1px solid #EEF1F3;text-align:right;font-weight:600">${v}</td></tr>`
    ).join("")
  }</table>
<p style="margin:20px 0 0"><a href="${SITE}/admin" style="color:#0C5F6E;font-weight:600">Paneli aç</a></p>
<p style="margin:16px 0 0;font-size:12px;color:#5A6772">Gelir, uygulamanın bildirdiği siparişlerden hesaplanır; kesin rakamlar Play Console'dadır.</p>
</div></body></html>`;
  const text = `Nereye Koydum? haftalık özet (son 7 gün)\n\n` + rows.map(([k, v]) => `${k}: ${v}`).join("\n") + `\n\nPanel: ${SITE}/admin`;
  return { html, text };
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });
  if (req.method !== "POST") return json(405, { error: "method_not_allowed" });

  const url = Deno.env.get("SUPABASE_URL");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !anonKey || !serviceKey) return json(500, { error: "server_misconfigured" });

  const admin = createClient(url, serviceKey, { auth: { persistSession: false, autoRefreshToken: false } });

  // Either the scheduled job (shared secret) or a signed-in admin.
  let allowed = false;
  const cronHeader = req.headers.get("x-cron-secret") ?? "";
  if (cronHeader) {
    const { data } = await admin.rpc("admin_cron_secret");
    allowed = sameSecret(cronHeader, typeof data === "string" ? data : "");
  } else {
    const auth = req.headers.get("Authorization") ?? "";
    if (auth.startsWith("Bearer ")) {
      const asCaller = createClient(url, anonKey, {
        global: { headers: { Authorization: auth } },
        auth: { persistSession: false, autoRefreshToken: false },
      });
      const { data } = await asCaller.rpc("is_admin");
      allowed = data === true;
    }
  }
  if (!allowed) return json(403, { error: "forbidden" });

  const { data: summary, error } = await admin.rpc("admin_weekly_summary");
  if (error || !summary) return json(500, { error: "summary_failed" });
  const s = summary as Summary;
  if (!s.recipients.length) return json(200, { sent: 0 });

  let apiKey = Deno.env.get("RESEND_API_KEY") ?? "";
  if (!apiKey) {
    const { data } = await admin.rpc("admin_resend_key");
    apiKey = typeof data === "string" ? data : "";
  }
  if (!apiKey) return json(500, { error: "email_not_configured" });

  const { html, text } = render(s);
  const response = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: { Authorization: `Bearer ${apiKey}`, "Content-Type": "application/json" },
    body: JSON.stringify({ from: FROM, to: s.recipients, subject: "Haftalık özet · Nereye Koydum?", html, text }),
  });
  return response.ok ? json(200, { sent: s.recipients.length }) : json(502, { error: "send_failed" });
});
