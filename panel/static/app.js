"use strict";
// PlebMc Panel front end. Plain JS, no build step. Text always goes in with textContent (no innerHTML).
// Long lists (players, leaderboard, designs, the log) load one page at a time with "Show more",
// so a big network never makes the browser load everyone at once.

const app = document.getElementById("app");
const nav = document.getElementById("nav");
const who = document.getElementById("who");
const titleEl = document.getElementById("title");
let me = null;
let info = { site: "PlebMc", server: {}, client: {} };
let catalogCache = null;

// ==================== helpers ====================

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

const ICONS = {
  home: "M3 11l9-8 9 8M5 10v10h5v-6h4v6h5V10",
  trophy: "M8 21h8M12 17v4M7 4h10v5a5 5 0 01-10 0V4zM17 5h3v2a3 3 0 01-3 3M7 5H4v2a3 3 0 003 3",
  bag: "M6 7h12l1 14H5L6 7zM9 7V5a3 3 0 016 0v2",
  image: "M4 4h16v16H4zM4 16l5-5 4 4 3-3 4 4M15 9h.01",
  hammer: "M14 4l6 6-3 3-6-6 3-3zM11 7l-8 8 3 3 8-8",
  user: "M12 12a4 4 0 100-8 4 4 0 000 8zM4 21a8 8 0 0116 0",
  grid: "M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z",
  users: "M9 11a4 4 0 100-8 4 4 0 000 8zM2 21a7 7 0 0114 0M16 3.5a4 4 0 010 7.5M18 14a7 7 0 014 7",
  megaphone: "M3 11v2a1 1 0 001 1h2l5 4V6L6 10H4a1 1 0 00-1 1zM15 8a5 5 0 010 8M18 5a9 9 0 010 14",
  upload: "M12 16V4M7 9l5-5 5 5M4 16v4h16v-4",
  refresh: "M20 11a8 8 0 00-14.9-3M4 4v4h4M4 13a8 8 0 0014.9 3M20 20v-4h-4",
  list: "M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01",
  heart: "M12 21s-7-4.5-9.5-9A5.5 5.5 0 0112 6a5.5 5.5 0 019.5 6c-2.5 4.5-9.5 9-9.5 9z",
  logout: "M9 21H5V3h4M16 17l5-5-5-5M21 12H9",
  coin: "M12 21a9 9 0 100-18 9 9 0 000 18zM9 9h4.5a1.5 1.5 0 010 3H10.5a1.5 1.5 0 000 3H15M12 7v2M12 15v2",
  bell: "M6 16V11a6 6 0 0112 0v5l2 2H4l2-2zM10 21h4",
  plus: "M12 5v14M5 12h14",
  trash: "M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13",
  online: "M5 12.5a10 10 0 0114 0M8.5 16a5 5 0 017 0M12 19.5h.01M2 9a14 14 0 0120 0",
  shield: "M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6l8-3z",
  hat: "M4 17h16M7 17V9a5 5 0 0110 0v8M9 6h6",
  wings: "M12 8v10M12 9C9 5 4 4 2 5c1 4 3 8 10 9M12 9c3-4 8-5 10-4-1 4-3 8-10 9",
  search: "M11 18a7 7 0 100-14 7 7 0 000 14zM21 21l-5-5",
  cloak: "M8 3h8l3 18H5L8 3zM10 3v4M14 3v4",
  download: "M12 4v12M7 11l5 5 5-5M4 20h16",
  note: "M5 4h14v16H5zM9 8h6M9 12h6M9 16h3",
  edit: "M4 20h4L20 8l-4-4L4 16v4z",
  crown: "M3 18h18M4 18L3 7l5 4 4-6 4 6 5-4-1 11",
  box: "M21 8l-9-5-9 5v8l9 5 9-5V8zM3 8l9 5 9-5M12 13v8",
  key: "M15 7a4 4 0 11-3.5 6L5 19.5V22H2v-3h2v-2h2l3.5-3.5A4 4 0 0115 7zM16 9h.01",
};
function icon(name) {
  const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
  svg.setAttribute("viewBox", "0 0 24 24");
  svg.setAttribute("fill", "none");
  svg.setAttribute("stroke", "currentColor");
  svg.setAttribute("stroke-width", "1.8");
  svg.setAttribute("stroke-linecap", "round");
  svg.setAttribute("stroke-linejoin", "round");
  svg.setAttribute("aria-hidden", "true");
  const p = document.createElementNS("http://www.w3.org/2000/svg", "path");
  p.setAttribute("d", ICONS[name] || ICONS.list);
  svg.append(p);
  return svg;
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
  if (r.status === 413) throw new Error("That's too big for the panel's web server. Raise client_max_body_size in nginx (see the README).");
  const j = await r.json().catch(() => ({}));
  if (!r.ok || j.ok === false) throw new Error(j.error || "Something went wrong (" + r.status + ").");
  return j;
}

const fmt = (n) => (n == null ? "–" : Number(n).toLocaleString());
const when = (t) => (t ? new Date(t * 1000).toLocaleString() : "never");
function ago(t) {
  if (!t) return "never";
  const s = Math.max(0, Date.now() / 1000 - t);
  if (s < 60) return "just now";
  if (s < 3600) return Math.floor(s / 60) + " min ago";
  if (s < 86400) return Math.floor(s / 3600) + " h ago";
  if (s < 86400 * 30) return Math.floor(s / 86400) + " d ago";
  return new Date(t * 1000).toLocaleDateString();
}
const coinName = () => (info.server && info.server.currency) || "PlebMc Coins";
const isAdmin = () => me && (me.role === "admin" || me.role === "owner");
const isOwner = () => me && me.role === "owner";
function params() {
  const q = location.hash.split("?")[1] || "";
  return Object.fromEntries(new URLSearchParams(q));
}
function message(box, text, good) {
  box.replaceChildren();
  if (text) box.append(h("div", { class: "msg " + (good ? "good" : "bad"), role: "status" }, text));
}
let toastTimer;
function toast(text, good = true) {
  document.querySelectorAll(".toast").forEach((t) => t.remove());
  const t = h("div", { class: "toast " + (good ? "good" : "bad"), role: "status" }, text);
  document.body.append(t);
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => t.remove(), 4000);
}
function field(label, attrs) {
  const input = attrs.rows ? h("textarea", attrs) : h("input", attrs);
  return [h("label", { for: attrs.id }, label), input];
}
function select(id, options, value) {
  return h("select", { id, name: id }, options.map(([v, t]) => h("option", { value: v, selected: v === value }, t)));
}
function rolePill(role) {
  return role && role !== "player" ? h("span", { class: "pill " + role }, role) : null;
}
// a little colored square with the first letter, so lists don't need images from other sites
function initial(name) {
  let hash = 0;
  for (const c of String(name || "?")) hash = (hash * 31 + c.charCodeAt(0)) >>> 0;
  const el = h("span", { class: "face", "aria-hidden": "true" }, String(name || "?").charAt(0).toUpperCase());
  el.style.cssText = "display:inline-grid;place-items:center;font-weight:700;font-size:12px;color:#fff;background:hsl(" + (hash % 360) + " 45% 38%)";
  return el;
}
function tile(iconName, label, value, sub) {
  return h("div", { class: "tile" }, h("div", { class: "k" }, icon(iconName), label), h("div", { class: "v" }, value), sub ? h("div", { class: "s" }, sub) : null);
}
function empty(iconName, text) {
  return h("div", { class: "empty" }, icon(iconName), text);
}
function card(title, ...kids) {
  return h("div", { class: "card" }, title ? h("div", { class: "card-head" }, typeof title === "string" ? h("h2", {}, title) : title) : null, ...kids);
}
async function catalog() {
  if (!catalogCache) catalogCache = (await api("shop")).catalog || [];
  return catalogCache;
}

/**
 * A list that loads one page at a time. load(offset) returns {items, total, more};
 * render(item) makes a row. Returns the element to put on the page and a reset function.
 */
function pager(container, load, render, opts = {}) {
  const more = h("div", { class: "more" });
  let offset = 0;
  let shown = 0;
  let busy = false;
  let token = 0;
  const btn = h("button", { onclick: () => next() }, "Show more");
  const count = h("small");
  async function next() {
    if (busy) return;
    busy = true;
    btn.disabled = true;
    btn.textContent = "Loading…";
    const my = token;
    try {
      const r = await load(offset);
      if (my !== token) return;
      for (const it of r.items) container.append(render(it));
      offset = r.nextOffset != null ? r.nextOffset : offset + r.items.length;
      shown += r.items.length;
      if (!shown) container.append(opts.empty ? opts.empty() : empty("list", "Nothing here yet."));
      more.replaceChildren();
      count.textContent = r.total != null ? "Showing " + fmt(shown) + " of " + fmt(r.total) : "";
      if (r.more) more.append(btn);
      if (count.textContent) more.append(count);
    } catch (e) {
      more.replaceChildren(h("div", { class: "msg bad" }, e.message));
    } finally {
      busy = false;
      btn.disabled = false;
      btn.textContent = "Show more";
    }
  }
  function reset() {
    token++;
    offset = 0;
    shown = 0;
    busy = false;
    container.replaceChildren();
    more.replaceChildren();
    next();
  }
  return { more, reset, next };
}

// ==================== shell ====================

const NAV = [
  ["General", [["home", "Home", "home"], ["downloads", "Downloads", "download"], ["leaderboard", "Leaderboard", "trophy"], ["shop", "Shop", "bag"]]],
  ["You", [["me", "My account", "user", true]]],
  ["Admin", [["admin", "Dashboard", "grid"], ["players", "Players", "users"], ["ranks", "Ranks", "crown"], ["announce", "Announcements", "megaphone"], ["makers", "Cosmetic makers", "hammer"], ["publish", "Publish to shop", "upload"], ["builds", "Builds", "box"], ["client", "Client updates", "refresh"], ["audit", "Admin log", "list"]], "admin"],
];

