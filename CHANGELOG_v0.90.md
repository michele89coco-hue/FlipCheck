# FlipCheck Native v0.90

- Corretto il crash dopo `SCATTA FOTO`: la UI carica una miniatura campionata e mantiene l'originale soltanto per OCR/analisi.
- Aggiunta visibilità esplicita dell'intent fotocamera nel manifest Android.
- Distinta la semantica `x/y`: tiratura sulle carte sportive, numero collezione nei TCG (es. Pokémon `10/102`).
- Una stampa/variante TCG irrisolta impedisce la conferma esatta, soprattutto quando incide sui comps.
- Vietato `CONFIRMED` con modello vuoto.
- Rimossi dai dettagli pubblici i sentinel negativi come `none identified`.
- Richiesta foto specifica per smartphone: `Impostazioni > Info sul telefono`.
- Una tupla completa stampata su un box sigillato resta almeno a livello famiglia anche se la ricerca non restituisce candidati grounded.
- Normalizzate anche le ipotesi Vision per evitare duplicazioni come `Topps Topps`.
