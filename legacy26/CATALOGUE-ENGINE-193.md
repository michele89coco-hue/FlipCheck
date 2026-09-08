# Build 193 — motore di identificazione per cataloghi

Questa build sostituisce il percorso decisionale delle scansioni con tre moduli:

- `catalogue-engine.js`: registro delle osservazioni, chiavi tipizzate, candidati catalografici, ambito delle varianti e decisione finale.
- `catalogue-sources.js`: adattatori per TCGdex, checklist e pagine catalografiche; ogni voce conserva la fonte. Carte con numeri diversi rimangono voci distinte.
- `catalogue-runtime.js`: esegue lettura iniziale, OCR locale, recupero dei cataloghi, eventuale crop decisivo e confronto delle varianti. Le regole precedenti non possono riscrivere un risultato finale del nuovo motore.

La preparazione delle foto originali, la cache per scansione, il servizio Android in background, la contabilizzazione e il percorso certificato/etichetta delle slab restano componenti del prodotto. Le slab non passano nel recupero OCR/web aggiuntivo. La società di grading, il voto e il certificato rimangono nell'identificazione.

## Dati, catalogo e varianti

Le osservazioni mantengono testo, provenienza, certezza, immagine e regione. Un numero di carta, un numero nelle statistiche, il copyright e la tiratura dell'esemplare hanno ruoli diversi. Le alternative OCR e le rotazioni non vengono perse. Una lettura `215` non diventa `2/5` perché una checklist elenca una parallela /5: serve una lettura chiara del dettaglio.

Pokémon parte dal nome e dal numero, anche parziale. Il set ipotizzato dalla visione non vincola la ricerca. L'anno di copyright non è un veto sulla data di pubblicazione. First Edition, Shadowless e No Rarity Symbol hanno ambiti distinti per set e lingua; un simbolo illeggibile non equivale a un simbolo assente. Rarità e finitura restano campi separati.

Le sportive usano la checklist del prodotto. Colore del bordo, pattern, finitura, autografo, patch e seriale restano distinti. Un seriale conserva numeratore e tiratura. Le immagini delle varianti devono essere attribuite alla fonte: la somiglianza locale non chiude da sola un'identità.

Le confezioni conservano il prodotto già letto anche se il formato rimane incerto. Promesse come un autografo ogni tre scatole non sono equivalenti a un autografo per scatola. Gli oggetti generici cercano il codice modello nelle specifiche, senza restringere la ricerca ai siti sportivi.

## Recupero delle fonti

- TCGdex: ricerca per nome nella lingua letta, dettaglio della carta e data del set.
- Topps, Panini, Upper Deck e Leaf: directory delle checklist, documenti pertinenti e ricerca mirata quando il documento non è accessibile.
- One Piece: ricerca della card list Bandai e separazione delle voci per codice. La lista identifica il nucleo; l'illustrazione o la variante può richiedere un ulteriore riferimento.
- Pagine di supporto: testo conservato, colonne delle tabelle, nomi e numeri collegati alla stessa voce. Il testo estratto dal modello deve trovare riscontro nella pagina recuperata.

I recuperi hanno limiti di tempo, numero di candidati e costo. Blocchi del sito, copertura incompleta o dettagli fotografici illeggibili possono lasciare la variante aperta, mantenendo i campi già verificati. Non esiste un archivio locale completo di tutte le carte.

## Controlli

Il gate della build esegue i test del nuovo motore, i controlli dei componenti conservati, il flusso reale della UI con trasporto simulato e i test Android. `catalogue-browser.test.cjs` sostituisce nel gate i vecchi replay `visual-browser.test.cjs` e `production-flow-release.test.cjs`, che descrivono la sequenza di chiamate della precedente architettura. I test di caricamento, sostituzione e cancellazione delle foto rimangono in `browser.test.cjs`.

La fixture `diagnostics-192.json.gz` contiene le letture iniziali e le risposte OCR/fonti dei sei test forniti dall'utente, selezionate senza credenziali o payload immagine. I replay non effettuano chiamate API a pagamento. Le immagini sintetiche nei test browser sono supporti per verificare trasporto, ritagli e logica: non misurano l'accuratezza di una nuova lettura Vision sulle fotografie.

La build è destinata al test installabile. Il superamento dei controlli non dimostra riconoscimento universale né accuratezza “umana”; serve il confronto con nuove scansioni reali prima di una distribuzione pubblica.
