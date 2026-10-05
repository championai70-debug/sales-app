# SalesPlan: notes for Claude Code

SalesPlan turns a customer budget and a split across segments into money and units per article.
It runs fully on the phone (no account, no server). See `README.md` for how the numbers are made.

| Path | Role |
|---|---|
| `web/` | The app: plain JavaScript, no build step |
| `android/` | Android WebView app that bundles `web/` (no internet permission) |
| `tests/` | `node --test tests/*.test.mjs` (Node 22, no install) |
| `store-kit/` | Google Play listing texts and checklist |
| `.github/workflows/salesplan.yml` | Tests, APK + AAB build, emulator check, `salesplan-preview` release |

Run locally: `python3 -m http.server 8765 -d web`, then open http://localhost:8765.
Run the tests after every change.
