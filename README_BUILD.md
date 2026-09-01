# FlipCheck Native v1.06

La v1.06 consolida categoria, fatti visivi e identità fisica e aggiunge una seconda lettura
visiva selettiva per i soli casi informativi in cui la prima risposta omette campi strutturati.
Non contiene scorciatoie specifiche per singole carte o prodotti.

Versione Android nativa con pipeline universale **physical evidence consolidation + probable references**. La UI usa Activity/Views Java e non usa WebView per il riconoscimento.

## Toolchain prevista

- Android Gradle Plugin 9.0.1
- Gradle 9.1.0
- JDK 17
- compileSdk / targetSdk 36
- minSdk 23

## Pipeline per singolo tentativo

1. OCR multipass e barcode ML Kit on-device.
2. Una chiamata multimodale `gpt-5.6-luna` con foto originali, Structured Outputs strict e al massimo una chiamata Web Search.
3. Dentro quella stessa chiamata il resolver usa almeno tre query, e quattro/cinque nei casi ricchi di identità o quando una famiglia forte non ha ancora un riferimento probabile. La prima query resta senza filtri `site:` e neutrale rispetto ai marchi non osservati; un produttore stampato su una confezione sigillata in primo piano può essere usato soltanto dopo riscontro nell'OCR locale.
4. Osservazione, retrieval, confronto foto-fonte e disproof restano nella stessa richiesta, così la geometria fotografica non viene ridotta a un riassunto testuale.
5. Nessuna ricerca prezzo finché l'identità esatta non supera le soglie previste.
6. Nessun retry della ricerca. I near-miss con tupla completa possono usare una verifica visiva;
   carte e box ricche di segnali ma con campi omessi possono usare una seconda lettura visiva
   senza Web, soltanto quando il costo previsto complessivo resta entro 0,02 dollari.
7. Per dispositivi con pannelli ricchi, marca non leggibile e leader web senza confronto
   immagine-riferimento, una seconda lettura visiva selettiva può scartare un falso positivo
   cross-brand. Può sostenere soltanto marca/famiglia probabili, mai il modello esatto.

## Marchi, testo e codici

- Una parola dichiarata da Vision come logo/marca non può corroborare se stessa tramite `visible_labels`.
- Il marchio diventa un vincolo soltanto se la stessa parola è presente anche nell'OCR locale e il ruolo di produttore del prodotto è coerente.
- Un'ipotesi di marchio non corroborata viene tolta da titolo, etichette osservate, query e vincoli.
- Controlli distintivi recuperati localmente, per esempio `TOP PICKS` insieme a `NETFLIX`, vengono passati come impronta morbida da verificare sulla foto; non diventano mai marchi o identificatori.
- Le etichette OCR vengono ordinate per potere informativo: combinazioni rare come `FIRST WEEK`, `SECOND WEEK` e `WATERING DURATION MINUTES` hanno priorità sulle parole generiche e possono recuperare una famiglia documentata.
- Display variabili e comandi generici non possono essere usati da soli nelle query.
- Un codice OCR testuale diventa identificatore solo se Vision lo lega a una marcatura identitaria del prodotto in primo piano.
- Codici su manuali, cartellini di istruzioni, mascherine, componenti, confezioni o oggetti vicini non identificano automaticamente il prodotto principale.
- L'OCR non appiattisce più righe separate prima di associare `MODEL`/`P/N` al valore.

## Candidati e chiarimenti

- Una fonte ufficiale prova che un riferimento esiste, non che sia l'oggetto fotografato.
- Una famiglia o linea di prodotto source-backed può essere mostrata come risultato parziale anche quando il riferimento completo non è disponibile.
- Il campo modello resta vuoto finché la fonte non stampa un riferimento completo; frasi come “reference not exposed” o “model unresolved” diventano lacune di prova interne e non nomi pubblici.
- Una contraddizione geometrica importante elimina il candidato.
- I candidati sotto 55/100 non vengono mostrati nelle “Top opzioni” pubbliche.
- Un codice può essere proposto come verifica fisica soltanto quando la fonte sostiene l'identità esatta della stessa entità fotografata.
- I modelli dell'apparecchio host, come i codici di un lettore Blu-ray associato a un telecomando, non vengono presentati come codici da leggere sul telecomando.
- La conferma utente restringe i candidati ma non certifica da sola modello o variante.
- Un riferimento fisicamente plausibile ma non provato esatto vive nel campo separato `probable_reference` e viene mostrato come “da verificare”, mai come modello confermato.

## Prodotti sigillati

- Quando la confezione sigillata è l'oggetto in primo piano, scatola e wrapper sono la superficie fisica identificativa e non vengono scartati come imballaggio estraneo.
- Produttore, stagione, linea, categoria/sport, formato e configurazione fisicamente stampati formano una tupla composita verificabile.
- Il produttore diventa vincolo di ricerca solo quando compare nella tupla fotografica ed è corroborato indipendentemente dall'OCR locale.
- Una confezione non richiede un codice `MODEL/P/N` separato quando la tupla stampata identifica un prodotto univoco.
- Una vista frontale può chiudere un box quando marca, stagione, linea, sport, formato e configurazione visibili convergono su un solo SKU commerciale; il barcode resta una prova aggiuntiva, non obbligatoria.

