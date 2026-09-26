(function () {
  "use strict";

  // Client-safe values only (same project URL and anon key the app ships with).
  // Every admin query is authorised in the database by public.is_admin().
  var SUPABASE_URL = "https://gxvhltdvtidqcoyafkyl.supabase.co";
  var SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imd4dmhsdGR2dGlkcWNveWFma3lsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTAzNjE3MTYsImV4cCI6MjEwNTkzNzcxNn0.3ZGDDoFa6pH5cGCY_PzaQ_f6pS_JvhYUWL66wyvpdlU";
  var PAGE = 50;
  var GOOGLE_FEE = 0.15;
  var FREE_LIMIT = 5;

  var sb = window.supabase.createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
    auth: { storage: window.sessionStorage, persistSession: true, autoRefreshToken: true, detectSessionInUrl: false }
  });

  var $ = function (id) { return document.getElementById(id); };
  var nf = new Intl.NumberFormat("tr-TR");
  var dateFmt = new Intl.DateTimeFormat("tr-TR", { day: "numeric", month: "short", year: "numeric" });
  var dayFmt = new Intl.DateTimeFormat("tr-TR", { day: "numeric", month: "short" });
  var timeFmt = new Intl.DateTimeFormat("tr-TR", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" });

  var state = { view: "overview", stats: null, usersOffset: 0, usersTotal: 0, purchasesOffset: 0, purchasesTotal: 0, search: "" };

  // ---- Small DOM helpers (text only, never HTML strings with data) --------

  function el(tag, props, children) {
    var node = document.createElement(tag);
    if (props) Object.keys(props).forEach(function (k) {
      if (k === "text") node.textContent = props[k];
      else if (k === "class") node.className = props[k];
      else node.setAttribute(k, props[k]);
    });
    (children || []).forEach(function (c) { if (c) node.appendChild(c); });
    return node;
  }
  function svg(tag, attrs) {
    var node = document.createElementNS("http://www.w3.org/2000/svg", tag);
    Object.keys(attrs || {}).forEach(function (k) { node.setAttribute(k, attrs[k]); });
    return node;
  }
  function clear(node) { while (node.firstChild) node.removeChild(node.firstChild); }
  function fmtDate(v) { return v ? dateFmt.format(new Date(v)) : "—"; }
  function fmtTime(v) { return v ? timeFmt.format(new Date(v)) : "—"; }
  function money(amount, currency) {
    try { return new Intl.NumberFormat("tr-TR", { style: "currency", currency: currency }).format(amount); }
    catch (e) { return nf.format(amount) + " " + currency; }
  }
  function emptyRow(tbody, cols, text) {
    tbody.appendChild(el("tr", null, [el("td", { class: "empty", colspan: String(cols), text: text })]));
  }

  // ---- Auth ---------------------------------------------------------------

  async function isAdmin() {
    var res = await sb.rpc("is_admin");
    return !res.error && res.data === true;
  }

  async function start() {
    var session = (await sb.auth.getSession()).data.session;
    if (session && await isAdmin()) return showApp(session);
    if (session) await sb.auth.signOut();
    showLogin();
  }

  function showLogin() {
    $("app").hidden = true;
    $("login").hidden = false;
    $("login-email").focus();
  }

  function showApp(session) {
    $("login").hidden = true;
    $("app").hidden = false;
    $("admin-email").textContent = session.user.email || "";
    switchView(state.view);
  }

  $("login-form").addEventListener("submit", async function (e) {
    e.preventDefault();
    var error = $("login-error");
    var button = $("login-submit");
    error.hidden = true;
    var email = $("login-email").value.trim();
    var password = $("login-password").value;
    if (!email || !password) {
      error.textContent = "E-posta ve şifreyi gir.";
      error.hidden = false;
      return;
    }
    button.disabled = true;
    button.textContent = "Giriş yapılıyor…";
    var res = await sb.auth.signInWithPassword({ email: email, password: password });
    if (res.error) {
      var msg = String(res.error.message || "").toLowerCase();
      error.textContent = msg.indexOf("invalid") >= 0 ? "E-posta ya da şifre yanlış."
        : msg.indexOf("rate") >= 0 || res.error.status === 429 ? "Çok fazla deneme yapıldı. Birkaç dakika sonra tekrar dene."
        : "Giriş yapılamadı. İnternet bağlantını kontrol edip tekrar dene.";
      error.hidden = false;
    } else if (!(await isAdmin())) {
      await sb.auth.signOut();
      error.textContent = "Bu hesabın yönetici yetkisi yok.";
      error.hidden = false;
    } else {
      $("login-password").value = "";
      showApp(res.data.session);
    }
    button.disabled = false;
    button.textContent = "Giriş yap";
  });

  async function signOut() {
    await sb.auth.signOut();
    state.stats = null;
    showLogin();
  }
  $("sign-out").addEventListener("click", signOut);
  $("mobile-sign-out").addEventListener("click", signOut);

  sb.auth.onAuthStateChange(function (event) {
    if (event === "SIGNED_OUT" && !$("app").hidden) showLogin();
  });

  // ---- Navigation ---------------------------------------------------------

  var TITLES = { overview: "Genel bakış", users: "Kullanıcılar", purchases: "Satın almalar", mail: "E-posta" };

  function switchView(view) {
    state.view = view;
    document.querySelectorAll(".nav-item").forEach(function (b) {
      if (b.dataset.view === view) b.setAttribute("aria-current", "page"); else b.removeAttribute("aria-current");
    });
    Object.keys(TITLES).forEach(function (v) { $("view-" + v).hidden = v !== view; });
    $("view-title").textContent = TITLES[view];
    load(view);
  }
  document.querySelectorAll(".nav-item").forEach(function (b) {
    b.addEventListener("click", function () { switchView(b.dataset.view); });
  });
  $("refresh").addEventListener("click", function () { state.stats = null; load(state.view); });

  async function load(view) {
    try {
      if (view === "overview") await loadOverview();
      else if (view === "users") await loadUsers();
      else if (view === "purchases") await loadPurchases();
      else if (view === "mail") await loadMail();
      $("updated-at").textContent = "Güncellendi " + timeFmt.format(new Date());
    } catch (e) {
      $("updated-at").textContent = "Yüklenemedi. Yenile'ye bas.";
    }
  }

  async function getStats() {
    if (state.stats) return state.stats;
    var res = await sb.rpc("admin_stats");
    if (res.error) throw res.error;
    state.stats = res.data;
    return state.stats;
  }

  // ---- Overview -----------------------------------------------------------

  function kpi(label, value, sub, accent) {
    return el("div", { class: "card kpi" + (accent ? " accent" : "") }, [
      el("span", { class: "label", text: label }),
      el("span", { class: "value", text: value }),
      sub ? el("span", { class: "sub", text: sub }) : null
    ]);
  }

  function primaryRevenue(stats) {
    var list = stats.revenue || [];
    var tr = list.filter(function (r) { return r.currency === "TRY"; })[0];
    return tr || list[0] || null;
  }

  async function loadOverview() {
    var s = await getStats();
    var box = $("kpis");
    clear(box);
    var rev = primaryRevenue(s);
    var others = (s.revenue || []).filter(function (r) { return r !== rev; })
      .map(function (r) { return money(Number(r.gross), r.currency); }).join(" · ");
    var confirmedPct = s.users_total ? Math.round((s.users_confirmed / s.users_total) * 100) : 0;

    box.appendChild(kpi("Toplam kullanıcı", nf.format(s.users_total), "+" + nf.format(s.users_new_7d) + " son 7 günde"));
    box.appendChild(kpi("Aktif kullanıcı", nf.format(s.active_7d), "Son 7 günde kayıt ekleyen/düzenleyen"));
    box.appendChild(kpi("Premium (aktif)", nf.format(s.premium_active), nf.format(s.buyers_total) + " kişi şimdiye kadar satın aldı", true));
    box.appendChild(kpi("Toplam gelir (brüt)", rev ? money(Number(rev.gross), rev.currency) : money(0, "TRY"),
      rev ? "Net ≈ " + money(Number(rev.gross) * (1 - GOOGLE_FEE), rev.currency) + (others ? " · " + others : "") : "Henüz satış yok", true));
    box.appendChild(kpi("Son 30 gün gelir", rev ? money(Number(rev.gross_30d || 0), rev.currency) : money(0, "TRY"),
      nf.format(s.orders_total) + " sipariş toplam"));
    box.appendChild(kpi("Kayıtlı eşya", nf.format(s.items_live), "+" + nf.format(s.items_new_7d) + " son 7 günde · " + nf.format(s.photos) + " fotoğraflı"));
    box.appendChild(kpi("Limitteki kullanıcı", nf.format(s.users_at_limit), FREE_LIMIT + " ücretsiz kaydın hepsini kullananlar"));
    box.appendChild(kpi("E-posta doğrulama", "%" + confirmedPct, nf.format(s.users_confirmed) + " doğrulanmış · " + nf.format(s.email_opt_out) + " duyuru iptali"));

    drawChart("signups", s.signups_by_day || [], "kullanıcı");
    drawChart("orders", s.orders_by_day || [], "sipariş");
  }

  // Single-series bar chart: one hue, thin bars with gaps, rounded data end,
  // recessive grid, hover tooltip, and a table view for accessibility.
  function drawChart(name, rows, unit) {
    var box = $("chart-" + name);
    clear(box);
    var total = rows.reduce(function (a, r) { return a + Number(r.count); }, 0);
    $(name + "-total").textContent = nf.format(total);
    box.setAttribute("aria-label", "Son 30 günde günlük " + unit + " sayısı, toplam " + total);

    var width = Math.max(box.clientWidth, 240), height = box.clientHeight || 180;
    var padL = 28, padB = 22, padT = 6;
    var plotW = width - padL, plotH = height - padB - padT;
    var max = Math.max(1, Math.max.apply(null, rows.map(function (r) { return Number(r.count); })));
    var step = max <= 4 ? 1 : Math.ceil(max / 4);
    var top = step * Math.ceil(max / step);
    var root = svg("svg", { viewBox: "0 0 " + width + " " + height, "aria-hidden": "true" });

    for (var v = 0; v <= top; v += step) {
      var y = padT + plotH - (v / top) * plotH;
      root.appendChild(svg("line", { class: "grid-line", x1: padL, x2: width, y1: y, y2: y }));
      var t = svg("text", { class: "axis-label", x: padL - 6, y: y + 4, "text-anchor": "end" });
      t.textContent = nf.format(v);
      root.appendChild(t);
    }

    var slot = plotW / Math.max(rows.length, 1);
    var barW = Math.max(2, slot - 2);
    rows.forEach(function (r, i) {
      var value = Number(r.count);
      var h = (value / top) * plotH;
      var x = padL + i * slot + 1;
      var yTop = padT + plotH - h;
      var hit = svg("rect", { class: "bar-hit", x: padL + i * slot, y: padT, width: slot, height: plotH });
      var bar;
      if (value > 0) {
        var rad = Math.min(4, barW / 2, h);
        // Rounded top, square base anchored to the baseline.
        bar = svg("path", {
          class: "bar",
          d: "M" + x + "," + (padT + plotH) + "V" + (yTop + rad) + "Q" + x + "," + yTop + " " + (x + rad) + "," + yTop +
             "H" + (x + barW - rad) + "Q" + (x + barW) + "," + yTop + " " + (x + barW) + "," + (yTop + rad) + "V" + (padT + plotH) + "Z"
        });
      }
      var label = dayFmt.format(new Date(r.day + "T00:00:00")) + ": " + nf.format(value) + " " + unit;
      hit.addEventListener("pointerenter", function (ev) { showTip(ev, label); if (bar) bar.classList.add("active"); });
      hit.addEventListener("pointermove", function (ev) { showTip(ev, label); });
      hit.addEventListener("pointerleave", function () { hideTip(); if (bar) bar.classList.remove("active"); });
      root.appendChild(hit);
      if (bar) root.appendChild(bar);
    });

    [0, Math.floor(rows.length / 2), rows.length - 1].forEach(function (i, idx) {
      if (!rows[i]) return;
      var t = svg("text", {
        class: "axis-label", y: height - 4,
        x: idx === 0 ? padL : idx === 2 ? width : padL + i * slot + slot / 2,
        "text-anchor": idx === 0 ? "start" : idx === 2 ? "end" : "middle"
      });
      t.textContent = dayFmt.format(new Date(rows[i].day + "T00:00:00"));
      root.appendChild(t);
    });
    box.appendChild(root);

    var table = el("table", null, [
      el("thead", null, [el("tr", null, [el("th", { text: "Gün" }), el("th", { class: "num", text: "Sayı" })])])
    ]);
    var tb = el("tbody");
    rows.slice().reverse().forEach(function (r) {
      tb.appendChild(el("tr", null, [
        el("td", { text: dateFmt.format(new Date(r.day + "T00:00:00")) }),
        el("td", { class: "num", text: nf.format(Number(r.count)) })
      ]));
    });
    table.appendChild(tb);
    var holder = $("table-" + name);
    clear(holder);
    holder.appendChild(table);
  }

  var tip = $("tooltip");
  function showTip(ev, text) {
    tip.textContent = text;
    tip.hidden = false;
    tip.style.left = ev.clientX + "px";
    tip.style.top = ev.clientY + "px";
  }
  function hideTip() { tip.hidden = true; }

  var resizeTimer;
  window.addEventListener("resize", function () {
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(function () {
      if (state.view === "overview" && state.stats) {
        drawChart("signups", state.stats.signups_by_day || [], "kullanıcı");
        drawChart("orders", state.stats.orders_by_day || [], "sipariş");
      }
    }, 150);
  });

  // ---- Users --------------------------------------------------------------

  async function loadUsers() {
    var res = await sb.rpc("admin_users", { p_search: state.search || null, p_limit: PAGE, p_offset: state.usersOffset });
    if (res.error) throw res.error;
    var rows = res.data || [];
    state.usersTotal = rows.length ? Number(rows[0].total_count) : 0;
    var tbody = $("users-body");
    clear(tbody);
    if (!rows.length) emptyRow(tbody, 6, state.search ? "Bu aramayla eşleşen kullanıcı yok." : "Henüz kullanıcı yok.");
    rows.forEach(function (u) {
      var fill = el("span", { class: "meter-fill" });
      fill.style.width = Math.min(100, (Number(u.items) / FREE_LIMIT) * 100) + "%";
      tbody.appendChild(el("tr", null, [
        el("td", { class: "email", text: u.email || "—", title: u.email || "" }),
        el("td", { text: fmtDate(u.created_at) }),
        el("td", { text: fmtDate(u.last_sign_in_at) }),
        el("td", null, [el("span", { class: "meter" }, [
          el("span", { class: "meter-track" }, [u.premium ? null : fill]),
          el("span", { text: nf.format(Number(u.items)) + (u.premium ? "" : " / " + FREE_LIMIT) })
        ])]),
        el("td", null, [u.premium ? el("span", { class: "badge premium", text: "Premium" })
          : u.confirmed ? el("span", { class: "badge", text: "Ücretsiz" })
          : el("span", { class: "badge warn", text: "Doğrulanmadı" })]),
        el("td", null, [u.email_opt_out ? el("span", { class: "badge", text: "Kapalı" }) : el("span", { class: "badge ok", text: "Açık" })])
      ]));
    });
    $("users-count").textContent = nf.format(state.usersTotal) + " kullanıcı";
    pager("users", state.usersOffset, state.usersTotal);
  }

  function pager(name, offset, total) {
    var pages = Math.max(1, Math.ceil(total / PAGE));
    $(name + "-page").textContent = "Sayfa " + (Math.floor(offset / PAGE) + 1) + " / " + pages;
    $(name + "-prev").disabled = offset <= 0;
    $(name + "-next").disabled = offset + PAGE >= total;
  }

  var searchTimer;
  $("user-search").addEventListener("input", function (e) {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(function () {
      state.search = e.target.value.trim();
      state.usersOffset = 0;
      load("users");
    }, 300);
  });
  $("users-prev").addEventListener("click", function () { state.usersOffset = Math.max(0, state.usersOffset - PAGE); load("users"); });
  $("users-next").addEventListener("click", function () { state.usersOffset += PAGE; load("users"); });

  // ---- Purchases ----------------------------------------------------------

  async function loadPurchases() {
    var s = await getStats();
    var box = $("purchase-kpis");
    clear(box);
    var rev = primaryRevenue(s);
    box.appendChild(kpi("Premium (aktif)", nf.format(s.premium_active), "Son 32 günde ödemesi olan abonelik", true));
    box.appendChild(kpi("Satın alan", nf.format(s.buyers_total), "Şimdiye kadar abone olan"));
    box.appendChild(kpi("Brüt gelir", rev ? money(Number(rev.gross), rev.currency) : money(0, "TRY"), nf.format(s.orders_total) + " sipariş"));
    box.appendChild(kpi("Net gelir (tahmini)", rev ? money(Number(rev.gross) * (1 - GOOGLE_FEE), rev.currency) : money(0, "TRY"), "Google payı %15 düşülmüş"));

    var res = await sb.rpc("admin_purchases", { p_limit: PAGE, p_offset: state.purchasesOffset });
    if (res.error) throw res.error;
    var rows = res.data || [];
    state.purchasesTotal = rows.length ? Number(rows[0].total_count) : 0;
    var tbody = $("purchases-body");
    clear(tbody);
    if (!rows.length) emptyRow(tbody, 6, "Henüz satın alma yok. İlk Premium aboneliği burada görünecek.");
    rows.forEach(function (p) {
      tbody.appendChild(el("tr", null, [
        el("td", { text: fmtTime(p.purchased_at) }),
        el("td", { class: "email", text: p.email || "Silinmiş hesap", title: p.email || "" }),
        el("td", { text: p.order_id }),
        el("td", { text: p.product_id }),
        el("td", { class: "num", text: money(Number(p.price), p.currency) }),
        el("td", null, [p.auto_renewing ? el("span", { class: "badge ok", text: "Açık" }) : el("span", { class: "badge warn", text: "İptal edildi" })])
      ]));
    });
    pager("purchases", state.purchasesOffset, state.purchasesTotal);
  }
  $("purchases-prev").addEventListener("click", function () { state.purchasesOffset = Math.max(0, state.purchasesOffset - PAGE); load("purchases"); });
  $("purchases-next").addEventListener("click", function () { state.purchasesOffset += PAGE; load("purchases"); });

  // ---- Mail ---------------------------------------------------------------

  var AUDIENCE_LABEL = { all: "Tüm kullanıcılar", premium: "Premium üyeler", free: "Ücretsiz plan" };

  async function loadMail() {
    renderPreview();
    var res = await sb.rpc("admin_campaigns");
    if (res.error) throw res.error;
    var tbody = $("campaigns-body");
    clear(tbody);
    var rows = res.data || [];
    if (!rows.length) emptyRow(tbody, 6, "Henüz duyuru gönderilmedi.");
    rows.forEach(function (c) {
      tbody.appendChild(el("tr", null, [
        el("td", { text: fmtTime(c.created_at) }),
        el("td", { class: "email", text: c.subject, title: c.subject }),
        el("td", { text: AUDIENCE_LABEL[c.audience] || c.audience }),
        el("td", { class: "num", text: nf.format(c.recipients) }),
        el("td", { class: "num", text: nf.format(c.sent) }),
        el("td", { class: "num", text: nf.format(c.failed) })
      ]));
    });
  }

  function renderPreview() {
    $("preview-subject").textContent = $("mail-subject").value.trim() || "Konu";
    var body = $("preview-body");
    clear(body);
    var text = $("mail-body").value.trim();
    (text ? text.split(/\n{2,}/) : ["Mesajın burada görünecek."]).forEach(function (para) {
      var p = el("p");
      para.split("\n").forEach(function (line, i) {
        if (i) p.appendChild(el("br"));
        p.appendChild(document.createTextNode(line));
      });
      body.appendChild(p);
    });
  }
  $("mail-subject").addEventListener("input", renderPreview);
  $("mail-body").addEventListener("input", renderPreview);

  function mailStatus(text, isError) {
    var s = $("mail-status");
    s.textContent = text;
    s.className = "form-status" + (isError ? " form-error" : "");
    s.hidden = !text;
  }

  function readMail() {
    var subject = $("mail-subject").value.trim();
    var body = $("mail-body").value.trim();
    if (!subject || !body) {
      mailStatus("Konu ve mesajı yaz.", true);
      return null;
    }
    return { subject: subject, body: body, audience: $("mail-audience").value };
  }

  async function invokeMail(payload) {
    var res = await sb.functions.invoke("admin-mail", { body: payload });
    if (res.error) {
      var code = "";
      try { code = (await res.error.context.json()).error || ""; } catch (e) {}
      throw new Error(code || "failed");
    }
    return res.data;
  }

  function mailError(e) {
    var code = e && e.message;
    return code === "forbidden" ? "Bu hesabın gönderim yetkisi yok."
      : code === "email_not_configured" ? "E-posta servisi ayarlı değil (Resend anahtarı yok)."
      : code === "send_failed" ? "Resend e-postayı kabul etmedi. Günlük limit dolmuş olabilir."
      : "Gönderilemedi. Bağlantını kontrol edip tekrar dene.";
  }

  $("mail-test").addEventListener("click", async function () {
    var mail = readMail();
    if (!mail) return;
    var button = $("mail-test");
    button.disabled = true;
    mailStatus("Test e-postası gönderiliyor…");
    try {
      await invokeMail(Object.assign({ action: "test" }, mail));
      mailStatus("Test e-postası " + $("admin-email").textContent + " adresine gönderildi.");
    } catch (e) {
      mailStatus(mailError(e), true);
    }
    button.disabled = false;
  });

  function estimateRecipients(audience) {
    var s = state.stats;
    if (!s) return null;
    var all = Math.max(0, s.users_confirmed - s.email_opt_out);
    if (audience === "premium") return Math.min(all, s.premium_active);
    if (audience === "free") return Math.max(0, all - s.premium_active);
    return all;
  }

  $("mail-form").addEventListener("submit", async function (e) {
    e.preventDefault();
    var mail = readMail();
    if (!mail) return;
    try { await getStats(); } catch (err) {}
    var estimate = estimateRecipients(mail.audience);
    $("confirm-title").textContent = "Duyuru gönderilsin mi?";
    $("confirm-text").textContent = AUDIENCE_LABEL[mail.audience] + " · yaklaşık " +
      (estimate === null ? "?" : nf.format(estimate)) + " kişi. \"" + mail.subject + "\" konulu e-posta gönderilecek. Bu geri alınamaz.";
    var dialog = $("confirm");
    dialog.returnValue = "";
    dialog.showModal();
    dialog.addEventListener("close", async function onClose() {
      dialog.removeEventListener("close", onClose);
      if (dialog.returnValue !== "ok") return;
      var button = $("mail-send");
      button.disabled = true;
      mailStatus("Gönderiliyor… Bu sayfayı kapatma.");
      try {
        var result = await invokeMail(Object.assign({ action: "send" }, mail));
        mailStatus(nf.format(result.sent) + " kişiye gönderildi" + (result.failed ? ", " + nf.format(result.failed) + " gönderilemedi." : "."), result.failed > 0);
        if (!result.failed) { $("mail-subject").value = ""; $("mail-body").value = ""; }
        await loadMail();
      } catch (err) {
        mailStatus(mailError(err), true);
      }
      button.disabled = false;
    });
  });

  start();
})();
