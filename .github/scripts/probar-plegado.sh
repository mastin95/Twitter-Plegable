#!/usr/bin/env bash
# Plegar y desplegar Pliegue en el Android virtual con las webs de X puestas. Para entrar en el modo
# con sesión sin cuenta real se pone una cookie de sesión falsa (X no la acepta: las webs enseñan lo
# que X dé a un visitante, normalmente su comprobación "Verify you are human"). Lo que se prueba es
# la app: que cada web se vea (no en negro) al plegar y desplegar, y que cada ranura cambie de modo.
# "Plegado" es la pantalla del Pixel 6 tal cual (411 dp); "abierto" la ensancha a 2208x1840 como la
# pantalla interior de un plegable (la app decide por el ancho si no hay bisagra).
set -u
OUT="${GITHUB_WORKSPACE:-$PWD}/plegado-out"
mkdir -p "$OUT"
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
abrir() { adb shell wm size 2208x1840; sleep 12; }
plegar() { adb shell wm size reset; sleep 12; }

adb logcat -c
adb install -r "$APK"
adb shell am start -W -n "$PKG/.MainActivity" --ez depurar_web true
sleep 8
sin_avisos
toca '^Iniciar sesión en X$'
sleep 12
devtools && python3 .github/scripts/cdp.py sesion-falsa 9222
sleep 15
paso plegado

abrir
paso abierto
plegar
paso plegado-otra-vez
abrir
paso abierto-otra-vez
plegar
paso plegado-tercera-vez

adb shell wm size reset
adb logcat -d -s Pliegue:I > "$OUT/registro-app.txt" 2>&1
echo "===== registro de la app"
tail -80 "$OUT/registro-app.txt"
exit 0
