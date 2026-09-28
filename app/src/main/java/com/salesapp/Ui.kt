package com.salesapp

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.salesapp.core.Engine
import com.salesapp.core.OrderLine
import java.util.Locale
import kotlin.math.roundToInt

private fun euro(x: Double) = "€" + String.format(Locale.ROOT, "%.2f", x)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesApp(model: AppModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var visiting by rememberSaveable { mutableStateOf<String?>(null) }
    val engine = model.engine

    val open = visiting
    if (open != null && engine != null) {
        BackHandler { visiting = null }
        VisitScreen(model, engine, open, onDone = { visiting = null })
        return
    }

    val titles = listOf("Customers", "Stock", "Orders", "Settings")
    Scaffold(
        topBar = { TopAppBar(title = { Text(titles[tab]) }) },
        bottomBar = {
            NavigationBar {
                val icons = listOf(Icons.Default.Person, Icons.AutoMirrored.Filled.List, Icons.Default.ShoppingCart, Icons.Default.Settings)
                titles.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(icons[i], contentDescription = null) },
                        label = { Text(t) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                engine == null -> Loading()
                tab == 0 -> CustomersScreen(model, engine) { visiting = it }
                tab == 1 -> StockScreen(engine)
                tab == 2 -> OrdersScreen(model, engine)
                else -> SettingsScreen(model, engine)
            }
            if (model.busy && engine != null) {
                CircularProgressIndicator(Modifier.align(Alignment.TopEnd).padding(12.dp).width(20.dp).height(20.dp), strokeWidth = 2.dp)
            }
        }
    }
}

@Composable
private fun Loading() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text("Learning from your sales history…")
    }
}

