package com.salesapp.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EngineTest {
    private val data = SampleData.generate(LocalDate.of(2026, 9, 28))

    @Test
    fun modelLearnsSomethingRealFromHistory() {
        val m = assertNotNull(DemandModel.train(data))
        val score = assertNotNull(m.checkScore)
        assertTrue(score > 0.8, "check score $score")
    }

    @Test
    fun aucIsCorrect() {
        assertEquals(1.0, DemandModel.auc(listOf(0.1 to false, 0.9 to true)))
        assertEquals(0.5, DemandModel.auc(listOf(0.5 to false, 0.5 to true)))
        assertEquals(0.75, DemandModel.auc(listOf(0.1 to false, 0.4 to true, 0.5 to false, 0.8 to true)))
    }

    @Test
    fun ranksWhatAShopActuallyBuysFirst() {
        val e = Engine(data, emptyList())
        val pharmacy = data.customers.first { it.type == "Pharmacy" }
        val top = e.suggestionsFor(pharmacy.id).take(5)
        assertTrue(top.count { it.product.category == "Personal care" } >= 3, top.map { it.product.name }.toString())
        val kiosk = data.customers.first { it.type == "Kiosk" }
        val kioskTop = e.suggestionsFor(kiosk.id).take(5)
        assertTrue(kioskTop.all { it.product.category in setOf("Drinks", "Snacks") }, kioskTop.map { it.product.name }.toString())
        assertTrue(kioskTop.all { it.reasons.isNotEmpty() })
    }

    @Test
    fun planNeverExceedsStock() {
        val e = Engine(data, emptyList())
        val lines = e.stock()
        assertTrue(lines.any { it.isShort }, "sample data should have some short products")
        lines.forEach { assertTrue(it.planned <= it.available, it.toString()) }
        data.customers.forEach { c ->
            e.suggestionsFor(c.id).forEach { s -> assertTrue(s.suggestedQty <= s.wantedQty) }
        }
    }

    @Test
    fun savedOrdersUseUpStock() {
        val p = data.products.first()
        val c = data.customers.first()
        val e = Engine(data, listOf(Order("O1", c.id, "now", listOf(OrderLine(p.id, p.stock + 5)))))
        val line = e.stock().first { it.product.id == p.id }
        assertEquals(0, line.available)
        assertEquals(0, line.planned)
    }

    @Test
    fun marginWeightChangesTheOrder() {
        val c = data.customers.first { it.type == "Supermarket" }
        val bySales = Engine(data, emptyList(), 0.0).suggestionsFor(c.id).map { it.product.id }
        val byProfit = Engine(data, emptyList(), 1.0).suggestionsFor(c.id).map { it.product.id }
        assertTrue(bySales != byProfit)
    }

    @Test
    fun worksWithNoHistoryAtAll() {
        val e = Engine(DataSet(data.products, data.customers, emptyList()), emptyList())
        assertEquals(null, e.model)
        assertEquals(data.products.size, e.suggestionsFor(data.customers.first().id).size)
    }
}
