// PlebMc Hat Maker (panel version). Needs three.js and bridge.js first.
(() => {
  'use strict';
  const N = 24;            // build space: x/z -12..11, y 0..23 (head top is y 8)
  const HEAD = 8;
  const PALETTE = ['#1c1c1c','#ffffff','#b3201e','#e8b923','#3a7bd5','#2d9cdb','#4fe39a','#8b5a2b',
                   '#d9a441','#f28c28','#b085ff','#ff6fae','#5de2e7','#6b6b6b','#3f2a14','#f2f2f2'];
  let palette = PALETTE.slice();
  let color = '#e8b923', tool = 'add', mirror = true;
  const vox = new Map();   // "x,y,z" -> "#rrggbb"
  const hist = [], redoStack = [];
  const $ = id => document.getElementById(id);
  const key = (x,y,z) => x+','+y+','+z;

  // ---------- 3D ----------
  const view = $('view');
  const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, preserveDrawingBuffer: true });
  renderer.setPixelRatio(Math.min(2, window.devicePixelRatio || 1));
  view.appendChild(renderer.domElement);
  const scene = new THREE.Scene();
  const cam = new THREE.PerspectiveCamera(40, 4/3, 0.1, 500);
  scene.add(new THREE.AmbientLight(0xffffff, 0.75));
  const sun = new THREE.DirectionalLight(0xffffff, 0.55); sun.position.set(20, 40, 30); scene.add(sun);
  const sun2 = new THREE.DirectionalLight(0xffffff, 0.25); sun2.position.set(-30, 10, -20); scene.add(sun2);

  // the head: a skin-colored cube with simple face pixels
  const headTex = (() => {
  'use strict';
    const c = document.createElement('canvas'); c.width = c.height = 8; const g = c.getContext('2d');
    g.fillStyle = '#c69a74'; g.fillRect(0,0,8,8); g.fillStyle = '#4a2d18'; g.fillRect(0,0,8,2);
    return c;
  })();
  const faceTex = (() => {
  'use strict';
    const c = document.createElement('canvas'); c.width = c.height = 8; const g = c.getContext('2d');
    g.drawImage(headTex, 0, 0); g.fillStyle = '#fff'; g.fillRect(1,4,2,1); g.fillRect(5,4,2,1);
    g.fillStyle = '#3b5bdb'; g.fillRect(2,4,1,1); g.fillRect(5,4,1,1); g.fillStyle = '#7a4a2e'; g.fillRect(3,6,2,1);
    return c;
  })();
  const mk = c => { const t = new THREE.CanvasTexture(c); t.magFilter = THREE.NearestFilter; t.minFilter = THREE.NearestFilter; return t; };
  // the face is on -z (north), like a Minecraft head
  const headMats = [mk(headTex), mk(headTex), mk(headTex), mk(headTex), mk(headTex), mk(faceTex)]
    .map(t => new THREE.MeshLambertMaterial({ map: t, transparent: true, opacity: 1 }));
  headMats[2] = new THREE.MeshLambertMaterial({ map: mk((() => { const c = document.createElement('canvas'); c.width = c.height = 8; const g = c.getContext('2d'); g.fillStyle = '#4a2d18'; g.fillRect(0,0,8,8); return c; })()), transparent: true });
  const head = new THREE.Mesh(new THREE.BoxGeometry(HEAD, HEAD, HEAD), headMats);
  head.position.set(0, HEAD/2, 0);
  head.userData.head = true;
  scene.add(head);
  // the neck/shoulders hint
  const body = new THREE.Mesh(new THREE.BoxGeometry(8, 6, 4), new THREE.MeshLambertMaterial({ color: 0x2a8f8f, transparent: true, opacity: 0.5 }));
  body.position.set(0, -3, 0); scene.add(body);
  // a floor grid under the build space
  const grid = new THREE.GridHelper(N, N, 0x2f4658, 0x1a2834); grid.position.y = -6.01; scene.add(grid);

  const boxGeo = new THREE.BoxGeometry(1, 1, 1);
  const mats = {};
  const matFor = c => mats[c] || (mats[c] = new THREE.MeshLambertMaterial({ color: new THREE.Color(c) }));
  const group = new THREE.Group(); scene.add(group);
  const meshes = new Map();

  function rebuild() {
    for (const m of meshes.values()) group.remove(m);
    meshes.clear();
    for (const [k, c] of vox) {
      const [x,y,z] = k.split(',').map(Number);
      const m = new THREE.Mesh(boxGeo, matFor(c));
      m.position.set(x + .5, y + .5, z + .5);
      m.userData.k = k;
      group.add(m); meshes.set(k, m);
    }
    $('count').textContent = vox.size + (vox.size === 1 ? ' block' : ' blocks');
    renderCfg();
    keepDraft();
  }

  // orbit
  let yaw = Math.PI - 0.6, pitch = 0.45, dist = 34, spin = false;
  function placeCam() {
    const cy = 6;
    cam.position.set(Math.sin(yaw) * Math.cos(pitch) * dist, cy + Math.sin(pitch) * dist, Math.cos(yaw) * Math.cos(pitch) * dist);
    cam.lookAt(0, cy, 0);
  }
  function resize() {
    const r = view.getBoundingClientRect();
    renderer.setSize(r.width, r.height, false);
    cam.aspect = r.width / Math.max(1, r.height); cam.updateProjectionMatrix();
  }
  new ResizeObserver(resize).observe(view);

  // ---------- editing ----------
  const ray = new THREE.Raycaster(), mouse = new THREE.Vector2();
  function hitAt(ev) {
    const r = renderer.domElement.getBoundingClientRect();
    mouse.set(((ev.clientX - r.left) / r.width) * 2 - 1, -((ev.clientY - r.top) / r.height) * 2 + 1);
    ray.setFromCamera(mouse, cam);
    const objs = [...meshes.values()];
    if ($('ghost').getAttribute('aria-pressed') === 'true') objs.push(head);
    const hits = ray.intersectObjects(objs, false);
    return hits[0] || null;
  }
  const inside = (x,y,z) => x >= -N/2 && x < N/2 && z >= -N/2 && z < N/2 && y >= -4 && y < N;
  const inHead = (x,y,z) => x >= -4 && x < 4 && z >= -4 && z < 4 && y >= 0 && y < 8;
  function snapshot() { hist.push(JSON.stringify([...vox])); if (hist.length > 200) hist.shift(); redoStack.length = 0; }
  function setV(x,y,z,c) {
    const ks = [[x,y,z]];
    if (mirror) ks.push([-x-1,y,z]);
    for (const [a,b,d] of ks) {
      if (!inside(a,b,d) || inHead(a,b,d)) continue;
      if (c) vox.set(key(a,b,d), c); else vox.delete(key(a,b,d));
    }
  }
  function apply(hit) {
    if (!hit) return;
    const isHead = hit.object.userData.head;
    const n = hit.face.normal;
    if (tool === 'pick') { if (!isHead) { selectColor(vox.get(hit.object.userData.k)); } return; }
    if (tool === 'recolor') {
      if (isHead) return;
      const from = vox.get(hit.object.userData.k);
      if (!from || from === color) return;
      snapshot();
      for (const [k, c] of vox) if (c === from) vox.set(k, color);
      rebuild();
      return;
    }
    snapshot();
    if (tool === 'add') {
      let p;
      if (isHead) {
        const q = hit.point.clone().addScaledVector(n, 0.5);
        p = [Math.floor(q.x), Math.floor(q.y), Math.floor(q.z)];
      } else {
        const o = hit.object.position;
        p = [Math.floor(o.x + n.x), Math.floor(o.y + n.y), Math.floor(o.z + n.z)];
      }
      setV(p[0], p[1], p[2], color);
    } else if (!isHead) {
      const o = hit.object.position; const x = Math.floor(o.x), y = Math.floor(o.y), z = Math.floor(o.z);
      setV(x, y, z, tool === 'erase' ? null : color);
    } else { hist.pop(); return; }
    rebuild();
  }

  let drag = null;
  renderer.domElement.addEventListener('contextmenu', e => e.preventDefault());
  renderer.domElement.addEventListener('pointerdown', e => {
    renderer.domElement.setPointerCapture(e.pointerId);
    drag = { x: e.clientX, y: e.clientY, moved: false, btn: e.button, hit: e.button === 0 ? hitAt(e) : null };
  });
  renderer.domElement.addEventListener('pointermove', e => {
    if (!drag) return;
    const dx = e.clientX - drag.x, dy = e.clientY - drag.y;
    if (Math.abs(dx) + Math.abs(dy) > 4) drag.moved = true;
    if (drag.moved && (drag.btn !== 0 || !drag.hit)) {
      yaw -= dx * 0.01; pitch = Math.max(-1.2, Math.min(1.45, pitch + dy * 0.01));
      drag.x = e.clientX; drag.y = e.clientY;
    }
  });
  renderer.domElement.addEventListener('pointerup', e => {
    if (drag && !drag.moved && drag.btn === 0) apply(drag.hit);
    if (drag && !drag.moved && drag.btn === 2) { const t = tool; tool = 'erase'; apply(hitAt(e)); tool = t; }
    drag = null;
  });
  renderer.domElement.addEventListener('wheel', e => { e.preventDefault(); dist = Math.max(14, Math.min(80, dist * (e.deltaY > 0 ? 1.1 : 0.9))); }, { passive: false });

  // ---------- UI ----------
  function drawPalette() {
    const el = $('pal'); el.innerHTML = '';
    for (const c of palette) {
      const b = document.createElement('button');
      b.style.background = c; b.title = c; b.setAttribute('aria-label', 'Color ' + c);
      b.setAttribute('aria-pressed', String(c === color));
      b.onclick = () => selectColor(c);
      el.appendChild(b);
    }
  }
  function selectColor(c) {
    if (!c) return;
    color = c.toLowerCase();
    if (!palette.includes(color)) { palette.push(color); if (palette.length > 24) palette.splice(16, 1); }
    $('custom').value = color;
    drawPalette();
  }
  $('custom').addEventListener('input', e => selectColor(e.target.value));
  function setTool(t) {
    tool = t;
    document.querySelectorAll('#tools button').forEach(b => b.setAttribute('aria-pressed', String(b.dataset.tool === t)));
  }
  document.querySelectorAll('#tools button').forEach(b => b.onclick = () => setTool(b.dataset.tool));
  const toggle = (id, on) => { $(id).setAttribute('aria-pressed', String(on)); return on; };
  $('mirror').onclick = () => { mirror = toggle('mirror', !mirror); };
  $('spin').onclick = () => { spin = toggle('spin', !spin); };
  $('ghost').onclick = () => { const on = toggle('ghost', $('ghost').getAttribute('aria-pressed') !== 'true'); head.visible = on; body.visible = on; };
  $('viewFront').onclick = () => { yaw = Math.PI; pitch = 0.15; };
  $('viewSide').onclick = () => { yaw = Math.PI / 2; pitch = 0.15; };
  $('viewTop').onclick = () => { yaw = Math.PI; pitch = 1.4; };
  function undo() { if (!hist.length) return; redoStack.push(JSON.stringify([...vox])); load(hist.pop()); }
  function redo() { if (!redoStack.length) return; hist.push(JSON.stringify([...vox])); load(redoStack.pop()); }
  function load(s) { vox.clear(); for (const [k,c] of JSON.parse(s)) vox.set(k, c); rebuild(); }
  $('undo').onclick = undo; $('redo').onclick = redo;
  $('clear').onclick = () => { snapshot(); vox.clear(); rebuild(); };
  window.addEventListener('keydown', e => {
    if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA' || e.target.tagName === 'SELECT') return;
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'z') { e.preventDefault(); e.shiftKey ? redo() : undo(); return; }
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'y') { e.preventDefault(); redo(); return; }
    const t = { '1': 'add', '2': 'paint', '3': 'erase', '4': 'pick', '5': 'recolor' }[e.key];
    if (t) setTool(t);
    if (e.key.toLowerCase() === 'm') $('mirror').click();
  });

  // ---------- templates ----------
  function fill(x0,y0,z0,x1,y1,z1,c) { for (let x=x0;x<=x1;x++) for (let y=y0;y<=y1;y++) for (let z=z0;z<=z1;z++) if (!inHead(x,y,z)) vox.set(key(x,y,z), c); }
  function ring(y, r, c, t = 1) { for (let x=-r;x<r;x++) for (let z=-r;z<r;z++) { const edge = x < -r+t || x >= r-t || z < -r+t || z >= r-t; if (edge && !inHead(x,y,z)) vox.set(key(x,y,z), c); } }
  const T = {
    tophat() { fill(-6,8,-6,5,8,5,'#1c1c1c'); fill(-4,9,-4,3,9,3,'#b3201e'); fill(-4,10,-4,3,14,3,'#1c1c1c'); },
    crown() { ring(8,5,'#e8b923'); ring(9,5,'#e8b923'); for (const [x,z] of [[-5,-5],[-1,-5],[0,-5],[4,-5],[-5,4],[-1,4],[0,4],[4,4],[-5,-1],[-5,0],[4,-1],[4,0]]) vox.set(key(x,10,z),'#e8b923'); vox.set(key(-1,9,-5),'#b3201e'); vox.set(key(0,9,-5),'#b3201e'); vox.set(key(-5,9,0),'#3a7bd5'); vox.set(key(4,9,-1),'#3a7bd5'); },
    cap() { fill(-4,8,-4,3,9,3,'#2d9cdb'); fill(-4,8,-8,3,8,-5,'#2d9cdb'); fill(-1,10,-1,0,10,0,'#ffffff'); },
    beanie() { fill(-4,8,-4,3,10,3,'#3a7bd5'); ring(8,5,'#2a5aa0'); fill(-1,11,-1,0,12,0,'#f2f2f2'); },
    horns() { for (const s of [-1,1]) { const x = s < 0 ? -4 : 3; fill(x,8,0,x,9,0,'#b3201e'); fill(x+s,10,0,x+s,11,0,'#b3201e'); fill(x+s*2,12,0,x+s*2,12,0,'#7a1410'); } },
    halo() { ring(12,5,'#ffe66b'); },
    witch() { fill(-7,8,-7,6,8,6,'#2b1a3d'); fill(-4,9,-4,3,10,3,'#2b1a3d'); ring(9,4,'#7a4ac9'); fill(-3,11,-3,2,12,2,'#2b1a3d'); fill(-2,13,-2,1,14,1,'#2b1a3d'); fill(-1,15,-1,0,16,0,'#2b1a3d'); fill(0,17,0,1,17,1,'#2b1a3d'); },
    straw() { fill(-7,8,-7,6,8,6,'#d9a441'); fill(-4,9,-4,3,11,3,'#e8c066'); ring(9,4,'#b3201e'); },
    headphones() { fill(-5,8,-1,4,8,0,'#1c1c1c'); for (const x of [-5,4]) { fill(x,2,-1,x,7,0,'#1c1c1c'); } fill(-6,2,-2,-5,5,1,'#3a7bd5'); fill(4,2,-2,5,5,1,'#3a7bd5'); },
    viking() { fill(-5,8,-5,4,9,4,'#8a8f98'); ring(8,5,'#6b4a2b'); fill(-1,8,-6,0,9,-5,'#8a8f98'); for (const s of [-1,1]) { const x = s < 0 ? -6 : 5; fill(x,9,0,x,10,0,'#f2f2e6'); fill(x+s,11,0,x+s,12,0,'#f2f2e6'); fill(x+s,13,0,x+s,13,0,'#d9d2b8'); } }
  };
  document.querySelectorAll('#templates button').forEach(b => b.onclick = () => { snapshot(); vox.clear(); T[b.dataset.t](); rebuild(); });

  // ---------- export ----------
  function slug(s) { return (s || 'my_hat').toLowerCase().replace(/[^a-z0-9_]+/g, '_').replace(/^_+|_+$/g, '') || 'my_hat'; }
  function renderCfg() {
    const id = slug($('hid').value), name = ($('hname').value || 'My Hat').replace(/'/g, "''");
    $('cfg').textContent = `  ${id}: {name: '${name}', category: hat, model: 'hats/${id}.bbmodel', price: 2000, description: 'Made with the Hat Maker'}`;
  }
  $('hid').addEventListener('input', () => { renderCfg(); keepDraft(); }); $('hname').addEventListener('input', () => { renderCfg(); keepDraft(); });

  /** Merges runs of same-colored blocks along x, then y, into boxes (fewer faces). */
  function boxes() {
    const left = new Map(vox), out = [];
    const sorted = [...left.keys()].map(k => k.split(',').map(Number)).sort((a,b) => a[1]-b[1] || a[2]-b[2] || a[0]-b[0]);
    for (const [x,y,z] of sorted) {
      const c = left.get(key(x,y,z)); if (!c) continue;
      let x1 = x; while (left.get(key(x1+1,y,z)) === c) x1++;
      let z1 = z;
      outer: while (true) { for (let xx = x; xx <= x1; xx++) if (left.get(key(xx,y,z1+1)) !== c) break outer; z1++; }
      for (let xx = x; xx <= x1; xx++) for (let zz = z; zz <= z1; zz++) left.delete(key(xx,y,zz));
      out.push({ from: [x, y, z], to: [x1+1, y+1, z1+1], c });
    }
    return out;
  }
  function uuid() { return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, ch => { const r = Math.random()*16|0; return (ch === 'x' ? r : (r&3|8)).toString(16); }); }
  function bbmodel() {
    const bs = boxes();
    const colors = [...new Set(bs.map(b => b.c))];
    const size = 16; // a 16x16 palette texture: one pixel per color
    const cv = document.createElement('canvas'); cv.width = cv.height = size; const g = cv.getContext('2d');
    colors.forEach((c, i) => { g.fillStyle = c; g.fillRect(i % size, Math.floor(i / size), 1, 1); });
    const png = cv.toDataURL('image/png');
    const els = bs.map((b, i) => {
      const ci = colors.indexOf(b.c), u = ci % size, v = Math.floor(ci / size);
      // a hair inside the pixel, so no neighboring color bleeds in
      const uv = [u + 0.25, v + 0.25, u + 0.75, v + 0.75];
      const face = { uv, texture: 0 };
      return { name: 'block' + i, box_uv: false, rescale: false, locked: false, render_order: 'default', allow_mirror_modeling: true,
        from: b.from, to: b.to, autouv: 0, color: 0, origin: [0, 0, 0],
        faces: { north: face, east: face, south: face, west: face, up: face, down: face }, type: 'cube', uuid: uuid() };
    });
    return { meta: { format_version: '4.5', model_format: 'free', box_uv: false }, name: slug($('hid').value),
      model_identifier: '', visible_box: [1, 1, 0], resolution: { width: size, height: size }, elements: els,
      outliner: els.map(e => e.uuid),
      textures: [{ path: '', name: slug($('hid').value) + '.png', folder: '', namespace: '', id: '0', particle: false,
        render_mode: 'default', visible: true, mode: 'bitmap', saved: false, uuid: uuid(), relative_path: '',
        width: size, height: size, uv_width: size, uv_height: size, source: png }] };
  }
  function status(msg, bad) { const s = $('status'); s.textContent = msg; s.className = 'status' + (bad ? ' bad' : ''); }
  async function download(name, text, type) {
    const blob = text instanceof Blob ? text : new Blob([text], { type: type || 'application/json' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = name; document.body.appendChild(a); a.click(); a.remove();
    setTimeout(() => URL.revokeObjectURL(a.href), 2000);
    return true;
  }
  $('dl').onclick = async () => {
    if (!vox.size) { status('Place some blocks first.', true); return; }
    const m = bbmodel();
    const faces = m.elements.length * 6;
    if (faces > 3000) { status('That has ' + faces + ' faces. A hat can have at most 3000, so remove some blocks.', true); return; }
    if (await download(slug($('hid').value) + '.bbmodel', JSON.stringify(m))) {
      status('Saved ' + slug($('hid').value) + '.bbmodel (' + m.elements.length + ' cubes).');
    }
  };
  $('copy').onclick = async () => {
    try { await navigator.clipboard.writeText($('cfg').textContent); status('Config line copied.'); }
    catch (e) { status('Copy failed. Select the line below and copy it.', true); }
  };
  $('save').onclick = () => download(slug($('hid').value) + '.hat.json', JSON.stringify({ v: 1, id: $('hid').value, name: $('hname').value, vox: [...vox] }));
  $('load').onclick = () => {
    const i = document.createElement('input'); i.type = 'file'; i.accept = '.json,application/json';
    i.onchange = async () => {
      try {
        const d = JSON.parse(await i.files[0].text());
        snapshot(); loadProject(d); status('Opened ' + i.files[0].name + '.');
      } catch (e) { status('That isn’t a Hat Maker project.', true); }
    };
    i.click();
  };

  // ---------- move and shape ----------
  function moveAll(dx, dy, dz) {
    const next = new Map();
    for (const [k, c] of vox) {
      const [x, y, z] = k.split(',').map(Number);
      const a = x + dx, b = y + dy, d = z + dz;
      if (!inside(a, b, d)) { status('That would push blocks out of the build space.', true); return; }
      if (inHead(a, b, d)) { status('That would push blocks into the head.', true); return; }
      next.set(key(a, b, d), c);
    }
    snapshot(); vox.clear(); for (const [k, c] of next) vox.set(k, c); rebuild(); status('');
  }
  document.querySelectorAll('#move button').forEach(b => b.onclick = () => { const [x, y, z] = b.dataset.m.split(',').map(Number); moveAll(x, y, z); });
  $('flip').onclick = () => {
    const next = new Map();
    for (const [k, c] of vox) { const [x, y, z] = k.split(',').map(Number); next.set(key(-x - 1, y, -z - 1), c); }
    snapshot(); vox.clear(); for (const [k, c] of next) vox.set(k, c); rebuild();
  };
  $('hollow').onclick = () => {
    const solid = (x, y, z) => vox.has(key(x, y, z)) || inHead(x, y, z);
    const drop = [];
    for (const k of vox.keys()) {
      const [x, y, z] = k.split(',').map(Number);
      if (solid(x+1,y,z) && solid(x-1,y,z) && solid(x,y+1,z) && solid(x,y-1,z) && solid(x,y,z+1) && solid(x,y,z-1)) drop.push(k);
    }
    if (!drop.length) { status('Nothing hidden to remove.'); return; }
    snapshot(); for (const k of drop) vox.delete(k); rebuild();
    status('Removed ' + drop.length + ' hidden block' + (drop.length === 1 ? '' : 's') + '.');
  };

  // ---------- picture, draft, account ----------
  function snapshotPng(w, hgt) {
    renderer.render(scene, cam);
    const c = document.createElement('canvas'); c.width = w; c.height = hgt;
    const src = renderer.domElement, k = Math.min(w / src.width, hgt / src.height) * 1.0;
    const g = c.getContext('2d');
    g.drawImage(src, (w - src.width * k) / 2, (hgt - src.height * k) / 2, src.width * k, src.height * k);
    return c;
  }
  $('png').onclick = () => {
    if (!vox.size) { status('Place some blocks first.', true); return; }
    snapshotPng(1200, 900).toBlob(b => { if (b) download(slug($('hid').value) + '.png', b); }, 'image/png');
  };
  const DRAFT = 'plebmc.hatmaker.draft';
  let draftTimer = 0;
  function keepDraft() {
    clearTimeout(draftTimer);
    draftTimer = setTimeout(() => {
      try { localStorage.setItem(DRAFT, JSON.stringify({ v: 1, id: $('hid').value, name: $('hname').value, vox: [...vox] })); } catch (e) { /* private window */ }
    }, 400);
  }
  function loadProject(d) {
    if (!d || !Array.isArray(d.vox)) throw new Error('not a project');
    vox.clear();
    for (const [k, c] of d.vox) if (/^-?\d+,-?\d+,-?\d+$/.test(k) && /^#[0-9a-f]{6}$/i.test(c)) vox.set(k, c.toLowerCase());
    if (typeof d.id === 'string') $('hid').value = d.id;
    if (typeof d.name === 'string') $('hname').value = d.name;
    hist.length = 0; redoStack.length = 0;
    rebuild();
  }
  window.PlebTool.mount($('account'), {
    kind: 'hat',
    status,
    get() {
      if (!vox.size) throw new Error('Place some blocks first.');
      const thumb = snapshotPng(160, 120).toDataURL('image/png');
      return { name: $('hname').value || 'My Hat', itemId: slug($('hid').value),
        data: JSON.stringify({ v: 1, id: $('hid').value, name: $('hname').value, vox: [...vox] }), extra: {}, thumb };
    },
    set(d) { loadProject(JSON.parse(d.data)); if (!d.mine) $('hname').value = d.name + ' (remix)'; renderCfg(); },
    publishFile() {
      if (!vox.size) throw new Error('Place some blocks first.');
      const m = bbmodel();
      if (m.elements.length * 6 > 3000) throw new Error('That has too many faces for a hat (3000 at most). Try Hollow out.');
      return { file: JSON.stringify(m) };
    },
  });

  // ---------- go ----------
  drawPalette();
  let restored = false;
  if (!new URLSearchParams(location.search).get('design')) {
    try { const d = JSON.parse(localStorage.getItem(DRAFT) || 'null'); if (d && d.vox && d.vox.length) { loadProject(d); restored = true; } } catch (e) { /* no draft */ }
  }
  if (!restored) T.tophat();
  rebuild();
  resize();
  (function loop() {
    if (spin) yaw += 0.008;
    placeCam();
    renderer.render(scene, cam);
    requestAnimationFrame(loop);
  })();
})();
