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
  chart: "M4 20V10M10 20V4M16 20v-7M22 20H2",
  trophy: "M8 21h8M12 17v4M7 4h10v5a5 5 0 01-10 0V4zM17 5h3v2a3 3 0 01-3 3M7 5H4v2a3 3 0 003 3",
  bag: "M6 7h12l1 14H5L6 7zM9 7V5a3 3 0 016 0v2",
  check: "M5 12.5l4.5 4.5L19 7.5",
  image: "M4 4h16v16H4zM4 16l5-5 4 4 3-3 4 4M15 9h.01",
  hammer: "M14 4l6 6-3 3-6-6 3-3zM11 7l-8 8 3 3 8-8",
  user: "M12 12a4 4 0 100-8 4 4 0 000 8zM4 21a8 8 0 0116 0",
  grid: "M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z",
  users: "M9 11a4 4 0 100-8 4 4 0 000 8zM2 21a7 7 0 0114 0M16 3.5a4 4 0 010 7.5M18 14a7 7 0 014 7",
  clock: "M12 21a9 9 0 100-18 9 9 0 000 18zM12 7v5l3 2",
  bulb: "M9 18h6M10 21h4M12 3a6 6 0 00-4 10.5c.7.7 1 1.5 1 2.5h6c0-1 .3-1.8 1-2.5A6 6 0 0012 3z",
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
  key: "M15 7a4 4 0 11-3.5 6L5 19.5V22H2v-3h2v-2h2l3.5-3.5A4 4 0 0115 7zM16 9h.01",
  copy: "M9 9h11v11H9zM5 15H4V4h11v1",
  globe: "M12 21a9 9 0 100-18 9 9 0 000 18zM3 12h18M12 3a14 14 0 010 18M12 3a14 14 0 000 18",
  sparkle: "M12 3l2 6 6 2-6 2-2 6-2-6-6-2 6-2 2-6zM19 3v4M17 5h4",
  gamepad: "M6 8h12a4 4 0 014 4v2a3 3 0 01-5.4 1.8L15 14H9l-1.6 1.8A3 3 0 012 14v-2a4 4 0 014-4zM7 11v3M5.5 12.5h3M15.5 12h.01M18 11h.01",
  discord: "M8 7.5a14 14 0 018 0M7.5 17c3 1.3 6 1.3 9 0M9 5.5L8 4C6 4.4 4.5 5 3.5 6 2 9 1.5 12 2 16c1.5 1.4 3.2 2.3 5 2.8l1-2M15 5.5L16 4c2 .4 3.5 1 4.5 2 1.5 3 2 6 1.5 10-1.5 1.4-3.2 2.3-5 2.8l-1-2M9 13.5h.01M15 13.5h.01",
  arrow: "M5 12h14M13 6l6 6-6 6",
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
  wearSkin(el, name);
  return el;
}

// ==================== skins as profile pictures ====================
const skinCache = new Map();
/** The skin picture for a name (or null), asked for once per page load. */
function skinImage(name) {
  const key = String(name || "").toLowerCase();
  if (!/^[a-z0-9_]{3,16}$/.test(key)) return Promise.resolve(null);
  if (!skinCache.has(key)) skinCache.set(key, new Promise((done) => {
    const img = new Image();
    img.onload = () => done(img);
    img.onerror = () => done(null);
    img.src = "/api/skin/" + encodeURIComponent(key);
  }));
  return skinCache.get(key);
}
/** The face of a skin, with the hat layer on top, as an 8 by 8 canvas the page scales up crisply. */
function skinHead(img) {
  const c = document.createElement("canvas");
  c.width = c.height = 8;
  c.className = "skin-head";
  const g = c.getContext("2d");
  g.imageSmoothingEnabled = false;
  g.drawImage(img, 8, 8, 8, 8, 0, 0, 8, 8);
  try {
    const hat = document.createElement("canvas");
    hat.width = hat.height = 8;
    const hg = hat.getContext("2d");
    hg.drawImage(img, 40, 8, 8, 8, 0, 0, 8, 8);
    const px = hg.getImageData(0, 0, 8, 8).data;
    let solid = true;
    for (let i = 3; i < px.length; i += 4) if (px[i] < 255) { solid = false; break; }
    if (!solid) g.drawImage(hat, 0, 0);     // an old skin with a filled hat area has no hat
  } catch (e) { /* the face alone is fine */ }
  return c;
}
/** Swap a letter square for the player's skin face once (and if) there is one. */
function wearSkin(el, name) {
  skinImage(name).then((img) => {
    if (!img) return;
    el.replaceChildren(skinHead(img));
    el.classList.add("has-skin");
    el.style.background = "none";
  });
  return el;
}
function tile(iconName, label, value, sub) {
  return h("div", { class: "tile" }, h("div", { class: "k" }, icon(iconName), label), h("div", { class: "v" }, value), sub ? h("div", { class: "s" }, sub) : null);
}
function empty(iconName, text) {
  return h("div", { class: "empty" }, plebio("vanish", { scale: 1 }) || icon(iconName), text);
}

// ==================== Plebio, the mascot ====================

/** Each pose's picture size, so the page keeps its shape while the picture loads. */
const PLEBIO = { idle: [70, 80], idlesway: [76, 80], sit: [70, 78], bow: [60, 80], celebrate: [98, 80], hurt: [82, 84], love: [110, 122],
  mcdeath: [103, 89], mcjump: [70, 136], onfire: [70, 114], shake: [70, 80], shiver: [72, 80], sneakspam: [76, 80], spotlight: [134, 138],
  sprint: [70, 83], totem: [70, 82], vanish: [96, 88] };
const stillOnly = () => window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
/** Plebio in a pose, or null when the owners turned the mascot off. Poke him and he reacts. */
function plebio(pose, opts = {}) {
  if (content().mascot === false) return null;
  const scale = opts.scale || 2;
  const src = (p) => (stillOnly() ? "/static/plebio/still.png" : "/static/plebio/" + p + ".gif");
  const size = (p) => (stillOnly() ? [70, 80] : PLEBIO[p]);
  const img = h("img", { class: "plebio" + (opts.cls ? " " + opts.cls : ""), src: src(pose), alt: "", draggable: "false", title: "Plebio" });
  const fit = (p) => { img.width = Math.round(size(p)[0] * scale); img.height = Math.round(size(p)[1] * scale); };
  fit(pose);
  let busy = 0;
  let pokes = 0;
  img.addEventListener("click", () => {
    if (busy || stillOnly()) return;
    const react = opts.react || ["hurt", "mcjump", "sneakspam", "shake", "celebrate"][pokes++ % 5];
    img.src = src(react);
    fit(react);
    busy = setTimeout(() => { busy = 0; img.src = src(pose); fit(pose); }, 2000);
  });
  return img;
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

// [id, label, icon, needs: "me" (signed in), "admin" or "owner"]
const NAV = [["home", "Home", "home"], ["downloads", "Download", "download"], ["leaderboard", "Leaderboards", "trophy"], ["stats", "Stats", "chart"], ["shop", "Shop", "bag"], ["news", "News", "megaphone"], ["vote", "Vote", "heart"]];
const ADMIN_NAV = [["admin", "Dashboard", "grid"], ["players", "Players", "users"], ["ranks", "Ranks", "crown"], ["punishments", "Punishments", "shield"], ["announce", "Announcements", "megaphone"], ["ideas", "Ideas", "bulb"],
  ["site", "Site content", "edit", "owner"], ["makers", "Cosmetic makers", "hammer"], ["publish", "Publish to shop", "upload"], ["client", "Updates", "refresh"], ["audit", "Admin log", "list"]];
const ADMIN_VIEWS = new Set(["admin", "players", "player", "ranks", "rank", "punishments", "announce", "ideas", "site", "makers", "publish", "client", "audit"]);
// page headings: [first word, second word (in color), a line under it]
const HEADS = {
  downloads: ["Get", "PlebClient", "Pick how you want to play. The first time you start it, make your PlebMc account on the start screen."],
  stats: ["Player", "stats", "Type a name and pick PlebMc, PlebNite or PlebRPG."],
  leaderboard: ["The", "leaderboards", "The best players on every game, with a top 10 for every stat."],
  shop: ["The", "shop", "Hats, cloaks, wings and trails everyone on PlebMc can see."],
  vote: ["Vote for", "PlebMc", "Vote every day on each site. It takes a few seconds."],
  news: ["PlebMc", "news", "Updates, changelogs and events, newest first."],
  me: ["My", "account", ""],
};

const content = () => info.content || {};
const siteLogo = () => content().logo || "/static/logo.png";
const topnav = document.getElementById("topnav");
const subnav = document.getElementById("subnav");
const footer = document.getElementById("footer");

function applyTheme() {
  const c = content();
  if (/^#[0-9a-f]{6}$/i.test(c.accent || "")) setAccent(c.accent);
  document.getElementById("site").textContent = info.site;
  document.getElementById("brand-logo").src = siteLogo();
}
/** The site color, with dark or white writing on it, whichever reads better. */
function setAccent(hex) {
  const [r, g, b] = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16));
  const root = document.documentElement.style;
  root.setProperty("--accent", hex);
  root.setProperty("--accent-ink", (r * 299 + g * 587 + b * 114) / 1000 > 140 ? "#04162a" : "#ffffff");
}

function setNav(view) {
  nav.replaceChildren();
  for (const [id, label, ic] of NAV) {
    if (id === "vote" && !voteLinks().length) continue;
    const dot = id === "vote" && votesLeft() ? h("i", { class: "navdot", title: "You can vote today" }) : null;
    if (dot) { nav.append(h("a", { href: "#" + id, class: view === id ? "on" : null }, icon(ic), label, dot)); continue; }
    nav.append(h("a", { href: "#" + id, class: view === id ? "on" : null }, icon(ic), label));
  }
  if (isAdmin()) nav.append(h("a", { href: "#admin", class: "admin-link" + (ADMIN_VIEWS.has(view) ? " on" : "") }, icon("shield"), "Admin"));
  if (content().discordUrl) nav.append(h("a", { href: content().discordUrl, target: "_blank", rel: "noopener" }, icon("discord"), "Discord"));
  if (ADMIN_VIEWS.has(view) && isAdmin()) {
    const row = h("div", { class: "wrap" });
    for (const [id, label, ic, need] of ADMIN_NAV) {
      if (need === "owner" && !isOwner()) continue;
      const on = view === id || (view === "player" && id === "players") || (view === "rank" && id === "ranks");
      row.append(h("a", { href: "#" + id, class: on ? "on" : null }, icon(ic), label));
    }
    subnav.replaceChildren(row);
    subnav.classList.remove("hidden");
  } else subnav.classList.add("hidden");
  who.replaceChildren();
  if (me) {
    who.append(h("a", { href: "#me", class: "who" }, initial(me.name), h("span", { class: "name" }, me.name, " "), rolePill(me.role)),
      h("button", { class: "ghost small", onclick: logout, title: "Sign out", "aria-label": "Sign out" }, icon("logout")));
  } else {
    who.append(h("a", { class: "btn small ghost", href: "#login" }, "Sign in"), h("a", { class: "btn primary small", href: "#register" }, "Create account"));
  }
}

