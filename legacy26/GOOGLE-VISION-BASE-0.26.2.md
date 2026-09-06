# FlipCheck v0.26.2 · Google con chiave API · build 167

## Uso

Installare `FlipCheck-v0.26.2-GoogleFix-167.apk` sopra la versione precedente. Package e firma sono compatibili; versionCode 167.

In Impostazioni lasciare la chiave OpenAI e incollare la chiave **Google Cloud Vision** nel nuovo campo. Non occorrono URL, token di servizio, server personali o file di credenziali. Nel progetto Google associato alla chiave devono essere abilitati Cloud Vision API e fatturazione. La chiave deve poter utilizzare questa API; una configurazione che consente soltanto Gemini non basta.

Le due chiavi restano in memoria per la sessione: la chiave Google va reinserita dopo la chiusura dell’app. Nessuna chiave viene incorporata nell’APK, salvata nelle preferenze, inclusa nella diagnostica o inviata ai siti di confronto. Preferenze e tetto di spesa restano salvati. L’inserimento non lancia richieste di prova a pagamento; la validità viene verificata alla prima ricerca necessaria.

## Comportamento

La base scelta rimane v0.26.2, commit fbb4f1ead7cc65afe01f9aae7446c13161a32f10. Il motore originale, le correzioni per edizione/Shadowless, il caricamento simultaneo di tre foto e gli insets Android sono conservati. Una identità già pronta salta Google; senza chiave Google continua il percorso precedente.

Quando serve, Android invia una sola immagine a `https://vision.googleapis.com/v1/images:annotate`, con funzione WEB_DETECTION e chiave nell’header `x-goog-api-key`. Il crop parte dal file originale orientato e conserva l’intero oggetto, fino a 2048 pixel senza ingrandimento artificiale. I dati visibili guidano le eventuali query testuali. Restano al massimo due chiamate testuali per identificazione e una Google, mai imposte a una identità già chiusa.

Il telefono recupera fino a tre pagine e tre immagini pubbliche associate da Google. Se una pagina non è accessibile, il titolo indicizzato da Google viene marcato come tale; senza immagine utilizzabile il riferimento non può confermare una identità. Le immagini e le citazioni sono confrontate da OpenAI. Punteggi Google e somiglianza da soli non confermano identità, edizione, finitura, autenticità o stato fisico. Un errore dei comparabili non riapre una identità confermata.

La rete nativa conserva la verifica TLS, filtra gli indirizzi privati nella risoluzione DNS e controlla ciascun redirect delle fonti. Le richieste Google non seguono redirect; nessun retry applicativo o recupero automatico della connessione. Le fonti ricevono solo GET senza chiavi. URL con parametri di credenziali/firme sono esclusi; altri parametri, per esempio dimensione di immagini, sono ammessi.

Chiave non valida, API disabilitata, fatturazione non attiva, permesso negato, quota esaurita e riferimenti inaccessibili hanno messaggi distinti quando Google fornisce il motivo. Timeout e annullamento cancellano le richieste pendenti; risposte tardive non aggiornano una nuova scansione. Nessun loop di ricerche.

Il limite stimato resta €0,025 per identificazione e mercato. Google Web Detection riserva $0,0035 per immagine, senza presumere la fascia gratuita. Le richieste fallite potenzialmente addebitate conservano la riserva. Il fattore di pianificazione iniziale USD/EUR è 1, configurabile e non una quotazione aggiornata. Prezzi OpenAI ereditati dalla base: il limite è preventivo stimato, non un rendiconto fiscale del provider.

## Verifica

I controlli usano risposte simulate e immagini sintetiche. Nessuna chiamata reale a pagamento viene lanciata durante la preparazione. Il workflow controlla policy, percorso browser, payload nativo, separazione delle credenziali, limiti di rete e Android 16 (navigazione, tastiera, foto multiple). Non costituisce una prova di riconoscimento delle carte reali: quella richiede le chiavi dell’utente e fotografie reali.

La build 166 sostituisce il collegamento al backend introdotto nella 164 con rete nativa e chiave API diretta. I file `backend/visual-search` restano nel repository come implementazione precedente, ma l’APK non li usa e non richiede il loro deployment.

