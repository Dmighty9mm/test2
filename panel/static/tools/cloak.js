// PlebMc Cloak Maker (panel version). Needs three.js and bridge.js first.
(() => {
  'use strict';
  const $ = (id) => document.getElementById(id);
  const MAX_FRAMES = 64, MAX_PIXELS = 8388608, MAX_BYTES = 8 * 1024 * 1024;

  // ---------- state ----------
  const S = {
    source: 'effect',      // 'effect' | 'still' | 'frames' | 'sheet' (a saved cloak, opened as is)
    sheet: [],             // frames of a saved cloak
    effect: 'aurora',
    art: [],               // canvases for 'still' (1) or 'frames' (n)
    artMs: [],             // per-frame ms for GIFs
    frames: 16, ms: 90,
  };
  const hexToRgb = (h) => { const n = parseInt(h.slice(1), 16); return [n >> 16 & 255, n >> 8 & 255, n & 255]; };
  const mixRgb = (a, b, t) => a.map((v, i) => Math.round(v + (b[i] - v) * t));
  const css = (c, a = 1) => `rgba(${c[0]},${c[1]},${c[2]},${a})`;

  // ---------- generated effects (draw art for one frame, t in 0..1) ----------
  const EFFECTS = {
    aurora: { name: 'Aurora', draw(g, w, h, t, A, B) {
      const img = g.createImageData(w, h), d = img.data, ph = t * Math.PI * 2;
      for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
        const u = x / w * 10, v = y / h * 16;
        const k = (Math.sin(u * .9 + ph) + Math.sin(v * .45 - ph * 2 + u * .3) + Math.sin((u + v) * .35 + ph) + 3) / 6;
        const c = mixRgb([6, 10, 24], mixRgb(A, B, (Math.sin(v * .3 + ph) + 1) / 2), Math.pow(k, 1.4));
        const i = (y * w + x) * 4; d[i] = c[0]; d[i + 1] = c[1]; d[i + 2] = c[2]; d[i + 3] = 255;
      }
      g.putImageData(img, 0, 0); stars(g, w, h, t, 18);
    }},
    fire: { name: 'Fire', draw(g, w, h, t, A, B) {
      const img = g.createImageData(w, h), d = img.data, ph = t * Math.PI * 2;
      for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
        const u = x / w, v = y / h;
        const n = Math.sin(u * 14 + ph * 2) * .5 + Math.sin(u * 7 - ph * 3 + v * 9) * .5 + Math.sin(v * 22 + ph * 4 + u * 3) * .3;
        const k = Math.max(0, Math.min(1, v * 1.25 - .15 + n * .18));
        const c = k < .5 ? mixRgb([20, 4, 2], B, k * 2) : mixRgb(B, mixRgb(A, [255, 255, 210], .4), (k - .5) * 2);
        const i = (y * w + x) * 4; d[i] = c[0]; d[i + 1] = c[1]; d[i + 2] = c[2]; d[i + 3] = 255;
      }
      g.putImageData(img, 0, 0);
    }},
    rainbow: { name: 'Rainbow', draw(g, w, h, t) {
      for (let y = 0; y < h; y++) { g.fillStyle = `hsl(${(y / h * 360 + t * 360) % 360} 85% 55%)`; g.fillRect(0, y, w, 1); }
      const s = g.createLinearGradient(0, 0, w, 0); s.addColorStop(0, 'rgba(0,0,0,.25)'); s.addColorStop(.5, 'rgba(255,255,255,.08)'); s.addColorStop(1, 'rgba(0,0,0,.25)');
      g.fillStyle = s; g.fillRect(0, 0, w, h);
    }},
    galaxy: { name: 'Galaxy', draw(g, w, h, t, A, B) {
      const gr = g.createLinearGradient(0, 0, 0, h); gr.addColorStop(0, css(mixRgb([4, 4, 14], B, .45))); gr.addColorStop(1, css([3, 3, 10]));
      g.fillStyle = gr; g.fillRect(0, 0, w, h);
      const cx = w / 2, cy = h * .45, ph = t * Math.PI * 2;
      for (let i = 0; i < 3; i++) { const r = g.createRadialGradient(cx + Math.cos(ph + i * 2) * w * .2, cy + Math.sin(ph + i * 2) * h * .12, 0, cx, cy, w * .7);
        r.addColorStop(0, css(i ? B : A, .55)); r.addColorStop(1, 'rgba(0,0,0,0)'); g.fillStyle = r; g.fillRect(0, 0, w, h); }
      stars(g, w, h, t, 40);
    }},
    pulse: { name: 'Pulse', draw(g, w, h, t, A, B) {
      const k = (Math.sin(t * Math.PI * 2) + 1) / 2;
      const r = g.createRadialGradient(w / 2, h * .42, 0, w / 2, h * .42, h * (.45 + .25 * k));
      r.addColorStop(0, css(mixRgb(A, [255, 255, 255], .3 * k))); r.addColorStop(.6, css(B)); r.addColorStop(1, css(mixRgb(B, [0, 0, 0], .7)));
      g.fillStyle = r; g.fillRect(0, 0, w, h);
    }},
    matrix: { name: 'Code rain', draw(g, w, h, t, A) {
      g.fillStyle = '#020806'; g.fillRect(0, 0, w, h);
      const cols = 10, cw = w / cols, ch = Math.max(1, h / 32);
      for (let c = 0; c < cols; c++) {
        const speed = .6 + hash(c) * .9, off = hash(c * 7 + 1);
        for (let j = 0; j < 14; j++) {
          const y = (((off + t * speed) % 1) * 1.6 - .3) * h - j * ch * 1.6;
          if (y < -ch || y > h) continue;
          g.fillStyle = css(j ? A : mixRgb(A, [255, 255, 255], .7), j ? Math.max(0, .9 - j * .07) : 1);
          g.fillRect(Math.round(c * cw + cw * .25), Math.round(y), Math.max(1, Math.round(cw * .5)), Math.max(1, Math.round(ch)));
        }
      }
    }},
  };
  EFFECTS.plasma = { name: 'Plasma', draw(g, w, h, t, A, B) {
    const img = g.createImageData(w, h), d = img.data, ph = t * Math.PI * 2;
    for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
      const u = x / w * 6, v = y / h * 9;
      const k = (Math.sin(u + ph) + Math.sin(v * .8 - ph) + Math.sin((u + v + ph * 2) * .7) + Math.sin(Math.hypot(u - 3, v - 4.5) * 1.3 - ph) + 4) / 8;
      const c = k < .5 ? mixRgb(A, B, k * 2) : mixRgb(B, [255, 255, 255], (k - .5) * .7);
      const i = (y * w + x) * 4; d[i] = c[0]; d[i + 1] = c[1]; d[i + 2] = c[2]; d[i + 3] = 255;
    }
    g.putImageData(img, 0, 0);
  }};
  EFFECTS.waves = { name: 'Waves', draw(g, w, h, t, A, B) {
    const gr = g.createLinearGradient(0, 0, 0, h); gr.addColorStop(0, css(mixRgb(B, [0, 0, 0], .5))); gr.addColorStop(1, css(mixRgb(A, [0, 0, 0], .6)));
    g.fillStyle = gr; g.fillRect(0, 0, w, h);
    const bands = 7;
    for (let b = 0; b < bands; b++) {
      g.fillStyle = css(mixRgb(A, B, b / (bands - 1)), .55);
      g.beginPath(); g.moveTo(0, h);
      for (let x = 0; x <= w; x++) { const y = h * (b + 1) / (bands + 1) + Math.sin(x / w * Math.PI * 2 * 1.5 + t * Math.PI * 2 + b) * h * .04; g.lineTo(x, y); }
      g.lineTo(w, h); g.closePath(); g.fill();
    }
  }};
  EFFECTS.checker = { name: 'Checker', draw(g, w, h, t, A, B) {
    const s = Math.max(2, Math.round(w / 5)), off = Math.floor(t * s * 2);
    for (let y = -s * 2; y < h + s; y += s) for (let x = 0; x < w; x += s) {
      const yy = y + (off % (s * 2));
      g.fillStyle = css(((x + y) / s) % 2 ? A : B); g.fillRect(x, yy, s, s);
    }
  }};
  function hash(n) { n = (n << 13) ^ n; return ((n * (n * n * 15731 + 789221) + 1376312589) & 0x7fffffff) / 0x7fffffff; }
  function stars(g, w, h, t, n) {
    for (let i = 0; i < n; i++) { const tw = (Math.sin((t + hash(i)) * Math.PI * 2 * (1 + (i % 3))) + 1) / 2;
      g.fillStyle = `rgba(255,255,255,${.25 + .75 * tw})`; const s = Math.max(1, Math.round(w / 40));
      g.fillRect(Math.floor(hash(i * 3 + 1) * w), Math.floor(hash(i * 5 + 2) * h), s, s); }
  }

  // ---------- overlays on uploaded art ----------
  function overlay(g, w, h, t, kind, A, B) {
    const ph = t * Math.PI * 2;
    if (kind === 'shine') {
      const x = (t * 1.8 - .4) * (w + h);
      const gr = g.createLinearGradient(x - h * .4, 0, x, h * .6); gr.addColorStop(0, 'rgba(255,255,255,0)'); gr.addColorStop(.5, 'rgba(255,255,255,.45)'); gr.addColorStop(1, 'rgba(255,255,255,0)');
      g.globalCompositeOperation = 'lighter'; g.fillStyle = gr; g.fillRect(0, 0, w, h); g.globalCompositeOperation = 'source-over';
    } else if (kind === 'sparkle') {
      for (let i = 0; i < 14; i++) { const life = (t + hash(i * 9)) % 1, k = Math.sin(life * Math.PI);
        const x = hash(i * 3 + 4) * w, y = hash(i * 7 + 2) * h, s = Math.max(1, Math.round(w / 32)) * (1 + Math.round(k * 2));
        g.fillStyle = css(i % 2 ? A : [255, 255, 255], k); g.fillRect(x - s / 2, y - s / 6, s, s / 3 || 1); g.fillRect(x - s / 6, y - s / 2, s / 3 || 1, s); }
    } else if (kind === 'pulse') {
      const k = (Math.sin(ph) + 1) / 2, r = g.createRadialGradient(w / 2, h / 2, 0, w / 2, h / 2, h * .7);
      r.addColorStop(0, css(A, .35 * k)); r.addColorStop(1, 'rgba(0,0,0,0)');
      g.globalCompositeOperation = 'lighter'; g.fillStyle = r; g.fillRect(0, 0, w, h); g.globalCompositeOperation = 'source-over';
    } else if (kind === 'rainbow') {
      g.globalCompositeOperation = 'color';
      for (let y = 0; y < h; y++) { g.fillStyle = `hsla(${(y / h * 360 + t * 360) % 360},90%,55%,.55)`; g.fillRect(0, y, w, 1); }
      g.globalCompositeOperation = 'source-over';
    } else if (kind === 'scan') {
      const step = Math.max(2, Math.round(h / 32)), off = Math.floor(t * step * 4) % step;
      g.fillStyle = 'rgba(0,0,0,.28)'; for (let y = off; y < h; y += step) g.fillRect(0, y, w, Math.max(1, step / 2));
      const by = t * h; const gr = g.createLinearGradient(0, by - h * .1, 0, by + h * .1);
      gr.addColorStop(0, 'rgba(0,0,0,0)'); gr.addColorStop(.5, css(A, .35)); gr.addColorStop(1, 'rgba(0,0,0,0)'); g.fillStyle = gr; g.fillRect(0, 0, w, h);
    }
  }

  // ---------- drawing one frame of the cape layout ----------
  function artFor(i, n, aw, ah) {
    const c = document.createElement('canvas'); c.width = aw; c.height = ah; const g = c.getContext('2d'); g.imageSmoothingEnabled = false;
    const A = hexToRgb($('c1').value), B = hexToRgb($('c2').value), t = i / n;
    if (S.source === 'effect') { EFFECTS[S.effect].draw(g, aw, ah, t, A, B); return c; }
    const src = S.source === 'frames' ? S.art[i % S.art.length] : S.art[0];
    // behind see-through art (like a GIF with no background): a dark wash of the two colors
    const bg = g.createLinearGradient(0, 0, 0, ah); bg.addColorStop(0, css(mixRgb(A, [0, 0, 0], .7))); bg.addColorStop(1, css(mixRgb(B, [0, 0, 0], .8)));
    g.fillStyle = bg; g.fillRect(0, 0, aw, ah);
    const fit = $('fit').value;
    let dw = aw, dh = ah, dx = 0, dy = 0;
    if (fit !== 'stretch') {
      const k = fit === 'cover' ? Math.max(aw / src.width, ah / src.height) : Math.min(aw / src.width, ah / src.height);
      dw = src.width * k; dh = src.height * k; dx = (aw - dw) / 2; dy = (ah - dh) / 2;
    }
    if (S.source === 'still' && $('overlay').value === 'drift') { const k = 1.12, p = Math.sin(t * Math.PI * 2); dx -= (dw * (k - 1)) / 2 + p * dw * .04; dy -= (dh * (k - 1)) / 2; dw *= k; dh *= k; }
    g.imageSmoothingEnabled = src.width > aw;
    g.drawImage(src, dx, dy, dw, dh);
    const ov = $('overlay').value; if (ov !== 'none' && ov !== 'drift') overlay(g, aw, ah, t, ov, A, B);
    return c;
  }
  function average(c) {
    const g = c.getContext('2d'), d = g.getImageData(0, 0, c.width, c.height).data; let r = 0, gg = 0, b = 0, n = 0;
    for (let i = 0; i < d.length; i += 16) { r += d[i]; gg += d[i + 1]; b += d[i + 2]; n++; }
    return [r / n | 0, gg / n | 0, b / n | 0];
  }
  function layoutFrame(i, n, W) {
    const H = W / 2, u = W / 64, aw = 10 * u, ah = 16 * u;
    const c = document.createElement('canvas'); c.width = W; c.height = H; const g = c.getContext('2d'); g.imageSmoothingEnabled = false;
    const art = artFor(i, n, aw, ah);
    const avg = average(art), mode = $('edges').value;
    const edge = mode === 'c1' ? mixRgb(hexToRgb($('c1').value), [0, 0, 0], .35) : mode === 'black' ? [12, 12, 14] : mixRgb(avg, [0, 0, 0], .45);
    g.fillStyle = css(edge); g.fillRect(0, 0, 22 * u, 17 * u);
    g.drawImage(art, 1 * u, 1 * u);
    if ($('rim').checked) { g.fillStyle = 'rgba(0,0,0,.35)'; const r = Math.max(1, u / 2);
      g.fillRect(u, u, aw, r); g.fillRect(u, u + ah - r, aw, r); g.fillRect(u, u, r, ah); g.fillRect(u + aw - r, u, r, ah); }
    const inside = $('inside').value;
    if (inside === 'dark') { g.fillStyle = css(mixRgb(edge, [0, 0, 0], .4)); g.fillRect(12 * u, u, aw, ah); }
    else {
      g.save(); if (inside === 'mirror') { g.translate(12 * u + aw, u); g.scale(-1, 1); g.drawImage(art, 0, 0); } else g.drawImage(art, 12 * u, u); g.restore();
      if (inside === 'mirror') { g.fillStyle = 'rgba(0,0,0,.45)'; g.fillRect(12 * u, u, aw, ah); }
    }
    return c;
  }

  // ---------- frames, timing, and the sheet ----------
  let frames = [], frameMs = [], dirty = true, gen = 0;
  function frameCount() { return S.source === 'sheet' ? S.sheet.length : S.source === 'frames' ? Math.min(MAX_FRAMES, S.art.length) : S.frames; }
  function rebuild() {
    if (S.source === 'sheet') {
      frames = S.sheet.slice(); frameMs = frames.map(() => S.ms);
      $('ms').value = S.ms; $('msOut').textContent = `(${S.ms} ms)`;
      updateStats(); updateYaml(); keepSettings(); dirty = false;
      return;
    }
    const W = +$('res').value, n = frameCount(), my = ++gen;
    frames = []; frameMs = [];
    for (let i = 0; i < n; i++) frames.push(layoutFrame(i, n, W));
    const uniform = S.source === 'frames' && S.artMs.length ? Math.round(S.artMs.slice(0, n).reduce((a, b) => a + b, 0) / n / 10) * 10 : S.ms;
    for (let i = 0; i < n; i++) frameMs.push(uniform);
    if (S.source === 'frames' && S.artMs.length) { $('ms').value = Math.max(20, Math.min(500, uniform)); }
    S.ms = +$('ms').value; $('msOut').textContent = `(${S.ms} ms)`;
    if (my === gen) { updateStats(); updateYaml(); }
    keepSettings();
    dirty = false;
  }
  let pending = 0; const schedule = () => { cancelAnimationFrame(pending); pending = requestAnimationFrame(rebuild); };

  function sheet() {
    const W = frames.length ? frames[0].width : +$('res').value, H = W / 2, c = document.createElement('canvas'); c.width = W; c.height = H * frames.length;
    const g = c.getContext('2d'); frames.forEach((f, i) => g.drawImage(f, 0, i * H)); return c;
  }
  function updateStats() {
    const W = frames.length ? frames[0].width : +$('res').value, n = frames.length, px = W * (W / 2) * n;
    const lines = [
      `<b>${W} x ${W / 2 * n}</b> PNG`,
      `<b>${n}</b> frame${n === 1 ? '' : 's'} at <b>${S.ms} ms</b> (${(1000 / S.ms).toFixed(1)} fps)`,
      `loop: <b>${(n * S.ms / 1000).toFixed(2)} s</b>`,
      px > MAX_PIXELS ? `<span class="err">too big: ${px.toLocaleString()} pixels, the limit is ${MAX_PIXELS.toLocaleString()}. Lower the detail or the frames.</span>`
        : `<span class="ok">fits the PlebioAPI limits</span>`,
    ];
    $('stats').innerHTML = lines.join('<br>');
  }
  function q(s) { return "'" + String(s).replace(/'/g, "''") + "'"; }
  function safeId() { return ($('cid').value.trim().toLowerCase().replace(/[^a-z0-9_]+/g, '_').replace(/^_+|_+$/g, '') || 'my_cloak'); }
  function updateYaml() {
    const id = safeId(), parts = [`name: ${q($('cname').value || id)}`, 'category: cloak', `texture: 'cloaks/${id}.png'`];
    if (frames.length > 1) parts.push(`frametime: ${S.ms}`);
    parts.push(`price: ${parseInt($('price').value, 10) || 0}`);
    if ($('col').value !== 'plebmc') parts.push(`collection: ${$('col').value}`);
    if ($('creator').value.trim()) parts.push(`creator: ${q($('creator').value.trim())}`);
    parts.push(`description: ${q($('desc').value)}`);
    $('yaml').textContent = `  ${id}: {${parts.join(', ')}}`;
  }

  // ---------- inputs: files and GIFs ----------
  const toast = (m, cls = '') => { const t = $('toast'); t.className = 'status ' + cls; t.textContent = m; };
  async function loadImage(file) {
    const url = URL.createObjectURL(file);
    try { const img = new Image(); img.src = url; await img.decode(); const c = document.createElement('canvas'); c.width = img.naturalWidth; c.height = img.naturalHeight; c.getContext('2d').drawImage(img, 0, 0); return c; }
    finally { URL.revokeObjectURL(url); }
  }
  // A small GIF decoder (no download needed): frames, delays, disposal and transparency.
  function decodeGif(buf) {
    const b = new Uint8Array(buf); let p = 0;
    const u16 = () => { const v = b[p] | b[p + 1] << 8; p += 2; return v; };
    if (String.fromCharCode(b[0], b[1], b[2]) !== 'GIF') throw new Error('not a GIF');
    p = 6; const W = u16(), H = u16(), fl = b[p++]; p += 2;
    const readTable = (n) => { const t = []; for (let i = 0; i < n; i++) { t.push([b[p], b[p + 1], b[p + 2]]); p += 3; } return t; };
    const gct = fl & 0x80 ? readTable(1 << ((fl & 7) + 1)) : null;
    const subBlocks = () => { const parts = []; let n = 0; for (let len = b[p++]; len; len = b[p++]) { parts.push(b.subarray(p, p + len)); n += len; p += len; }
      const out = new Uint8Array(n); let o = 0; for (const q of parts) { out.set(q, o); o += q.length; } return out; };
    const frames = []; let gce = { delay: 100, trans: -1, disp: 0 };
    while (p < b.length) {
      const t = b[p++];
      if (t === 0x3B) break;
      if (t === 0x21) { const label = b[p++];
        if (label === 0xF9) { p++; const f = b[p++], d = u16(), ti = b[p++]; p++; gce = { delay: (d || 10) * 10, trans: f & 1 ? ti : -1, disp: f >> 2 & 7 }; }
        else subBlocks();
        continue; }
      if (t !== 0x2C) break;
      const x = u16(), y = u16(), w = u16(), h = u16(), f = b[p++];
      const lct = f & 0x80 ? readTable(1 << ((f & 7) + 1)) : null, inter = f & 0x40;
      const minCode = b[p++], data = subBlocks();
      frames.push({ x, y, w, h, inter, pal: lct || gct, idx: lzw(minCode, data, w * h), ...gce });
      gce = { delay: 100, trans: -1, disp: 0 };
    }
    return { W, H, frames };
  }
  function lzw(minCode, data, n) {
    const out = new Uint8Array(n), clear = 1 << minCode, eoi = clear + 1;
    const prefix = new Int16Array(4096), suffix = new Uint8Array(4096), stack = new Uint8Array(4097);
    let size = minCode + 1, mask = (1 << size) - 1, avail = clear + 2, old = -1, first = 0, bits = 0, cur = 0, o = 0, i = 0;
    for (let c = 0; c < clear; c++) { prefix[c] = -1; suffix[c] = c; }
    while (o < n) {
      while (bits < size) { if (i >= data.length) return out; cur |= data[i++] << bits; bits += 8; }
      let code = cur & mask; cur >>= size; bits -= size;
      if (code === clear) { size = minCode + 1; mask = (1 << size) - 1; avail = clear + 2; old = -1; continue; }
      if (code === eoi) break;
      if (old === -1) { out[o++] = suffix[code]; old = code; first = code; continue; }
      const inCode = code; let sp = 0;
      if (code >= avail) { stack[sp++] = first; code = old; }
      while (code >= clear) { stack[sp++] = suffix[code]; code = prefix[code]; }
      first = suffix[code]; stack[sp++] = first;
      if (avail < 4096) { prefix[avail] = old; suffix[avail] = first; avail++; if ((avail & mask) === 0 && avail < 4096) { size++; mask = (1 << size) - 1; } }
      old = inCode;
      while (sp && o < n) out[o++] = stack[--sp];
    }
    return out;
  }
  async function loadGif(file) {
    const { W, H, frames: fr } = decodeGif(await file.arrayBuffer());
    const full = document.createElement('canvas'); full.width = W; full.height = H;
    const g = full.getContext('2d'), patch = document.createElement('canvas'), pg = patch.getContext('2d');
    let out = [], ms = [], before = null;
    for (const f of fr) {
      if (f.disp === 3) before = g.getImageData(0, 0, W, H);
      patch.width = f.w; patch.height = f.h;
      const id = pg.createImageData(f.w, f.h), d = id.data;
      const rows = []; if (f.inter) { for (const [s, st] of [[0, 8], [4, 8], [2, 4], [1, 2]]) for (let r = s; r < f.h; r += st) rows.push(r); } else for (let r = 0; r < f.h; r++) rows.push(r);
      for (let k = 0; k < f.h; k++) { const r = rows[k]; for (let c = 0; c < f.w; c++) { const ix = f.idx[k * f.w + c], q = (r * f.w + c) * 4;
        if (ix === f.trans || !f.pal || !f.pal[ix]) continue; const col = f.pal[ix]; d[q] = col[0]; d[q + 1] = col[1]; d[q + 2] = col[2]; d[q + 3] = 255; } }
      pg.putImageData(id, 0, 0);
      g.drawImage(patch, f.x, f.y);
      const snap = document.createElement('canvas'); snap.width = W; snap.height = H; snap.getContext('2d').drawImage(full, 0, 0);
      out.push(snap); ms.push(f.delay);
      if (f.disp === 2) g.clearRect(f.x, f.y, f.w, f.h);
      else if (f.disp === 3 && before) g.putImageData(before, 0, 0);
    }
    if (out.length > MAX_FRAMES) { // keep 64 evenly spaced frames, same loop length
      const keep = [], kms = [], step = out.length / MAX_FRAMES, total = ms.reduce((a, b) => a + b, 0);
      for (let i = 0; i < MAX_FRAMES; i++) { keep.push(out[Math.floor(i * step)]); kms.push(total / MAX_FRAMES); }
      out = keep; ms = kms; toast(`The GIF had more than ${MAX_FRAMES} frames, so every ${step.toFixed(1)}th frame is used.`, 'warn');
    }
    return { out, ms };
  }
  async function takeFiles(list) {
    const files = [...list].filter(f => /^image\//.test(f.type)).sort((a, b) => a.name.localeCompare(b.name, undefined, { numeric: true }));
    if (!files.length) { toast('Pick a GIF, PNG, JPG or WebP image.', 'err'); return; }
    toast('Reading ' + (files.length > 1 ? files.length + ' images' : files[0].name) + '...');
    try {
      if (files.length === 1 && files[0].type === 'image/gif') {
        const { out, ms } = await loadGif(files[0]);
        if (out.length > 1) { S.source = 'frames'; S.art = out; S.artMs = ms; }
        else { S.source = 'still'; S.art = out; S.artMs = []; }
      } else if (files.length === 1) {
        const im = await loadImage(files[0]), ready = asCapeTexture(im);
        if (ready) {
          // already a cape texture (front, back and edges laid out): use it as it is instead of squeezing it onto the back
          S.source = 'sheet'; S.sheet = [ready]; S.art = []; S.artMs = [];
          markEffects(); syncMotion(); rebuild();
          toast('That image is already a cape texture, so it is used as it is. To put a picture on the back instead, use one that isn\u2019t shaped like a cape texture (2:1, 46:22 or 22:17).', 'ok');
          return;
        }
        S.source = 'still'; S.art = [im]; S.artMs = [];
      }
      else { S.source = 'frames'; S.art = []; for (const f of files.slice(0, MAX_FRAMES)) S.art.push(await loadImage(f)); S.artMs = []; }
      if (!$('toast').classList.contains('warn')) toast(S.source === 'frames' ? `${S.art.length} frames loaded.` : 'Image loaded. Pick an overlay in step 2 to animate it.', 'ok');
      S.sheet = [];
      markEffects(); syncMotion(); schedule();
    } catch (e) { toast('Could not read that file: ' + (e && e.message || e), 'err'); }
  }
  /**
   * A canvas in the 64x32 cape layout when the image already is a cape texture: a full 2:1 sheet
   * (64x32, 128x64, or any size close to 2:1 such as 1000x500), an OptiFine-style sheet with the
   * elytra part (46x22 and its multiples), or just the cape part (22x17, or any size that shape).
   * Otherwise null.
   */
  function asCapeTexture(im) {
    const w = im.width, h = im.height, near = (r) => Math.abs(w / h - r) / r < 0.03;
    // the sheet is copied onto a 64 * u by 32 * u canvas, scaled when its size isn't already that
    const sheet = (cols, rows, u) => {
      const c = document.createElement('canvas'); c.width = 64 * u; c.height = 32 * u;
      const g = c.getContext('2d'); g.imageSmoothingEnabled = w > cols * u;  // only when shrinking a big sheet
      g.drawImage(im, 0, 0, cols * u, rows * u); return c;
    };
    if (w >= 64 && near(2)) return sheet(64, 32, Math.max(1, Math.round(w / 64)));
    if (w >= 46 && near(46 / 22)) return sheet(46, 22, Math.max(1, Math.round(w / 46)));
    if (w >= 22 && near(22 / 17)) return sheet(22, 17, Math.max(1, Math.round(w / 22)));
    return null;
  }
  const drop = $('drop');
  drop.addEventListener('click', () => $('file').click());
  drop.addEventListener('keydown', (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); $('file').click(); } });
  $('file').addEventListener('change', (e) => { takeFiles(e.target.files); e.target.value = ''; });
  ['dragenter', 'dragover'].forEach(t => drop.addEventListener(t, (e) => { e.preventDefault(); drop.classList.add('over'); }));
  ['dragleave', 'drop'].forEach(t => drop.addEventListener(t, (e) => { e.preventDefault(); drop.classList.remove('over'); }));
  drop.addEventListener('drop', (e) => takeFiles(e.dataTransfer.files));
  window.addEventListener('paste', (e) => { const f = [...(e.clipboardData?.files || [])]; if (f.length) takeFiles(f); });

  // effect buttons with live thumbnails
  const fxBox = $('effects'), thumbs = [];
  Object.entries(EFFECTS).forEach(([key, fx]) => {
    const b = document.createElement('button'); b.className = 'fx'; b.type = 'button'; b.dataset.key = key;
    const c = document.createElement('canvas'); c.width = 20; c.height = 32; b.append(c, document.createTextNode(fx.name));
    b.addEventListener('click', () => { S.source = 'effect'; S.effect = key; S.art = []; S.artMs = []; S.sheet = []; markEffects(); syncMotion(); schedule(); toast(''); });
    fxBox.append(b); thumbs.push([c, fx]);
  });
  function markEffects() { fxBox.querySelectorAll('.fx').forEach(b => b.setAttribute('aria-pressed', String(S.source === 'effect' && b.dataset.key === S.effect))); }
  function syncMotion() {
    const gifTiming = S.source === 'frames' || S.source === 'sheet';
    $('frames').disabled = gifTiming || (S.source === 'still' && $('overlay').value === 'none');
    $('overlay').disabled = S.source !== 'still' && S.source !== 'frames';
    if (S.source === 'still' && $('overlay').value === 'none') S.frames = 1; else if (!gifTiming) S.frames = +$('frames').value;
    $('framesOut').textContent = S.source === 'sheet' ? `(${S.sheet.length} in the saved cloak)` : gifTiming ? `(${Math.min(MAX_FRAMES, S.art.length)} from your files)` : `(${S.frames})`;
    $('msOut').textContent = `(${$('ms').value} ms)`;
  }

  ['c1', 'c2', 'res', 'fit', 'inside', 'edges', 'rim', 'overlay'].forEach(id => $(id).addEventListener('input', () => {
    if (S.source === 'sheet' && id !== 'c1' && id !== 'c2') toast('This is a saved cloak opened as it is. Pick an effect or new art to change how it looks.', 'warn');
    syncMotion(); schedule();
  }));
  const hex = (r, g, b) => '#' + [r, g, b].map(v => Math.round(v).toString(16).padStart(2, '0')).join('');
  function hsl(hh, s, l) { const a = s * Math.min(l, 1 - l), f = n => { const k = (n + hh / 30) % 12; return 255 * (l - a * Math.max(-1, Math.min(k - 3, 9 - k, 1))); }; return hex(f(0), f(8), f(4)); }
  $('shuffle').addEventListener('click', () => { const base = Math.random() * 360; $('c1').value = hsl(base, .8, .58); $('c2').value = hsl((base + 90 + Math.random() * 120) % 360, .75, .5); syncMotion(); schedule(); });
  $('swap').addEventListener('click', () => { const a = $('c1').value; $('c1').value = $('c2').value; $('c2').value = a; syncMotion(); schedule(); });
  $('frames').addEventListener('input', () => { syncMotion(); schedule(); });
  $('ms').addEventListener('input', () => { S.ms = +$('ms').value; S.artMs = S.source === 'frames' ? [] : S.artMs; syncMotion(); updateStats(); updateYaml(); keepSettings(); });
  ['cid', 'cname', 'price', 'col', 'creator', 'desc'].forEach(id => $(id).addEventListener('input', () => { updateYaml(); keepSettings(); }));

  // ---------- settings kept in this browser (not the art) ----------
  const KEEP = ['c1', 'c2', 'res', 'fit', 'inside', 'edges', 'overlay', 'frames', 'ms', 'cid', 'cname', 'price', 'col', 'creator', 'desc'];
  const SETTINGS = 'plebmc.cloakmaker.settings';
  let keepTimer = 0;
  function keepSettings() {
    clearTimeout(keepTimer);
    keepTimer = setTimeout(() => {
      const o = { effect: S.effect, rim: $('rim').checked };
      for (const id of KEEP) o[id] = $(id).value;
      try { localStorage.setItem(SETTINGS, JSON.stringify(o)); } catch (e) { /* private window */ }
    }, 400);
  }
  function restoreSettings() {
    try {
      const o = JSON.parse(localStorage.getItem(SETTINGS) || 'null');
      if (!o) return;
      for (const id of KEEP) if (typeof o[id] === 'string') $(id).value = o[id];
      if (typeof o.rim === 'boolean') $('rim').checked = o.rim;
      if (EFFECTS[o.effect]) S.effect = o.effect;
      S.ms = +$('ms').value || 90;
    } catch (e) { /* nothing kept */ }
  }

  // ---------- save and copy ----------
  async function sheetBlob() {
    if (!frames.length) throw new Error('Nothing to save yet.');
    const W = frames[0].width, px = W * (W / 2) * frames.length;
    if (px > MAX_PIXELS) throw new Error('Too big for PlebioAPI. Lower the detail or the frames first.');
    const blob = await new Promise(res => sheet().toBlob(res, 'image/png'));
    if (!blob) throw new Error('Could not make the PNG.');
    if (blob.size > MAX_BYTES) throw new Error(`The PNG is ${(blob.size / 1048576).toFixed(1)} MB, over the 8 MB limit. Lower the detail or the frames.`);
    return blob;
  }
  const dataUrl = (blob) => new Promise((res, rej) => { const r = new FileReader(); r.onload = () => res(r.result); r.onerror = () => rej(new Error('Could not read the PNG.')); r.readAsDataURL(blob); });
  function saveBlob(blob, name) {
    const a = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = name; document.body.append(a); a.click(); a.remove();
    setTimeout(() => URL.revokeObjectURL(a.href), 4000);
  }
  $('save').addEventListener('click', async () => {
    try { const blob = await sheetBlob(); saveBlob(blob, safeId() + '.png'); toast(`Saved ${safeId()}.png (${(blob.size / 1024).toFixed(0)} KB).`, 'ok'); }
    catch (e) { toast(e.message, 'err'); }
  });
  $('pic').addEventListener('click', () => {
    const cv = $('three');
    if (!renderer) { toast('The 3D preview is off in this browser.', 'err'); return; }
    cv.toBlob(b => { if (b) saveBlob(b, safeId() + '-preview.png'); }, 'image/png');
  });
  $('copy').addEventListener('click', async () => {
    const text = $('yaml').textContent;
    try { await navigator.clipboard.writeText(text); toast('Config line copied.', 'ok'); }
    catch { const r = document.createRange(); r.selectNodeContents($('yaml')); const s = getSelection(); s.removeAllRanges(); s.addRange(r); toast('Press Ctrl+C to copy the selected line.', 'warn'); }
  });

  // ---------- 3D preview ----------
  const flat = $('flat'), fg = flat.getContext('2d');
  let renderer = null, scene, camera, cape, tex, texCanvas = document.createElement('canvas');
  function setupThree() {
    if (!window.THREE) return;
    const cv = $('three');
    renderer = new THREE.WebGLRenderer({ canvas: cv, antialias: true, alpha: true, preserveDrawingBuffer: true });
    renderer.setPixelRatio(Math.min(2, window.devicePixelRatio || 1));
    scene = new THREE.Scene(); camera = new THREE.PerspectiveCamera(35, 1, .1, 100);
    scene.add(new THREE.AmbientLight(0xffffff, .75)); const dl = new THREE.DirectionalLight(0xffffff, .55); dl.position.set(-2, 3, -4); scene.add(dl);
    const body = new THREE.MeshLambertMaterial({ color: 0x2c3a46 }), skin = new THREE.MeshLambertMaterial({ color: 0x3a4b59 });
    const box = (w, h, d, m, x, y, z) => { const b = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), m); b.position.set(x, y, z); scene.add(b); return b; };
    box(8, 8, 8, skin, 0, 28, 0); box(8, 12, 4, body, 0, 18, 0); box(4, 12, 4, body, -6, 18, 0); box(4, 12, 4, body, 6, 18, 0);
    box(4, 12, 4, skin, -2, 6, 0); box(4, 12, 4, skin, 2, 6, 0);
    texCanvas.width = 64; texCanvas.height = 32; tex = new THREE.CanvasTexture(texCanvas); tex.magFilter = THREE.NearestFilter; tex.minFilter = THREE.NearestFilter;
    const geo = new THREE.BoxGeometry(10, 16, 1), uv = geo.attributes.uv;
    // faces: +x, -x, +y, -y, +z (outside, facing away from the player), -z (inside)
    const R = { px: [11, 1, 1, 16], nx: [0, 1, 1, 16], py: [1, 0, 10, 1], ny: [11, 0, 10, 1], pz: [1, 1, 10, 16], nz: [12, 1, 10, 16] };
    ['px', 'nx', 'py', 'ny', 'pz', 'nz'].forEach((f, fi) => { const [x, y, w, h] = R[f], u0 = x / 64, u1 = (x + w) / 64, vt = 1 - y / 32, vb = 1 - (y + h) / 32;
      const q4 = [[u0, vt], [u1, vt], [u0, vb], [u1, vb]]; q4.forEach((p, k) => uv.setXY(fi * 4 + k, p[0], p[1])); });
    uv.needsUpdate = true;
    cape = new THREE.Mesh(geo, new THREE.MeshLambertMaterial({ map: tex, transparent: true }));
    const pivot = new THREE.Group(); pivot.position.set(0, 24, -2.6); cape.position.set(0, -8, -.5); pivot.add(cape); pivot.rotation.x = .16; scene.add(pivot);
    cape.userData.pivot = pivot;
    let yaw = Math.PI * .82, drag = null, touched = false; // once you turn it yourself it stays where you leave it
    cv.addEventListener('pointerdown', (e) => { drag = e.clientX; touched = true; cv.setPointerCapture(e.pointerId); cv.style.cursor = 'grabbing'; });
    cv.addEventListener('pointermove', (e) => { if (drag !== null) { yaw += (e.clientX - drag) * .01; drag = e.clientX; } });
    cv.addEventListener('pointerup', () => { drag = null; cv.style.cursor = 'grab'; });
    const resize = () => { const r = cv.getBoundingClientRect(); renderer.setSize(r.width, r.height, false); camera.aspect = r.width / Math.max(1, r.height); camera.updateProjectionMatrix(); };
    new ResizeObserver(resize).observe(cv); resize();
    const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;
    return (now) => {
      if (drag === null && !touched && !reduce) yaw += .004;
      camera.position.set(Math.sin(yaw) * 58, 22, Math.cos(yaw) * 58); camera.lookAt(0, 16, 0);
      pivot.rotation.x = .16 + (reduce ? 0 : Math.sin(now / 700) * .04);
      renderer.render(scene, camera);
    };
  }
  const draw3d = setupThree();
  let last = performance.now(), acc = 0, idx = 0, thumbT = 0;
  function loop(now) {
    const dt = now - last; last = now;
    if (frames.length) {
      acc += dt; const ms = S.ms || 100; while (acc >= ms) { acc -= ms; idx = (idx + 1) % frames.length; }
      const f = frames[idx % frames.length];
      if (flat.width !== f.width) { flat.width = f.width; flat.height = f.height; }
      fg.clearRect(0, 0, flat.width, flat.height); fg.drawImage(f, 0, 0);
      flat.style.width = Math.min(176, f.width * Math.max(1, Math.floor(176 / f.width))) + 'px';
      if (tex) { if (texCanvas.width !== f.width) { texCanvas.width = f.width; texCanvas.height = f.height; } texCanvas.getContext('2d').drawImage(f, 0, 0); tex.needsUpdate = true; }
    }
    thumbT += dt; if (thumbT > 90) { thumbT = 0; const A = hexToRgb($('c1').value), B = hexToRgb($('c2').value), t = (now / 2000) % 1;
      thumbs.forEach(([c, fx]) => fx.draw(c.getContext('2d'), c.width, c.height, t, A, B)); }
    if (draw3d) draw3d(now);
    requestAnimationFrame(loop);
  }
  // ---------- your account (save, share, publish) ----------
  function thumb() {
    const f = frames[0], u = f.width / 64, c = document.createElement('canvas'); c.width = 120; c.height = 96;
    const g = c.getContext('2d'); g.imageSmoothingEnabled = false;
    // the back of the cloak (10 x 16) and the inside, side by side
    g.drawImage(f, u, u, 10 * u, 16 * u, 22, 8, 50, 80);
    g.globalAlpha = .55; g.drawImage(f, 12 * u, u, 10 * u, 16 * u, 78, 24, 32, 52); g.globalAlpha = 1;
    return c.toDataURL('image/png');
  }
  async function loadSheet(src, W, ms) {
    const img = new Image(); img.src = src; await img.decode();
    W = W || img.naturalWidth; const H = W / 2, n = Math.max(1, Math.min(MAX_FRAMES, Math.floor(img.naturalHeight / H)));
    const out = [];
    for (let i = 0; i < n; i++) { const c = document.createElement('canvas'); c.width = W; c.height = H; c.getContext('2d').drawImage(img, 0, i * H, W, H, 0, 0, W, H); out.push(c); }
    S.source = 'sheet'; S.sheet = out; S.art = []; S.artMs = []; S.ms = Math.max(20, Math.min(500, ms || 90));
    markEffects(); syncMotion(); rebuild();
  }
  window.PlebTool.mount($('account'), {
    kind: 'cloak',
    status: (m, bad) => toast(m, bad ? 'err' : 'ok'),
    async get() {
      const blob = await sheetBlob();
      return { name: $('cname').value || 'My Cloak', itemId: safeId(), data: await dataUrl(blob), thumb: thumb(),
        extra: { frametime: S.ms, frames: frames.length, resolution: frames[0].width, description: $('desc').value, price: parseInt($('price').value, 10) || 0 } };
    },
    async set(d) {
      const x = d.extra || {};
      $('cname').value = d.mine ? d.name : d.name + ' (remix)'; $('cid').value = d.itemId || 'my_cloak';
      if (x.description) $('desc').value = x.description;
      if (x.price != null) $('price').value = x.price;
      await loadSheet(d.data, x.resolution, x.frametime);
    },
    async publishFile() {
      const blob = await sheetBlob();
      return { file: await dataUrl(blob), frametime: frames.length > 1 ? S.ms : null };
    },
  });

  restoreSettings();
  markEffects(); syncMotion(); rebuild(); requestAnimationFrame(loop);
})();
