#!/usr/bin/env bash
# Prueba en el Android virtual el estilo que esconde la columna de iconos de X (XWeb.RAIL_CSS) con la
# WebView de verdad: pantalla ancha como una mitad abierta del plegable, una página pública de X sin
# sesión, medidas y capturas antes y después de poner el estilo.
set -u
OUT="${GITHUB_WORKSPACE:-$PWD}/estilos-out"
mkdir -p "$OUT"
APK=app/build/outputs/apk/debug/app-debug.apk
PKG=io.github.mastin95.pliegue
UI=.github/scripts/ui.py

volcar() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 && adb pull /sdcard/ui.xml "$1" >/dev/null 2>&1; }
toca() {
  volcar /tmp/ui.xml
  local xy
  xy=$(python3 "$UI" find /tmp/ui.xml "$1") || { echo "no encuentro: $1"; return 1; }
  adb shell input tap $xy
}

# CSS sacado del código de la app, envuelto en un script que lo pone en la página.
CSS=$(sed -n 's/.*const val RAIL_CSS = """\(.*\)""".*/\1/p' app/src/main/java/io/github/mastin95/pliegue/web/XWeb.kt)
echo "estilo: $CSS"
python3 - "$CSS" > /tmp/poner-estilo.js <<'PY'
import json, sys
print("(function(){var s=document.createElement('style');s.textContent=%s;document.head.appendChild(s);return 'puesto';})();" % json.dumps(sys.argv[1]))
PY

# Unos 860 px de ancho en la web, como una mitad del plegable abierto con la letra pequeña.
adb shell wm density 200
adb install -r "$APK"
adb shell am start -W -n "$PKG/.MainActivity" --ez depurar_web true
sleep 8
toca '^Iniciar sesión en X$'
sleep 15
pid=$(adb shell pidof "$PKG" | tr -d '\r')
sock=$(adb shell cat /proc/net/unix | grep -o "webview_devtools_remote_$pid" | head -1)
adb forward tcp:9222 "localabstract:$sock"

for url in https://x.com/explore https://x.com/XDevelopers; do
  name=$(basename "$url")
  python3 .github/scripts/cdp.py nav 9222 "$url"
  sleep 15
  echo "===== $name antes"
  python3 .github/scripts/cdp.py eval 9222 .github/scripts/medir-columnas.js | tee "$OUT/$name-antes.json"
  python3 .github/scripts/cdp.py shot 9222 "$OUT/$name-antes.png"
  python3 .github/scripts/cdp.py eval 9222 /tmp/poner-estilo.js
  sleep 2
  echo "===== $name después"
  python3 .github/scripts/cdp.py eval 9222 .github/scripts/medir-columnas.js | tee "$OUT/$name-despues.json"
  python3 .github/scripts/cdp.py shot 9222 "$OUT/$name-despues.png"
done
adb shell wm density reset
exit 0
