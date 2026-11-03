# ADR-0022: Kitchen Screen as an Electron kiosk on touch PC hardware

**Date:** 2026-11-03
**Status:** Accepted

The Kitchen Screen (repo `blitzpay-kitchen`) is an Electron app running full-screen in kiosk mode on a touchscreen PC, not a web app on tablets. Electron gives auto-start on boot, a window staff cannot close by accident, reliable audio chimes, and encrypted storage of the long-lived Keycloak offline token. The cost is that iPad/Android tablets are not supported.

## Recommended hardware

Big, glanceable, wall- or arm-mounted, reasonable price (approx. €450–700 per kitchen, prices indicative as of 2026):

| Option | Setup | Why |
|---|---|---|
| **Default** | 24" capacitive touch monitor (e.g. iiyama ProLite T2454MSC, ViewSonic TD2423) + fanless Intel N100 mini PC (e.g. Beelink Mini S12 / similar, 8–16 GB RAM) mounted on the monitor's VESA back | Cheapest big screen; parts replaceable separately; N100 is plenty for one Electron window |
| Kitchen-hardened | 21.5"–23.8" POS all-in-one touch PC with IP65 front bezel | Splash/grease resistant, one cable; costs more |

Minimum spec: 21.5"+ Full HD, 10-point capacitive touch (works with wet fingers better than resistive), VESA 75/100 mount, built-in or USB speaker loud enough for a kitchen, wired Ethernet preferred over Wi-Fi. OS: Windows 11 or Ubuntu LTS.

## Considered Options

- **PWA on tablets** — cheaper and works on iPad/Android, but no kiosk lock, background audio is unreliable, and tablets under 13" are too small to read across a kitchen.