@Composable
private fun CustomersScreen(model: AppModel, engine: Engine, onOpen: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val withOrder = model.orders.map { it.customerId }.toSet()
    val shown = engine.data.customers
        .filter { query.isBlank() || "${it.name} ${it.type} ${it.region}".contains(query.trim(), ignoreCase = true) }
        .sortedWith(compareBy({ it.id in withOrder }, { it.priority }, { it.name }))
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text("Search shops") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            if (model.usingSample) {
                Text(
                    "You are seeing sample data. Load your own files in Settings.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        items(shown, key = { it.id }) { c ->
            val top = remember(engine, c.id) { engine.suggestionsFor(c.id).take(3).joinToString(", ") { it.product.name } }
            Card(onClick = { onOpen(c.id) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(c.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(
                            if (c.id in withOrder) "Order saved" else priorityLabel(c.priority),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(listOf(c.type, c.region).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                    if (top.isNotEmpty()) Text("Top picks: $top", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        if (shown.isEmpty()) item { Text("No shops found.", modifier = Modifier.padding(16.dp)) }
    }
}

private fun priorityLabel(p: Int) = when (p) { 1 -> "Key customer"; 2 -> "Regular"; else -> "Small" }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VisitScreen(model: AppModel, engine: Engine, customerId: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val customer = engine.data.customersById[customerId]
    val suggestions = remember(engine, customerId) { engine.suggestionsFor(customerId) }
    val qty = remember(engine, customerId) {
        mutableStateMapOf<String, Int>().apply { suggestions.forEach { put(it.product.id, it.suggestedQty) } }
    }
    val total = suggestions.sumOf { (qty[it.product.id] ?: 0) * it.product.price }
    val units = qty.values.sum()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(customer?.name ?: "Customer") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("$units units", style = MaterialTheme.typography.bodyMedium)
                        Text(euro(total), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        enabled = units > 0,
                        onClick = {
                            model.saveOrder(customerId, suggestions.map { OrderLine(it.product.id, qty[it.product.id] ?: 0) })
                            Toast.makeText(context, "Order saved", Toast.LENGTH_SHORT).show()
                            onDone()
                        },
                    ) { Text("Save order") }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "Best products for this shop, best first. The numbers are suggestions; change them as you like.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            items(suggestions.size, key = { suggestions[it].product.id }) { i ->
                val s = suggestions[i]
                val p = s.product
                val q = qty[p.id] ?: 0
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (q > 0) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    else CardDefaults.cardColors(),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.widthIn(min = 32.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${euro(p.price)} · ${(s.probability * 100).roundToInt()}% likely to buy · ${engine.available(p)} in stock",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            s.reasons.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { qty[p.id] = (q - p.packSize).coerceAtLeast(0) }) { Text("−", style = MaterialTheme.typography.titleLarge) }
                                Text("$q", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 28.dp))
                                TextButton(onClick = { qty[p.id] = q + p.packSize }) { Text("+", style = MaterialTheme.typography.titleLarge) }
                            }
                            if (p.packSize > 1) Text("packs of ${p.packSize}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StockScreen(engine: Engine) {
    val lines = remember(engine) { engine.stock() }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(
                "When shops want more than you have, the app shares it out. Key customers get a bigger share.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        items(lines, key = { it.product.id }) { l ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row {
                        Text(l.product.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (l.isShort) Text(
                            "Short by ${l.wanted - l.available}",
                            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    Text(
                        "Left to sell: ${l.available} · Shops want: ${l.wanted} · Planned: ${l.planned}" +
                            if (l.ordered > 0) " · Already ordered: ${l.ordered}" else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun OrdersScreen(model: AppModel, engine: Engine) {
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }
    val orders = model.orders.sortedByDescending { it.createdAt }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = orders.isNotEmpty(),
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Orders")
                            putExtra(Intent.EXTRA_TEXT, model.ordersCsv())
                        }
                        context.startActivity(Intent.createChooser(send, "Send orders"))
                    },
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Send all")
                }
                OutlinedButton(enabled = orders.isNotEmpty(), onClick = { confirmClear = true }) { Text("Clear all") }
            }
        }
        if (orders.isEmpty()) item { Text("No orders yet. Open a customer to take one.", modifier = Modifier.padding(top = 16.dp)) }
        items(orders, key = { it.id }) { o ->
            val value = o.lines.sumOf { (engine.data.productsById[it.productId]?.price ?: 0.0) * it.quantity }
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(engine.data.customersById[o.customerId]?.name ?: o.customerId, style = MaterialTheme.typography.titleMedium)
                        Text("${o.createdAt} · ${o.lines.size} products · ${euro(value)}", style = MaterialTheme.typography.bodySmall)
                        Text(
                            o.lines.joinToString(", ") { "${it.quantity} × ${engine.data.productsById[it.productId]?.name ?: it.productId}" },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    IconButton(onClick = { model.deleteOrder(o.id) }) { Icon(Icons.Default.Delete, contentDescription = "Delete order") }
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all orders?") },
            text = { Text("Send them first if you still need them. This gives their stock back.") },
            confirmButton = { TextButton(onClick = { model.clearOrders(); confirmClear = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SettingsScreen(model: AppModel, engine: Engine) {
    val context = LocalContext.current
    var pending by remember { mutableStateOf(FileKind.PRODUCTS) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmSample by remember { mutableStateOf(false) }
    var weight by remember(model.marginWeight) { mutableFloatStateOf(model.marginWeight) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val bytes = try {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } catch (e: Exception) { null }
            message = if (bytes == null) "Could not read that file." else model.import(pending, bytes)
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("How to rank products", style = MaterialTheme.typography.titleMedium)
        Slider(value = weight, onValueChange = { weight = it }, onValueChangeFinished = { model.setMargin(weight) })
        Row {
            Text("Sell more", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Text("Earn more per item", style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider()

        Text("Your data", style = MaterialTheme.typography.titleMedium)
        Text(
            "Load CSV or Excel files. The first row must have column names:\n" +
                "• Products: product_id, name, category, price, cost, stock, pack_size\n" +
                "• Customers: customer_id, name, type, region, priority (1 = key, 3 = small)\n" +
                "• Sales history: date, customer_id, product_id, quantity",
            style = MaterialTheme.typography.bodySmall,
        )
        FileKind.entries.forEach { kind ->
            FilledTonalButton(onClick = { pending = kind; picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                Text("Load ${kind.label.lowercase()}")
            }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        OutlinedButton(onClick = { confirmSample = true }, modifier = Modifier.fillMaxWidth()) { Text("Go back to sample data") }
        HorizontalDivider()

        Text("About the ranking", style = MaterialTheme.typography.titleMedium)
        val d = engine.data
        Text("${d.products.size} products, ${d.customers.size} customers, ${d.sales.size} sales lines up to ${engine.asOf}.", style = MaterialTheme.typography.bodySmall)
        val m = engine.model
        Text(
            when {
                m == null -> "Not enough sales history to learn from yet (needs about 3 months). Using simple rules for now."
                m.checkScore != null -> "The app learned from ${m.trainingExamples} past shop-and-product months. " +
                    "Tested on the latest month it had not seen, it put real buys above non-buys ${(m.checkScore!! * 100).roundToInt()}% of the time."
                else -> "The app learned from ${m.trainingExamples} past shop-and-product months."
            },
            style = MaterialTheme.typography.bodySmall,
        )
        Text("Everything runs on this phone. No internet needed.", style = MaterialTheme.typography.bodySmall)
    }

    if (confirmSample) {
        AlertDialog(
            onDismissRequest = { confirmSample = false },
            title = { Text("Use sample data?") },
            text = { Text("This replaces your products, customers, sales and orders on this phone.") },
            confirmButton = { TextButton(onClick = { model.useSampleData(); message = "Sample data loaded."; confirmSample = false }) { Text("Replace") } },
            dismissButton = { TextButton(onClick = { confirmSample = false }) { Text("Cancel") } },
        )
    }
}
