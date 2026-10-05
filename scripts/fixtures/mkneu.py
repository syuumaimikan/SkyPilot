# Inputs are read from $SKYPILOT_FIXTURE_INPUT (default: ./input next to this script);
# outputs go to <repo>/fixtures. See scripts/fixtures/README.md.
import json, os, shutil, glob, gzip, re

SP = os.environ.get('SKYPILOT_FIXTURE_INPUT', os.path.join(os.path.dirname(os.path.abspath(__file__)), 'input'))
REPO = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..'))
NEU = os.path.join(SP, 'neu')
OUT = os.path.join(REPO, 'fixtures', 'neu')
os.makedirs(OUT + '/constants', exist_ok=True)
os.makedirs(OUT + '/items', exist_ok=True)

for c in ['leveling', 'pets', 'hotmlayout', 'hotflayout', 'misc', 'parents', 'essencecosts', 'garden', 'reforgestones', 'sacks', 'museum', 'petnums']:
    shutil.copy(os.path.join(NEU, 'constants', c + '.json'), OUT + '/constants/' + c + '.json')
shutil.copy(os.path.join(NEU, 'LICENSE'), OUT + '/LICENSE')


def load(i):
    p = os.path.join(NEU, 'items', i + '.json')
    if not os.path.exists(p):
        return None
    return json.load(open(p, encoding='utf-8'))


wanted = set()
for f in glob.glob(os.path.join(NEU, 'items', '*_BOSS.json')):
    wanted.add(os.path.basename(f)[:-5])
for i in range(1, 13):
    wanted.add(f'COBBLESTONE_GENERATOR_{i}')
    wanted.add(f'MITHRIL_GENERATOR_{i}')
    wanted.add(f'MELON_GENERATOR_{i}')

targets = ['HYPERION', 'ENCHANTED_MELON_BLOCK', 'ENCHANTED_SUGAR_CANE', 'ENCHANTED_BAKED_POTATO', 'MUTANT_NETHER_STALK',
           'ENCHANTED_HAY_BLOCK', 'POLISHED_PUMPKIN', 'ENCHANTED_GOLDEN_CARROT', 'REFINED_MITHRIL', 'TITANIUM_DRILL_1',
           'DIVAN_DRILL', 'MITHRIL_PLATE', 'GEMSTONE_MIXTURE', 'TERMINATOR', 'JUJU_SHORTBOW', 'ASPECT_OF_THE_VOID',
           'RECOMBOBULATOR_3000', 'FUMING_POTATO_BOOK', 'HOT_POTATO_BOOK', 'GOLDEN_PLATE', 'AMBER_MATERIAL',
           'PERFECT_SAPPHIRE_GEM', 'FLAWLESS_SAPPHIRE_GEM', 'ENCHANTED_DIAMOND_BLOCK', 'SINFUL_DICE', 'NECRON_BLADE']


def ingredients(d):
    out = set()
    r = d.get('recipe')
    if isinstance(r, dict):
        for k, v in r.items():
            if isinstance(v, str) and v:
                out.add(v.split(':')[0])
    for rec in d.get('recipes', []):
        t = rec.get('type')
        if t == 'crafting':
            for k, v in rec.items():
                if re.match(r'^[A-C][1-3]$', k) and v:
                    out.add(v.split(':')[0])
        elif t == 'forge':
            for v in rec.get('inputs', []):
                out.add(v.split(':')[0])
        elif t == 'katgrade':
            out.add(rec.get('input', '').split(':')[0])
            for v in rec.get('items', []):
                out.add(v.split(':')[0])
    return out


stack = [(t, 0) for t in targets]
while stack:
    t, depth = stack.pop()
    if t in wanted:
        continue
    d = load(t)
    if d is None:
        continue
    wanted.add(t)
    if depth < 6:
        for ing in ingredients(d):
            if ing and ing != 'SKYBLOCK_COIN':
                stack.append((ing, depth + 1))

forge = kat = 0
for f in sorted(glob.glob(os.path.join(NEU, 'items', '*.json'))):
    d = json.load(open(f, encoding='utf-8'))
    types = {r.get('type') for r in d.get('recipes', [])}
    name = os.path.basename(f)[:-5]
    if 'forge' in types and forge < 40:
        wanted.add(name)
        forge += 1
        for ing in ingredients(d):
            if load(ing):
                wanted.add(ing)
    if 'katgrade' in types and kat < 25 and name.startswith(('ENDER_DRAGON', 'GOLDEN_DRAGON', 'BAL', 'SCATHA', 'MOOSHROOM_COW', 'ELEPHANT', 'BEE', 'GRIFFIN', 'BLAZE', 'ARMADILLO')):
        wanted.add(name)
        kat += 1
        for ing in ingredients(d):
            if load(ing):
                wanted.add(ing)

for w in sorted(wanted):
    src = os.path.join(NEU, 'items', w + '.json')
    if os.path.exists(src):
        shutil.copy(src, OUT + '/items/' + w + '.json')
print('neu items', len(os.listdir(OUT + '/items')), 'forge', forge, 'kat', kat)

# full Hypixel items resource, gzipped (5 MB -> small) for registry tests
src = os.path.join(SP, 'live/resources_skyblock_items.json')
with open(src, 'rb') as fi, gzip.open(os.path.join(REPO, 'fixtures', 'hypixel', 'resources_items.json.gz'), 'wb', compresslevel=9) as fo:
    fo.write(fi.read())
print('items gz', os.path.getsize(os.path.join(REPO, 'fixtures', 'hypixel', 'resources_items.json.gz')))
