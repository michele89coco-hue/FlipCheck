# FlipCheck v0.26.2 · Google con chiave API · build 166

## Uso

Installare `FlipCheck-v0.26.2-GoogleFix-166.apk` sopra la versione precedente. Package e firma sono compatibili; versionCode 166.

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
