package com.salesapp.core

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Result of reading one file: what was understood, and plain-English notes about skipped rows. */
data class Imported<T>(val items: List<T>, val problems: List<String>)

/**
 * Turns spreadsheet rows into products, customers and sales. Column names are matched
 * loosely ("Product ID", "sku", "code" all mean the product id), so files exported from
 * most systems load without editing.
 */
object Importer {

    fun products(rows: List<List<String>>): Imported<Product> = parse(rows, "products") { r ->
        val price = number(r.get("price", "unit_price", "sell_price", "selling_price")) ?: return@parse "no price"
        Product(
            id = r.get("id", "product_id", "sku", "code", "item_id", "item") ?: return@parse "no product id",
            name = r.get("name", "product_name", "product", "description", "item_name") ?: r.get("id", "sku", "code")!!,
            category = r.get("category", "group", "product_group", "family") ?: "Other",
            price = price,
            cost = number(r.get("cost", "unit_cost", "buy_price", "purchase_price")) ?: price * 0.75,
            stock = number(r.get("stock", "available", "on_hand", "inventory", "quantity", "qty"))?.toInt() ?: 0,
            packSize = (number(r.get("pack_size", "pack", "case_size", "units_per_case"))?.toInt() ?: 1).coerceAtLeast(1),
        )
    }

    fun customers(rows: List<List<String>>): Imported<Customer> = parse(rows, "customers") { r ->
        Customer(
            id = r.get("id", "customer_id", "customer", "code", "account") ?: return@parse "no customer id",
            name = r.get("name", "customer_name", "shop", "store", "outlet") ?: r.get("id", "customer_id")!!,
            type = r.get("type", "segment", "channel", "customer_type", "class") ?: "Shop",
            region = r.get("region", "area", "territory", "city", "route") ?: "",
            priority = (number(r.get("priority", "tier", "rank"))?.toInt() ?: 2).coerceIn(1, 3),
        )
    }

    fun sales(rows: List<List<String>>): Imported<Sale> = parse(rows, "sales") { r ->
        Sale(
            date = date(r.get("date", "sale_date", "order_date", "invoice_date", "day") ?: return@parse "no date")
                ?: return@parse "date not understood",
            customerId = r.get("customer_id", "customer", "account", "outlet_id") ?: return@parse "no customer id",
            productId = r.get("product_id", "product", "sku", "item_id", "item", "code") ?: return@parse "no product id",
            quantity = number(r.get("quantity", "qty", "units", "amount"))?.toInt() ?: return@parse "no quantity",
        )
    }

    private class Row(private val cells: Map<String, String>) {
        fun get(vararg names: String): String? = names.firstNotNullOfOrNull { cells[it]?.takeIf(String::isNotBlank) }
    }

    private fun <T> parse(rows: List<List<String>>, what: String, make: (Row) -> Any): Imported<T> {
        if (rows.isEmpty()) return Imported(emptyList(), listOf("The $what file is empty."))
        val header = rows.first().map(::key)
        val items = mutableListOf<T>()
        val problems = mutableListOf<String>()
        rows.drop(1).forEachIndexed { i, cells ->
            val made = try {
                make(Row(header.zip(cells).toMap()))
            } catch (e: RuntimeException) {
                "could not read it"
            }
            @Suppress("UNCHECKED_CAST")
            if (made is String) problems.add("Row ${i + 2}: $made.") else items.add(made as T)
        }
        return Imported(items, problems)
    }

    /** "Product ID" -> "product_id" */
    private fun key(s: String) = s.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')

    /** Reads "1.19", "1,19", "€ 1.234,50", "1,234.50". */
    fun number(s: String?): Double? {
        if (s == null) return null
        var t = s.replace(Regex("[^0-9,.\\-]"), "")
        if (t.isEmpty()) return null
        val lastComma = t.lastIndexOf(','); val lastDot = t.lastIndexOf('.')
        t = when {
            lastComma >= 0 && lastDot >= 0 ->
                if (lastComma > lastDot) t.replace(".", "").replace(',', '.') else t.replace(",", "")
            lastComma >= 0 -> t.replace(',', '.')
            else -> t
        }
        return t.toDoubleOrNull()
    }

    private val dateFormats = listOf("yyyy-MM-dd", "dd.MM.yyyy", "dd/MM/yyyy", "d.M.yyyy", "d/M/yyyy", "yyyy/MM/dd")
        .map { DateTimeFormatter.ofPattern(it) }
    private val excelEpoch: LocalDate = LocalDate.of(1899, 12, 30)

    /** ISO, German and day-first dates, or an Excel date number. */
    fun date(s: String): LocalDate? {
        val t = s.trim().take(10)
        for (f in dateFormats) try { return LocalDate.parse(t, f) } catch (e: RuntimeException) {}
        val serial = s.trim().toDoubleOrNull() ?: return null
        return if (serial in 20000.0..80000.0) excelEpoch.plusDays(serial.toLong()) else null
    }
}