function setNav(view) {
  nav.replaceChildren();
  for (const [group, links, need] of NAV) {
    if (need === "admin" && !isAdmin()) continue;
    const g = h("div", { class: "navgroup" }, h("span", {}, group));
    for (const [id, label, ic, signedIn] of links) {
      if (signedIn && !me) continue;
      g.append(h("a", { href: "#" + id, class: view === id || (view === "player" && id === "players") || (view === "rank" && id === "ranks") ? "on" : null }, icon(ic), label));
    }
    if (g.children.length > 1) nav.append(g); // no empty group (like "You" when signed out)
  }
  if (!me) nav.append(h("div", { class: "navgroup" }, h("span", {}, "Account"), h("a", { href: "#login", class: view === "login" ? "on" : null }, icon("user"), "Sign in"),
    h("a", { href: "#register", class: view === "register" ? "on" : null }, icon("plus"), "Create account")));
  who.replaceChildren();
  if (me) {
    who.append(h("a", { href: "#me", class: "who" }, initial(me.name), h("span", { class: "name" }, me.name, " "), rolePill(me.role)),
      h("button", { class: "ghost small", onclick: logout, title: "Sign out" }, icon("logout")));
  } else {
    who.append(h("a", { class: "btn small", href: "#login" }, "Sign in"), h("a", { class: "btn primary small", href: "#register" }, "Create account"));
  }
}

const menuBtn = document.getElementById("menu");
const side = document.getElementById("side");
menuBtn.addEventListener("click", () => {
  const open = side.classList.toggle("open");
  menuBtn.setAttribute("aria-expanded", String(open));
});

async function logout() {
  await api("logout", {}).catch(() => {});
  me = null;
  location.hash = "#home";
  route();
}

function announcementBanner() {
  const a = info.announcement;
  if (!a || !a.text) return null;
  return h("div", { class: "banner" }, icon("megaphone"), h("span", {}, a.text), h("small", {}, ago(a.at)));
}

// ==================== home ====================

async function homeView() {
  const s = info.server || {};
  const c = info.client || {};
  const wrap = h("div");
  wrap.append(announcementBanner() || "");
  wrap.append(h("div", { class: "card hero" }, h("img", { src: "/static/logo.png", alt: "" }),
    h("div", {}, h("h1", {}, info.site), h("p", { class: "muted" }, me ? "Welcome back, " + me.name + "." : "Your account, your coins and cosmetics, the shop and the leaderboard."),
      h("div", { class: "row" },
        me ? h("a", { class: "btn primary", href: "#me" }, icon("user"), "My account") : h("a", { class: "btn primary", href: "#register" }, icon("plus"), "Create account"),
        me ? null : h("a", { class: "btn", href: "#login" }, "Sign in"),
        isAdmin() ? h("a", { class: "btn", href: "#makers" }, icon("hammer"), "Cosmetic makers") : null,
        (info.downloads || []).length ? h("a", { class: "btn", href: "#downloads" }, icon("download"), "Get PlebClient" + (c.latest ? " " + c.latest : ""))
          : c.url ? h("a", { class: "btn", href: c.url, target: "_blank", rel: "noopener" }, icon("download"), "Get PlebClient" + (c.latest ? " " + c.latest : "")) : null))));
  wrap.append(h("div", { class: "tiles" },
    tile("online", "Server", h("span", {}, h("span", { class: "dot" + (s.connected ? " on" : "") }), s.connected ? "Online" : "Offline"), s.connected ? fmt(s.online) + " playing now" : "The panel can't see the server right now"),
    tile("refresh", "Latest PlebClient", c.latest || "–", c.minimum ? "Oldest allowed: " + c.minimum : "Your client tells you when it's out of date"),
    tile("coin", "Currency", coinName(), "Earn it by playing, spend it in the shop")));
  const board = h("tbody");
  wrap.append(card(h("div", { class: "row", style: "width:100%" }, h("h2", {}, "Richest players"), h("span", { class: "spacer" }), h("a", { href: "#leaderboard" }, "Top 10 and search")),
    h("div", { class: "scroll" }, h("table", {}, h("tbody", {}, board)))));
  api("leaderboard?limit=5").then((r) => {
    if (!r.rows.length) board.append(h("tr", {}, h("td", { class: "muted" }, "Nobody yet.")));
    for (const p of r.rows) board.append(leaderRow(p));
  }).catch((e) => board.append(h("tr", {}, h("td", { class: "muted" }, e.message))));
  return wrap;
}

function leaderRow(p) {
  return h("tr", {}, h("td", { style: "width:40px" }, h("span", { class: "rank" + (p.rank <= 3 ? " r" + p.rank : "") }, p.rank)),
    h("td", {}, initial(p.name), p.name), h("td", { class: "num" }, h("b", {}, fmt(p.coins)), " ", h("span", { class: "muted" }, coinName())),
    h("td", { class: "num muted" }, fmt(p.items) + " items"), h("td", { class: "num muted" }, fmt(p.streak) + " day streak"));
}

async function leaderboardView() {
  const top = h("tbody");
  const found = h("tbody");
  const q = h("input", { type: "search", placeholder: "Find a player", "aria-label": "Find a player", maxlength: 16, spellcheck: "false" });
  const foundCard = card("Search", h("div", { class: "scroll" }, h("table", {}, found)));
  foundCard.hidden = true;
  const fill = (body, rows, none) => {
    body.replaceChildren(...(rows.length ? rows.map(leaderRow) : [h("tr", {}, h("td", { class: "muted" }, none))]));
  };
  api("leaderboard?limit=10").then((r) => fill(top, r.rows, "Nobody has coins yet.")).catch((e) => fill(top, [], e.message));
  let timer;
  const search = () => {
    const v = q.value.trim();
    if (!v) { foundCard.hidden = true; return; }
    api("leaderboard?q=" + encodeURIComponent(v) + "&limit=25").then((r) => {
      if (q.value.trim() !== v) return;
      foundCard.hidden = false;
      fill(found, r.rows, "No player called " + v + ".");
    }).catch((e) => { foundCard.hidden = false; fill(found, [], e.message); });
  };
  q.addEventListener("input", () => { clearTimeout(timer); timer = setTimeout(search, 250); });
  return h("div", {},
    card(h("div", { class: "row", style: "width:100%" }, h("h2", {}, "Top 10"), h("span", { class: "spacer" }), h("label", { class: "searchbox" }, icon("search"), q)),
      h("p", { class: "muted" }, "The richest players by " + coinName() + ". Search to see anyone's rank."), h("div", { class: "scroll" }, h("table", {}, top))),
    h("div", { style: "height:18px" }), foundCard);
}

async function shopView() {
  const items = await catalog();
  const q = h("input", { placeholder: "Search the shop", "aria-label": "Search the shop" });
  let cat = "all";
  const cats = ["all", ...new Set(items.map((i) => i.category))];
  const chips = h("div", { class: "chips" });
  const grid = h("div", { class: "items" });
  const draw = () => {
    chips.replaceChildren(...cats.map((c) => h("button", { class: "chip", "aria-pressed": String(c === cat), onclick: () => { cat = c; draw(); } }, c === "all" ? "Everything" : c)));
    const t = q.value.trim().toLowerCase();
    const list = items.filter((i) => (cat === "all" || i.category === cat) && (!t || i.name.toLowerCase().includes(t) || i.id.includes(t)));
    grid.replaceChildren(...list.map((i) => h("div", { class: "item" }, h("b", {}, i.name), h("small", {}, i.category),
      h("span", { class: "price" }, i.price == null || i.price < 0 ? "Not for sale" : fmt(i.price) + " " + coinName()))));
    if (!list.length) grid.append(empty("bag", items.length ? "Nothing matches." : "The shop shows up once the server connects."));
  };
  q.addEventListener("input", draw);
  draw();
  return card("Shop", h("p", { class: "muted" }, "Buy these in the PlebClient shop (Right Shift > Shop)."), h("div", { class: "row" }, q), h("div", { style: "height:10px" }), chips, h("div", { style: "height:14px" }), grid);
}

// ==================== designs ====================

function toolUrl(kind, id) {
  // hats and wings are both made in the Cosmetic Maker (hat.html)
  if (kind === "wings") return "/static/tools/hat.html?" + (id ? "design=" + id : "mode=wings");
  return "/static/tools/" + kind + ".html" + (id ? "?design=" + id : "");
}

function designCard(d, opts = {}) {
  const pic = h("div", { class: "pic" }, d.thumb ? h("img", { src: d.thumb, alt: "" }) : icon(d.kind));
  const actions = h("div", { class: "row" });
  actions.append(h("a", { class: "btn small", href: toolUrl(d.kind, d.id) }, "Open"));
  if (d.mine || (opts.admin && isAdmin())) {
    actions.append(h("button", { class: "small danger", title: "Delete", onclick: async () => {
      if (!confirm("Delete " + d.name + "?")) return;
      try { await api("designs/delete", { id: d.id }); el.remove(); toast("Deleted."); } catch (e) { toast(e.message, false); }
    } }, icon("trash")));
  }
  const el = h("div", { class: "design" }, pic, h("div", { class: "body" }, h("b", {}, d.name),
    h("small", { class: "muted" }, d.kind + (d.author ? " by " + d.author : "") + " · " + ago(d.updated)),
    h("div", { class: "row", style: "gap:6px" }, d.published ? h("span", { class: "pill admin" }, "in shop") : null),
    actions));
  return el;
}

async function makersView() {
  const designs = h("div", { class: "designs" });
  api("designs/mine").then((d) => {
    if (!d.designs.length) designs.replaceWith(empty("image", "Nothing saved yet. Make something and press Save to my account."));
    for (const x of d.designs) designs.append(designCard(x, { mine: true }));
  }).catch((e) => message(designs, e.message));
  return h("div", {},
    h("div", { class: "tools" },
      h("a", { class: "tool-card", href: toolUrl("hat") }, icon("hat"), h("h2", {}, "Cosmetic Maker: hats"),
        h("span", { class: "muted" }, "Build a hat out of blocks right on a Minecraft head. Templates, mirror, recolor, undo, and a Blockbench export.")),
      h("a", { class: "tool-card", href: toolUrl("wings") }, icon("wings"), h("h2", {}, "Cosmetic Maker: wings"),
        h("span", { class: "muted" }, "Build wings on a player's back. You build one side, the other is mirrored, and they flap in game.")),
      h("a", { class: "tool-card", href: toolUrl("cloak") }, icon("cloak"), h("h2", {}, "Cloak Maker"),
        h("span", { class: "muted" }, "Turn a GIF, frames or a picture into an animated cloak, or use a generated effect. Live 3D preview."))),
    h("div", { style: "height:18px" }),
    card("Your designs", h("p", { class: "muted" }, "Only admins can use the makers. Save up to 30 designs, then publish them to the shop with a price."), designs));
}

