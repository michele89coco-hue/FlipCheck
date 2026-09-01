# FlipCheck Native v1.06

- Corretto il difetto osservato con Stephen Curry: una carta sportiva con fronte e retro completi non resta più `Panini collectible trading card` se il catalogo Web non restituisce una scheda perfetta.
- Le carte informative riservano la seconda chiamata alla ricerca catalogo, evitando che una rilettura OCR senza Web consumi prima il budget.
- Aggiunta una chiusura deterministica basata sulla tupla fisica: produttore, soggetto, linea/set, anno o numero collezione, viste complementari e almeno due discriminanti indipendenti.
- Il Web resta una corroborazione e può aggiungere numero, parallel o RC; questi attributi non vengono mai inventati quando non sono fisicamente visibili o verificati.
- Le contraddizioni forti continuano a bloccare la conferma.
- Per TCG resta obbligatorio il numero collezione fisicamente leggibile; per le sportive senza numero sono obbligatori fronte/retro e più campi discriminanti.
- Budget massimo della ricerca aggiuntiva invariato: 0,025 USD.
- Regressioni deterministiche: 20/20 PASS, incluso il caso Curry reale senza candidato Web.
