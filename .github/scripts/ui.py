"""Lee el volcado de uiautomator: lista lo que hay en pantalla o da el centro de un elemento."""
import re
import sys
import xml.etree.ElementTree as ET


def nodes(path):
    try:
        return list(ET.parse(path).getroot().iter('node'))
    except Exception as e:  # volcado a medias mientras la web se mueve
        print('volcado ilegible:', e, file=sys.stderr)
        return []


def label(n):
    return (n.get('text') or n.get('content-desc') or '').strip()


def center(bounds):
    m = re.match(r'\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]', bounds or '')
    if not m:
        return None
    x1, y1, x2, y2 = map(int, m.groups())
    if x2 <= x1 or y2 <= y1:
        return None
    return (x1 + x2) // 2, (y1 + y2) // 2


def main():
    cmd, path = sys.argv[1], sys.argv[2]
    ns = nodes(path)
    if cmd == 'texts':
        for n in ns:
            cls = n.get('class', '').split('.')[-1]
            t = label(n)
            if t or cls == 'EditText':
                extra = ' [foco]' if n.get('focused') == 'true' else ''
                print(f"{cls} {n.get('bounds')} {t[:120]!r}{extra}")
        return 0
    if cmd == 'find':  # find volcado.xml patrón [x-mínima]: el primer nodo que case (a la derecha de x-mínima, si se da)
        pat = sys.argv[3]
        minx = int(sys.argv[4]) if len(sys.argv) > 4 else 0
        if pat.startswith('class='):
            cand = [n for n in ns if n.get('class') == pat[6:]]
        else:
            rx = re.compile(pat)
            cand = [n for n in ns if rx.search(label(n))]
        for n in cand:
            c = center(n.get('bounds'))
            if c and c[0] >= minx:
                print(c[0], c[1])
                return 0
        return 1
    if cmd == 'fields':  # campos de texto: lo escrito (o [contraseña] si es de contraseña) y si tiene el foco
        for n in ns:
            if n.get('class') == 'android.widget.EditText':
                kind = 'contraseña' if n.get('password') == 'true' else 'texto'
                print(f"{kind} {(n.get('text') or '')!r}{' [foco]' if n.get('focused') == 'true' else ''}")
        return 0
    return 2


if __name__ == '__main__':
    sys.exit(main())
