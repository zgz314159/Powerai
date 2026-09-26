# -*- coding: utf-8 -*-
import pathlib

root = pathlib.Path(r"feature\search-chat\src\main\java\com\example\powerai\feature\searchchat")
for p in sorted(root.glob("*.kt")):
    data = p.read_bytes()
    ok = None
    for enc in ("utf-8", "utf-8-sig", "gb18030", "gbk", "utf-16", "utf-16-le"):
        try:
            data.decode(enc)
            ok = enc
            break
        except Exception:
            pass
    print(f"{p.name}: {ok or 'UNKNOWN'}, size={len(data)}, head={data[:24]!r}")
