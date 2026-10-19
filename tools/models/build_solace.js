/*
 * Builds Solace's model inside Blockbench (runs in the Blockbench page, see bb_run.mjs).
 * Units are Blockbench pixels: 16 = one block. The model faces north (-z), its right side is +x.
 *
 * No head and no eyes in either form: the body ends in flat, sloping shoulders.
 * Group name prefixes decide which form shows a part:
 *   n_  only in the normal form (dark and smoky: spiked shoulders, tattered cloak, wisps, claws)
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
  const STRAND = i => [64 + (i % 6) * 32, 0, 32, 64];
  const CLOAK = [0, 64, 128, 128];
  const SPIKE = [128, 64, 32, 64];
  const BONE = [160, 64, 32, 64];
  const BAND = [192, 64, 64, 32];
  const ROBE = [192, 96, 64, 32];
  const CURL = i => [128 + (i % 2) * 64, 128, 64, 64];
  const FLAME = i => [(i % 4) * 32, 192, 32, 64];
  const BLADE = i => [128 + (i % 2) * 64, 192, 64, 64];
  const X = [1, 0, 0], Z = [0, 0, 1];
  const ring = (y, rx, rz, cx = 0, cz = 0) => ({ c: [cx, y, cz], x: X, z: Z, rx, rz });
  /** A ring tilted round the z axis (for bands wrapped at an angle). */
  const tilted = (y, rx, rz, deg) => {
    const r = deg * Math.PI / 180;
    return { c: [0, y, 0], x: [Math.cos(r), Math.sin(r), 0], z: Z, rx, rz };
  };

  const root = group('root', [0, 0, 0]);

  // ---------- torso: wiry, hunched, a narrow waist under a broad chest; flat sloping shoulders, no neck ----------
  const body = group('body', [0, 15.5, 0], [-8, 0, 0], root);
  const top = { c: [0, 31.2, 0.2], x: X, z: Z, rx: 4.9, rz: 1.65, slope: 0.16, capCentre: [0, 31.25, 0.2] };
  loft('torso', body, [
    ring(14.4, 2.5, 1.6), ring(16.5, 2.7, 1.7), ring(19, 1.6, 1.15), ring(21.5, 2.2, 1.5),
    ring(24.5, 3.4, 2.0, 0, 0.1), ring(27.5, 3.9, 2.1, 0, 0.2), ring(29.6, 4.6, 1.95, 0, 0.2), top,
  ], SKIN, 10, { caps: ['start', 'end'] });
  // The spine shows through the back.
  for (let k = 0; k < 7; k++) {
    const y = 18 + k * 1.7;
    tube('vertebra' + k, body, [0, y, 1.0 + (k > 2 ? 0.6 : 0.2)], [0, y, 2.1 + (k > 2 ? 0.5 : 0.1)], 0.42, 0.18, BONE, 4);
  }

  // Spiked shoulders (normal): black spikes fanning up, out and back from the outer shoulders. Nothing in the middle.
  for (const s of [1, -1]) {
    const g = group(s > 0 ? 'n_spikes_r' : 'n_spikes_l', [s * 4.0, 30.6, 0.3], [0, 0, 0], body);
    const spikes = [[2.8, 30.9, 0.6, 2.4, 5.6, 2.8, 0.55], [3.6, 30.5, 0.0, 3.6, 4.2, 1.2, 0.6], [4.4, 30.0, 0.6, 4.8, 2.6, 2.8, 0.5],
      [4.9, 29.3, -0.2, 5.4, 0.8, 0.6, 0.45], [3.2, 30.6, 1.4, 1.8, 3.4, 4.8, 0.45], [4.2, 29.8, 1.6, 3.4, 1.2, 5.2, 0.4]];
    spikes.forEach(([x, y, z, dx, dy, dz, r], k) => {
      const a = [s * x, y, z], tip = [s * (x + dx), y + dy, z + dz];
      claw('spike' + k, g, a, add(a, mul(sub(tip, a), 0.5)), tip, r, SPIKE);
    });
  }

  // Wisps of shadow pouring off the back and streaming behind it.
  [[2.0, 29.5, 1.9, 96, 16], [-2.0, 29.5, 1.9, 100, -14], [0.8, 24, 2.2, 112, 6], [-1.0, 20, 1.7, 122, -8]].forEach(([x, y, z, rx, ry], i) => {
    const g = group('n_wisp_back' + i, [x, y, z], [rx, ry, 0], body);
    cross('w', g, [x, y, z], 5, 10, STRAND(i), STRAND(i + 3));
  });

  // Black bands wrapped across the white body (negative).
  const bands = group('x_bands', [0, 24, 0], [0, 0, 0], body);
  loft('band1', bands, [tilted(25.6, 3.75, 2.25, 22), tilted(26.7, 3.95, 2.3, 22)], BAND, 10);
  loft('band2', bands, [tilted(27.2, 4.3, 2.3, -26), tilted(28.3, 4.5, 2.3, -26)], BAND, 10);
  loft('band3', bands, [tilted(19.2, 1.75, 1.3, 10), tilted(20.3, 1.95, 1.4, 10)], BAND, 10);
  loft('band4', bands, [ring(15.4, 2.75, 1.8), ring(16.6, 2.85, 1.85)], BAND, 10);

  // White flames licking up from the outer shoulders (negative), leaning out.
  for (const s of [1, -1]) {
    const g = group(s > 0 ? 'x_flame_r' : 'x_flame_l', [s * 3.7, 30.8, 0.2], [0, 0, -s * 16], body);
    cross('f1', g, [s * 3.7, 30.8, 0.2], 6, 12, FLAME(s > 0 ? 0 : 1), FLAME(2));
    const g2 = group(s > 0 ? 'x_flame_r2' : 'x_flame_l2', [s * 4.8, 30.0, 0.6], [12, 0, -s * 38], body);
    cross('f2', g2, [s * 4.8, 30.0, 0.6], 4, 8, FLAME(3), FLAME(s > 0 ? 1 : 0));
  }

  // ---------- cloak (normal): one tattered sheet over the back, falling from the shoulders nearly to the ground ----------
  const cloak = group('n_cloak', [0, 31, 0.5], [0, 0, 0], body);
  drape('cloak', cloak, { a0: -100, a1: 100, cols: 14, rows: 8, top: 31, len: 28, rx: 4.9, rz: 2.2, flareX: 2.5, flareZ: 2.0, dz: 0.3, back: 1.5, fold: 0.1, folds: 7, uv: CLOAK });

  // ---------- long robe (negative): closes all the way round and hides the legs ----------
  const robe = group('x_robe', [0, 17, 0], [0, 0, 0], body);
  drape('robe', robe, { a0: -180, a1: 180, cols: 16, rows: 6, top: 17.2, len: 17, rx: 2.9, rz: 1.9, flareX: 2.4, flareZ: 2.0, fold: 0.12, folds: 6, uv: CLOAK });
  drape('robe_over', robe, { a0: -150, a1: 150, cols: 14, rows: 4, top: 18.5, len: 9, rx: 3.0, rz: 2.0, flareX: 1.6, flareZ: 1.3, fold: 0.14, folds: 5, uv: ROBE });

  // ---------- arms: long and thin, reaching past the knees ----------
  for (const s of [1, -1]) {
    const side = s > 0 ? '_r' : '_l';
    const sx = s * 4.5;
    const arm = group('arm' + side, [sx, 30.0, 0.2], [6, 0, s * 6], body);
    loft('upper', arm, [ring(30.6, 1.05, 1.05, sx, 0.2), ring(28.5, 1.0, 0.95, sx, 0.2), ring(24, 0.75, 0.75, sx, 0.2), ring(20.2, 0.62, 0.62, sx, 0.2)], SKIN, 6, { caps: ['start'] });
    loft('x_armband', group('x_band_arm' + side, [sx, 24, 0.2], [0, 0, 0], arm), [ring(23.4, 0.9, 0.9, sx, 0.2), ring(24.6, 0.95, 0.95, sx, 0.2)], BAND, 6);
    const fore = group('forearm' + side, [sx, 20.2, 0.2], [14, 0, 0], arm);
    loft('fore', fore, [ring(20.9, 0.7, 0.7, sx, 0.2), ring(19.6, 0.78, 0.7, sx, 0.2), ring(14, 0.6, 0.55, sx, 0.2), ring(9.2, 0.42, 0.4, sx, 0.2)], SKIN, 6, { caps: ['start'] });
    const ws = group('n_wisp_arm' + side, [sx, 17, 0.7], [130, s * 10, 0], fore);
    cross('w', ws, [sx, 17, 0.7], 4, 9, STRAND(s > 0 ? 1 : 4), STRAND(s > 0 ? 2 : 5));

    // Hand: a narrow palm and four long, bent claws.
    const hand = group('hand' + side, [sx, 9.2, 0.2], [8, 0, 0], fore);
    const handBits = group('n_claws' + side, [sx, 9.2, 0.2], [0, 0, 0], hand);
    loft('palm', handBits, [ring(9.4, 0.5, 0.45, sx, 0.2), ring(8.2, 0.75, 0.42, sx, 0.0), ring(7.0, 0.7, 0.35, sx, -0.1)], SKIN, 6, { caps: ['end'] });
    [[-0.55, -0.7], [-0.2, -0.25], [0.2, 0.2], [0.55, 0.6]].forEach(([dx, dz], k) => {
      const base = [sx + dx * s * 0.6, 7.0, dz * 0.5 - 0.1];
      const thumb = k === 0;
      const mid = add(base, thumb ? [s * 0.7, -2.0, -1.0] : [dx * s * 0.6, -3.4, -0.6]);
      const tip = add(mid, thumb ? [s * 0.2, -1.4, -1.2] : [dx * s * 0.4, -2.8, -1.7]);
      claw('claw' + k, handBits, base, mid, tip, 0.22, BONE);
    });
    // A long curved scythe blade grows from the wrist instead of a hand (negative).
    const bl = group('x_blade' + side, [sx, 9.5, 0.2], [34, 0, 0], hand);
    loft('wrist', bl, [ring(9.4, 0.45, 0.42, sx, 0.2), ring(7.6, 0.3, 0.3, sx, 0.0)], SKIN, 6, { caps: ['end'] });
    card('blade', bl, [sx, 9.5, 0.6], 10, 12, BLADE(s > 0 ? 0 : 1), { hang: true, rotation: [0, -90, 0] });
  }

  // ---------- legs: long and thin, digging in on clawed toes ----------
  for (const s of [1, -1]) {
    const side = s > 0 ? '_r' : '_l';
    const x = s * 1.7;
    const leg = group('leg' + side, [x, 15.2, 0], [-6, 0, s * 3], root);
    loft('thigh', leg, [ring(15.8, 1.15, 1.1, x, 0), ring(13, 1.05, 1.0, x, 0.1), ring(8.6, 0.68, 0.7, x, 0.1)], SKIN, 6, { caps: ['start'] });
    const lw = group('n_wisp_leg' + side, [x + s * 0.8, 13, 0.4], [150, s * 30, s * 20], leg);
    cross('w', lw, [x + s * 0.8, 13, 0.4], 4, 8, STRAND(s > 0 ? 0 : 3));
    const shin = group('shin' + side, [x, 8.8, 0.1], [10, 0, 0], leg);
    loft('shin', shin, [ring(9.4, 0.72, 0.75, x, 0.1), ring(6, 0.55, 0.6, x, 0.25), ring(1.6, 0.36, 0.4, x, 0.3)], SKIN, 6, { caps: ['start'] });
    loft('foot', shin, [ring(1.9, 0.42, 0.45, x, 0.3), ring(0.5, 0.7, 0.9, x, -0.3)], SKIN, 6, { caps: ['end'] });
    [-0.45, 0, 0.45].forEach((dx, k) => {
      const base = [x + dx, 0.6, -0.8];
      claw('toe' + k, shin, base, add(base, [dx * 0.4, -0.1, -1.6]), add(base, [dx * 0.8, -0.55, -3.0]), 0.2, BONE);
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