// ==================== sign in / register ====================

function loginView() {
  const out = h("div");
  const btn = h("button", { class: "primary full", type: "submit" }, "Sign in");
  const form = h("form", { class: "card narrow", onsubmit: async (e) => {
    e.preventDefault();
    btn.disabled = true;
    try {
      const r = await api("login", { name: form.elements.namedItem("name").value.trim(), password: form.elements.namedItem("password").value });
      me = r.account;
      const next = params().next || "#me";
      // back to a maker page after signing in from it
      if (next.startsWith("/static/tools/")) location.href = next;
      else location.hash = next.startsWith("#") ? next : "#me";
    } catch (err) {
      message(out, err.message);
    }
    btn.disabled = false;
  } },
  h("div", { class: "center" }, h("img", { src: "/static/logo.png", alt: "", style: "width:64px;height:64px;image-rendering:pixelated" })),
  h("h1", { class: "center" }, "Sign in"),
  h("p", { class: "muted center" }, "Your Minecraft name and your " + info.site + " password."),
  field("Minecraft name", { id: "name", name: "name", autocomplete: "username", required: true, maxlength: 16 }),
  field("Password", { id: "password", name: "password", type: "password", autocomplete: "current-password", required: true }),
  out, btn, h("p", { class: "muted center", style: "margin-top:14px" }, "No account yet? ", h("a", { href: "#register" }, "Create one")));
  return form;
}

function registerView() {
  const p = params();
  const out = h("div");
  const btn = h("button", { class: "primary full", type: "submit" }, "Create account");
  const form = h("form", { class: "card narrow", onsubmit: async (e) => {
    e.preventDefault();
    if (form.elements.namedItem("password").value !== form.elements.namedItem("password2").value) return message(out, "The passwords don't match.");
    btn.disabled = true;
    try {
      const code = form.elements.namedItem("code");
      const r = await api("register", { name: form.elements.namedItem("name").value.trim(), code: code ? code.value : "", password: form.elements.namedItem("password").value });
      me = r.account;
      location.hash = "#me";
    } catch (err) {
      message(out, err.message);
    }
    btn.disabled = false;
  } },
  h("div", { class: "center" }, h("img", { src: "/static/logo.png", alt: "", style: "width:64px;height:64px;image-rendering:pixelated" })),
  h("h1", { class: "center" }, "Create your account"),
  info.openRegistration ? h("p", { class: "muted center" }, "Pick the name you play as, then sign in with it on the PlebClient start screen.") :
    h("div", {}, h("p", { class: "muted" }, "Your account belongs to the name you play with. To prove it's you:"),
      h("ol", { class: "muted" }, h("li", {}, "Join the server with your name"), h("li", {}, "Type ", h("code", {}, "/account code"), " in chat"), h("li", {}, "Type the code here"))),
  field("Minecraft name", { id: "name", name: "name", required: true, maxlength: 16, value: p.name || "", autocomplete: "username" }),
  info.openRegistration ? null : field("Code from the game", { id: "code", name: "code", required: true, value: p.code || "", placeholder: "ABCD-1234", autocomplete: "off" }),
  field("Password (8+ characters)", { id: "password", name: "password", type: "password", required: true, minlength: 8, autocomplete: "new-password" }),
  field("Password again", { id: "password2", name: "password2", type: "password", required: true, minlength: 8, autocomplete: "new-password" }),
  out, btn, h("p", { class: "muted center", style: "margin-top:14px" }, "Already have one? ", h("a", { href: "#login" }, "Sign in")));
  return form;
}

// ==================== my account ====================

function itemList(owned, equipped, cat) {
  const byId = Object.fromEntries((cat || []).map((i) => [i.id, i]));
  const worn = new Set(Object.values(equipped || {}));
  if (!owned || !owned.length) return empty("bag", "No cosmetics yet. Buy them in the PlebClient shop (Right Shift).");
  return h("div", { class: "items" }, owned.map((id) => {
    const it = byId[id] || { name: id, category: "" };
    return h("div", { class: "item" + (worn.has(id) ? " worn" : "") }, h("b", {}, it.name), h("small", {}, it.category + (worn.has(id) ? " · wearing" : "")));
  }));
}

async function meView() {
  const r = await api("me");
  if (!r.account) { location.hash = "#login?next=" + encodeURIComponent("#me"); return loginView(); }
  me = r.account;
  const p = r.player;
  const wrap = h("div");
  wrap.append(announcementBanner() || "");
  wrap.append(h("div", { class: "card hero" }, initialBig(me.name),
    h("div", {}, h("h1", {}, me.name, " ", rolePill(me.role)),
      h("p", { class: "muted" }, h("span", { class: "dot" + (r.online ? " on" : "") }), r.online ? "Online now" : "Last signed in " + ago(me.lastLogin)),
      p ? null : h("p", { class: "muted" }, "Join the server once and your coins show up here."))));
  wrap.append(h("div", { class: "tiles" },
    tile("coin", coinName(), fmt(p ? p.coins : 0)),
    tile("bag", "Cosmetics", fmt(p ? p.owned.length : 0)),
    tile("trophy", "Login streak", fmt(p ? p.streak : 0) + " days"),
    tile("shield", "Remembered clients", fmt(r.devices))));
  wrap.append(card("Your cosmetics", itemList(p && p.owned, p && p.equipped, r.catalog)));
  wrap.append(h("div", { class: "grid g2" }, passwordCard(), signoutCard()));
  return wrap;
}

function initialBig(name) {
  const el = initial(name);
  el.style.width = el.style.height = "88px";
  el.style.fontSize = "40px";
  el.style.borderRadius = "20px";
  el.style.margin = "0";
  return el;
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
  field("Current password", { id: "old", name: "old", type: "password", required: true, autocomplete: "current-password" }),
  field("New password", { id: "npw", name: "password", type: "password", required: true, minlength: 8, autocomplete: "new-password" }),
  field("New password again", { id: "npw2", name: "password2", type: "password", required: true, minlength: 8, autocomplete: "new-password" }),
  h("div", { style: "height:14px" }), h("button", { class: "primary", type: "submit" }, "Change password"), out);
  return form;
}

function signoutCard() {
  const out = h("div");
  return h("div", { class: "card" }, h("h2", {}, "Sign out everywhere"),
    h("p", { class: "muted" }, "Forgets every remembered game client and signs you out in game. Use this if someone else might know your password."),
    h("button", { class: "danger", onclick: async () => {
      try { await api("me/signout", {}); message(out, "Done. Sign in again in game.", true); } catch (err) { message(out, err.message); }
    } }, icon("logout"), "Sign out everywhere"), out);
}

// ==================== admin: dashboard ====================

async function dashboardView() {
  const d = await api("admin/dashboard");
  const s = d.server;
  const wrap = h("div");
  if (!s.connected) wrap.append(h("div", { class: "banner update-bar" }, icon("online"), h("span", {}, "The proxy isn't connected (last sync " + ago(s.lastSync) + "). Check accounts: in PlebioAPI's config.yml.")));
  wrap.append(h("div", { class: "tiles" },
    tile("online", "Online now", fmt(s.online), s.connected ? "Last sync " + ago(s.lastSync) : "Proxy offline"),
    tile("users", "Accounts", fmt(d.accounts), "+" + fmt(d.newDay) + " today · +" + fmt(d.newWeek) + " this week"),
    tile("user", "Active players", fmt(d.activeDay), fmt(d.activeWeek) + " this week"),
    tile("trophy", "Players seen", fmt(d.players), fmt(d.staff) + " staff · " + fmt(d.locked) + " locked"),
    tile("coin", coinName() + " in circulation", fmt(d.coins), "Richest has " + fmt(d.richest)),
    tile("image", "Designs saved", fmt(d.designs))));
  const max = Math.max(1, ...d.signups.map((x) => x.count));
  const bars = h("div", { class: "bars" }, d.signups.map((x) => {
    const b = h("div", { title: new Date(x.day * 1000).toLocaleDateString() + ": " + x.count + " new" });
    b.style.height = Math.max(2, (x.count / max) * 100) + "%";
    return b;
  }));
  const labels = h("div", { class: "bar-labels" }, d.signups.map((x, i) => h("span", {}, i % 2 ? "" : new Date(x.day * 1000).toLocaleDateString(undefined, { month: "short", day: "numeric" }))));
  const pmax = Math.max(1, ...d.popular.map((x) => x.owners));
  const popular = d.popular.length ? h("div", { class: "list" }, d.popular.map((x) => {
    const m = h("i");
    m.style.width = (x.owners / pmax) * 100 + "%";
    return h("div", { class: "li" }, h("span", {}, x.name, " ", h("small", { class: "muted" }, x.category)), h("b", {}, fmt(x.owners)), h("div", { class: "meter" }, m));
  })) : empty("bag", "Nobody owns anything yet.");
  const vtotal = d.clients.reduce((a, x) => a + x.n, 0) || 1;
  const latest = (info.client || {}).latest;
  const clients = d.clients.length ? h("div", { class: "list" }, d.clients.slice(0, 8).map((x) => {
    const m = h("i");
    m.style.width = (x.n / vtotal) * 100 + "%";
    return h("div", { class: "li" }, h("span", {}, "PlebClient " + x.version, " ", h("small", { class: "muted" }, x.platform), " ",
      latest && olderVersion(x.version, latest) ? h("span", { class: "pill warn" }, "outdated") : null), h("b", {}, fmt(x.n)), h("div", { class: "meter" }, m));
  })) : empty("refresh", "No client has checked in yet (clients from this update on do).");
  wrap.append(h("div", { class: "grid g2" },
    card("New accounts, last 14 days", bars, labels),
    card("Most owned cosmetics", popular)));
  wrap.append(h("div", { style: "height:18px" }));
  wrap.append(h("div", { class: "grid g2" },
    card(h("div", { class: "row", style: "width:100%" }, h("h2", {}, "Clients in use (7 days)"), h("span", { class: "spacer" }), h("a", { href: "#client" }, "Update settings")), clients),
    card("Quick actions", h("div", { class: "row" },
      h("a", { class: "btn", href: "#players" }, icon("users"), "Players"),
      h("a", { class: "btn", href: "#announce" }, icon("megaphone"), "Announce"),
      h("a", { class: "btn", href: "#publish" }, icon("upload"), "Publish to shop"),
      h("a", { class: "btn", href: "#builds" }, icon("box"), "Builds"),
      h("a", { class: "btn", href: "#makers" }, icon("hammer"), "Cosmetic makers"),
      h("a", { class: "btn", href: "#audit" }, icon("list"), "Admin log")))));
  wrap.append(h("div", { style: "height:18px" }));
  wrap.append(h("div", { class: "grid g2" }, giveAllCard(), liveBuildsCard()));
  return wrap;
}

