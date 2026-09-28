// Tests the planning logic inside web/index.html. Run: node --test tests/
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const html = readFileSync(new URL("../web/index.html", import.meta.url), "utf8");
const logic = html.split("<script>")[1].split("// ---------- state")[0];
const api = new Function(logic + "\nreturn { sampleData, plan, readCsv, parseRows, importers, num, dayOf, boost };")();
const prm = { budget: 120000, weeks: 8, st: 0.8, min: 3 };
const data = api.sampleData();
const R = api.plan(data, prm);

test("never spends more than the budget, per category or in total", () => {
  assert.ok(R.spent <= prm.budget + 0.01);
  for (const c of R.cats) assert.ok(c.spent <= c.budget + 0.01, c.name);
  const small = api.plan(data, { ...prm, budget: 5000 });
  assert.ok(small.spent <= 5000 + 0.01);
});

test("euros per article are units times cost, and categories add up", () => {
  for (const r of R.rows) assert.equal(Math.round(r.buyEur * 100), Math.round(r.buy * r.a.cost * 100));
  for (const c of R.cats) assert.ok(Math.abs(c.spent - c.rows.reduce((s, r) => s + r.buyEur, 0)) < 0.01);
  assert.ok(Math.abs(R.spent - R.rows.reduce((s, r) => s + r.buyEur, 0)) < 0.01);
});

test("respects the smallest order", () => {
  for (const r of R.rows) assert.ok(r.buy === 0 || r.buy >= prm.min, r.a.name);
});

test("does not buy articles that already have enough stock", () => {
  for (const r of R.rows) if (r.need === 0) assert.equal(r.buy, 0, r.a.name);
});

test("new articles get a forecast from similar articles in their category", () => {
  const fresh = R.rows.filter(r => !r.hasHist);
  assert.ok(fresh.length > 0);
  for (const r of fresh) {
    assert.ok(r.rate > 0 && r.similar.length > 0, r.a.name);
    const sameCat = data.articles.filter(a => r.similar.includes(a.name) && a.category === r.a.category);
    assert.ok(sameCat.length >= 1, r.a.name);
  }
});

test("the boosted model is tested and does not do worse than a plain average", () => {
  assert.ok(R.acc, "accuracy should be measured on sample data");
  assert.ok(R.acc.model <= R.acc.naive * 1.05, JSON.stringify(R.acc));
});

test("sell-through is between 0 and 1", () => {
  for (const r of R.rows) if (r.st != null) assert.ok(r.st >= 0 && r.st <= 1);
  assert.ok(R.expST > 0 && R.expST <= 1);
});

test("reads German CSV files", () => {
  const rows = api.readCsv("﻿Artikel;x\r\n" + "article_id;name;category;price;cost;stock\r\nA1;\"Shoe; black\";Shoes;1.299,00;650,5;12\r\n");
  const r = api.parseRows(rows.slice(1), api.importers.articles);
  assert.deepEqual(r.problems, []);
  assert.equal(r.items[0].name, "Shoe; black");
  assert.equal(r.items[0].price, 1299);
  assert.equal(r.items[0].cost, 650.5);
  const s = api.parseRows(api.readCsv("date,article_id,units\n05.01.2026,A1,3\nsoon,A1,2\n"), api.importers.sales);
  assert.equal(s.items.length, 1);
  assert.deepEqual(s.problems, ["row 3: date not understood"]);
});

test("works with sales history but no articles file yet", () => {
  const onlySales = { articles: [{ id: "A1", name: "A1", category: "Other", price: 10, cost: 5, stock: 0, brand: "", color: "" }], sales: data.sales.filter(s => s.a === "A001").map(s => ({ ...s, a: "A1" })) };
  const r = api.plan(onlySales, prm);
  assert.ok(r.rows[0].rate > 0);
});
