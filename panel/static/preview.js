// 3D previews of shop cosmetics, drawn the same way PlebClient draws them in game
// (a port of CosmeticLayer: the same boxes, colors and wing shapes).
"use strict";

window.PlebPreview = (() => {
  const T = () => window.THREE;
  const CROWN = [[-5,7.8,-5,5,10.2,5,85,87,95,89,96,87,106,89,107,87,117,89,118,87,128,89,97,20,107,30,108,20,118,30],[-5.2,7.6,-5.2,5.2,8.2,5.2,91,99,101,100,102,99,112,100,113,99,123,100,124,99,134,100,119,20,129,30,130,20,140,30],[-5.2,10,-5.2,5.2,10.6,5.2,135,99,145,100,146,99,156,100,157,99,167,100,168,99,178,100,141,20,151,30,152,20,162,30],[-4,10.2,-3.8,4,13.2,3.8,239,70,247,73,1,75,9,78,10,75,18,78,19,75,27,78,99,32,107,40,108,32,116,40],[-2.8,13.2,-2.6,2.8,14.2,2.6,135,101,141,102,142,101,148,102,223,101,228,102,229,101,234,102,105,57,111,62,112,57,118,62],[-0.8,8.4,-5.55,0.8,9.8,-5.05,145,105,147,106,148,105,150,106,105,109,106,110,107,109,108,110,151,105,153,106,154,105,156,106],[-0.8,8.4,5,0.8,9.8,5.5,157,105,159,106,160,105,162,106,109,109,110,110,111,109,112,110,163,105,165,106,166,105,168,106],[2.3,8.5,-5.45,3.5,9.7,-5.05,113,109,114,110,115,109,116,110,117,109,118,110,119,109,120,110,121,109,122,110,123,109,124,110],[5,8.5,-0.7,5.5,9.7,0.7,125,109,126,110,127,109,128,110,129,109,130,110,131,109,132,110,133,109,134,110,135,109,136,110],[4.2,10.5,-0.7,5,12.7,0.7,234,93,235,95,236,93,237,95,238,93,239,95,240,93,241,95,137,109,138,110,139,109,140,110],[4.1,12.5,-0.5,5.1,13.5,0.5,141,109,142,110,143,109,144,110,145,109,146,110,147,109,148,110,149,109,150,110,151,109,152,110],[-3.5,8.5,-5.45,-2.3,9.7,-5.05,153,109,154,110,155,109,156,110,157,109,158,110,159,109,160,110,161,109,162,110,163,109,164,110],[-5.5,8.5,-0.7,-5,9.7,0.7,165,109,166,110,167,109,168,110,169,109,170,110,171,109,172,110,173,109,174,110,175,109,176,110],[-5,10.5,-0.7,-4.2,12.7,0.7,242,93,243,95,244,93,245,95,246,93,247,95,248,93,249,95,177,109,178,110,179,109,180,110],[-5.1,12.5,-0.5,-4.1,13.5,0.5,181,109,182,110,183,109,184,110,185,109,186,110,187,109,188,110,189,109,190,110,191,109,192,110],[-4.1,10.5,-5,-2.7,12.7,-4.2,250,93,251,95,252,93,253,95,254,93,255,95,1,96,2,98,193,109,194,110,195,109,196,110],[-3.9,12.5,-5.1,-2.9,13.5,-4.1,197,109,198,110,199,109,200,110,201,109,202,110,203,109,204,110,205,109,206,110,207,109,208,110],[-4.1,10.5,4.2,-2.7,12.7,5,3,96,4,98,5,96,6,98,7,96,8,98,9,96,10,98,209,109,210,110,211,109,212,110],[-3.9,12.5,4.1,-2.9,13.5,5.1,213,109,214,110,215,109,216,110,217,109,218,110,219,109,220,110,221,109,222,110,223,109,224,110],[-0.9,10.5,-5,0.9,13.5,-4.2,4,83,6,86,7,83,9,86,46,83,47,86,48,83,49,86,169,105,171,106,172,105,174,106],[-0.5,13.5,-5.1,0.5,14.5,-4.1,225,109,226,110,227,109,228,110,229,109,230,110,231,109,232,110,233,109,234,110,235,109,236,110],[-0.7,10.5,4.2,0.7,12.7,5,11,96,12,98,13,96,14,98,15,96,16,98,17,96,18,98,237,109,238,110,239,109,240,110],[-0.5,12.5,4.1,0.5,13.5,5.1,241,109,242,110,243,109,244,110,245,109,246,110,247,109,248,110,249,109,250,110,251,109,252,110],[2.7,10.5,-5,4.1,12.7,-4.2,19,96,20,98,21,96,22,98,23,96,24,98,25,96,26,98,253,109,254,110,1,111,2,112],[2.9,12.5,-5.1,3.9,13.5,-4.1,3,111,4,112,5,111,6,112,7,111,8,112,9,111,10,112,11,111,12,112,13,111,14,112],[2.7,10.5,4.2,4.1,12.7,5,27,96,28,98,29,96,30,98,31,96,32,98,33,96,34,98,15,111,16,112,17,111,18,112],[2.9,12.5,4.1,3.9,13.5,5.1,19,111,20,112,21,111,22,112,23,111,24,112,25,111,26,112,27,111,28,112,29,111,30,112],[-0.6,14,-3.2,0.6,14.8,3.2,31,111,32,112,33,111,34,112,149,101,155,102,156,101,162,102,5,57,6,63,7,57,8,63],[-3.6,14,-0.6,3.6,14.8,0.6,103,101,110,102,111,101,118,102,35,111,36,112,37,111,38,112,119,101,126,102,127,101,134,102],[-0.35,14.8,-0.35,0.35,16.8,0.35,35,96,36,98,37,96,38,98,39,96,40,98,41,96,42,98,39,111,40,112,41,111,42,112],[-1.1,15.6,-0.35,1.1,16.2,0.35,175,105,177,106,178,105,180,106,43,111,44,112,45,111,46,112,181,105,183,106,184,105,186,106]];

  // ---------- geometry builders ----------

  // collects colored quads (head space: pixels, y down from the neck, front is -z)
  function Quads() {
    const pos = [], col = [];
    function face(rgb, k, a, b, c, d) {
      const r = ((rgb >> 16) & 255) / 255 * k, g = ((rgb >> 8) & 255) / 255 * k, bl = (rgb & 255) / 255 * k;
      for (const p of [a, b, c, a, c, d]) { pos.push(p[0], p[1], p[2]); col.push(r, g, bl); }
    }
    return {
      face,
      box(x0, y0, z0, x1, y1, z1, rgb) {
        face(rgb, 1.0, [x0, y0, z0], [x1, y0, z0], [x1, y0, z1], [x0, y0, z1]);
        face(rgb, 0.5, [x0, y1, z0], [x0, y1, z1], [x1, y1, z1], [x1, y1, z0]);
        face(rgb, 0.8, [x0, y0, z0], [x0, y1, z0], [x1, y1, z0], [x1, y0, z0]);
        face(rgb, 0.8, [x0, y0, z1], [x1, y0, z1], [x1, y1, z1], [x0, y1, z1]);
        face(rgb, 0.62, [x0, y0, z0], [x0, y0, z1], [x0, y1, z1], [x0, y1, z0]);
        face(rgb, 0.62, [x1, y0, z0], [x1, y1, z0], [x1, y1, z1], [x1, y0, z1]);
      },
      mesh() {
        const g = new (T().BufferGeometry)();
        g.setAttribute("position", new (T().Float32BufferAttribute)(pos, 3));
        g.setAttribute("color", new (T().Float32BufferAttribute)(col, 3));
        return new (T().Mesh)(g, new (T().MeshBasicMaterial)({ vertexColors: true, side: T().DoubleSide }));
      },
    };
  }

  function shade(rgb, k) {
    const c = (s) => Math.min(255, Math.floor(((rgb >> s) & 255) * k));
    return (c(16) << 16) | (c(8) << 8) | c(0);
  }
  function mix(a, b, t) {
    const c = (s) => Math.round(((a >> s) & 255) * (1 - t) + ((b >> s) & 255) * t);
    return (c(16) << 16) | (c(8) << 8) | c(0);
  }
  const tintOf = (it) => (it.color ? parseInt(it.color.slice(1), 16) : -1);

  // ---------- hats ----------

  const HATS = {
    tophat(q, c) { c = c < 0 ? 0x1c1c1c : c;
      q.box(-5.6, -8.7, -5.6, 5.6, -8.0, 5.6, c); q.box(-3.6, -10.2, -3.6, 3.6, -8.7, 3.6, 0xa01c1c); q.box(-3.5, -15.5, -3.5, 3.5, -10.2, 3.5, c); },
    crown(q, gold) { gold = gold < 0 ? 0xf2c230 : gold;
      const r = 4.4, w = 0.8, y0 = -10.6, y1 = -7.9;
      q.box(-r, y0, -r, r, y1, -r + w, gold); q.box(-r, y0, r - w, r, y1, r, gold);
      q.box(-r, y0, -r, -r + w, y1, r, gold); q.box(r - w, y0, -r, r, y1, r, gold);
      const p = [-r, -0.5, r - 1.0];
      for (const a of p) for (const b of p) {
        if (a !== -0.5 || b !== -0.5) {
          if (a === -r || a === r - 1.0 || b === -r || b === r - 1.0) q.box(a, y0 - 1.8, b, a + 1.0, y0, b + 1.0, gold);
        }
      }
      q.box(-0.7, -9.8, -r - 0.25, 0.7, -8.6, -r + 0.1, 0xd3202a); q.box(-0.7, -9.8, r - 0.1, 0.7, -8.6, r + 0.25, 0x2a6bd3); },
    halo(q, c) { c = c < 0 ? 0xffe66b : c;
      const y = -11.0, r = 4.6, n = 20;
      for (let i = 0; i < n; i++) { const a = i * Math.PI * 2 / n, x = Math.cos(a) * r, z = Math.sin(a) * r;
        q.box(x - 0.75, y - 0.35, z - 0.75, x + 0.75, y + 0.35, z + 0.75, c); } },
    partyhat(q, tint) {
      const stripes = tint < 0 ? [0xe84a5f, 0xffd166, 0x06d6a0, 0x118ab2] : [tint, 0xffffff];
      const layers = 7;
      for (let i = 0; i < layers; i++) { const half = 3.6 - i * 0.48, y1 = -8.0 - i * 1.1; q.box(-half, y1 - 1.1, -half, half, y1, half, stripes[i % stripes.length]); }
      q.box(-0.8, -8.0 - layers * 1.1 - 1.4, -0.8, 0.8, -8.0 - layers * 1.1, 0.8, 0xffffff); },
    beanie(q, c) { c = c < 0 ? 0x3a7bd5 : c;
      q.box(-4.4, -9.4, -4.4, 4.4, -6.2, 4.4, c); q.box(-4.7, -6.6, -4.7, 4.7, -5.4, 4.7, shade(c, 0.75)); q.box(-1.2, -10.8, -1.2, 1.2, -9.4, 1.2, 0xf2f2f2); },
    horns(q, c) { c = c < 0 ? 0xb3201e : c; const tip = shade(c, 0.7);
      for (const s of [-1, 1]) { const x = s * 2.6;
        q.box(x - 0.9, -9.4, -1.0, x + 0.9, -7.8, 0.8, c); q.box(x + s * 0.4 - 0.7, -10.8, -0.8, x + s * 0.4 + 0.7, -9.4, 0.6, c);
        q.box(x + s * 0.9 - 0.45, -12.0, -0.6, x + s * 0.9 + 0.45, -10.8, 0.3, tip); } },
    cap(q, c) { c = c < 0 ? 0x2d9cdb : c;
      q.box(-4.3, -9.4, -4.3, 4.3, -7.6, 4.3, c); q.box(-1.6, -9.9, -1.6, 1.6, -9.4, 1.6, shade(c, 0.85));
      q.box(-4.1, -7.9, -8.2, 4.1, -7.3, -4.3, shade(c, 0.8)); q.box(-1.2, -9.0, -4.5, 1.2, -8.0, -4.3, 0xffffff); },
    headphones(q, c) { c = c < 0 ? 0x3fd8e6 : c; const band = 0x23272b;
      q.box(-4.7, -9.1, -0.8, 4.7, -8.3, 0.8, band);
      for (const s of [-1, 1]) { const x = s * 4.5;
        q.box(Math.min(x, x + s * 0.4), -8.3, -0.8, Math.max(x, x + s * 0.4), -5.6, 0.8, band);
        q.box(s < 0 ? -5.8 : 4.3, -6.2, -2.0, s < 0 ? -4.3 : 5.8, -2.4, 2.0, c);
        q.box(s < 0 ? -6.1 : 5.8, -5.6, -1.4, s < 0 ? -5.8 : 6.1, -3.0, 1.4, shade(c, 0.7)); } },
    bunny(q, c) { c = c < 0 ? 0xf4f4f4 : c; const inner = 0xf7a1c4;
      for (const s of [-1, 1]) { const x = s * 2.0;
        q.box(x - 1.0, -12.0, -0.4, x + 1.0, -8.0, 0.6, c); q.box(x + s * 0.4 - 0.9, -15.5, -0.4, x + s * 0.4 + 0.9, -12.0, 0.6, c);
        q.box(x - 0.5, -11.6, -0.6, x + 0.5, -8.4, -0.4, inner); q.box(x + s * 0.4 - 0.45, -15.0, -0.6, x + s * 0.4 + 0.45, -12.0, -0.4, inner); } },
    wizard(q, c) { c = c < 0 ? 0x3b2a8c : c;
      q.box(-6.2, -8.6, -6.2, 6.2, -7.9, 6.2, c);
      for (let i = 0; i < 8; i++) { const half = 3.9 - i * 0.45, lean = i > 4 ? (i - 4) * 0.55 : 0, y1 = -8.6 - i * 1.15;
        q.box(-half, y1 - 1.15, -half + lean, half, y1, half + lean, i === 1 ? 0xe8c547 : c); }
      const star = 0xffe066;
      q.box(-2.2, -12.0, -3.2, -1.4, -11.2, -3.0, star); q.box(1.0, -10.6, -3.6, 1.8, -9.8, -3.4, star); q.box(-0.6, -14.0, -2.4, 0.2, -13.2, -2.2, star); },
    viking(q, metal) { metal = metal < 0 ? 0x8a929b : metal; const horn = 0xede4cc;
      q.box(-4.6, -9.4, -4.6, 4.6, -6.2, 4.6, metal); q.box(-3.4, -10.2, -3.4, 3.4, -9.4, 3.4, shade(metal, 1.1));
      q.box(-4.8, -6.8, -4.8, 4.8, -5.8, 4.8, 0x6b4a2b); q.box(-0.5, -6.0, -4.9, 0.5, -3.6, -4.6, metal);
      q.box(-0.4, -10.3, -3.6, 0.4, -6.2, 3.6, shade(metal, 0.8));
      for (const s of [-1, 1]) {
        q.box(s < 0 ? -6.4 : 4.6, -8.6, -0.8, s < 0 ? -4.6 : 6.4, -7.2, 0.8, horn);
        q.box(s < 0 ? -7.4 : 6.0, -10.6, -0.6, s < 0 ? -6.0 : 7.4, -8.6, 0.6, horn);
        q.box(s < 0 ? -7.8 : 6.8, -12.0, -0.4, s < 0 ? -6.8 : 7.8, -10.6, 0.4, shade(horn, 0.85)); } },
    cowboy(q, c) { c = c < 0 ? 0x8b5a2b : c;
      q.box(-7.0, -8.4, -6.0, 7.0, -7.8, 6.0, c); q.box(-7.0, -9.4, -6.0, -6.0, -8.4, 6.0, c); q.box(6.0, -9.4, -6.0, 7.0, -8.4, 6.0, c);
      q.box(-3.8, -12.2, -3.8, 3.8, -8.4, 3.8, c); q.box(-3.9, -9.4, -3.9, 3.9, -8.4, 3.9, 0x3a2414); q.box(-0.9, -12.3, -3.9, 0.9, -11.6, 3.9, shade(c, 0.75)); },
    propeller(q) { const k = [0xe84a5f, 0xffd166, 0x118ab2, 0x06d6a0];
      q.box(-4.4, -9.3, -4.4, 0, -7.6, 0, k[0]); q.box(0, -9.3, -4.4, 4.4, -7.6, 0, k[1]);
      q.box(0, -9.3, 0, 4.4, -7.6, 4.4, k[2]); q.box(-4.4, -9.3, 0, 0, -7.6, 4.4, k[3]);
      q.box(-4.6, -7.9, -4.6, 4.6, -7.3, 4.6, 0x2a2a2a); q.box(-0.3, -11.0, -0.3, 0.3, -9.3, 0.3, 0x333333); },
    chef(q, c) { c = c < 0 ? 0xffffff : c;
      q.box(-4.4, -9.8, -4.4, 4.4, -7.6, 4.4, shade(c, 0.95)); q.box(-5.2, -13.8, -5.2, 5.2, -9.8, 5.2, c);
      q.box(-3.6, -14.8, -3.6, 3.6, -13.8, 3.6, c); q.box(-5.4, -12.6, -1.6, 5.4, -10.6, 1.6, c); q.box(-1.6, -12.6, -5.4, 1.6, -10.6, 5.4, c); },
  };

  // ---------- wings ----------

  const wingWidth = (r) => (r === "butterfly" || r === "bat" ? 11 : 13);
  const sq = (f) => f * f;
  function wingCell(render, u, v) {
    const w = wingWidth(render);
    if (u < 0 || u >= w) return 0;
    if (render === "angel") {
      let top = 2 - Math.round(5 * Math.sin(Math.min(1, u / (w - 2)) * Math.PI / 2));
      if (u === w - 1) top += 2;
      const len = Math.round(12 - 0.62 * u) + (u % 2 === 0 ? 1 : 0), d = v - top;
      if (d < 0 || d >= len) return 0;
      if (d === 0) return 2;
      return u % 3 === 2 && d > len * 0.4 ? 4 : d > len - 2 ? 4 : 1;
    }
    if (render === "butterfly") {
      const inside = (x, y) => sq((x - 5.6) / 5.6) + sq((y + 1.8) / 5.2) <= 1 || sq((x - 3.6) / 3.8) + sq((y - 6.5) / 4.2) <= 1;
      const x = u + 0.5, y = v + 0.5;
      const upper = sq((x - 5.6) / 5.6) + sq((y + 1.8) / 5.2) <= 1;
      if (!inside(x, y)) return 0;
      for (const [du, dv] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
        const nu = u + du;
        if (nu >= 0 && !inside(nu + 0.5, v + dv + 0.5)) return 2;
      }
      if (sq(x - 7.2) + sq(y + 2.2) <= 2.3 || sq(x - 3.5) + sq(y - 7.5) <= 1.2) return 3;
      return upper ? 1 : 4;
    }
    const bat = render === "bat";
    const top = 2 - Math.round(u * (bat ? 0.45 : 0.6));
    const period = Math.max(3, Math.floor((w + 1) / 3)), f = u % period;
    const finger = f === period - 1 || u === w - 1;
    const dip = finger ? 0 : Math.sin(Math.PI * (f + 1) / period) * (bat ? 3.2 : 4);
    const bottom = Math.round((bat ? 8.5 : 10.5) - 0.3 * u - dip);
    if (render === "demon" && finger && v === top - 1) return 2;
    if (render === "dragon" && u === w - 1 && v >= top - 2 && v < top) return 3;
    if (v < top || v > bottom) return 0;
    if (v === top || finger) return 2;
    return render === "dragon" && (u + v) % 4 === 0 ? 4 : 1;
  }
  function wingColors(render, tint) {
    const c = { angel: [0xf4f4f4, 0xffffff, 0xffffff, 0xc9cfd8], demon: [0x8a1414, 0x2a0808, 0xff5a2a, 0x5e0d0d],
      dragon: [0x2f7d3a, 0x173f1d, 0xede6cf, 0x256530], butterfly: [0x3fa9f5, 0x15182b, 0xffffff, 0xff8a3d] }[render]
      || [0x34343f, 0x16161c, 0x9a2a2a, 0x2a2a33];
    const out = c.slice();
    if (tint >= 0) {
      out[0] = tint;
      out[3] = render === "butterfly" ? mix(tint, 0xff8a3d, 0.5) : mix(tint, 0, 0.22);
      if (render === "angel") out[1] = mix(tint, 0xffffff, 0.45);
    }
    return out;
  }
  function wingMesh(render, tint) {
    const q = Quads(), colors = wingColors(render, tint), w = wingWidth(render);
    for (let u = 0; u < w; u++) {
      let start = -12, cur = 0;
      for (let v = -12; v <= 16; v++) {
        const c = v === 16 ? 0 : wingCell(render, u, v);
        if (c !== cur) {
          if (cur !== 0) q.box(u, start, 0, u + 1, v, 0.7, colors[cur - 1]);
          cur = c; start = v;
        }
      }
    }
    return q.mesh();
  }

  // ---------- textures and models from the panel ----------

  const texCache = new Map();
  function texture(url) {
    if (!texCache.has(url)) {
      texCache.set(url, new Promise((ok) => {
        new (T().TextureLoader)().load(url, (t) => {
          t.magFilter = T().NearestFilter; t.minFilter = T().NearestFilter; t.generateMipmaps = false;
          ok(t);
        }, undefined, () => ok(null));
      }));
    }
    return texCache.get(url);
  }
  const jsonCache = new Map();
  function json(url) {
    if (!jsonCache.has(url)) jsonCache.set(url, fetch(url).then((r) => (r.ok ? r.json() : null)).catch(() => null));
    return jsonCache.get(url);
  }

  // textured quads (y up model space): 20 floats each, 4 corners of x, y, z, u, v
  function quadMesh(q, tex) {
    const pos = [], uv = [], col = [];
    for (let i = 0; i + 20 <= q.length; i += 20) {
      const ax = q[i + 5] - q[i], ay = q[i + 6] - q[i + 1], az = q[i + 7] - q[i + 2];
      const bx = q[i + 15] - q[i], by = q[i + 16] - q[i + 1], bz = q[i + 17] - q[i + 2];
      let nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
      const len = Math.hypot(nx, ny, nz) || 1;
      nx = -nx / len; ny = -ny / len; nz = -nz / len;
      const s = len < 1e-6 ? 0.8 : Math.max(0, ny) + Math.max(0, -ny) * 0.5 + Math.abs(nx) * 0.62 + Math.abs(nz) * 0.8;
      const push = 0.012 + Math.min(40, Math.floor(i / 120)) * 0.003;
      for (const c of [0, 1, 2, 0, 2, 3]) {
        const k = i + c * 5;
        pos.push(q[k] + nx * push, q[k + 1] + ny * push, q[k + 2] + nz * push);
        uv.push(q[k + 3], q[k + 4]);
        col.push(s, s, s);
      }
    }
    const g = new (T().BufferGeometry)();
    g.setAttribute("position", new (T().Float32BufferAttribute)(pos, 3));
    g.setAttribute("uv", new (T().Float32BufferAttribute)(uv, 2));
    g.setAttribute("color", new (T().Float32BufferAttribute)(col, 3));
    const t = tex.clone(); t.flipY = false; t.needsUpdate = true;
    return new (T().Mesh)(g, new (T().MeshBasicMaterial)({ map: t, vertexColors: true, side: T().DoubleSide, transparent: true, alphaTest: 0.1 }));
  }

  // the textured crown (CrownModel) as quads
  function crownQuads() {
    const k = 1 / 256, out = [];
    const tface = (b, i, a, bb, c, d) => {
      if (b[i] < 0) return;
      const u0 = b[i] * k, v0 = b[i + 1] * k, u1 = b[i + 2] * k, v1 = b[i + 3] * k;
      out.push(...a, u0, v0, ...bb, u1, v0, ...c, u1, v1, ...d, u0, v1);
    };
    for (const b of CROWN) {
      const [x0, y0, z0, x1, y1, z1] = b;
      tface(b, 6, [x1, y1, z0], [x0, y1, z0], [x0, y0, z0], [x1, y0, z0]);
      tface(b, 10, [x0, y1, z1], [x1, y1, z1], [x1, y0, z1], [x0, y0, z1]);
      tface(b, 14, [x1, y1, z1], [x1, y1, z0], [x1, y0, z0], [x1, y0, z1]);
      tface(b, 18, [x0, y1, z0], [x0, y1, z1], [x0, y0, z1], [x0, y0, z0]);
      tface(b, 22, [x0, y1, z0], [x1, y1, z0], [x1, y1, z1], [x0, y1, z1]);
      tface(b, 26, [x0, y0, z1], [x1, y0, z1], [x1, y0, z0], [x0, y0, z0]);
    }
    return out;
  }

  async function modelGroup(it) {
    const [m, tex] = await Promise.all([json("/api/asset/" + it.model), it.texture ? texture("/api/asset/" + it.texture) : null]);
    if (!m || !Array.isArray(m.quads) || !tex) return null;
    const g = new (T().Group)();
    g.scale.set(1, -1, 1); // the model is y up
    g.add(quadMesh(m.quads, tex));
    return g;
  }

  // ---------- the player ----------

  function player() {
    const q = Quads();
    const skin = 0xc69a74, hair = 0x3b2a1a, shirt = 0x2e9ea6, pants = 0x3a3fa0;
    q.box(-4, -8, -4, 4, 0, 4, skin);
    q.box(-4.05, -8.05, -4.05, 4.05, -6.5, 4.05, hair);
    q.box(-4.05, -6.5, -0.5, 4.05, -3.5, 4.05, hair);
    q.box(-3, -4, -4.1, -1, -3, -4, 0xffffff); q.box(-2, -4, -4.12, -1, -3, -4.1, 0x4a3aa8);
    q.box(1, -4, -4.1, 3, -3, -4, 0xffffff); q.box(1, -4, -4.12, 2, -3, -4.1, 0x4a3aa8);
    q.box(-1, -3, -4.1, 1, -2, -4, 0x9c6c4c); q.box(-2, -2, -4.1, 2, -1, -4, 0x6e4430);
    q.box(-4, 0, -2, 4, 12, 2, shirt);
    for (const s of [-1, 1]) {
      q.box(s < 0 ? -8 : 4, 0, -2, s < 0 ? -4 : 8, 4, 2, shirt);
      q.box(s < 0 ? -8 : 4, 4, -2, s < 0 ? -4 : 8, 12, 2, skin);
      q.box(s < 0 ? -4 : 0, 12, -2, s < 0 ? 0 : 4, 22, 2, pants);
      q.box(s < 0 ? -4 : 0, 22, -2, s < 0 ? 0 : 4, 24, 2, 0x55555f);
    }
    return q.mesh();
  }

  // the cloak, 10x16x1 behind the back with its texture (the standard 64x32 cape layout)
  function cloakMesh(tex, frames) {
    const pos = [], uv = [];
    const quad = (a, b, c, d, u0, v0, u1, v1) => {
      const U = (u) => u / 64, V = (v) => 1 - v / 32;
      const t = [[U(u0), V(v0)], [U(u1), V(v0)], [U(u1), V(v1)], [U(u0), V(v1)]];
      for (const i of [0, 1, 2, 0, 2, 3]) { pos.push(...[a, b, c, d][i]); uv.push(...t[i]); }
    };
    const x0 = -5, x1 = 5, y0 = 0, y1 = 16, z0 = 2, z1 = 3;
    quad([x1, y0, z1], [x0, y0, z1], [x0, y1, z1], [x1, y1, z1], 1, 1, 11, 17); // outside
    quad([x0, y0, z0], [x1, y0, z0], [x1, y1, z0], [x0, y1, z0], 12, 1, 22, 17); // against the back
    quad([x0, y0, z0], [x0, y0, z1], [x0, y1, z1], [x0, y1, z0], 0, 1, 1, 17);
    quad([x1, y0, z1], [x1, y0, z0], [x1, y1, z0], [x1, y1, z1], 11, 1, 12, 17);
    quad([x0, y0, z1], [x1, y0, z1], [x1, y0, z0], [x0, y0, z0], 1, 0, 11, 1);
    quad([x0, y1, z0], [x1, y1, z0], [x1, y1, z1], [x0, y1, z1], 11, 0, 21, 1);
    const g = new (T().BufferGeometry)();
    g.setAttribute("position", new (T().Float32BufferAttribute)(pos, 3));
    g.setAttribute("uv", new (T().Float32BufferAttribute)(uv, 2));
    const t = tex.clone();
    t.needsUpdate = true;
    t.repeat.set(1, 1 / frames);
    t.offset.set(0, 1 - 1 / frames);
    const m = new (T().Mesh)(g, new (T().MeshBasicMaterial)({ map: t, side: T().DoubleSide, transparent: true, alphaTest: 0.1 }));
    m.userData.tex = t;
    return m;
  }

  // ---------- one item on a player ----------

  /**
   * Builds the scene for an item: {root, focus, update(ticks)}. The root is in
   * world space (y up, feet at 0, the player faces +z); focus says where to look.
   */
  async function build(it, withPlayer = true) {
    const root = new (T().Group)();
    const body = new (T().Group)(); // head/body space: pixels, y down from the neck, front is -z
    body.position.y = 24;
    body.scale.set(1, -1, -1);
    root.add(body);
    if (withPlayer) body.add(player());
    const updates = [];
    const tint = tintOf(it);
    let focus = "body";
    if (it.category === "hat") {
      focus = "head";
      if (it.render === "model" && it.model) {
        const g = await modelGroup(it);
        if (g) body.add(g);
      } else if (it.render === "crown") {
        const tex = await texture("/static/shop/crown.png");
        if (tex) {
          const g = new (T().Group)();
          g.scale.set(1, -1, 1);
          g.add(quadMesh(crownQuads(), tex));
          body.add(g);
        } else {
          const q = Quads(); HATS.crown(q, tint); body.add(q.mesh());
        }
      } else if (HATS[it.render]) {
        const q = Quads();
        HATS[it.render](q, tint);
        const mesh = q.mesh();
        if (it.render === "halo") {
          updates.push((t) => { mesh.position.y = Math.sin(t * 0.08) * 0.35; });
        }
        body.add(mesh);
        if (it.render === "propeller") {
          const spin = new (T().Group)();
          spin.position.y = -11;
          for (const [i, rgb] of [[0, 0xe84a5f], [1, 0x118ab2]]) {
            const b = Quads();
            b.box(0, -0.25, -0.9, 4.8, 0.25, 0.9, rgb);
            const m = b.mesh();
            m.rotation.y = i * Math.PI;
            spin.add(m);
          }
          updates.push((t) => { spin.rotation.y = -t * 0.6; });
          body.add(spin);
        }
      }
    } else if (it.category === "wings") {
      const model = it.render === "model";
      const g = model ? await modelGroup(it) : null;
      for (const side of [-1, 1]) {
        const outer = new (T().Group)();
        outer.position.set(0, model ? 12 : 0, model ? 2 : 2.1);
        outer.scale.set(side, 1, 1);
        const pivot = new (T().Group)();
        if (!model) pivot.position.x = 0.6;
        outer.add(pivot);
        const part = model ? (g ? g.clone() : null) : wingMesh(it.render, tint);
        if (part) pivot.add(part);
        updates.push((t) => {
          const beat = Math.sin(t * 0.22);
          const flap = (30 + 24 * beat) * Math.PI / 180, lift = 9 * beat * Math.PI / 180;
          pivot.rotation.set(0, -flap, model ? lift : -lift, "YZX");
        });
        body.add(outer);
      }
    } else if (it.category === "cloak" && it.texture) {
      const tex = await texture("/api/asset/" + it.texture);
      if (tex) {
        const frames = it.frames || 1;
        const m = cloakMesh(tex, frames);
        const tilt = new (T().Group)();
        tilt.position.z = 2;
        m.position.z = -2;
        tilt.rotation.x = 8 * Math.PI / 180; // hangs out a little from the back
        tilt.add(m);
        body.add(tilt);
        if (frames > 1) {
          const per = Math.max(1, it.frameTime || 2); // ticks per frame
          updates.push((t) => {
            const f = Math.floor(t / per) % frames;
            m.userData.tex.offset.y = 1 - (f + 1) / frames;
          });
        }
      }
    }
    return { root, focus, back: it.category !== "hat", update: (t) => updates.forEach((u) => u(t)) };
  }

  // ---------- drawing ----------

  function camera(scene, aspect, yaw, pitch) {
    const cam = new (T().PerspectiveCamera)(30, aspect, 1, 1000);
    const head = scene.focus === "head";
    const target = head ? new (T().Vector3)(0, 30, 0) : new (T().Vector3)(0, 18, 0);
    const dist = head ? 52 : 76;
    const a = yaw + (scene.back ? Math.PI : 0);
    cam.position.set(Math.sin(a) * Math.cos(pitch) * dist, target.y + Math.sin(pitch) * dist, Math.cos(a) * Math.cos(pitch) * dist);
    cam.lookAt(target);
    return cam;
  }

  let shared = null;
  const thumbs = new Map();
  /** A still picture of an item (a data URL), drawn once and kept. */
  function thumb(it, size = 200) {
    const key = it.id + "|" + (it.texture || "") + "|" + (it.model || "") + "|" + (it.color || "") + "|" + size;
    if (!thumbs.has(key)) {
      thumbs.set(key, (async () => {
        if (!window.THREE) return null;
        if (!shared) {
          try {
            shared = new (T().WebGLRenderer)({ antialias: true, alpha: true, preserveDrawingBuffer: true });
          } catch (e) {
            return null;
          }
        }
        shared.setPixelRatio(1);
        shared.setSize(size, size, false);
        const s = await build(it);
        s.update(20);
        const scene = new (T().Scene)();
        scene.add(s.root);
        shared.setClearColor(0x000000, 0);
        shared.render(scene, camera(s, 1, -0.6, 0.25));
        return shared.domElement.toDataURL("image/png");
      })());
    }
    return thumbs.get(key);
  }

  /** A live, turning preview in a canvas you can drag. Returns {show(item), stop()}. */
  function stage(canvas) {
    let renderer;
    try {
      renderer = new (T().WebGLRenderer)({ canvas, antialias: true, alpha: true });
    } catch (e) {
      return null;
    }
    renderer.setPixelRatio(Math.min(2, window.devicePixelRatio || 1));
    let cur = null, yaw = -0.6, pitch = 0.2, drag = null, idle = 0, running = true, token = 0;
    const scene = new (T().Scene)();
    canvas.addEventListener("pointerdown", (e) => { drag = [e.clientX, e.clientY, yaw, pitch]; canvas.setPointerCapture(e.pointerId); });
    canvas.addEventListener("pointermove", (e) => {
      if (!drag) return;
      yaw = drag[2] - (e.clientX - drag[0]) * 0.01;
      pitch = Math.max(-0.6, Math.min(1.1, drag[3] + (e.clientY - drag[1]) * 0.01));
      idle = 0;
    });
    const up = () => { drag = null; };
    canvas.addEventListener("pointerup", up);
    canvas.addEventListener("pointercancel", up);
    const t0 = performance.now();
    let last = t0;
    function frame(now) {
      if (!running) return;
      if (!canvas.isConnected) { stop(); return; }
      const dt = (now - last) / 1000; last = now;
      const w = canvas.clientWidth, hgt = canvas.clientHeight;
      if (canvas.width !== Math.floor(w * renderer.getPixelRatio()) || canvas.height !== Math.floor(hgt * renderer.getPixelRatio())) {
        renderer.setSize(w, hgt, false);
      }
      if (!drag && (idle += dt) > 1.5) yaw += dt * 0.5;
      if (cur) {
        cur.update((now - t0) / 50);
        renderer.render(scene, camera(cur, w / Math.max(1, hgt), yaw, pitch));
      }
      requestAnimationFrame(frame);
    }
    requestAnimationFrame(frame);
    function stop() { running = false; renderer.dispose(); }
    return {
      async show(it) {
        const my = ++token;
        const s = await build(it);
        if (my !== token) return;
        scene.clear ? scene.clear() : scene.children.slice().forEach((c) => scene.remove(c));
        scene.add(s.root);
        cur = s;
        yaw = -0.6; pitch = 0.2; idle = 0;
      },
      stop,
    };
  }

  /** True when this item can be shown in 3D. */
  function canShow(it) {
    if (it.category === "hat") return !!(HATS[it.render] || (it.render === "model" && it.model));
    if (it.category === "wings") return it.render === "model" ? !!it.model : ["angel", "demon", "dragon", "butterfly", "bat"].includes(it.render);
    if (it.category === "cloak") return !!it.texture;
    return false;
  }

  return { thumb, stage, canShow };
})();
