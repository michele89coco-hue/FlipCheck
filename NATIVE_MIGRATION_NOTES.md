# FlipCheck v0.75 — note di consolidamento nativo

## Base recuperata

La v0.75 conserva l'app Android nativa della v0.74 e ne ricostruisce i sorgenti applicativi. Non reintroduce una WebView come runtime del riconoscimento.

## Pipeline effettiva

1. Fino a tre fotografie; la vista completa resta sempre l'evidenza primaria.
2. OCR latino e barcode ML Kit on-device, con passaggi originali, ruotati e crop laterali.
3. Estrazione conservativa di MODEL / P/N / PART / SKU / REF / TYPE / ITEM; revisione e anno restano attributi separati.
4. Osservatore visivo universale: produce fatti visibili, geometria, marchi letterali e ipotesi, senza confonderli.
5. Discovery web neutra multi-candidato a partire dalle osservazioni; l'indizio dell'utente e' soltanto un lead.
6. Le pagine realmente recuperate forniscono fonti e immagini prodotto; i candidati non collegati a tali risultati restano preliminari.
7. Confronto foto-foto per candidato: similarita', coerenza geometrica, ruolo dell'entita' e distinguibilita' della variante.
8. Universal Consistency Gate e disproof: una contraddizione forte elimina il candidato anche con similarita' alta.
9. Stati pubblici distinti: `CONFIRMED`, `PROBABLE`, `NEED_ANOTHER_PHOTO`.

## Invarianti

- Un testo non letteralmente osservato non viene presentato come OCR.
- Una pagina su un prodotto compatibile, ospitante o correlato non identifica automaticamente l'oggetto fotografato.
- Una somiglianza di famiglia non prova modello o variante esatti.
- L'assenza di un attributo e' `UNKNOWN`, non una corrispondenza e non una contraddizione.
- La conferma dell'utente puo' eliminare candidati incompatibili, ma non crea una prova indipendente.
- Marca, famiglia, modello e variante hanno confidenze separate.

## Test

Il telecomando usato nelle versioni precedenti resta soltanto uno stress test. La release deve essere valutata su categorie diverse e su casi negativi/ambigui; non e' corretto dichiarare accuratezza universale sulla base di un singolo oggetto.
