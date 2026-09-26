(function () {
  var buttons = document.querySelectorAll(".lang button");
  function apply(lang) {
    document.documentElement.lang = lang;
    document.querySelectorAll("[data-doc]").forEach(function (el) { el.hidden = el.getAttribute("data-doc") !== lang; });
    buttons.forEach(function (b) { b.setAttribute("aria-pressed", String(b.dataset.lang === lang)); });
    try { localStorage.setItem("wdipi-lang", lang); } catch (e) {}
  }
  buttons.forEach(function (b) { b.addEventListener("click", function () { apply(b.dataset.lang); }); });
  var saved = null;
  try { saved = localStorage.getItem("wdipi-lang"); } catch (e) {}
  apply(saved || ((navigator.language || "").toLowerCase().indexOf("tr") === 0 ? "tr" : "en"));
})();
