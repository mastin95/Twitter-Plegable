"""Prueba los estilos que la app pone en la web de X: abre un perfil público en Chromium al ancho de
una mitad del plegable, mide la columna de iconos y la columna de posts, y guarda capturas antes y
después de aplicar RAIL_CSS (leído de XWeb.kt, el mismo texto que usa la app)."""
import json
import re
import sys

from playwright.sync_api import sync_playwright

src = open('app/src/main/java/io/github/mastin95/pliegue/web/XWeb.kt', encoding='utf-8').read()
css = re.search(r'const val RAIL_CSS = """(.*?)"""', src).group(1)
UA = ('Mozilla/5.0 (Linux; Android 15; SM-F956B) AppleWebKit/537.36 (KHTML, like Gecko) '
      'Chrome/141.0.0.0 Mobile Safari/537.36')
MEDIR = """() => {
  const r = s => { const e = document.querySelector(s); if (!e) return null;
    const b = e.getBoundingClientRect(); return {x: Math.round(b.x), ancho: Math.round(b.width)}; };
  return {url: location.href, ancho: innerWidth, iconos: r('header[role="banner"]'),
          main: r('main[role="main"]'), posts: r('[data-testid="primaryColumn"]')};
}"""
out = sys.argv[1]
print('estilo:', css)
with sync_playwright() as p:
    b = p.chromium.launch()
    for w in (560, 420):
        ctx = b.new_context(viewport={'width': w, 'height': 900}, user_agent=UA, is_mobile=True,
                            has_touch=True, device_scale_factor=2, color_scheme='dark')
        pg = ctx.new_page()
        pg.goto('https://x.com/XDevelopers', wait_until='domcontentloaded', timeout=60000)
        pg.wait_for_timeout(9000)
        print(w, 'antes  ', json.dumps(pg.evaluate(MEDIR), ensure_ascii=False))
        pg.screenshot(path=f'{out}/{w}-antes.png')
        pg.add_style_tag(content=css)
        pg.wait_for_timeout(1500)
        print(w, 'después', json.dumps(pg.evaluate(MEDIR), ensure_ascii=False))
        pg.screenshot(path=f'{out}/{w}-despues.png')
        ctx.close()
    b.close()
