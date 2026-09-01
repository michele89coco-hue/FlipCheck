# FlipCheck Native v0.89

- Verifica tirature molto piccole negli angoli della carta tramite un fatto Vision strutturato e fisicamente localizzato, anche quando l'OCR non legge `1/1`.
- Un valore digitato dall'utente resta un indizio: non diventa prova senza verifica visiva sulla superficie della carta.
- Rimuove dai riferimenti pubblici le tirature di checklist incompatibili con la copia fotografata (`/120` contro `8/9`).
- Distingue identità della carta e tiratura fisica dal nome commerciale del parallel, che resta non determinato se la fonte non lo dimostra.
- Deduplica marca, famiglia e modello nei candidati e normalizza `Chrome Update Basketball`.
- Tratta l'assenza della targhetta modello e del confronto visivo del manuale come lacune di prova, non come contraddizioni della famiglia Orbit.
- Richiede foto successive specifiche: retro/angoli della carta, lato/barcode del box o targhetta interna del controller.
- Mantiene selezione multipla, fotocamera, analisi in background e massimo una ricerca Web.
