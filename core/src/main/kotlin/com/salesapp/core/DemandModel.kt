package com.salesapp.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * What the history says about one customer and one product up to a given day.
 * The model learns from these numbers; the app also shows some of them as reasons.
 */
data class Signals(
    val qty30: Int,
    val qty90: Int,
    val qty180: Int,
    val monthsBought: Int,       // out of the last 6 months (30-day blocks)
    val daysSinceLast: Int?,     // null = never bought
    val peerShare: Double,       // share of same-type customers who bought it in the last 90 days
    val overallShare: Double,    // share of all customers who bought it in the last 90 days
    val marginPercent: Double,
    val categoryShare: Double,   // share of this customer's last-180-day units in the product's category
    val peerMonthlyQty: Double,  // typical monthly units for same-type customers who buy it
) {
    fun features(): DoubleArray = doubleArrayOf(
        ln(1.0 + qty30),
        ln(1.0 + qty90),
        monthsBought / 6.0,
        daysSinceLast?.let { exp(-it / 45.0) } ?: 0.0,
        peerShare,
        overallShare,
        marginPercent,
        categoryShare,
    )

    /** Units this customer usually takes in a month when they buy it. */
    fun monthlyQty(packSize: Int): Double = when {
        monthsBought > 0 -> qty180.toDouble() / monthsBought
        peerMonthlyQty > 0 -> peerMonthlyQty
        else -> packSize.toDouble()
    }

    companion object {
        val featureNames = listOf(
            "Units last 30 days", "Units last 90 days", "Months bought", "Bought recently",
            "Similar shops buy it", "All shops buy it", "Margin", "Fits their range",
        )
    }
}

/** Works out [Signals] for every customer and product as of one day. */
class SignalTable(data: DataSet, val asOf: LocalDate) {
    private val signals: Map<Pair<String, String>, Signals>

    init {
        val from180 = asOf.minusDays(180)
        val qty30 = HashMap<Pair<String, String>, Int>()
        val qty90 = HashMap<Pair<String, String>, Int>()
        val qty180 = HashMap<Pair<String, String>, Int>()
        val months = HashMap<Pair<String, String>, MutableSet<Long>>()
        val last = HashMap<Pair<String, String>, LocalDate>()
        val catQty = HashMap<Pair<String, String>, Int>()
        val custQty = HashMap<String, Int>()
        for (s in data.sales) {
            if (s.date.isAfter(asOf)) continue
            val k = s.customerId to s.productId
            val prev = last[k]
            if (prev == null || s.date.isAfter(prev)) last[k] = s.date
            if (!s.date.isAfter(from180)) continue
            val age = ChronoUnit.DAYS.between(s.date, asOf)
            if (age < 30) qty30.merge(k, s.quantity, Int::plus)
            if (age < 90) qty90.merge(k, s.quantity, Int::plus)
            qty180.merge(k, s.quantity, Int::plus)
            months.getOrPut(k) { HashSet() }.add(age / 30)
            val cat = data.productsById[s.productId]?.category ?: continue
            catQty.merge(s.customerId to cat, s.quantity, Int::plus)
            custQty.merge(s.customerId, s.quantity, Int::plus)
        }
        val typeOf = data.customers.associate { it.id to it.type }
        val typeCount = data.customers.groupingBy { it.type }.eachCount()
        val buyers90 = HashMap<String, Int>()
        val typeBuyers90 = HashMap<Pair<String, String>, Int>()
        val typeMonthly = HashMap<Pair<String, String>, MutableList<Double>>()
        for ((k, q) in qty90) {
            if (q <= 0) continue
            val type = typeOf[k.first] ?: continue
            buyers90.merge(k.second, 1, Int::plus)
            typeBuyers90.merge(type to k.second, 1, Int::plus)
        }
        for ((k, q) in qty180) {
            val type = typeOf[k.first] ?: continue
            val m = months[k]?.size ?: continue
            typeMonthly.getOrPut(type to k.second) { mutableListOf() }.add(q.toDouble() / m)
        }
        val nCust = data.customers.size.coerceAtLeast(1)
        val map = HashMap<Pair<String, String>, Signals>()
        for (c in data.customers) for (p in data.products) {
            val k = c.id to p.id
            val sameType = (typeCount[c.type] ?: 1)
            val mine = if ((qty90[k] ?: 0) > 0) 1 else 0
            val others = sameType - 1
            map[k] = Signals(
                qty30 = qty30[k] ?: 0,
                qty90 = qty90[k] ?: 0,
                qty180 = qty180[k] ?: 0,
                monthsBought = months[k]?.size ?: 0,
                daysSinceLast = last[k]?.let { ChronoUnit.DAYS.between(it, asOf).toInt() },
                peerShare = if (others > 0) ((typeBuyers90[c.type to p.id] ?: 0) - mine).toDouble() / others else 0.0,
                overallShare = (buyers90[p.id] ?: 0).toDouble() / nCust,
                marginPercent = p.marginPercent,
                categoryShare = custQty[c.id]?.let { tot -> (catQty[c.id to p.category] ?: 0).toDouble() / tot } ?: 0.0,
                peerMonthlyQty = typeMonthly[c.type to p.id]?.let { it.sorted()[it.size / 2] } ?: 0.0,
            )
        }
        signals = map
    }