function giveAllCard() {
  const out = h("div");
  const amount = h("input", { type: "number", min: 1, step: 50, value: 100, "aria-label": "Amount", style: "max-width:140px" });
  const btn = h("button", { class: "primary", onclick: async () => {
    const n = Math.floor(Number(amount.value));
    if (!(n > 0)) return message(out, "Give a whole number above 0.");
    if (!confirm("Give " + fmt(n) + " " + coinName() + " to everyone online right now?")) return;
    btn.disabled = true;
    try {
      await api("admin/command", { action: "coinsall", amount: n });
      message(out, "Sent. Everyone online gets " + fmt(n) + " " + coinName() + " within a few seconds.", true);
    } catch (e) { message(out, e.message); }
    btn.disabled = false;
  } }, icon("coin"), "Give to everyone online");
  return card("Give coins to everyone online", h("p", { class: "muted" }, "A gift for everyone playing right now (players on PlebClient when plebclient-only is on)."),
    h("div", { class: "row" }, amount, btn), out);
}

// ==================== admin: players ====================

async function playersView() {
  const p0 = params();
  const state = { q: p0.q || "", filter: p0.filter || "all", sort: p0.sort || "coins" };
  const status = h("p", { class: "muted" });
  const search = h("input", { placeholder: "Search by name", "aria-label": "Search players", value: state.q, type: "search" });
  const sortSel = select("sort", [["coins", "Most coins"], ["recent", "Recently seen"], ["new", "Newest accounts"], ["name", "Name A-Z"]], state.sort);
  const chips = h("div", { class: "chips" });
  const body = h("tbody");
  let first = true;
  const pg = pager(body, async (offset) => {
    const r = await api("admin/players?q=" + encodeURIComponent(state.q) + "&filter=" + state.filter + "&sort=" + state.sort + "&offset=" + offset);
    if (first || offset === 0) {
      const s = r.server;
      status.replaceChildren(h("span", { class: "dot" + (s.connected ? " on" : "") }),
        s.connected ? "Proxy connected · " + s.online + " online · last sync " + ago(s.lastSync) : "Proxy not connected (last sync " + ago(s.lastSync) + ").");
      first = false;
    }
    return { items: r.players, total: r.total, more: r.more };
  }, (p) => h("tr", { class: "click", tabindex: "0", onclick: () => { location.hash = "#player?name=" + encodeURIComponent(p.name); },
    onkeydown: (e) => { if (e.key === "Enter") location.hash = "#player?name=" + encodeURIComponent(p.name); } },
  h("td", {}, initial(p.name), p.online ? h("span", { class: "dot on", title: "Online" }) : null, p.name, " ", rolePill(p.role)),
  h("td", { class: "num" }, fmt(p.coins)), h("td", { class: "num" }, fmt(p.items)),
  h("td", {}, p.locked ? h("span", { class: "pill locked" }, "locked") : p.account ? h("span", { class: "pill good" }, "yes") : h("span", { class: "muted" }, "none")),
  h("td", { class: "muted" }, ago(p.lastSeen))), { empty: () => h("tr", {}, h("td", { class: "muted", colspan: "5" }, "Nobody found.")) });
  const keep = () => history.replaceState(null, "", "#players?" + new URLSearchParams(state).toString());
  const drawChips = () => chips.replaceChildren(...[["all", "Everyone"], ["online", "Online"], ["accounts", "Has account"], ["noaccount", "No account"], ["staff", "Staff"], ["locked", "Locked"]]
    .map(([k, t]) => h("button", { class: "chip", "aria-pressed": String(k === state.filter), onclick: () => { state.filter = k; drawChips(); keep(); pg.reset(); } }, t)));
  drawChips();
  let timer;
  search.addEventListener("input", () => { clearTimeout(timer); timer = setTimeout(() => { state.q = search.value.trim(); keep(); pg.reset(); }, 250); });
  sortSel.addEventListener("change", () => { state.sort = sortSel.value; keep(); pg.reset(); });
  pg.reset();
  return h("div", {}, card("Players", status, h("div", { class: "row" }, search, sortSel), h("div", { style: "height:10px" }), chips, h("div", { style: "height:10px" }),
    h("div", { class: "scroll" }, h("table", {}, h("thead", {}, h("tr", {}, h("th", {}, "Player"), h("th", { class: "num" }, coinName()), h("th", { class: "num" }, "Items"), h("th", {}, "Account"), h("th", {}, "Last seen"))), body)), pg.more),
  createCard(() => pg.reset()));
}

function createCard(after) {
  const out = h("div");
  const form = h("form", { class: "card", onsubmit: async (e) => {
    e.preventDefault();
    const name = form.elements.namedItem("name").value.trim();
    try {
      await api("admin/account", { action: "create", name, password: form.elements.namedItem("password").value, role: form.elements.namedItem("role") ? form.elements.namedItem("role").value : "player" });
      message(out, "Created " + name + ".", true);
      form.reset();
      after();
    } catch (err) { message(out, err.message); }
  } }, h("h2", {}, "Create an account for a player"),
  h("div", { class: "grid g3" },
    h("div", {}, field("Minecraft name", { id: "cname", name: "name", required: true, maxlength: 16 })),
    h("div", {}, field("Password", { id: "cpw", name: "password", type: "password", required: true, minlength: 8, autocomplete: "new-password" })),
    isOwner() ? h("div", {}, h("label", { for: "role" }, "Role"), select("role", ROLES.map((r) => [r, r]), "player")) : h("div")),
  h("div", { style: "height:14px" }), h("button", { class: "primary", type: "submit" }, icon("plus"), "Create"), out);
  return form;
}
const ROLES = ["player", "admin", "owner"];

async function playerView() {
  const name = params().name || "";
  const r = await api("admin/player?name=" + encodeURIComponent(name));
  const p = r.player;
  const acc = r.account;
  const out = h("div");
  const done = (text) => { toast(text); setTimeout(route, 1200); };
  const act = (body) => api("admin/command", { name: r.name, ...body }).then(() => done("Queued. The server applies it within a few seconds.")).catch((e) => toast(e.message, false));
  const accAct = (body, confirmText) => {
    if (confirmText && !confirm(confirmText)) return;
    api("admin/account", { name: r.name, ...body }).then(() => done("Done.")).catch((e) => toast(e.message, false));
  };
  const wrap = h("div", {}, h("p", {}, h("a", { href: "#players" }, "← Players")));
  wrap.append(h("div", { class: "card hero" }, initialBig(r.name),
    h("div", {}, h("h1", {}, r.name, " ", rolePill(acc && acc.role), acc && acc.locked ? h("span", { class: "pill locked" }, "locked") : null),
      h("p", { class: "muted" }, h("span", { class: "dot" + (r.online ? " on" : "") }), r.online ? "Online now" : "Offline",
        acc ? " · account since " + when(acc.created) + " · last sign-in " + ago(acc.lastLogin) + " · " + r.devices + " remembered client(s)" : " · no account yet"),
      out)));
  wrap.append(h("div", { class: "tiles" },
    tile("coin", coinName(), fmt(p ? p.coins : 0)),
    tile("bag", "Cosmetics", fmt(p ? p.owned.length : 0)),
    tile("trophy", "Login streak", fmt(p ? p.streak : 0))));

  const amount = h("input", { type: "number", min: 0, value: 100, "aria-label": "Amount", style: "max-width:160px" });
  const quick = [100, 1000, 10000].map((n) => h("button", { class: "small ghost", onclick: () => { amount.value = n; } }, "+" + fmt(n)));
  const coins = card(coinName(), h("div", { class: "row" }, amount, ...quick), h("div", { style: "height:10px" }), h("div", { class: "row" },
    h("button", { class: "primary", onclick: () => act({ action: "coins", op: "give", amount: amount.value }) }, "Give"),
    h("button", { onclick: () => act({ action: "coins", op: "take", amount: amount.value }) }, "Take"),
    h("button", { onclick: () => act({ action: "coins", op: "set", amount: amount.value }) }, "Set to")));

  const cat = r.catalog || [];
  const itemSel = h("select", { "aria-label": "Item" }, cat.map((i) => h("option", { value: i.id }, i.name + " (" + i.category + ")")));
  const cos = card("Cosmetics", itemList(p && p.owned, p && p.equipped, cat), h("div", { style: "height:12px" }),
    cat.length ? h("div", { class: "row" }, itemSel,
      h("button", { onclick: () => act({ action: "give", item: itemSel.value }) }, "Give"),
      h("button", { onclick: () => act({ action: "remove", item: itemSel.value }) }, "Remove"),
      h("button", { onclick: () => { const it = cat.find((i) => i.id === itemSel.value); act({ action: "equip", category: it.category, item: it.id }); } }, "Put on"))
      : h("p", { class: "muted" }, "The item list appears after the proxy connects."));
  wrap.append(h("div", { class: "grid g2" }, coins, cos));
  wrap.append(h("div", { style: "height:18px" }));
  wrap.append(await playerRankCard(r.name, r.rank));
  wrap.append(h("div", { style: "height:18px" }));

  const reason = h("input", { placeholder: "Kick reason", value: "Kicked by an admin." });
  const mod = card("Moderation", h("div", { class: "row" }, reason, h("button", { class: "danger", onclick: () => act({ action: "kick", reason: reason.value }) }, "Kick")));
  if (acc) {
    const npw = h("input", { type: "password", placeholder: "New password (8+)", autocomplete: "new-password" });
    mod.append(h("div", { style: "height:12px" }), h("div", { class: "row" }, npw, h("button", { onclick: () => accAct({ action: "password", password: npw.value }) }, "Reset password")),
      h("div", { style: "height:12px" }),
      h("div", { class: "row" },
        h("button", { onclick: () => accAct({ action: "signout" }) }, "Sign out everywhere"),
        acc.locked ? h("button", { onclick: () => accAct({ action: "unlock" }) }, "Unlock") : h("button", { class: "danger", onclick: () => accAct({ action: "lock" }, "Lock " + r.name + "? They can't sign in until you unlock them.") }, "Lock account"),
        h("button", { class: "danger", onclick: () => accAct({ action: "delete" }, "Delete " + r.name + "'s account? Their coins stay, but anyone can then register the name.") }, "Delete account")));
    if (isOwner()) {
      const roleSel = h("select", { "aria-label": "Role" }, ROLES.map((x) => h("option", { value: x, selected: x === acc.role }, x)));
      mod.append(h("div", { style: "height:12px" }), h("div", { class: "row" }, roleSel, h("button", { onclick: () => accAct({ action: "role", role: roleSel.value }) }, "Set role")));
    }
  }

  // staff notes
  const notesBox = h("div", { class: "list" });
  const drawNotes = (notes) => {
    notesBox.replaceChildren();
    if (!notes.length) notesBox.append(h("p", { class: "muted" }, "No notes yet. Notes are only seen by admins."));
    for (const n of notes) {
      notesBox.append(h("div", { class: "note" }, h("small", {}, h("b", {}, n.by_name), ago(n.at), h("span", { class: "spacer" }),
        h("button", { class: "small ghost", title: "Delete note", onclick: async () => {
          try { drawNotes((await api("admin/note", { name: r.name, delete: n.id })).notes); } catch (e) { toast(e.message, false); }
        } }, icon("trash"))), h("p", {}, n.text)));
    }
  };
  drawNotes(r.notes || []);
  const noteText = h("textarea", { placeholder: "Write a note about this player (warnings, appeals, anything)", maxlength: 1000, "aria-label": "Note" });
  const notes = card("Staff notes", h("div", { class: "row" }, noteText), h("div", { style: "height:8px" }),
    h("button", { onclick: async () => {
      try { drawNotes((await api("admin/note", { name: r.name, text: noteText.value })).notes); noteText.value = ""; } catch (e) { toast(e.message, false); }
    } }, icon("note"), "Add note"), h("div", { style: "height:12px" }), notesBox);
  wrap.append(h("div", { class: "grid g2" }, mod, notes));
  wrap.append(h("div", { style: "height:18px" }));
  if (r.commands.length) {
    wrap.append(card("Recent changes", h("div", { class: "scroll" }, h("table", {},
      h("thead", {}, h("tr", {}, h("th", {}, "When"), h("th", {}, "By"), h("th", {}, "Change"), h("th", {}, "Status"))),
      h("tbody", {}, r.commands.map((c) => h("tr", {}, h("td", {}, ago(c.at)), h("td", {}, c.by), h("td", {}, c.action + " " + c.args.join(" ")),
        h("td", {}, statusPill(c.status), c.result ? " " + c.result : ""))))))));
  }
  return wrap;
}

