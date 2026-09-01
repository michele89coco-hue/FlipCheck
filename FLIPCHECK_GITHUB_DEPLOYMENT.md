# Collegamento a GitHub e build firmata (repo privata `FlipCheck`)

## 1) Collegamento repo
1. Crea su GitHub una repo privata chiamata `FlipCheck` (sotto il tuo utente o organizzazione).
2. Nel terminale (project root):
   - `git init`
   - `git add .`
   - `git commit -m "Initial import FlipCheck Android"`
   - `git branch -M main`
   - `git remote add origin https://github.com/<TUO_UTENTE_O_ORG>/FlipCheck.git`
   - `git push -u origin main`

## 2) Segreti GitHub da impostare (Actions -> Secrets and variables -> Actions)
- `FLIPCHECK_KEYSTORE_BASE64`
- `FLIPCHECK_KEYSTORE_PASSWORD`
- `FLIPCHECK_KEY_ALIAS`
- `FLIPCHECK_KEY_PASSWORD`

Nota: inserire il keystore base64 con la tua chiave privata già usata dagli APK già installati, così il nuovo APK manterrà la stessa firma e aggiorna senza disinstallazione.

## 3) Come ottenere il base64 del keystore
- Windows PowerShell: ` [Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\\percorso\\flipcheck-release.jks"))`
- Linux/macOS: `base64 -w0 flipcheck-release.jks`

## 4) Conversione firma (facoltativa)
Nel repo è stato mantenuto il fingerprint documentato nei BUILD_STATUS (`8ae3136a2c55f4721ed2b7e351a932094e77d606da27b8176d72ef052783dd22`) come riferimento per verificare la continuità della firma dopo la build.
