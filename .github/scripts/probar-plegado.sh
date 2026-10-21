#!/usr/bin/env bash
# Plegar y desplegar Pliegue en el Android virtual con las webs de X puestas. La app arranca con la
# red simulada (red_simulada): las webs no salen a internet y cada página es un documento vacío en la
# dirección pedida, porque desde aquí X no deja entrar (Cloudflare) y, cuando deja, borra la cookie de
# sesión falsa que se pone para entrar en el modo con sesión. Lo que se prueba es la app: que cada web
# se vea (no en negro) al plegar y desplegar, que cada ranura cambie de modo y que la interfaz propia
# pinte las respuestas de ejemplo que se le inyectan.
# "Plegado" es la pantalla del Pixel 6 tal cual (411 dp); "abierto" la ensancha a 2208x1840 como la
# pantalla interior de un plegable (la app decide por el ancho si no hay bisagra).
set -u
OUT="${GITHUB_WORKSPACE:-$PWD}/plegado-out"
mkdir -p "$OUT"
# Lo que dice la prueba también va a la rama de resultados (los registros de Actions no siempre se pueden leer).
exec > >(tee -a "$OUT/salida.txt") 2>&1
APK=app/build/outputs/apk/debug/app-debug.apk
PKG=io.github.mastin95.pliegue
UI=.github/scripts/ui.py
n=0

volcar() {
  for _ in 1 2 3; do
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 && adb pull /sdcard/ui.xml "$1" >/dev/null 2>&1 && return 0
    sleep 1
  done
  return 1
}
# El Android virtual a veces saca "Pixel Launcher isn't responding" encima de todo; se espera.
sin_avisos() {
  for _ in 1 2 3; do
    volcar /tmp/aviso.xml || return 0
    python3 "$UI" find /tmp/aviso.xml "isn't responding" >/dev/null 2>&1 || return 0
    echo "aviso de 'no responde': toco Wait"
    adb shell input tap $(python3 "$UI" find /tmp/aviso.xml '^Wait$')
    sleep 3
  done
}
paso() {
  sin_avisos
  n=$((n + 1))
  local base
  base=$(printf '%02d-%s' "$n" "$1")
  adb exec-out screencap -p > "$OUT/$base.png" 2>/dev/null
  volcar "$OUT/$base.xml"
  python3 "$UI" texts "$OUT/$base.xml" > "$OUT/$base.txt" 2>&1
  echo "===== $base"
  head -c 3000 "$OUT/$base.txt"
  echo
}
toca() {
  volcar /tmp/ui.xml
  local xy
  xy=$(python3 "$UI" find /tmp/ui.xml "$1") || { echo "no encuentro: $1"; return 1; }
  echo "toco '$1' en $xy"
  adb shell input tap $xy
}
devtools() {
  local pid sock
  pid=$(adb shell pidof "$PKG" | tr -d '\r')
  sock=$(adb shell cat /proc/net/unix | grep -o "webview_devtools_remote_$pid" | head -1)
  [ -n "$sock" ] || { echo "sin socket de DevTools"; return 1; }
  adb forward tcp:9222 "localabstract:$sock" >/dev/null && echo "DevTools en $sock"
}
# Pone la cookie falsa hasta que la app la ve (lo apunta al mirar las cookies tras una carga).
sesion_falsa() {
  for i in 1 2 3 4; do
    devtools && python3 .github/scripts/cdp.py sesion-falsa 9222
    sleep 10
    if adb logcat -d -s Pliegue:I | grep -q "cookies de x.com:.*auth_token"; then
      echo "la app ya ve la sesión falsa"
      return 0
    fi
    echo "la app aún no ve la sesión falsa (intento $i)"
  done
  return 1
}
abrir() { adb shell wm size 2208x1840; sleep 12; }
# La interfaz propia con posts de ejemplo: a la web de la ranura se le da una respuesta de X de las
# pruebas, como si la hubiera recibido, y la app la pinta (en el Android virtual X no deja entrar).
EJ=app/src/test/resources/x
inyectar() { devtools && python3 .github/scripts/cdp.py inyectar 9222 "$EJ/$1.json" "$2" "${3:-}" "${4:-"{}"}"; sleep 4; }
evaluar() { devtools && python3 .github/scripts/cdp.py evaluar 9222 "$1" ".github/scripts/$2.js"; }
desliza() { adb shell input swipe "$1" "$2" "$1" "$3" 400; sleep 2; }   # x, y de salida, y de llegada
busca_y_toca() {  # baja por la lista (en x=$2, de y=$3 a y=$4) hasta ver el texto y lo toca
  local i
  for i in 1 2 3 4 5 6 7 8; do
    toca "$1" && return 0
    desliza "${2:-500}" "${3:-1400}" "${4:-500}"
  done
  return 1
}
plegar() { adb shell wm size reset; sleep 12; }

