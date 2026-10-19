/*
 * Builds Solace's model inside Blockbench (runs in the Blockbench page, see bb_run.mjs).
 * Units are Blockbench pixels: 16 = one block. The model faces north (-z), its right side is +x.
 *
 * No head and no eyes in either form: a high coat collar stands round an empty hollow where the head should be.
 * Group name prefixes decide which form shows a part:
 *   n_  only in the normal form (dark: spiked shoulders, tattered cloak, claws)
 *   x_  only in the negative (white flaming wraith: shoulder flames, black bands, long robe, scythe blades)
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
   * A thick curved scythe blade growing straight out of the end of the forearm (negative), so it reads as part of the arm.
   * Its back edge is thick, its front edge sharp. Curves down, then forward.
   */
  function scythe(name, parent, x, rootY, rootZ, len) {
    const tris = [];
    const P0 = [rootY, rootZ], P1 = [rootY - len * 0.85, rootZ + 0.9], P2 = [rootY - len * 0.95, rootZ - len * 0.62];
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
      const w = Math.pow(1 - t, 0.7) * (1.5 + 1.8 * Math.sin(Math.PI * t));
      const th = Math.pow(1 - t, 0.5) * 0.45 + 0.04;
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

  // ---------- torso: gaunt but solid, hunched, a broad chest over a narrower waist ----------
  const body = group('body', [0, 15.5, 0], [-8, 0, 0], root);
  // The top dips inward inside the collar: an empty hollow where a head should be.
  const top = { c: [0, 31.2, 0.2], x: X, z: Z, rx: 5.4, rz: 2.3, slope: 0.12, capCentre: [0, 29.8, 0.3] };
  loft('torso', body, [
    ring(14.2, 3.0, 2.0), ring(16.5, 3.2, 2.15), ring(19, 2.6, 1.85), ring(21.5, 3.2, 2.15),
    ring(24.5, 4.2, 2.55, 0, 0.1), ring(27.5, 4.7, 2.65, 0, 0.2), ring(29.6, 5.3, 2.5, 0, 0.2), top,
  ], SKIN, 12, { caps: ['start', 'end'] });

  // The coat's collar: black and solid in the normal form; in the negative, white and torn along the top.
  const coll = group('n_collar', [0, 30.5, 0.4], [0, 0, 0], body);
  collar('collar', coll, { y: 30.2, height: 4.8, drop: 0.8, rx: 3.9, rz: 2.5, flare: 2.3, thick: 0.55, dz: 0.4, back: 1.4, spread: 125, cols: 16, uv: [0, 66, 128, 36] });
  const xcoll = group('x_collar', [0, 30.5, 0.4], [0, 0, 0], body);
  collar('collar', xcoll, { y: 30.2, height: 5.4, drop: 0.8, rx: 3.9, rz: 2.5, flare: 2.6, thick: 0.4, dz: 0.4, back: 1.4, spread: 125, cols: 16, uv: [192, 96, 64, 32], flip: true });

  // Spikes on the shoulders (normal): three thick black ones each side, fanning up and out. Nothing in the middle.
  for (const s of [1, -1]) {
    const g = group(s > 0 ? 'n_spikes_r' : 'n_spikes_l', [s * 4.6, 30.6, 0.3], [0, 0, 0], body);
    const spikes = [[4.2, 30.6, 0.9, 2.2, 4.4, 2.4, 1.15], [5.2, 29.8, 0.2, 3.8, 2.2, 1.0, 1.05], [4.6, 30.2, 1.9, 2.0, 2.0, 4.0, 0.95]];
    spikes.forEach(([x, y, z, dx, dy, dz, r], k) => {
      const a = [s * x, y, z], tip = [s * (x + dx), y + dy, z + dz];
      claw('spike' + k, g, a, add(a, mul(sub(tip, a), 0.5)), tip, r, SPIKE);
    });
  }

  // Black bands wrapped across the white body (negative).
  const bands = group('x_bands', [0, 24, 0], [0, 0, 0], body);
  loft('band1', bands, [tilted(25.6, 4.45, 2.75, 22), tilted(27.0, 4.65, 2.8, 22)], BAND, 12);
  loft('band2', bands, [tilted(27.4, 4.9, 2.8, -26), tilted(28.8, 5.1, 2.75, -26)], BAND, 12);
  loft('band3', bands, [tilted(19.1, 2.75, 1.95, 10), tilted(20.5, 2.9, 2.05, 10)], BAND, 12);
  loft('band4', bands, [ring(15.2, 3.25, 2.2), ring(16.8, 3.35, 2.3)], BAND, 12);

  // White flames licking up from the outer shoulders (negative), leaning out. One big tongue and one small each side.
  for (const s of [1, -1]) {
    const g = group(s > 0 ? 'x_flame_r' : 'x_flame_l', [s * 4.4, 30.6, 0.3], [0, 0, -s * 18], body);
    cross('f1', g, [s * 4.4, 30.6, 0.3], 7, 12, FLAME(s > 0 ? 0 : 1), FLAME(2));
    const g2 = group(s > 0 ? 'x_flame_r2' : 'x_flame_l2', [s * 5.6, 29.8, 0.8], [12, 0, -s * 40], body);
    cross('f2', g2, [s * 5.6, 29.8, 0.8], 5, 8, FLAME(3), FLAME(s > 0 ? 1 : 0));
  }

  // ---------- cloak (normal): one tattered sheet over the back, falling from the collar nearly to the ground ----------
  const cloak = group('n_cloak', [0, 31, 0.5], [0, 0, 0], body);
  drape('cloak', cloak, { a0: -105, a1: 105, cols: 14, rows: 8, top: 31, len: 28, rx: 5.6, rz: 2.8, flareX: 2.6, flareZ: 2.2, dz: 0.3, back: 1.5, fold: 0.1, folds: 6, uv: CLOAK });

  // ---------- long robe (negative): closes all the way round and hides the legs ----------
  const robe = group('x_robe', [0, 17, 0], [0, 0, 0], body);
  drape('robe', robe, { a0: -180, a1: 180, cols: 16, rows: 6, top: 17.2, len: 17, rx: 3.4, rz: 2.35, flareX: 2.6, flareZ: 2.2, fold: 0.12, folds: 6, uv: CLOAK });
  drape('robe_over', robe, { a0: -150, a1: 150, cols: 14, rows: 4, top: 18.5, len: 9, rx: 3.5, rz: 2.45, flareX: 1.6, flareZ: 1.3, fold: 0.14, folds: 5, uv: ROBE });

  // ---------- arms: long, reaching past the knees ----------
  for (const s of [1, -1]) {
    const side = s > 0 ? '_r' : '_l';
    const sx = s * 5.2;
    const arm = group('arm' + side, [sx, 29.8, 0.2], [6, 0, s * 6], body);
    loft('upper', arm, [ring(30.6, 1.6, 1.55, sx, 0.2), ring(28.5, 1.5, 1.45, sx, 0.2), ring(24, 1.2, 1.15, sx, 0.2), ring(20.2, 1.0, 1.0, sx, 0.2)], SKIN, 8, { caps: ['start'] });
    loft('x_armband', group('x_band_arm' + side, [sx, 24, 0.2], [0, 0, 0], arm), [ring(23.3, 1.3, 1.25, sx, 0.2), ring(24.7, 1.38, 1.32, sx, 0.2)], BAND, 8);
    const fore = group('forearm' + side, [sx, 20.2, 0.2], [14, 0, 0], arm);
    loft('fore', fore, [ring(21.0, 1.1, 1.1, sx, 0.2), ring(19.6, 1.2, 1.1, sx, 0.2), ring(14, 1.0, 0.95, sx, 0.2), ring(9.2, 0.78, 0.75, sx, 0.2)], SKIN, 8, { caps: ['start'] });

    // Hand (normal): a big palm and three thick, bent claws.
    const hand = group('hand' + side, [sx, 9.2, 0.2], [8, 0, 0], fore);
    const handBits = group('n_claws' + side, [sx, 9.2, 0.2], [0, 0, 0], hand);
    loft('palm', handBits, [ring(9.6, 0.8, 0.78, sx, 0.2), ring(8.2, 1.15, 0.7, sx, 0.0), ring(6.8, 1.05, 0.6, sx, -0.1)], SKIN, 8, { caps: ['end'] });
    [[-0.7, -0.5], [0, 0], [0.7, 0.5]].forEach(([dx, dz], k) => {
      const base = [sx + dx * s, 7.0, dz * 0.5 - 0.1];
      const mid = add(base, [dx * s * 0.4, -3.0, -0.8]);
      const tip = add(mid, [dx * s * 0.3, -2.6, -1.8]);
      claw('claw' + k, handBits, base, mid, tip, 0.42, BONE);
    });
    // Negative: the forearm runs straight on into a thick curved scythe blade.
    const bl = group('x_blade' + side, [sx, 9.2, 0.2], [0, 0, 0], hand);
    scythe('blade', bl, sx, 10.0, 0.2, 11);
  }

  // ---------- legs: long, digging in on clawed toes ----------
  for (const s of [1, -1]) {
    const side = s > 0 ? '_r' : '_l';
    const x = s * 1.9;
    const leg = group('leg' + side, [x, 15.2, 0], [-6, 0, s * 3], root);
    loft('thigh', leg, [ring(15.8, 1.7, 1.6, x, 0), ring(13, 1.55, 1.5, x, 0.1), ring(8.6, 1.05, 1.05, x, 0.1)], SKIN, 8, { caps: ['start'] });
    const shin = group('shin' + side, [x, 8.8, 0.1], [10, 0, 0], leg);
    loft('shin', shin, [ring(9.4, 1.1, 1.1, x, 0.1), ring(6, 0.9, 0.9, x, 0.25), ring(1.6, 0.62, 0.65, x, 0.3)], SKIN, 8, { caps: ['start'] });
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
