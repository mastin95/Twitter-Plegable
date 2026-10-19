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
adb shell am start -W -n "$PKG/.MainActivity"
sleep 8
paso inicio

toca '^Iniciar sesión en X$'
espera '(?i)phone, email|email or username|teléfono, correo|correo o usuario|^(Next|Siguiente|Continue|Continuar)$' 90
sleep 4
paso formulario

# Que la app apunte en su registro qué hay en la página (y si el formulario está oculto, por qué).
toca '^Más opciones$' && sleep 2 && toca '^Anotar lo que se ve en el registro$'
sleep 3
paso registro-abierto

if toca 'class=android.widget.EditText'; then
  sleep 2
  adb shell input text "$EMAIL"
  sleep 2
fi
paso correo-escrito

toca '^(Next|Siguiente|Continue|Continuar)$' || adb shell input keyevent 66
sleep 12
paso tras-siguiente

sleep 10
paso diez-segundos-despues

# Lo que la app apunta en su registro (también va a logcat con la etiqueta Pliegue).
adb logcat -d -s Pliegue:I > "$OUT/registro-app.txt" 2>&1
adb logcat -d | grep -iE 'chromium|cr_|webview' | tail -300 > "$OUT/logcat-webview.txt" 2>&1
echo "===== registro de la app"
cat "$OUT/registro-app.txt" | tail -80
exit 0