adb logcat -c
adb install -r "$APK"
# Un vídeo de muestra para el reproductor propio (en el Android virtual no hay vídeos de X): se graba la
# pantalla del propio Android virtual unos segundos (screenrecord; el ejecutor no trae ffmpeg) y se deja
# en la carpeta privada de la app, que con la red simulada lo reproduce en vez del de X. Los vídeos
# públicos de muestra de internet no valen: el de Google devolvía 403.
video_muestra() {
  adb shell screenrecord --time-limit 6 --size 640x360 --bit-rate 1500000 /data/local/tmp/muestra.mp4 || return 1
  sleep 2
  # Por dos caminos, que la app mira los dos: la carpeta privada con run-as (como la app misma; cp daba
  # "Permission denied", se prueba con cat) y la externa (adb escribe ahí, pero el archivo queda de shell
  # con permisos 600 y la app no lo podía leer: se abre al grupo, del que la app forma parte).
  local ext="/sdcard/Android/data/$PKG/files" ok=1
  adb shell "run-as $PKG sh -c 'mkdir -p files && cat /data/local/tmp/muestra.mp4 > files/muestra.mp4 && chmod 644 files/muestra.mp4 && ls -l files/muestra.mp4'" && ok=0
  adb shell "mkdir -p $ext && cp /data/local/tmp/muestra.mp4 $ext/muestra.mp4 && chmod 664 $ext/muestra.mp4 && ls -l $ext/muestra.mp4" && ok=0
  return $ok
}
if video_muestra; then echo "vídeo de muestra en la carpeta de la app"; else echo "sin vídeo de muestra (el reproductor dará error en las capturas)"; fi
# Sin los avisos de "no responde" del sistema (el Android virtual va lento y el del lanzador tapa la app).
adb shell settings put global hide_error_dialogs 1
adb shell am start -W -n "$PKG/.MainActivity" --ez depurar_web true --ez red_simulada true
sleep 8
sin_avisos
for _ in 1 2 3 4 5; do toca '^Iniciar sesión en X$' && break; sleep 5; done
sleep 12
sesion_falsa
sleep 10
paso plegado

abrir
paso abierto
plegar
paso plegado-otra-vez
abrir
paso abierto-otra-vez
plegar
paso plegado-tercera-vez

# Interfaz propia: Inicio con posts de ejemplo, plegado y abierto; un post tocado se abre al otro lado.
inyectar home HomeTimeline /home
paso propia-plegado
abrir
inyectar home2 HomeTimeline /home '{"cursor":"CURSOR-BOTTOM-1"}'
paso propia-abierto
busca_y_toca 'Para quien preguntaba' 500 1400 500 && sleep 8
inyectar detail TweetDetail /status/ '{"focalTweetId":"1008"}'
paso propia-post-al-lado
plegar
paso propia-post-plegado

# Fase 2: un me gusta desde la tarjeta. La tarjeta cambia al momento y la web escondida de acciones abre
# el post; como aquí no hay X, se le monta un post falso con los botones de X y se comprueba que el
# guion lo toca y la app lo da por hecho (el corazón sigue lleno y no sale el aviso de "no aceptado").
toca 'Me gusta' && sleep 3
evaluar /i/status/ post-falso
sleep 5
paso propia-me-gusta

# Fase 3: Notificaciones pintadas por la app, con avisos de ejemplo inyectados en su web escondida.
adb shell input keyevent 4
sleep 3
toca 'Notificaciones' && sleep 6
inyectar notifications NotificationsTimeline /notifications '{"timeline_type":"All"}'
paso propia-notificaciones

# Fase 4: la caché de Inicio y el visor de fotos. Se cierra la app del todo y se vuelve a abrir: Inicio
# tiene que salir del disco al momento, sin inyectar nada (en el registro, "de la caché"). Si la sesión
# falsa no sobrevivió al cierre, se vuelve a poner. Luego una foto tocada se abre en el visor propio
# (con la red simulada las imágenes son cuadros de color con su nombre).
adb shell am force-stop "$PKG"
sleep 2
adb shell am start -W -n "$PKG/.MainActivity" --ez depurar_web true --ez red_simulada true
sleep 10
sin_avisos
if toca '^Iniciar sesión en X$'; then sleep 12; sesion_falsa; sleep 10; fi
sleep 6
paso propia-cache
busca_y_toca 'Imagen' 500 1400 500 && sleep 6
paso propia-foto

# Vídeos en vertical: al tocar un vídeo se abre a pantalla completa con quién lo publicó y las acciones,
# y deslizando hacia arriba viene el siguiente con vídeo del mismo feed (aquí, el GIF del post de ejemplo;
# con la red simulada todos reproducen un vídeo de muestra). Atrás cierra la foto primero y los vídeos después.
adb shell input keyevent 4
sleep 3
busca_y_toca '^Vídeo$' 500 1400 500 && sleep 12
paso propia-videos
desliza 500 1500 300
sleep 8
paso propia-videos-2
adb shell input keyevent 4
sleep 3

# Enlaces que llegan de fuera, con la app ya abierta: un post de X, un enlace corto t.co (web ajena) y
# algo compartido con Pliegue (va al redactor). Se manda a la actividad para que no salga el selector.
adb shell am start -W -n "$PKG/.MainActivity" -a android.intent.action.VIEW -d "https://x.com/XDevelopers/status/1" >/dev/null
sleep 10
paso enlace-de-x
adb shell am start -W -n "$PKG/.MainActivity" -a android.intent.action.VIEW -d "https://t.co/pliegue" >/dev/null
sleep 10
paso enlace-corto
adb shell am start -W -n "$PKG/.MainActivity" -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "Mira https://example.com/noticia" >/dev/null
sleep 10
paso compartido

adb shell wm size reset
adb logcat -d -s Pliegue:I > "$OUT/registro-app.txt" 2>&1
echo "===== registro de la app"
tail -80 "$OUT/registro-app.txt"
exit 0
