# Silence

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![GitHub release](https://img.shields.io/github/v/release/mvinodk2082-glitch/Silence?include_prereleases)](https://github.com/mvinodk2082-glitch/Silence/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/mvinodk2082-glitch/Silence/total)](https://github.com/mvinodk2082-glitch/Silence/releases)

**Device-wide Android ad & tracker blocker** — local DNS filter via Android VPN API.  
**No root. No account. No remote VPN server. Open source (GPL-3.0).**

### ⬇ Download for anyone (non-developers)

| | |
|--|--|
| **Latest APK** | https://github.com/mvinodk2082-glitch/Silence/releases/latest |
| **Website** | https://mvinodk2082-glitch.github.io/Silence/ |
| **Auto-updates** | Install [Obtainium](https://github.com/ImranR98/Obtainium) → add `https://github.com/mvinodk2082-glitch/Silence` |
| **Privacy** | [docs/PRIVACY.md](docs/PRIVACY.md) |

> Phone: allow *Install unknown apps* → open APK → **Turn protection on** → accept the VPN prompt (local filter only).

---

## Why people need this

Ads and trackers appear in **browsers and apps**. Browser extensions cannot clean in-app SDKs. Silence sits at the only whole-device choke point Android allows without root: a **local** `VpnService` that filters **DNS**.

## Features

| Area | Capability |
|------|------------|
| Coverage | System-wide DNS blocking (apps + browsers) |
| Privacy | On-device only; no analytics; no remote tunnel |
| Lists | Built-in samples + remote packs (StevenBlack, AdGuard, OISD, …) |
| Media ads | Optional audio/video ad domains (DNS limits apply) |
| Controls | Profiles, allow/block rules, per-app exclusions |
| Reliability | Battery tips, Always-on VPN guide, restart, boot resume |
| Languages | English + ES / HI / PT-BR system strings (UI expanding) |

**Honest limits:** DNS cannot block ads served from the same host as content (some streaming). See [docs/LIMITATIONS.md](docs/LIMITATIONS.md).

## For maintainers: make it visible worldwide

See **[docs/DISTRIBUTION.md](docs/DISTRIBUTION.md)** — GitHub Releases, Obtainium, IzzyOnDroid, F-Droid, Reddit/XDA, Play Store risks, signing.

**Short priority list:**

1. Keep [Releases](https://github.com/mvinodk2082-glitch/Silence/releases) updated with APK + SHA-256  
2. Share Obtainium + latest release link  
3. Submit to **IzzyOnDroid**, then **F-Droid** (`fastlane/metadata` ready)  
4. Post once in FOSS/privacy communities (no spam)  
5. Optional: release-signed keystore ([docs/SIGNING.md](docs/SIGNING.md))

## Architecture

```
Apps & browsers → DNS → Silence (block or forward) → upstream DNS
                 └── only fake DNS IP enters the local TUN
```

Details: [ARCHITECTURE.md](ARCHITECTURE.md) · Reliability: [docs/RELIABILITY.md](docs/RELIABILITY.md)

## Build from source

```bash
# JDK 17 + Android SDK 35
./gradlew :app:assembleDebug
./gradlew :app:test
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Project layout

```
app/…/com/silence/blocker/
  vpn/ filter/ dns/ lists/ ui/ data/
docs/          privacy, distribution, site
fastlane/      store listing metadata (F-Droid / Play style)
```

## Security

Report vulnerabilities privately — see [SECURITY.md](SECURITY.md).

## License

[GPL-3.0](LICENSE) — free to use, study, share, and improve.
