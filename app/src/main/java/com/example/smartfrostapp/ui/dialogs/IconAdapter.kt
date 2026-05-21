package com.example.smartfrostapp.ui.dialogs

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.smartfrostapp.databinding.ItemIconBinding

class IconAdapter(
    private val icons: List<String>,
    private val onIconSelected: (String) -> Unit
) : RecyclerView.Adapter<IconAdapter.IconViewHolder>() {

    class IconViewHolder(val binding: ItemIconBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IconViewHolder {
        val binding = ItemIconBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return IconViewHolder(binding)
    }

    override fun onBindViewHolder(holder: IconViewHolder, position: Int) {
        val icon = icons[position]
        holder.binding.iconText.text = icon
        holder.binding.root.setOnClickListener { onIconSelected(icon) }
    }

    override fun getItemCount() = icons.size
}