const menuBtn = document.getElementById("menu");
menuBtn.addEventListener("click", () => {
  const open = topnav.classList.toggle("open");
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

/** "New post" on the homepage for a week after a post goes up. Hiding it lasts until the next post. */
function newsNotice() {
  const p = info.newPost;
  if (!p) return null;
  try { if (localStorage.getItem("news-notice-hidden") === String(p.id)) return null; } catch (e) { /* show it */ }
  const bar = h("div", { class: "news-notice" }, h("div", { class: "wrap" }, kindPill(p.tag),
    h("a", { href: "#news?post=" + p.id }, h("b", {}, p.title), h("span", { class: "go" }, "Read it", icon("arrow"))),
    h("small", {}, ago(p.at)),
    h("button", { class: "x", title: "Hide this", "aria-label": "Hide this notice", onclick: () => {
      try { localStorage.setItem("news-notice-hidden", String(p.id)); } catch (e) { /* hidden for this visit */ }
      bar.remove();
    } }, "×")));
  return bar;
}

function copyIp(ip) {
  const done = () => toast("Copied " + ip + ". Add it as a server in PlebClient.");
  if (navigator.clipboard) navigator.clipboard.writeText(ip).then(done, () => toast(ip));
  else toast(ip);
}

function drawFooter() {
  const c = content();
  const col = (title, links) => h("div", {}, h("h4", {}, title), h("ul", {}, ...links.filter(Boolean).map(([label, href, ext]) =>
    h("li", {}, h("a", ext ? { href, target: "_blank", rel: "noopener" } : { href }, label)))));
  footer.replaceChildren(h("div", { class: "wrap" },
    plebio("sit", { cls: "foot-pal", scale: 1 }),
    h("div", { class: "foot-in" },
      h("div", {}, h("a", { class: "brand", href: "#home" }, h("img", { src: siteLogo(), alt: "" }), info.site),
        h("p", {}, c.subtitle || "")),
      col("Play", [["Download PlebClient", "#downloads"], c.serverIp ? [c.serverIp, "#home"] : null, ["Shop", "#shop"]]),
      col("Community", [c.discordUrl ? ["Discord", c.discordUrl, true] : null, voteLinks().length ? ["Vote", "#vote"] : null, ["News", "#news"], ["Leaderboards", "#leaderboard"], ["Stats", "#stats"]]),
      col("Account", me ? [["My account", "#me"], isAdmin() ? ["Admin", "#admin"] : null] : [["Sign in", "#login"], ["Create account", "#register"]])),
    h("div", { class: "foot-bottom" }, h("span", {}, "© " + new Date().getFullYear() + " " + info.site), h("span", {}, c.footer || ""))));
}

// ==================== posts: news and changelogs ====================

/** Inline markdown (bold, italic, strike, code, links, pictures) as DOM nodes. Text is never parsed as HTML. */
function mdInline(text) {
  const out = [];
  const re = /(`[^`]+`)|\[([^\]]+)\]\(([^)\s]+)\)|(https?:\/\/[^\s<>)\]]+)|\*\*([^*]+(?:\*(?!\*)[^*]*)*)\*\*|~~([^~]+)~~|\*([^*\s][^*]*)\*|(?<![A-Za-z0-9])_([^_\s][^_]*)_(?![A-Za-z0-9])|!\[([^\]]*)\]\(([^)\s]+)\)/g;
  const safe = (u) => (/^(https?:\/\/|#|mailto:)/i.test(u) ? u : null);
  const link = (label, u) => (safe(u) ? h("a", u.startsWith("#") ? { href: u } : { href: u, target: "_blank", rel: "noopener nofollow" }, ...label) : label);
  let last = 0;
  let m;
  while ((m = re.exec(text))) {
    if (m.index > last) out.push(text.slice(last, m.index));
    if (m[1]) out.push(h("code", {}, m[1].slice(1, -1)));
    else if (m[2]) out.push(...[].concat(link(mdInline(m[2]), m[3])));
    else if (m[4]) { const u = m[4].replace(/[.,!?;:]+$/, ""); re.lastIndex -= m[4].length - u.length; out.push(h("a", { href: u, target: "_blank", rel: "noopener nofollow" }, u)); }
    else if (m[5]) out.push(h("strong", {}, ...mdInline(m[5])));
    else if (m[6]) out.push(h("s", {}, ...mdInline(m[6])));
    // a picture: from an https address, or one uploaded to this site
    else if (m[10] !== undefined) out.push(/^(https:\/\/|\/(?!\/))/i.test(m[10]) ? h("img", { src: m[10], alt: m[9], loading: "lazy", referrerpolicy: "no-referrer" }) : m[0]);
    else out.push(h("em", {}, ...mdInline(m[7] || m[8])));
    last = re.lastIndex;
  }
  if (last < text.length) out.push(text.slice(last));
  return out;
}
/** Markdown as a DOM tree: headings, lists (one level of nesting), quotes, code blocks, rules and paragraphs. */
function md(src) {
  const root = h("div", { class: "md" });
  const lines = String(src || "").replace(/\r\n?/g, "\n").split("\n");
  const item = /^(\s*)([-*+]|\d{1,3}[.)])\s+(.*)$/;
  let i = 0;
  while (i < lines.length) {
    const line = lines[i];
    let m;
    if (!line.trim()) { i++; continue; }
    if (/^```/.test(line)) {
      const code = [];
      for (i++; i < lines.length && !/^```/.test(lines[i]); i++) code.push(lines[i]);
      i++;
      root.append(h("pre", {}, h("code", {}, code.join("\n"))));
    } else if ((m = /^(#{1,4})\s+(.*?)\s*#*$/.exec(line))) {
      root.append(h("h" + (m[1].length + 1), {}, ...mdInline(m[2])));
      i++;
    } else if (/^\s*([-*_])(\s*\1){2,}\s*$/.test(line)) {
      root.append(h("hr"));
      i++;
    } else if (/^>\s?/.test(line)) {
      const q = [];
      for (; i < lines.length && /^>\s?/.test(lines[i]); i++) q.push(lines[i].replace(/^>\s?/, ""));
      root.append(h("blockquote", {}, ...md(q.join("\n")).childNodes));
    } else if ((m = item.exec(line))) {
      const ordered = (mm) => /\d/.test(mm[2]);
      const list = h(ordered(m) ? "ol" : "ul");
      const base = m[1].length;
      let li = null;
      let sub = null;
      for (; i < lines.length; i++) {
        const mm = item.exec(lines[i]);
        if (mm && mm[1].length <= base + 1) { li = h("li", {}, ...mdInline(mm[3])); sub = null; list.append(li); }
        else if (mm && li) { if (!sub) { sub = h(ordered(mm) ? "ol" : "ul"); li.append(sub); } sub.append(h("li", {}, ...mdInline(mm[3]))); }
        else if (lines[i].trim() && /^\s{2,}/.test(lines[i]) && li) (sub ? sub.lastChild : li).append(" ", ...mdInline(lines[i].trim()));
        else break;
      }
      root.append(list);
    } else {
      const para = [];
      for (; i < lines.length && lines[i].trim() && !/^(```|#{1,4}\s|>\s?)/.test(lines[i]) && !item.test(lines[i]); i++) para.push(lines[i]);
      const kids = [];
      para.forEach((l, k) => { if (k) kids.push(/ {2}$/.test(para[k - 1]) ? h("br") : " "); kids.push(...mdInline(l.trim())); });
      root.append(h("p", {}, ...kids));
    }
  }
  return root;
}

const POST_KINDS = { news: "News", server: "Server update", client: "Client update", event: "Event" };
const postDate = (t) => new Date(t * 1000).toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" });
const kindPill = (tag) => h("span", { class: "kind k-" + tag }, POST_KINDS[tag] || "News");

function postCard(p, opts = {}) {
  const body = md(p.body);
  const art = h("article", { class: "post" + (p.pinned ? " pinned" : ""), id: "post-" + p.id },
    h("header", {}, kindPill(p.tag), p.pinned ? h("span", { class: "kind k-pin" }, "Pinned") : null,
      h("span", { class: "when" }, postDate(p.at) + " · " + p.author + (p.editedAt > p.at + 60 ? " · edited" : "")),
      isOwner() && !opts.preview ? h("a", { class: "btn small ghost", href: "#announce?post=" + p.id }, icon("edit"), "Edit") : null),
    h("h2", {}, opts.preview || opts.full ? p.title : h("a", { href: "#news?post=" + p.id }, p.title)), body);
  if (!opts.full && !opts.preview && p.body.length > 900) {
    art.classList.add("clamp");
    art.append(h("button", { class: "more-btn", onclick: (e) => { art.classList.remove("clamp"); e.target.remove(); } }, "Read the rest"));
  }
  return art;
}

async function newsView() {
  const q = params();
  if (q.post) {
    const r = await api("posts/" + encodeURIComponent(q.post));
    return h("div", { class: "posts" }, h("a", { class: "back", href: "#news" }, "← All posts"), postCard(r.post, { full: true }));
  }
  const list = h("div", { class: "posts" });
  let tag = "";
  const pg = pager(list, async (offset) => {
    const r = await api("posts?offset=" + offset + "&limit=8" + (tag ? "&tag=" + tag : ""));
    return { items: r.posts, more: r.more, total: r.total };
  }, (p) => postCard(p), { empty: () => empty("megaphone", tag ? "No posts of this kind yet." : "No posts yet. Check back soon.") });
  const tabs = h("div", { class: "ptabs" });
  const drawTabs = () => tabs.replaceChildren(...[["", "Everything"], ...Object.entries(POST_KINDS)].map(([k, label]) =>
    h("button", { class: "tab" + (k === tag ? " on" : ""), onclick: () => { tag = k; drawTabs(); pg.reset(); } }, label + (k && k !== "news" ? "s" : ""))));
  drawTabs();
  pg.next();
  return h("div", {}, h("div", { class: "row", style: "margin-bottom:16px;flex-wrap:wrap;gap:10px" }, tabs, h("span", { class: "spacer" }),
    isOwner() ? h("a", { class: "btn primary", href: "#announce" }, icon("edit"), "Write a post") : null), list, pg.more);
}

/** The row of formatting buttons above a markdown text box. Each one changes the text as if it was typed, so undo works. */
function mdToolbar(area) {
  const put = (text, selFrom, selTo) => {
    area.focus();
    const start = area.selectionStart;
    let done = false;
    try { done = document.execCommand("insertText", false, text); } catch (e) { /* older browsers: below */ }
    if (!done) { area.setRangeText(text, start, area.selectionEnd, "end"); area.dispatchEvent(new Event("input", { bubbles: true })); }
    area.setSelectionRange(start + selFrom, start + selTo);
  };
  // **like this**: around what is selected, or around a word to type over
  const wrap = (before, after, sample) => () => {
    const sel = area.value.slice(area.selectionStart, area.selectionEnd);
    if (sel && sel.startsWith(before) && sel.endsWith(after) && sel.length >= before.length + after.length) {
      const inner = sel.slice(before.length, sel.length - after.length);
      return put(inner, 0, inner.length);       // pressing it again takes it off
    }
    // the marks sit just outside what is selected (as they do right after pressing the button): take them off too
    const v = area.value;
    const a = area.selectionStart - before.length;
    const b = area.selectionEnd + after.length;
    const star = before === "*" && (v[a - 1] === "*") !== (v[b] === "*") ? false : !(before === "*" && v[a - 1] === "*" && v[a - 2] !== "*");
    if (a >= 0 && star && v.slice(a, a + before.length) === before && v.slice(b - after.length, b) === after) {
      area.setSelectionRange(a, b);
      return put(sel, 0, sel.length);
    }
    const inner = sel || sample;
    put(before + inner + after, before.length, before.length + inner.length);
  };
  // "- " or "## " in front of every selected line
  const lines = (prefix, sample) => () => {
    const v = area.value;
    const from = v.lastIndexOf("\n", area.selectionStart - 1) + 1;
    let to = v.indexOf("\n", area.selectionEnd);
    if (to < 0) to = v.length;
    area.setSelectionRange(from, to);
    const strip = /^(#{1,4}\s+|>\s?|[-*+]\s+|\d{1,3}[.)]\s+)/;
    const cur = v.slice(from, to).split("\n");
    const pre = (i) => (typeof prefix === "function" ? prefix(i) : prefix);
    const on = cur.every((l, i) => l.startsWith(pre(i)));
    const next = cur.map((l, i) => (on ? l.slice(pre(i).length) : pre(i) + (l.replace(strip, "") || (cur.length === 1 ? sample : "")))).join("\n");
    put(next, on || cur.length > 1 || cur[0] ? 0 : pre(0).length, next.length);
  };
  // something on lines of its own
  const block = (text, selFrom, selTo) => {
    const v = area.value;
    const s = area.selectionStart;
    const lead = s === 0 || v.slice(0, s).endsWith("\n\n") ? "" : v[s - 1] === "\n" ? "\n" : "\n\n";
    const after = v.slice(area.selectionEnd);
    const tail = after.startsWith("\n\n") || !after ? "\n" : after.startsWith("\n") ? "\n" : "\n\n";
    put(lead + text + tail, lead.length + selFrom, lead.length + selTo);
  };
  const address = (what, start) => {
    const u = (prompt(what, start) || "").trim();
    if (!u || u === start) return null;
    return /^(https?:\/\/|\/|#|mailto:)/i.test(u) ? u : "https://" + u;
  };
  const link = () => {
    const sel = area.value.slice(area.selectionStart, area.selectionEnd);
    const u = address("Where should the link go?", "https://");
    if (!u) return area.focus();
    const label = sel || "link text";
    put("[" + label + "](" + u + ")", 1, 1 + label.length);
  };
  const image = () => {
    const u = address("The picture's address (a link that ends in .png, .jpg, .gif or .webp)", "https://");
    if (!u) return area.focus();
    if (/^http:\/\//i.test(u)) { toast("Pictures need an https:// address.", false); return area.focus(); }
    const sel = area.value.slice(area.selectionStart, area.selectionEnd).replace(/\n/g, " ");
    const alt = sel || "picture";
    block("![" + alt + "](" + u + ")", 2, 2 + alt.length);
  };
  const code = () => {
    const sel = area.value.slice(area.selectionStart, area.selectionEnd);
    if (sel.includes("\n")) block("```\n" + sel + "\n```", 4, 4 + sel.length);
    else wrap("`", "`", "code")();
  };
  const tools = [
    ["B", "Bold (Ctrl+B)", wrap("**", "**", "bold text"), "b"], ["I", "Italic (Ctrl+I)", wrap("*", "*", "italic text"), "i"], ["S", "Strikethrough", wrap("~~", "~~", "crossed out"), "s"], null,
    ["H1", "Big heading", lines("## ", "Heading")], ["H2", "Smaller heading", lines("### ", "Heading")], null,
    ["• List", "Bullet list", lines("- ", "List item")], ["1. List", "Numbered list", lines((i) => i + 1 + ". ", "List item")], ["❝ Quote", "Quote", lines("> ", "Quote")], ["</> Code", "Code", code], null,
    ["Link", "Link (Ctrl+K)", link], ["Image", "Picture from an address", image], ["― Line", "A line across the post", () => block("---", 3, 3)],
  ];
  const keys = { b: tools[0][2], i: tools[1][2], k: link };
  area.addEventListener("keydown", (e) => {
    const run = (e.ctrlKey || e.metaKey) && !e.altKey && !e.shiftKey && keys[e.key.toLowerCase()];
    if (run) { e.preventDefault(); run(); }
  });
  return h("div", { class: "md-tools", role: "toolbar", "aria-label": "Formatting" }, tools.map((t) => (t
    ? h("button", { type: "button", class: "small" + (t[3] ? " md-" + t[3] : ""), title: t[1], "aria-label": t[1], onmousedown: (e) => e.preventDefault(), onclick: t[2] }, t[0])
    : h("span", { class: "sep", "aria-hidden": "true" }))));
}

/** Owners: write, edit and delete posts, with formatting buttons and a live preview beside the text. */
async function postsEditor() {
  const out = h("div");
  const q = params();
  let editing = null;
  if (q.post) { try { editing = (await api("posts/" + encodeURIComponent(q.post))).post; } catch (e) { /* gone: start a new one */ } }
  const title = h("input", { id: "p-title", maxlength: 120, placeholder: "PlebClient 2.1 is out", value: editing ? editing.title : "" });
  const kind = select("p-kind", Object.entries(POST_KINDS), editing ? editing.tag : "news");
  const pin = h("input", { type: "checkbox", id: "p-pin" });
  pin.checked = !!(editing && editing.pinned);
  const body = h("textarea", { id: "p-body", rows: 14, maxlength: 20000, spellcheck: "true", placeholder: "## What's new\n\n- Faster loading\n- **New cloaks** in the shop\n\nSee you in game!" });
  body.value = editing ? editing.body : "";
  const preview = h("div", { class: "post-preview" });
  const draw = () => preview.replaceChildren(postCard({ id: 0, title: title.value || "Your title", tag: kind.value, pinned: pin.checked, body: body.value || "*Your post shows here as you type.*",
    author: me.name, at: editing ? editing.at : Math.floor(Date.now() / 1000), editedAt: 0 }, { preview: true }));
  [title, body].forEach((el) => el.addEventListener("input", draw));
  [kind, pin].forEach((el) => el.addEventListener("change", draw));
  draw();
  const list = h("div", { class: "post-rows" });
  const load = async () => {
    const r = await api("posts?limit=30");
    list.replaceChildren(...(r.posts.length ? r.posts.map((p) => h("div", { class: "post-row" }, kindPill(p.tag), h("a", { href: "#news?post=" + p.id }, p.title), h("span", { class: "spacer" }),
      h("small", { class: "muted" }, postDate(p.at)),
      h("a", { class: "btn small", href: "#announce?post=" + p.id }, icon("edit"), "Edit"),
      h("button", { class: "small danger", onclick: async () => {
        if (!confirm("Delete \"" + p.title + "\"? This can't be undone.")) return;
        try { await api("admin/posts", { delete: true, id: p.id }); info = await api("info"); toast("Deleted."); load(); } catch (e) { toast(e.message, false); }
      } }, icon("trash"), "Delete"))) : [empty("megaphone", "No posts yet.")]));
  };
  await load();
  const save = h("button", { class: "primary", onclick: async () => {
    save.disabled = true;
    try {
      const r = await api("admin/posts", { id: editing ? editing.id : undefined, title: title.value, tag: kind.value, pinned: pin.checked, body: body.value });
      info = await api("info");
      toast(editing ? "Saved." : "Posted.");
      location.hash = "#news?post=" + r.post.id;
    } catch (e) { message(out, e.message); }
    save.disabled = false;
  } }, icon("megaphone"), editing ? "Save changes" : "Publish");
  return h("div", {},
    card(editing ? "Edit post" : "Write a post",
      h("p", { class: "muted" }, "Posts show on the News page: changelogs, events, anything. The buttons add the formatting for you (it is markdown, so you can type it too), and the preview shows the post as players will see it."),
      h("div", { class: "row post-top" }, h("div", { class: "grow" }, h("label", { for: "p-title" }, "Title"), title),
        h("div", {}, h("label", { for: "p-kind" }, "Kind"), kind), h("label", { class: "check", for: "p-pin" }, pin, " Pin to the top")),
      h("div", { class: "grid g2 post-edit" },
        h("div", { class: "post-write" }, h("label", { for: "p-body" }, "Post"), mdToolbar(body), body),
        h("div", { class: "post-preview-col" }, h("label", {}, "Preview"), preview)),
      h("div", { class: "row", style: "gap:8px;margin-top:14px" }, save, editing ? h("a", { class: "btn", href: "#announce" }, "Cancel") : null), out),
    card("Posts", list));
}

// ==================== voting ====================

const voteLinks = () => content().voteLinks || [];
const today = () => new Date().toISOString().slice(0, 10);
/** What this browser remembers about today's votes: {day, sites: [links voted on], later: true once "Later" was pressed}. */
function voteState() {
  try {
    const v = JSON.parse(localStorage.getItem("pleb-vote") || "{}");
    if (v && v.day === today() && Array.isArray(v.sites)) return v;
  } catch (e) { /* no storage: fall through */ }
  return { day: today(), sites: [], later: false };
}
function saveVoteState(v) {
  try { localStorage.setItem("pleb-vote", JSON.stringify(v)); } catch (e) { /* private window */ }
}
const votesLeft = () => { const v = voteState(); return voteLinks().filter((l) => !v.sites.includes(l.url)).length; };
function markVoted(url) {
  const v = voteState();
  if (!v.sites.includes(url)) v.sites.push(url);
  saveVoteState(v);
  drawVoteNudge();
}
const rewardText = () => (content().voteReward > 0 ? fmt(content().voteReward) + " " + coinName() : "");

/** The reminder in the corner: shows until today's votes are done or "Later" is pressed. */
function drawVoteNudge(view) {
  document.querySelectorAll(".vote-nudge").forEach((n) => n.remove());
  const v = voteState();
  if (view === undefined) view = location.hash.slice(1).split("?")[0] || "home";
  if (!voteLinks().length || !votesLeft() || v.later || view === "vote" || view === "login" || view === "register" || ADMIN_VIEWS.has(view)) return;
  const r = rewardText();
  const box = h("div", { class: "vote-nudge", role: "status" },
    h("button", { class: "x", "aria-label": "Remind me later", onclick: () => { saveVoteState({ ...voteState(), later: true }); box.remove(); } }, "×"),
    plebio("sneakspam", { cls: "vn-pal", scale: 0.8, react: "love" }) || h("div", { class: "vn-icon" }, icon("heart")),
    h("div", {}, h("b", {}, "Voted for " + info.site + " today?"), h("p", {}, r ? "Every vote pays " + r + " and helps new players find us." : "Every vote helps new players find us."),
      h("div", { class: "row" }, h("a", { class: "btn primary small", href: "#vote" }, "Vote now"),
        h("button", { class: "ghost small", onclick: () => { saveVoteState({ ...voteState(), later: true }); box.remove(); } }, "Later"))));
  document.body.append(box);
}

async function voteView() {
  const c = content();
  const links = voteLinks();
  if (!links.length) return card(null, empty("heart", "There's nowhere to vote yet. Check back soon."));
  const r = rewardText();
  const list = h("div", { class: "vote-list" });
  const progress = h("div", { class: "vote-progress" });
  const nameBox = h("input", { id: "vote-name", maxlength: 16, spellcheck: "false", placeholder: "Your Minecraft name", value: me ? me.name : "" });
  const draw = () => {
    const v = voteState();
    const done = links.filter((l) => v.sites.includes(l.url)).length;
    const bar = h("i");
    bar.style.width = Math.round(done / links.length * 100) + "%";
    progress.replaceChildren(...[done === links.length ? plebio("celebrate", { cls: "vp-pal", scale: 1 }) : null].filter(Boolean), h("div", { class: "row" }, h("b", {}, done === links.length ? "All done for today. Thank you!" : done + " of " + links.length + " voted today"),
      h("span", { class: "spacer" }), h("span", { class: "muted" }, "Resets every day")), h("div", { class: "meter" }, bar));
    list.replaceChildren(...links.map((l, i) => {
      const voted = v.sites.includes(l.url);
      let host = "";
      try { host = new URL(l.url).hostname.replace(/^www\./, ""); } catch (e) { /* shown without it */ }
      return h("div", { class: "vote-site" + (voted ? " done" : "") }, h("span", { class: "vn" }, voted ? icon("check") : i + 1),
        h("div", {}, h("b", {}, l.name), h("small", {}, host)),
        r ? h("span", { class: "reward" }, icon("coin"), "+" + fmt(c.voteReward)) : null,
        h("a", { class: "btn " + (voted ? "" : "primary"), href: l.url, target: "_blank", rel: "noopener", onclick: () => { setTimeout(() => { markVoted(l.url); draw(); }, 400); } },
          voted ? "Vote again" : "Vote", icon("arrow")));
    }));
  };
  draw();
  return h("div", { class: "vote" },
    h("div", { class: "vote-main" }, progress, list),
    h("aside", { class: "card" }, h("h2", {}, "How voting works"),
      h("ol", { class: "vote-steps" },
        h("li", {}, h("b", {}, "Copy your name"), h("div", { class: "row", style: "margin-top:6px" }, nameBox,
          h("button", { onclick: () => { const n = nameBox.value.trim(); if (!n) return; if (navigator.clipboard) navigator.clipboard.writeText(n).then(() => toast("Copied " + n + ".")); } }, icon("copy"), "Copy"))),
        h("li", {}, h("b", {}, "Press Vote"), h("p", { class: "muted" }, "The site opens in a new tab. Paste your Minecraft name there, exactly as you play.")),
        h("li", {}, h("b", {}, r ? "Get " + r : "That's it"), h("p", { class: "muted" }, r ? "Your coins arrive in game right away, or the next time you join." : "Thanks for helping the server grow."))),
      c.voteText ? h("p", { class: "faint", style: "font-size:13px;margin:12px 0 0" }, c.voteText) : null));
}

// ==================== home ====================

async function homeView() {
  const s = info.server || {};
  const cl = info.client || {};
  const c = content();
  const wrap = h("div", { class: "home" });
  const dl = (info.downloads || []).length ? "#downloads" : "#register";
  const title = (a, b) => h("div", { class: "sec-title" }, h("h2", {}, ...a), b ? h("p", {}, b) : null);

  // hero: the art, then the name, tagline and buttons on the fade under it
  const art = h("div", { class: "hero-art" });
  if (c.heroImage) art.style.backgroundImage = "url(\"" + c.heroImage + "\")";
  const ann = announcementBanner();
  // a new post this week: a strip across the top, before anything else
  const notice = newsNotice();
  if (notice) wrap.append(notice);
  const ping = h("span", { class: "ping" + (s.connected ? " on" : ""), "aria-hidden": "true" }, h("i"), h("i"), h("i"), h("i"), h("i"));
  wrap.append(h("section", {}, art, h("div", { class: "wrap hero-body" },
    c.badge ? h("a", { class: "news", href: "#downloads" }, h("b", {}, "New"), c.badge) : null,
    c.wordmark ? h("h1", { style: "margin:0" }, h("img", { class: "wordmark-img", src: c.wordmark, alt: info.site }))
      : h("h1", { class: "wordmark" }, info.site),
    h("p", { class: "tagline" }, c.title1 || "", " ", h("em", {}, c.title2 || "")),
    h("p", { class: "lead" }, c.subtitle || ""),
    h("div", { class: "hero-cta" },
      h("a", { class: "btn primary big", href: dl }, icon("download"), "Download PlebClient"),
      c.serverIp ? h("button", { class: "server", onclick: () => copyIp(c.serverIp), title: "Copy the server address" },
        h("img", { src: siteLogo(), alt: "" }),
        h("span", {}, h("b", {}, c.serverIp), h("small", {}, s.connected ? "Server online · click to copy" : "Click to copy the address")), ping) : null),
    ann ? h("div", { style: "width:100%;max-width:760px;margin-top:10px;text-align:left" }, ann) : null,
    statBar(s, cl))));

  // the newest posts
  const news = info.latestPosts || [];
  if (news.length) {
    wrap.append(h("section", { class: "sec news-sec" }, h("div", { class: "wrap" },
      h("div", { class: "news-head" }, h("h2", {}, "Latest ", h("em", {}, "news")), h("a", { class: "btn", href: "#news" }, "All posts", icon("arrow"))),
      h("div", { class: "news-cards" }, ...news.map((p) => h("a", { class: "news-card", href: "#news?post=" + p.id }, kindPill(p.tag), h("b", {}, p.title), h("small", {}, postDate(p.at))))))));
  }

  // features: picture and words, swapping sides
  const feats = c.features || [];
  if (feats.length) {
    wrap.append(h("section", { class: "sec", id: "features" }, h("div", { class: "wrap" },
      title([c.featuresTitle || ""], c.featuresText),
      h("div", { class: "rows" }, ...feats.map((f, i) => h("article", { class: "frow" + (f.image ? "" : " noshot") },
        f.image ? h("div", { class: "shot" }, h("img", { src: f.image, alt: "", loading: "lazy" })) : null,
        h("div", {}, h("span", { class: "n" }, i + 1), h("h3", {}, f.title), h("p", {}, f.text))))))));
  }

  // leaderboards: pick a game, see its podium
  const tabs = h("div", { class: "tabs" });
  const stage = h("div", { class: "podium-wrap" });
  wrap.append(h("section", { class: "sec alt" }, h("div", { class: "wrap" },
    title(["Who's on ", h("em", {}, "top"), "?"], "The best players right now. Click a name to see all of their stats."), tabs, stage)));
  const hue = (name) => { let x = 0; for (const ch of String(name)) x = (x * 31 + ch.charCodeAt(0)) >>> 0; return "hsl(" + (x % 360) + " 45% 40%)"; };
  const showBoard = (games, g) => {
    tabs.replaceChildren(...games.map((x) => h("button", { "aria-pressed": String(x.id === g.id), onclick: () => showBoard(games, x) }, x.title)));
    const st = g.stats[0];
    if (!st) { stage.replaceChildren(empty("trophy", "No " + g.title + " stats yet. They show up after people play.")); return; }
    stage.replaceChildren(h("div", { class: "skeleton" }));
    api("stats/top?game=" + encodeURIComponent(g.id) + "&stat=" + encodeURIComponent(st.id) + "&limit=5").then((t) => {
      if (!t.rows.length) { stage.replaceChildren(empty("trophy", "Nobody is on the " + g.title + " board yet. Be the first.")); return; }
      const link = (x) => h("a", { href: "#stats?game=" + g.id + "&name=" + encodeURIComponent(x.name) }, x.name);
      const pod = (x, n) => {
        if (!x) return h("div", {});
        const head = h("div", { class: "head", "aria-hidden": "true" }, x.name.charAt(0).toUpperCase());
        head.style.background = hue(x.name);
        wearSkin(head, x.name);
        return h("div", { class: "pod p" + n }, head, link(x), h("span", { class: "val" }, statValue(t.stat, x.value) + " " + st.label.toLowerCase()), h("div", { class: "block" }, n));
      };
      stage.replaceChildren(h("div", { class: "podium" }, pod(t.rows[1], 2), pod(t.rows[0], 1), pod(t.rows[2], 3)),
        h("ol", { class: "runners" }, ...t.rows.slice(3).map((x) => h("li", {}, h("span", { class: "place" }, x.place), link(x), h("b", {}, statValue(t.stat, x.value))))),
        h("div", { class: "board-foot" }, h("span", {}, g.title + " · " + st.label), h("a", { class: "btn", href: "#leaderboard?game=" + g.id }, icon("trophy"), "Full leaderboards")));
    }).catch(() => stage.replaceChildren(empty("trophy", "Couldn't load this board.")));
  };
  api("stats/games").then((r) => {
    const order = ["plebmc", "plebnite", "plebrpg"];
    const games = order.map((id) => r.games.find((g) => g.id === id)).filter(Boolean).concat(r.games.filter((g) => !order.includes(g.id)));
    if (games.length) showBoard(games, games[0]);
    else stage.replaceChildren(empty("trophy", "No stats yet."));
  }).catch(() => stage.replaceChildren(empty("trophy", "Leaderboards are unavailable right now.")));

  // how to join
  wrap.append(h("section", { class: "sec" }, h("div", { class: "wrap" },
    title(["Playing in ", h("em", {}, "3 steps")], "No launcher, no payment. It runs in your browser."),
    h("div", { class: "steps3" },
      h("div", { class: "step3" }, h("h3", {}, "Get PlebClient"), h("p", {}, "Play in your browser, or grab the Forge 1.8.9 version."),
        h("a", { class: "btn", href: dl }, icon("download"), "Downloads")),
      h("div", { class: "step3" }, h("h3", {}, "Make your account"), h("p", {}, "Pick your name and a password on the client's start screen. It keeps your coins and cosmetics yours."),
        me ? h("a", { class: "btn", href: "#me" }, icon("user"), "My account") : h("a", { class: "btn", href: "#register" }, icon("plus"), "Create account")),
      h("div", { class: "step3" }, h("h3", {}, "Join the server"), h("p", {}, c.serverIp ? "Add " + c.serverIp + " to your server list and hop in." : "Pick PlebMc in your server list and hop in."),
        c.serverIp ? h("button", { onclick: () => copyIp(c.serverIp) }, icon("copy"), "Copy address") : null)))));

  // vote
  if (voteLinks().length) {
    const r = rewardText();
    wrap.append(h("section", { class: "sec", style: "padding-top:0" }, h("div", { class: "wrap" }, h("div", { class: "vote-strip" },
      plebio("love", { cls: "vs-pal", scale: 1 }) || h("div", { class: "vs-coin" }, icon("heart")),
      h("div", {}, h("h2", {}, r ? "Vote every day, get " + r : "Vote for " + info.site + " every day"),
        h("p", {}, "It takes a few seconds and helps new players find the server.")),
      h("a", { class: "btn big", href: "#vote" }, "Vote now", icon("arrow"))))));
  }

  // FAQ
  const faq = c.faq || [];
  if (faq.length) {
    wrap.append(h("section", { class: "sec alt", id: "faq" }, h("div", { class: "wrap" },
      title(["Questions? ", h("em", {}, "Answers.")]),
      h("div", { class: "qa" }, ...faq.map((f, i) => h("details", { open: i === 0 }, h("summary", {}, f.q), h("p", {}, f.a)))),
      h("p", { class: "qa-foot" }, "Still stuck? ", c.discordUrl ? h("a", { href: c.discordUrl, target: "_blank", rel: "noopener" }, "Ask on our Discord.") : "Ask a staff member in game."))));
  }

  // the last banner: a TNT block
  wrap.append(h("section", { class: "sec" }, h("div", { class: "wrap" },
    h("div", { class: "tnt" }, plebio("onfire", { cls: "tnt-pal", scale: 1.5 }), h("div", { class: "label" }, h("h2", {}, c.ctaTitle || ""))),
    h("div", { class: "tnt-foot" }, h("p", {}, c.ctaText || ""),
      h("div", { class: "hero-cta" }, h("a", { class: "btn primary big", href: dl }, icon("download"), "Download PlebClient"),
        c.discordUrl ? h("a", { class: "btn big", href: c.discordUrl, target: "_blank", rel: "noopener" }, icon("discord"), "Join the Discord") : h("a", { class: "btn big", href: "#shop" }, icon("bag"), "Browse the shop"))))));
  return wrap;
}

function leaderRow(p) {
  return h("tr", {}, h("td", { style: "width:40px" }, h("span", { class: "rank" + (p.rank <= 3 ? " r" + p.rank : "") }, p.rank)),
    h("td", {}, initial(p.name), p.name), h("td", { class: "num" }, h("b", {}, fmt(p.coins)), " ", h("span", { class: "muted" }, coinName())),
    h("td", { class: "num muted" }, fmt(p.items) + " items"), h("td", { class: "num muted" }, fmt(p.streak) + " day streak"));
}

function statValue(st, v) {
  if (st && st.kind === "time") {
    const hrs = v / 3600;
    return hrs >= 1 ? (Math.round(hrs * 10) / 10).toLocaleString() + " h" : Math.round(v / 60) + " min";
  }
  return Number.isInteger(v) ? fmt(v) : (Math.round(v * 100) / 100).toLocaleString();
}

/** One stat's top 10 as a small card, like the Discord bot's leaderboard embeds. */
function statBoard(game, b, open) {
  const rows = b.rows.length ? b.rows.map((x) => h("li", {},
    h("span", { class: "rank" + (x.place <= 3 ? " r" + x.place : "") }, x.place),
    h("a", { href: "#stats?game=" + game.id + "&name=" + encodeURIComponent(x.name) }, initial(x.name), x.name),
    h("b", {}, statValue(b.stat, x.value))))
    : [h("li", { class: "muted none" }, "Nobody yet.")];
  return h("div", { class: "board" },
    h("div", { class: "board-head" }, icon("trophy"), h("h3", {}, b.stat.label), h("span", { class: "spacer" }),
      b.rows.length ? h("button", { class: "linkish", onclick: () => open(b.stat.id) }, "Full board →") : null),
    h("ol", {}, ...rows));
}

/** Stats and leaderboards: PlebMc, PlebNite and PlebRPG. "All stats" shows every stat's top 10 at once, or pick one stat for its top 100. */
async function leaderboardView() {
  const games = (await api("stats/games")).games;
  const p = params();
  let game = games.find((g) => g.id === p.game) || games[0];
  let stat = (game.stats.find((s) => s.id === p.stat) || {}).id || "";
  const gameChips = h("div", { class: "chips" });
  const statChips = h("div", { class: "chips" });
  const out = h("div", {});
  const body = h("tbody");
  const head = h("h2", {});
  const keep = () => history.replaceState(null, "", "#leaderboard?" + new URLSearchParams({ game: game.id, stat }).toString());
  const pg = pager(body, async (offset) => {
    const r = await api("stats/top?game=" + encodeURIComponent(game.id) + "&stat=" + encodeURIComponent(stat) + "&offset=" + offset + "&limit=25");
    return { items: r.rows.map((x) => ({ ...x, st: r.stat })), total: r.total, more: offset + r.rows.length < r.total };
  }, (x) => h("tr", {}, h("td", { style: "width:40px" }, h("span", { class: "rank" + (x.place <= 3 ? " r" + x.place : "") }, x.place)),
    h("td", {}, h("a", { href: "#stats?game=" + game.id + "&name=" + encodeURIComponent(x.name) }, initial(x.name), x.name)),
    h("td", { class: "num" }, h("b", {}, statValue(x.st, x.value)))),
  { empty: () => h("tr", {}, h("td", { class: "muted" }, "Nobody is on this board yet. Stats show up after people play.")) });
  const single = h("div", {}, head, h("div", { class: "scroll" }, h("table", {}, body)), pg.more);
  let ask = 0;
  const showAll = () => {
    const mine = ++ask;
    out.replaceChildren(h("p", { class: "muted" }, "Loading…"));
    api("stats/boards?game=" + encodeURIComponent(game.id)).then((r) => {
      if (mine !== ask) return;
      out.replaceChildren(h("div", { class: "boards" }, ...r.boards.map((b) => statBoard(r.game, b, pick))));
    }).catch((e) => { if (mine === ask) out.replaceChildren(h("p", { class: "muted" }, e.message)); });
  };
  const show = () => {
    draw();
    keep();
    if (stat) { ask++; out.replaceChildren(single); pg.reset(); } else showAll();
  };
  const pick = (id) => { stat = id; show(); window.scrollTo({ top: 0 }); };
  const draw = () => {
    gameChips.replaceChildren(...games.map((g) => h("button", { class: "chip", "aria-pressed": String(g.id === game.id), onclick: () => {
      game = g; stat = ""; show();
    } }, g.title)));
    statChips.replaceChildren(h("button", { class: "chip", "aria-pressed": String(!stat), onclick: () => pick("") }, "All stats"),
      ...game.stats.map((s) => h("button", { class: "chip", "aria-pressed": String(s.id === stat), onclick: () => pick(s.id) }, s.label)));
    const st = game.stats.find((s) => s.id === stat);
    head.textContent = game.title + (st ? " · " + st.label : "");
  };
  show();
  return card(null, h("p", { class: "muted" }, "The top 10 for every stat. Pick a stat for its top 100, or click a name to see their stats."),
    gameChips, h("div", { style: "height:8px" }), statChips, h("div", { style: "height:16px" }), out);
}

/** Stats: type a player and see their stats, one game at a time (PlebMc, PlebNite, PlebRPG). */
async function statsView() {
  const p = params();
  const q = h("input", { type: "search", placeholder: "Type a player's name", "aria-label": "Player name", maxlength: 16, spellcheck: "false", value: p.name || "" });
  const tabs = h("div", { class: "chips" });
  const out = h("div", {});
  const result = h("div", {});
  let data = null;
  let game = p.game || "plebmc";
  const keep = () => history.replaceState(null, "", "#stats?" + new URLSearchParams({ name: data ? data.name : q.value.trim(), game }).toString());
  const draw = () => {
    if (!data) return;
    const order = ["plebmc", "plebnite", "plebrpg"];
    const titles = { plebnite: "PlebNite", plebrpg: "PlebRPG", plebmc: "PlebMc" };
    const all = order.map((id) => data.games.find((g) => g.id === id) || { id, title: titles[id], stats: [] })
      .concat(data.games.filter((g) => !order.includes(g.id)));
    if (!all.some((g) => g.id === game)) game = all[0].id;
    tabs.replaceChildren(...all.map((g) => h("button", { class: "chip", "aria-pressed": String(g.id === game), onclick: () => { game = g.id; keep(); draw(); } }, g.title)));
    const g = all.find((x) => x.id === game);
    out.replaceChildren(g.stats.length
      ? h("div", { class: "tiles" }, ...g.stats.map((s) => tile("trophy", s.label, statValue(s, s.value),
        s.place ? h("a", { href: "#leaderboard?game=" + g.id + "&stat=" + s.id }, "#" + fmt(s.place) + " on the leaderboard") : null)))
      : empty("trophy", data.name + " hasn't played " + g.title + " yet."));
  };
  const look = (name) => {
    if (!name) return;
    data = null;
    result.replaceChildren(h("p", { class: "muted" }, "Loading…"));
    api("stats/player?name=" + encodeURIComponent(name)).then((r) => {
      data = r;
      keep();
      result.replaceChildren(h("div", { class: "row" }, initial(r.name), h("h2", {}, r.name), r.rank ? h("span", { class: "pill" }, r.rank) : null,
        h("span", { class: "muted seen", title: r.online || !r.lastSeen ? "" : when(r.lastSeen) }, h("span", { class: "dot" + (r.online ? " on" : "") }),
          r.online ? "Online now" : r.lastSeen ? "Last seen " + ago(r.lastSeen) : "Not seen in game yet")),
        h("div", { style: "height:12px" }), tabs, h("div", { style: "height:14px" }), out);
      draw();
    }).catch((e) => result.replaceChildren(h("p", { class: "muted" }, e.message)));
  };
  q.addEventListener("keydown", (e) => { if (e.key === "Enter") look(q.value.trim()); });
  if (p.name) look(p.name);
  else if (me) { q.value = me.name; look(me.name); }
  return card(h("div", { class: "row", style: "width:100%" }, h("h2", {}, "Player stats"), h("span", { class: "spacer" }),
    h("label", { class: "searchbox" }, icon("search"), q), h("button", { onclick: () => look(q.value.trim()) }, "Look up")),
  h("p", { class: "muted" }, "Type a name and press Enter. Pick PlebMc, PlebNite or PlebRPG to switch."), result);
}

const scriptLoads = {};
function loadScript(src) {
  if (!scriptLoads[src]) {
    scriptLoads[src] = new Promise((ok) => {
      const el = h("script", { src });
      el.onload = () => ok(true);
      el.onerror = () => ok(false);
      document.head.append(el);
    });
  }
  return scriptLoads[src];
}
async function previews() {
  if (!(await loadScript("/static/vendor/three.min.js"))) return null;
  if (!(await loadScript("/static/preview.js"))) return null;
  return window.PlebPreview || null;
}

const forSale = (i) => i.price != null && i.price >= 0;
const catName = { hat: "Hats", wings: "Wings", cloak: "Cloaks", trail: "Trails" };

// rarity comes from the price, like the colors of item names in game
function rarity(i) {
  if (!forSale(i)) return ["exclusive", "Exclusive"];
  if (i.price >= 10000) return ["legendary", "Legendary"];
  if (i.price >= 4000) return ["epic", "Epic"];
  if (i.price >= 1500) return ["rare", "Rare"];
  return ["common", "Common"];
}
const catIcon = (c) => (c === "hat" ? "hat" : c === "cloak" ? "cloak" : c === "wings" ? "wings" : c === "trail" ? "sparkle" : "bag");

async function shopView() {
  const [items, account] = await Promise.all([catalog(), me ? api("me").catch(() => null) : null]);
  let player = account && account.player;
  const P = await previews();
  if (!items.length) return card(null, empty("bag", "The shop shows up once the server connects."));
  const q = h("input", { type: "search", placeholder: "Search cosmetics", "aria-label": "Search cosmetics" });
  const sort = select("shop-sort", [["featured", "Featured"], ["low", "Price: low to high"], ["high", "Price: high to low"], ["name", "Name"]], "featured");
  sort.setAttribute("aria-label", "Sort");
  let cat = "all";
  let only = "all"; // all | owned | missing
  const cats = ["all", ...new Set(items.map((i) => i.category))];
  const tabs = h("div", { class: "shop-tabs", role: "tablist" });
  const filters = h("div", { class: "chips" });
  const grid = h("div", { class: "shop-grid" });
  const count = h("span", { class: "muted" });
  const owned = (i) => !!(player && player.owned.includes(i.id));

  // the display case: a live 3D preview with the item's details and Buy
  const canvas = h("canvas", { class: "stage", "aria-label": "3D preview, drag to turn" });
  const stage = P ? P.stage(canvas) : null;
  const flat = h("div", { class: "stage flat hidden" });
  const info = h("div", { class: "case-info" });
  const wallet = h("div", { class: "wallet" });
  const showWallet = () => wallet.replaceChildren(...(player
    ? [icon("coin"), h("div", {}, h("b", {}, fmt(player.coins)), h("small", {}, coinName())), h("span", { class: "spacer" }), h("span", { class: "muted" }, fmt(player.owned.length) + " owned")]
    : me ? [icon("coin"), h("span", { class: "muted" }, "Join the server once to see your coins here.")]
      : [icon("coin"), h("span", { class: "muted" }, "Sign in to buy with your coins."), h("span", { class: "spacer" }), h("a", { class: "btn small primary", href: "#login?next=%23shop" }, "Sign in")]));
  let selected = null;
  let buying = false;

  function buyArea(i) {
    if (!forSale(i)) return h("p", { class: "muted" }, "Not for sale. This one is earned or given out.");
    if (owned(i)) return h("span", { class: "pill good big-pill" }, icon("check"), "You own this");
    if (!me) return h("a", { class: "btn primary big full", href: "#login?next=%23shop" }, "Sign in to buy");
    if (!player) return h("p", { class: "muted" }, "Join the server once, then you can buy here.");
    if (i.permission) return h("p", { class: "muted" }, "Needs a rank. Buy it in game.");
    if (player.coins < i.price) {
      const pct = Math.max(2, Math.min(100, Math.round(player.coins / i.price * 100)));
      const bar = h("i");
      bar.style.width = pct + "%";
      return h("div", { style: "display:grid;gap:8px" }, h("div", { class: "meter" }, bar),
        h("p", { class: "muted", style: "margin:0" }, "You need " + fmt(i.price - player.coins) + " more " + coinName() + "."),
        (content().voteLinks || []).length ? h("a", { class: "btn", href: "#vote" }, icon("heart"), "Vote for free coins") : null);
    }
    return h("button", { class: "primary big full", disabled: buying, onclick: () => buy(i) }, icon("bag"), buying ? "Buying..." : "Buy for " + fmt(i.price));
  }
  function showInfo() {
    const i = selected;
    if (!i) { info.replaceChildren(h("p", { class: "muted" }, "Pick something to see it.")); return; }
    const [rk, rl] = rarity(i);
    info.replaceChildren(...[
      h("div", { class: "row", style: "gap:6px" }, h("span", { class: "rar " + rk }, rl), h("span", { class: "pill" }, i.category), i.collection ? h("span", { class: "pill" }, i.collection) : null),
      h("h2", { class: "case-name" }, i.name),
      i.description ? h("p", { class: "muted" }, i.description) : null,
      i.creator ? h("p", { class: "faint", style: "font-size:12.5px" }, "Made by " + i.creator) : null,
      forSale(i) ? h("div", { class: "case-price" }, icon("coin"), fmt(i.price), h("small", {}, coinName())) : null,
      buyArea(i)].filter(Boolean));
    showWallet();
  }
  function pick(i, scroll) {
    selected = i;
    showInfo();
    const can = stage && P.canShow(i);
    canvas.classList.toggle("hidden", !can);
    flat.classList.toggle("hidden", !!can);
    if (can) stage.show(i);
    else flat.replaceChildren(icon(catIcon(i.category)), h("span", {}, stage ? "No 3D preview for this one. See it in game." : "3D previews need WebGL."));
    if (scroll && window.matchMedia("(max-width: 900px)").matches) canvas.parentNode.scrollIntoView({ behavior: "smooth", block: "start" });
    grid.querySelectorAll(".citem").forEach((el) => el.classList.toggle("on", el.dataset.id === i.id));
  }

  async function buy(i) {
    if (buying) return;
    if (!confirm("Buy " + i.name + " for " + fmt(i.price) + " " + coinName() + "?")) return;
    buying = true;
    showInfo();
    try {
      const r = await api("shop/buy", { item: i.id });
      let o = null;
      for (let n = 0; n < 40; n++) {
        await new Promise((ok) => setTimeout(ok, 1500));
        o = await api("shop/order?id=" + r.id);
        if (o.status !== "pending" && o.status !== "sent") break;
      }
      if (o && o.player) player = o.player;
      if (o && o.status === "done") toast("Bought " + i.name + "! It's yours in game.");
      else if (o && o.status === "failed") toast("Couldn't buy it: " + (o.result || "the server said no"), false);
      else toast("The server hasn't answered yet. Check back in a minute.", false);
    } catch (e) {
      toast(e.message, false);
    }
    buying = false;
    showInfo();
    draw();
  }

  const tile = (i) => {
    const [rk, rl] = rarity(i);
    const pic = h("div", { class: "pic" }, icon(catIcon(i.category)));
    if (P && P.canShow(i)) P.thumb(i).then((url) => { if (url) pic.replaceChildren(h("img", { src: url, alt: "" })); });
    return h("button", { class: "citem " + rk + (selected && selected.id === i.id ? " on" : ""), "data-id": i.id, onclick: () => pick(i, true), title: i.description || i.name },
      owned(i) ? h("span", { class: "own" }, icon("check"), "Owned") : null, pic,
      h("span", { class: "rar " + rk }, rl), h("b", {}, i.name),
      h("span", { class: "cprice" }, ...(forSale(i) ? [icon("coin"), fmt(i.price)] : ["Not for sale"])));
  };
  const draw = () => {
    const n = (c) => items.filter((i) => c === "all" || i.category === c).length;
    tabs.replaceChildren(...cats.map((c) => h("button", { role: "tab", "aria-selected": String(c === cat), onclick: () => { cat = c; draw(); } },
      icon(c === "all" ? "grid" : catIcon(c)), c === "all" ? "Everything" : catName[c] || c, h("small", {}, n(c)))));
    filters.replaceChildren(...(player ? [["all", "All"], ["missing", "Not owned"], ["owned", "Owned"]] : []).map(([id, label]) =>
      h("button", { class: "chip", "aria-pressed": String(only === id), onclick: () => { only = id; draw(); } }, label)));
    const t = q.value.trim().toLowerCase();
    let list = items.filter((i) => (cat === "all" || i.category === cat) && (!t || i.name.toLowerCase().includes(t) || i.id.includes(t)) &&
      (only === "all" || owned(i) === (only === "owned")));
    const price = (i) => (forSale(i) ? i.price : Infinity);
    if (sort.value === "low") list = [...list].sort((a, b) => price(a) - price(b));
    if (sort.value === "high") list = [...list].sort((a, b) => (forSale(b) ? b.price : -1) - (forSale(a) ? a.price : -1));
    if (sort.value === "name") list = [...list].sort((a, b) => a.name.localeCompare(b.name));
    count.textContent = list.length + (list.length === 1 ? " cosmetic" : " cosmetics");
    grid.replaceChildren(...list.map(tile));
    if (!list.length) grid.append(empty("bag", "Nothing matches."));
  };
  q.addEventListener("input", draw);
  sort.addEventListener("change", draw);
  draw();
  const first = items.find((i) => forSale(i) && (!P || P.canShow(i))) || items[0];
  pick(first, false);
  return h("div", { class: "shop" },
    h("aside", { class: "case" }, wallet, h("div", { class: "case-stage" }, canvas, flat, stage ? h("span", { class: "hint" }, "Drag to spin") : null), info),
    h("div", { class: "shop-main" }, tabs,
      h("div", { class: "shop-bar" }, h("label", { class: "searchbox" }, icon("search"), q), sort, filters, h("span", { class: "spacer" }), count),
      grid,
      h("p", { class: "faint", style: "margin-top:16px;font-size:13px" }, "You can also buy in game: press Right Shift and open the Shop. What you buy here shows up in game right away.")));
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
  h("div", { class: "center" }, plebio("bow", { scale: 1.5 }) || h("img", { src: siteLogo(), alt: "", style: "width:72px;height:72px;image-rendering:pixelated;object-fit:contain" })),
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
  h("div", { class: "center" }, h("img", { src: siteLogo(), alt: "", style: "width:72px;height:72px;image-rendering:pixelated;object-fit:contain" })),
  h("h1", { class: "center" }, "Create your account"),
  info.openRegistration ? h("p", { class: "muted center" }, "Pick the name you play as, then sign in with it on the PlebClient start screen.") :
    h("div", {}, h("p", { class: "muted" }, "Sign-ups need a code on this site right now:"),
      h("ol", { class: "muted" }, h("li", {}, "Ask an admin for a sign-up code"), h("li", {}, "Type the code here"))),
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
  wrap.append(h("div", { class: "grid g2" }, skinCard(me.name), nameCard(r)));
  wrap.append(h("div", { class: "grid g2" }, passwordCard(), signoutCard()));
  return wrap;
}

function nameCard(r) {
  const out = h("div");
  const days = Math.ceil((r.renameWait || 0) / 86400);
  const form = h("form", { class: "card", onsubmit: async (e) => {
    e.preventDefault();
    const name = form.elements.namedItem("name").value.trim();
    if (!confirm("Change your name to " + name + "? You can't change it again for " + (r.renameDays || 7) + " days.")) return;
    try {
      await api("me/name", { name, password: form.elements.namedItem("password").value });
      location.reload();
    } catch (err) { message(out, err.message); }
  } }, h("h2", {}, "Change username"),
  h("p", { class: "muted" }, "Your coins, cosmetics and rank move to the new name, and you sign in with it from then on. ",
    "Things saved on a game server under your old name (like an inventory) stay with the old name. ",
    "You can do this once every " + (r.renameDays || 7) + " days."),
  days ? h("p", { class: "muted" }, h("b", {}, "You can change your name again in " + days + " day" + (days === 1 ? "" : "s") + ".")) : null,
  field("New username", { id: "nname", name: "name", required: true, minlength: 3, maxlength: 16, pattern: "[A-Za-z0-9_]{3,16}", autocomplete: "off", disabled: !!days }),
  field("Your password", { id: "nname-pw", name: "password", type: "password", required: true, autocomplete: "current-password", disabled: !!days }),
  h("div", { style: "height:14px" }), h("button", { class: "primary", type: "submit", disabled: !!days }, "Change username"), out);
  return form;
}

function skinCard(name) {
  const out = h("div");
  const file = h("input", { type: "file", id: "skin-file", accept: "image/png", onchange: () => {
    const f = file.files[0];
    if (!f) return;
    if (f.size > 24 * 1024) { file.value = ""; return message(out, "That picture is too big for a skin."); }
    const rd = new FileReader();
    rd.onload = async () => {
      try { await api("me/skin", { png: String(rd.result) }); location.reload(); } catch (err) { message(out, err.message); }
      file.value = "";
    };
    rd.readAsDataURL(f);
  } });
  return h("div", { class: "card" }, h("h2", {}, "Profile picture"),
    h("p", { class: "muted" }, "Your picture is the face of your Minecraft skin. Pick your skin file (the 64 by 64 PNG) to set it."),
    h("div", { class: "row" }, initialBig(name), h("div", {}, h("label", { for: "skin-file" }, "Skin file"), file)),
    h("div", { style: "height:14px" }),
    h("button", { type: "button", class: "ghost", onclick: async () => {
      try { await api("me/skin", { remove: true }); location.reload(); } catch (err) { message(out, err.message); }
    } }, "Remove my skin"), out);
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
    const b = h("div", { title: new Date(x.day * 1000).toLocaleDateString() + ": " + x.count + " new" }, h("b", { class: x.count ? "" : "zero" }, fmt(x.count)));
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
      h("a", { class: "btn", href: "#makers" }, icon("hammer"), "Cosmetic makers"),
      h("a", { class: "btn", href: "#audit" }, icon("list"), "Admin log")))));
  wrap.append(h("div", { style: "height:18px" }));
  wrap.append(giveAllCard());
  return wrap;
}

