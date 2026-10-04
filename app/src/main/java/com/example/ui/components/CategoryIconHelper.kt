package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

object CategoryIconHelper {
    fun getIcon(iconName: String): ImageVector {
        return when (iconName.lowercase()) {
            "shopping_cart" -> Icons.Default.ShoppingCart
            "restaurant" -> Icons.Default.Restaurant
            "shopping_bag" -> Icons.Default.ShoppingBag
            "directions_car" -> Icons.Default.DirectionsCar
            "home" -> Icons.Default.Home
            "bolt" -> Icons.Default.Bolt
            "movie" -> Icons.Default.Movie
            "local_hospital" -> Icons.Default.LocalHospital
            "flight" -> Icons.Default.Flight
            "autorenew" -> Icons.Default.Autorenew
            "trending_up" -> Icons.Default.TrendingUp
            "spa" -> Icons.Default.Spa
            "attach_money" -> Icons.Default.AttachMoney
            else -> Icons.Default.Category
        }
    }

    fun parseColor(hex: String, fallback: Color = Color(0xFF64748B)): Color {
        return try {
            val cleanHex = hex.removePrefix("#")
            if (cleanHex.length == 6) {
                Color(android.graphics.Color.parseColor("#$cleanHex"))
            } else if (cleanHex.length == 8) {
                Color(android.graphics.Color.parseColor("#$cleanHex"))
            } else {
                fallback
            }
        } catch (_: Exception) {
            fallback
        }
    }
}
