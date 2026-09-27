// Verifies a Premium subscription with the Google Play Developer API and
// stores the result in public.subscriptions. Called by the app (signed-in
// user) after a purchase and when the app comes to the foreground.
//
// Needs a Google Cloud service account with access to the app in Play
// Console, stored as the GOOGLE_PLAY_SERVICE_ACCOUNT function secret or in
// Supabase Vault as `google_play_service_account` (the JSON key file). Until
// then it answers { verified: false, reason: "not_configured" }.
import { createClient } from "npm:@supabase/supabase-js@2";
import { importPKCS8, SignJWT } from "npm:jose@5";

const PACKAGE = "com.wheredidiputit.app";
const PRODUCT = "wdipi_premium";

function json(status: number, body: Record<string, unknown>): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

type ServiceAccount = { client_email: string; private_key: string };

async function googleAccessToken(account: ServiceAccount): Promise<string> {
  const key = await importPKCS8(account.private_key, "RS256");
  const now = Math.floor(Date.now() / 1000);
  const assertion = await new SignJWT({ scope: "https://www.googleapis.com/auth/androidpublisher" })
    .setProtectedHeader({ alg: "RS256", typ: "JWT" })
    .setIssuer(account.client_email)
    .setAudience("https://oauth2.googleapis.com/token")
    .setIssuedAt(now)
    .setExpirationTime(now + 3600)
    .sign(key);
  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion }),
  });
  if (!response.ok) throw new Error("google_auth_failed");
  const body = await response.json();
  return body.access_token as string;
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return json(405, { error: "method_not_allowed" });

  const url = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !serviceKey) return json(500, { error: "server_misconfigured" });
  const admin = createClient(url, serviceKey, { auth: { persistSession: false, autoRefreshToken: false } });

  const auth = req.headers.get("Authorization") ?? "";
  const token = auth.startsWith("Bearer ") ? auth.slice(7) : "";
  const { data: userData } = token ? await admin.auth.getUser(token) : { data: null };
  const userId = userData?.user?.id;
  if (!userId) return json(401, { error: "unauthorized" });

  let input: { purchase_token?: string };
  try {
    input = await req.json();
  } catch {
    return json(400, { error: "bad_request" });
  }
  const purchaseToken = input.purchase_token ?? "";
  if (!purchaseToken || purchaseToken.length > 500) return json(400, { error: "bad_request" });

  let raw = Deno.env.get("GOOGLE_PLAY_SERVICE_ACCOUNT") ?? "";
  if (!raw) {
    const { data } = await admin.rpc("admin_play_service_account");
    raw = typeof data === "string" ? data : "";
  }
  if (!raw) return json(200, { verified: false, reason: "not_configured" });

  // A purchase belongs to the account that first verified it.
  const { data: existing } = await admin.from("subscriptions").select("user_id").eq("purchase_token", purchaseToken).maybeSingle();
  if (existing?.user_id && existing.user_id !== userId) return json(409, { error: "token_in_use" });

  let google: Record<string, unknown>;
  try {
    const accessToken = await googleAccessToken(JSON.parse(raw) as ServiceAccount);
    const response = await fetch(
      `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${PACKAGE}/purchases/subscriptionsv2/tokens/${encodeURIComponent(purchaseToken)}`,
      { headers: { Authorization: `Bearer ${accessToken}` } },
    );
    if (response.status === 404 || response.status === 410 || response.status === 400) {
      return json(200, { verified: false, reason: "invalid" });
    }
    if (!response.ok) return json(502, { error: "google_unavailable" });
    google = await response.json();
  } catch {
    return json(502, { error: "google_unavailable" });
  }

  const lineItems = (google.lineItems as Record<string, unknown>[] | undefined) ?? [];
  const item = lineItems.find((l) => l.productId === PRODUCT);
  if (!item) return json(200, { verified: false, reason: "wrong_product" });

  const state = String(google.subscriptionState ?? "SUBSCRIPTION_STATE_UNSPECIFIED");
  const expiresAt = typeof item.expiryTime === "string" ? item.expiryTime : null;
  const autoRenewing = Boolean((item.autoRenewingPlan as Record<string, unknown> | undefined)?.autoRenewEnabled);
  const latestOrderId = typeof item.latestSuccessfulOrderId === "string"
    ? item.latestSuccessfulOrderId
    : typeof google.latestOrderId === "string" ? google.latestOrderId : null;

  const { error } = await admin.from("subscriptions").upsert({
    purchase_token: purchaseToken,
    user_id: userId,
    product_id: PRODUCT,
    state,
    expires_at: expiresAt,
    auto_renewing: autoRenewing,
    test_purchase: google.testPurchase != null,
    latest_order_id: latestOrderId,
    verified_at: new Date().toISOString(),
  });
  if (error) return json(500, { error: "store_failed" });

  const active = expiresAt != null && Date.parse(expiresAt) > Date.now() &&
    ["SUBSCRIPTION_STATE_ACTIVE", "SUBSCRIPTION_STATE_IN_GRACE_PERIOD", "SUBSCRIPTION_STATE_CANCELED"].includes(state);
  return json(200, { verified: true, premium: active, expires_at: expiresAt });
});