function giveAllCard() {
  const out = h("div");
  const amount = h("input", { type: "number", min: 1, step: 50, value: 100, "aria-label": "Amount", style: "max-width:140px" });
  const btn = h("button", { class: "primary", onclick: async () => {
    const n = Math.floor(Number(amount.value));
    if (!(n > 0)) return message(out, "Give a whole number above 0.");
    if (!confirm("Give " + fmt(n) + " " + coinName() + " to " + (only.checked ? "everyone online on PlebClient" : "everyone online, on any client,") + " right now?")) return;
    btn.disabled = true;
    try {
      await api("admin/command", { action: "coinsall", amount: n, clientOnly: only.checked });
      message(out, "Sent. " + (only.checked ? "Everyone online on PlebClient gets " : "Everyone online gets ") + fmt(n) + " " + coinName() + " within a few seconds.", true);
    } catch (e) { message(out, e.message); }
    btn.disabled = false;
  } }, icon("coin"), "Give to everyone online");
  const only = h("input", { type: "checkbox", id: "giveall-only" });
  only.checked = true;
  return card("Give coins to everyone online", h("p", { class: "muted" }, "A gift for everyone playing right now. This switch is only for this gift: it doesn't change plebclient-only on the server."),
    h("label", { class: "check", for: "giveall-only" }, only, " PlebClient players only (off: everyone online gets it)"),
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
  h("td", { class: "cr-lead" }, initial(p.name), p.online ? h("span", { class: "dot on", title: "Online" }) : null, p.name, " ", rolePill(p.role)),
  h("td", { class: "num", "data-label": coinName() }, fmt(p.coins)), h("td", { class: "num", "data-label": "Items" }, fmt(p.items)),
  h("td", { "data-label": "Account" }, p.locked ? h("span", { class: "pill locked" }, "locked") : p.account ? h("span", { class: "pill good" }, "yes") : h("span", { class: "muted" }, "none")),
  h("td", { class: "muted", "data-label": "Last seen" }, ago(p.lastSeen))), { empty: () => h("tr", {}, h("td", { class: "muted", colspan: "5" }, "Nobody found.")) });
  const keep = () => history.replaceState(null, "", "#players?" + new URLSearchParams(state).toString());
  const drawChips = () => chips.replaceChildren(...[["all", "Everyone"], ["online", "Online"], ["accounts", "Has account"], ["noaccount", "No account"], ["staff", "Staff"], ["locked", "Locked"]]
    .map(([k, t]) => h("button", { class: "chip", "aria-pressed": String(k === state.filter), onclick: () => { state.filter = k; drawChips(); keep(); pg.reset(); } }, t)));
  drawChips();
  let timer;
  search.addEventListener("input", () => { clearTimeout(timer); timer = setTimeout(() => { state.q = search.value.trim(); keep(); pg.reset(); }, 250); });
  sortSel.addEventListener("change", () => { state.sort = sortSel.value; keep(); pg.reset(); });
  pg.reset();
  return h("div", {}, card("Players", status, h("div", { class: "row" }, search, sortSel), h("div", { style: "height:10px" }), chips, h("div", { style: "height:10px" }),
    h("div", { class: "scroll" }, h("table", { class: "cardrows" }, h("thead", {}, h("tr", {}, h("th", {}, "Player"), h("th", { class: "num" }, coinName()), h("th", { class: "num" }, "Items"), h("th", {}, "Account"), h("th", {}, "Last seen"))), body)), pg.more),
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
      h("p", { class: "muted" }, h("span", { class: "dot" + (r.online ? " on" : "") }),
        r.online ? "Online now" : h("span", { title: r.lastSeen ? when(r.lastSeen) : "" }, r.lastSeen ? "Last seen " + ago(r.lastSeen) : "Never seen"),
        acc ? " · account since " + when(acc.created) + " · last sign-in " + ago(acc.lastLogin) + " · " + r.devices + " remembered client(s)" : " · no account yet"),
      out)));
  wrap.append(h("div", { class: "tiles" },
    tile("coin", coinName(), fmt(p ? p.coins : 0)),
    tile("bag", "Cosmetics", fmt(p ? p.owned.length : 0)),
    tile("trophy", "Login streak", fmt(p ? p.streak : 0)),
    tile("clock", "Last seen", r.online ? "Online now" : r.lastSeen ? ago(r.lastSeen) : "Never", r.online || !r.lastSeen ? null : when(r.lastSeen))));

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
  const mod = card("Moderation", h("div", { class: "row" }, reason, h("button", { class: "danger", onclick: () => act({ action: "kick", reason: reason.value }) }, "Kick")),
    h("p", { class: "muted", style: "margin:10px 0 0" }, h("a", { href: "#punishments?q=" + encodeURIComponent(r.name) }, "Bans, mutes, warnings and kicks for " + r.name + " →")));
  if (acc) {
    const npw = h("input", { type: "password", placeholder: "New password (8+)", autocomplete: "new-password" });
    const nname = h("input", { placeholder: "New username", maxlength: 16, autocomplete: "off", "aria-label": "New username" });
    mod.append(h("div", { style: "height:12px" }), h("div", { class: "row" }, npw, h("button", { onclick: () => accAct({ action: "password", password: npw.value }) }, "Reset password")),
      h("div", { style: "height:12px" }), h("div", { class: "row" }, nname, h("button", { onclick: () => {
        const to = nname.value.trim();
        if (!to || !confirm("Rename " + r.name + " to " + to + "? Their coins, cosmetics and rank move to the new name.")) return;
        api("admin/account", { name: r.name, action: "rename", newName: to })
          .then(() => { toast("Renamed to " + to + "."); location.hash = "#player?name=" + encodeURIComponent(to); })
          .catch((e) => toast(e.message, false));
      } }, "Change username")),
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

// ==================== ideas (the staff's own board) ====================
const IDEA_PRIORITY = { urgent: ["Urgent", "bad"], high: ["High", "warn"], medium: ["Medium", "admin"], low: ["Low", ""] };
const IDEA_STATUS = { idea: "Idea", planned: "Planned", doing: "Working on it", done: "Done", dropped: "Dropped" };

async function ideasView() {
  let data = await api("admin/ideas");
  let hideDone = false;
  try { hideDone = localStorage.getItem("ideas-hide-done") === "1"; } catch (e) { /* fine */ }
  const board = h("div", { class: "stack" });
  const send = async (body) => {
    try { data = await api("admin/ideas", body); draw(); return true; } catch (e) { toast(e.message, false); return false; }
  };
  const choice = (map, value, label) => h("select", { "aria-label": label },
    Object.entries(map).map(([v, t]) => h("option", { value: v, selected: v === value }, Array.isArray(t) ? t[0] : t)));

  // the form for a new idea, or to change one
  const ideaForm = (sec, idea, close) => {
    const name = h("input", { placeholder: "Idea", maxlength: 100, value: idea ? idea.name : "", "aria-label": "Idea name" });
    const text = h("textarea", { rows: 2, placeholder: "Description (optional)", maxlength: 4000, "aria-label": "Description" });
    text.value = idea ? idea.description : "";
    const pri = choice(IDEA_PRIORITY, idea ? idea.priority : "medium", "Priority");
    const st = choice(IDEA_STATUS, idea ? idea.status : "idea", "Status");
    const where = h("select", { "aria-label": "Section" }, data.sections.map((x) => h("option", { value: x.id, selected: x.id === sec.id }, x.name)));
    const form = h("form", { class: "idea-form", onsubmit: async (e) => {
      e.preventDefault();
      if (!name.value.trim()) return name.focus();
      await send({ action: "save", id: idea ? idea.id : undefined, section: +where.value, name: name.value, description: text.value, priority: pri.value, status: st.value });
    } }, name, text, h("div", { class: "row" }, pri, st, idea ? where : null, h("span", { class: "spacer" }),
      close ? h("button", { type: "button", class: "ghost", onclick: close }, "Cancel") : null,
      h("button", { class: "primary", type: "submit" }, icon(idea ? "check" : "plus"), idea ? "Save" : "Add idea")));
    return form;
  };

  const ideaBlock = (sec, idea) => {
    const [plabel, pcls] = IDEA_PRIORITY[idea.priority] || [idea.priority, ""];
    const st = choice(IDEA_STATUS, idea.status, "Status of " + idea.name);
    st.className = "idea-status";
    st.onchange = () => send({ action: "set", id: idea.id, status: st.value });
    const block = h("div", { class: "idea p-" + idea.priority + " s-" + idea.status },
      h("div", { class: "idea-top" }, h("span", { class: "pill " + pcls }, plabel), h("span", { class: "spacer" }), st),
      h("h3", {}, idea.name),
      idea.description ? h("p", {}, idea.description) : null,
      h("div", { class: "idea-foot" }, h("span", { class: "muted" }, idea.author + " · " + ago(idea.created)), h("span", { class: "spacer" }),
        h("button", { class: "small ghost", onclick: () => block.replaceWith(h("div", { class: "idea editing" }, ideaForm(sec, idea, draw))) }, "Edit"),
        h("button", { class: "small ghost danger", onclick: () => { if (confirm("Delete the idea \"" + idea.name + "\"?")) send({ action: "delete", id: idea.id }); } }, "Delete")));
    return block;
  };

  const sectionCard = (sec, i) => {
    const shown = sec.ideas.filter((x) => !hideDone || (x.status !== "done" && x.status !== "dropped"));
    const hidden = sec.ideas.length - shown.length;
    const adder = h("div", { class: "idea add" });
    const closed = () => adder.replaceChildren(h("button", { class: "ghost", onclick: () => { adder.replaceChildren(ideaForm(sec, null, closed)); adder.querySelector("input").focus(); } }, icon("plus"), "Add an idea"));
    closed();
    return h("section", { class: "card idea-sec" },
      h("div", { class: "card-head" }, h("h2", {}, sec.name), h("span", { class: "muted" }, sec.ideas.length + (sec.ideas.length === 1 ? " idea" : " ideas") + (hidden ? " · " + hidden + " finished hidden" : "")),
        h("span", { class: "spacer" }),
        h("button", { class: "small ghost", disabled: i === 0, title: "Move up", "aria-label": "Move " + sec.name + " up", onclick: () => send({ action: "section-move", section: sec.id, dir: "up" }) }, "↑"),
        h("button", { class: "small ghost", disabled: i === data.sections.length - 1, title: "Move down", "aria-label": "Move " + sec.name + " down", onclick: () => send({ action: "section-move", section: sec.id, dir: "down" }) }, "↓"),
        h("button", { class: "small ghost", onclick: () => { const n = prompt("Section name", sec.name); if (n && n.trim()) send({ action: "section-rename", section: sec.id, name: n }); } }, "Rename"),
        h("button", { class: "small ghost danger", onclick: () => {
          if (confirm("Delete the section \"" + sec.name + "\"" + (sec.ideas.length ? " and its " + sec.ideas.length + " idea(s)?" : "?"))) send({ action: "section-delete", section: sec.id });
        } }, "Delete")),
      h("div", { class: "idea-grid" }, shown.map((x) => ideaBlock(sec, x)), adder));
  };

  const newName = h("input", { placeholder: "New section, like Website", maxlength: 40, "aria-label": "New section name" });
  const hide = h("input", { type: "checkbox", id: "ideas-hide" });
  hide.checked = hideDone;
  hide.onchange = () => { hideDone = hide.checked; try { localStorage.setItem("ideas-hide-done", hideDone ? "1" : "0"); } catch (e) { /* fine */ } draw(); };
  const bar = h("form", { class: "row", onsubmit: async (e) => {
    e.preventDefault();
    if (newName.value.trim() && await send({ action: "section-add", name: newName.value })) newName.value = "";
  } }, newName, h("button", { type: "submit" }, icon("plus"), "Add section"), h("span", { class: "spacer" }),
    h("label", { class: "check", for: "ideas-hide" }, hide, " Hide finished ideas"));
  function draw() {
    board.replaceChildren(...(data.sections.length ? data.sections.map(sectionCard)
      : [empty("bulb", "No sections yet. Add one above, like PlebMc or Website.")]));
  }
  draw();
  return h("div", { class: "stack" },
    h("p", { class: "muted", style: "margin:0" }, "A board for the team's ideas. Only admins and owners can see it. The most urgent ideas come first in each section."),
    bar, board);
}

async function announceView() {
  const out = h("div");
  const text = h("textarea", { id: "atext", maxlength: 200, placeholder: "Double coins this weekend!" });
  const site = h("input", { type: "checkbox", id: "asite", checked: true });
  const game = h("input", { type: "checkbox", id: "agame", checked: true });
  const left = h("small", { class: "muted" }, "200 left");
  text.addEventListener("input", () => { left.textContent = 200 - text.value.length + " left"; });
  const cur = info.announcement;
  const editor = isOwner() ? await postsEditor() : null;
  return h("div", {}, ...[editor, h("div", { class: "grid g2" },
    card("Quick announcement (banner and game chat)",
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
      } }, icon("trash"), "Remove banner")) : empty("megaphone", "No banner right now.")))].filter(Boolean));
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

