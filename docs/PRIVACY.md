# Privacy Policy — Silence

**Last updated:** 2026-07-31  
**App:** Silence (Android DNS ad & tracker blocker)  
**Contact / source:** https://github.com/mvinodk2082-glitch/Silence

## Summary

Silence is designed for **privacy-first, on-device** filtering. It does **not** require an account and does **not** send your browsing traffic through our servers.

## What Silence does on your device

- Opens a **local VPN** used only as a DNS filter (not a commercial VPN exit node).
- Intercepts DNS queries on the device, blocks domains on your enabled lists, and forwards allowed queries to an upstream DNS you choose (e.g. Cloudflare, Quad9, Google, AdGuard DNS).
- Stores **preferences and stats** locally (DataStore).

## What we do **not** collect

- No analytics SDKs  
- No advertising identifiers for tracking  
- No account / email required  
- No sale of personal data  
- No remote VPN tunnel operated by Silence  

## Network activity

| Destination | Why |
|-------------|-----|
| Upstream DNS you configure | Resolve allowed domains |
| Blocklist URLs you enable (e.g. GitHub raw / AdGuard Hostlists) | Download filter packs when you update lists |
| Optional: GitHub Releases (if you check for updates) | See if a new APK version exists |

DNS queries for **blocked** domains are answered locally (sinkhole) and are **not** forwarded upstream.

## Permissions (high level)

- **VPN** — local filter only  
- **Internet** — upstream DNS + list downloads  
- **Notifications** — quiet “protection on” status  
- **Boot completed** — optional resume after reboot  

## Children

Silence is a general-purpose network tool. We do not knowingly collect data from children. Use is subject to your device’s policies and local law.

## Changes

We may update this policy in the repository. Material changes will bump the “Last updated” date.

## Contact

Open an issue: https://github.com/mvinodk2082-glitch/Silence/issues
