# Silence

[![Android CI](https://github.com/mvinodk2082-glitch/Silence/actions/workflows/android.yml/badge.svg)](https://github.com/mvinodk2082-glitch/Silence/actions/workflows/android.yml)

**Device-wide Android ad & tracker blocker** using a **local VPN** and **DNS domain filtering**. No root. No remote VPN server. Traffic is filtered on-device only.

> Version `0.4.0` (Phase 3) — open in Android Studio, grant VPN permission, and turn protection on.

## Download CI build (GitHub Actions)

1. Open [Actions → Android CI](https://github.com/mvinodk2082-glitch/Silence/actions/workflows/android.yml)
2. Open the latest successful run
3. Download the **silence-debug-apk** artifact
4. Install on your phone (allow “install unknown apps” for your browser/Files app)

Or after a release workflow run:

```bash
gh run download -R mvinodk2082-glitch/Silence -n silence-debug-apk
```

## Why this exists

Ads follow you across browsers **and** apps. Browser extensions cannot touch in-app SDKs. Silence sits at the network choke point Android *does* allow without root: `VpnService`, then sinkholes known ad, tracker, and media-ad hostnames at DNS time.

## Features (scaffold)

| Feature | Status |
|---------|--------|
| Local VPN (no remote exit) | Yes |
| DNS A/AAAA sinkhole for blocked hosts | Yes |
| IPv4 + IPv6 fake DNS path | Yes (Phase 1) |
| Upstream retries, fallbacks, TCP fallback | Yes (Phase 1) |
| Private DNS bypass warning | Yes (Phase 1) |
| Stats persist across process death | Yes (Phase 1) |
| Embedded ad / tracker / media-ad lists | Yes |
| Quiet notification (no sound) | Yes |
| Compose UI: Home, Stats, Lists, Rules, Apps, Settings | Yes |
| Profiles: Balanced / Aggressive / Privacy | Yes (Aggressive expands media list) |
| Custom allow / block rules | Yes |
| Per-app package exclusions | Yes (manual package names) |
| Upstream DNS presets (Cloudflare / Quad9 / Google / AdGuard) | Yes |
| Remote list packs + on-disk cache | Yes (Phase 2) |
| Update now / daily WorkManager auto-update | Yes (Phase 2) |
| Wi‑Fi-only list downloads | Yes (Phase 2) |
| Installed-app exclusion picker | Yes (Phase 2) |
| Battery unrestricted prompt | Yes (Phase 3) |
| Always-on VPN guidance | Yes (Phase 3) |
| Restart protection + exclusion apply | Yes (Phase 3) |
| Loop health watchdog / sticky recovery | Yes (Phase 3) |
| Power-saver flush mode | Yes (Phase 3) |
| Full packet / HTTPS MITM | Out of scope |

## Architecture (short)

```
Apps & browsers → system DNS → Silence fake DNS (10.10.10.1)
                                    │
                         DomainFilter + blocklists
                                    │
                    BLOCK → 0.0.0.0 / ::    ALLOW → upstream DNS (e.g. 1.1.1.1)
```

Only the fake DNS IP is routed into the TUN. Normal TCP/UDP traffic keeps using your Wi‑Fi/cellular path. See [ARCHITECTURE.md](./ARCHITECTURE.md).

## Requirements

- Android Studio Ladybug+ (or equivalent) with Android SDK 35
- JDK 17
- Physical device recommended for VPN testing (emulators vary)

## Build & run

```bash
cd /home/mkvk/Documents/Silence
# With Android Studio: Open this folder, wait for Gradle sync, Run 'app'
# Or CLI (when SDK + wrapper jars are installed):
./gradlew :app:assembleDebug
./gradlew :app:test
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

1. Open the app  
2. Tap **Turn protection on**  
3. Accept the Android VPN consent (local filter only)  
4. Browse; check **Stats** / Home for blocked counts  

## Audio / video ads

Media-oriented hosts (IMA SDK, DoubleClick pubads, AdSwizz, Vungle, AppLovin, …) are in the **Media ads** category (on by default).  

DNS **cannot** strip ads that share the same hostname as the content stream (SSAI / first-party). Details: [docs/LIMITATIONS.md](./docs/LIMITATIONS.md).

## Privacy

- No accounts, no analytics SDK, no remote tunnel  
- Blocklists may be fetched over HTTPS when you enable remote updates  
- Stats stay on device  

## Project layout

```
app/src/main/java/com/silence/blocker/
  vpn/      SilenceVpnService, PacketLoop, VpnController
  dns/      DnsPacket, DnsServer, UpstreamResolver
  filter/   DomainFilter, FilterEngine
  lists/    BuiltinLists, ListRepository
  data/     SettingsStore, UserRulesStore
  ui/       Compose screens
```

## Roadmap

1. **Phase 1** — harden DNS path, IPv6, Private DNS warnings ✅  
2. **Phase 2** — remote list packs, installed-app picker, scheduled updates ✅  
3. **Phase 3** — battery, Always-on VPN, recovery ✅  
4. **Optional** — HTTPS inspection (high trust cost)  

Reliability details: [docs/RELIABILITY.md](./docs/RELIABILITY.md)

## License

Choose a license before publishing (e.g. GPL-3.0 common for ad blockers, or MIT for broader reuse).
