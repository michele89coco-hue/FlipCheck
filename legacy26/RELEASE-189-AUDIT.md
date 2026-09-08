# FlipCheck 189 — revisione per il rilascio

Questa revisione interviene sulle cause osservate nei report della build 188. È una candidata al rilascio con chiavi API dell'utente (BYOK). Non certifica un'accuratezza universale del 100% e non introduce un servizio commerciale di crediti o abbonamenti.

## Logiche conservate, corrette e limiti

| Area | Valutazione e intervento | Riscontro richiesto |
|---|---|---|
| Slab | Funziona sui report Charizard e Cloyster. Etichetta come base, ricerca sul titolo, certificato PSA facoltativo. Ora funziona anche senza chiave Google. | Test di flusso: una lettura iniziale e una ricerca; certificato non dichiarato verificato. |
| Nome, numero, anno | Le chiavi erano utilizzabili ma alcuni numeri espliciti finivano nel ruolo testo. Recupero di No./# con contesto fisico; nessuna promozione di statistiche, numero di maglia o Pokédex. | Boniface No.21 e controlli negativi; frazioni intere conservate. |
| Alias | Accenti, ordine e varianti ortografiche possono aiutare il nome; non cambiano numero, anno, set o seriale. | Nomi compatibili non annullano conflitti tra chiavi. |
| Query e checklist | Conservata la ricerca sul produttore/set; aggiunti sport letto sulla confezione, stagione completa e percorsi mirati anche per marchi meno comuni. | Topps Basketball non diventa baseball; 2025-2026 non viene troncato. |
| Varianti | Il solo numero carta non identifica necessariamente la parallela. Colore e tiratura richiedono corrispondenza foto/checklist. | Un verde della maglia non prova una parallela verde; /5 non assegna automaticamente un nome variante. |
| Immagini web | Una pagina corretta poteva contenere immagini di altre carte. Ora si filtrano nome, numero e soggetto della singola immagine, prima e dopo OCR. | Esclusi Ayton/Barkley per Doncic, Alakazam e 25/144 per Politoed H23/H32, collage di carte per una scatola. |
| Duplicati | Due risoluzioni della stessa immagine non sono due prove. Classificazione di tutti i metadati prima di scaricare fino a due candidati. | Deduplicazione prima di download, OCR e confronto. |
| Identità già verificata | Una fase successiva incerta o un errore poteva cancellare prove valide. Introdotto mantenimento delle prove con tracciamento delle transizioni. | Un'immagine estranea non revoca il nucleo confermato; una rilettura fisica realmente contraddittoria sì. |
| Informazioni parziali | Le chiavi effettivamente confermate restano disponibili; non si ripete l'estrazione identica. Anno mancante cercato in pagine pertinenti già note. | Politoed conserva nome/set/H23/H32; anno confermato solo da una data letterale pertinente. |
| Budget | La riserva per un confronto ancora inesistente poteva impedire la ricerca decisiva. Riserva solo con immagini realmente utilizzabili. | Tetto invariato; errori e chiamate con fatturazione incerta restano contabilizzati. |
| OCR locale | Modello Latin già incluso. Aggiunte rotazioni e ingrandimenti mirati quando la lettura è scarsa o minuta, fino a sei passaggi. | OCR Android sulla vera foto Boniface; coordinate ricondotte alla foto; cifre discordanti conservate come alternative. |
| Interfaccia e ciclo Android | Conservati selezione foto, annullamento e attività in background. Corretti esito di errore 401 e collegamenti non HTTP(S). | Nessuna risposta tardiva sovrascrive la scansione; nessuna chiave o immagine incorporata nelle diagnostiche. |
| Distribuzione | Build release non debuggabile, stesso package e stessa identità di firma. | Installazione e test sull'APK distribuito, versione e firma controllate in CI. |

## Come viene provata

La suite comprende 278 test di regole, 130 test nel browser e 25 test Android previsti dal gate. Il risultato definitivo dei gate e il riferimento della build sono nel rapporto consegnato insieme all'APK.

I nuovi test di flusso usano gli ultimi cinque report reali della build 188: Charizard, Cloyster, Topps, Doncic e Politoed. Riutilizzano risultati, utilizzo token, OCR e testi salvati. Gli involucri HTTP sono ricostruiti perché i report non contengono la risposta di rete integrale. Le immagini del replay browser sono contenitori sintetici: non misurano la capacità di riconoscere fotografie. La foto Boniface è invece usata come immagine reale nei test del modello OCR Android. Le mutazioni del caso Boniface basate sul report 187 sono esplicitamente separate dal replay 188.

Sono coperti anche errori 401, JSON incompleto o malformato, annullamento, doppio tocco, più fotografie, rimozione di una foto, OCR non disponibile, chiave Google assente, metadati facoltativi mancanti, collegamenti ostili, budget e risposte tardive. Le richieste esterne del simulatore sono intercettate; richieste inattese fanno fallire il test. Nessuna chiamata API a pagamento viene usata per questa validazione.

## Criterio di chiusura

Un punteggio 90 è una regola di decisione accompagnata da prove, non una probabilità calibrata del 90%. Nome, numero e anno aiutano a chiudere la carta base soltanto se il set è coerente; la variante esatta richiede i propri discriminanti. La chiusura dell'identità non attesta autenticità, certificazione PSA o valutazione economica. Non si aggira una vera discrepanza fisica per ottenere un risultato verde.

## Limiti residui per una pubblicazione responsabile

- L'OCR incluso è Latin. Non garantisce testi giapponesi/cinesi, dettagli sfocati, foil illeggibile o prestazioni umane. Il limite temporale viene controllato tra i passaggi, non interrompe una singola elaborazione nativa già avviata.
- Il replay dimostra la logica sulle prove disponibili; non simula ogni futura risposta di modello, motore di ricerca o sito. La disponibilità delle fonti e l'accuratezza futura richiedono prove sul campo.
- I report Topps e Doncic consentono di mantenere l'identità principale, ma non autorizzano da soli a inventare formato commerciale o parallela.
- Il contatore prova/crediti è locale. Mancano backend di fatturazione, autenticazione e controllo server dei crediti: questa versione non è un servizio pubblico a pagamento pronto alla vendita.
- Il gate Android usa Android 16 su emulatore. Non sostituisce una matrice di telefoni reali e prove di aggiornamento sul dispositivo dell'utente.

Riferimenti tecnici primari: [ML Kit Android](https://developers.google.com/ml-kit/vision/text-recognition/v2/android), [contratto Text.Line](https://developers.google.com/android/reference/com/google/mlkit/vision/text/Text.Line).

## Seconda build: 190

Il primo gate completo ha superato 278 test di regole, 130 browser e 24 test Android su 25. La vera fotografia Boniface ha fatto emergere un difetto che i replay non misurano: la prima lettura ha impiegato circa 24 secondi e ha esaurito il vecchio limite di 4,5 secondi prima delle riletture. Il seriale verticale non era stato acquisito.

La 190 separa il tempo della lettura iniziale dal recupero (fino a sei passaggi, budget di recupero di 8 secondi verificato tra passaggi), dà priorità a viste ingrandite dei bordi per testi minuti e mantiene le rotazioni generali. Il timeout dell'app concede 45 secondi alla prima richiesta OCR e 20 alle successive. Sono limiti di attesa, non ritardi fissi. Il test richiede ancora nome, numero e seriale dalla fotografia reale, controlla che le riletture siano state eseguite e salva l'output OCR completo. Non è stata introdotta alcuna risposta preimpostata per Boniface. VersionCode 190, versione 0.27.1-release-candidate.
