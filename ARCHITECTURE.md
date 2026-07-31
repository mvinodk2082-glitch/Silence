# Silence architecture

## Problem

Android offers no system-wide content filter API for third-party apps. Ads load in:

- Browsers (often third-party hosts)
- In-app WebViews
- Native ad SDKs (network calls to ad exchanges)
- Audio/video ad beacons and VAST endpoints

Without root, the supported interception surface is **`VpnService`**: a local TUN that can observe or respond to selected traffic.

## Design choice: DNS-only local VPN

| Approach | Pros | Cons |
|----------|------|------|
| **DNS-only (chosen)** | Simple, private, low battery, hard to MITM yourself | Misses same-origin/SSAI ads; Private DNS can bypass |
| Full userspace TCP stack | Can filter HTTP URLs | Large, battery-heavy, complex |
| HTTPS MITM + local CA | Deep ad stripping | Trust prompts, breaks pinning, Play policy risk |
| Root hosts/iptables | Strong, system-wide | Root only |

Silence implements **DNS-only**:

1. Establish VPN with addresses `10.10.10.2/32` and `fd00:10:10:10::2/128`
2. Advertise DNS `10.10.10.1` and `fd00:10:10:10::1`
3. Route **only** those fake DNS prefixes into the TUN
4. Parse IPv4/IPv6 UDP/53 queries on the TUN
5. If host matches blocklists → sinkhole A=`0.0.0.0` / AAAA=`::`
6. Else forward query to upstream DNS (primary + fallbacks; UDP retries; TCP if truncated/fail) on `protect()`ed sockets
7. On total upstream failure → SERVFAIL so clients do not hang forever

Non-DNS traffic never enters Silence.

### Phase 1 hardening

- **IPv6 DNS**: UDP checksum with IPv6 pseudo-header
- **UpstreamResolver**: multi-host fallback (`1.1.1.1` → `9.9.9.9` → `8.8.8.8` by default), 1.5s UDP timeout × 2 attempts, TCP fallback
- **PrivateDnsMonitor**: reads `private_dns_mode` / `private_dns_specifier`; UI warning when Strict or Opportunistic
- **StatsStore**: flush blocked/allowed every 15s while VPN is up; hydrate on app/VPN start; midnight day rollover
- **FilterEngine**: throttled UI publish on allow path (every 25 allows) to reduce main-thread pressure

## Components

```
UI (Compose) ──► VpnController ──► SilenceVpnService
                                        │
                                        ▼
                                   PacketLoop ──► DnsServer ──► FilterEngine
                                        │              │
                                        │              └── UpstreamResolver
                                        └── write DNS response to TUN
```

### DomainFilter

- Exact host sets per `FilterCategory` (ads, trackers, media_ads, malware)
- Parent-domain walk: `a.b.doubleclick.net` matches rule `doubleclick.net`
- Precedence: user allow → user block → category lists → allow
- Thread-safe (`ConcurrentHashMap` sets)

### FilterEngine

- Wraps DomainFilter
- Atomic counters + recent blocked host ring buffer for UI
- Profiles adjust enabled categories / aggressive media pack

### Lists

- `BuiltinLists` ships offline sample hosts
- `ListRepository.parseHostsFile` understands `0.0.0.0 host` formats
- Remote catalog URLs ready for Phase 2 download + cache

### Persistence

- DataStore preferences: protection flag, boot, upstream DNS, profile, allow/block, app excludes

## Security & privacy properties

- **No remote VPN**: Silence is not an anonymity proxy
- **No cleartext MITM** in this design
- Own package always `addDisallowedApplication` to avoid routing loops
- Notification channel: `IMPORTANCE_LOW`, silent

## Failure modes

| Failure | Behavior |
|---------|----------|
| User denies VPN | Stay OFF |
| VPN revoked | Service stops, state OFF |
| Upstream DNS timeout | Query effectively fails (no response) |
| Private DNS (DoT) forced | May bypass Silence; document for user |
| Banking app VPN detect | Exclude package via Apps screen |

## Phase 2 — remote packs & apps

```
Lists UI ──► ListUpdateManager ──► ListRepository (HTTP + filesDir/lists)
                    │
                    ▼
              FilterEngine.replaceCategoryHosts()
                    │
WorkManager (24h) ──┘

Apps UI ──► InstalledAppsRepository (launcher intent query)
       ──► SettingsStore.excludedPackages
       ──► SilenceVpnService.addDisallowedApplication
```

- Default-enabled packs download on first launch (Wi‑Fi only if configured)
- No `QUERY_ALL_PACKAGES` — uses `<queries>` launcher intent visibility

## Phase 3 — reliability

```
BatteryHelper ──► Home/Settings tips + ignore-optimizations intent
AlwaysOnVpnGuide ──► system VPN settings deep link
VpnHealthMonitor ──► packet/DNS counters
SilenceVpnService
  ├── ACTION_RESTART (exclusions / settings)
  ├── START_STICKY + health watchdog
  ├── throttled notifications (5s)
  └── power-aware stats flush (20s / 60s)
BootCompletedReceiver ──► boot / quickboot / package replace
```

## Extension points

1. Replace `PacketLoop` with full stack without changing `DomainFilter` UI  
2. Add DoH upstream in `UpstreamResolver`  
3. Optional HTTPS MITM (high trust cost)  

## Testing

- Unit: domain match, hosts parse, DNS encode/sinkhole  
- Manual: enable VPN, resolve a listed ad host vs `example.com`
