"""Habla con la web de Pliegue por las herramientas de Chrome (DevTools) desde el Android virtual.

  cdp.py shot PUERTO salida.png        captura de lo que pinta la propia web
  cdp.py eval PUERTO script.js         ejecuta el script y escribe lo que devuelve
  cdp.py watch PUERTO SEGUNDOS salida  apunta las peticiones a X y los mensajes de consola
  cdp.py inyectar PUERTO archivo.json NOMBRE [parte-de-la-url] [variables]
                                       le da a la app una respuesta de X de ejemplo como si la web la hubiera recibido

Los textos largos sin espacios (fichas de sesión y similares) se tapan con "…".
"""
import base64
import json
import re
import sys
import time
import urllib.parse
import urllib.request

import websocket


def red(s):
    return re.sub(r'[A-Za-z0-9_\-.%]{40,}', '…', str(s))


def target(port, part=None):
    pages = json.load(urllib.request.urlopen(f'http://127.0.0.1:{port}/json', timeout=10))
    pages = [p for p in pages if p.get('type') == 'page' and 'webSocketDebuggerUrl' in p]
    if part:
        pages = [p for p in pages if part in p.get('url', '')] or pages
    xs = [p for p in pages if 'x.com' in p.get('url', '')]
    return (xs or pages)[0]


class CDP:
    def __init__(self, port, part=None):
        t = target(port, part)
        print('página:', t.get('url'), file=sys.stderr)
        self.ws = websocket.create_connection(t['webSocketDebuggerUrl'], timeout=30, suppress_origin=True)
        self.n = 0
        self.events = []

    def call(self, method, **params):
        self.n += 1
        mid = self.n
        self.ws.send(json.dumps({'id': mid, 'method': method, 'params': params}))
        while True:
            m = json.loads(self.ws.recv())
            if m.get('id') == mid:
                if 'error' in m:
                    raise RuntimeError(f"{method}: {m['error']}")
                return m.get('result', {})
            self.events.append(m)

    def recv(self, timeout):
        self.ws.settimeout(timeout)
        try:
            return json.loads(self.ws.recv())
        except websocket.WebSocketTimeoutException:
            return None


def shot(port, out):
    c = CDP(port)
    r = c.call('Page.captureScreenshot', format='png')
    with open(out, 'wb') as f:
        f.write(base64.b64decode(r['data']))
    print('captura de la web guardada en', out)


def evaluate(port, path):
    c = CDP(port)
    with open(path, encoding='utf-8') as f:
        expr = f.read()
    r = c.call('Runtime.evaluate', expression=expr, returnByValue=True, awaitPromise=True)
    if 'exceptionDetails' in r:
        print('excepción:', json.dumps(r['exceptionDetails'])[:2000])
    v = r.get('result', {}).get('value')
    try:
        print(json.dumps(json.loads(v), indent=1, ensure_ascii=False))
    except Exception:
        print(v)


def fake_session(port):
    """Cookie de sesión FALSA (no vale para X) solo para que la app pase a su modo con sesión en el
    Android virtual y se puedan probar plegar y desplegar. No es de ninguna cuenta."""
    c = CDP(port)
    for name, value, http_only in (('auth_token', 'prueba-falsa-del-emulador', True), ('twid', 'u%3D1', False)):
        r = c.call('Network.setCookie', name=name, value=value, domain='.x.com', path='/', secure=True, httpOnly=http_only)
        print('cookie', name, r.get('success'))
    # La web de X recarga el formulario al ver la cookie y su servidor la borra por falsa antes de que la
    # app la mire. Una página en blanco no pasa por X: la app mira las cookies al acabar de cargarla.
    c.call('Page.navigate', url='about:blank')


def inject(port, path, name, part=None, variables='{}'):
    """Llama a Pliegue.data en la web de la ranura elegida con una respuesta de ejemplo (las de las
    pruebas de la app). Así se ve la interfaz propia en el Android virtual, donde X no deja entrar."""
    c = CDP(port, part)
    with open(path, encoding='utf-8') as f:
        body = f.read()
    url = 'https://x.com/i/api/graphql/prueba/' + name + '?variables=' + urllib.parse.quote(variables, safe='')
    expr = 'window.Pliegue && window.Pliegue.data ? (window.Pliegue.data(%s, %s, %s), "ok") : "sin puente"' % (json.dumps(name), json.dumps(url), json.dumps(body))
    r = c.call('Runtime.evaluate', expression=expr, returnByValue=True)
    print('inyectado', name, '→', r.get('result', {}).get('value'), r.get('exceptionDetails', {}).get('text', ''))


