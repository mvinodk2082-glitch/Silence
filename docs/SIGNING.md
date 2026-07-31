# Release signing

Debug builds are fine for early testers. For a **global** audience, ship **release-signed** APKs.

## Generate a keystore (once, keep offline backup)

```bash
keytool -genkey -v \
  -keystore silence-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias silence
```

**Never commit** `*.jks` or passwords to git.

## `keystore.properties` (local only)

```properties
storeFile=/absolute/path/to/silence-release.jks
storePassword=****
keyAlias=silence
keyPassword=****
```

Add to `.gitignore` (already ignores secrets patterns if you add this file).

## Wire into Gradle (optional next step)

Use `signingConfigs` in `app/build.gradle.kts` reading `keystore.properties`, then:

```bash
./gradlew :app:assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

## Publish integrity

In every GitHub Release:

```bash
sha256sum app-release.apk
```

Paste the hash in the release notes so users can verify downloads.
