"""Habla con la web de Pliegue por las herramientas de Chrome (DevTools) desde el Android virtual.

  cdp.py shot PUERTO salida.png        captura de lo que pinta la propia web
  cdp.py eval PUERTO script.js         ejecuta el script y escribe lo que devuelve
  cdp.py watch PUERTO SEGUNDOS salida  apunta las peticiones a X y los mensajes de consola

Los textos largos sin espacios (fichas de sesión y similares) se tapan con "…".
"""
import base64
import json
import re
import sys
import time
import urllib.request

import websocket


def red(s):
    return re.sub(r'[A-Za-z0-9_\-.%]{40,}', '…', str(s))


def target(port):
    pages = json.load(urllib.request.urlopen(f'http://127.0.0.1:{port}/json', timeout=10))
    pages = [p for p in pages if p.get('type') == 'page' and 'webSocketDebuggerUrl' in p]
    xs = [p for p in pages if 'x.com' in p.get('url', '')]
    return (xs or pages)[0]


class CDP:
    def __init__(self, port):
        t = target(port)
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
        elif cmd == 'watch':
            watch(sys.argv[2], sys.argv[3], sys.argv[4])
    except Exception as e:
        print('cdp falló:', e)
