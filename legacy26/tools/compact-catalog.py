"""Build a factual, offline vintage checklist from a pinned pokemon-tcg-data snapshot.

Usage: python3 legacy26/tools/compact-catalog.py /path/to/downloaded-json
No images, card rules, prices or network access are included in the generated file.
"""
import json
import pathlib
import sys

root = pathlib.Path(sys.argv[1])
meta = json.loads((root / 'selected-sets.json').read_text())
sets = []
for s in meta['sets']:
    cards = json.loads((root / (s['id'] + '.json')).read_text())
    sets.append({'id': s['id'], 'name': s['name'], 'total': s['printedTotal'],
                 'cards': [[c['number'], c['name'], c.get('hp', ''),
                            [a['name'] for a in c.get('attacks', [])]] for c in cards]})
data = {'source': 'https://github.com/PokemonTCG/pokemon-tcg-data',
        'commit': meta['commit'], 'sets': sets}
target = pathlib.Path(__file__).resolve().parents[1] / 'src/main/assets/tcg-catalog.js'
target.write_text('/* Factual checklist; provenance and scope: legacy26/RECOGNITION-0.26.3.md. */\n'
                  '(function(r){const data=' + json.dumps(data, ensure_ascii=False, separators=(',', ':'))
                  + ';if(typeof module!=="undefined"&&module.exports)module.exports=data;'
                  'else r.FlipCheckCatalog=data;})(typeof window!=="undefined"?window:globalThis);\n')
print(len(sets), 'sets;', sum(len(s['cards']) for s in sets), 'cards;', target.stat().st_size, 'bytes')
