package com.salesapp.core

import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.roundToInt

/** One product on a customer's visit screen, best first. */
data class Suggestion(
    val product: Product,
    /** Chance (0..1) the customer buys it in the next 30 days. */
    val probability: Double,
    /** Units the customer should take if there were enough stock. */
    val wantedQty: Int,
    /** Units after sharing limited stock with the other customers. */
    val suggestedQty: Int,
    val score: Double,
    val reasons: List<String>,
)

/** One product on the stock screen. */
data class StockLine(
    val product: Product,
    val ordered: Int,
    val available: Int,
    val wanted: Int,
    val planned: Int,
) {
    val isShort: Boolean get() = wanted > available
}

/**
 * Ranks products for each customer and shares out stock. [marginWeight] 0 ranks by
 * sales value only, 1 by profit only; 0.5 is an even mix.
 */
class Engine(val data: DataSet, val orders: List<Order>, val marginWeight: Double = 0.5) {

    val model: DemandModel? = DemandModel.train(data)
    val asOf: LocalDate = data.sales.maxOfOrNull { it.date } ?: LocalDate.now()
    private val signals = SignalTable(data, asOf)

    private val ordered: Map<String, Int> = orders.flatMap { it.lines }
        .groupBy({ it.productId }, { it.quantity }).mapValues { it.value.sum() }
    private val customersWithOrder: Set<String> = orders.map { it.customerId }.toSet()

    private class Raw(val product: Product, val probability: Double, val wanted: Int, val score: Double, val signals: Signals)

    private val raw: Map<String, List<Raw>> = data.customers.associate { c -> c.id to rawFor(c) }

    /** productId -> customerId -> units planned for that customer. */
    val plan: Map<String, Map<String, Int>> = data.products.associate { p ->
        val requests = data.customers.filter { it.id !in customersWithOrder }.mapNotNull { c ->
            raw[c.id]?.firstOrNull { it.product.id == p.id }?.takeIf { it.wanted > 0 }
                ?.let { Request(c.id, it.wanted, c.priority, it.score) }
        }
        p.id to Allocator.allocate(available(p), p.packSize, requests)
    }

    fun available(p: Product): Int = (p.stock - (ordered[p.id] ?: 0)).coerceAtLeast(0)

    fun suggestionsFor(customerId: String): List<Suggestion> {
        val c = data.customersById[customerId] ?: return emptyList()
        return (raw[customerId] ?: emptyList()).map { r ->
            val planned = plan[r.product.id]?.get(customerId) ?: 0
            val reasons = reasons(c, r).toMutableList()
            if (r.wanted > 0 && planned < r.wanted) reasons.add(0, "Stock is short, shared with other shops")
            Suggestion(r.product, r.probability, r.wanted, if (c.id in customersWithOrder) r.wanted else planned, r.score, reasons.take(3))
        }
    }

    fun stock(): List<StockLine> = data.products.map { p ->
        StockLine(
            product = p,
            ordered = ordered[p.id] ?: 0,
            available = available(p),
            wanted = data.customers.filter { it.id !in customersWithOrder }
                .sumOf { c -> raw[c.id]?.firstOrNull { it.product.id == p.id }?.wanted ?: 0 },
            planned = plan[p.id]?.values?.sum() ?: 0,
        )
    }.sortedWith(compareByDescending<StockLine> { it.isShort }.thenBy { it.product.name })

    private fun rawFor(c: Customer): List<Raw> {
        val rows = data.products.map { p ->
            val s = signals[c.id, p.id]!!
            val prob = model?.probability(s)
                ?: (0.6 * (s.monthsBought / 3.0).coerceAtMost(1.0) + 0.4 * s.peerShare)
            val monthly = s.monthlyQty(p.packSize)
            val wanted = when {
                prob >= 0.35 -> packs(monthly, p.packSize)
                prob >= 0.15 -> p.packSize
                else -> 0
            }
            Triple(p, prob, Pair(wanted, s)) to Pair(prob * monthly * p.price, prob * monthly * p.margin)
        }
        val maxRev = rows.maxOfOrNull { it.second.first }?.takeIf { it > 0 } ?: 1.0
        val maxProfit = rows.maxOfOrNull { it.second.second }?.takeIf { it > 0 } ?: 1.0
        return rows.map { (t, money) ->
            val score = (1 - marginWeight) * money.first / maxRev + marginWeight * money.second / maxProfit
            Raw(t.first, t.second, t.third.first, score, t.third.second)
        }.sortedByDescending { it.score }
    }

    private fun packs(units: Double, pack: Int): Int = (ceil(units / pack - 1e-9).toInt().coerceAtLeast(1)) * pack

    private fun reasons(c: Customer, r: Raw): List<String> {
        val s = r.signals
        val out = mutableListOf<String>()
        out += when {
            s.qty30 > 0 -> "Bought ${s.qty30} in the last 30 days"
            s.monthsBought >= 3 -> "Buys it most months (${s.monthsBought} of 6)"
            s.daysSinceLast != null -> "Last bought ${s.daysSinceLast} days ago"
            else -> "New for this shop"
        }
        if (s.peerShare >= 0.4) out += "${pct(s.peerShare)} of similar shops (${c.type}) buy it"
        if (r.product.marginPercent >= 0.3) out += "Good margin (${pct(r.product.marginPercent)})"
        return out
    }

    private fun pct(x: Double) = "${(x * 100).roundToInt()}%"
}
