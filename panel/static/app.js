"use strict";
// PlebMc Panel front end. Plain JS, no build step. All text goes in with textContent (no innerHTML).

const app = document.getElementById("app");
const nav = document.getElementById("nav");
let me = null;
let info = { site: "PlebMc", server: {} };

function h(tag, attrs, ...kids) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs || {})) {
    if (v == null || v === false) continue;
    if (k.startsWith("on")) el.addEventListener(k.slice(2), v);
    else if (k === "class") el.className = v;
    else el.setAttribute(k, v === true ? "" : v);
  }
  for (const k of kids.flat()) {
    if (k == null || k === false) continue;
    el.append(k instanceof Node ? k : document.createTextNode(String(k)));
  }
  return el;
}

async function api(path, body) {
  const opts = body === undefined ? {} : {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Requested-With": "PlebPanel" },
    body: JSON.stringify(body),
  };
  let r;
  try {
    r = await fetch("/api/" + path, { credentials: "same-origin", ...opts });
  } catch (e) {
    throw new Error("Can't reach the panel.");
  }
  const j = await r.json().catch(() => ({}));
  if (!r.ok || j.ok === false) throw new Error(j.error || "Something went wrong.");
  return j;
}

function fmt(n) { return n == null ? "–" : Number(n).toLocaleString(); }
function when(t) { return t ? new Date(t * 1000).toLocaleString() : "never"; }
function coinName() { return (info.server && info.server.currency) || "PlebMc Coins"; }
function params() {
  const q = location.hash.split("?")[1] || "";
  return Object.fromEntries(new URLSearchParams(q));
}
function message(box, text, good) {
  box.replaceChildren();
  if (text) box.append(h("div", { class: "msg " + (good ? "good" : "bad") }, text));
}
function field(label, attrs) {
  const input = h("input", attrs);
  return [h("label", { for: attrs.id }, label), input];
}
function rolePill(role) {
  return role && role !== "player" ? h("span", { class: "pill " + role }, role) : null;
}

function setNav() {
  nav.replaceChildren();
  if (me) {
    nav.append(h("a", { class: "btn", href: "#me" }, me.name));
    if (me.role !== "player") nav.append(h("a", { class: "btn", href: "#admin" }, "Admin"));
    nav.append(h("button", { onclick: logout }, "Sign out"));
  } else {
    nav.append(h("a", { class: "btn", href: "#login" }, "Sign in"), h("a", { class: "btn", href: "#register" }, "Create account"));
  }
}

async function logout() {
  await api("logout", {}).catch(() => {});
  me = null;
  location.hash = "#login";
  route();
}

// ==================== sign in / register ====================

function loginView() {
  const out = h("div");
  const form = h("form", { class: "card narrow", onsubmit: async (e) => {
    e.preventDefault();
    btn.disabled = true;
    try {
      const r = await api("login", { name: form.elements.namedItem("name").value.trim(), password: form.elements.namedItem("password").value });
      me = r.account;
      location.hash = "#me";
    } catch (err) {
      message(out, err.message);
    }
    btn.disabled = false;
  } },
  h("h1", {}, "Sign in"),
  h("p", { class: "muted" }, "Use your Minecraft name and your " + info.site + " account password."),
  field("Minecraft name", { id: "name", name: "name", autocomplete: "username", required: true, maxlength: 16 }),
  field("Password", { id: "password", name: "password", type: "password", autocomplete: "current-password", required: true }),
  out);
  const btn = h("button", { class: "primary full", type: "submit" }, "Sign in");
  form.append(btn, h("p", { class: "muted center" }, "No account yet? ", h("a", { href: "#register" }, "Create one")));
  return form;
}

