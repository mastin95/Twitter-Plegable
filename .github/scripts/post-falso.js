// Un post falso con los botones de la web de X (sus data-testid), para probar la web escondida de
// acciones en el Android virtual, donde X no deja entrar. Cada botón cambia al contrario al tocarlo,
// como hace X; repostear abre su menú de confirmación. Devuelve el id del post que simula.
(function () {
  var id = (location.pathname.match(/status\/(\d+)/) || [])[1] || '0';
  document.body.innerHTML =
    '<article><a href="/prueba/status/' + id + '">post</a>' +
    '<button data-testid="like">like</button><button data-testid="retweet">rt</button>' +
    '<button data-testid="bookmark">bm</button></article>';
  var flip = { like: 'unlike', unlike: 'like', bookmark: 'removeBookmark', removeBookmark: 'bookmark' };
  document.body.addEventListener('click', function (e) {
    var b = e.target, t = b.getAttribute && b.getAttribute('data-testid');
    if (!t) return;
    if (t === 'retweet' || t === 'unretweet') {
      var m = document.createElement('div');
      m.id = 'menu';
      m.innerHTML = '<button data-testid="' + (t === 'retweet' ? 'retweetConfirm' : 'unretweetConfirm') + '">ok</button>';
      document.body.appendChild(m);
      return;
    }
    if (t === 'retweetConfirm' || t === 'unretweetConfirm') {
      var rt = document.querySelector('article [data-testid="retweet"], article [data-testid="unretweet"]');
      if (rt) rt.setAttribute('data-testid', t === 'retweetConfirm' ? 'unretweet' : 'retweet');
      var menu = document.getElementById('menu');
      if (menu) menu.remove();
      return;
    }
    if (flip[t]) b.setAttribute('data-testid', flip[t]);
  });
  return 'post falso ' + id;
})();