def evaluate_in(port, part, path):
    """Ejecuta un archivo JS en la web cuya dirección contiene `part` (pruebas: montar un post falso
    con los botones de X en la web escondida de acciones, donde X no deja entrar)."""
    c = CDP(port, part)
    with open(path, encoding='utf-8') as f:
        expr = f.read()
    r = c.call('Runtime.evaluate', expression=expr, returnByValue=True)
    print('evaluado en', part, '→', r.get('result', {}).get('value'), r.get('exceptionDetails', {}).get('text', ''))


def watch(port, seconds, out):
    c = CDP(port)
    c.call('Network.enable')
    c.call('Runtime.enable')
    c.call('Log.enable')
    reqs = {}
    lines = []
    end = time.time() + float(seconds)
    while time.time() < end:
        try:
            m = c.recv(max(0.1, end - time.time()))
        except Exception as e:  # la WebView se cerró (por ejemplo, al salir del login)
            lines.append(f"{time.strftime('%H:%M:%S')} fin: {e}")
            break
        if not m:
            continue
        meth, p = m.get('method'), m.get('params', {})
        n0 = len(lines)
        if meth == 'Network.requestWillBeSent':
            u = p['request']['url']
            if re.search(r'(^|\.)x\.com|twitter\.com|google\.com', u.split('/')[2] if '//' in u else ''):
                reqs[p['requestId']] = u
                body = p['request'].get('postData', '')
                lines.append(f"pide {p['request']['method']} {red(u)[:200]} {red(body)[:300]}")
        elif meth == 'Network.responseReceived' and p['requestId'] in reqs:
            lines.append(f"responde {p['response']['status']} {red(reqs[p['requestId']])[:200]}")
        elif meth == 'Network.loadingFinished' and p['requestId'] in reqs:
            u = reqs[p['requestId']]
            if re.search(r'onboarding|login|sso|task|flow', u):
                try:
                    b = c.call('Network.getResponseBody', requestId=p['requestId'])
                    body = b.get('body', '')
                    if b.get('base64Encoded'):
                        body = base64.b64decode(body).decode('utf-8', 'replace')
                    txt = ''.join(ch if ch.isprintable() else ' ' for ch in body)
                    lines.append(f"  cuerpo {red(u)[:120]}: {red(txt)[:1500]}")
                except Exception as e:
                    lines.append(f"  sin cuerpo: {e}")
        elif meth == 'Network.loadingFailed' and p['requestId'] in reqs:
            lines.append(f"falla {red(reqs[p['requestId']])[:200]}: {p.get('errorText')} {p.get('blockedReason', '')}")
        elif meth == 'Runtime.consoleAPICalled':
            args = ' '.join(str(a.get('value', a.get('description', ''))) for a in p.get('args', []))
            lines.append(f"consola {p.get('type')}: {red(args)[:400]}")
        elif meth == 'Runtime.exceptionThrown':
            d = p.get('exceptionDetails', {})
            lines.append(f"excepción: {red(d.get('exception', {}).get('description', d.get('text')))[:600]}")
        elif meth == 'Log.entryAdded':
            e = p.get('entry', {})
            lines.append(f"aviso {e.get('level')}: {red(e.get('text'))[:400]} {e.get('url', '')[:100]}")
        stamp = time.strftime('%H:%M:%S')
        lines[n0:] = [f"{stamp} {l}" for l in lines[n0:]]
    with open(out, 'w', encoding='utf-8') as f:
        f.write('\n'.join(lines) + '\n')
    print(f'{len(lines)} líneas de red y consola en {out}')


if __name__ == '__main__':
    cmd = sys.argv[1]
    try:
        if cmd == 'shot':
            shot(sys.argv[2], sys.argv[3])
        elif cmd == 'eval':
            evaluate(sys.argv[2], sys.argv[3])
        elif cmd == 'sesion-falsa':
            fake_session(sys.argv[2])
        elif cmd == 'watch':
            watch(sys.argv[2], sys.argv[3], sys.argv[4])
        elif cmd == 'evaluar':
            evaluate_in(sys.argv[2], sys.argv[3], sys.argv[4])
        elif cmd == 'inyectar':
            inject(sys.argv[2], sys.argv[3], sys.argv[4], sys.argv[5] if len(sys.argv) > 5 else None, sys.argv[6] if len(sys.argv) > 6 else '{}')
    except Exception as e:
        print('cdp falló:', e)
