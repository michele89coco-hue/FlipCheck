# Changelog v1.02

- Corretto il falso positivo cross-brand che trasformava un Orbit WaterMaster a sei
  stazioni in Rain Bird ISA 406 usando testi e funzioni condivisi.
- Un candidato di marca non osservata, privo di confronto immagine-riferimento, non può
  più promuovere marca/famiglia dalla sola co-occorrenza dei comandi.
- Aggiunto un controllo visivo selettivo senza Web per dispositivi con pannello ricco,
  marca non letta e leader web privo di verifica visiva diretta.
- Il controllo può mantenere marca/famiglia come probabili e scartare una marca concorrente,
  ma non può confermare un modello esatto né creare un vincolo di marca fisicamente letto.
- Se il controllo visivo è indeciso, il candidato basato solo su testi condivisi resta interno
  sotto la soglia pubblica: l'app preferisce un risultato generico a una marca falsa.
- Carte, box e telecomandi generici restano sui percorsi dedicati; il costo previsto totale
  rimane bloccato a un massimo di 0,02 dollari.
