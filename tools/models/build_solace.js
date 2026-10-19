/*
 * Builds Solace's model inside Blockbench (runs in the Blockbench page, see bb_run.mjs).
 * Units are Blockbench pixels: 16 = one block. The model faces north (-z), its right side is +x.
 *
 * A headless samurai: no head and no eyes in either form; a tall laced neck guard stands round an empty hollow.
 * Its armour (chest plates, shoulder guards, armoured skirt, sleeves and shin guards, a katana at its hip) is shared:
 * the negative is the same armour with the colours flipped.
 * Group name prefixes decide which form shows a part:
 *   n_  only in the normal form (tattered cloak, claws)
 *   x_  only in the negative (shoulder flames, long robe, sword blades for hands)
 *   anything else is shared.
 * The mod animates groups by name (body, arm_r, forearm_r, hand_r, leg_l, shin_l, cloak, wisp..., flame...).
 *
 * Everything is built from triangles (Blockbench meshes) and flat cards, so the mod's loader stays simple.
 * Texture regions (256 x 256 sheet) are painted by tools/shade_textures.py.
 */
function buildSolace(textureUrl) {
  newProject(Formats.free);
  Project.name = 'solace';
  Project.texture_width = 256;
  Project.texture_height = 256;
  const tex = new Texture({ name: 'solace_shade.png' }).fromDataURL(textureUrl).add(false);
  tex.uv_width = 256;
  tex.uv_height = 256;
  const T = tex.uuid;

  // ---------- small vector helpers ----------
  const add = (a, b) => [a[0] + b[0], a[1] + b[1], a[2] + b[2]];
  const sub = (a, b) => [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
  const mul = (a, s) => [a[0] * s, a[1] * s, a[2] * s];
  const cross3 = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
  const norm = a => { const l = Math.hypot(a[0], a[1], a[2]) || 1; return mul(a, 1 / l); };
  let seed = 3;
  const rnd = () => ((seed = (seed * 16807) % 2147483647) / 2147483647);

  function group(name, origin, rotation, parent) {
    const g = new Group({ name, origin, rotation: rotation || [0, 0, 0] }).init();
    if (parent) g.addTo(parent);
    return g;
  }

  /** A mesh made of triangles. tris: [[p0, p1, p2, uv0, uv1, uv2], ...] */
  function mesh(name, parent, tris, origin) {
    const m = new Mesh({ name, origin: origin || [0, 0, 0], vertices: {} });
    for (const [a, b, c, ua, ub, uc] of tris) {
      const k = m.addVertices(a, b, c);
      m.addFaces(new MeshFace(m, { vertices: k, uv: { [k[0]]: ua, [k[1]]: ub, [k[2]]: uc }, texture: T }));
    }
    m.init();
    m.addTo(parent);
    return m;
  }

  /** A quad as two triangles (corners in order round the edge). */
  function quad(tris, p, uv) {
    tris.push([p[0], p[1], p[2], uv[0], uv[1], uv[2]]);
    tris.push([p[0], p[2], p[3], uv[0], uv[2], uv[3]]);
  }

  /**
   * A lofted tube through rings. ring: {c: centre, x: side axis, z: depth axis, rx, rz}.
   * uv: [u, v, w, h] texture region, wrapped round the tube and stretched along it.
   */
  function loft(name, parent, rings, uv, sides = 8, opts = {}) {
    const tris = [];
    const pt = (r, i) => {
      const a = (i / sides) * Math.PI * 2 + (opts.twist || 0);
      const p = add(r.c, add(mul(r.x, Math.cos(a) * r.rx), mul(r.z, Math.sin(a) * r.rz)));
      // slope: the ring droops towards its sides (sloping shoulders)
      return r.slope ? [p[0], p[1] - r.slope * Math.abs(Math.cos(a)) * r.rx, p[2]] : p;
    };
    const U = i => uv[0] + uv[2] * i / sides;
    const V = j => uv[1] + uv[3] * j / (rings.length - 1);
    for (let j = 0; j < rings.length - 1; j++) {
      for (let i = 0; i < sides; i++) {
        quad(tris, [pt(rings[j], i), pt(rings[j], i + 1), pt(rings[j + 1], i + 1), pt(rings[j + 1], i)],
          [[U(i), V(j)], [U(i + 1), V(j)], [U(i + 1), V(j + 1)], [U(i), V(j + 1)]]);
      }
    }
    for (const end of opts.caps || []) {
      const r = rings[end === 'start' ? 0 : rings.length - 1];
      const cu = uv[0] + uv[2] / 2, cv = uv[1] + uv[3] / 2;
      for (let i = 0; i < sides; i++) {
        tris.push([r.capCentre || r.c, pt(r, i), pt(r, i + 1), [cu, cv], [U(i), uv[1]], [U(i + 1), uv[1]]]);
      }
    }
    return mesh(name, parent, tris);
  }

  /** A tapering tube from a to b (radius ra to rb), round or flattened. */
  function tube(name, parent, a, b, ra, rb, uv, sides = 6, flat = 1) {
    const d = norm(sub(b, a));
    const ref = Math.abs(d[1]) > 0.9 ? [1, 0, 0] : [0, 1, 0];
    const x = norm(cross3(ref, d)), z = norm(cross3(d, x));
    const rings = [{ c: a, x, z, rx: ra, rz: ra * flat }, { c: b, x, z, rx: rb, rz: rb * flat }];
    return loft(name, parent, rings, uv, sides, { caps: rb > 0.05 ? ['start', 'end'] : ['start'] });
  }

  /** A bent claw or spike: a pointed tube with a joint in the middle. */
  function claw(name, parent, a, mid, tip, r, uv) {
    const tris = [];
    const seg = (p, q, ra, rb, v0, v1) => {
      const d = norm(sub(q, p));
      const ref = Math.abs(d[1]) > 0.9 ? [1, 0, 0] : [0, 1, 0];
      const x = norm(cross3(ref, d)), z = norm(cross3(d, x));
      for (let i = 0; i < 4; i++) {
        const a0 = i / 4 * Math.PI * 2, a1 = (i + 1) / 4 * Math.PI * 2;
        const o = (ang, rr) => add(mul(x, Math.cos(ang) * rr), mul(z, Math.sin(ang) * rr));
        const u0 = uv[0] + uv[2] * i / 4, u1 = uv[0] + uv[2] * (i + 1) / 4;
        quad(tris, [add(p, o(a0, ra)), add(p, o(a1, ra)), add(q, o(a1, rb)), add(q, o(a0, rb))],
          [[u0, uv[1] + uv[3] * v0], [u1, uv[1] + uv[3] * v0], [u1, uv[1] + uv[3] * v1], [u0, uv[1] + uv[3] * v1]]);
      }
    };
    seg(a, mid, r, r * 0.6, 0, 0.55);
    seg(mid, tip, r * 0.6, 0.01, 0.55, 1);
    return mesh(name, parent, tris);
  }

  /** A flat card growing up from its base (or hanging from its top), facing north. */
  function card(name, parent, at, w, h, uv, opts = {}) {
    const [x, y, z] = at;
    const from = [x - w / 2, opts.hang ? y - h : y, z];
    const to = [x + w / 2, opts.hang ? y : y + h, z];
    const none = { uv: [0, 0, 0, 0], texture: null };
    const faces = {
      north: { uv: [uv[0], uv[1], uv[0] + uv[2], uv[1] + uv[3]], texture: T },
      south: none, east: none, west: none, up: none, down: none,
    };
    const c = new Cube({ name, from, to, origin: [x, y, z], rotation: opts.rotation || [0, 0, 0], faces }).init();
    c.addTo(parent);
    return c;
  }

  /** Two cards crossed at right angles, so a wisp or flame looks full from every side. */
  function cross(name, parent, at, w, h, uv, uv2) {
    card(name + '_a', parent, at, w, h, uv);
    card(name + '_b', parent, at, w, h, uv2 || uv, { rotation: [0, 90, 0] });
  }

  /**
   * A hanging cloth sheet on an arc round the body. From angle a0 to a1 (0 = straight behind, 90 = its right),
   * starting at height top and falling len pixels, flaring outwards towards the hem, with soft folds.
   */
  function drape(name, parent, o) {
    const tris = [];
    const P = (i, j) => {
      const t = j / o.rows, a = (o.a0 + (o.a1 - o.a0) * i / o.cols) * Math.PI / 180;
      const fold = 1 + o.fold * Math.sin(a * o.folds + t * 1.5) * t;
      const rx = (o.rx + o.flareX * t * t) * fold, rz = (o.rz + o.flareZ * t * t) * fold;
      return [Math.sin(a) * rx, o.top - o.len * t, Math.cos(a) * rz + (o.dz || 0) + (o.back || 0) * t];
    };
    const UV = (i, j) => [o.uv[0] + o.uv[2] * i / o.cols, o.uv[1] + o.uv[3] * j / o.rows];
    for (let j = 0; j < o.rows; j++) {
      for (let i = 0; i < o.cols; i++) {
        quad(tris, [P(i, j), P(i + 1, j), P(i + 1, j + 1), P(i, j + 1)], [UV(i, j), UV(i + 1, j), UV(i + 1, j + 1), UV(i, j + 1)]);
      }
    }
    return mesh(name, parent, tris);
  }

  // Texture regions (see shade_textures.py)
  const SKIN = [0, 0, 64, 64];
  const CLOAK = [0, 64, 128, 128];
  const SPIKE = [128, 64, 32, 64];
  const BONE = [160, 64, 32, 64];
  const BAND = [192, 64, 64, 32];
  const ROBE = [192, 96, 64, 32];
  const FLAME = i => [(i % 4) * 32, 192, 32, 64];
  const ARMOR = (rows, w = 64) => [64, 0, w, 8 * rows];  // lamellar plates, 8 texels per row (narrower w = fewer cords)
  const SAYA = [128, 0, 64, 16], TSUKA = [128, 16, 64, 16], METAL = [128, 16, 4, 16];
  const X = [1, 0, 0], Z = [0, 0, 1];
  const ring = (y, rx, rz, cx = 0, cz = 0) => ({ c: [cx, y, cz], x: X, z: Z, rx, rz });
  /** A ring tilted round the z axis (for bands wrapped at an angle). */
  const tilted = (y, rx, rz, deg) => {
    const r = deg * Math.PI / 180;
    return { c: [0, y, 0], x: [Math.cos(r), Math.sin(r), 0], z: Z, rx, rz };
  };

  const root = group('root', [0, 0, 0]);

  /**
   * The coat's high collar: stands up round the back and sides of where a neck would be, open at the front and
   * empty inside. Tallest at the back, sloping down towards the front like lapels. Built with real thickness.
   */
  function collar(name, parent, o) {
    const tris = [];
    const n = o.cols, half = o.spread;
    const at = (i, j, inner) => {
      const a = (-half + 2 * half * i / n) * Math.PI / 180;
      const f = Math.abs(a) / (half * Math.PI / 180);
      const h = o.height * (1 - o.drop * f * f);         // lower towards the front opening
      const t = j;                                        // 0 = bottom, 1 = top
      const grow = inner ? -o.thick : 0;
      const rx = o.rx + o.flare * t + grow, rz = o.rz + o.flare * 0.8 * t + grow;
      return [Math.sin(a) * rx, o.y + h * t, Math.cos(a) * rz + o.dz + o.back * t];
    };
    const U = i => o.uv[0] + o.uv[2] * i / n;
    // flip: the texture's bottom edge (a torn hem) goes along the collar's top instead
    const vTop = o.flip ? o.uv[1] + o.uv[3] : o.uv[1], vBottom = o.flip ? o.uv[1] : o.uv[1] + o.uv[3];
    for (let i = 0; i < n; i++) {
      for (const inner of [false, true]) {
        quad(tris, [at(i, 0, inner), at(i + 1, 0, inner), at(i + 1, 1, inner), at(i, 1, inner)],
          [[U(i), vBottom], [U(i + 1), vBottom], [U(i + 1), vTop], [U(i), vTop]]);
      }
      // the folded top edge
      if (!o.flip) quad(tris, [at(i, 1, false), at(i + 1, 1, false), at(i + 1, 1, true), at(i, 1, true)],
        [[U(i), o.uv[1]], [U(i + 1), o.uv[1]], [U(i + 1), o.uv[1] + 2], [U(i), o.uv[1] + 2]]);
    }
    for (const i of [0, n]) { // the two front edges
      quad(tris, [at(i, 0, false), at(i, 1, false), at(i, 1, true), at(i, 0, true)],
        [[o.uv[0], o.uv[1]], [o.uv[0] + 2, o.uv[1]], [o.uv[0] + 2, o.uv[1] + o.uv[3]], [o.uv[0], o.uv[1] + o.uv[3]]]);
    }
    return mesh(name, parent, tris);
  }

  /**
   * A long sword blade growing straight out of the end of the forearm (negative), so it reads as part of the arm.
   * Thick at the back edge, sharp at the front, with a katana's gentle curve and a pointed tip.
   */
  function scythe(name, parent, x, rootY, rootZ, len) {
    const tris = [];
    const P0 = [rootY, rootZ], P1 = [rootY - len * 0.55, rootZ + 0.6], P2 = [rootY - len, rootZ - len * 0.3];
    const N = 14;
    const pts = [];
    for (let k = 0; k <= N; k++) {
      const t = k / N;
      const y = (1 - t) ** 2 * P0[0] + 2 * (1 - t) * t * P1[0] + t * t * P2[0];
      const z = (1 - t) ** 2 * P0[1] + 2 * (1 - t) * t * P1[1] + t * t * P2[1];
      const dy = 2 * (1 - t) * (P1[0] - P0[0]) + 2 * t * (P2[0] - P1[0]);
      const dz = 2 * (1 - t) * (P1[1] - P0[1]) + 2 * t * (P2[1] - P1[1]);
      const l = Math.hypot(dy, dz) || 1;
      const ny = -dz / l, nz = dy / l;                    // across the blade, towards its sharp (front) edge
      const w = 1.5 * (t < 0.82 ? 1 - 0.15 * t : (1 - t) / 0.18 * 0.88);
      const th = 0.38 * (t < 0.82 ? 1 : (1 - t) / 0.18) + 0.03;
      // back edge (thick) and front edge (sharp), each as +x and -x points
      const back = [y - ny * w * 0.35, z - nz * w * 0.35], front = [y + ny * w * 0.65, z + nz * w * 0.65];
      pts.push({ bp: [x + th, back[0], back[1]], bm: [x - th, back[0], back[1]], fp: [x + 0.06, front[0], front[1]], fm: [x - 0.06, front[0], front[1]] });
    }
    const u = (k) => BONE[1] + BONE[3] * k / N;
    for (let k = 0; k < N; k++) {
      const a = pts[k], b = pts[k + 1];
      quad(tris, [a.bp, b.bp, b.fp, a.fp], [[BONE[0], u(k)], [BONE[0], u(k + 1)], [BONE[0] + 24, u(k + 1)], [BONE[0] + 24, u(k)]]);
      quad(tris, [a.bm, a.fm, b.fm, b.bm], [[BONE[0], u(k)], [BONE[0] + 24, u(k)], [BONE[0] + 24, u(k + 1)], [BONE[0], u(k + 1)]]);
      quad(tris, [a.bp, a.bm, b.bm, b.bp], [[BONE[0] + 26, u(k)], [BONE[0] + 30, u(k)], [BONE[0] + 30, u(k + 1)], [BONE[0] + 26, u(k + 1)]]);
      quad(tris, [a.fp, b.fp, b.fm, a.fm], [[BONE[0] + 30, u(k)], [BONE[0] + 30, u(k + 1)], [BONE[0] + 32, u(k + 1)], [BONE[0] + 32, u(k)]]);
    }
    return mesh(name, parent, tris);
  }

  /**
   * A curved armour plate: part of a cylinder round (cx, cz) from angle a0 to a1 (0 = behind, 90 = its right side),
   * from yTop down to yBot, widening from rTop to rBot (so it flares out like a skirt), with real thickness.
   * sx/sz squash the circle into an oval.
   */
  function plate(name, parent, o) {
    const tris = [];
    const n = o.cols || 6, sx = o.sx || 1, sz = o.sz || 1;
    const P = (i, j, inner) => {
      const a = (o.a0 + (o.a1 - o.a0) * i / n) * Math.PI / 180;
      const r = o.rTop + (o.rBot - o.rTop) * j - (inner ? o.thick : 0);
      return [o.cx + Math.sin(a) * r * sx, o.yTop + (o.yBot - o.yTop) * j, o.cz + Math.cos(a) * r * sz];
    };
    const U = i => o.uv[0] + o.uv[2] * i / n, V = j => o.uv[1] + o.uv[3] * j;
    for (let i = 0; i < n; i++) {
      quad(tris, [P(i, 0, false), P(i + 1, 0, false), P(i + 1, 1, false), P(i, 1, false)], [[U(i), V(0)], [U(i + 1), V(0)], [U(i + 1), V(1)], [U(i), V(1)]]);
      quad(tris, [P(i, 0, true), P(i, 1, true), P(i + 1, 1, true), P(i + 1, 0, true)], [[U(i), V(0)], [U(i), V(1)], [U(i + 1), V(1)], [U(i + 1), V(0)]]);
      quad(tris, [P(i, 0, true), P(i + 1, 0, true), P(i + 1, 0, false), P(i, 0, false)], [[U(i), V(0)], [U(i + 1), V(0)], [U(i + 1), V(0) + 1], [U(i), V(0) + 1]]);
      quad(tris, [P(i, 1, false), P(i + 1, 1, false), P(i + 1, 1, true), P(i, 1, true)], [[U(i), V(1) - 1], [U(i + 1), V(1) - 1], [U(i + 1), V(1)], [U(i), V(1)]]);
    }
    for (const i of [0, n]) {
      quad(tris, [P(i, 0, false), P(i, 1, false), P(i, 1, true), P(i, 0, true)], [[U(0), V(0)], [U(0), V(1)], [U(0) + 1, V(1)], [U(0) + 1, V(0)]]);
    }
    return mesh(name, parent, tris);
  }

  // ---------- torso: hunched, a broad chest over a narrower waist ----------
  const body = group('body', [0, 15.5, 0], [-6, 0, 0], root);
  // The top dips inward inside the collar: an empty hollow where a head should be.
  const top = { c: [0, 31.2, 0.2], x: X, z: Z, rx: 5.4, rz: 2.3, slope: 0.12, capCentre: [0, 29.8, 0.3] };
  loft('torso', body, [
    ring(14.2, 3.0, 2.0), ring(16.5, 3.2, 2.15), ring(19, 2.6, 1.85), ring(21.5, 3.2, 2.15),
    ring(24.5, 4.2, 2.55, 0, 0.1), ring(27.5, 4.7, 2.65, 0, 0.2), ring(29.6, 5.3, 2.5, 0, 0.2), top,
  ], SKIN, 12, { caps: ['start', 'end'] });

  // ---------- samurai armour, the same in both forms (the sheet makes it black with red cords, or white with black) ----------
  const armor = group('armor', [0, 24, 0], [0, 0, 0], body);
  // Chest armour (do): rows of laced plates from the waist up to the shoulders.
  loft('do', armor, [ring(30.5, 5.6, 2.8, 0, 0.2), ring(28, 5.0, 2.95, 0, 0.2), ring(25, 4.55, 2.85, 0, 0.1), ring(22.5, 3.65, 2.45), ring(21.0, 3.35, 2.35)],
    ARMOR(8), 12);
  // A thick sash (obi) round the waist.
  loft('obi', armor, [ring(19.8, 2.9, 2.1), ring(18.2, 3.25, 2.3), ring(16.8, 3.45, 2.4)], BAND, 12);
  // Armoured skirt (kusazuri): broad plates hanging round the hips, flaring out.
  [180, -125, 125, -70, 70].forEach((a, i) => {
    plate('kusazuri' + i, armor, { cx: 0, cz: 0.1, a0: a - 27, a1: a + 27, yTop: 17.6, yBot: 10.2, rTop: 3.5, rBot: 4.6, sz: 0.72, thick: 0.4, uv: ARMOR(5, 24), cols: 4 });
  });
  // The neck guard (shikoro) as the coat's collar: three flaring tiers of laced plates round an empty hollow.
  const coll = group('collar', [0, 30.5, 0.4], [0, 0, 0], body);
  [[30.0, 3.9, 2.5, 1.3], [31.3, 4.75, 3.0, 1.4], [32.6, 5.65, 3.6, 1.5]].forEach(([y, rx, rz, flare], k) => {
    collar('tier' + k, coll, { y, height: 1.8, drop: 0.7, rx, rz, flare, thick: 0.45, dz: 0.4 + k * 0.25, back: 0.5, spread: 128 - k * 6, cols: 16, uv: ARMOR(1, 48) });
  });

  // Two broad, swept-back horns of black (or white) on each shoulder.
  for (const s of [1, -1]) {
    const g = group(s > 0 ? 'spikes_r' : 'spikes_l', [s * 4.6, 30.6, 0.3], [0, 0, 0], body);
    [[4.4, 30.8, 1.0, 2.0, 4.2, 3.2, 1.2], [5.4, 30.0, 0.0, 3.4, 2.4, 1.6, 1.05]].forEach(([x, y, z, dx, dy, dz, r], k) => {
      const a = [s * x, y, z], tip = [s * (x + dx), y + dy, z + dz];
      claw('spike' + k, g, a, add(a, mul(sub(tip, a), 0.5)), tip, r, SPIKE);
    });
  }

  // A sheathed katana at its left hip: hilt forward, scabbard sweeping back.
  const sword = group('katana', [-3.7, 16.4, 0], [0, 0, 0], body);
  tube('saya', sword, [-3.7, 16.3, -2.2], [-4.0, 13.2, 9.5], 0.55, 0.5, SAYA, 6, 1.5);
  tube('tsuba', sword, [-3.68, 16.36, -2.2], [-3.66, 16.45, -2.75], 1.15, 1.15, METAL, 8);
  tube('tsuka', sword, [-3.66, 16.45, -2.7], [-3.4, 17.7, -7.6], 0.48, 0.44, TSUKA, 6, 1.3);

  // White flames licking up from the outer shoulders (negative), leaning out.
  for (const s of [1, -1]) {
    const g = group(s > 0 ? 'x_flame_r' : 'x_flame_l', [s * 4.4, 30.6, 0.3], [0, 0, -s * 18], body);
    cross('f1', g, [s * 4.4, 30.6, 0.3], 7, 12, FLAME(s > 0 ? 0 : 1), FLAME(2));
    const g2 = group(s > 0 ? 'x_flame_r2' : 'x_flame_l2', [s * 5.6, 29.8, 0.8], [12, 0, -s * 40], body);
    cross('f2', g2, [s * 5.6, 29.8, 0.8], 5, 8, FLAME(3), FLAME(s > 0 ? 1 : 0));
  }

  // ---------- cloak (normal): a tattered surcoat over the back, falling from the collar nearly to the ground ----------
  const cloak = group('n_cloak', [0, 31, 0.5], [0, 0, 0], body);
  drape('cloak', cloak, { a0: -100, a1: 100, cols: 14, rows: 8, top: 31, len: 28, rx: 5.8, rz: 3.0, flareX: 2.6, flareZ: 2.2, dz: 0.4, back: 1.5, fold: 0.1, folds: 6, uv: CLOAK });

  // ---------- long robe (negative): closes all the way round under the armoured skirt and hides the legs ----------
  const robe = group('x_robe', [0, 17, 0], [0, 0, 0], body);
  drape('robe', robe, { a0: -180, a1: 180, cols: 16, rows: 6, top: 16.5, len: 16.3, rx: 3.4, rz: 2.35, flareX: 2.6, flareZ: 2.2, fold: 0.12, folds: 6, uv: CLOAK });

  // ---------- arms: long, reaching past the knees, with armoured shoulders and forearms ----------
  for (const s of [1, -1]) {
    const side = s > 0 ? '_r' : '_l';
    const sx = s * 5.2;
    const arm = group('arm' + side, [sx, 29.8, 0.2], [6, 0, s * 6], body);
    loft('upper', arm, [ring(30.6, 1.6, 1.55, sx, 0.2), ring(28.5, 1.5, 1.45, sx, 0.2), ring(24, 1.2, 1.15, sx, 0.2), ring(20.2, 1.0, 1.0, sx, 0.2)], SKIN, 8, { caps: ['start'] });
    // Shoulder guards (sode): three broad laced plates, each a little wider, hanging over the outside of the arm.
    for (let k = 0; k < 3; k++) {
      const yTop = 31.4 - 2.5 * k;
      plate('sode' + k, arm, { cx: sx, cz: 0.2, a0: s * 90 - 72, a1: s * 90 + 72, yTop, yBot: yTop - 3.6, rTop: 1.95 + 0.32 * k, rBot: 2.5 + 0.32 * k,
        thick: 0.35, uv: ARMOR(2, 32), cols: 6 });
    }
    const fore = group('forearm' + side, [sx, 20.2, 0.2], [14, 0, 0], arm);
    loft('fore', fore, [ring(21.0, 1.1, 1.1, sx, 0.2), ring(19.6, 1.2, 1.1, sx, 0.2), ring(14, 1.0, 0.95, sx, 0.2), ring(9.2, 0.78, 0.75, sx, 0.2)], SKIN, 8, { caps: ['start'] });
    // Armoured sleeve (kote).
    loft('kote', fore, [ring(19.2, 1.38, 1.3, sx, 0.2), ring(14, 1.22, 1.15, sx, 0.2), ring(10.6, 1.0, 0.95, sx, 0.2)], ARMOR(5, 32), 8);

    // Hand (normal): a big palm and three thick, bent claws.
    const hand = group('hand' + side, [sx, 9.2, 0.2], [8, 0, 0], fore);
    const handBits = group('n_claws' + side, [sx, 9.2, 0.2], [0, 0, 0], hand);
    loft('palm', handBits, [ring(9.6, 0.8, 0.78, sx, 0.2), ring(8.2, 1.15, 0.7, sx, 0.0), ring(6.8, 1.05, 0.6, sx, -0.1)], SKIN, 8, { caps: ['end'] });
    [[-0.7, -0.5], [0, 0], [0.7, 0.5]].forEach(([dx, dz], k) => {
      const base = [sx + dx * s, 7.0, dz * 0.5 - 0.1];
      const mid = add(base, [dx * s * 0.35, -2.4, -0.7]);
      const tip = add(mid, [dx * s * 0.25, -2.0, -1.5]);
      claw('claw' + k, handBits, base, mid, tip, 0.45, BONE);
    });
    // Negative: the forearm runs straight on into a long sword blade, with a guard (tsuba) at the wrist.
    const bl = group('x_blade' + side, [sx, 9.2, 0.2], [0, 0, 0], hand);
    tube('tsuba', bl, [sx, 9.9, 0.2], [sx, 9.3, 0.2], 1.25, 1.25, METAL, 8);
    scythe('blade', bl, sx, 9.6, 0.2, 13);
  }

  // ---------- legs: long, with armoured shins, digging in on clawed toes ----------
  for (const s of [1, -1]) {
    const side = s > 0 ? '_r' : '_l';
    const x = s * 1.9;
    const leg = group('leg' + side, [x, 15.2, 0], [-6, 0, s * 3], root);
    loft('thigh', leg, [ring(15.8, 1.7, 1.6, x, 0), ring(13, 1.55, 1.5, x, 0.1), ring(8.6, 1.05, 1.05, x, 0.1)], SKIN, 8, { caps: ['start'] });
    const shin = group('shin' + side, [x, 8.8, 0.1], [10, 0, 0], leg);
    loft('shin', shin, [ring(9.4, 1.1, 1.1, x, 0.1), ring(6, 0.9, 0.9, x, 0.25), ring(1.6, 0.62, 0.65, x, 0.3)], SKIN, 8, { caps: ['start'] });
    // Shin guard (suneate).
    loft('suneate', shin, [ring(8.4, 1.3, 1.35, x, 0.0), ring(5, 1.12, 1.18, x, 0.15), ring(2.4, 0.95, 1.0, x, 0.2)], ARMOR(4, 24), 8);
    loft('foot', shin, [ring(2.0, 0.7, 0.72, x, 0.3), ring(0.5, 1.0, 1.25, x, -0.4)], SKIN, 8, { caps: ['end'] });
    [-0.6, 0, 0.6].forEach((dx, k) => {
      const base = [x + dx, 0.65, -1.0];
      claw('toe' + k, shin, base, add(base, [dx * 0.4, -0.1, -1.5]), add(base, [dx * 0.7, -0.6, -2.8]), 0.36, BONE);
    });
  }

  Canvas.updateAll();
  return { meshes: Mesh.all.length, cubes: Cube.all.length, groups: Group.all.length };
}

/** Show one form: hides the other form's parts and swaps the sheet. */
function showForm(negative, textureUrl) {
  const hidden = g => { for (let p = g; p instanceof Group; p = p.parent) if (p.name.startsWith(negative ? 'n_' : 'x_')) return true; return false; };
  for (const el of [...Cube.all, ...Mesh.all]) {
    el.visibility = !(el.parent instanceof Group && hidden(el.parent));
  }
  if (textureUrl) Texture.all[0].fromDataURL(textureUrl);
  Canvas.updateAll();
}
