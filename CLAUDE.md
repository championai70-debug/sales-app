# Sales App: notes for Claude Code

Budget allocation tool for retail buying: upload articles + sales, set budget and targets,
get euros per category and units/euros per article. No server, no AI service at runtime.

- `web/index.html` holds the whole app (UI + logic in plain JS, no build step). It is page
  content only: no doctype/html/head/body; the artifact host and `MainActivity` wrap it.
- Keep the planning logic above the `// ---------- state` marker: `tests/plan.test.mjs`
  evaluates that part. Run `node --test tests/plan.test.mjs` after every change and add a
  test for every bug fixed.
- `app/` is only a WebView shell; the APK is built by `.github/workflows/build-apk.yml`
  and published at the `latest-apk` release. The sandbox cannot build Android locally.
- Money must add up: article € = units × cost; categories and the total are sums; never
  over budget. Label forecasts as forecasts.
- User-facing text: short, plain English. Prices in euros (`€1,299`).