// ==================== admin: client updates ====================

/** True when version a is older than b ("2.0" vs "2.0.1"; "2.0" and "2.0.0" are the same). */
function olderVersion(a, b) {
  const k = (v) => { const p = String(v || "0").split(".").map((x) => parseInt(x, 10) || 0).slice(0, 4); while (p.length < 4) p.push(0); return p; };
  const x = k(a), y = k(b);
  for (let i = 0; i < 4; i++) if (x[i] !== y[i]) return x[i] < y[i];
  return false;
}

/** Owners: the website fetching its own new builds from GitHub. */
function panelUpdateCard(u, ro) {
  const box = h("div");
  const short = (b) => (b ? b.slice(0, 10) : "unknown");
  const draw = () => {
    const out = h("div");
    const same = u.latest && u.latest === u.running;
    const newer = u.latest && !same && (!u.runningAt || u.latestAt > u.runningAt);
    const act = (body, good) => async (e) => {
      e.target.disabled = true;
      try { u = await api("admin/update", body); draw(); if (good) toast(good(u)); } catch (err) { message(out, err.message); e.target.disabled = false; }
    };
    const auto = h("input", { type: "checkbox", id: "u-auto", disabled: ro || !u.enabled, onchange: (e) => act({ action: "auto", auto: e.target.checked })(e) });
    auto.checked = u.auto && u.enabled;
    box.replaceChildren(...[
      h("p", { class: "muted" }, "The website checks github.com/" + u.repo + " (" + u.branch + ") every " + u.minutes + " minutes. When a new panel build is merged there, it downloads it, tests that it starts, and restarts into it. Accounts and settings are not touched."),
      h("div", { class: "row", style: "gap:8px;flex-wrap:wrap;margin:10px 0" },
        h("span", { class: "pill" }, "Running " + short(u.running)),
        !u.enabled ? h("span", { class: "pill warn" }, "turned off on this server")
          : u.busy === "restarting" ? h("span", { class: "pill warn" }, "restarting")
            : same ? h("span", { class: "pill good" }, "up to date")
              : newer ? h("span", { class: "pill warn" }, "new build " + short(u.latest))
                : u.latest ? h("span", { class: "pill" }, "newer than GitHub") : h("span", { class: "pill" }, "not checked yet")),
      h("p", { class: "faint", style: "font-size:13px;margin:0 0 8px" }, u.checkedAt ? "Last checked " + ago(u.checkedAt) + "." : "Checks a minute or two after the site starts.",
        u.installedAt ? " Last update installed " + ago(u.installedAt) + "." : ""),
      u.error ? h("div", { class: "banner update-bar" }, icon("online"), h("span", {}, u.error)) : null,
      h("label", { class: "check", for: "u-auto" }, auto, " Install new builds automatically"),
      ro || !u.enabled ? (ro ? h("p", { class: "muted" }, "Only owners can change these.") : null) : h("div", { class: "row", style: "gap:8px;margin-top:8px" },
        h("button", { onclick: act({ action: "check" }, (r) => (r.latest === r.running ? "You're on the newest build." : "Checked.")) }, icon("refresh"), "Check now"),
        !same && u.latest ? h("button", { class: "primary", onclick: act({ action: "install" }, (r) => (r.busy === "restarting" ? "Installed. The site restarts in a few seconds." : "Nothing to install.")) }, "Update now") : null),
      out].filter(Boolean));
    if (u.busy === "restarting") setTimeout(async function again() { try { u = await api("admin/update"); draw(); } catch (e) { setTimeout(again, 2000); } }, 4000);
  };
  draw();
  return card("Website updates", box);
}

