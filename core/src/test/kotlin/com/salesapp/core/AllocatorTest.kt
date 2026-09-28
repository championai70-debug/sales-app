package com.salesapp.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AllocatorTest {

    @Test
    fun everyoneGetsWhatTheyAskWhenThereIsEnough() {
        val a = Allocator.allocate(100, 6, listOf(Request("A", 10, 2, 1.0), Request("B", 6, 3, 0.5)))
        assertEquals(mapOf("A" to 12, "B" to 6), a)
    }

    @Test
    fun neverGivesMoreThanStockOrMoreThanAsked() {
        val requests = (1..9).map { Request("C$it", it * 6, 1 + it % 3, it.toDouble()) }
        for (stock in listOf(0, 5, 6, 30, 97, 150, 270, 1000)) {
            val a = Allocator.allocate(stock, 6, requests)
            assertTrue(a.values.sum() <= stock, "stock $stock: ${a.values.sum()}")
            assertTrue(a.values.all { it % 6 == 0 })
            requests.forEach { r -> assertTrue((a[r.customerId] ?: 0) <= r.quantity) }
            if (stock >= 6) assertTrue(stock - a.values.sum() < 6 || a.values.sum() == requests.sumOf { it.quantity })
        }
    }

    @Test
    fun importantCustomersGetABiggerShareWhenShort() {
        val a = Allocator.allocate(60, 1, listOf(Request("Key", 60, 1, 0.1), Request("Small", 60, 3, 0.9)))
        assertEquals(45, a["Key"])
        assertEquals(15, a["Small"])
    }

    @Test
    fun capsAreRespectedAndFreedStockGoesRound() {
        // A's fair share (15) is more than it asked for (10), so the 5 extra go to B.
        val a = Allocator.allocate(25, 1, listOf(Request("A", 10, 1, 0.0), Request("B", 20, 3, 0.0)))
        assertEquals(mapOf("A" to 10, "B" to 15), a)
    }
}
