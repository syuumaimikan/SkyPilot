# Inputs are read from $SKYPILOT_FIXTURE_INPUT (default: ./input next to this script);
# outputs go to <repo>/fixtures. See scripts/fixtures/README.md.
import json, base64, gzip, hashlib, struct, os, random, re
from collections import Counter

random.seed(7)
SP = os.environ.get('SKYPILOT_FIXTURE_INPUT', os.path.join(os.path.dirname(os.path.abspath(__file__)), 'input'))
REPO = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..'))
OUT = os.path.join(REPO, 'fixtures')


def anon(u):
    if not u:
        return u
    return hashlib.sha256(("skypilot-fixture:" + u.replace('-', '')).encode()).hexdigest()[:32]


def rd(b):
    pos = [0]

    def take(n):
        s = b[pos[0]:pos[0] + n]
        pos[0] += n
        return s

    def s():
        n = struct.unpack('>H', take(2))[0]
        return take(n).decode('utf-8', 'replace')

    def pl(t):
        if t == 1: return struct.unpack('>b', take(1))[0]
        if t == 2: return struct.unpack('>h', take(2))[0]
        if t == 3: return struct.unpack('>i', take(4))[0]
        if t == 4: return struct.unpack('>q', take(8))[0]
        if t == 5: return struct.unpack('>f', take(4))[0]
        if t == 6: return struct.unpack('>d', take(8))[0]
        if t == 7:
            n = struct.unpack('>i', take(4))[0]
            return list(take(n))
        if t == 8: return s()
        if t == 9:
            et = take(1)[0]
            n = struct.unpack('>i', take(4))[0]
            return [pl(et) for _ in range(n)]
        if t == 10:
            d = {}
            while True:
                tt = take(1)[0]
                if tt == 0:
                    break
                k = s()
                d[k] = pl(tt)
            return d
        if t == 11:
            n = struct.unpack('>i', take(4))[0]
            return [struct.unpack('>i', take(4))[0] for _ in range(n)]
        if t == 12:
            n = struct.unpack('>i', take(4))[0]
            return [struct.unpack('>q', take(8))[0] for _ in range(n)]

    t = take(1)[0]
    s()
    return pl(t)


def item_of(au):
    return rd(gzip.decompress(base64.b64decode(au['item_bytes'])))['i'][0]


a = json.load(open(os.path.join(SP, 'live/skyblock_auctions.json'), encoding='utf-8'))
auctions = a['auctions']
buckets = {}
for au in auctions:
    try:
        it = item_of(au)
    except Exception:
        continue
    ea = it.get('tag', {}).get('ExtraAttributes', {})
    iid = ea.get('id', '?')
    keys = set(ea.keys())
    tags = []
    if iid == 'PET': tags.append('pet')
    if iid == 'ENCHANTED_BOOK': tags.append('book')
    if 'gems' in keys: tags.append('gems')
    if ea.get('rarity_upgrades'): tags.append('recomb')
    if 'upgrade_level' in keys: tags.append('stars')
    if 'attributes' in keys: tags.append('attributes')
    if 'hot_potato_count' in keys: tags.append('hpb')
    if 'skin' in keys: tags.append('skin')
    if 'runes' in keys: tags.append('rune')
    if not tags: tags.append('plain')
    for t in tags:
        buckets.setdefault(t, []).append(au)
print({k: len(v) for k, v in buckets.items()})
chosen = {}
for k, v in buckets.items():
    for au in (v[:15] if k != 'plain' else random.sample(v, min(40, len(v)))):
        chosen[au['uuid']] = au

cnt = Counter()
ids = {}
for au in auctions:
    if au.get('bin'):
        try:
            iid = item_of(au).get('tag', {}).get('ExtraAttributes', {}).get('id')
        except Exception:
            continue
        ids[au['uuid']] = iid
        cnt[iid] += 1
common = [i for i, c in cnt.most_common(40) if i and i not in ('PET', 'ENCHANTED_BOOK')][:6]
print('common', common)
per = Counter()
for au in auctions:
    iid = ids.get(au['uuid'])
    if iid in common and per[iid] < 12:
        per[iid] += 1
        chosen[au['uuid']] = au


def clean(au):
    au = json.loads(json.dumps(au))
    au['uuid'] = anon(au['uuid'])
    au['auctioneer'] = anon(au['auctioneer'])
    au['profile_id'] = anon(au['profile_id'])
    au['coop'] = [anon(c) for c in au.get('coop', [])]
    au['claimed_bidders'] = [anon(c) for c in au.get('claimed_bidders', [])]
    for b in au.get('bids', []):
        b['auction_id'] = au['uuid']
        b['bidder'] = anon(b['bidder'])
        b['profile_id'] = anon(b['profile_id'])
    return au


sample = [clean(x) for x in chosen.values()]
out = {k: v for k, v in a.items() if k != 'auctions'}
out.update(page=0, totalPages=1, totalAuctions=len(sample), auctions=sample)
os.makedirs(OUT + '/hypixel', exist_ok=True)
json.dump(out, open(OUT + '/hypixel/auctions_page0.json', 'w', encoding='utf-8'), ensure_ascii=False)
print('auctions sample', len(sample))

