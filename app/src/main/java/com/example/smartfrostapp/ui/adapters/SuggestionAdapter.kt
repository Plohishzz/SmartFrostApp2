package com.example.smartfrostapp.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.smartfrostapp.R
import com.example.smartfrostapp.data.model.ProductTemplate

class SuggestionAdapter(
    private var suggestions: List<ProductTemplate>,
    private val onItemClick: (ProductTemplate) -> Unit,
    private val onDeleteClick: (ProductTemplate) -> Unit
) : RecyclerView.Adapter<SuggestionAdapter.SuggestionViewHolder>() {

    class SuggestionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.suggestion_name)
        val expiry: TextView = view.findViewById(R.id.suggestion_expiry)
        val icon: TextView = view.findViewById(R.id.suggestion_icon)
        val delete: ImageView = view.findViewById(R.id.suggestion_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_suggestion, parent, false)
        return SuggestionViewHolder(view)
    }

    override fun onBindViewHolder(holder: SuggestionViewHolder, position: Int) {
        val template = suggestions[position]
        holder.name.text = template.name
        holder.icon.text = template.icon
        holder.expiry.text = "Suggested expiry date: +${template.defaultShelfLifeDays} days"

        holder.itemView.setOnClickListener {
            onItemClick(template)
        }

        holder.delete.setOnClickListener {
            onDeleteClick(template)
        }
    }

    override fun getItemCount() = suggestions.size

    fun updateSuggestions(newSuggestions: List<ProductTemplate>) {
        suggestions = newSuggestions
        notifyDataSetChanged()
    }
}
