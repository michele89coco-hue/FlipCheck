# Collegamento, firma e prima build (repo privata `FlipCheck`)

## 1) Stato attuale
Ho già preparato la pipeline e il codice è impostato con release firmata.
Il progetto usa variabili/segreti:
- `FLIPCHECK_KEYSTORE_BASE64`
- `FLIPCHECK_KEYSTORE_PASSWORD`
- `FLIPCHECK_KEY_ALIAS`
- `FLIPCHECK_KEY_PASSWORD`

Nel caso non hai più il keystore originale, al termine trovi la procedura alternativa (firma nuova) e la nota "una tantum" per telefono.

## 2) Imposta i Secrets (solo una volta)
GitHub -> Impostazioni repo `FlipCheck` -> Secrets and variables -> Actions -> New repository secret

### Se hai il keystore originale (aggiornamenti diretti)
1. `FLIPCHECK_KEYSTORE_PASSWORD` = password del keystore.
2. `FLIPCHECK_KEY_ALIAS` = alias della chiave.
3. `FLIPCHECK_KEY_PASSWORD` = password della chiave.
4. `FLIPCHECK_KEYSTORE_BASE64` = contenuto Base64 del file `.jks/.keystore`.

Comando Windows (PowerShell):
```powershell
$path = "C:\percorso\flipcheck-release.jks"
[Convert]::ToBase64String([IO.File]::ReadAllBytes($path)) | Set-Content C:\temp\flipcheck-keystore-base64.txt
```

### Se il keystore originale NON è disponibile (crea nuova firma stabile)
1. Crea un nuovo keystore Android (una tantum):
```powershell
keytool -genkeypair ^
  -v ^
  -keystore C:\temp\flipcheck-release.jks ^
  -alias flipcheck-release ^
  -keyalg RSA ^
  -keysize 4096 ^
  -validity 10000 ^
  -storetype JKS ^
  -dname "CN=FlipCheck, OU=Mobile, O=FlipCheck, L=Rome, ST=Roma, C=IT"
```
2. Salva subito:
- `FLIPCHECK_KEYSTORE_PASSWORD`
- `FLIPCHECK_KEY_ALIAS` = `flipcheck-release`
- `FLIPCHECK_KEY_PASSWORD` (puoi usare la stessa del keystore)
3. Codifica Base64 e imposta `FLIPCHECK_KEYSTORE_BASE64` come sopra.

## 3) Nota precisa “una tantum” se hai dovuto creare nuovo keystore
La nuova firma non potrà aggiornare APK firmati con la chiave precedente.

Sul telefono fai una sola volta:
1. Disinstallare l’app esistente `com.flipcheck.beta.nativev098.clean` (tutti i dati verranno rimossi).
2. Installare la prima APK della nuova serie firmata con il nuovo keystore.
3. Da quel momento in poi, tutti i build futuri con la stessa chiave si aggiorneranno in-over-the-air senza altre disinstallazioni.

## 4) Avviare la build firmata
### Manuale (sicuro)
1. Apri GitHub -> `Actions` -> workflow `Android Signed APK`.
2. Clicca **Run workflow**.
3. Conferma su branch `main`.

### Da terminale (se preferisci)
```bash
cd "C:\path\to\repo"
git checkout main
git push origin main
```
Il push su `main` avvia anche la build automaticamente (trigger push).

## 5) Verifiche rapide dopo la build
- Vai su `Actions` -> run completato -> scarica artifact `flipcheck-release-apk-*`.
- L'APK deve trovarsi in `app/build/outputs/apk/release/`.
- Nella scheda Actions viene mantenuta solo la retention logica delle ultime 3 build firmate (cleanup automatico).
