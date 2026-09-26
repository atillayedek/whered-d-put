(function () {
  // Turns off announcement emails for the address the link was sent to.
  // Account emails (confirmation, password reset) are not affected.
  var SUPABASE_URL = "https://gxvhltdvtidqcoyafkyl.supabase.co";
  var SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imd4dmhsdGR2dGlkcWNveWFma3lsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTAzNjE3MTYsImV4cCI6MjEwNTkzNzcxNn0.3ZGDDoFa6pH5cGCY_PzaQ_f6pS_JvhYUWL66wyvpdlU";
  var tr = (navigator.language || "").toLowerCase().indexOf("tr") === 0;
  var t = tr ? {
    working: "Abonelikten çıkılıyor…",
    doneTitle: "Abonelikten çıktın",
    doneBody: "Artık duyuru e-postası almayacaksın. Hesabınla ilgili e-postalar (doğrulama, şifre sıfırlama) gelmeye devam eder.",
    badTitle: "Bu link geçersiz",
    badBody: "Link eksik ya da artık geçerli değil. Yardım için atilla12339@gmail.com adresine yaz.",
    errTitle: "Şu an yapılamadı",
    errBody: "Bağlantını kontrol edip bu sayfayı yenile."
  } : {
    working: "Unsubscribing…",
    doneTitle: "You're unsubscribed",
    doneBody: "You won't get announcement emails anymore. Emails about your account (confirmation, password reset) still arrive.",
    badTitle: "This link isn't valid",
    badBody: "The link is incomplete or no longer valid. For help, write to atilla12339@gmail.com.",
    errTitle: "Couldn't do that right now",
    errBody: "Check your connection and reload this page."
  };
  document.documentElement.lang = tr ? "tr" : "en";
  function show(title, body) {
    document.getElementById("title").textContent = title;
    document.getElementById("body").textContent = body;
  }
  var token = new URLSearchParams(location.search).get("t") || "";
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(token)) {
    show(t.badTitle, t.badBody);
    return;
  }
  show(t.working, "");
  fetch(SUPABASE_URL + "/rest/v1/rpc/unsubscribe", {
    method: "POST",
    headers: { "apikey": SUPABASE_ANON_KEY, "Authorization": "Bearer " + SUPABASE_ANON_KEY, "Content-Type": "application/json" },
    body: JSON.stringify({ token: token })
  }).then(function (r) { return r.ok ? r.json() : Promise.reject(r.status); })
    .then(function (ok) { if (ok === true) show(t.doneTitle, t.doneBody); else show(t.badTitle, t.badBody); })
    .catch(function () { show(t.errTitle, t.errBody); });
})();
