# FlipCheck v0.26.2 + Google Vision — build 164

## Base richiesta e compatibilità

Questa versione parte dall'APK v0.26.2 allegato dall'utente, non dalla v0.26.6:

- Repository: `michele89coco-hue/FlipCheck`.
- Branch: `codex/v0262-google-visual`.
- Commit della base: `fbb4f1ead7cc65afe01f9aae7446c13161a32f10`.
- APK della base: `versionCode=159`, `versionName=0.26.2-android-ui`.
- SHA-256 dell'APK allegato: `a64d206c2461f237f12e20908b4837e8cf8180b2e3c42f10126fdf34070094ea`.
- Tutti e tre gli asset della base sono stati confrontati byte per byte con l'APK.
- Nuovo APK: `versionCode=164`, `versionName=0.26.2-google-visual`.
- Package invariato: `com.flipcheck.beta.legacy26fix`. La numerazione consente l'aggiornamento anche da build 163.
- Firma attesa SHA-256: `d4d02478ea31cb6bd83228047e9accd7dfb191ed991a4c8e8c06a228e54614c7`.
- Backend aggiunto: `visual-api-1-build164`, protocollo 1, sorgenti nella stessa revisione dell'APK.

Il motore JavaScript inline originale, `editions.js`, `targeted-fixes.js`, il caricamento multiplo e la gestione Android di barra/tastiera restano quelli della v0.26.2. Nel wrapper Android cambia soltanto l'elenco dei due nuovi asset consentiti. Non vengono importate le pipeline delle build 160–163. I nuovi moduli sono `visual-policy.js` e `visual-runtime.js`.

## Comportamento

Una identità già pronta nella v0.26.2 resta pronta e non avvia Google. Quando il servizio è configurato, la prima lettura raccoglie anche indizi con ruolo, posizione, incertezza, unità fisica e un riquadro dell'intero oggetto. La ricerca testuale esistente usa le scritte fisiche disponibili, senza richiedere marca, anno o serie. Se il testo è assente o generico, il nuovo percorso può passare direttamente alla ricerca tramite immagine.

Google riceve una sola immagine, tramite contenuto base64 nella richiesta `WEB_DETECTION`: non viene creata una URL pubblica della foto. Il ritaglio proviene dall'originale orientato, fino a 2048 px, JPEG 0,94; se il riquadro è incerto viene usata l'immagine completa. Il ritaglio conserva l'intero pannello e i bordi. La preparazione della prima lettura v0.26.2 resta invariata.

Il backend restituisce candidati, fino a tre pagine e tre immagini di riferimento. Il modello già usato dall'app confronta fisicamente foto e riferimenti in una richiesta Vision senza ulteriori ricerche web. I punteggi di Google non sono probabilità e non confermano nulla da soli. Le citazioni devono provenire dai testi recuperati. Numero di fascicolo e numero di catalogo rimangono distinti. Seriali, edizioni, condizioni e autenticità non vengono copiati da un altro esemplare.

Il confronto può rimanere incerto: una ristampa indistinguibile non viene autenticata. I risultati senza immagini di riferimento utilizzabili non vengono promossi a confermati. Le fonti non accessibili, gli errori Google e la mancanza di configurazione producono un esito finito e riconoscibile, senza richiesta di foto usata per nascondere il problema del servizio.

## Attivazione del servizio

**Il codice è integrato, ma questa consegna non configura credenziali Google e non effettua un deployment.** Nell'APK, servizio e token inizialmente non sono impostati. In tale stato continua il riconoscimento v0.26.2 e la diagnostica riporta `not_configured`. Non è Google Lens.

1. In un progetto Google Cloud abilitare fatturazione e Cloud Vision API. Preparare una identità di servizio con i permessi necessari per Vision e impostare le quote nel progetto.
2. Eseguire `backend/visual-search/server.py` con Python 3.12 e le dipendenze di `requirements.txt`, oppure costruire il Dockerfile della stessa cartella. Su Cloud Run è preferibile assegnare una identità di servizio e usare Application Default Credentials; in ambiente locale usare ADC o `GOOGLE_APPLICATION_CREDENTIALS` con un file protetto, mai nel repository/APK.
3. Impostare `VISUAL_ENABLED=true` e un `FLIPCHECK_ACCESS_TOKEN` casuale. Questo token protegge l'accesso al proprio servizio e va comunicato soltanto agli utenti autorizzati. Le credenziali Google restano esclusivamente sul server.
4. Esporre il servizio tramite HTTPS. Il server locale ascolta per default su `127.0.0.1:8080`; il Dockerfile ascolta su `0.0.0.0:8080`. La piattaforma deve terminare TLS. Non pubblicare direttamente il server HTTP di sviluppo su Internet.
5. Nell'app, Impostazioni → Ricerca tramite immagine → Configurazione del servizio, inserire l'indirizzo HTTPS e il token di accesso. Il token resta soltanto in memoria. Il controllo di disponibilità avviene quando la ricerca è necessaria.