function registerView() {
  const p = params();
  const out = h("div");
  const form = h("form", { class: "card narrow", onsubmit: async (e) => {
    e.preventDefault();
    if (form.elements.namedItem("password").value !== form.elements.namedItem("password2").value) return message(out, "The passwords don't match.");
    btn.disabled = true;
    try {
      const r = await api("register", { name: form.elements.namedItem("name").value.trim(), code: form.elements.namedItem("code") ? form.elements.namedItem("code").value : "", password: form.elements.namedItem("password").value });
      me = r.account;
      location.hash = "#me";
    } catch (err) {
      message(out, err.message);
    }
    btn.disabled = false;
  } },
  h("h1", {}, "Create your account"),
  info.openRegistration ? h("p", { class: "muted" }, "Pick the exact name you play with.") :
    h("div", {}, h("p", { class: "muted" }, "Your account belongs to the name you play with. To prove it's you:"),
      h("ol", { class: "steps" }, h("li", {}, "Join the server with your name"), h("li", {}, "Type ", h("code", {}, "/account code"), " in chat"),
        h("li", {}, "Type the code here (or click the link in chat)"))),
  field("Minecraft name", { id: "name", name: "name", required: true, maxlength: 16, value: p.name || "", autocomplete: "username" }),
  info.openRegistration ? null : field("Code from the game", { id: "code", name: "code", required: true, value: p.code || "", placeholder: "ABCD-1234", autocomplete: "off" }),
  field("Password (8+ characters)", { id: "password", name: "password", type: "password", required: true, minlength: 8, autocomplete: "new-password" }),
  field("Password again", { id: "password2", name: "password2", type: "password", required: true, minlength: 8, autocomplete: "new-password" }),
  out);
  const btn = h("button", { class: "primary full", type: "submit" }, "Create account");
  form.append(btn, h("p", { class: "muted center" }, "You can also make one in game: ", h("code", {}, "/account register <password> <password>")));
  return form;
}

// ==================== my account ====================

function itemList(owned, equipped, catalog) {
  const byId = Object.fromEntries((catalog || []).map((i) => [i.id, i]));
  const worn = new Set(Object.values(equipped || {}));
  if (!owned || !owned.length) return h("p", { class: "muted" }, "No cosmetics yet. Buy them in the PlebMc client shop (Right Shift).");
  return h("div", { class: "items" }, owned.map((id) => {
    const it = byId[id] || { name: id, category: "" };
    return h("div", { class: "item" + (worn.has(id) ? " worn" : "") }, it.name, h("small", {}, it.category + (worn.has(id) ? " · wearing" : "")));
  }));
}

async function meView() {
  const r = await api("me");
  if (!r.account) { location.hash = "#login"; return loginView(); }
  me = r.account;
  setNav();
  const p = r.player;
  const wrap = h("div");
  wrap.append(h("div", { class: "card" },
    h("h1", {}, me.name, " ", rolePill(me.role)),
    h("p", { class: "muted" }, r.online ? "Online now" : "Last signed in " + when(me.lastLogin)),
    h("div", { class: "stats" },
      h("div", { class: "stat" }, h("b", {}, fmt(p ? p.coins : 0)), h("span", {}, coinName())),
      h("div", { class: "stat" }, h("b", {}, fmt(p ? p.owned.length : 0)), h("span", {}, "Cosmetics")),
      h("div", { class: "stat" }, h("b", {}, fmt(p ? p.streak : 0)), h("span", {}, "Login streak")),
      h("div", { class: "stat" }, h("b", {}, fmt(r.devices)), h("span", {}, "Remembered game clients"))),
    p ? null : h("p", { class: "muted" }, "Join the server once and your coins show up here.")),
  h("div", { class: "card" }, h("h2", {}, "Your cosmetics"), itemList(p && p.owned, p && p.equipped, r.catalog)),
  h("div", { class: "card" }, h("h2", {}, "In game"),
    h("p", { class: "muted" }, "On the PlebMc client, sign in once with Right Shift > Sign in and tick Remember. On any other client type ",
      h("code", {}, "/account login <password>"), " after you join. Until you sign in, nobody on your name can spend your coins.")));
  wrap.append(passwordCard(), signoutCard());
  return wrap;
}

