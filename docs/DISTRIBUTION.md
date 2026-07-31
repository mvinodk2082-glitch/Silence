# How to make Silence visible worldwide

This guide is for maintainers and contributors who want people who need an ad blocker to **find** and **trust** the APK.

## 1. Primary download hub (do this first)

| Channel | Why | Action |
|---------|-----|--------|
| **GitHub Releases** | Free, versioned, trusted by power users | Tag every build (`v0.x.x`), attach signed APK |
| **Project website (GitHub Pages)** | Simple landing page for non-developers | Enable Pages → `docs/site` |
| **README badges** | First thing people see on the repo | Big “Download APK” link at top |

Direct pattern people share:

```text
https://github.com/mvinodk2082-glitch/Silence/releases/latest
```

## 2. App stores & catalogs (high discovery)

| Store | Fit for Silence | Notes |
|-------|-----------------|-------|
| **[IzzyOnDroid](https://apt.izzysoft.de/fdroid/)** | Excellent | Accepts many FOSS Android apps outside main F-Droid; often faster listing |
| **[F-Droid](https://f-droid.org/)** | Excellent long-term | Needs GPL/Apache/MIT, reproducible build preferred, metadata under `metadata/` or fdroiddata MR |
| **Google Play** | Hard for ad blockers | Policy risk: “interfere with ads”; may need careful framing as privacy DNS filter; paid developer account |
| **Amazon Appstore / Samsung Galaxy Store** | Secondary | Similar policy review |
| **APKPure / Uptodown** | Optional | Submit APK + screenshots; mixed trust signals |

### F-Droid checklist

1. GPL-3.0 (done) + public source  
2. No proprietary libraries  
3. `fastlane/metadata/android/en-US/` descriptions (done)  
4. Open issue / MR on [fdroiddata](https://gitlab.com/fdroid/fdroiddata)  
5. Prefer reproducible release builds  

### IzzyOnDroid checklist

1. Email/issue with app ID `com.silence.blocker`, source URL, license  
2. Host APK on GitHub Releases  
3. They pull updates from tags  

## 3. Update clients (people stay forever)

| Tool | Why |
|------|-----|
| **[Obtainium](https://github.com/ImranR98/Obtainium)** | Users track GitHub Releases and auto-notify of new APKs — **promote this** |
| **[Droid-ify / F-Droid client](https://f-droid.org/)** | After F-Droid listing |
| In-app “Check for updates” | Opens Releases page (in app Settings) |

Tell users:

> Install Obtainium → Add app → paste  
> `https://github.com/mvinodk2082-glitch/Silence`

## 4. Communities that already want this

Post **once** with: problem → local VPN not remote → privacy → download link → limitations (honest).

| Community | Angle |
|-----------|--------|
| Reddit r/fossdroid, r/privacytoolsIO, r/androidapps | FOSS + privacy |
| Reddit r/adb, r/pihole | Network-level blocking |
| XDA Developers forum | Custom ROM / power users |
| Privacy Guides discussions | Tool listing (follow their rules) |
| Telegram privacy FOSS groups | Soft share, no spam |
| Discord privacy servers | Same |
| Mastodon / Bluesky FOSS hashtags | `#FOSS #Android #Privacy` |
| Product Hunt / AlternativeTo | Product listing |

**Do not** spam. One quality post + answer questions builds trust.

### Suggested short post

```text
Silence — free, open-source, no-root Android ad & tracker blocker.

Uses a local VPN as a DNS filter only (traffic stays on your device).
Blocks ads in browsers + many apps. Optional media-ad lists.

Download: https://github.com/mvinodk2082-glitch/Silence/releases/latest
Source / privacy: https://github.com/mvinodk2082-glitch/Silence
Limitations: cannot block same-origin/SSAI video ads (honest DNS limits).
```

## 5. SEO & discoverability

- Repo description: `Open-source Android ad blocker · local VPN DNS · no root · privacy`
- Topics (GitHub): `android`, `adblock`, `dns`, `privacy`, `foss`, `vpn-service`, `kotlin`
- Website title: “Silence — Device-wide Android Ad Blocker”
- AlternativeTo, Slant, Softpedia (optional submissions)

## 6. Trust signals (global users need these)

- [x] Public source code  
- [x] Privacy policy  
- [x] License (GPL-3.0)  
- [ ] Screenshots (add under `fastlane/…/images`)  
- [ ] Reproducible release signing key published (fingerprint)  
- [ ] Security policy (`SECURITY.md`)  
- [ ] Multiple languages in UI  

## 7. Signing for “real” global distribution

Debug APKs work for testing. For public trust:

1. Generate an upload keystore (offline, backed up)  
2. `./gradlew assembleRelease`  
3. Publish **only** release-signed APKs on Releases  
4. Publish SHA-256 of the APK in release notes  

See [SIGNING.md](./SIGNING.md).

## 8. Metrics (optional, privacy-preserving)

- GitHub Release download counts (built-in)  
- No Google Analytics on the site (use none or Plausible self-host)  

## Priority order (recommended)

1. Polish GitHub Release + Pages site  
2. Tell people to use **Obtainium**  
3. Submit to **IzzyOnDroid**  
4. F-Droid metadata + MR  
5. Community posts (Reddit/XDA)  
6. Play Store only if legal/product review is intentional  