Endpoint autenticati: `GET /v1/config` e `POST /v1/visual-search`. Il secondo accetta `scan_id`, `image_base64`, `clues` e `remaining_usd`. Non accetta URL della foto utente. Protocollo/revisione e stato effettivo sono inclusi nella risposta.

## Quote, timeout e costo

- `VISUAL_ENABLED`: default `false` sul server; interruttore anche nell'app.
- `GOOGLE_WEB_DETECTION_UNIT_USD`: default `0.0035` come stima per immagine, senza assumere che la quota gratuita sia ancora disponibile.
- `VISUAL_TIMEOUT_SECONDS`: default 22, massimo 30. Zero retry automatici di annotazione.
- `VISUAL_REQUESTS_PER_MINUTE`: default 10 per istanza.
- `ALLOWED_ORIGIN`: default `https://flipcheck.local`.
- Massimo una richiesta Google per scansione; massimo due tentativi di ricerca testuale identificativa inclusi quelli della baseline. Questi limiti non impongono di utilizzare tutte le chiamate.
- Tetto iniziale app: €0,025 per scansione, identificazione e mercato inclusi. Si può ridurre; non viene aumentato automaticamente. Il fattore iniziale di pianificazione è 1 USD per EUR, configurabile: non rappresenta una quotazione valutaria aggiornata.
- Il controllo preventivo usa stime di token e riserva l'output massimo; l'usage restituito regola la quota effettivamente conteggiata. I timeout mantengono la riserva perché possono essere addebitati. I prezzi OpenAI sono quelli configurati nella v0.26.2: verificarli prima di una distribuzione commerciale. Costi di hosting e imposte non sono una misura per-scansione fornita dal provider.
- Il budget può fermare il confronto o il mercato anche dopo una ricerca. L'identità già confermata rimane tale; non viene inventato un prezzo.

La deduplicazione conserva soltanto risultati di ricerca e digest della foto per la stessa scansione, in memoria per dieci minuti. Non riusa una identità tra esemplari diversi. Per scalare su più istanze usare affinità di scansione o un registro condiviso equivalente e quote Google del progetto: il limite per istanza non è un limite globale di progetto. Nessun upload utente viene scritto su disco; riferimenti pubblici possono restare nella cache breve. URL con parametri di query sono esclusi dalla restituzione per evitare URL temporanee sensibili: alcune immagini di riferimento possono quindi non essere utilizzabili.

## Verifiche e limiti

I controlli locali e browser usano fixture e risposte simulate. Verificano il payload ufficiale Google, campi mancanti/errori per immagine, confronto, provenienza, pannelli, duplicati, budget, annullamento e conservazione della baseline. Nessuna campagna di riconoscimento live a pagamento è autorizzata o eseguita in questa consegna. I test simulati non dimostrano che Google troverà la carta reale o che la foto del box verrà riconosciuta. La diagnostica distingue `production` da `mock` e non contiene immagini base64 né credenziali.

Per riprodurre: `node --test legacy26/tests/editions.test.cjs legacy26/tests/visual-policy.test.cjs`, `python3 -m unittest discover -s backend/visual-search -p 'test_*.py'`, poi i test browser con Playwright indicati nel workflow. L'APK viene firmato e controllato contro package, versione, certificato e commit. Il controllo Android della tastiera è separato dai risultati di riconoscimento e il suo esito va riportato senza trasformare un fallimento in un successo.

Fonti tecniche verificate il 6 settembre 2026: [richiesta e risposta Web Detection](https://cloud.google.com/vision/docs/detecting-web), [listino Cloud Vision](https://cloud.google.com/vision/pricing), [formati supportati](https://cloud.google.com/vision/docs/supported-files), [autenticazione Google](https://google-auth.readthedocs.io/en/latest/user-guide.html). Il listino indica $3,50 per 1.000 unità Web Detection nella fascia a pagamento ordinaria; servizi di hosting aggiuntivi possono avere costi separati.
