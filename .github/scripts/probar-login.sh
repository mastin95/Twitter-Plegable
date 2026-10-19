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
# Cuenta pública de X (la de desarrolladores) solo para ver que el formulario pide la contraseña.
USUARIO="XDevelopers"
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
  python3 .github/scripts/cdp.py watch 9222 110 "$OUT/red-correo.txt" &
  WATCH=$!
fi

# Escribe en el campo del formulario y comprueba que el texto se queda (si la página se recargara,
# el campo volvería vacío). Hasta tres intentos.
escribir() { # texto
  local intento
  for intento in 1 2 3; do
    espera 'class=android.widget.EditText' 30 || continue
    toca 'class=android.widget.EditText' || continue
    sleep 2
    adb shell input text "$1"
    sleep 3
    volcar /tmp/ui.xml
    if python3 "$UI" fields /tmp/ui.xml | grep -qF "$1"; then echo "escrito y sigue en el campo: $1"; return 0; fi
    echo "el campo no conservó el texto (intento $intento)"
  done
  return 1
}

# Envía el paso con Intro, como en el teclado del móvil; si no avanza, cierra el teclado y toca el botón.
enviar() { # patrón del paso siguiente, nombre del paso
  adb shell input keyevent 66
  sleep 2
  paso "$2-al-momento"
  espera "$1" 25 && return 0
  echo "Intro no avanzó: cierro el teclado y toco el botón"
  adb shell input keyevent 111
  sleep 2
  toca '^(Next|Siguiente|Continue|Continuar|Log in|Iniciar sesión)$'
  espera "$1" 25
}

SIGUIENTE='(?i)could not find|couldn.t find|no hemos podido encontrar|password|contraseña|phone number or username|unusual|verify|verific|try again|inténtalo'

# 1) El formulario no debe recargarse solo: se espera 20 s y el campo tiene que seguir conservando lo escrito.
sleep 20
paso formulario-20-segundos
escribir "$EMAIL"
paso correo-escrito
sleep 10
paso correo-diez-segundos-despues
python3 "$UI" fields "$OUT/$(printf '%02d' "$n")-correo-diez-segundos-despues.xml"
enviar "$SIGUIENTE" correo
sleep 3
paso tras-enviar-correo

# 2) Con un usuario público que sí existe, X debe pasar a pedir la contraseña. Aquí se para: no se
# escribe ninguna contraseña. Se sale del login y se vuelve a entrar, como haría una persona.
adb shell input keyevent 111
sleep 1
toca '^Atrás$'
sleep 3
toca '^Iniciar sesión en X$'
espera 'class=android.widget.EditText' 90
sleep 6
[ -n "${WATCH:-}" ] && wait "$WATCH"
if devtools; then
  python3 .github/scripts/cdp.py watch 9222 75 "$OUT/red-usuario.txt" &
  WATCH=$!
fi
escribir "$USUARIO"
paso usuario-escrito
enviar '(?i)password|contraseña' usuario
sleep 3
paso tras-enviar-usuario
volcar /tmp/ui.xml && python3 "$UI" fields /tmp/ui.xml

sleep 10
paso diez-segundos-despues

# 3) El botón Recargar de la barra sobre el formulario de X: debe volver a pintarlo, no dejarlo en negro.
adb shell input keyevent 111
toca '^Recargar$'
espera 'class=android.widget.EditText' 45
sleep 5
paso tras-recargar
[ -n "${WATCH:-}" ] && wait "$WATCH"
for f in red-correo red-usuario; do
  [ -f "$OUT/$f.txt" ] && { echo "===== $f"; grep -v 'cuerpo https://jf.x.com/onboarding/web?mode' "$OUT/$f.txt" | head -c 8000; echo; }
done
devtools && python3 .github/scripts/cdp.py shot 9222 "$OUT/web-final.png"

# Lo que la app apunta en su registro (también va a logcat con la etiqueta Pliegue).
adb logcat -d -s Pliegue:I > "$OUT/registro-app.txt" 2>&1
echo "===== cargas de x.com/i/flow/login (deben ser 2: una por cada vez que se abre el login): $(grep -c 'carga x.com/i/flow/login' "$OUT/registro-app.txt")"
adb logcat -d | grep -iE 'chromium|cr_|webview' | tail -300 > "$OUT/logcat-webview.txt" 2>&1
echo "===== registro de la app"
cat "$OUT/registro-app.txt" | tail -80
exit 0
