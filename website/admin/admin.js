(function () {
  "use strict";

  // Client-safe values only (same project URL and anon key the app ships with).
  // Every admin query is authorised in the database by public.is_admin().
  var SUPABASE_URL = "https://gxvhltdvtidqcoyafkyl.supabase.co";
  var SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imd4dmhsdGR2dGlkcWNveWFma3lsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTAzNjE3MTYsImV4cCI6MjEwNTkzNzcxNn0.3ZGDDoFa6pH5cGCY_PzaQ_f6pS_JvhYUWL66wyvpdlU";
  var PAGE = 50;
  var GOOGLE_FEE = 0.15;
  var DEFAULT_FREE_LIMIT = 5;

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

  /** True when the account has two-step verification and this session hasn't passed it yet. */
  async function needsSecondStep() {
    try {
      var res = await sb.auth.mfa.getAuthenticatorAssuranceLevel();
      return !res.error && res.data.nextLevel === "aal2" && res.data.currentLevel !== "aal2";
    } catch (e) {
      // The database still refuses admin access without the second step.
      return false;
    }
  }

  async function start() {
    var session = (await sb.auth.getSession()).data.session;
    if (session && await needsSecondStep()) return showSecondStep();
    if (session && await isAdmin()) return showApp(session);
    if (session) await sb.auth.signOut();
    showLogin();
  }

  function showLogin() {
    $("app").hidden = true;
    $("login").hidden = false;
    $("mfa-form").hidden = true;
    $("login-form").hidden = false;
    $("login-email").focus();
  }

  function showSecondStep() {
    $("app").hidden = true;
    $("login").hidden = false;
    $("login-form").hidden = true;
    $("mfa-form").hidden = false;
    $("mfa-error").hidden = true;
    $("mfa-code").value = "";
    $("mfa-code").focus();
  }

  async function finishSignIn(errorBox) {
    if (!(await isAdmin())) {
      await sb.auth.signOut();
      errorBox.textContent = "Bu hesabın yönetici yetkisi yok.";
      errorBox.hidden = false;
      showLogin();
      $("login-error").textContent = errorBox.textContent;
      $("login-error").hidden = false;
      return;
    }
    showApp((await sb.auth.getSession()).data.session);
  }

  $("mfa-form").addEventListener("submit", async function (e) {
    e.preventDefault();
    var error = $("mfa-error");
    var code = $("mfa-code").value.replace(/\s/g, "");
    error.hidden = true;
    if (!/^\d{6}$/.test(code)) {
      error.textContent = "6 haneli kodu gir.";
      error.hidden = false;
      return;
    }
    var button = $("mfa-submit");
    button.disabled = true;
    var factors = await sb.auth.mfa.listFactors();
    var totp = factors.data && factors.data.totp && factors.data.totp[0];
    var res = totp ? await sb.auth.mfa.challengeAndVerify({ factorId: totp.id, code: code }) : { error: true };
    button.disabled = false;
    if (res.error) {
      error.textContent = "Kod yanlış ya da süresi geçti. Uygulamadaki yeni kodu dene.";
      error.hidden = false;
      return;
    }
    await finishSignIn(error);
  });
  $("mfa-cancel").addEventListener("click", async function () {
    await sb.auth.signOut();
    showLogin();
  });

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
    } else {
      $("login-password").value = "";
      if (await needsSecondStep()) showSecondStep();
      else await finishSignIn(error);
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

  var TITLES = { overview: "Genel bakış", users: "Kullanıcılar", purchases: "Satın almalar", mail: "E-posta", settings: "Ayarlar" };

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
      else if (view === "settings") await loadSettings();
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

  function freeLimit() {
    return (state.stats && Number(state.stats.free_item_limit)) || DEFAULT_FREE_LIMIT;
  }

  /** Asks for confirmation in the shared dialog. Resolves true on OK. */
  function confirmAsk(title, text, okLabel, danger) {
    return new Promise(function (resolve) {
      var dialog = $("confirm");
      $("confirm-title").textContent = title;
      $("confirm-text").textContent = text;
      $("confirm-ok").textContent = okLabel;
      $("confirm-ok").className = "btn primary" + (danger ? " danger" : "");
      dialog.returnValue = "";
      dialog.addEventListener("close", function onClose() {
        dialog.removeEventListener("close", onClose);
        resolve(dialog.returnValue === "ok");
      });
      dialog.showModal();
    });
  }

  async function functionError(res) {
    var code = "";
    try { code = (await res.error.context.json()).error || ""; } catch (e) {}
    return code || "failed";
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
    box.appendChild(kpi("Premium (aktif)", nf.format(s.premium_active), nf.format(s.buyers_total) + " kişi şimdiye kadar satın aldı" +
      (s.premium_verified ? " · " + nf.format(s.premium_verified) + " Google doğrulamalı" : ""), true));
    box.appendChild(kpi("Toplam gelir (brüt)", rev ? money(Number(rev.gross), rev.currency) : money(0, "TRY"),
      rev ? "Net ≈ " + money(Number(rev.gross) * (1 - GOOGLE_FEE), rev.currency) + (others ? " · " + others : "") : "Henüz satış yok", true));
    box.appendChild(kpi("Son 30 gün gelir", rev ? money(Number(rev.gross_30d || 0), rev.currency) : money(0, "TRY"),
      nf.format(s.orders_total) + " sipariş toplam"));
    box.appendChild(kpi("Kayıtlı eşya", nf.format(s.items_live), "+" + nf.format(s.items_new_7d) + " son 7 günde · " + nf.format(s.photos) + " fotoğraflı"));
    box.appendChild(kpi("Limitteki kullanıcı", nf.format(s.users_at_limit), freeLimit() + " ücretsiz kaydın hepsini kullananlar"));
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
    try { await getStats(); } catch (e) {}
    var res = await sb.rpc("admin_users", { p_search: state.search || null, p_limit: PAGE, p_offset: state.usersOffset });
    if (res.error) throw res.error;
    var rows = res.data || [];
    state.usersTotal = rows.length ? Number(rows[0].total_count) : 0;
    var tbody = $("users-body");
    clear(tbody);
    if (!rows.length) emptyRow(tbody, 7, state.search ? "Bu aramayla eşleşen kullanıcı yok." : "Henüz kullanıcı yok.");
    var limit = freeLimit();
    rows.forEach(function (u) {
      var fill = el("span", { class: "meter-fill" });
      fill.style.width = Math.min(100, (Number(u.items) / limit) * 100) + "%";
      tbody.appendChild(el("tr", null, [
        el("td", { class: "email", text: u.email || "—", title: u.email || "" }),
        el("td", { text: fmtDate(u.created_at) }),
        el("td", { text: fmtDate(u.last_sign_in_at) }),
        el("td", null, [el("span", { class: "meter" }, [
          el("span", { class: "meter-track" }, [u.premium ? null : fill]),
          el("span", { text: nf.format(Number(u.items)) + (u.premium ? "" : " / " + limit) })
        ])]),
        el("td", null, [u.premium ? el("span", { class: "badge premium", text: "Premium" })
          : u.confirmed ? el("span", { class: "badge", text: "Ücretsiz" })
          : el("span", { class: "badge warn", text: "Doğrulanmadı" })]),
        el("td", null, [u.email_opt_out ? el("span", { class: "badge", text: "Kapalı" }) : el("span", { class: "badge ok", text: "Açık" })]),
        el("td", null, [userActions(u)])
      ]));
    });
    $("users-count").textContent = nf.format(state.usersTotal) + " kullanıcı";
    pager("users", state.usersOffset, state.usersTotal);
  }

  function userActions(u) {
    var box = el("div", { class: "row-actions" });
    if (!u.confirmed) {
      var resend = el("button", { class: "btn ghost small", type: "button", text: "Doğrulama e-postası" });
      resend.addEventListener("click", function () { userAction(u, "resend_confirmation", resend); });
      box.appendChild(resend);
    }
    var del = el("button", { class: "btn small danger", type: "button", text: "Sil" });
    del.addEventListener("click", function () { userAction(u, "delete_user", del); });
    box.appendChild(del);
    return box;
  }

  async function userAction(u, action, button) {
    var ok = action === "delete_user"
      ? await confirmAsk("Kullanıcı silinsin mi?", u.email + " hesabı, tüm kayıtları ve fotoğraflarıyla kalıcı olarak silinecek. Bu geri alınamaz.", "Kalıcı olarak sil", true)
      : await confirmAsk("Doğrulama e-postası gönderilsin mi?", u.email + " adresine yeni bir doğrulama linki gidecek.", "Gönder", false);
    if (!ok) return;
    button.disabled = true;
    var res = await sb.functions.invoke("admin-actions", { body: { action: action, user_id: u.id } });
    button.disabled = false;
    if (res.error) {
      var code = await functionError(res);
      alertStatus(code === "cannot_delete_admin" ? "Yönetici hesapları buradan silinemez."
        : code === "already_confirmed" ? "Bu e-posta zaten doğrulanmış."
        : code === "send_failed" ? "E-posta gönderilemedi. Birkaç dakika sonra tekrar dene."
        : "İşlem yapılamadı. Tekrar dene.");
      return;
    }
    alertStatus(action === "delete_user" ? u.email + " silindi." : "Doğrulama e-postası gönderildi.");
    state.stats = null;
    load("users");
  }

  function alertStatus(text) {
    $("users-count").textContent = text;
  }

  // ---- CSV export ---------------------------------------------------------

  function csvCell(v) {
    var t = v === null || v === undefined ? "" : String(v);
    // Keep spreadsheet apps from running cell contents as formulas.
    if (/^[=+\-@\t\r]/.test(t)) t = "'" + t;
    return /[",\n;]/.test(t) ? '"' + t.replace(/"/g, '""') + '"' : t;
  }

  function downloadCsv(name, header, rows) {
    var text = "\ufeff" + [header].concat(rows).map(function (r) { return r.map(csvCell).join(","); }).join("\r\n");
    var url = URL.createObjectURL(new Blob([text], { type: "text/csv;charset=utf-8" }));
    var a = el("a", { href: url, download: name });
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(function () { URL.revokeObjectURL(url); }, 1000);
  }

  async function fetchAll(fn, params, pageSize) {
    var out = [];
    for (var offset = 0; ; offset += pageSize) {
      var res = await sb.rpc(fn, Object.assign({}, params, { p_limit: pageSize, p_offset: offset }));
      if (res.error) throw res.error;
      out = out.concat(res.data || []);
      if (!res.data || res.data.length < pageSize) break;
    }
    return out;
  }

  var today = function () { return new Date().toISOString().slice(0, 10); };

  $("users-csv").addEventListener("click", async function () {
    var button = this;
    button.disabled = true;
    try {
      var rows = await fetchAll("admin_users", { p_search: null }, 200);
      downloadCsv("kullanicilar-" + today() + ".csv",
        ["E-posta", "Kayıt tarihi", "Son giriş", "E-posta doğrulandı", "Kayıt sayısı", "Premium", "Duyuru iptali"],
        rows.map(function (u) {
          return [u.email, u.created_at, u.last_sign_in_at || "", u.confirmed ? "evet" : "hayır", u.items, u.premium ? "evet" : "hayır", u.email_opt_out ? "evet" : "hayır"];
        }));
    } catch (e) {
      alertStatus("CSV hazırlanamadı. Tekrar dene.");
    }
    button.disabled = false;
  });

  $("purchases-csv").addEventListener("click", async function () {
    var button = this;
    button.disabled = true;
    try {
      var rows = await fetchAll("admin_purchases", {}, 500);
      downloadCsv("satin-almalar-" + today() + ".csv",
        ["Tarih", "E-posta", "Sipariş no", "Ürün", "Tutar", "Para birimi", "Otomatik yenileme"],
        rows.map(function (p) {
          return [p.purchased_at, p.email || "Silinmiş hesap", p.order_id, p.product_id, p.price, p.currency, p.auto_renewing ? "açık" : "kapalı"];
        }));
    } catch (e) {}
    button.disabled = false;
  });

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
    var ok = await confirmAsk("Duyuru gönderilsin mi?", AUDIENCE_LABEL[mail.audience] + " · yaklaşık " +
      (estimate === null ? "?" : nf.format(estimate)) + " kişi. \"" + mail.subject + "\" konulu e-posta gönderilecek. Bu geri alınamaz.", "Gönder", false);
    if (ok) {
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
    }
  });

  // ---- Settings -----------------------------------------------------------

  function setStatus(id, text, isError) {
    var node = $(id);
    node.textContent = text;
    node.className = "form-status" + (isError ? " form-error" : "");
    node.hidden = !text;
  }

  async function loadSettings() {
    var cfg = await sb.rpc("admin_config");
    if (cfg.error) throw cfg.error;
    $("cfg-limit").value = cfg.data.free_item_limit;
    $("cfg-ads").value = cfg.data.ads_per_day;
    $("cfg-enforce").checked = cfg.data.server_limit_enforced === true;

    var ann = await sb.from("app_announcements").select("message_tr, message_en").eq("active", true).maybeSingle();
    $("announce-current").hidden = !ann.data;
    $("announce-current-text").textContent = ann.data ? ann.data.message_tr + (ann.data.message_en ? " / " + ann.data.message_en : "") : "";
    $("announce-clear").disabled = !ann.data;

    try {
      var s = await getStats();
      $("verified-count").textContent = nf.format(s.premium_verified || 0);
    } catch (e) {}

    await renderMfa();
  }

  $("config-form").addEventListener("submit", async function (e) {
    e.preventDefault();
    var limit = Number($("cfg-limit").value);
    var ads = Number($("cfg-ads").value);
    if (!Number.isInteger(limit) || limit < 1 || limit > 1000) return setStatus("config-status", "Limit 1 ile 1000 arasında bir tam sayı olmalı.", true);
    if (!Number.isInteger(ads) || ads < 0 || ads > 10) return setStatus("config-status", "Reklam sayısı 0 ile 10 arasında olmalı.", true);
    var enforce = $("cfg-enforce").checked;
    var calls = [
      sb.rpc("admin_set_config", { p_key: "free_item_limit", p_value: limit }),
      sb.rpc("admin_set_config", { p_key: "ads_per_day", p_value: ads }),
      sb.rpc("admin_set_config", { p_key: "server_limit_enforced", p_value: enforce })
    ];
    var results = await Promise.all(calls);
    var failed = results.some(function (r) { return r.error; });
    state.stats = null;
    setStatus("config-status", failed ? "Kaydedilemedi. Tekrar dene." : "Kaydedildi. Uygulama bir sonraki açılışta yeni ayarları kullanır.", failed);
  });

  $("announce-form").addEventListener("submit", async function (e) {
    e.preventDefault();
    var tr = $("announce-tr").value.trim();
    var en = $("announce-en").value.trim();
    if (!tr) return setStatus("announce-status", "Türkçe mesajı yaz.", true);
    var ok = await confirmAsk("Duyuru yayınlansın mı?", "Uygulamayı açan herkes ana ekranda bu mesajı görecek. Yayındaki eski duyuru kaldırılır.", "Yayınla", false);
    if (!ok) return;
    var res = await sb.rpc("admin_publish_announcement", { p_tr: tr, p_en: en || null });
    if (res.error) return setStatus("announce-status", "Yayınlanamadı. Mesaj en fazla 280 karakter olabilir.", true);
    $("announce-tr").value = "";
    $("announce-en").value = "";
    setStatus("announce-status", "Yayında. Kullanıcılar uygulamayı açınca görecek.");
    loadSettings();
  });

  $("announce-clear").addEventListener("click", async function () {
    var res = await sb.rpc("admin_clear_announcement");
    setStatus("announce-status", res.error ? "Kaldırılamadı. Tekrar dene." : "Duyuru yayından kaldırıldı.", !!res.error);
    loadSettings();
  });

  $("report-now").addEventListener("click", async function () {
    var button = this;
    button.disabled = true;
    var res = await sb.functions.invoke("weekly-report", { body: {} });
    button.disabled = false;
    setStatus("report-status", res.error ? "Gönderilemedi. Tekrar dene." : "Özet e-postası gönderildi.", !!res.error);
  });

  // Two-step verification (TOTP)
  var enrolling = null;

  async function renderMfa() {
    var factors = await sb.auth.mfa.listFactors();
    var verified = factors.data && factors.data.totp && factors.data.totp[0];
    $("mfa-state").textContent = verified ? "Açık. Girişte doğrulama kodu isteniyor." : "Kapalı. Şu an sadece şifre ile giriliyor.";
    $("mfa-start").hidden = !!verified || !!enrolling;
    $("mfa-remove").hidden = !verified;
    $("mfa-confirm").hidden = !enrolling;
    $("mfa-enroll").hidden = !enrolling;
  }

  $("mfa-start").addEventListener("click", async function () {
    setStatus("mfa-status", "");
    // Clear half-finished setups first.
    var existing = await sb.auth.mfa.listFactors();
    var stale = ((existing.data && existing.data.all) || []).filter(function (f) { return f.status !== "verified"; });
    for (var i = 0; i < stale.length; i++) await sb.auth.mfa.unenroll({ factorId: stale[i].id });

    var res = await sb.auth.mfa.enroll({ factorType: "totp", friendlyName: "Yönetim paneli" });
    if (res.error) return setStatus("mfa-status", "Kurulum başlatılamadı. Tekrar dene.", true);
    enrolling = res.data.id;
    $("mfa-qr").src = res.data.totp.qr_code;
    $("mfa-secret").textContent = res.data.totp.secret;
    $("mfa-enroll-code").value = "";
    await renderMfa();
    $("mfa-enroll-code").focus();
  });

  $("mfa-confirm").addEventListener("click", async function () {
    var code = $("mfa-enroll-code").value.replace(/\s/g, "");
    if (!/^\d{6}$/.test(code)) return setStatus("mfa-status", "Uygulamadaki 6 haneli kodu gir.", true);
    var res = await sb.auth.mfa.challengeAndVerify({ factorId: enrolling, code: code });
    if (res.error) return setStatus("mfa-status", "Kod yanlış ya da süresi geçti. Yeni kodu dene.", true);
    enrolling = null;
    setStatus("mfa-status", "İki adımlı doğrulama açıldı.");
    await renderMfa();
  });

  $("mfa-remove").addEventListener("click", async function () {
    var ok = await confirmAsk("İki adımlı doğrulama kapatılsın mı?", "Panele sadece şifreyle girilebilecek.", "Kapat", true);
    if (!ok) return;
    var factors = await sb.auth.mfa.listFactors();
    var totp = factors.data && factors.data.totp && factors.data.totp[0];
    var res = totp ? await sb.auth.mfa.unenroll({ factorId: totp.id }) : { error: true };
    setStatus("mfa-status", res.error ? "Kapatılamadı. Tekrar dene." : "İki adımlı doğrulama kapatıldı.", !!res.error);
    await renderMfa();
  });

  start();
})();
