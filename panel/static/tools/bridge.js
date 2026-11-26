"use strict";
// Connects a maker (Hat Maker, Cloak Maker) to your PlebMc account on the panel:
// save to your account, open your designs, share them in the gallery, remix shared ones,
// and (admins) publish straight into the server's shop.
window.PlebTool = (() => {
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
    if (r.status === 413) throw new Error("Too big for the panel's web server. The owner needs to raise client_max_body_size in nginx.");
    const j = await r.json().catch(() => ({}));
    if (!r.ok || j.ok === false) throw new Error(j.error || "Something went wrong (" + r.status + ").");
    return j;
  }
  const ago = (t) => {
    const s = Math.max(0, Date.now() / 1000 - t);
    if (s < 3600) return Math.max(1, Math.floor(s / 60)) + " min ago";
    if (s < 86400) return Math.floor(s / 3600) + " h ago";
    return Math.floor(s / 86400) + " d ago";
  };

  /**
   * tool: { kind, get() -> {name, itemId, data, extra, thumb} (may be async),
   *         set(design), publishFile() -> {file, frametime?} (may be async), status(text, bad) }
   */
  function mount(box, tool) {
    let me = null;
    let currentId = null;
    let currentMine = false;
    const who = h("div", { class: "muted" }, "Checking your account…");
    const share = h("input", { type: "checkbox", id: "pt-share" });
    const saveBtn = h("button", { class: "primary", onclick: () => save(false) }, "Save to my account");
    const newBtn = h("button", { onclick: () => save(true) }, "Save as new");
    const list = h("div", { class: "designs" });
    const pub = h("div", { class: "acct hidden" });
    const shownName = h("small", { class: "muted" });
    box.append(h("div", { class: "acct" }, who, shownName,
      h("label", { class: "check" }, share, "Share in the gallery (others can like and remix it)"),
      h("div", { class: "row" }, saveBtn, newBtn),
      h("details", {}, h("summary", {}, "My saved " + tool.kind + "s"), h("div", { style: "height:8px" }), list),
      pub));

    function showName() {
      shownName.textContent = currentId ? (currentMine ? "Editing your saved design #" + currentId : "Remixing design #" + currentId + ": saving makes your own copy") : "Not saved to your account yet";
    }

    async function refreshList() {
      if (!me) return;
      try {
        const r = await api("designs/mine");
        const mine = r.designs.filter((d) => d.kind === tool.kind);
        list.replaceChildren();
        if (!mine.length) list.append(h("p", { class: "muted" }, "Nothing saved yet."));
        for (const d of mine) {
          list.append(h("div", { class: "design" },
            h("div", { class: "pic" }, d.thumb ? h("img", { src: d.thumb, alt: "" }) : ""),
            h("div", { class: "body" }, h("b", {}, d.name), h("small", { class: "muted" }, ago(d.updated) + (d.public ? " · shared" : "")),
              h("div", { class: "row" },
                h("button", { class: "small", onclick: () => open(d.id) }, "Open"),
                h("button", { class: "small danger", title: "Delete", onclick: async () => {
                  if (!confirm("Delete " + d.name + "?")) return;
                  try {
                    await api("designs/delete", { id: d.id });
                    if (currentId === d.id) { currentId = null; showName(); }
                    refreshList();
                  } catch (e) { tool.status(e.message, true); }
                } }, "✕")))));
        }
      } catch (e) { list.replaceChildren(h("p", { class: "muted" }, e.message)); }
    }

    async function save(asNew) {
      if (!me) { location.href = "/#login?next=" + encodeURIComponent(location.pathname + location.search); return; }
      saveBtn.disabled = newBtn.disabled = true;
      try {
        const d = await tool.get();
        const body = { kind: tool.kind, name: d.name, itemId: d.itemId, data: d.data, extra: d.extra || {}, thumb: d.thumb || "", public: share.checked };
        if (currentId && currentMine && !asNew) body.id = currentId;
        const r = await api("designs/save", body);
        currentId = r.id;
        currentMine = true;
        history.replaceState(null, "", location.pathname + "?design=" + r.id);
        showName();
        tool.status(share.checked ? "Saved and shared in the gallery." : "Saved to your account.");
        refreshList();
      } catch (e) {
        tool.status(e.message, true);
      }
      saveBtn.disabled = newBtn.disabled = false;
    }

    async function open(id) {
      try {
        const d = (await api("designs/get?id=" + encodeURIComponent(id))).design;
        if (d.kind !== tool.kind) {
          location.href = "/static/tools/" + d.kind + ".html?design=" + d.id;
          return;
        }
        await tool.set(d);
        currentId = d.id;
        currentMine = !!d.mine;
        share.checked = d.mine ? d.public : false;
        history.replaceState(null, "", location.pathname + "?design=" + d.id);
        showName();
        tool.status(d.mine ? "Opened " + d.name + "." : "Opened " + d.name + " by " + (d.author || "someone") + ". Save to keep your own copy.");
      } catch (e) {
        tool.status(e.message, true);
      }
    }

    function publishBox() {
      const id = h("input", { type: "text", id: "pt-id", maxlength: 32, spellcheck: "false" });
      const price = h("input", { type: "number", id: "pt-price", value: tool.kind === "hat" ? 2000 : 5000, step: 100 });
      const desc = h("input", { type: "text", id: "pt-desc", maxlength: 100, value: tool.kind === "hat" ? "Made with the Hat Maker" : "Animated cloak" });
      const coll = h("input", { type: "text", id: "pt-coll", maxlength: 40, placeholder: "plebmc" });
      const creator = h("input", { type: "text", id: "pt-creator", maxlength: 40, placeholder: "optional" });
      const go = h("button", { class: "primary", onclick: async () => {
        go.disabled = true;
        try {
          const d = await tool.get();
          const f = await tool.publishFile();
          await api("admin/publish", { kind: tool.kind, itemId: (id.value || d.itemId).trim(), name: d.name, price: price.value, file: f.file,
            frametime: f.frametime, description: desc.value, collection: coll.value, creator: creator.value, designId: currentMine ? currentId : null });
          tool.status("Sent to the server: it's in the shop within a few seconds. (See Publish to shop on the panel for the result.)");
        } catch (e) {
          tool.status(e.message, true);
        }
        go.disabled = false;
      } }, "Publish to shop");
      pub.classList.remove("hidden");
      pub.append(h("h3", {}, "Publish to the shop (admins)"),
        h("div", { class: "grid g2" },
          h("div", {}, h("label", { for: "pt-id" }, "Item id (blank = from the id above)"), id),
          h("div", {}, h("label", { for: "pt-price" }, "Price (-1 = not for sale)"), price),
          h("div", {}, h("label", { for: "pt-desc" }, "Description"), desc),
          h("div", {}, h("label", { for: "pt-coll" }, "Shop tab (collection)"), coll),
          h("div", {}, h("label", { for: "pt-creator" }, "Made by"), creator)),
        h("div", { class: "row" }, go));
    }

    (async () => {
      try {
        me = (await api("me")).account;
      } catch (e) { me = null; }
      if (me) {
        who.replaceChildren("Signed in as ", h("b", {}, me.name), ". ", h("a", { href: "/#me" }, "My account"));
        refreshList();
        if (me.role === "admin" || me.role === "owner") publishBox();
      } else {
        who.replaceChildren(h("a", { href: "/#login?next=" + encodeURIComponent(location.pathname + location.search) }, "Sign in"),
          " to save your designs to your account and share them. You can still download the files without signing in.");
      }
      showName();
      const want = new URLSearchParams(location.search).get("design");
      if (want) open(want);
    })();
  }

  return { mount, h, api };
})();