function statusPill(s) {
  return h("span", { class: "pill " + (s === "done" ? "good" : s === "failed" ? "bad" : "warn") }, s);
}

// ==================== admin: LuckPerms ranks ====================

const MC_COLORS = { 0: "#000000", 1: "#0000AA", 2: "#00AA00", 3: "#00AAAA", 4: "#AA0000", 5: "#AA00AA", 6: "#FFAA00", 7: "#AAAAAA",
  8: "#555555", 9: "#5555FF", a: "#55FF55", b: "#55FFFF", c: "#FF5555", d: "#FF55FF", e: "#FFFF55", f: "#FFFFFF" };

/** A prefix like "&#00E5FF&lVIP &7" drawn the way the game shows it. */
function mcText(text, fallback) {
  const out = h("span", { class: "mc" });
  let color = "#FFFFFF", bold = false, italic = false, under = false, strike = false;
  let i = 0;
  const str = String(text || "");
  if (!str) {
    if (fallback === "") return h("span");
    out.append(h("span", { class: "muted" }, fallback || "none"));
    return out;
  }
  let buf = "";
  const flush = () => {
    if (!buf) return;
    const sp = h("span", {}, buf);
    sp.style.color = color;
    if (bold) sp.style.fontWeight = "800";
    if (italic) sp.style.fontStyle = "italic";
    const deco = [under ? "underline" : "", strike ? "line-through" : ""].join(" ").trim();
    if (deco) sp.style.textDecoration = deco;
    out.append(sp);
    buf = "";
  };
  while (i < str.length) {
    const c = str[i];
    if ((c === "&" || c === "\u00a7") && i + 1 < str.length) {
      const hex = /^#([0-9a-fA-F]{6})/.exec(str.slice(i + 1));
      if (hex) { flush(); color = "#" + hex[1]; bold = italic = under = strike = false; i += 8; continue; }
      const k = str[i + 1].toLowerCase();
      if (MC_COLORS[k]) { flush(); color = MC_COLORS[k]; bold = italic = under = strike = false; i += 2; continue; }
      if ("lmnor".includes(k)) {
        flush();
        if (k === "l") bold = true; else if (k === "o") italic = true; else if (k === "n") under = true; else if (k === "m") strike = true;
        else { color = "#FFFFFF"; bold = italic = under = strike = false; }
        i += 2; continue;
      }
      if (k === "k") { i += 2; continue; }
    }
    buf += c;
    i++;
  }
  flush();
  return out;
}

async function rankApi(body, after) {
  try {
    await api("admin/ranks", body);
    toast("Queued. The proxy applies it within a few seconds.");
    setTimeout(after || route, 4500);
  } catch (e) { toast(e.message, false); }
}

function ranksStatus(r) {
  if (r.connected) return null;
  return h("div", { class: "card warnbox" }, h("h2", {}, "LuckPerms isn't connected"),
    h("p", { class: "muted" }, r.reason || "The proxy hasn't sent any ranks yet."),
    h("p", { class: "muted" }, "Install LuckPerms on the BungeeCord proxy (using the same database as your backend servers), then restart the proxy. PlebioAPI finds it on its own."));
}

async function ranksView() {
  const r = await api("admin/ranks");
  const wrap = h("div");
  const st = ranksStatus(r);
  if (st) wrap.append(st);
  const head = h("div", { class: "card-head" }, h("h2", {}, "Ranks"), h("span", { class: "muted" }, r.groups.length + " ranks from LuckPerms"));
  const list = h("div", { class: "scroll" }, h("table", {},
    h("thead", {}, h("tr", {}, h("th", {}, "Rank"), h("th", {}, "Prefix"), h("th", { class: "num" }, "Weight"), h("th", {}, "Inherits"), h("th", { class: "num" }, "Permissions"))),
    h("tbody", {}, r.groups.length ? r.groups.map((g) => h("tr", { class: "link", onclick: () => { location.hash = "#rank?name=" + encodeURIComponent(g.name); } },
      h("td", {}, h("a", { href: "#rank?name=" + encodeURIComponent(g.name) }, h("b", {}, g.display || g.name)), g.display && g.display !== g.name ? h("small", { class: "muted" }, " " + g.name) : null),
      h("td", {}, mcText(g.prefix)),
      h("td", { class: "num" }, String(g.weight)),
      h("td", {}, g.parents.join(", ") || h("span", { class: "muted" }, "-")),
      h("td", { class: "num" }, String(g.perms.length)))) : h("tr", {}, h("td", { class: "muted" }, "No ranks yet.")))));
  const main = card(null, head, list);
  wrap.append(main);
  if (isOwner()) {
    const name = h("input", { placeholder: "new rank name (like vip or mod)", maxlength: 36, "aria-label": "Rank name" });
    const create = card("New rank", h("p", { class: "muted" }, "Lowercase letters, numbers, - and _. You set its prefix, weight and permissions next."),
      h("div", { class: "row" }, name, h("button", { class: "primary", onclick: () => {
        const n = name.value.trim().toLowerCase();
        rankApi({ op: "create", group: n }, () => { location.hash = "#rank?name=" + encodeURIComponent(n); });
      } }, icon("plus"), "Create rank")));
    wrap.append(h("div", { style: "height:18px" }), create);
  } else {
    wrap.append(h("p", { class: "muted" }, "Only owners can change ranks."));
  }
  if (r.tracks && r.tracks.length) {
    wrap.append(h("div", { style: "height:18px" }), card("Tracks", h("div", { class: "list" }, r.tracks.map((t) =>
      h("div", {}, h("b", {}, t.name), " ", h("span", { class: "muted" }, t.groups.join(" → ")))))));
  }
  wrap.append(h("div", { style: "height:18px" }), rankChanges(r.changes));
  return wrap;
}

function rankChanges(changes) {
  if (!changes || !changes.length) return h("div");
  return card("Recent rank changes", h("div", { class: "scroll" }, h("table", {},
    h("thead", {}, h("tr", {}, h("th", {}, "When"), h("th", {}, "By"), h("th", {}, "Rank / player"), h("th", {}, "Change"), h("th", {}, "Status"))),
    h("tbody", {}, changes.map((c) => h("tr", {}, h("td", {}, ago(c.created_at)), h("td", {}, c.by_name), h("td", {}, c.target),
      h("td", {}, c.args.map(String).join(" ")), h("td", {}, statusPill(c.status), c.result ? " " + c.result : "")))))));
}

