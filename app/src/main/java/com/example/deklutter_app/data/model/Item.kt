package com.example.deklutter_app.data.model

data class Item(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val price: Double = 0.0,
    val category: String = "",
    val city: String = "",
    val imageUrl: String = "",
    val imageUrls: List<String> = emptyList(),
    val sellerId: String = "",
    val sellerName: String = "",
    val sellerEmail: String = "",
    val sellerPhone: String = "",
    val createdAt: Long = 0L,
    val sold: Boolean = false
)
