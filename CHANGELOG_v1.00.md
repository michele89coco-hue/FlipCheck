# FlipCheck Native v1.00

- Correzione universale della categoria tramite segnali fisici: una carta sciolta non resta
  bloccata come `sealed_products` quando presenta struttura, numero e assi tipici di una carta.
- I fatti visivi strutturati di carte sportive e TCG vengono consolidati nella stessa tupla
  identitaria usata dai gate deterministici.
- Le viste fronte/retro possono chiudere numero, parallel, rookie e finitura senza dipendere
  dal solo flag `photo_identity.complete` restituito dal modello.
- Le carte TCG con fronte completo possono essere rivalutate dopo il match esatto di catalogo,
  conservando stampa, edizione e finitura fisicamente osservate.
- Una box sigillata con tupla completa e almeno un riscontro OCR locale può chiudersi anche
  quando la ricerca web non restituisce candidati superstiti.
- Nessun nome di carta, giocatore o SKU è codificato nelle regole di produzione.

