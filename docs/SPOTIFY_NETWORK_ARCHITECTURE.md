# Spotify + Silence: Multi-Layer Network Architecture

**Status:** Living design doc  
**Product decision:** Option A — **split-tunnel Spotify (VPN exclude) + optional L3 media mute/skip**

## 1. Problem statement

Users want:

1. Silence **on** (block ads system-wide), and  
2. Spotify **online**, and  
3. Spotify **free-tier ads reduced or gone**.

Those three goals conflict on stock Android:

| Goal | Mechanism | Conflict |
|------|-----------|----------|
| System-wide block | Local `VpnService` DNS filter | Spotify often marks any VPN as offline |
| Spotify online | Exclude Spotify from VPN **or** never use VPN | Excluded app is not DNS-filtered |
| Kill free audio ads | DNS sinkhole ad hosts | Music and many ads share CDN/API hosts over HTTPS |

### What Spotify Free looks like on the wire

```
Spotify app ──HTTPS (pinned)──► API + CDN mesh
                 │
                 ├── music segments (scdn.co / spotifycdn / Akamai …)
                 ├── free audio ads (often same mesh / SSAI-style)
                 └── telemetry / some dedicated ad beacons (sometimes distinct)
```

- **SSAI / same-path ads:** the client is told to play a stream that *is* the ad. There is no separate “ad hostname” to sinkhole safely.  
- **Certificate pinning:** a local VPN cannot read HTTPS URLs without MITM + usually unpinning (root).  
- **VPN interface:** product policy frequently → UI “Offline”.

## 2. Silence control / data plane (today)

```
┌──────────────────────────────────────────────────────────┐
│ Control plane: UI, lists, allow/block, excludes          │
└──────────────────────────────────────────────────────────┘
┌──────────────────────────────────────────────────────────┐
│ Data plane: SilenceVpnService                            │
│  • Only fake DNS IPs routed into TUN                     │
│  • DNS A/AAAA → DomainFilter → sinkhole or upstream      │
│  • addDisallowedApplication(pkg) → app never sees VPN    │
└──────────────────────────────────────────────────────────┘
```

**CompatibilityAllowlist** always allows core Spotify domains so remote packs cannot black-hole playback when Spotify is *not* excluded.  
**Apps → Bypass Spotify** excludes `com.spotify.music` so Spotify stays online under active Silence.

## 3. Layer catalog (L0–L7)

| Layer | Name | Spotify online? | Free audio ads | Root? | Maintenance | Silence fit |
|-------|------|-----------------|----------------|-------|-------------|-------------|
| **L0** | Premium / alt client / web+uBlock | Yes | Premium: none; others: varies | No | Low | Document only |
| **L1** | DNS surgical lists | Risk if over-block | Weak (shared CDN) | No | Medium | Core product (other apps) |
| **L2** | Split-tunnel exclude | Yes | None blocked | No | Low | **Required for online** |
| **L3** | Media mute/skip | Yes (with L2) | Partial–good | No | Medium | **Option A build** |
| **L4** | HTTPS MITM | Maybe | Partial; pinning fails | Often yes | High | Poor ROI for Spotify |
| **L5** | Protocol reverse proxy | Maybe | High if maintained | No* | Very high | Out of scope |
| **L6** | Patched Spotify APK | Yes | Often high | No | High + malware risk | Do not ship |
| **L7** | Magisk / LSPosed hooks | Yes | High | Yes | High | Future optional |

\*Desktop tools exist; Android equivalents are rare and fragile.

### L1 detail — why DNS alone fails for Spotify ads

- Blocking parent `spotify.com` / `scdn.co` → offline or broken seek.  
- Allowlisting them (correct for stability) → DNS cannot drop ads on those hosts.  
- Community “Spotify ad domain” lists either (a) stop working or (b) break playback.

### L3 detail — media plane (no packet decrypt)

```
MediaSession / Notification
        │
        ▼
  Heuristic: is this an ad?
        │
   yes ─┼─► mute STREAM_MUSIC and/or skipToNext()
   no  ─┴─► ensure unmuted
```

Does not need VPN path into Spotify. Works **with** L2 exclude.

## 4. Recommended deployment modes

| Mode | Config | Other apps | Spotify |
|------|--------|------------|---------|
| **Stability** | L2 exclude | L1 DNS | Online, ads unfiltered |
| **Balanced (A)** | L2 + L3 mute | L1 DNS | Online, ads often muted/skipped |
| **Aggressive DNS** | Spotify through VPN + surgical list | L1 | Offline risk; weak ad kill |
| **Nuclear** | L0 Premium or mod client | L1 | Near-zero ads |

## 5. Decision matrix (ROI)

| Investment | Value for Spotify free ads | Recommend |
|------------|----------------------------|-----------|
| L2 exclude UX | Unblocks product | Already shipping |
| **L3 media guard** | Best no-root ad reduction | **Yes (this release)** |
| L1 curated telemetry-only | Minor tracker cut | Optional later |
| L4 MITM | Low vs pinning/SSAI | No |
| L6 mods | High ads gone, high risk | User choice only |

## 6. Non-goals / marketing honesty

- Silence will **not** claim “100% ad-free Spotify Free via DNS.”  
- L3 is **experimental** and may break when Spotify changes metadata.  
- We do **not** ship modded Spotify binaries.

## 7. Implementation sketch (Option A)

See app package `com.silence.blocker.media`:

- `MediaAdGuard` — enable/disable, settings bridge  
- `SpotifyAdDetector` — heuristics on metadata  
- `SpotifyMediaWatcher` — `MediaSessionManager` + optional notification listener  
- `PlaybackAdActions` — mute/unmute + skip  

**Settings:** “Mute Spotify ads (experimental)” → ensures Spotify VPN exclude + starts watcher.

## 8. Test plan (manual)

1. Protection ON, Spotify **not** excluded → often Offline (baseline).  
2. Bypass Spotify → Online, ads play.  
3. Enable L3 mute → free ads muted or skipped; music volume returns after ad.  
4. Toggle L3 off → volume normal; ads audible again.  
5. Chrome/other apps still DNS-blocked.

## 9. Related files

- `lists/CompatibilityAllowlist.kt` — domain allowlist  
- `ui/apps/AppsScreen.kt` — bypass buttons  
- `docs/LIMITATIONS.md` — user-facing short version  