async function rankView() {
  const name = (params().name || "").toLowerCase();
  const r = await api("admin/ranks");
  const g = r.groups.find((x) => x.name === name);
  const wrap = h("div", {}, h("p", {}, h("a", { href: "#ranks" }, "← Ranks")));
  if (!g) {
    wrap.append(card(null, h("h2", {}, name), h("p", { class: "muted" }, "This rank isn't in LuckPerms yet. If you just made it, it shows up within a minute.")));
    return wrap;
  }
  const owner = isOwner();
  const go = (body) => rankApi({ group: g.name, ...body });
  wrap.append(h("div", { class: "card hero" }, h("div", { class: "rank-badge" }, icon("crown")),
    h("div", {}, h("h1", {}, g.display || g.name, " ", g.display && g.display !== g.name ? h("small", { class: "muted" }, g.name) : null),
      h("p", {}, mcText(g.prefix, "no prefix"), h("span", { class: "mc-name" }, "Steve"), mcText(g.suffix, "")),
      h("p", { class: "muted" }, "Weight " + g.weight + (g.parents.length ? " · inherits " + g.parents.join(", ") : "") + " · " + g.perms.length + " permissions"))));

  // looks
  const field2 = (label, value, op, ph) => {
    const inp = h("input", { value: value || "", placeholder: ph, maxlength: 80, disabled: !owner, "aria-label": label });
    const prev = h("div", { class: "preview" }, mcText(value, "none"));
    inp.addEventListener("input", () => prev.replaceChildren(mcText(inp.value, "none")));
    return h("div", {}, h("label", {}, label), h("div", { class: "row" }, inp, owner ? h("button", { onclick: () => go({ op, text: inp.value }) }, "Save") : null), prev);
  };
  const weight = h("input", { type: "number", value: g.weight, disabled: !owner, "aria-label": "Weight", style: "max-width:140px" });
  const looks = card("Look", h("p", { class: "muted" }, "Use & codes and &#RRGGBB hex colors. PlebClient players see hex colors exactly."),
    field2("Prefix", g.prefix, "prefix", "&#00E5FF&lVIP &7"), h("div", { style: "height:10px" }),
    field2("Suffix", g.suffix, "suffix", ""), h("div", { style: "height:10px" }),
    field2("Display name", g.display, "display", g.name), h("div", { style: "height:10px" }),
    h("label", {}, "Weight (higher ranks win)"), h("div", { class: "row" }, weight, owner ? h("button", { onclick: () => go({ op: "weight", weight: weight.value }) }, "Save") : null));

  // inheritance
  const others = r.groups.filter((x) => x.name !== g.name && !g.parents.includes(x.name));
  const parentSel = h("select", { "aria-label": "Rank to inherit" }, others.map((x) => h("option", { value: x.name }, x.display || x.name)));
  const parents = card("Inherits from", h("p", { class: "muted" }, "A rank gets every permission of the ranks it inherits."),
    h("div", { class: "chips" }, g.parents.length ? g.parents.map((pn) => h("span", { class: "chip" }, pn, owner ? h("button", { class: "x", title: "Stop inheriting", onclick: () => go({ op: "unparent", parent: pn }) }, "×") : null)) : h("span", { class: "muted" }, "Nothing")),
    owner && others.length ? h("div", { class: "row", style: "margin-top:12px" }, parentSel, h("button", { onclick: () => go({ op: "parent", parent: parentSel.value }) }, icon("plus"), "Inherit")) : null);
  wrap.append(h("div", { class: "grid g2" }, looks, parents), h("div", { style: "height:18px" }));

  // permissions
  const filter = h("input", { placeholder: "Filter permissions", "aria-label": "Filter permissions" });
  const body = h("tbody");
  const draw = () => {
    const q = filter.value.trim().toLowerCase();
    const rows = g.perms.filter((n) => !q || n.node.toLowerCase().includes(q));
    body.replaceChildren(...(rows.length ? rows.map((n) => h("tr", {},
      h("td", {}, h("code", {}, n.node)),
      h("td", {}, h("span", { class: "pill " + (n.value ? "good" : "bad") }, n.value ? "allow" : "deny")),
      h("td", {}, n.server || h("span", { class: "muted" }, "all servers"), n.world ? " / " + n.world : ""),
      h("td", {}, n.expiry ? "until " + new Date(n.expiry * 1000).toLocaleString() : ""),
      h("td", {}, owner ? h("button", { class: "small ghost", title: "Remove", onclick: () => go({ op: "unperm", node: n.node, server: n.server }) }, icon("trash")) : null)))
      : [h("tr", {}, h("td", { class: "muted" }, "No permissions" + (q ? " match." : " yet.")))]));
  };
  filter.addEventListener("input", draw);
  draw();
  const node = h("input", { placeholder: "permission, like essentials.fly", maxlength: 200, "aria-label": "Permission" });
  const server = h("input", { placeholder: "server (empty = all)", maxlength: 36, "aria-label": "Server", style: "max-width:200px" });
  const value = h("select", { "aria-label": "Allow or deny", style: "max-width:120px" }, h("option", { value: "1" }, "allow"), h("option", { value: "0" }, "deny"));
  const perms = card("Permissions", h("div", { class: "row" }, filter),
    owner ? h("div", { class: "row", style: "margin-top:10px" }, node, server, value, h("button", { class: "primary", onclick: () => go({ op: "perm", node: node.value.trim(), server: server.value.trim(), value: value.value === "1" }) }, icon("key"), "Add")) : null,
    h("div", { style: "height:10px" }),
    h("div", { class: "scroll" }, h("table", {}, h("thead", {}, h("tr", {}, h("th", {}, "Permission"), h("th", {}, ""), h("th", {}, "Where"), h("th", {}, "Expires"), h("th", {}, ""))), body)));
  wrap.append(perms);
  if (owner && g.name !== "default") {
    wrap.append(h("div", { style: "height:18px" }), card("Danger zone", h("p", { class: "muted" }, "Deleting a rank removes it from everyone who has it."),
      h("button", { class: "danger", onclick: () => {
        if (!confirm("Delete the rank " + g.name + "? Everyone with it loses it.")) return;
        rankApi({ op: "delete", group: g.name }, () => { location.hash = "#ranks"; });
      } }, icon("trash"), "Delete rank")));
  }
  return wrap;
}

async function playerRankCard(name, rank) {
  let groups = [];
  try { groups = (await api("admin/ranks")).groups; } catch (e) { /* no ranks */ }
  const current = h("p", {}, "Rank: ", rank ? h("b", {}, rank) : h("span", { class: "muted" }, "unknown (shows after they join)"));
  if (!isOwner() || !groups.length) return card("Rank", current, !groups.length ? h("p", { class: "muted" }, "Ranks appear once LuckPerms is connected (see Ranks).") : null);
  const sel = h("select", { "aria-label": "Rank" }, groups.map((g) => h("option", { value: g.name, selected: g.name === rank }, g.display || g.name)));
  const go = (mode) => rankApi({ op: "player", name, group: sel.value, mode });
  return card("Rank", current, h("div", { class: "row" }, sel,
    h("button", { class: "primary", onclick: () => go("set") }, "Set as rank"),
    h("button", { onclick: () => go("add") }, "Add"),
    h("button", { onclick: () => go("remove") }, "Remove")),
    h("p", { class: "muted" }, "Set replaces their ranks with this one. Add gives an extra rank."));
}

// ==================== admin: announcements ====================

async function announceView() {
  const out = h("div");
  const text = h("textarea", { id: "atext", maxlength: 200, placeholder: "Double coins this weekend!" });
  const site = h("input", { type: "checkbox", id: "asite", checked: true });
  const game = h("input", { type: "checkbox", id: "agame", checked: true });
  const left = h("small", { class: "muted" }, "200 left");
  text.addEventListener("input", () => { left.textContent = 200 - text.value.length + " left"; });
  const cur = info.announcement;
  return h("div", { class: "grid g2" },
    card("Make an announcement",
      h("label", { for: "atext" }, "Message"), text, left,
      h("label", { class: "check" }, site, "Show a banner on this website"),
      h("label", { class: "check" }, game, "Send it in game chat to everyone online"),
      h("div", { style: "height:8px" }),
      h("button", { class: "primary", onclick: async () => {
        try {
          await api("admin/announce", { text: text.value, site: site.checked, game: game.checked });
          info = await api("info");
          toast("Announced.");
          route();
        } catch (e) { message(out, e.message); }
      } }, icon("megaphone"), "Announce"), out),
    card("Website banner now", cur && cur.text ? h("div", {}, h("div", { class: "banner" }, icon("megaphone"), h("span", {}, cur.text)),
      h("p", { class: "muted" }, "By " + cur.by + ", " + ago(cur.at)),
      h("button", { class: "danger", onclick: async () => {
        try { await api("admin/announce", { clear: true }); info = await api("info"); toast("Banner removed."); route(); } catch (e) { toast(e.message, false); }
      } }, icon("trash"), "Remove banner")) : empty("megaphone", "No banner right now.")));
}

// ==================== admin: publish to shop ====================

