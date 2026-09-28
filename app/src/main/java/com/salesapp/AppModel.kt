package com.salesapp

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.salesapp.core.DataSet
import com.salesapp.core.Engine
import com.salesapp.core.Importer
import com.salesapp.core.Order
import com.salesapp.core.OrderLine
import com.salesapp.core.Orders
import com.salesapp.core.SampleData
import com.salesapp.core.Tables
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class FileKind(val label: String) { PRODUCTS("Products"), CUSTOMERS("Customers"), SALES("Sales history") }

/**
 * Holds everything the screens show. All data lives on the phone as CSV files in the
 * app's private folder; nothing is sent anywhere unless the rep shares orders.
 */
class AppModel(context: Context, private val scope: CoroutineScope) {
    private val dir: File = context.filesDir
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var data by mutableStateOf<DataSet?>(null); private set
    var orders by mutableStateOf<List<Order>>(emptyList()); private set
    var engine by mutableStateOf<Engine?>(null); private set
    var busy by mutableStateOf(true); private set
    var usingSample by mutableStateOf(prefs.getBoolean("sample", true)); private set
    var marginWeight by mutableFloatStateOf(prefs.getFloat("marginWeight", 0.5f)); private set

    private var job: Job? = null

    fun start() {
        scope.launch {
            val (d, o) = withContext(Dispatchers.IO) { load() }
            data = d
            orders = o
            rebuild()
        }
    }

    /** Re-trains the model and re-plans stock in the background. */
    private fun rebuild() {
        val d = data ?: return
        val o = orders
        val w = marginWeight.toDouble()
        job?.cancel()
        busy = true
        job = scope.launch {
            val e = withContext(Dispatchers.Default) { Engine(d, o, w) }
            engine = e
            busy = false
        }
    }

    fun setMargin(w: Float) {
        marginWeight = w
        prefs.edit().putFloat("marginWeight", w).apply()
        rebuild()
    }

    fun saveOrder(customerId: String, lines: List<OrderLine>) {
        val now = LocalDateTime.now()
        val order = Order(
            id = "O" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")),
            customerId = customerId,
            createdAt = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
            lines = lines.filter { it.quantity > 0 },
        )
        setOrders(orders.filter { it.id != order.id } + order)
    }

    fun deleteOrder(id: String) = setOrders(orders.filter { it.id != id })

    fun clearOrders() = setOrders(emptyList())

    fun ordersCsv(): String = Orders.toCsv(orders, data)

    private fun setOrders(o: List<Order>) {
        orders = o
        scope.launch(Dispatchers.IO) { File(dir, "orders.csv").writeText(Orders.toCsv(o, data)) }
        rebuild()
    }

    /** Loads a CSV or Excel file. Returns a short message for the rep. */
    fun import(kind: FileKind, bytes: ByteArray): String {
        val d = data ?: return "Still loading, try again in a moment."
        val rows = try { Tables.read(bytes) } catch (e: Exception) { return "Could not open that file. Use CSV or Excel (.xlsx)." }
        val (next, count, problems) = when (kind) {
            FileKind.PRODUCTS -> Importer.products(rows).let { Triple(d.copy(products = it.items), it.items.size, it.problems) }
            FileKind.CUSTOMERS -> Importer.customers(rows).let { Triple(d.copy(customers = it.items), it.items.size, it.problems) }
            FileKind.SALES -> Importer.sales(rows).let { Triple(d.copy(sales = it.items), it.items.size, it.problems) }
        }
        if (count == 0) return "Nothing loaded. " + (problems.firstOrNull() ?: "Check the column names.")
        data = DataSet(next.products, next.customers, next.sales)
        usingSample = false
        prefs.edit().putBoolean("sample", false).apply()
        save(data!!)
        rebuild()
        val skipped = if (problems.isEmpty()) "" else " Skipped ${problems.size} rows (${problems.first()})"
        return "Loaded $count ${kind.label.lowercase()}.$skipped"
    }

    fun useSampleData() {
        val d = SampleData.generate()
        data = d
        usingSample = true
        prefs.edit().putBoolean("sample", true).apply()
        save(d)
        setOrders(emptyList())
    }

    private fun save(d: DataSet) {
        scope.launch(Dispatchers.IO) {
            File(dir, "products.csv").writeText(SampleData.productsCsv(d))
            File(dir, "customers.csv").writeText(SampleData.customersCsv(d))
            File(dir, "sales.csv").writeText(SampleData.salesCsv(d))
        }
    }

    private fun load(): Pair<DataSet, List<Order>> {
        val p = File(dir, "products.csv"); val c = File(dir, "customers.csv"); val s = File(dir, "sales.csv")
        val d = if (p.exists() && c.exists() && s.exists()) {
            DataSet(
                Importer.products(Tables.readCsv(p.readText())).items,
                Importer.customers(Tables.readCsv(c.readText())).items,
                Importer.sales(Tables.readCsv(s.readText())).items,
            )
        } else {
            SampleData.generate().also { sample ->
                p.writeText(SampleData.productsCsv(sample))
                c.writeText(SampleData.customersCsv(sample))
                s.writeText(SampleData.salesCsv(sample))
            }
        }
        val o = File(dir, "orders.csv").takeIf { it.exists() }?.let { Orders.fromCsv(it.readText()) } ?: emptyList()
        return d to o
    }
}
