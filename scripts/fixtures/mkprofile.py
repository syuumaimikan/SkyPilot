# Inputs are read from $SKYPILOT_FIXTURE_INPUT (default: ./input next to this script);
# outputs go to <repo>/fixtures. See scripts/fixtures/README.md.
"""Anonymise the MIT-licensed SkyHelper-Networth-Go test profile into a SkyPilot fixture.

Replaces every UUID with a stable hash, renames people, and rewrites inventory NBT
blobs (typed round-trip) so names inside item data are anonymised too.
"""
import json, base64, gzip, hashlib, struct, re, os, io

SP = os.environ.get('SKYPILOT_FIXTURE_INPUT', os.path.join(os.path.dirname(os.path.abspath(__file__)), 'input'))
REPO = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..'))
OUT = os.path.join(REPO, 'fixtures', 'hypixel', 'profiles_sample.json')
NAMES = {"DeathStreeks": "SampleUser", "Dctr": "AdminUser"}
UUID_RE = re.compile(r"\b[0-9a-f]{8}-?[0-9a-f]{4}-?[0-9a-f]{4}-?[0-9a-f]{4}-?[0-9a-f]{12}\b")


def anon_uuid(u):
    dashed = '-' in u
    h = hashlib.sha256(("skypilot-fixture:" + u.replace('-', '').lower()).encode()).hexdigest()[:32]
    if dashed:
        return f"{h[:8]}-{h[8:12]}-{h[12:16]}-{h[16:20]}-{h[20:]}"
    return h


def anon_text(s):
    for a, b in NAMES.items():
        s = s.replace(a, b)
    return UUID_RE.sub(lambda m: anon_uuid(m.group(0)), s)


# ---- typed NBT round trip
def read_nbt(b):
    pos = [0]

    def take(n):
        s = b[pos[0]:pos[0] + n]
        pos[0] += n
        return s

    def rstr():
        n = struct.unpack('>H', take(2))[0]
        return take(n)

    def pl(t):
        if t == 1: return take(1)
        if t == 2: return take(2)
        if t == 3: return take(4)
        if t == 4: return take(8)
        if t == 5: return take(4)
        if t == 6: return take(8)
        if t == 7:
            n = struct.unpack('>i', take(4))[0]
            return take(n)
        if t == 8: return rstr()
        if t == 9:
            et = take(1)[0]
            n = struct.unpack('>i', take(4))[0]
            return (et, [pl(et) for _ in range(n)])
        if t == 10:
            items = []
            while True:
                tt = take(1)[0]
                if tt == 0:
                    break
                k = rstr()
                items.append((tt, k, pl(tt)))
            return items
        if t == 11:
            n = struct.unpack('>i', take(4))[0]
            return take(4 * n)
        if t == 12:
            n = struct.unpack('>i', take(4))[0]
            return take(8 * n)
        raise ValueError(t)

    t = take(1)[0]
    name = rstr()
    return t, name, pl(t)


def write_nbt(t, name, payload):
    out = io.BytesIO()

    def wstr(bs):
        out.write(struct.pack('>H', len(bs)))
        out.write(bs)

    def wp(t, p):
        if t in (1, 2, 3, 4, 5, 6):
            out.write(p)
        elif t == 7:
            out.write(struct.pack('>i', len(p)))
            out.write(p)
        elif t == 8:
            wstr(p)
        elif t == 9:
            et, items = p
            out.write(bytes([et]))
            out.write(struct.pack('>i', len(items)))
            for x in items:
                wp(et, x)
        elif t == 10:
            for tt, k, v in p:
                out.write(bytes([tt]))
                wstr(k)
                wp(tt, v)
            out.write(b'\x00')
        elif t == 11:
            out.write(struct.pack('>i', len(p) // 4))
            out.write(p)
        elif t == 12:
            out.write(struct.pack('>i', len(p) // 8))
            out.write(p)

    out.write(bytes([t]))
    wstr(name)
    wp(t, payload)
    return out.getvalue()


def anon_payload(t, p):
    if t == 8:
        try:
            s = p.decode('utf-8')
        except UnicodeDecodeError:
            return p
        return anon_text(s).encode('utf-8')
    if t == 9:
        et, items = p
        return (et, [anon_payload(et, x) for x in items])
    if t == 10:
        return [(tt, k, anon_payload(tt, v)) for tt, k, v in p]
    return p


def anon_blob(b64):
    raw = gzip.decompress(base64.b64decode(b64))
    t, name, payload = read_nbt(raw)
    payload = anon_payload(t, payload)
    data = write_nbt(t, name, payload)
    assert read_nbt(data)[0] == t
    return base64.b64encode(gzip.compress(data, mtime=0)).decode()


def walk(o):
    if isinstance(o, dict):
        new = {}
        for k, v in o.items():
            nk = anon_text(k) if UUID_RE.search(k) else k
            if k == 'data' and isinstance(v, str) and v.startswith('H4sI'):
                new[nk] = anon_blob(v)
            elif k in ('display_name', 'initiator_name', 'invited_by') and isinstance(v, str):
                new[nk] = 'SampleUser' if v else v
            else:
                new[nk] = walk(v)
        return new
    if isinstance(o, list):
        return [walk(x) for x in o]
    if isinstance(o, str):
        return anon_text(o)
    return o


src = json.load(open(os.path.join(SP, 'snw/tests/test_profile.json'), encoding='utf-8'))
profile = walk(src)
# Present it the way /v2/skyblock/profiles returns it.
doc = {"success": True, "profiles": [profile]}
json.dump(doc, open(OUT, 'w', encoding='utf-8'), ensure_ascii=False)
print('written', os.path.getsize(OUT), 'member', list(profile['members'].keys()))
museum_src = os.path.join(SP, 'snw/tests/test_museum.json')
if os.path.exists(museum_src):
    mus = walk(json.load(open(museum_src, encoding='utf-8')))
    json.dump(mus, open(OUT.replace('profiles_sample', 'museum_sample'), 'w', encoding='utf-8'), ensure_ascii=False)
    print('museum keys', list(mus.keys())[:5])
