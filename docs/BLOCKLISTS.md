# Blocklists

## Embedded (shipped)

| Category | Source in code | Purpose |
|----------|----------------|---------|
| Ads | `BuiltinLists.ads` | Display / exchange hosts |
| Trackers | `BuiltinLists.trackers` | Analytics |
| Media ads | `BuiltinLists.mediaAds` | VAST / video / audio ad tech |
| Media aggressive | `BuiltinLists.mediaAdsAggressive` | Extra media hosts for Aggressive profile |
| Malware | `BuiltinLists.malware` | Placeholder only |

Embedded lists are intentionally small so the APK stays light and offline-first.

## Remote catalog (Phase 2)

Defined in `PackCatalog` (`lists/RemotePack.kt`):

| Pack ID | Default on | Category |
|---------|------------|----------|
| `stevenblack_unified` | Yes | Ads |
| `adguard_tracking` | Yes | Trackers |
| `adguard_mobile_ads` | Yes | Ads |
| `adguard_dns_filter` | No | Ads |
| `oisd_small` | No | Ads |
| `urlhaus` | No | Malware |

Parsers (`ListRepository`):

- Hosts (`0.0.0.0 domain`)
- Domain-per-line
- Simple adblock `||domain^`
- Auto-detect from sample lines

## Update policy (implemented)

1. **Update now** / per-pack refresh from Lists screen  
2. **WorkManager** every 24h when auto-update is on  
3. **Wi‑Fi only** respected for scheduled + default Update now  
4. **Force (any network)** button ignores Wi‑Fi constraint  
5. Cache under `filesDir/lists/{packId}.txt` + `.json` meta  
6. Failed download keeps previous cache; built-in samples always seed  
7. Host counts + last updated shown in Lists UI  
8. Packs and categories can be disabled independently  

## Matching rules

- Case-insensitive FQDN  
- Subdomains inherit parent rules (`ads.x.com` matches `x.com`)  
- User allowlist always wins  
- IP literals are not blocked by domain lists  

## Legal / ethics

Filter lists are third-party works — respect their licenses when shipping redistributed full lists. Linking to upstream URLs and downloading at runtime is often cleaner than vendoring multi‑MB lists in git.
