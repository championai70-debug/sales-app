package com.salesapp.core

import java.time.LocalDate

/** Something the reps sell. [stock] is what is left to share out between customers. */
data class Product(
    val id: String,
    val name: String,
    val category: String,
    val price: Double,
    val cost: Double,
    val stock: Int,
    val packSize: Int = 1,
) {
    val margin: Double get() = price - cost
    val marginPercent: Double get() = if (price > 0) (price - cost) / price else 0.0
}

/** A shop the reps visit. Priority 1 is most important, 3 least. */
data class Customer(
    val id: String,
    val name: String,
    val type: String,
    val region: String,
    val priority: Int = 2,
)

/** One line of past sales history. */
data class Sale(
    val date: LocalDate,
    val customerId: String,
    val productId: String,
    val quantity: Int,
)

data class OrderLine(val productId: String, val quantity: Int)

/** An order a rep saved on the phone. */
data class Order(
    val id: String,
    val customerId: String,
    val createdAt: String,
    val lines: List<OrderLine>,
)

/** Everything the app knows: products, customers and past sales. */
data class DataSet(
    val products: List<Product>,
    val customers: List<Customer>,
    val sales: List<Sale>,
) {
    val productsById: Map<String, Product> = products.associateBy { it.id }
    val customersById: Map<String, Customer> = customers.associateBy { it.id }
}