async function clientView() {
  const [r, upd] = await Promise.all([api("admin/client"), api("admin/update")]);
  const c = r.client;
  const out = h("div");
  const ro = !isOwner();
  const latest = h("input", { id: "clatest", value: c.latest, placeholder: "2.0", disabled: ro });
  const minimum = h("input", { id: "cmin", value: c.minimum, placeholder: "optional, e.g. 1.9", disabled: ro });
  const url = h("input", { id: "curl", value: c.url, placeholder: "https://plebmc.top/download", disabled: ro });
  const msg = h("input", { id: "cmsg", value: c.message, maxlength: 200, placeholder: "New: faster loading and new cloaks", disabled: ro });
  const vbody = h("tbody", {}, r.versions.map((v) => h("tr", {}, h("td", {}, "PlebClient " + v.version), h("td", {}, v.platform), h("td", { class: "num" }, fmt(v.n)),
    h("td", {}, c.latest && olderVersion(v.version, c.latest) ? h("span", { class: "pill warn" }, "outdated") : h("span", { class: "pill good" }, "up to date")))));
  return h("div", { class: "grid g2" },
    panelUpdateCard(upd, ro),
    card("PlebClient update checker",
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
      : empty("refresh", "No client has checked in yet.")),
    downloadsCard(r.downloads, ro));
}

