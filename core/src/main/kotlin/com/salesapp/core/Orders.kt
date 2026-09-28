package com.salesapp.core

/** Saves orders as CSV (one row per product line) and reads them back. */
object Orders {

    fun toCsv(orders: List<Order>, data: DataSet? = null): String = Tables.writeCsv(
        listOf(listOf("order_id", "created_at", "customer_id", "customer_name", "product_id", "product_name", "quantity", "price", "value")) +
            orders.flatMap { o ->
                o.lines.map { l ->
                    val p = data?.productsById?.get(l.productId)
                    listOf(
                        o.id, o.createdAt, o.customerId, data?.customersById?.get(o.customerId)?.name ?: "",
                        l.productId, p?.name ?: "", "${l.quantity}",
                        p?.let { money(it.price) } ?: "", p?.let { money(it.price * l.quantity) } ?: "",
                    )
                }
            }
    )

    fun fromCsv(text: String): List<Order> {
        val rows = Tables.readCsv(text)
        if (rows.size < 2) return emptyList()
        val h = rows.first()
        fun col(name: String) = h.indexOf(name)
        val id = col("order_id"); val at = col("created_at"); val cust = col("customer_id")
        val prod = col("product_id"); val qty = col("quantity")
        if (listOf(id, at, cust, prod, qty).any { it < 0 }) return emptyList()
        return rows.drop(1).filter { it.size > maxOf(id, at, cust, prod, qty) }
            .groupBy { it[id] }
            .map { (oid, lines) ->
                Order(oid, lines.first()[cust], lines.first()[at],
                    lines.map { OrderLine(it[prod], it[qty].toIntOrNull() ?: 0) })
            }
    }

    private fun money(x: Double) = "%.2f".format(java.util.Locale.ROOT, x)
}
