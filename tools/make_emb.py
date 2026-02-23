import sys
import struct

def make_emb(text, dim=384, out='tmp.emb'):
    h = 0
    for ch in text:
        h = (h * 31 + ord(ch)) & 0xFFFFFFFF
    floats = []
    for i in range(dim):
        h = (h * 31 + i) & 0xFFFFFFFF
        val = float(h & 0xffff) / 65536.0
        floats.append(val)
    with open(out, 'wb') as f:
        for v in floats:
            f.write(struct.pack('<f', v))

if __name__ == '__main__':
    if len(sys.argv) < 3:
        print('Usage: make_emb.py "text" out.emb [dim]')
        sys.exit(2)
    text = sys.argv[1]
    out = sys.argv[2]
    dim = int(sys.argv[3]) if len(sys.argv) > 3 else 384
    make_emb(text, dim, out)
    print('wrote', out)
