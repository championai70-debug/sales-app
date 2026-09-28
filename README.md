# Sales App

Upload your sales, set a budget, and see what to buy: **euros per category** and
**units and euros per article**. Everything runs on the device; nothing is uploaded.

- **Try it in a browser:** open `web/index.html` (also published as a private Claude artifact).
- **Android:** https://github.com/championai70-debug/sales-app/releases/download/latest-apk/SalesApp.apk
  (open it on the phone, allow installing from your browser if asked). New builds install
  over the old one and keep your data.

## How to use it

1. **Your data.** One CSV sales file is enough (comma or semicolon; in Excel use *Save as › CSV*):
   - Needed: `date, article_id, units` (daily or weekly rows; a year is best)
   - Better with: `name, category, price, cost, stock`
   - Optional articles file: `article_id, name, category, price, cost, stock, brand, color`,
     for current stock and new articles that have no sales yet.

   Until you load yours, the app shows a sample sports shop.
2. **Your plan.** Budget (at cost), time frame (2 weeks, a month, 2 months, a season or any
   number of weeks), target sell-through,
   smallest order per article. The results update as you change them.
3. **What to buy.** Totals, euros per category, and a table of units and euros per
   article. Tap a category to filter. *Copy plan as CSV* to paste into Excel.

## How it decides

1. **Top sellers.** Gradient-boosted trees (the idea behind XGBoost), written in plain
   JavaScript, predict each article's weekly sales from recent sales, trend, price,
   margin, age and category momentum. It starts from the 4-week average and learns
   corrections, trained for the chosen time frame. It is tested by hiding the last weeks
   of sales and forecasting them; accuracy is shown for the whole plan, per category and
   per article. Top 20% of forecast revenue = *Top seller*, bottom half = *Slow*.
2. **New articles** (no sales yet) take the forecast of their 3 most similar articles
   (category, price, brand, colour), minus 20%.
3. **Sell-through** = units sold in the last 8 weeks ÷ (sold + stock now).
4. **Need** per article = forecast for the period ÷ target sell-through − stock.
5. **Euros per category** = budget split by each category's need at cost, weighted by
   the square root of its sell-through index (capped at 125% of need).
6. **Units per article** = the category's euros split by need (top sellers ×1.25, slow
   ×0.8), capped at 150% of need, turned into units at cost. Orders below the smallest
   order are dropped.

## For developers

- `web/index.html` is the whole app (page content only; the web host or the Android
  shell adds the `<html>` around it).
- `tests/plan.test.mjs` tests the planning logic: `node --test tests/plan.test.mjs`.
- `app/` is a small Android shell that shows `web/index.html` from its assets in a WebView.
- `.github/workflows/build-apk.yml` runs the tests, builds the APK and publishes it on the
  `latest-apk` release on every push.
- The APK is signed with a test key in the repo (`app/test-signing.keystore`) so updates
  install over each other. Make a private key before publishing on Google Play.
