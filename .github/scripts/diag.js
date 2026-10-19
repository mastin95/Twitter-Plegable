// Por qué el formulario de X no se ve: estilos calculados del diálogo, sus padres y lo de dentro,
// animaciones en marcha, capa superior y si la página recibe fotogramas (requestAnimationFrame).
(async () => {
  const out = {};
  out.visibilidad = document.visibilityState;
  const alto = (u) => { const e = document.createElement('div'); e.style.cssText = `position:fixed;top:0;left:0;width:1px;visibility:hidden;height:100${u}`; document.documentElement.appendChild(e); const h = e.getBoundingClientRect().height; e.remove(); return Math.round(h); };
  out.alto100 = { vh: alto('vh'), dvh: alto('dvh'), svh: alto('svh'), lvh: alto('lvh'), innerHeight };
  out.foco = document.hasFocus();
  out.fotogramasEnMedioSegundo = await new Promise((done) => {
    let n = 0;
    const t0 = performance.now();
    const f = () => { n++; if (performance.now() - t0 < 500) requestAnimationFrame(f); else done(n); };
    requestAnimationFrame(f);
    setTimeout(() => done(n), 1500);
  });
  const st = (el) => {
    const c = getComputedStyle(el);
    const r = el.getBoundingClientRect();
    const o = {
      tag: el.tagName.toLowerCase(),
      rect: [Math.round(r.x), Math.round(r.y), Math.round(r.width), Math.round(r.height)],
      op: c.opacity, vis: c.visibility, disp: c.display, color: c.color, bg: c.backgroundColor, pos: c.position, z: c.zIndex,
    };
    if (el.id) o.id = el.id;
    if (typeof el.className === 'string' && el.className) o.cls = el.className.slice(0, 100);
    if (el.getAttribute('role')) o.role = el.getAttribute('role');
    if (el.hasAttribute('popover')) o.popover = el.getAttribute('popover');
    if (c.transform !== 'none') o.transform = c.transform;
    if (c.clipPath !== 'none') o.clip = c.clipPath;
    if (c.filter !== 'none') o.filter = c.filter;
    if (c.contentVisibility && c.contentVisibility !== 'visible') o.cv = c.contentVisibility;
    if (c.overflow !== 'visible') o.overflow = c.overflow;
    if (c.mixBlendMode !== 'normal') o.blend = c.mixBlendMode;
    if (el.childElementCount === 0) o.text = (el.textContent || '').trim().slice(0, 40);
    return o;
  };
  const d = document.querySelector('[role=dialog]') || document.querySelector('dialog');
  if (d) {
    out.padres = [];
    for (let el = d; el; el = el.parentElement) out.padres.push(st(el));
    out.dentro = [];
    const walk = (el, depth) => {
      if (depth > 7 || out.dentro.length > 80) return;
      out.dentro.push(Object.assign({ depth }, st(el)));
      for (const ch of el.children) walk(ch, depth + 1);
    };
    walk(d, 0);
  }
  out.animaciones = document.getAnimations().slice(0, 30).map((a) => ({
    nombre: a.animationName || a.transitionProperty || a.constructor.name,
    estado: a.playState,
    tiempo: a.currentTime,
    destino: a.effect && a.effect.target ? a.effect.target.tagName.toLowerCase() + (a.effect.target.id ? '#' + a.effect.target.id : '') : null,
  }));
  try { out.capaSuperior = [...document.querySelectorAll(':popover-open, dialog[open]')].map((e) => e.tagName.toLowerCase() + (e.id ? '#' + e.id : '')); } catch (e) { out.capaSuperior = String(e); }
  out.cuerpo = document.body ? st(document.body) : null;
  out.html = st(document.documentElement);
  out.estilosHoja = document.styleSheets.length;
  out.temaColor = (document.querySelector('meta[name=theme-color]') || {}).content;
  return JSON.stringify(out);
})()
