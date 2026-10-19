#!/usr/bin/env bash
# Recorre el inicio de sesión de X dentro de Pliegue en el Android virtual. En cada paso guarda una
# captura, el árbol de lo que hay en pantalla y un resumen en texto. No usa ninguna cuenta real: el
# correo es de example.com (reservado), así que X debería responder que no encuentra la cuenta.
set -u
OUT="${GITHUB_WORKSPACE:-$PWD}/prueba-out"
mkdir -p "$OUT"
APK=app/build/outputs/apk/debug/app-debug.apk
PKG=io.github.mastin95.pliegue
EMAIL="pliegue.prueba.ci@example.com"
UI=.github/scripts/ui.py
n=0

volcar() { # destino
  for _ in 1 2 3; do
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 && adb pull /sdcard/ui.xml "$1" >/dev/null 2>&1 && return 0
    sleep 1
  done
  return 1
}

paso() { # nombre
  n=$((n + 1))
  local base
  base=$(printf '%02d-%s' "$n" "$1")
  adb exec-out screencap -p > "$OUT/$base.png" 2>/dev/null
  volcar "$OUT/$base.xml"
  python3 "$UI" texts "$OUT/$base.xml" > "$OUT/$base.txt" 2>&1
  echo "===== $base"
  head -c 4000 "$OUT/$base.txt"
  echo
}

toca() { # patrón
  volcar /tmp/ui.xml
  local xy
  xy=$(python3 "$UI" find /tmp/ui.xml "$1") || { echo "no encuentro: $1"; return 1; }
  echo "toco '$1' en $xy"
  adb shell input tap $xy
}

espera() { # patrón segundos
  local fin=$(( $(date +%s) + $2 ))
  while [ "$(date +%s)" -lt "$fin" ]; do
    volcar /tmp/ui.xml && python3 "$UI" find /tmp/ui.xml "$1" >/dev/null && return 0
    sleep 2
  done
  echo "no apareció a tiempo: $1"
  return 1
}

adb logcat -c
adb install -r "$APK"
adb shell am start -W -n "$PKG/.MainActivity" --ez depurar_web true
sleep 8
paso inicio

toca '^Iniciar sesión en X$'
espera '(?i)phone, email|email or username|teléfono, correo|correo o usuario|^(Next|Siguiente|Continue|Continuar)$' 90
sleep 4
paso formulario

# Herramientas de Chrome contra la web del login: lo que pinta la propia web y por qué.
devtools() {
  local pid sock
  pid=$(adb shell pidof "$PKG" | tr -d '\r')
  sock=$(adb shell cat /proc/net/unix | grep -o "webview_devtools_remote_$pid" | head -1)
  [ -n "$sock" ] || { echo "sin socket de DevTools (pid $pid)"; return 1; }
  adb forward tcp:9222 "localabstract:$sock" >/dev/null && echo "DevTools en $sock"
}
if devtools; then
  python3 .github/scripts/cdp.py shot 9222 "$OUT/web-formulario.png"
  python3 .github/scripts/cdp.py eval 9222 .github/scripts/diag.js > "$OUT/diag-formulario.json" 2>&1
  echo "===== diag-formulario"; head -c 6000 "$OUT/diag-formulario.json"; echo
  python3 .github/scripts/cdp.py watch 9222 75 "$OUT/red-correo.txt" &
  WATCH=$!
fi

if toca 'class=android.widget.EditText'; then
  sleep 2
  adb shell input text "$EMAIL"
  sleep 2
fi
paso correo-escrito

# Como haría una persona: Intro en el teclado; si sigue en el mismo paso, se toca el botón Continuar
# con el teclado ya cerrado (con el teclado abierto el botón queda casi tapado).
adb shell input keyevent 66
sleep 10
paso tras-intro
if volcar /tmp/ui.xml && python3 "$UI" find /tmp/ui.xml '(?i)^email or username$' >/dev/null; then
  echo "sigue en el paso del correo: cierro el teclado y toco Continuar"
  adb shell input keyevent 111
  sleep 3
  toca '^(Next|Siguiente|Continue|Continuar)$'
  sleep 12
  paso tras-continuar
fi

sleep 10
paso diez-segundos-despues
[ -n "${WATCH:-}" ] && wait "$WATCH"
[ -f "$OUT/red-correo.txt" ] && { echo "===== red-correo"; head -c 8000 "$OUT/red-correo.txt"; echo; }
devtools && python3 .github/scripts/cdp.py shot 9222 "$OUT/web-final.png"

# Lo que la app apunta en su registro (también va a logcat con la etiqueta Pliegue).
adb logcat -d -s Pliegue:I > "$OUT/registro-app.txt" 2>&1
adb logcat -d | grep -iE 'chromium|cr_|webview' | tail -300 > "$OUT/logcat-webview.txt" 2>&1
echo "===== registro de la app"
cat "$OUT/registro-app.txt" | tail -80
exit 0
