package com.salesapp.core

import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Made-up but realistic FMCG data so the app is useful the first time it opens.
 * Replace it with your own files in Settings.
 */
object SampleData {

    private val catalogue = listOf(
        // name, category, price €, cost €, pack size
        listOf("Cola 1.5 L", "Drinks", 1.49, 0.95, 6),
        listOf("Still Water 6 x 1.5 L", "Drinks", 2.99, 2.10, 1),
        listOf("Orange Juice 1 L", "Drinks", 1.79, 1.20, 6),
        listOf("Energy Drink 250 ml", "Drinks", 1.29, 0.70, 12),
        listOf("Beer 6 x 0.5 L", "Drinks", 5.49, 4.10, 4),
        listOf("Iced Tea 1.5 L", "Drinks", 1.19, 0.72, 6),
        listOf("Potato Chips 175 g", "Snacks", 1.99, 1.15, 10),
        listOf("Chocolate Bar 100 g", "Snacks", 1.29, 0.70, 20),
        listOf("Butter Biscuits 200 g", "Snacks", 1.59, 0.98, 12),
        listOf("Salted Peanuts 200 g", "Snacks", 1.89, 1.10, 10),
        listOf("Gummy Bears 200 g", "Snacks", 1.19, 0.62, 18),
        listOf("Pasta 500 g", "Food", 0.99, 0.62, 12),
        listOf("Rice 1 kg", "Food", 2.29, 1.60, 10),
        listOf("Tomato Sauce 400 g", "Food", 1.39, 0.85, 12),
        listOf("Sunflower Oil 1 L", "Food", 2.49, 1.85, 6),
        listOf("Instant Noodles 5-pack", "Food", 2.19, 1.30, 8),
        listOf("Ground Coffee 500 g", "Hot drinks", 6.99, 5.10, 6),
        listOf("Black Tea 25 bags", "Hot drinks", 1.99, 1.05, 12),
        listOf("UHT Milk 1 L", "Dairy", 1.09, 0.82, 12),
        listOf("Yogurt 4-pack", "Dairy", 1.69, 1.10, 6),
        listOf("Toothpaste 75 ml", "Personal care", 1.95, 0.98, 12),
        listOf("Shampoo 300 ml", "Personal care", 3.49, 1.90, 6),
        listOf("Shower Gel 250 ml", "Personal care", 1.79, 0.92, 6),
        listOf("Deodorant 150 ml", "Personal care", 2.95, 1.45, 6),
        listOf("Laundry Detergent 20 loads", "Household", 5.99, 4.20, 4),
        listOf("Dish Soap 500 ml", "Household", 1.49, 0.80, 12),
        listOf("Toilet Paper 8 rolls", "Household", 3.99, 3.05, 4),
        listOf("Kitchen Towels 4 rolls", "Household", 2.99, 2.15, 6),
    )

    // How much each kind of shop likes each category (0..1), and how big its orders are.
    private val tastes = mapOf(
        "Supermarket" to mapOf("Drinks" to .8, "Snacks" to .7, "Food" to .9, "Hot drinks" to .7, "Dairy" to .9, "Personal care" to .7, "Household" to .8),
        "Kiosk" to mapOf("Drinks" to .9, "Snacks" to .9, "Food" to .2, "Hot drinks" to .2, "Dairy" to .3, "Personal care" to .1, "Household" to .1),
        "Petrol station" to mapOf("Drinks" to .9, "Snacks" to .8, "Food" to .1, "Hot drinks" to .4, "Dairy" to .2, "Personal care" to .2, "Household" to .2),
        "Pharmacy" to mapOf("Drinks" to .3, "Snacks" to .2, "Food" to .05, "Hot drinks" to .2, "Dairy" to .05, "Personal care" to .95, "Household" to .4),
    )
    private val size = mapOf("Supermarket" to 3.0, "Kiosk" to 1.0, "Petrol station" to 1.3, "Pharmacy" to 1.0)

