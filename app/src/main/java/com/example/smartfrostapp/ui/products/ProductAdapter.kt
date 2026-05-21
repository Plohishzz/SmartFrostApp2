package com.example.smartfrostapp.ui.products

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.smartfrostapp.R
import com.example.smartfrostapp.data.model.Product
import com.example.smartfrostapp.databinding.ItemProductBinding

class ProductAdapter(
    private var products: List<Product>,
    private val onUpdateClick: (Product) -> Unit,
    private val onDeleteClick: (Product) -> Unit,
    private val onEditClick: (Product) -> Unit
) : RecyclerView.Adapter<ProductAdapter.ProductViewHolder>() {

    class ProductViewHolder(val binding: ItemProductBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val binding = ItemProductBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ProductViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        val product = products[position]
        val context = holder.binding.root.context

        holder.binding.productName.text = product.name
        holder.binding.productIcon.text = product.icon
        holder.binding.productQuantity.text = product.quantity
        holder.binding.productAddedDate.text = if (product.addedDate.isNotEmpty()) "Добавлено: ${product.addedDate}" else ""

        val (expiryText, expiryColor) = when {
            product.expiryDays == 0 -> context.getString(R.string.today) to Color.parseColor("#F44336")
            product.expiryDays < 0 -> context.getString(R.string.expired) to Color.parseColor("#F44336")
            else -> context.getString(R.string.days_left, product.expiryDays) to if (product.expiryDays <= 2) Color.parseColor("#F44336") else Color.parseColor("#4CAF50")
        }

        holder.binding.productExpiryText.text = expiryText
        holder.binding.productExpiryText.setTextColor(expiryColor)
        holder.binding.expiryBadge.setCardBackgroundColor(ColorStateList.valueOf(expiryColor).withAlpha(25))

        holder.binding.btnMinus.setOnClickListener {
            val parts = product.quantity.split(" ")
            if (parts.size == 2) {
                val amount = parts[0].toIntOrNull() ?: 0
                if (amount > 0) onUpdateClick(product.copy(quantity = "${amount - 1} ${parts[1]}"))
            }
        }

        holder.binding.btnPlus.setOnClickListener {
            val parts = product.quantity.split(" ")
            if (parts.size == 2) {
                val amount = parts[0].toIntOrNull() ?: 0
                onUpdateClick(product.copy(quantity = "${amount + 1} ${parts[1]}"))
            }
        }

        holder.binding.deleteButton.setOnClickListener { onDeleteClick(product) }
        holder.binding.root.setOnClickListener { onEditClick(product) }
    }

    override fun getItemCount() = products.size

    fun updateProducts(newProducts: List<Product>) {
        products = newProducts
        notifyDataSetChanged()
    }
}