Riferimenti ufficiali: [autenticazione Vision con API key](https://docs.cloud.google.com/vision/product-search/docs/auth), [header delle API key](https://docs.cloud.google.com/docs/authentication/api-keys-use), [Web Detection](https://docs.cloud.google.com/vision/docs/detecting-web), [listino](https://cloud.google.com/vision/pricing).

## Correzione mirata 166: risultati del 6 settembre

I log ricevuti mostrano Politoed H23/H32 pronta al mercato dopo una sola Vision (31,8 s API, zero web e zero Google). Il box legge anno, linea e frase sull’autografo, ma la risposta del resolver testuale termina a `max_output_tokens`; nella 165 il catch esterno interrompeva il percorso prima di Google. Questi due test non verificavano ancora una chiamata Google reale.

Nella 166 il resolver restituisce solo candidati, riepilogo e dati mancanti, con massimo 1800 token invece di 950. I dati fotografici già acquisiti non devono essere ripetuti nella risposta. Il motore originario resta byte-identico.

Una risposta testuale troncata, vuota o malformata viene scartata senza inventarne la parte mancante. Le fonti web complete eventualmente già presenti nella stessa risposta possono ancora essere valutate dalla logica esistente. Se non chiudono l’identità, si può proseguire con una sola ricerca Google, nei limiti di tempo e budget. Non viene ripetuta la ricerca testuale. Errori di autenticazione, annullamento e limite di spesa non vengono trattati come JSON recuperabile.

La diagnostica distingue HTTP 200 da contenuto incompleto e registra ragione del recupero, fonti disponibili e risposta parziale scartata. Se non resta budget sufficiente, dichiara il limite e non promette una identificazione. I test simulati verificano anche questo caso: recuperare il flusso non implica che tutti gli oggetti possano chiudersi entro il limite.

La preparazione foto non cambia: 1280 px per la lettura iniziale, fino a 2048 px dall’originale per Google. L’etichetta nella schermata chiarisce entrambi i passaggi. Il codice nativo Google, la firma, gli insets e il selettore delle foto non cambiano.

## Correzione mirata 167: indizi discriminanti e riferimenti

Nei tre nuovi log della 166, Vileplume chiude con una sola lettura (zero web e Google). Google risponde correttamente nei due casi non chiusi: 952 ms sul pannello e 1268 ms sul box. Il problema riscontrato riguarda la selezione delle prove, non un errore di chiave Google.

Le query assegnano priorità a identificatori, quantità e specifiche chiare, anche quando questi indizi arrivano dopo i primi cinque. Il resolver riceve le trascrizioni chiare e l’unità fisica, senza le varianti ipotizzate nel titolo o OCR incerto rientrato nei vecchi campi. Un conflitto richiede una trascrizione chiara e una citazione della fonte. Un errore di etichetta documentato è distinto da una contraddizione. Le quantità mancanti e i conflitti non verificabili impediscono una promozione automatica per punteggio. Le parole composte senza cifre non vengono estratte come codici modello.

Prima di spendere per Google, l’app recupera fino a tre pagine pertinenti già trovate dalla ricerca testuale e le loro immagini esplicitamente collegate (metadati social o immagini principali). Il parser nativo jsoup conserva titolo e testo principale, rimuove script e navigazione e applica i limiti di rete già esistenti. Le fonti restano dati non attendibili come istruzioni. Le immagini devono essere confrontate, non bastano titolo, pagina o somiglianza. Se non ci sono riferimenti utilizzabili, può partire la singola chiamata Google prevista. Le pagine Google vengono filtrate per immagini associate prima del limite di tre; una pagina senza immagini non è più registrata come download immagine fallito.

Il confronto usa il ritaglio dell’intero oggetto dall’originale, ad alta qualità, e da una a tre immagini di riferimento in base al budget disponibile. Elimina la copia ridondante della panoramica e il lungo rapporto Vision già acquisito. La stima conservativa per immagine e il tetto €0,025 restano attivi. Se neppure un confronto è finanziabile, il risultato esplicita il limite; non garantisce una chiusura. Una quantità chiara richiede un confronto della configurazione con lo stesso numero. Le varianti ipotizzate di oggetti non vengono aggiunte alla nuova query confermata; i dati fisici delle carte restano protetti.

Le regressioni sono sintetiche e senza chiamate a pagamento: quantità al sesto indizio, conflitti veri e inventati, errore stampato documentato, selezione delle fonti, immagini Google oltre la terza pagina, recupero delle immagini di catalogo prima di Google e confronto entro budget. Le foto reali dei tre test devono essere riprovate sul telefono; i log da soli non consentono una nuova verifica visiva.

## Correzioni mirate 168: identità e osservazioni fisiche

Le diagnostiche 167 mostrano un percorso rapido valido su Politoed, ma anche tre errori di gestione: il controllo della stampa avveniva dopo la decisione di saltare la verifica; la stagione poteva essere usata come codice prodotto; il confronto poteva confondere custodia, giornale e numero di inserzione con caratteristiche dell’oggetto.

La lettura assistita conserva fino a quattro osservazioni fisiche chiare (conteggio, configurazione, disposizione, forma, misura) con indice foto e ambito. Passano al resolver e al confronto insieme ai testi. Le query privilegiano una quantità discriminante e rimuovono etichette singole già contenute in frasi più complete. Gli identificatori fotografici derivano dai ruoli modello, numero carta e barcode; anno/stagione, fascicolo, seriale e certificato slab restano distinti. Un indizio esplicito dell’utente resta disponibile, senza diventare prova fotografica.

Una configurazione documentata può superare il limite del riconoscimento per sola somiglianza solo quando tutte le osservazioni chiare corrispondono, esiste una fonte specifica e forte e le quantità sono supportate da citazioni letterali. Due candidati distinti con queste prove richiedono ancora confronto. Nessuna regola nomina un prodotto dei test.

Per carte e pannelli il titolo può essere composto da editore, anno, soggetto e serie/fascicolo/numero, tutti citati separatamente. Il numero di inserzione non viene promosso a numero catalogo. I confronti dichiarano se un dettaglio deriva davvero dall’immagine o dalla descrizione. Custodia, misure del giornale e autenticità non certificabile restano separati dall’identità; differenze reali del soggetto, dell’unità o della variante continuano a bloccare la conferma.

Il controllo Pokémon precede la chiusura. Se restano timbro, bordo o copyright da leggere, è consentita una sola rilettura Vision: crop localizzati sull’originale, oppure oggetto intero quando la posizione non è certa. I dettagli già certi non vengono sovrascritti. Non parte una ricerca web per risolvere un’ombra illeggibile. La carta può ancora chiudersi con la prima lettura quando le prove sono sufficienti. Se la rilettura non basta, la richiesta indica il dettaglio mancante.

I PDF pertinenti già trovati sono recuperati con gli stessi controlli di rete, fino a 6 MB; Android PdfRenderer rende le prime tre pagine in una tavola di riferimento. Il confronto riceve gli indici delle pagine e distingue il testo indicizzato dalle figure. Nessuna ricerca aggiuntiva è obbligatoria. Figure oltre la terza pagina non vengono recuperate in questa build. API nativa: https://developer.android.com/reference/android/graphics/pdf/PdfRenderer.

La diagnostica registra la chiusura dopo la politica di stampa e separa identità e mercato. Il rifiuto di una ricerca mercato per budget conserva l’identità; se non è partita alcuna chiamata, conserva anche il tentativo gratuito. Restano il tetto configurato, la firma compatibile, gli insets Android e il caricamento multiplo. Il motore inline v0.26.2 resta identico.

Le regressioni usano risposte API e immagini sintetiche, senza chiamate a pagamento. Verificano i vincoli e il percorso applicativo, non l’accuratezza sulle fotografie originali: le carte e gli oggetti reali vanno riprovati sul telefono. Non viene dichiarato un riconoscimento al 100%.

## Correzioni mirate 169: recupero Google e copertura delle foto

I quattro log della 168 mostrano un Hobby Box confermato, Machamp con timbro letto ma bordo escluso dalla seconda foto, Pelé con confronto visivo esatto rifiutato per il campo soggetto e Dončić con variante aperta e confronto bloccato dal budget. Non risultava alcuna annotazione Google in questi quattro tentativi.

La build 169 usa la stessa Google Cloud Vision API e la stessa chiave già previste, con una sola feature WEB_DETECTION per una sola immagine dell’oggetto. Le carte senza identificatore e gli oggetti con aspetto distintivo o variante incerta possono usare Google prima del web testuale. Le identità già chiuse e il percorso testuale del box restano rapidi. Una pagina con immagini accessibili non chiude più da sola il recupero: se il confronto non conferma e non manca un dettaglio fisico, Google può essere chiamato una volta. Prima di spendere per l’annotazione viene verificato che resti anche spazio stimato per confrontare almeno un riferimento. Errori di servizio e risposte incomplete non diventano conferme. Le stime non costituiscono una garanzia sulla fatturazione effettiva dei provider.

Il listino Google verificato il 6 settembre 2026 indica WEB_DETECTION a $3,50/1000 unità dopo le prime 1000 unità mensili gratuite ($0,0035 per immagine, 0,35 centesimi USD). L’app contabilizza prudentemente anche le unità che potrebbero rientrare nella franchigia; la verifica OpenAI è separata. Fonte: https://cloud.google.com/vision/pricing. Documentazione corrispondenze immagini/pagine: https://docs.cloud.google.com/vision/docs/detecting-web.

La prima lettura distingue colori, pattern e finitura dal nome commerciale ipotizzato. Questi dati entrano nel contesto della ricerca. Il confronto riceve ogni foto caricata, con il riquadro completo per ciascun originale o la foto intera se il riquadro non è certo; il budget non viene aggirato eliminando silenziosamente il retro. Il testo delle fonti viene compattato, omettendo blocchi ripetitivi e cookie. Le immagini palesemente generiche (logo, placeholder, hero-inner) vengono escluse. Il numero carta conserva il testo fotografato e separa l’etichetta NO./# dal valore; la stagione viene estratta dalla riga estesa senza confonderla con il codice prodotto. Una variante incerta richiede confronto del suo aspetto e nome citato.

Per la stampa Pokémon il recupero è pianificato per ciascun dettaglio mancante. Se il copyright ha un crop certo ma il bordo no, vengono inviate anche la carta intera o la foto pertinente: un crop disponibile non sopprime più gli altri dettagli da verificare. Se il dettaglio resta illeggibile non viene dichiarato assente.

Per il soggetto di una carta/pannello, quando il valore generato non coincide con la citazione, può essere conservata la breve descrizione letterale citata invece del valore discordante. Questo recupero richiede citazione presente nella fonte, testo del soggetto osservato in foto e confronto visivo corrispondente sulla stessa fonte. La diagnostica marca recovered_from=cited_subject_description. Nessun numero di inserzione è aggiunto al numero carta; originalità e condizioni restano non certificate.

Dopo la conferma titolo, modello e richieste residue vengono sincronizzati. Un’identità fotografica già letta viene conservata separatamente quando la variante resta aperta. La diagnostica registra ordine dei passaggi, tutte le foto preparate, copertura dei dettagli e confronti eseguiti. I test includono riproduzioni delle risposte testuali reali 168 e casi sintetici; non sono nuove letture delle fotografie né test API a pagamento. Nessuna promessa di accuratezza al 100%.

## Build 170 — evidence and response budget

Authorized cap: EUR 0.03 per scan, using the configured USD/EUR planning factor.
The previous default of 0.025 migrates to 0.03; smaller custom caps are retained.
The budget includes Google, OpenAI, recovery attempts and market lookup, with
unknown billing retained as a reservation. Reservations are estimates, not a
provider-side spending limit. The scan deadline is 120 seconds.

Before confirmation, an explicit subtype doubt remains pending even if the model
returns a high confidence and market_ready. Card expansion names inferred from a
collector number or symbol require catalogue evidence unless the series name is
printed on the photo/slab. Confirmed catalogue fields must be cited; no card-name
or product-specific tables were added.

At least two image features are still required in assisted comparison. Literal
source descriptions may complete quantities/specifications; normalized numbers,
units and per-unit bases must agree. A cited model title may supply the season.
Incidental surface descriptions are not mandatory matches for every object.
Colour/pattern remain relevant when distinguishing uncertain card parallels.

Queries include the observed category and prioritize printed identifiers,
quantities and texts over incidental textures. Native page extraction removes
navigation and related products before selecting images, preserves paragraph
boundaries and includes Product structured data. Excerpts prioritize identifiers
and quantities rather than menu text.

Initial Vision now uses low reasoning, 4500 maximum output tokens and concise
arrays. Resolver output allows 2600 tokens; comparison allows 2200, or 1800 with a
single compact reference when needed. At most ONE response-completion attempt
per scan is permitted, with preflight budget and cancellation checks. Initial
Vision may retry at 6000 tokens. Truncated web output can be reconstructed from
complete collected source metadata without another web search. Truncated image
comparison may retry at 3600 tokens. Partial JSON is never accepted as identity.

Validation: sanitized build 169 diagnostics replay box rejection and Doncic
uncertainty; negative tests cover invented citations, wrong quantity/container,
missing images, family-only matches and conflicting variants. Browser tests use
synthetic images and intercepted API responses. No paid recognition tests.