    private val shops = listOf(
        listOf("Central Supermarket", "Supermarket", "North", 1),
        listOf("Fresh Market Riverside", "Supermarket", "South", 1),
        listOf("Budget Foods Hill Street", "Supermarket", "East", 2),
        listOf("Family Grocer", "Supermarket", "West", 2),
        listOf("Neighbourhood Market", "Supermarket", "North", 3),
        listOf("Station Kiosk", "Kiosk", "North", 2),
        listOf("Park Corner Kiosk", "Kiosk", "South", 3),
        listOf("Late Night Shop", "Kiosk", "East", 2),
        listOf("Campus Kiosk", "Kiosk", "West", 3),
        listOf("Market Square Kiosk", "Kiosk", "South", 3),
        listOf("Highway Fuel Stop", "Petrol station", "North", 1),
        listOf("City Fuel", "Petrol station", "East", 2),
        listOf("Ring Road Petrol", "Petrol station", "West", 3),
        listOf("Sun Pharmacy", "Pharmacy", "North", 2),
        listOf("Health Corner Pharmacy", "Pharmacy", "South", 2),
        listOf("Old Town Pharmacy", "Pharmacy", "East", 3),
    )

    fun generate(today: LocalDate = LocalDate.now(), seed: Int = 7): DataSet {
        val rnd = Random(seed)
        val products = catalogue.mapIndexed { i, r ->
            Product("P%03d".format(i + 1), r[0] as String, r[1] as String, r[2] as Double, r[3] as Double, 0, r[4] as Int)
        }
        val customers = shops.mapIndexed { i, r ->
            Customer("C%03d".format(i + 1), r[0] as String, r[1] as String, r[2] as String, r[3] as Int)
        }
        // Each shop has its own liking for each product; some products are growing, some shrinking.
        val like = customers.associate { c ->
            c.id to products.associate { p ->
                val base = tastes.getValue(c.type).getValue(p.category)
                p.id to (base * (0.35 + rnd.nextDouble() * 0.9)).coerceIn(0.0, 0.95).let { if (it < 0.15) 0.0 else it }
            }
        }
        val trend = products.associate { it.id to 0.6 + rnd.nextDouble() * 0.8 }
        val sales = mutableListOf<Sale>()
        val start = today.minusDays(364)
        val monthly = HashMap<String, Double>()
        for (week in 0 until 52) {
            val t = week / 51.0
            for (c in customers) {
                val visitDay = start.plusDays(week * 7L + (c.id.hashCode() and 3))
                if (visitDay.isAfter(today)) continue
                for (p in products) {
                    val tr = trend.getValue(p.id)
                    val chance = like.getValue(c.id).getValue(p.id) * (1 - t + t * tr) * 0.55
                    if (rnd.nextDouble() >= chance) continue
                    val qty = max(1, (p.packSize * size.getValue(c.type) * (0.5 + rnd.nextDouble())).roundToInt())
                    sales += Sale(visitDay, c.id, p.id, qty)
                    if (week >= 48) monthly.merge(p.id, qty.toDouble(), Double::plus)
                }
            }
        }
        // Stock for the coming month: some products plentiful, some short.
        val withStock = products.map { p ->
            val need = monthly[p.id] ?: 0.0
            val factor = listOf(0.6, 0.9, 1.3, 1.8, 2.4)[rnd.nextInt(5)]
            p.copy(stock = ((need * factor) / p.packSize).roundToInt() * p.packSize)
        }
        return DataSet(withStock, customers, sales.sortedBy { it.date })
    }

    /** The same data as CSV files, to show people what a file should look like. */
    fun productsCsv(d: DataSet) = Tables.writeCsv(
        listOf(listOf("product_id", "name", "category", "price", "cost", "stock", "pack_size")) +
            d.products.map { listOf(it.id, it.name, it.category, "%.2f".format(java.util.Locale.ROOT, it.price), "%.2f".format(java.util.Locale.ROOT, it.cost), "${it.stock}", "${it.packSize}") }
    )

    fun customersCsv(d: DataSet) = Tables.writeCsv(
        listOf(listOf("customer_id", "name", "type", "region", "priority")) +
            d.customers.map { listOf(it.id, it.name, it.type, it.region, "${it.priority}") }
    )

    fun salesCsv(d: DataSet) = Tables.writeCsv(
        listOf(listOf("date", "customer_id", "product_id", "quantity")) +
            d.sales.map { listOf(it.date.toString(), it.customerId, it.productId, "${it.quantity}") }
    )
}
