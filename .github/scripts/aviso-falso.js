// Pruebas de los posts nuevos: en el Android virtual X no avisa de nada, así que en la web de Inicio se
// monta una píldora como la suya ("New posts are available…"). La app la pulsa sola (mira cada 20 s) y,
// al pulsarla, la web le da los posts de ejemplo (window.__nuevos, de home-nuevos.json) como si X los
// mandara por arriba, con el cursor de arriba del Inicio de ejemplo.
(function(){
  var old = document.getElementById('pliegue-aviso-falso');
  if (old) old.remove();
  var b = document.createElement('div');
  b.id = 'pliegue-aviso-falso';
  b.setAttribute('role', 'button');
  b.setAttribute('aria-label', 'New posts are available. Push the period key to go to the them.');
  b.textContent = 'posted';
  b.addEventListener('click', function(){
    var url = 'https://x.com/i/api/graphql/prueba/HomeTimeline?variables=' + encodeURIComponent('{"cursor":"CURSOR-TOP-1"}');
    window.Pliegue.data('HomeTimeline', url, JSON.stringify(window.__nuevos));
    b.remove();
  });
  (document.body || document.documentElement).appendChild(b);
  return 'aviso falso puesto en ' + location.pathname + (window.Pliegue && window.Pliegue.data ? '' : ' (sin puente)');
})();
