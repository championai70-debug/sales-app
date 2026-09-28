package com.salesapp.core

import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImporterTest {

    @Test
    fun readsGermanExcelCsvWithSemicolonsAndDecimalCommas() {
        val csv = "﻿Product ID;Name;Category;Price;Cost;Stock;Pack size\r\nP1;\"Cola; 1,5 L\";Drinks;1,49;0,95;120;6\r\nP2;Chips;Snacks;€ 1.234,50;;10;\r\n"
        val r = Importer.products(Tables.read(csv.toByteArray()))
        assertEquals(listOf<String>(), r.problems)
        assertEquals("Cola; 1,5 L", r.items[0].name)
        assertEquals(1.49, r.items[0].price, 1e-9)
        assertEquals(6, r.items[0].packSize)
        assertEquals(1234.5, r.items[1].price, 1e-9)
        assertEquals(1, r.items[1].packSize)
    }

    @Test
    fun skipsBadRowsAndSaysWhy() {
        val csv = "date,customer_id,product_id,quantity\n2026-01-05,C1,P1,4\nyesterday,C1,P1,4\n05.01.2026,C2,P1,\n"
        val r = Importer.sales(Tables.readCsv(csv))
        assertEquals(1, r.items.size)
        assertEquals(listOf("Row 3: date not understood.", "Row 4: no quantity."), r.problems)
    }

    @Test
    fun readsDates() {
        assertEquals(LocalDate.of(2026, 1, 5), Importer.date("05.01.2026"))
        assertEquals(LocalDate.of(2026, 1, 5), Importer.date("2026-01-05 00:00"))
        assertEquals(LocalDate.of(2026, 1, 5), Importer.date("46027"))
    }

    @Test
    fun readsExcelFiles() {
        val shared = """<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><si><t>customer_id</t></si><si><t>name</t></si><si><t>priority</t></si><si><t>Kiosk One</t></si></sst>"""
        val sheet = """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
            <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c><c r="C1" t="s"><v>2</v></c></row>
            <row r="2"><c r="A2" t="inlineStr"><is><t>C9</t></is></c><c r="B2" t="s"><v>3</v></c><c r="C2"><v>1</v></c></row>
            <row r="3"><c r="A3" t="inlineStr"><is><t>C10</t></is></c><c r="C3"><v>3</v></c></row>
            </sheetData></worksheet>"""
        val bytes = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { z ->
                z.putNextEntry(ZipEntry("xl/sharedStrings.xml")); z.write(shared.toByteArray()); z.closeEntry()
                z.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml")); z.write(sheet.toByteArray()); z.closeEntry()
            }
        }.toByteArray()
        val r = Importer.customers(Tables.read(bytes))
        assertEquals(2, r.items.size)
        assertEquals(Customer("C9", "Kiosk One", "Shop", "", 1), r.items[0])
        assertEquals("C10", r.items[1].name)
        assertEquals(3, r.items[1].priority)
    }

    @Test
    fun sampleDataSurvivesARoundTripThroughCsv() {
        val d = SampleData.generate(LocalDate.of(2026, 9, 28))
        val products = Importer.products(Tables.readCsv(SampleData.productsCsv(d)))
        val customers = Importer.customers(Tables.readCsv(SampleData.customersCsv(d)))
        val sales = Importer.sales(Tables.readCsv(SampleData.salesCsv(d)))
        assertTrue(products.problems.isEmpty() && customers.problems.isEmpty() && sales.problems.isEmpty())
        assertEquals(d.products, products.items)
        assertEquals(d.customers, customers.items)
        assertEquals(d.sales, sales.items)
    }

    @Test
    fun ordersRoundTrip() {
        val orders = listOf(
            Order("O1", "C1", "2026-09-28T10:00", listOf(OrderLine("P1", 6), OrderLine("P2", 12))),
            Order("O2", "C2", "2026-09-28T11:00", listOf(OrderLine("P1", 4))),
        )
        assertEquals(orders, Orders.fromCsv(Orders.toCsv(orders)))
    }
}