function downloadsCard(list, ro) {
  const out = h("div");
  const rows = list.map((d) => ({
    kind: d.kind,
    name: h("input", { id: "dln-" + d.kind, value: d.name, maxlength: 60, disabled: ro, "aria-label": "Name" }),
    help: h("input", { id: "dlh-" + d.kind, value: d.help, maxlength: 200, disabled: ro, "aria-label": "Description" }),
    url: h("input", { id: "dl-" + d.kind, type: "url", value: d.url, placeholder: "https://… (empty hides it)", disabled: ro, maxlength: 500, "aria-label": "Link" }),
  }));
  return card("Downloads page",
    h("p", { class: "muted" }, "The name, description and link of each download. The files stay where they're hosted; leave a link empty to hide that download."),
    ...rows.flatMap((r) => [h("label", { for: "dln-" + r.kind }, "Name"), r.name, h("label", { for: "dlh-" + r.kind }, "Description"), r.help,
      h("label", { for: "dl-" + r.kind }, "Link"), r.url, h("div", { style: "height:12px;border-bottom:1px solid var(--line, rgba(255,255,255,.08));margin-bottom:6px" })]),
    h("div", { style: "height:8px" }),
    ro ? h("p", { class: "muted" }, "Only owners can change these.") : h("button", { class: "primary", onclick: async () => {
      const body = {};
      rows.forEach((r) => { body[r.kind] = { name: r.name.value.trim(), help: r.help.value.trim(), url: r.url.value.trim() }; });
      try {
        await api("admin/downloads", body);
        info = await api("info");
        message(out, "Saved.", true);
      } catch (e) { message(out, e.message); }
    } }, "Save"), out);
}

// how to get each build going, by its kind
/** Real captures of the client, shown on the downloads page. */
const CLIENT_SHOTS = [
  ["/static/home/client-menu.jpg", "Main menu", "Everything one click away: play, servers, shop, mods and settings."],
  ["/static/home/mods.jpg", "Mods", "34 mods you switch on and off, with settings on right-click."],
  ["/static/home/client-hud.jpg", "HUD in game", "Keystrokes, CPS, coordinates and a compass. Move them where you like."],
  ["/static/home/shop.jpg", "Shop", "Try hats, cloaks and wings on your skin before you buy."],
  ["/static/home/client-settings.jpg", "Video settings", "Every option says what it does and what it costs in FPS."],
  ["/static/home/client-signin.jpg", "Your account", "Sign in once on the start screen. Coins and cosmetics follow you."],
];
/** A picture viewer over the page: arrows or the arrow keys to move, Escape or a click outside to close. */
function lightbox(i) {
  const img = h("img", { alt: "" });
  const cap = h("p");
  const show = (n) => { i = (n + CLIENT_SHOTS.length) % CLIENT_SHOTS.length; img.src = CLIENT_SHOTS[i][0]; img.alt = CLIENT_SHOTS[i][1]; cap.replaceChildren(h("b", {}, CLIENT_SHOTS[i][1]), " ", CLIENT_SHOTS[i][2]); };
  const close = () => { box.remove(); document.removeEventListener("keydown", keys); };
  const keys = (e) => { if (e.key === "Escape") close(); else if (e.key === "ArrowRight") show(i + 1); else if (e.key === "ArrowLeft") show(i - 1); };
  const box = h("div", { class: "lightbox", role: "dialog", "aria-label": "Pictures of PlebClient", onclick: (e) => { if (e.target === box) close(); } },
    h("button", { class: "lb-x", "aria-label": "Close", onclick: close }, "×"),
    h("button", { class: "lb-nav", "aria-label": "Previous picture", onclick: () => show(i - 1) }, "‹"),
    h("figure", {}, img, cap),
    h("button", { class: "lb-nav", "aria-label": "Next picture", onclick: () => show(i + 1) }, "›"));
  show(i);
  document.addEventListener("keydown", keys);
  document.body.append(box);
  box.querySelector(".lb-x").focus();
}