## Livello commerciale per i comps

- Smartphone: varianti regionali dello stesso hardware non separano il prezzo; `Galaxy S24 Ultra` può chiudersi anche con suffisso `SM-S928…` troncato, purché marca, nome e geometria siano verificati.
- Pokémon/TCG: il retro comune non è richiesto. Una foto frontale nitida può chiudere soggetto, set, numero e stampa quando il confronto visivo risolve davvero la variante.
- Per ogni carta riconosciuta il miglior riferimento specifico source-backed viene mostrato almeno come probabile; la conferma resta riservata ai campi fisici convergenti.
- Nelle TCG il numero collezionista frazionario prevale su numeri narrativi come il Pokédex; candidati catalogo equivalenti vengono fusi prima del margine.
- Una sportiva con fronte e retro completi può usare una seconda Web Search mirata al parallel esatto entro un tetto previsto di 0,025 USD.
- Qualsiasi carta ricca di informazioni rimasta aperta usa il recovery catalogo anche quando la prima ricerca non produce candidati grounded.
- Per una sportiva priva di numero stampato, un confronto univoco fronte/retro con almeno cinque segnali distintivi può chiudere l’identità.
- La scritta `TRADING CARD GAME` non trasforma un prodotto sportivo in una TCG quando categoria, giocatore, squadra o lega provano il dominio sportivo.
- Un telecomando senza logo, MODEL/P/N o confronto diretto non espone una marca dedotta dai soli tasti condivisi.
- Pokémon Base Set: Shadowless, Unlimited e 1st Edition vengono distinti tramite timbro localizzato, ombra del riquadro illustrazione e copyright/layout. Il timbro vale soltanto nella posizione fisica prevista sotto e a sinistra dell'illustrazione.

## Acquisizione foto

- `GALLERIA · PIÙ FOTO` abilita la selezione multipla e importa fino a tre immagini nella stessa operazione.
- `SCATTA FOTO` apre la fotocamera di sistema e conserva lo scatto a piena risoluzione per l'analisi; l'interfaccia visualizza soltanto una miniatura campionata per evitare picchi di memoria.
- Lo scatto viene scritto tramite un `ContentProvider` privato dell'app, con permessi temporanei concessi esplicitamente alla fotocamera OEM. Se il callback Android arriva prima della scrittura definitiva, l'app recupera e valida il JPEG nei secondi successivi.
- Il limite resta tre foto per singola analisi per contenere memoria, tempi e costo della richiesta multimodale.

## Identità completa visibile in foto

- Una targhetta, un barcode o una schermata identità può chiudere il modello quando il codice è completo, legato fisicamente all'oggetto, corroborato dall'OCR locale e coerente con una fonte recuperata.
- Un oggetto stampato multi-campo può essere confermato per composizione quando viste complementari mostrano produttore, set/anno, soggetto o variante, numero e gli altri discriminatori necessari e una fonte/checklist sostiene la stessa identità di base.
- Un seriale o una frazione di tiratura fisicamente stampata, per esempio `8/9`, identifica la copia osservata e non deve essere ripetuta da una checklist web che descrive il prodotto base.
- Un singolo errore OCR tra glifi comunemente confusi (`B/8`, `O/0`, `I/L/1`) può essere risolto soltanto se la lettura è ripetuta, il prefisso è visibile, la famiglia è fisicamente leggibile e una fonte ufficiale espone il codice completo.
- Watermark, didascalie, overlay, nomi file e codici troncati non sono mai prove fisiche dell'identità.
- La conferma identifica l'oggetto/versione rappresentata; non certifica autenticità, proprietà o stato di conservazione.

## OCR e barcode nativi

- `com.google.mlkit:text-recognition:16.0.1`
- `com.google.mlkit:barcode-scanning:17.3.0`
- `com.google.mlkit:object-detection:17.0.2`

## Sicurezza beta

La chiave OpenAI viene salvata nelle `SharedPreferences` private dell'app. Non è incorporata nei sorgenti o nell'APK. La versione pubblica dovrà usare **app -> backend FlipCheck -> OpenAI**.

La pipeline attiva usa soltanto la chiave OpenAI. OCR e barcode ML Kit sono locali e non richiedono altre chiavi. I percorsi Google/Gemini conservati nel sorgente storico restano disattivati e non sono collegati all'interfaccia.

## Application ID e installazione

La v1.06 conserva l'application ID `com.flipcheck.beta.nativev098.clean` e la firma stabile
della v0.98, quindi può essere installata direttamente come aggiornamento delle versioni precedenti.

## Build e test

Vedi `BUILD_STATUS_v1.06.md`. I test deterministici includono anche
`V105ExactCardCatalogRecoveryRegressionTest.java` e
`V106DeterministicCardTupleClosureRegressionTest.java`.
