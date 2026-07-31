# Reliability (Phase 3)

How Silence stays alive on real Android devices.

## Threats

| Risk | Mitigation |
|------|------------|
| OEM battery killers | Prompt for **unrestricted battery** / ignore optimizations |
| System power save | Detect + tip; power-saver mode lengthens flush interval |
| Only one VPN allowed | Always-on VPN guidance; exclusion list for conflict apps |
| Process death | `START_STICKY`, persisted `protectionEnabled`, boot / package-replace receivers |
| Packet loop crash | Health watchdog restarts TUN if loop exits while enabled |
| Exclusion changes ignored | `needsRestart` flag + one-tap **Restart protection** |
| Noisy notifications | Throttled updates (5s), silent LOW channel |

## Always-on VPN

Android 7+ system feature. Steps:

1. Settings → Network → VPN (or in-app **Open VPN settings**)
2. Gear next to Silence
3. Enable **Always-on VPN**
4. Optional: **Block connections without VPN** (strict; can break captive portals)

Silence cannot flip this programmatically for third-party apps without privilege.

## Boot

- `BootCompletedReceiver` + OEM quick-boot actions
- `startOnBoot` preference
- `MY_PACKAGE_REPLACED` resumes if protection was enabled after update

## Battery modes

| Mode | Behavior |
|------|----------|
| Default | Stats flush ~20s |
| Power-saver (in-app) | Stats flush ~60s |
| System power save | Treated like saver for flush interval |

DNS packet path itself is event-driven on TUN reads (no busy poll).

## Health metrics

`VpnHealthMonitor` tracks:

- packets seen
- DNS queries handled
- restart count

Shown on Home (summary) and Settings (detail).