const DL_INFO = {
  offline: { icon: "globe", tag: "Easiest", needs: "Any modern browser", steps: ["Download the HTML file.", "Open it (double-click, or drag it into your browser).", "Make your account on the start screen and play."] },
  wasm: { icon: "sparkle", tag: "Fastest", needs: "A recent Chrome or Edge", steps: ["Download and unzip it.", "Open index.html in Chrome or Edge.", "Make your account on the start screen and play."] },
  "forge-1.8.9": { icon: "hammer", tag: "Java Edition", needs: "Minecraft Java with Forge 1.8.9", steps: ["Install Forge 1.8.9.", "Put the jar in your .minecraft/mods folder.", "Start Minecraft with the Forge profile."] },
};

async function downloadsView() {
  const list = info.downloads || [];
  if (!list.length) return card(null, empty("download", "Nothing to download yet. Check back soon."));
  const c = content();
  const cl = info.client || {};
  const ip = c.serverIp;
  const meta = (d) => DL_INFO[d.kind] || { icon: "download", tag: "", needs: "", steps: [] };
  const btn = (d, big) => h("a", { class: "btn primary" + (big ? " big" : ""), href: "/api/dl/" + encodeURIComponent(d.kind), download: "" }, icon("download"), "Download");
  const steps = (d) => (meta(d).steps.length ? h("ol", { class: "dl-steps" }, ...meta(d).steps.map((s) => h("li", {}, s))) : null);
  const [top, ...rest] = list;
  const shot = "/static/home/client-menu.jpg";
  return h("div", { class: "dl" },
    h("section", { class: "dl-top" },
      h("div", { class: "dl-top-txt" },
        h("div", { class: "row", style: "gap:6px" }, h("span", { class: "tag hot" }, "Recommended"), meta(top).tag ? h("span", { class: "tag" }, meta(top).tag) : null,
          cl.latest ? h("span", { class: "tag" }, "v" + cl.latest) : null),
        h("h2", {}, top.name), h("p", { class: "muted" }, top.help),
        h("div", { class: "row" }, btn(top, true), meta(top).needs ? h("span", { class: "needs" }, icon("check"), meta(top).needs) : null),
        steps(top)),
      h("button", { class: "dl-top-pic", "aria-label": "See it bigger", onclick: () => lightbox(0) }, h("img", { src: shot, alt: "PlebClient's main menu", loading: "lazy" }))),
    h("h2", { class: "dl-h" }, "A look inside"),
    h("div", { class: "gallery" }, ...CLIENT_SHOTS.map(([src, title, text], i) => h("button", { class: "gshot", onclick: () => lightbox(i) },
      h("img", { src, alt: title, loading: "lazy" }), h("span", {}, h("b", {}, title), h("small", {}, text))))),
    rest.length ? h("h2", { class: "dl-h" }, "Other ways to play") : null,
    rest.length ? h("div", { class: "dl-grid" }, ...rest.map((d) => h("section", { class: "dl-card" },
      h("div", { class: "dl-ic" }, icon(meta(d).icon)),
      h("div", { class: "dl-body" }, meta(d).tag ? h("span", { class: "tag" }, meta(d).tag) : null, h("h3", {}, d.name), h("p", { class: "muted" }, d.help),
        meta(d).needs ? h("span", { class: "needs" }, icon("check"), meta(d).needs) : null, steps(d)),
      btn(d)))) : null,
    ip ? h("section", { class: "dl-join" }, h("img", { src: siteLogo(), alt: "" }),
      h("div", {}, h("b", {}, "Then join " + ip), h("small", {}, "Add it as a server in PlebClient, or in any Eaglercraft 1.8 client.")),
      h("button", { onclick: () => copyIp(ip) }, icon("copy"), "Copy address")) : null,
    h("p", { class: "faint", style: "font-size:13px;margin:18px 0 0" }, "Got PlebClient already? It tells you on its start screen when a newer version is out."));
}

// ==================== admin: punishments (LiteBans) ====================
const PUNISH_KIND = { ban: ["Ban", "bad"], mute: ["Mute", "warn"], warn: ["Warning", "admin"], kick: ["Kick", ""] };
const PUNISH_STATUS = { active: ["Active", "bad"], expired: ["Expired", ""], removed: ["Removed", "good"] };
/** "in 3 d", "in 2 h": how long until a time in the future. */
function until(t) {
  const s = t - Date.now() / 1000;
  if (s < 60) return "in under a minute";
  if (s < 3600) return "in " + Math.floor(s / 60) + " min";
  if (s < 86400) return "in " + Math.floor(s / 3600) + " h";
  if (s < 86400 * 60) return "in " + Math.floor(s / 86400) + " d";
  return new Date(t * 1000).toLocaleDateString();
}

async function punishmentsView() {
  const p0 = params();
  const state = { q: p0.q || "", kind: p0.kind || "", status: p0.status || "" };
  const status = h("p", { class: "muted" });
  const tiles = h("div", { class: "tiles" });
  const search = h("input", { placeholder: "Search by player, staff member or reason", "aria-label": "Search punishments", value: state.q, type: "search" });
  const statusSel = select("pstatus", [["", "Any status"], ["active", "Active"], ["expired", "Expired"], ["removed", "Removed by staff"]], state.status);
  statusSel.setAttribute("aria-label", "Status");
  const chips = h("div", { class: "chips" });
  const body = h("tbody");
  const keep = () => history.replaceState(null, "", "#punishments?" + new URLSearchParams(Object.entries(state).filter(([, v]) => v)).toString());
  const setKind = (k) => { state.kind = k; drawChips(); keep(); pg.reset(); };
  // a name with its skin head; players link to their admin page
  const person = (name, fallback, link) => {
    if (!name) return h("span", { class: "muted" }, fallback);
    const real = /^[A-Za-z0-9_]{3,16}$/.test(name) && name.toLowerCase() !== "console";
    const inner = [real ? initial(name) : null, name];
    return link && real ? h("a", { class: "pun-who", href: "#player?name=" + encodeURIComponent(name) }, inner) : h("span", { class: "pun-who" }, inner);
  };
  const expiry = (x) => {
    if (x.kind === "kick") return h("span", { class: "faint" }, "–");
    if (x.status === "removed") return h("span", { class: "muted", title: x.removedAt ? when(x.removedAt) : "" }, x.removedAt ? "lifted " + ago(x.removedAt) : "lifted");
    if (!x.until) return x.status === "active" ? h("b", {}, "Permanent") : h("span", { class: "muted" }, "Permanent");
    return h("span", { class: x.status === "active" ? "" : "muted", title: when(x.until) }, x.until * 1000 > Date.now() ? until(x.until) : ago(x.until));
  };
  const row = (x) => {
    const [klabel, kcls] = PUNISH_KIND[x.kind] || [x.kind, ""];
    const st = PUNISH_STATUS[x.status];
    const lifted = x.status === "removed" ? "Removed by " + (x.removedBy || "staff") + (x.removedReason ? ": " + x.removedReason : "") : "";
    return h("tr", { class: "pun s-" + (x.status || "none") },
      h("td", { class: "cr-lead" }, h("span", { class: "pill " + kcls }, klabel), x.ipBan ? h("span", { class: "pill", title: "This one is on the IP address too" }, "IP") : null),
      h("td", { class: "cr-lead" }, person(x.name, x.ipBan ? "An IP address" : "Unknown player", true)),
      h("td", { "data-label": "By" }, person(x.staff, "Unknown", false)),
      h("td", { class: "wrap", "data-label": "Reason" }, h("div", {}, x.reason || h("span", { class: "faint" }, "No reason given"),
        x.server && x.server !== "*" ? h("small", { class: "faint" }, " · on " + x.server) : null,
        lifted ? h("small", { class: "pun-lifted" }, lifted) : null)),
      h("td", { title: when(x.at), "data-label": "When" }, ago(x.at)),
      h("td", { "data-label": "Expires" }, expiry(x)),
      h("td", { "data-label": "Status" }, st ? h("span", { class: "pill " + st[1] }, st[0]) : h("span", { class: "faint" }, "–")));
  };
  const drawSource = (src) => {
    let text;
    if (!src.ever) text = "Nothing from the proxy yet. Punishments show up here once the newest PlebioAPI is on the BungeeCord proxy next to LiteBans.";
    else if (!src.available) text = "The proxy can't read LiteBans" + (src.reason ? ": " + src.reason : ".");
    else if (!src.connected) text = "The proxy isn't connected (last update " + ago(src.lastSync) + "). This is the list as it was then.";
    else text = "Read from LiteBans on the proxy · updated " + ago(src.lastSync);
    status.replaceChildren(h("span", { class: "dot" + (src.connected && src.available ? " on" : "") }), text);
  };
  const drawTiles = (c) => tiles.replaceChildren(
    ...[["ban", "Bans", "active now"], ["mute", "Mutes", "active now"], ["warn", "Warnings", "still counting"]].map(([k, label, sub]) =>
      tile("shield", label, fmt(c[k].total), fmt(c[k].active) + " " + sub)),
    tile("shield", "Kicks", fmt(c.kick.total), "in total"));
  const drawChips = () => chips.replaceChildren(...[["", "All"], ["ban", "Bans"], ["mute", "Mutes"], ["warn", "Warnings"], ["kick", "Kicks"]]
    .map(([k, t]) => h("button", { class: "chip", "aria-pressed": String(k === state.kind), onclick: () => setKind(k) }, t)));
  const pg = pager(body, async (offset) => {
    const r = await api("admin/punishments?" + new URLSearchParams({ ...state, offset }).toString());
    if (offset === 0) { drawSource(r.source); drawTiles(r.counts); }
    return { items: r.items, total: r.total, more: r.more };
  }, row, { empty: () => h("tr", {}, h("td", { class: "muted", colspan: "7" },
    state.q || state.kind || state.status ? "No punishments match that." : "No punishments yet.")) });
  drawChips();
  let timer;
  search.addEventListener("input", () => { clearTimeout(timer); timer = setTimeout(() => { state.q = search.value.trim(); keep(); pg.reset(); }, 250); });
  statusSel.addEventListener("change", () => { state.status = statusSel.value; keep(); pg.reset(); });
  pg.reset();
  return h("div", {}, tiles, card("Punishments", status, h("div", { class: "row" }, search, statusSel), h("div", { style: "height:10px" }), chips, h("div", { style: "height:10px" }),
    h("div", { class: "scroll" }, h("table", { class: "pun-table cardrows" }, h("thead", {}, h("tr", {}, h("th", {}, "Type"), h("th", {}, "Player"), h("th", {}, "By"), h("th", {}, "Reason"),
      h("th", {}, "When"), h("th", {}, "Expires"), h("th", {}, "Status"))), body)), pg.more));
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
  }, (a) => h("tr", {}, h("td", { title: when(a.at), "data-label": "When" }, ago(a.at)), h("td", { "data-label": "Who" }, a.actor), h("td", { "data-label": "What" }, h("span", { class: "pill" }, a.action)), h("td", { class: "wrap", "data-label": "Detail" }, h("div", {}, a.detail))),
  { empty: () => h("tr", {}, h("td", { class: "muted" }, "Nothing logged.")) });
  let timer;
  q.addEventListener("input", () => { clearTimeout(timer); timer = setTimeout(() => pg.reset(), 300); });
  pg.reset();
  return card("Admin log", h("p", { class: "muted" }, "Everything admins did on the panel, newest first."), h("div", { class: "row" }, q), h("div", { style: "height:10px" }),
    h("div", { class: "scroll" }, h("table", { class: "cardrows" }, h("thead", {}, h("tr", {}, h("th", {}, "When"), h("th", {}, "Who"), h("th", {}, "What"), h("th", {}, "Detail"))), body)), pg.more);
}

// ==================== owner: site content ====================

/** A picture field: preview, Upload (sent to the panel right away) and Use default. */
function imagePicker(label, value, fallback, hint) {
  let val = value || "";
  const img = h("img", { alt: "" });
  const file = h("input", { type: "file", accept: "image/png,image/jpeg,image/gif,image/webp" });
  const status = h("small", { class: "faint" }, hint || "");
  const show = () => { img.src = val || fallback || ""; img.style.visibility = val || fallback ? "visible" : "hidden"; };
  file.addEventListener("change", () => {
    const f = file.files[0];
    if (!f) return;
    if (f.size > 5 * 1024 * 1024) { toast("Pictures can be up to 5 MB.", false); return; }
    const rd = new FileReader();
    rd.onload = async () => {
      status.textContent = "Uploading…";
      try {
        const r = await api("admin/site/image", { data: rd.result });
        val = r.url;
        show();
        status.textContent = "Uploaded. Press Save to put it on the site.";
      } catch (e) { status.textContent = ""; toast(e.message, false); }
      file.value = "";
    };
    rd.readAsDataURL(f);
  });
  show();
  const el = h("div", {}, h("label", {}, label), h("div", { class: "picker" }, h("div", { class: "thumb" }, img),
    h("div", { style: "display:grid;gap:8px" }, h("div", { class: "row" },
      h("button", { type: "button", onclick: () => file.click() }, icon("upload"), "Upload"),
      h("button", { type: "button", class: "ghost", onclick: () => { val = ""; show(); status.textContent = fallback ? "Back to the default. Press Save." : "Removed. Press Save."; } }, fallback ? "Use default" : "Remove"),
      file), status)));
  return { el, get: () => val };
}

