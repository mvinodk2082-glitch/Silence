# Limitations

Silence is a **DNS domain blocker**. That is powerful and privacy-friendly, but it is not magic.

## What works well

- Third-party display ads (DoubleClick, AppNexus, Criteo, …)
- Many in-app ad SDKs (AdMob network hosts, Unity Ads, Vungle, AppLovin, …)
- Trackers and analytics beacons
- **Audio/video ad tech on dedicated hostnames** (IMA SDK, pubads, FreeWheel, AdSwizz, …)

## What often still gets through

### 1. First-party / same-origin ads
If the ad creative is served from the same domain as the main app/site content, DNS cannot distinguish ad from content without breaking the app.

### 2. Server-side ad insertion (SSAI)
Streaming services may stitch ads into the media playlist on the **same CDN hostname** as the show. There is no separate ad domain to sinkhole.

### 3. YouTube / some Google video surfaces
Parts of the ad path use hosts you *can* block (e.g. `*.googlesyndication.com`, `pubads.g.doubleclick.net`, `imasdk.googleapis.com`). Other parts are deeply intertwined with Google video infrastructure. Expect **reduced** ads, not zero, especially in the official YouTube app.

### 4. Hardcoded IPs
Rare clients that skip DNS will not hit the filter.

### 5. Private DNS / DoH inside apps
If the system or an app uses encrypted DNS outside Silence’s fake DNS, queries may never appear on the TUN.  
**Mitigation:** set Private DNS to Off or Automatic while testing; future versions can warn.

### 6. IPv6 extension headers
Phase 1 handles **IPv6 UDP DNS without extension headers**. Unusual stacks that insert hop-by-hop options on DNS may not be filtered until a full IPv6 header chain parser is added.

### 7. VPN conflicts
Only one VPN can be active. Corporate VPNs and Silence cannot run together. Some apps refuse to run under any VPN — use **Apps → exclusions**.

## Aggressive profile trade-off

Aggressive adds more media-ad hosts and can break legitimate features (telemetry-heavy apps, some free streaming). Prefer **Balanced**, then allowlist domains that break.

## What would be needed for “every ad including SSAI”

| Technique | Cost |
|-----------|------|
| HTTPS MITM + local CA + content rules | High trust, pinning breaks |
| App-specific clients (e.g. alternative YouTube frontends) | Not system-wide |
| Root + deeper hooks | Not stock Android |

Silence’s product bet: **maximum ads blocked with zero root and zero remote trust**, accepting SSAI limits honestly.