function passwordCard() {
  const out = h("div");
  const form = h("form", { class: "card", onsubmit: async (e) => {
    e.preventDefault();
    if (form.elements.namedItem("password").value !== form.elements.namedItem("password2").value) return message(out, "The new passwords don't match.");
    try {
      await api("me/password", { old: form.elements.namedItem("old").value, password: form.elements.namedItem("password").value });
      form.reset();
      message(out, "Password changed. Every game client was signed out.", true);
    } catch (err) { message(out, err.message); }
  } }, h("h2", {}, "Change password"),
  h("div", { class: "row" },
    h("div", {}, field("Current password", { id: "old", name: "old", type: "password", required: true, autocomplete: "current-password" })),
    h("div", {}, field("New password", { id: "npw", name: "password", type: "password", required: true, minlength: 8, autocomplete: "new-password" })),
    h("div", {}, field("New password again", { id: "npw2", name: "password2", type: "password", required: true, minlength: 8, autocomplete: "new-password" })),
    h("button", { class: "primary", type: "submit" }, "Change")), out);
  return form;
}

function signoutCard() {
  const out = h("div");
  return h("div", { class: "card" }, h("h2", {}, "Sign out everywhere"),
    h("p", { class: "muted" }, "Forgets every remembered game client and signs you out in game. Use this if someone else might know your password."),
    h("button", { class: "danger", onclick: async () => {
      try { await api("me/signout", {}); message(out, "Done. Sign in again in game.", true); } catch (err) { message(out, err.message); }
    } }, "Sign out everywhere"), out);
}

// ==================== admin ====================

async function adminView() {
  if (!me || me.role === "player") { location.hash = "#me"; return meView(); }
  const wrap = h("div");
  const list = h("div", { class: "scroll" });
  const status = h("p", { class: "muted" });
  const search = h("input", { placeholder: "Search players", "aria-label": "Search players" });
  let timer;
  const load = async () => {
    try {
      const r = await api("admin/players?q=" + encodeURIComponent(search.value.trim()));
      const s = r.server;
      status.replaceChildren(h("span", { class: "dot" + (s.connected ? " on" : "") }),
        s.connected ? "Proxy connected · " + s.online + " online · last sync " + when(s.lastSync) : "Proxy not connected (last sync " + when(s.lastSync) + "). Check accounts: in PlebioAPI's config.yml.");
      list.replaceChildren(h("table", {}, h("thead", {}, h("tr", {}, h("th", {}, "Player"), h("th", {}, coinName()), h("th", {}, "Items"), h("th", {}, "Account"))),
        h("tbody", {}, r.players.map((p) => h("tr", { class: "click", onclick: () => { location.hash = "#player?name=" + encodeURIComponent(p.name); } },
          h("td", {}, p.online ? h("span", { class: "dot on" }) : null, p.name, " ", rolePill(p.role)),
          h("td", {}, fmt(p.coins)), h("td", {}, fmt(p.items)),
          h("td", {}, p.locked ? h("span", { class: "pill locked" }, "locked") : p.account ? "yes" : h("span", { class: "muted" }, "none")))))));
      if (!r.players.length) list.append(h("p", { class: "muted" }, "Nobody found."));
    } catch (err) { message(list, err.message); }
  };
  search.addEventListener("input", () => { clearTimeout(timer); timer = setTimeout(load, 250); });
  wrap.append(h("div", { class: "card" }, h("h1", {}, "Players"), status, search, h("div", { style: "height:12px" }), list));
  wrap.append(createCard(load));
  wrap.append(auditCard());
  load();
  return wrap;
}

