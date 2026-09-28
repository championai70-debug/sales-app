# Sales App

An Android app for salespeople. For every shop you visit it shows **which products to
offer first** and **how many**, and it **shares out limited stock** fairly between shops.
The ranking is a small machine-learning model that learns from your own sales history
**on the phone**. It needs no internet, no server and no AI subscription.

## Install on a phone

1. On the phone, open
   **https://github.com/championai70-debug/sales-app/releases/download/latest-apk/SalesApp.apk**
2. Open the downloaded file. If Android asks, allow installing apps from your browser.
3. Open **Sales App**. It starts with sample data so you can try it straight away.

New builds install over the old one and keep your data.

## Using it

| Tab | What it does |
|---|---|
| Customers | Your shops. Tap one to see its ranked products with suggested amounts and the reasons. Change amounts with − / + and save the order. |
| Stock | For each product: how much is left, how much shops want, how much the app planned. Red means demand is higher than stock. |
| Orders | Saved orders. **Send all** shares them as CSV (email, WhatsApp, Drive…). **Clear all** starts a new round and gives the stock back. |
| Settings | Load your own files, choose "sell more" vs "earn more per item", see how well the model tested. |

### Your files

CSV (comma or semicolon) or Excel `.xlsx`, first row = column names:

- **Products:** `product_id, name, category, price, cost, stock, pack_size`
- **Customers:** `customer_id, name, type, region, priority` (1 = key customer, 3 = small)
- **Sales history:** `date, customer_id, product_id, quantity` (about 3+ months works best)

Similar column names ("SKU", "Qty", "Outlet"…) are recognised too. German number and date
formats (`1,49`, `05.01.2026`) are fine.

## How it decides

1. **Chance to buy.** For every shop and product the app looks at: units in the last 30 and
   90 days, how many of the last 6 months they bought it, how recently, how many similar
   shops buy it, margin and how well it fits the shop's range. A logistic-regression model
   learns from past months which of these signals predict a purchase in the next 30 days.
   It is tested on the newest month it did not learn from; the score is shown in Settings.
2. **How many.** What the shop usually takes in a month, rounded up to whole packs.
3. **Order of the list.** Expected sales value and expected profit, mixed by the Settings
   slider.
4. **Sharing stock.** If shops want more than you have, each gets a share in proportion to
   what it wants; key customers count 3x, regular 2x, small 1x. Nobody gets more than
   they want, and the total never goes over stock.

## For developers

- `core/`: plain Kotlin, no Android: file import (CSV/xlsx), the model (`DemandModel`),
  stock sharing (`Allocator`), ranking (`Engine`), sample data. Tests: `./gradlew :core:test`.
- `app/`: Jetpack Compose UI. Data is kept as CSV files in the app's private folder.
- `.github/workflows/build-apk.yml`: every push runs the tests, builds the APK and puts it
  on the `latest-apk` release.
- The APK is signed with a test key in the repo (`app/test-signing.keystore`) so updates
  install over each other. Make a private key before publishing on Google Play.
