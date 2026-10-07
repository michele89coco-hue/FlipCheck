# FlipCheck 228 originale — installazione 237

[Scarica l’APK dalla release](https://github.com/michele89coco-hue/FlipCheck/releases/tag/original-228-20261006-install237).

FlipCheck 228 originale del 6 ottobre 2026, ripubblicata con versione Android 2.31-test237 (versionCode 368).

Cambia esclusivamente la versione Android per consentire l’aggiornamento sopra le build fino alla 236 (versionCode 367). Tutti i 566 elementi del contenuto restano identici alla 228 originale: codice, Gemini, prompt, cataloghi, prezzi, risorse e interfaccia. Le etichette interne e i log possono continuare a indicare 228.

Firma APK v1/v2/v3 verificata con il certificato originale; allineamento 16 KB verificato. Controllo completo del contenuto: 599 verifiche superate. L’installazione sul telefono non è stata eseguita in questa verifica.

Il pacchetto firmato è archiviato in parti binarie ordinate nella cartella `payload`, per rispettare le dimensioni dei file Git. `manifest.json` registra dimensione e SHA-256 di ogni parte e dell’APK completo. La release contiene l’APK completo direttamente installabile.

Per ricostruire esattamente l’APK archiviato:

```bash
python3 distribution/original-228-install237/assemble_apk.py
```

Il codice applicativo non viene ricompilato. Il workflow assembla e pubblica gli stessi byte dell’APK firmato e verificato. Non servono credenziali di firma.