function createCard(after) {
  const out = h("div");
  const form = h("form", { class: "card", onsubmit: async (e) => {
    e.preventDefault();
    try {
      await api("admin/account", { action: "create", name: form.elements.namedItem("name").value.trim(), password: form.elements.namedItem("password").value, role: form.elements.namedItem("role") ? form.elements.namedItem("role").value : "player" });
      message(out, "Created " + form.elements.namedItem("name").value.trim() + ".", true);
      form.reset();
      after();
    } catch (err) { message(out, err.message); }
  } }, h("h2", {}, "Create an account for a player"),
  h("div", { class: "row" },
    h("div", {}, field("Minecraft name", { id: "cname", name: "name", required: true, maxlength: 16 })),
    h("div", {}, field("Password", { id: "cpw", name: "password", type: "password", required: true, minlength: 8, autocomplete: "new-password" })),
    me.role === "owner" ? h("div", {}, h("label", { for: "crole" }, "Role"), h("select", { id: "crole", name: "role" }, ROLE_OPTIONS())) : null,
    h("button", { class: "primary", type: "submit" }, "Create")), out);
  return form;
}

function ROLE_OPTIONS(sel) {
  return ["player", "admin", "owner"].map((r) => h("option", { value: r, selected: r === sel }, r));
}

function auditCard() {
  const box = h("div", { class: "scroll" }, h("button", { onclick: async () => {
    try {
      const r = await api("admin/audit");
      box.replaceChildren(h("table", {}, h("thead", {}, h("tr", {}, h("th", {}, "When"), h("th", {}, "Who"), h("th", {}, "What"), h("th", {}, "Detail"))),
        h("tbody", {}, r.audit.map((a) => h("tr", {}, h("td", {}, when(a.at)), h("td", {}, a.actor), h("td", {}, a.action), h("td", {}, a.detail))))));
    } catch (err) { message(box, err.message); }
  } }, "Show the log"));
  return h("div", { class: "card" }, h("h2", {}, "Admin log"), box);
}