    operator fun get(customerId: String, productId: String): Signals? = signals[customerId to productId]
}

/**
 * The machine-learning part: a logistic regression that learns, from the shop's own
 * history, how likely a customer is to buy a product in the next 30 days. It is trained
 * on the phone in a second or two, so no server or internet is needed.
 */
class DemandModel private constructor(
    private val weights: DoubleArray,
    private val bias: Double,
    private val mean: DoubleArray,
    private val std: DoubleArray,
    /** Area under the ROC curve on the most recent month the model did not see (0.5 = guessing, 1 = perfect). */
    val checkScore: Double?,
    val trainingExamples: Int,
) {
    fun probability(s: Signals): Double {
        val x = s.features()
        var z = bias
        for (i in x.indices) z += weights[i] * (x[i] - mean[i]) / std[i]
        return 1.0 / (1.0 + exp(-z))
    }

    /** How much each signal pushes the chance up (+) or down (-) for this customer and product. */
    fun contributions(s: Signals): DoubleArray {
        val x = s.features()
        return DoubleArray(x.size) { weights[it] * (x[it] - mean[it]) / std[it] }
    }

    companion object {
        private const val HORIZON = 30L

        /** Trains on up to [windows] past months. Returns null when there is too little history. */
        fun train(data: DataSet, windows: Int = 6): DemandModel? {
            val lastDay = data.sales.maxOfOrNull { it.date } ?: return null
            val firstDay = data.sales.minOf { it.date }
            val sets = (1..windows).mapNotNull { k ->
                val cutoff = lastDay.minusDays(HORIZON * k)
                if (ChronoUnit.DAYS.between(firstDay, cutoff) < 60) null else examples(data, cutoff)
            }
            if (sets.isEmpty()) return null
            val all = thin(sets.flatten())
            if (all.none { it.second } || all.all { it.second }) return null
            // Check: learn from older months, test on the newest month.
            val check = if (sets.size >= 2) {
                val m = fit(thin(sets.drop(1).flatten()), null, 0)
                auc(sets.first().map { m.probability(it.first) to it.second })
            } else null
            return fit(all, check, all.size)
        }

        /** Keeps training quick on a phone with big files: at most [max] evenly spread examples. */
        private fun <T> thin(all: List<T>, max: Int = 50_000): List<T> =
            if (all.size <= max) all else List(max) { all[(it.toLong() * all.size / max).toInt()] }

        private fun examples(data: DataSet, cutoff: LocalDate): List<Pair<Signals, Boolean>> {
            val table = SignalTable(data, cutoff)
            val end = cutoff.plusDays(HORIZON)
            val bought = data.sales.asSequence()
                .filter { it.date.isAfter(cutoff) && !it.date.isAfter(end) && it.quantity > 0 }
                .map { it.customerId to it.productId }.toHashSet()
            return data.customers.flatMap { c ->
                data.products.mapNotNull { p -> table[c.id, p.id]?.let { it to ((c.id to p.id) in bought) } }
            }
        }

        private fun fit(examples: List<Pair<Signals, Boolean>>, check: Double?, n: Int): DemandModel {
            val xs = examples.map { it.first.features() }
            val ys = examples.map { if (it.second) 1.0 else 0.0 }
            val d = xs.first().size
            val mean = DoubleArray(d) { j -> xs.sumOf { it[j] } / xs.size }
            val std = DoubleArray(d) { j ->
                sqrt(xs.sumOf { (it[j] - mean[j]) * (it[j] - mean[j]) } / xs.size).takeIf { it > 1e-9 } ?: 1.0
            }
            val z = xs.map { x -> DoubleArray(d) { (x[it] - mean[it]) / std[it] } }
            val w = DoubleArray(d)
            var b = ln((ys.sum() + 1) / (ys.size - ys.sum() + 1))
            val lr = 0.5
            val l2 = 1e-3
            repeat(300) {
                val gw = DoubleArray(d)
                var gb = 0.0
                for (i in z.indices) {
                    var s = b
                    for (j in 0 until d) s += w[j] * z[i][j]
                    val err = 1.0 / (1.0 + exp(-s)) - ys[i]
                    for (j in 0 until d) gw[j] += err * z[i][j]
                    gb += err
                }
                for (j in 0 until d) w[j] -= lr * (gw[j] / z.size + l2 * w[j])
                b -= lr * gb / z.size
            }
            return DemandModel(w, b, mean, std, check, n)
        }

        /** Chance that a random buyer scores above a random non-buyer. */
        fun auc(scored: List<Pair<Double, Boolean>>): Double? {
            val pos = scored.count { it.second }
            val neg = scored.size - pos
            if (pos == 0 || neg == 0) return null
            val sorted = scored.sortedBy { it.first }
            var rankSum = 0.0
            var i = 0
            while (i < sorted.size) {
                var j = i
                while (j + 1 < sorted.size && sorted[j + 1].first == sorted[i].first) j++
                val avgRank = (i + j) / 2.0 + 1
                for (k in i..j) if (sorted[k].second) rankSum += avgRank
                i = j + 1
            }
            return (rankSum - pos * (pos + 1) / 2.0) / (pos.toDouble() * neg)
        }
    }
}