async function publishView() {
  const out = h("div");
  const pre = params();
  const kind = select("pkind", [["cloak", "Cloak (PNG)"], ["hat", "Hat (.bbmodel)"], ["wings", "Wings (.bbmodel, the right wing)"]], pre.kind || "cloak");
  const fileIn = h("input", { type: "file", id: "pfile", accept: ".png,.bbmodel,image/png,application/json" });
  const fileNote = h("small", { class: "muted" }, "Or pick one of your saved designs below.");
  let file = null;
  let designId = null;
  const f = (label, attrs) => h("div", {}, field(label, attrs));
  const idIn = h("input", { id: "pid", placeholder: "fire_cloak", maxlength: 32, spellcheck: "false" });
  const nameIn = h("input", { id: "pname", placeholder: "Fire Cloak", maxlength: 40 });
  const price = h("input", { id: "pprice", type: "number", value: 5000, step: 100 });
  const desc = h("input", { id: "pdesc", maxlength: 100, placeholder: "Animated cloak" });
  const coll = h("input", { id: "pcoll", maxlength: 40, placeholder: "plebmc (or partners)" });
  const creator = h("input", { id: "pcreator", maxlength: 40, placeholder: "optional" });
  const ft = h("input", { id: "pft", type: "number", min: 20, max: 2000, placeholder: "90" });
  const slug = (s) => s.toLowerCase().replace(/[^a-z0-9_]+/g, "_").replace(/^_+|_+$/g, "").slice(0, 32);
  nameIn.addEventListener("input", () => { if (!idIn.dataset.touched) idIn.value = slug(nameIn.value); });
  idIn.addEventListener("input", () => { idIn.dataset.touched = "1"; });
  fileIn.addEventListener("change", async () => {
    const fl = fileIn.files[0];
    if (!fl) return;
    designId = null;
    if (fl.name.endsWith(".bbmodel") || fl.type === "application/json") {
      kind.value = "hat";
      file = await fl.text();
    } else {
      kind.value = "cloak";
      file = await new Promise((res) => { const rd = new FileReader(); rd.onload = () => res(rd.result); rd.readAsDataURL(fl); });
    }
    if (!nameIn.value) { nameIn.value = fl.name.replace(/\.[^.]+$/, "").replace(/[_-]+/g, " "); nameIn.dispatchEvent(new Event("input")); }
    fileNote.textContent = fl.name + " (" + Math.round(fl.size / 1024) + " KB)";
  });
  const btn = h("button", { class: "primary", onclick: async () => {
    if (!file) return message(out, "Pick a file or one of your designs first.");
    btn.disabled = true;
    try {
      await api("admin/publish", { kind: kind.value, itemId: idIn.value.trim(), name: nameIn.value.trim(), price: price.value, file,
        description: desc.value, collection: coll.value, creator: creator.value, frametime: ft.value, designId });
      catalogCache = null;
      message(out, "Sent to the server. It shows up in the shop within a few seconds (check Recent shop changes below).", true);
      loadCmds();
    } catch (e) { message(out, e.message); }
    btn.disabled = false;
  } }, icon("upload"), "Publish to shop");
  const mine = h("div", { class: "designs" });
  api("designs/mine").then((d) => {
    if (!d.designs.length) mine.replaceWith(h("p", { class: "muted" }, "You have no saved designs. Make one in the ", h("a", { href: "#makers" }, "makers"), "."));
    for (const x of d.designs) {
      mine.append(h("div", { class: "design" }, h("div", { class: "pic" }, x.thumb ? h("img", { src: x.thumb, alt: "" }) : icon(x.kind)),
        h("div", { class: "body" }, h("b", {}, x.name), h("small", { class: "muted" }, x.kind), h("div", { class: "row" },
          h("button", { class: "small", onclick: async () => {
            if (x.kind === "hat" || x.kind === "wings") {
              location.href = toolUrl(x.kind, x.id);
              return;
            }
            try {
              const full = (await api("designs/get?id=" + x.id)).design;
              kind.value = full.kind;
              designId = full.id;
              file = full.data;
              nameIn.value = full.name;
              idIn.value = full.itemId;
              idIn.dataset.touched = "1";
              if (full.extra.frametime) ft.value = full.extra.frametime;
              if (full.extra.description) desc.value = full.extra.description;
              if (full.extra.price != null) price.value = full.extra.price;
              fileNote.textContent = "Using your design " + full.name;
              message(out, "");
            } catch (e) { message(out, e.message); }
          } }, x.kind === "cloak" ? "Use this" : "Publish from the Cosmetic Maker")))));
    }
  }).catch(() => {});
  const cmds = h("tbody");
  const loadCmds = () => api("admin/commands").then((r) => {
    cmds.replaceChildren(...r.commands.filter((c) => c.action === "additem" || c.action === "broadcast").slice(0, 15).map((c) => h("tr", {},
      h("td", {}, ago(c.created_at)), h("td", {}, c.by_name), h("td", {}, c.action === "additem" ? "Add to shop" : "Announce"),
      h("td", {}, statusPill(c.status), c.result ? " " + c.result : ""))));
    if (!cmds.children.length) cmds.append(h("tr", {}, h("td", { class: "muted" }, "Nothing yet.")));
  }).catch(() => {});
  loadCmds();
  return h("div", {},
    card("Publish a hat or cloak to the shop",
      h("p", { class: "muted" }, "The server saves the file, adds the item to config.yml and reloads the shop. Nothing of yours in the config is removed."),
      h("div", { class: "grid g2" },
        h("div", {}, h("label", { for: "pkind" }, "Type"), kind),
        h("div", {}, h("label", { for: "pfile" }, "File"), fileIn, fileNote),
        h("div", {}, h("label", { for: "pname" }, "Name in the shop"), nameIn),
        h("div", {}, h("label", { for: "pid" }, "Item id (lowercase, no spaces)"), idIn),
        h("div", {}, h("label", { for: "pprice" }, "Price (" + coinName() + ", -1 = not for sale)"), price),
        h("div", {}, h("label", { for: "pft" }, "Time per frame in ms (animated cloaks)"), ft),
        h("div", {}, h("label", { for: "pdesc" }, "Description"), desc),
        h("div", {}, h("label", { for: "pcoll" }, "Shop tab (collection)"), coll),
        h("div", {}, h("label", { for: "pcreator" }, "Made by"), creator)),
      h("div", { style: "height:16px" }), btn, out),
    card("Your saved designs", mine),
    card(h("div", { class: "row", style: "width:100%" }, h("h2", {}, "Recent shop changes"), h("span", { class: "spacer" }), h("button", { class: "small", onclick: loadCmds }, icon("refresh"), "Refresh")),
      h("div", { class: "scroll" }, h("table", {}, cmds))));
}

// ==================== builds (Downloads page and admin Builds page) ====================

function size(n) {
  if (n >= 1024 * 1024) return (n / 1024 / 1024).toFixed(1) + " MB";
  if (n >= 1024) return Math.round(n / 1024) + " KB";
  return n + " B";
}

const BUILD_HELP = {
  windows: "Unzip it and run PlebClient.",
  wasm: "The fast web client. Unzip it and open index.html, or host the folder.",
  offline: "One HTML file. Open it in your browser, no install.",
  "forge-1.8.9": "Install Forge 1.8.9, then put the jar in your mods folder.",
  "forge-1.21.1": "Install Forge 1.21.1 (52.1.x), then put the jar in your mods folder.",
  plebcolors: "Server plugin (staff only).",
  plebioapi: "Proxy plugin (staff only).",
  panel: "This panel (staff only).",
};

function liveBuildsCard() {
  const list = h("div", { class: "list" }, h("div", { class: "muted" }, "Loading…"));
  api("downloads").then((r) => {
    list.replaceChildren(...(r.builds.length ? r.builds.map((b) => h("div", { class: "li" },
      h("span", {}, b.kindName, " ", h("small", { class: "muted" }, b.version)), h("b", {}, fmt(b.downloads), h("small", { class: "muted" }, " downloads"))))
      : [empty("box", "No builds published yet.")]));
  }).catch((e) => list.replaceChildren(h("div", { class: "muted" }, e.message)));
  return card(h("div", { class: "row", style: "width:100%" }, h("h2", {}, "Published builds"), h("span", { class: "spacer" }), h("a", { href: "#builds" }, "Manage builds")), list);
}

async function downloadsView() {
  const r = await api("downloads");
  const c = info.client || {};
  const rows = r.builds.map((b) => h("div", { class: "card build" },
    h("div", { class: "row", style: "width:100%" }, icon(b.audience === "public" ? "download" : "shield"),
      h("div", {}, h("h2", {}, b.kindName), h("div", { class: "muted" }, "Version " + b.version + " · " + size(b.size) + " · " + ago(b.publishedAt))),
      h("span", { class: "spacer" }),
      h("a", { class: "btn primary", href: b.url, download: b.filename }, icon("download"), "Download")),
    h("p", { class: "muted" }, BUILD_HELP[b.kind] || ""),
    b.notes ? h("p", {}, b.notes) : null,
    h("details", {}, h("summary", { class: "muted" }, "File details"), h("p", { class: "muted wrap" }, b.filename, h("br"), "SHA-256 " + b.sha256))));
  return h("div", {},
    card("Downloads", h("p", { class: "muted" }, "The latest PlebClient builds" + (c.latest ? " (PlebClient " + c.latest + ")" : "") + ". Sign in on the start screen with your PlebMc account.")),
    h("div", { style: "height:18px" }),
    rows.length ? h("div", { class: "grid g2" }, rows) : card(null, empty("download", "Nothing to download yet. Check back soon.")));
}

function uploadBuild(q, file, onProgress) {
  return new Promise((resolve, reject) => {
    const x = new XMLHttpRequest();
    x.open("POST", "/api/admin/builds/upload?" + new URLSearchParams(q));
    x.setRequestHeader("X-Requested-With", "PlebPanel");
    x.setRequestHeader("Content-Type", "application/octet-stream");
    x.upload.onprogress = (e) => { if (e.lengthComputable) onProgress(e.loaded / e.total); };
    x.onload = () => {
      if (x.status === 413) return reject(new Error("That's too big for the panel's web server. Raise client_max_body_size in nginx (see the README)."));
      let j = {};
      try { j = JSON.parse(x.responseText); } catch (e) { /* not JSON */ }
      if (x.status >= 400 || j.ok === false) return reject(new Error(j.error || "Upload failed (" + x.status + ")."));
      resolve(j);
    };
    x.onerror = () => reject(new Error("The upload stopped. Check your connection and try again."));
    x.send(file);
  });
}