async function playerView() {
  if (!me || me.role === "player") { location.hash = "#me"; return meView(); }
  const name = params().name || "";
  const r = await api("admin/player?name=" + encodeURIComponent(name));
  const p = r.player;
  const acc = r.account;
  const out = h("div");
  const done = (text) => { message(out, text, true); setTimeout(route, 1500); };
  const act = (body) => api("admin/command", { name: r.name, ...body }).then(() => done("Queued. The server applies it within a few seconds.")).catch((e) => message(out, e.message));
  const accAct = (body, confirmText) => {
    if (confirmText && !confirm(confirmText)) return;
    api("admin/account", { name: r.name, ...body }).then(() => done("Done.")).catch((e) => message(out, e.message));
  };
  const wrap = h("div", {}, h("p", {}, h("a", { href: "#admin" }, "← Players")));
  wrap.append(h("div", { class: "card" },
    h("h1", {}, r.online ? h("span", { class: "dot on" }) : null, r.name, " ", rolePill(acc && acc.role), acc && acc.locked ? h("span", { class: "pill locked" }, "locked") : null),
    h("p", { class: "muted" }, acc ? "Account since " + when(acc.created) + " · last sign-in " + when(acc.lastLogin) + " · " + r.devices + " remembered client(s)" : "No account yet: anyone on this name can't spend until it has one."),
    h("div", { class: "stats" },
      h("div", { class: "stat" }, h("b", {}, fmt(p ? p.coins : 0)), h("span", {}, coinName())),
      h("div", { class: "stat" }, h("b", {}, fmt(p ? p.owned.length : 0)), h("span", {}, "Cosmetics")),
      h("div", { class: "stat" }, h("b", {}, fmt(p ? p.streak : 0)), h("span", {}, "Login streak"))), out));

  // coins
  const amount = h("input", { type: "number", min: 0, value: 100, "aria-label": "Amount" });
  wrap.append(h("div", { class: "card" }, h("h2", {}, coinName()), h("div", { class: "row" }, amount,
    h("button", { onclick: () => act({ action: "coins", op: "give", amount: amount.value }) }, "Give"),
    h("button", { onclick: () => act({ action: "coins", op: "take", amount: amount.value }) }, "Take"),
    h("button", { onclick: () => act({ action: "coins", op: "set", amount: amount.value }) }, "Set to"))));

  // cosmetics
  const cat = r.catalog || [];
  const itemSel = h("select", { "aria-label": "Item" }, cat.map((i) => h("option", { value: i.id }, i.name + " (" + i.category + ")")));
  wrap.append(h("div", { class: "card" }, h("h2", {}, "Cosmetics"), itemList(p && p.owned, p && p.equipped, cat), h("div", { style: "height:12px" }),
    cat.length ? h("div", { class: "row" }, itemSel,
      h("button", { onclick: () => act({ action: "give", item: itemSel.value }) }, "Give"),
      h("button", { onclick: () => act({ action: "remove", item: itemSel.value }) }, "Remove"),
      h("button", { onclick: () => { const it = cat.find((i) => i.id === itemSel.value); act({ action: "equip", category: it.category, item: it.id }); } }, "Put on"))
      : h("p", { class: "muted" }, "The item list appears after the proxy connects.")));

  // moderation
  const reason = h("input", { placeholder: "Kick reason", value: "Kicked by an admin." });
  const mod = h("div", { class: "card" }, h("h2", {}, "Moderation"), h("div", { class: "row" }, reason, h("button", { class: "danger", onclick: () => act({ action: "kick", reason: reason.value }) }, "Kick")));
  if (acc) {
    const npw = h("input", { type: "password", placeholder: "New password (8+)", autocomplete: "new-password" });
    mod.append(h("div", { style: "height:12px" }), h("div", { class: "row" }, npw, h("button", { onclick: () => accAct({ action: "password", password: npw.value }) }, "Reset password")),
      h("div", { style: "height:12px" }),
      h("div", { class: "row" },
        h("button", { onclick: () => accAct({ action: "signout" }) }, "Sign out everywhere"),
        acc.locked ? h("button", { onclick: () => accAct({ action: "unlock" }) }, "Unlock") : h("button", { class: "danger", onclick: () => accAct({ action: "lock" }, "Lock " + r.name + "? They can't sign in until you unlock them.") }, "Lock account"),
        h("button", { class: "danger", onclick: () => accAct({ action: "delete" }, "Delete " + r.name + "'s account? Their coins stay, but anyone can then register the name.") }, "Delete account")));
    if (me.role === "owner") {
      const roleSel = h("select", { "aria-label": "Role" }, ROLE_OPTIONS(acc.role));
      mod.append(h("div", { style: "height:12px" }), h("div", { class: "row" }, roleSel, h("button", { onclick: () => accAct({ action: "role", role: roleSel.value }) }, "Set role")));
    }
  }
  wrap.append(mod);

  if (r.commands.length) {
    wrap.append(h("div", { class: "card scroll" }, h("h2", {}, "Recent changes"), h("table", {},
      h("thead", {}, h("tr", {}, h("th", {}, "When"), h("th", {}, "By"), h("th", {}, "Change"), h("th", {}, "Status"))),
      h("tbody", {}, r.commands.map((c) => h("tr", {}, h("td", {}, when(c.at)), h("td", {}, c.by), h("td", {}, c.action + " " + c.args.join(" ")),
        h("td", {}, c.status + (c.result ? ": " + c.result : ""))))))));
  }
  return wrap;
}

// ==================== router ====================

async function route() {
  const view = (location.hash.slice(1).split("?")[0]) || (me ? "me" : "login");
  setNav();
  let el;
  try {
    if (view === "register") el = registerView();
    else if (view === "login") el = me ? await meView() : loginView();
    else if (view === "admin") el = await adminView();
    else if (view === "player") el = await playerView();
    else el = await meView();
  } catch (err) {
    el = h("div", { class: "card narrow" }, h("p", {}, err.message), h("a", { href: "#me" }, "Back"));
  }
  app.replaceChildren(el);
  setNav();
}

(async function start() {
  try {
    info = await api("info");
    document.getElementById("site").textContent = info.site;
    document.title = info.site + " Panel";
    const r = await api("me");
    me = r.account;
  } catch (e) { /* shown by the view */ }
  window.addEventListener("hashchange", route);
  route();
})();
