/* Small v26 recognition guards. No fetch, model call or automatic identity promotion. */
(function(root) {
  'use strict';
  const catalog = typeof module !== 'undefined' && module.exports ? require('./tcg-catalog.js') : root.FlipCheckCatalog;
  const norm = s => String(s || '').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase().replace(/[^a-z0-9]+/g,' ').trim();
  const list = x => Array.isArray(x) ? x : [];
  const unique = a => [...new Set(a)];
  const setName = s => norm(s).replace(/^pokemon\s+/,'').replace(/^(set di base|set base)$/,'base')
    .replace(/^base set$/,'base').replace(/^base set 2$/,'base set 2')
    .replace(/^(expedition|spedizione)$/,'expedition base set');
  const knownSets = catalog.sets;
  const names = unique(knownSets.flatMap(s => s.cards.filter(c => c[2]).map(c => c[1]))).sort((a,b) => b.length-a.length);
  const hasName = (text,name) => (' '+norm(text)+' ').includes(' '+norm(name)+' ');
  const ratio = text => {
    const m = String(text || '').match(/\b(H?\d{1,3})\s*\/\s*(H?\d{1,3})\b/i);
    return m ? {number:m[1].toUpperCase().replace(/^0+(?=\d)/,''), total:m[2].toUpperCase()} : null;
  };
  function inspect(x,observed=x) {
    if (!x || x.kind !== 'card' || x.pokemon_printing?.is_pokemon !== true) return null;
    const language = norm(x.pokemon_printing.language);
    if (!['english','inglese','en','italian','italiano','it'].includes(language)) return null;
    const set = knownSets.find(s => setName(s.name) === setName(x.family || x.pokemon_printing.set_name));
    if (!set) return null; // An unindexed expansion is not an identity conflict.
    const subject = names.find(n => hasName(observed.model,n)) || names.find(n => hasName(observed.title,n));
    if (!subject) return null; // No English/Italian Pokémon name: do not guess translations.
    const mark = ratio(x.model) || ratio(x.title) || list(x.identifier_hints).map(ratio).find(Boolean);
    const literal = [...list(observed.distinctive_terms),...list(observed.layout_signature).map(t => t.term),observed.visual_fingerprint].join(' ');
    const hp = unique([...literal.matchAll(/\b(\d{2,3})\s*(?:HP|PV|PS)\b/gi)].map(m => Number(m[1])));
    const candidatesInSet = set.cards.filter(c => norm(c[1]) === norm(subject));
    const row = mark ? set.cards.find(c => c[0].toUpperCase() === mark.number) : null;
    const conflicts = [];
    if (['base4','base6','ecard1','ecard2','ecard3'].includes(set.id) && x.pokemon_printing.first_edition_stamp==='present')
      conflicts.push('Il timbro dichiarato non corrisponde alle edizioni occidentali di questo set.');
    if (!candidatesInSet.length) conflicts.push('Il nome della carta non appartiene al set indicato.');
    if (mark && !row) conflicts.push('Il numero indicato non esiste in questo set.');
    if (row && norm(row[1]) !== norm(subject)) conflicts.push('In questo set il numero indicato appartiene a '+row[1]+'.');
    const expectedTotal = mark?.number.startsWith('H') && set.id.startsWith('ecard') ? 'H32' : String(set.total);
    if (mark && mark.total !== expectedTotal && !(expectedTotal==='H32' && mark.total==='32')) conflicts.push('Il totale del numero carta non corrisponde al set.');
    if (row && hp.length === 1 && row[2] && Number(row[2]) !== hp[0]) conflicts.push('I PV letti non corrispondono al numero e set indicati.');
    const attacks = list(observed.layout_signature).filter(t => /(?:attacco|attack)/i.test(t.relation || '')).map(t => t.term);
    const english = ['english','inglese','en'].includes(language);
    if (row && english && attacks.length && row[3].length && attacks.some(a => !row[3].some(b => norm(a) === norm(b))))
      conflicts.push('Il nome di un attacco letto non corrisponde alla carta indicata.');
    // Alternatives are discovery hints, never a substitute for the observed card number.
    const alternatives = conflicts.length ? knownSets.flatMap(s => s.cards
      .filter(c => norm(c[1]) === norm(subject) && (hp.length !== 1 || Number(c[2]) === hp[0]))
      .map(c => ({set:s.name,number:c[0],name:c[1],hp:c[2],attacks:c[3]}))).slice(0,6) : [];
    return {status:conflicts.length ? 'conflict' : 'consistent',set:set.name,setId:set.id,subject,
      number:mark?.number || '',hp,conflicts,alternatives,source:catalog.source,sourceCommit:catalog.commit,
      attacksCompared:english && attacks.length > 0};
  }
  function guard(x,observed=x) {
    const check = inspect(x,observed) || (x?.card_consistency?.status==='conflict' ? x.card_consistency : null);
    if (!check) return x;
    const out = {...x,card_consistency:check};
    if (check.status === 'conflict') {
      out.status='uncertain'; out.model_verified=false; out.market_ready=false; out.normalized_query='';
      out.model_confidence=Math.min(79,Number(out.model_confidence || 0));
      out.family_confidence=Math.min(74,Number(out.family_confidence || 0));
      out.verification_summary='Dati della carta da verificare: '+check.conflicts.join(' ');
      out.candidate_models=check.alternatives.map(c=>({model:c.name+' '+c.number+' '+c.set,
        reason:'Suggerimento del catalogo locale da confrontare con la foto e una fonte; non confermato.'}));
    }
    return out;
  }
  function printingFor(x,p) {
    const check = x.card_consistency || inspect(x);
    if (!check || check.status !== 'consistent' || !check.number || !p) return p;
    // These Western sets have no first-edition issue. A claimed visible stamp remains
    // a conflict to verify; absence of an unreadable stamp must not block these sets.
    if (['base4','base6','ecard1','ecard2','ecard3'].includes(check.setId) && p.first_edition_stamp !== 'present')
      return {...p,set_name:check.set,first_edition_stamp:'not_applicable'};
    return p;
  }
  function removeRedundantPhoto(x,vision) {
    if (!x || x.kind !== 'object' || !x.next_photo_request) return x;
    const ids = list(vision?.identifier_hints).filter(s => /[a-z]/i.test(s) && /\d/.test(s));
    if (!ids.length) return x;
    const requested = String(x.next_photo_request);
    const rear = /(?:retro|back|rear)/i;
    const battery = /(?:vano batterie|battery compartment)/i;
    const legible = /(?:visibil|leggibil|legible|readable)/i;
    const uncertain = /(?:non (?:leggibil|visibil)|illeggibil|sfocat|unclear|unreadable|illegible)/i;
    const readArea = pattern => list(vision.evidence).some(e => pattern.test(e) && legible.test(e) && !uncertain.test(e)
      && ids.some(id => String(e).includes(id)));
    const asksRear = rear.test(requested), asksBattery = battery.test(requested);
    if (!(asksRear || asksBattery) || (asksRear && !readArea(rear)) || (asksBattery && !readArea(battery))) return x;
    // Preserve a request that names a genuinely unreadable character/area in missing_information.
    if (list(x.missing_information).some(t => uncertain.test(t) && /(?:codice|etichett|serial|model)/i.test(t)
      && ids.some(id => String(t).includes(id)))) return x;
    const out={...x,next_photo_request:null,photo_request_suppressed:{reason:'requested_area_already_read',identifiers:ids}};
    out.missing_information=list(x.missing_information).filter(t => !/retro.*non (?:disponibil|visibil)|vano batterie.*non (?:disponibil|visibil)/i.test(t));
    return out;
  }
  const visionPrompt = `COERENZA E FOTO GIA' FORNITE:
Per una carta confronta nome, set, numero, PV/HP e nomi degli attacchi: devono descrivere la stessa stampa. Il set dedotto dall'artwork non e' testo stampato. Non completare numeri/copyright dalla memoria. Se un dato contraddice gli altri, conserva i testi letterali e rendi l'identita' incerta per il confronto catalografico. Su oggetti, controlla TUTTE le foto prima di chiedere retro, etichetta o vano batterie: se vi hai gia' letto i codici, usa quei codici. Chiedi un dettaglio aggiuntivo solo specificando quale carattere o zona resta illeggibile. Il modello del televisore compatibile non e' necessario per identificare un telecomando con un codice univoco verificabile.`;
  const resolverPrompt = `RISPOSTA COMPATTA: non ripetere l'intero fingerprint, mantieni spiegazioni brevi e candidate_checks massimo 2. Il budget consente il JSON completo: restituisci tutti i campi richiesti. Conserva codici, nomi, numero carta, PV e attacchi letterali; non sostituirli con quelli di un'altra carta. Per una carta, candidate_checks.model deve contenere nome e numero catalografico, family deve contenere il set. I suggerimenti del catalogo locale sono candidati da verificare nella fonte, non prove fotografiche. Una confezione sigillata si identifica da serie, stagione e configurazione stampata, non dai giocatori illustrati. Per un telecomando risolvi i codici gia' letti; non chiedere nuovamente retro/etichetta/vano batterie gia' documentati.`;
  const api={inspect,guard,printingFor,removeRedundantPhoto,visionPrompt,resolverPrompt};
  if (typeof module!=='undefined' && module.exports) module.exports=api; else root.FlipCheckRecognition=api;
})(typeof window!=='undefined'?window:globalThis);
