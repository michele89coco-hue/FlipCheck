# FlipCheck Native v0.94

Ricostruita direttamente dalla base stabile v0.92.

## Correzioni

- Lo scatto della fotocamera viene conservato in MediaStore e recuperato al ritorno nell'app anche quando la fotocamera Samsung non consegna `onActivityResult`.
- Le URI delle foto già selezionate sopravvivono alla ricreazione della schermata durante una sessione fotografica.
- Una nuova scansione azzera risultato e indizio facoltativo precedenti, evitando contaminazioni come `24 Ultra` nell'analisi di una carta.
- Pokémon Base Set riconosce `1st Edition` soltanto quando il timbro è visibile nella posizione fisica corretta sotto e a sinistra dell'illustrazione.
- Un timbro `1st Edition` correttamente localizzato prevale su una più debole e potenzialmente errata lettura dell'ombra del riquadro.
- Quando il timbro è assente, la posizione è `not_applicable`; testo sorgente o OCR fuori area non vale come timbro.

## Comportamenti preservati dalla v0.92

- Mewtwo Shadowless e Pokémon Unlimited da una foto frontale quando i segnali fisici convergono.
- Carte sportive con fronte/retro, numero carta e parallela coerenti.
- Box Hobby e altri prodotti sigillati chiusi dalla tupla commerciale stampata.
- Samsung Galaxy S24 Ultra chiuso al livello commerciale senza separare i suffissi regionali.
- Telecomandi Philips prudentemente mantenuti come famiglia/riferimento probabile senza codice fisico.
- Analisi in background e selezione multipla fino a tre foto.

`CONFIRMED` identifica l'oggetto o la versione visibile; non certifica autenticità o condizione.
