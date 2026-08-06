# Release signing

Release builds are non-debuggable and minified. A signed release APK is uploaded by GitHub Actions only when all four repository secrets below are configured:

- `RELEASE_KEYSTORE_BASE64`
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

Create a keystore locally and keep it outside the repository:

```powershell
keytool -genkeypair -v -keystore timestampcamera-release.jks -alias timestampcamera -keyalg RSA -keysize 2048 -validity 10000
```

Encode the keystore for `RELEASE_KEYSTORE_BASE64`:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path .\timestampcamera-release.jks)))
```

Add the four values at **Repository Settings > Secrets and variables > Actions**. Never commit the keystore or passwords. Back up the keystore securely: future upgrades must use the same signing key.

Without these secrets, CI still compiles an unsigned release variant to validate release-only code and uploads only the debug APK.