async function siteView() {
  const c = JSON.parse(JSON.stringify(content()));
  const out = h("div");
  const text = (key, label, opts = {}) => {
    const input = opts.rows ? h("textarea", { id: "s-" + key, rows: opts.rows, maxlength: opts.max || 300 }) : h("input", { id: "s-" + key, maxlength: opts.max || 60, type: opts.type || "text", placeholder: opts.placeholder || "" });
    input.value = c[key] || "";
    return { key, el: [h("label", { for: "s-" + key }, label), input], get: () => input.value.trim() };
  };
  const fields = [
    text("badge", "News tag above the name (empty hides it)"), text("title1", "Tagline, first part", { max: 40 }), text("title2", "Tagline, second part (in color)", { max: 40 }),
    text("subtitle", "Text under the tagline", { rows: 3 }), text("serverIp", "Server address", { placeholder: "play.plebmc.top" }),
    text("discordUrl", "Discord link (empty hides Discord; an invite link is swapped for discord.plebmc.top)", { type: "url", max: 200, placeholder: "https://discord.plebmc.top" }),
    text("featuresTitle", "Features heading"), text("featuresText", "Features text", { rows: 2 }),
    text("voteText", "Note on the Vote page", { rows: 2 }), text("ctaTitle", "Bottom banner heading"), text("ctaText", "Bottom banner text", { rows: 2 }), text("footer", "Footer note", { max: 200 }),
  ];
  const F = Object.fromEntries(fields.map((f) => [f.key, f]));
  const accent = h("input", { type: "color", id: "s-accent", value: c.accent || "#2ea8ff" });
  accent.addEventListener("input", () => setAccent(accent.value));
  const logo = imagePicker("Logo", c.logo === "/static/logo.png" ? "" : c.logo, "/static/logo.png", "Square PNG with a see-through background looks best.");
  const hero = imagePicker("Big picture at the top", c.heroImage === "/static/home/wallpaper.jpg" ? "" : c.heroImage, "/static/home/wallpaper.jpg", "A wide picture, at least 1600 px across.");
  const show = imagePicker("Name logo (your blocky PLEBMC art)", c.wordmark, "", "A wide PNG with a see-through background. Without one, the site name is drawn in pixel letters.");

  // features and FAQ: lists with add and remove
  const feats = (c.features || []).map((f) => ({ ...f }));
  const faqs = (c.faq || []).map((f) => ({ ...f }));
  const featBox = h("div", { class: "repeat" });
  const faqBox = h("div", { class: "repeat" });
  const featPickers = [];
  const drawFeats = () => {
    featPickers.length = 0;
    featBox.replaceChildren(...feats.map((f, i) => {
      const t = h("input", { value: f.title, maxlength: 60, "aria-label": "Title", oninput: (e) => { f.title = e.target.value; } });
      const x = h("textarea", { rows: 2, maxlength: 300, "aria-label": "Text", oninput: (e) => { f.text = e.target.value; } });
      x.value = f.text;
      const pk = imagePicker("Picture", f.image, "", "");
      featPickers.push([f, pk]);
      return h("div", { class: "item-edit" }, h("div", { class: "row" }, h("b", {}, "Card " + (i + 1)), h("span", { class: "spacer" }),
        h("button", { type: "button", class: "ghost small", onclick: () => { save(); feats.splice(i, 1); drawFeats(); } }, icon("trash"), "Remove")),
      h("label", {}, "Title"), t, h("label", {}, "Text"), x, pk.el);
    }), feats.length < 6 ? h("button", { type: "button", onclick: () => { save(); feats.push({ title: "", text: "", image: "" }); drawFeats(); } }, icon("plus"), "Add a card") : "");
    function save() { featPickers.forEach(([ff, pk]) => { ff.image = pk.get(); }); }
  };
  const drawFaq = () => {
    faqBox.replaceChildren(...faqs.map((f, i) => {
      const q = h("input", { value: f.q, maxlength: 150, "aria-label": "Question", oninput: (e) => { f.q = e.target.value; } });
      const a = h("textarea", { rows: 2, maxlength: 600, "aria-label": "Answer", oninput: (e) => { f.a = e.target.value; } });
      a.value = f.a;
      return h("div", { class: "item-edit" }, h("div", { class: "row" }, h("b", {}, "Question " + (i + 1)), h("span", { class: "spacer" }),
        h("button", { type: "button", class: "ghost small", onclick: () => { faqs.splice(i, 1); drawFaq(); } }, icon("trash"), "Remove")),
      h("label", {}, "Question"), q, h("label", {}, "Answer"), a);
    }), faqs.length < 12 ? h("button", { type: "button", onclick: () => { faqs.push({ q: "", a: "" }); drawFaq(); } }, icon("plus"), "Add a question") : "");
  };
  const votes = (c.voteLinks || []).map((v) => ({ ...v }));
  const voteBox = h("div", { class: "repeat" });
  const reward = h("input", { id: "s-voteReward", type: "number", min: 0, step: 1, value: c.voteReward == null ? 100 : c.voteReward });
  const drawVotes = () => {
    voteBox.replaceChildren(...votes.map((v, i) => h("div", { class: "item-edit" }, h("div", { class: "row" }, h("b", {}, "Vote site " + (i + 1)), h("span", { class: "spacer" }),
      h("button", { type: "button", class: "ghost small", onclick: () => { votes.splice(i, 1); drawVotes(); } }, icon("trash"), "Remove")),
    h("label", {}, "Name"), h("input", { value: v.name || "", maxlength: 40, "aria-label": "Site name", placeholder: "Eaglercraft Servers", oninput: (e) => { v.name = e.target.value; } }),
    h("label", {}, "Vote link"), h("input", { value: v.url || "", type: "url", maxlength: 300, "aria-label": "Vote link", placeholder: "https://…", oninput: (e) => { v.url = e.target.value; } }))),
    votes.length < 8 ? h("button", { type: "button", onclick: () => { votes.push({ name: "", url: "" }); drawVotes(); } }, icon("plus"), "Add a vote site") : "");
  };
  drawFeats();
  drawFaq();
  drawVotes();

  const mascot = h("input", { type: "checkbox", id: "s-mascot" });
  mascot.checked = c.mascot !== false;
  const mojang = h("input", { type: "checkbox", id: "s-mojang" });
  mojang.checked = c.mojangSkins !== false;
  const saveBtn = h("button", { class: "primary", onclick: async () => {
    featPickers.forEach(([f, pk]) => { f.image = pk.get(); });
    const body = Object.fromEntries(fields.map((f) => [f.key, f.get()]));
    Object.assign(body, { accent: accent.value, logo: logo.get(), heroImage: hero.get(), wordmark: show.get(),
      features: feats.map((f) => ({ title: (f.title || "").trim(), text: (f.text || "").trim(), image: f.image || "" })),
      faq: faqs.map((f) => ({ q: (f.q || "").trim(), a: (f.a || "").trim() })).filter((f) => f.q && f.a),
      voteLinks: votes.map((v) => ({ name: (v.name || "").trim(), url: (v.url || "").trim() })).filter((v) => v.name || v.url),
      voteReward: Math.max(0, parseInt(reward.value, 10) || 0), mascot: mascot.checked, mojangSkins: mojang.checked });
    saveBtn.disabled = true;
    try {
      const r = await api("admin/site", body);
      info.content = r.content;
      applyTheme();
      toast("Saved. The homepage is updated.");
      message(out, "");
    } catch (e) { message(out, e.message); }
    saveBtn.disabled = false;
  } }, icon("check"), "Save");

  return h("div", { class: "stack" },
    h("p", { class: "muted", style: "margin:0" }, "Change the homepage's words, pictures and color. Everyone sees the changes as soon as you press Save."),
    h("div", { class: "grid g2" },
      card("Brand", logo.el, h("label", { for: "s-accent" }, "Accent color"), h("div", { class: "row" }, accent,
        ...[["PlebMc blue", "#2ea8ff"], ["Client cyan", "#3fd8e6"], ["TNT red", "#db3f2b"], ["Gold", "#f5c542"]].map(([n, v]) =>
          h("button", { type: "button", class: "ghost small", onclick: () => { accent.value = v; setAccent(v); } }, n))),
      ...F.serverIp.el, ...F.discordUrl.el, ...F.footer.el,
      h("label", { class: "check", for: "s-mascot" }, mascot, " Show Plebio around the site"),
      h("label", { class: "check", for: "s-mojang" }, mojang, " Look up the Minecraft skin of players who have not set their own")),
      card("Under the big picture", ...F.badge.el, ...F.title1.el, ...F.title2.el, ...F.subtitle.el)),
    h("div", { class: "grid g2" }, card("Pictures", hero.el, show.el), card("TNT banner at the bottom", ...F.ctaTitle.el, ...F.ctaText.el)),
    card("Feature cards", ...F.featuresTitle.el, ...F.featuresText.el, h("div", { style: "height:14px" }), featBox),
    card("FAQ", faqBox),
    card("Voting", h("p", { class: "muted" }, "The server lists players vote on. With none, the Vote page and the reminder are hidden. The reward is only what the site says: PlebioAPI pays it (voting.reward in its config)."),
      h("label", { for: "s-voteReward" }, "Coins per vote shown on the site (0 hides it)"), reward, ...F.voteText.el, h("div", { style: "height:14px" }), voteBox),
    out,
    h("div", { class: "savebar" }, saveBtn, h("a", { class: "btn", href: "#home" }, "View homepage"), h("span", { class: "spacer" }),
      h("button", { class: "ghost danger small", onclick: async () => {
        if (!window.confirm || !confirm("Put every word and picture back to the defaults?")) return;
        try { const r = await api("admin/site", { reset: true }); info.content = r.content; applyTheme(); toast("Back to the defaults."); route(); } catch (e) { toast(e.message, false); }
      } }, "Reset to defaults")));
}

// ==================== router ====================

const VIEWS = {
  home: ["Home", homeView],
  downloads: ["Download", downloadsView],
  stats: ["Stats", statsView],
  leaderboard: ["Leaderboards", leaderboardView],
  shop: ["Shop", shopView],
  vote: ["Vote", voteView],
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
  ideas: ["Ideas", ideasView, "admin"],
  punishments: ["Punishments", punishmentsView, "admin"],
  news: ["News", newsView],
  site: ["Site content", siteView, "owner"],
  publish: ["Publish to shop", publishView, "admin"],
  client: ["Updates", clientView, "admin"],
  audit: ["Admin log", auditView, "admin"],
};

// ==================== motion ====================

let revealer = null;
/** Brings a fresh page to life: homepage sections slide in as they scroll into view, and the big numbers count up. */
function animate(root) {
  if (revealer) revealer.disconnect();
  if (stillOnly() || !("IntersectionObserver" in window)) return;
  revealer = new IntersectionObserver((seen) => seen.forEach((e) => {
    if (!e.isIntersecting) return;
    e.target.classList.add("in");
    revealer.unobserve(e.target);
    // once it has arrived, hand the element back so hover effects aren't slowed by the reveal
    setTimeout(() => { e.target.classList.remove("reveal", "in", "from-left", "from-right"); e.target.style.transitionDelay = ""; }, 1100);
  }), { rootMargin: "0px 0px -8% 0px", threshold: 0.08 });
  root.querySelectorAll(".home .sec-title, .home .news-card, .home .frow, .home .step3, .home .vote-strip, .home .qa details, .home .qa-foot, .home .tnt, .home .tnt-foot, .home .podium-wrap").forEach((el) => {
    const i = el.parentElement ? Array.prototype.indexOf.call(el.parentElement.children, el) : 0;
    el.classList.add("reveal");
    if (el.classList.contains("frow")) el.classList.add(i % 2 ? "from-right" : "from-left");
    else el.style.transitionDelay = Math.min(i, 5) * 70 + "ms";
    revealer.observe(el);
  });
  root.querySelectorAll(".stat-tile b").forEach((b) => {
    const to = parseInt(b.textContent.replace(/,/g, ""), 10);
    if (!/^[\d,]+$/.test(b.textContent) || to < 2) return;
    const t0 = performance.now();
    const step = (t) => {
      const k = Math.min(1, (t - t0 - 350) / 900);
      b.textContent = fmt(Math.round(to * (k <= 0 ? 0 : 1 - Math.pow(1 - k, 3))));
      if (k < 1 && b.isConnected) requestAnimationFrame(step);
    };
    requestAnimationFrame(step);
  });
}

/** The homepage's numbers: who is on, how big the network is, and where to go next. Every tile is a link. */
function statBar(s, cl) {
  const st = info.stats || {};
  const big = (n) => (n >= 1e6 ? (n / 1e6).toFixed(n >= 1e7 ? 0 : 1) + "M" : n >= 1e5 ? Math.round(n / 1e3) + "K" : fmt(n));
  const tile = (cls, href, ic, value, label, sub) => h("a", { class: "stat-tile " + cls, href },
    h("span", { class: "stat-ico", "aria-hidden": "true" }, icon(ic)),
    h("b", {}, value), h("span", { class: "stat-label" }, label), h("small", {}, sub));
  const on = s.connected ? s.online || 0 : null;
  return h("div", { class: "statbar" },
    plebio("idlesway", { cls: "hot-pal", scale: 1.5 }),
    tile("live" + (on ? " on" : ""), "#leaderboard", "users", on == null ? "–" : fmt(on),
      ["Playing now", on ? h("i", { class: "live-dot", "aria-hidden": "true" }) : null],
      on == null ? "Server is starting up" : st.peakToday > on ? "Peak today: " + fmt(st.peakToday) : st.peak > on ? "Record: " + fmt(st.peak) + " at once" : on ? "Come join them" : "Be the first one on"),
    tile("", "#stats", "trophy", fmt(st.players || info.accounts || 0), "Players", "have joined PlebMc"),
    tile("gold", "#leaderboard", "coin", big(st.coins || 0), coinName(), "earned so far"),
    tile("", "#shop", "bag", fmt(st.cosmetics || 0), "Cosmetics", "hats, cloaks, wings, trails"),
    tile("accent", "#downloads", "download", cl.latest ? "v" + cl.latest : "Free", "PlebClient", cl.latest ? "Free · browser or Windows" : "Browser or Windows"));
}

/** Who stands next to each page's heading. */
const HEAD_POSE = { downloads: "sprint", stats: "sit", leaderboard: "celebrate", shop: "spotlight", vote: "love", me: "idlesway" };

/** Every page but the homepage sits in the page frame: a big heading, then the view. */
function framed(view, title, el) {
  const head = HEADS[view];
  const narrow = view === "login" || view === "register";
  const top = narrow ? null : h("div", { class: "page-head" },
    ADMIN_VIEWS.has(view) ? h("span", { class: "crumb" }, h("b", {}, "Admin"), " / " + title) : null,
    head ? h("h1", {}, head[0] + " ", h("span", {}, head[1])) : h("h1", {}, title),
    head && head[2] ? h("p", {}, head[2]) : null,
    HEAD_POSE[view] ? plebio(HEAD_POSE[view], { cls: "head-pal", scale: PLEBIO[HEAD_POSE[view]][1] > 100 ? 1 : 1.5 }) : null);
  return h("div", { class: "wrap page" + (narrow ? " narrow-page" : "") }, top, el);
}

async function route() {
  let view = location.hash.slice(1).split("?")[0] || "home";
  if (!VIEWS[view]) view = "home";
  if ((view === "login" || view === "register") && me) view = "me";
  const [title, fn, need] = VIEWS[view];
  topnav.classList.remove("open");
  menuBtn.setAttribute("aria-expanded", "false");
  setNav(view);
  titleEl.textContent = title;
  document.title = view === "home" ? info.site : title + " · " + info.site;
  let el;
  try {
    if ((need === "admin" && !isAdmin()) || (need === "owner" && !isOwner())) {
      el = me ? h("div", { class: "card narrow center" }, plebio("shake", { scale: 1.5 }), h("h2", {}, need === "owner" ? "Owners only" : "Admins only"),
        h("p", { class: "muted" }, need === "owner" ? "Only owners can change this." : "This page is for admins.")) : loginView();
    } else {
      if (view !== "home") app.replaceChildren(framed(view, title, h("div", { class: "skeleton" })));
      el = await fn();
    }
  } catch (err) {
    el = h("div", { class: "card narrow center" }, plebio("mcdeath", { scale: 1.5 }), h("p", {}, err.message), h("a", { href: "#home" }, "Back home"));
  }
  app.replaceChildren(view === "home" ? el : framed(view, title, el));
  animate(app);
  setNav(view);
  drawFooter();
  drawVoteNudge(view);
  window.scrollTo(0, 0);
}

(async function start() {
  try {
    info = await api("info");
    applyTheme();
    const r = await api("me");
    me = r.account;
  } catch (e) { /* the view shows it */ }
  window.addEventListener("hashchange", route);
  route();
})();
