package com.salesapp.core

import kotlin.math.floor

/** One customer's wish for a product. */
data class Request(val customerId: String, val quantity: Int, val priority: Int, val score: Double)

/**
 * Shares limited stock between customers, in whole packs.
 *
 * - If there is enough, everyone gets what they asked for.
 * - If not, stock is split in proportion to what each asked for, with priority-1 customers
 *   weighted 3x and priority-2 customers 2x a priority-3 customer. Nobody gets more than
 *   they asked for; what that frees up goes round again.
 * - Packs left over after rounding go to the most important, best-ranked customers first.
 * - The total never goes above the stock.
 */
object Allocator {

    fun weight(priority: Int): Double = when (priority) { 1 -> 3.0; 2 -> 2.0; else -> 1.0 }

    fun allocate(stock: Int, packSize: Int, requests: List<Request>): Map<String, Int> {
        val pack = packSize.coerceAtLeast(1)
        val wanted = requests.filter { it.quantity > 0 }
            .associate { it.customerId to (it.quantity + pack - 1) / pack }
        val byId = requests.associateBy { it.customerId }
        val stockPacks = (stock.coerceAtLeast(0)) / pack
        val given = HashMap<String, Int>()

        if (wanted.values.sum() <= stockPacks) {
            wanted.forEach { (id, packs) -> given[id] = packs }
        } else {
            // Weighted fair share with caps ("water filling"), worked in fractions first.
            val share = HashMap<String, Double>()
            var active = wanted.keys.toMutableSet()
            var remaining = stockPacks.toDouble()
            while (active.isNotEmpty() && remaining > 1e-9) {
                val totalW = active.sumOf { wanted.getValue(it) * weight(byId.getValue(it).priority) }
                val full = active.filter { id ->
                    remaining * wanted.getValue(id) * weight(byId.getValue(id).priority) / totalW >= wanted.getValue(id)
                }
                if (full.isEmpty()) {
                    active.forEach { id ->
                        share[id] = remaining * wanted.getValue(id) * weight(byId.getValue(id).priority) / totalW
                    }
                    break
                }
                full.forEach { id -> share[id] = wanted.getValue(id).toDouble(); remaining -= wanted.getValue(id) }
                active = (active - full.toSet()).toMutableSet()
            }
            share.forEach { (id, s) -> given[id] = floor(s + 1e-9).toInt() }
            var left = stockPacks - given.values.sum()
            val order = wanted.keys.sortedWith(
                compareBy<String> { byId.getValue(it).priority }.thenByDescending { byId.getValue(it).score }
            )
            while (left > 0) {
                val next = order.firstOrNull { (given[it] ?: 0) < wanted.getValue(it) } ?: break
                given[next] = (given[next] ?: 0) + 1
                left--
                // Hand out one pack per customer per round so leftovers are spread out.
                val rest = order.filter { it != next && (given[it] ?: 0) < wanted.getValue(it) }
                for (id in rest) {
                    if (left == 0) break
                    given[id] = (given[id] ?: 0) + 1
                    left--
                }
            }
        }
        return given.filterValues { it > 0 }.mapValues { it.value * pack }
    }
}