async function buildsView() {
  const r = await api("admin/builds");
  const ro = !isOwner();
  const clientKinds = ["windows", "wasm", "offline", "forge-1.8.9", "forge-1.21.1"];
  const wrap = h("div");
  if (!ro) {
    const out = h("div");
    const kind = select("bkind", r.kinds.map((k) => [k.kind, k.name + (k.audience === "staff" ? " (staff only)" : "")]), "windows");
    const version = h("input", { id: "bver", placeholder: "2.0", maxlength: 32, value: (info.client || {}).latest || "" });
    const file = h("input", { id: "bfile", type: "file" });
    const notes = h("input", { id: "bnotes", maxlength: 300, placeholder: "optional, e.g. New: wings and video settings" });
    const publish = h("input", { type: "checkbox", id: "bpub", checked: true });
    const notify = h("input", { type: "checkbox", id: "bnotify" });
    const notifyRow = h("label", { class: "check" }, notify, "Also tell PlebClient players to update to this version");
    const types = () => (r.kinds.find((k) => k.kind === kind.value) || {}).types || [];
    const sync = () => {
      file.setAttribute("accept", types().join(","));
      notifyRow.hidden = !clientKinds.includes(kind.value) || !publish.checked;
    };
    kind.addEventListener("change", sync);
    publish.addEventListener("change", sync);
    sync();
    const bar = h("i");
    bar.style.width = "0%";
    const meter = h("div", { class: "meter" }, bar);
    meter.hidden = true;
    const btn = h("button", { class: "primary", onclick: async () => {
      const f = file.files[0];
      if (!f) return message(out, "Pick the build file.");
      if (!types().some((t) => f.name.toLowerCase().endsWith(t))) return message(out, "That build must be a " + types().join(" or ") + " file.");
      if (f.size > r.maxMb * 1024 * 1024) return message(out, "That file is bigger than " + r.maxMb + " MB.");
      btn.disabled = true;
      meter.hidden = false;
      message(out, "Uploading " + f.name + " (" + size(f.size) + ")…", true);
      try {
        await uploadBuild({ kind: kind.value, version: version.value.trim(), name: f.name, notes: notes.value.trim(),
          publish: publish.checked ? "1" : "0", notify: publish.checked && notify.checked && clientKinds.includes(kind.value) ? "1" : "0" },
        f, (x) => { bar.style.width = Math.round(x * 100) + "%"; });
        info = await api("info");
        toast(publish.checked ? "Uploaded and published." : "Uploaded. Publish it when you're ready.");
        route();
      } catch (e) {
        message(out, e.message);
        btn.disabled = false;
        meter.hidden = true;
      }
    } }, icon("upload"), "Upload");
    wrap.append(card("Upload a build",
      h("p", { class: "muted" }, "Publishing puts the build on the Downloads page (staff-only builds show only to admins). The build it replaces is kept here, so you can publish it again to go back."),
      h("div", { class: "grid g2" }, h("div", {}, h("label", { for: "bkind" }, "What is it"), kind), h("div", {}, h("label", { for: "bver" }, "Version"), version)),
      h("label", { for: "bfile" }, "File (up to " + r.maxMb + " MB)"), file,
      h("label", { for: "bnotes" }, "Notes on the Downloads page"), notes,
      h("label", { class: "check" }, publish, "Publish it now"), notifyRow,
      h("div", { style: "height:10px" }), h("div", { class: "row" }, btn), h("div", { style: "height:10px" }), meter, out));
    wrap.append(h("div", { style: "height:18px" }));
  }
  const act = async (path, b, done) => {
    try {
      await api(path, { id: b.id, notify: false });
      info = await api("info");
      toast(done);
      route();
    } catch (e) { toast(e.message, false); }
  };
  for (const k of r.kinds) {
    const mine = r.builds.filter((b) => b.kind === k.kind);
    const rows = mine.map((b) => h("tr", {},
      h("td", {}, h("b", {}, b.version), " ", b.live ? h("span", { class: "pill good" }, "live") : null),
      h("td", { class: "wrap" }, b.filename, h("br"), h("small", { class: "muted" }, size(b.size) + (b.notes ? " · " + b.notes : ""))),
      h("td", { title: when(b.uploadedAt) }, ago(b.uploadedAt), h("br"), h("small", { class: "muted" }, "by " + b.by)),
      h("td", { class: "num" }, fmt(b.downloads)),
      h("td", { class: "num" }, h("div", { class: "row", style: "justify-content:flex-end" },
        h("a", { class: "btn small", href: "/download/build/" + b.id, title: "Download" }, icon("download")),
        ro ? null : b.live ? h("button", { class: "small", onclick: () => act("admin/builds/unpublish", b, "Unpublished.") }, "Unpublish")
          : h("button", { class: "small primary", onclick: () => {
            if (confirm("Publish " + k.name + " " + b.version + "? It replaces the one on the Downloads page.")) act("admin/builds/publish", b, "Published.");
          } }, "Publish"),
        ro || b.live ? null : h("button", { class: "ghost small", title: "Delete", onclick: () => {
          if (confirm("Delete " + b.filename + " for good?")) act("admin/builds/delete", b, "Deleted.");
        } }, icon("trash"))))));
    wrap.append(card(h("div", { class: "row", style: "width:100%" }, h("h2", {}, k.name), " ", k.audience === "staff" ? h("span", { class: "pill" }, "staff only") : null),
      rows.length ? h("div", { class: "scroll" }, h("table", {}, h("thead", {}, h("tr", {}, h("th", {}, "Version"), h("th", {}, "File"), h("th", {}, "Uploaded"), h("th", { class: "num" }, "Downloads"), h("th", {}, ""))), h("tbody", {}, rows)))
        : empty("box", "Nothing uploaded yet.")));
    wrap.append(h("div", { style: "height:18px" }));
  }
  if (ro) wrap.prepend(h("p", { class: "muted" }, "Only owners can upload and publish builds."));
  return wrap;
}

// ==================== admin: client updates ====================

/** True when version a is older than b ("2.0" vs "2.0.1"; "2.0" and "2.0.0" are the same). */
function olderVersion(a, b) {
  const k = (v) => { const p = String(v || "0").split(".").map((x) => parseInt(x, 10) || 0).slice(0, 4); while (p.length < 4) p.push(0); return p; };
  const x = k(a), y = k(b);
  for (let i = 0; i < 4; i++) if (x[i] !== y[i]) return x[i] < y[i];
  return false;
}

async function clientView() {
  const r = await api("admin/client");
  const c = r.client;
  const out = h("div");
  const ro = !isOwner();
  const latest = h("input", { id: "clatest", value: c.latest, placeholder: "2.0", disabled: ro });
  const minimum = h("input", { id: "cmin", value: c.minimum, placeholder: "optional, e.g. 1.9", disabled: ro });
  const url = h("input", { id: "curl", value: c.url, placeholder: "https://client.plebmc.top/download", disabled: ro });
  const msg = h("input", { id: "cmsg", value: c.message, maxlength: 200, placeholder: "New: faster loading and new cloaks", disabled: ro });
  const vbody = h("tbody", {}, r.versions.map((v) => h("tr", {}, h("td", {}, "PlebClient " + v.version), h("td", {}, v.platform), h("td", { class: "num" }, fmt(v.n)),
    h("td", {}, c.latest && olderVersion(v.version, c.latest) ? h("span", { class: "pill warn" }, "outdated") : h("span", { class: "pill good" }, "up to date")))));
  return h("div", { class: "grid g2" },
    card("Update checker",
      h("p", { class: "muted" }, "Every PlebClient asks the panel when it starts. Older than Latest: it shows an Update screen with a Later button. Older than Oldest allowed: it can't be played until updated."),
      h("label", { for: "clatest" }, "Latest version"), latest,
      h("label", { for: "cmin" }, "Oldest allowed version (blocks older clients)"), minimum,
      h("label", { for: "curl" }, "Download link"), url,
      h("label", { for: "cmsg" }, "Message on the update screen"), msg,
      h("div", { style: "height:14px" }),
      ro ? h("p", { class: "muted" }, "Only owners can change these.") : h("button", { class: "primary", onclick: async () => {
        try {
          await api("admin/client", { latest: latest.value.trim(), minimum: minimum.value.trim(), url: url.value.trim(), message: msg.value.trim() });
          info = await api("info");
          message(out, "Saved. Clients see it the next time they start.", true);
        } catch (e) { message(out, e.message); }
      } }, "Save"), out),
    card("Versions in use (last 7 days)", r.versions.length ? h("div", { class: "scroll" }, h("table", {},
      h("thead", {}, h("tr", {}, h("th", {}, "Version"), h("th", {}, "Platform"), h("th", { class: "num" }, "Starts"), h("th", {}, ""))), vbody))
      : empty("refresh", "No client has checked in yet.")));
}

// ==================== admin: log ====================

async function auditView() {
  const q = h("input", { type: "search", placeholder: "Filter by who, what or detail", "aria-label": "Filter the log" });
  const body = h("tbody");
  let before = null;
  const pg = pager(body, async (offset) => {
    if (offset === 0) before = null;
    const r = await api("admin/audit?q=" + encodeURIComponent(q.value.trim()) + (before ? "&before=" + before : ""));
    if (r.audit.length) before = r.audit[r.audit.length - 1].id;
    return { items: r.audit, more: r.more };
  }, (a) => h("tr", {}, h("td", { title: when(a.at) }, ago(a.at)), h("td", {}, a.actor), h("td", {}, h("span", { class: "pill" }, a.action)), h("td", { class: "wrap" }, a.detail)),
  { empty: () => h("tr", {}, h("td", { class: "muted" }, "Nothing logged.")) });
  let timer;
  q.addEventListener("input", () => { clearTimeout(timer); timer = setTimeout(() => pg.reset(), 300); });
  pg.reset();
  return card("Admin log", h("p", { class: "muted" }, "Everything admins did on the panel, newest first."), h("div", { class: "row" }, q), h("div", { style: "height:10px" }),
    h("div", { class: "scroll" }, h("table", {}, h("thead", {}, h("tr", {}, h("th", {}, "When"), h("th", {}, "Who"), h("th", {}, "What"), h("th", {}, "Detail"))), body)), pg.more);
}

// ==================== router ====================

const VIEWS = {
  home: ["Home", homeView],
  downloads: ["Downloads", downloadsView],
  leaderboard: ["Leaderboard", leaderboardView],
  shop: ["Shop", shopView],
  makers: ["Cosmetic makers", makersView, "admin"],
  login: ["Sign in", loginView],
  register: ["Create account", registerView],
  me: ["My account", meView],
  admin: ["Dashboard", dashboardView, "admin"],
  players: ["Players", playersView, "admin"],
  player: ["Player", playerView, "admin"],
  ranks: ["Ranks", ranksView, "admin"],
  rank: ["Rank", rankView, "admin"],
  announce: ["Announcements", announceView, "admin"],
  publish: ["Publish to shop", publishView, "admin"],
  builds: ["Builds", buildsView, "admin"],
  client: ["Client updates", clientView, "admin"],
  audit: ["Admin log", auditView, "admin"],
};

async function route() {
  let view = location.hash.slice(1).split("?")[0] || "home";
  if (!VIEWS[view]) view = "home";
  if ((view === "login" || view === "register") && me) view = "me";
  const [title, fn, need] = VIEWS[view];
  side.classList.remove("open");
  setNav(view);
  titleEl.textContent = title;
  document.title = title + " · " + info.site + " Panel";
  let el;
  try {
    if (need === "admin" && !isAdmin()) {
      el = me ? h("div", { class: "card narrow" }, h("h2", {}, "Admins only"), h("p", { class: "muted" }, "This page is for admins.")) : loginView();
    } else {
      app.replaceChildren(h("div", { class: "skeleton" }));
      el = await fn();
    }
  } catch (err) {
    el = h("div", { class: "card narrow" }, h("p", {}, err.message), h("a", { href: "#home" }, "Back home"));
  }
  app.replaceChildren(el);
  setNav(view);
  window.scrollTo(0, 0);
}

(async function start() {
  try {
    info = await api("info");
    document.getElementById("site").textContent = info.site;
    const r = await api("me");
    me = r.account;
  } catch (e) { /* the view shows it */ }
  window.addEventListener("hashchange", route);
  route();
})();
