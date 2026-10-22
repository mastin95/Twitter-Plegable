// El redactor de X con su botón de publicar (data-testid="tweetButton"), para probar en el Android
// virtual, donde X no deja entrar, que la app publica una respuesta pulsándolo. Al pulsarlo se
// "publica" y el redactor se cierra, como en X. Devuelve el texto que la app le pasó en la dirección.
(function () {
  var text = new URLSearchParams(location.search).get('text') || '';
  var to = new URLSearchParams(location.search).get('in_reply_to') || '';
  document.body.innerHTML = '<div id="redactor"><div data-testid="tweetTextarea_0"></div><button data-testid="tweetButton">Responder</button></div>';
  document.querySelector('[data-testid="tweetTextarea_0"]').textContent = text;
  document.querySelector('[data-testid="tweetButton"]').addEventListener('click', function () {
    document.getElementById('redactor').remove();
  });
  return 'redactor falso: respuesta a ' + to + ' con "' + text + '"';
})();
