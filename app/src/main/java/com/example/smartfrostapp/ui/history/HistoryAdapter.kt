package com.example.smartfrostapp.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.smartfrostapp.data.model.ActionHistoryEntry
import com.example.smartfrostapp.data.model.ActionType
import com.example.smartfrostapp.databinding.ItemHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter(
    private var entries: List<ActionHistoryEntry>,
    private val onRestore: (ActionHistoryEntry) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    class HistoryViewHolder(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val entry = entries[position]

        holder.binding.historyIcon.text = entry.productIcon
        holder.binding.historyProductName.text = entry.productName

        val parts = entry.productJson.split("::")
        val quantity = if (parts.size >= 3) parts[2] else ""
        holder.binding.historyQuantity.text = quantity

        val (chipText, chipColor) = when (entry.type) {
            ActionType.ADDED -> "Добавлен" to 0xFF4CAF50.toInt()
            ActionType.EDITED -> "Изменён" to 0xFF2196F3.toInt()
            ActionType.DELETED -> "Удалён" to 0xFFF44336.toInt()
        }
        holder.binding.historyActionChip.text = chipText
        holder.binding.historyActionChip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(chipColor))
        holder.binding.historyActionChip.setTextColor(android.graphics.Color.WHITE)

        val dateFormat = SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault())
        val timestampMs = entry.timestamp
        val date = if (timestampMs > 1e12) Date(timestampMs) else Date(timestampMs * 1000)
        holder.binding.historyTimestamp.text = dateFormat.format(date)

        holder.binding.btnRestore.visibility = if (entry.type == ActionType.DELETED) android.view.View.VISIBLE else android.view.View.GONE
        holder.binding.btnRestore.setOnClickListener { onRestore(entry) }
    }

    override fun getItemCount() = entries.size

    fun updateEntries(newEntries: List<ActionHistoryEntry>) {
        entries = newEntries
        notifyDataSetChanged()
    }
}
