// Mide la columna de iconos de X y la de posts en la página abierta (para la prueba de estilos).
(function () {
  function r(sel) {
    var e = document.querySelector(sel);
    if (!e) return null;
    var b = e.getBoundingClientRect();
    return { x: Math.round(b.x), ancho: Math.round(b.width), visible: b.width > 0 && getComputedStyle(e).display !== 'none' };
  }
  return JSON.stringify({
    url: location.href,
    ancho: innerWidth,
    iconos: r('header[role="banner"]'),
    main: r('main[role="main"]'),
    posts: r('[data-testid="primaryColumn"]'),
  });
})();