e = json.load(open(os.path.join(SP, 'live/skyblock_auctions_ended.json'), encoding='utf-8'))
for x in e['auctions']:
    for k in ('auction_id', 'seller', 'seller_profile', 'buyer', 'buyer_profile'):
        x[k] = anon(x[k])
json.dump(e, open(OUT + '/hypixel/auctions_ended.json', 'w', encoding='utf-8'), ensure_ascii=False)

bz = json.load(open(os.path.join(SP, 'live/skyblock_bazaar.json'), encoding='utf-8'))
keep_re = re.compile(
    r'^(ENCHANTMENT_(ULTIMATE_WISE|SHARPNESS|ULTIMATE_LEGION|GROWTH|PROTECTION)_\d'
    r'|.*_GEM'
    r'|ENCHANTED_[A-Z_]+'
    r'|MELON|PUMPKIN|SUGAR_CANE|CARROT_ITEM|POTATO_ITEM|NETHER_STALK|INK_SACK:3|WHEAT|CACTUS|SEEDS|MUSHROOM_COLLECTION|RED_MUSHROOM|BROWN_MUSHROOM'
    r'|MUTANT_NETHER_STALK|MITHRIL_ORE|TITANIUM_ORE|GLACITE|UMBER|TUNGSTEN|HARD_STONE|COBBLESTONE|COAL|IRON_INGOT|GOLD_INGOT|DIAMOND|EMERALD|REDSTONE|INK_SACK:4'
    r'|NULL_SPHERE|NULL_ATOM|SUMMONING_EYE|JUDGEMENT_CORE|TWILIGHT_ARROW_POISON|TRANSMISSION_TUNER|POCKET_ESPRESSO_MACHINE|HANDY_BLOOD_CHALICE|SINFUL_DICE'
    r'|REVENANT_FLESH|FOUL_FLESH|UNDEAD_CATALYST|REVENANT_CATALYST|BEHEADED_HORROR|SCYTHE_BLADE|REVENANT_VISCERA'
    r'|RECOMBOBULATOR_3000|HOT_POTATO_BOOK|FUMING_POTATO_BOOK|GIANT_FRAGMENT_LASER|WITHER_CATALYST|PLASMA|REFINED_MITHRIL|BOOSTER_COOKIE'
    r'|KISMET_FEATHER|FIRST_MASTER_STAR|SECOND_MASTER_STAR|THIRD_MASTER_STAR|FOURTH_MASTER_STAR|FIFTH_MASTER_STAR|ESSENCE_[A-Z]+'
    r'|GOLDEN_PLATE|DERELICT_ASHE|WOLF_TOOTH|HAMSTER_WHEEL|TARANTULA_WEB|TOXIC_ARROW_POISON|COVEN_SEAL|STOCK_OF_STONKS'
    r'|ENDERSNAKE_RUNE|MANA_STEAL_.*|SMARTY_PANTS.*|DRAGON_RUNE|ETHERWARP_MERGER|EXCEEDINGLY_RARE_ENDER_ARTIFACT_UPGRADER|ENDSTONE_IDOL|HAZMAT_ENDERMAN|PET_SKIN_ENDERMAN_SLAYER'
    r'|ATTRIBUTE_SHARD_SLAYER_DISCOUNT|SPIDER_CATALYST|BITE_RUNE|FLY_SWATTER|VIAL_OF_VENOM|SPIRIT_RUNE|FURBALL|RED_CLAW_EGG|GRIZZLY_BAIT|OVERFLUX_CAPACITOR|COUTURE_RUNE'
    r'|NECRON_HANDLE|DARK_CLAYMORE|FIRST_MASTER_STAR|MITHRIL_PLATE|DIVAN_FRAGMENT|GEMSTONE_MIXTURE|SLUDGE_JUICE|JUNGLE_HEART|ROUGH_.*|FLAWED_.*|FINE_.*'
    r'|FUEL_TANK|BIOFUEL|VOLTA|OIL_BARREL|ENCHANTED_BOOK_BUNDLE_.*|SUNFLOWER|MOONFLOWER|WILD_ROSE|DOUBLE_PLANT|COMPOST|SPRAYONATOR|SKYMART_.*)$')
prods = {k: v for k, v in bz['products'].items() if keep_re.match(k)}
print('bz products', len(prods))
json.dump({'success': True, 'lastUpdated': bz['lastUpdated'], 'products': prods}, open(OUT + '/hypixel/bazaar.json', 'w', encoding='utf-8'), ensure_ascii=False)

for f, name in [('resources_skyblock_skills.json', 'resources_skills.json'), ('resources_skyblock_collections.json', 'resources_collections.json'),
                ('resources_skyblock_election.json', 'resources_election.json'), ('skyblock_firesales.json', 'firesales.json'), ('skyblock_news.json', 'news.json')]:
    d = json.load(open(os.path.join(SP, 'live', f), encoding='utf-8'))
    json.dump(d, open(OUT + '/hypixel/' + name, 'w', encoding='utf-8'), ensure_ascii=False)
