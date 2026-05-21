package com.example.smartfrostapp.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.smartfrostapp.data.model.ProductTemplate

object UserProductTemplates {
    private const val PREFS_NAME = "user_templates_prefs"
    private const val KEY_TEMPLATES = "user_templates"
    private const val DELIMITER = "|||"
    private const val FIELD_SEP = "::"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveTemplate(template: ProductTemplate, quantity: String = "1", unit: String = "шт") {
        val existing = loadTemplates().toMutableList()
        val updatedTemplate = template.copy(defaultQuantity = quantity, defaultUnit = unit)
        val index = existing.indexOfFirst { it.name.equals(template.name, ignoreCase = true) }
        if (index != -1) {
            existing[index] = updatedTemplate
        } else {
            existing.add(updatedTemplate)
        }
        val serialized = existing.joinToString(DELIMITER) {
            "${it.name}$FIELD_SEP${it.icon}$FIELD_SEP${it.defaultShelfLifeDays}$FIELD_SEP${it.category}$FIELD_SEP${it.defaultQuantity}$FIELD_SEP${it.defaultUnit}"
        }
        prefs.edit().putString(KEY_TEMPLATES, serialized).apply()
    }

    fun loadTemplates(): List<ProductTemplate> {
        val serialized = prefs.getString(KEY_TEMPLATES, null) ?: return emptyList()
        return serialized.split(DELIMITER).mapNotNull { entry ->
            val parts = entry.split(FIELD_SEP)
            if (parts.size >= 4) {
                ProductTemplate(
                    name = parts[0],
                    icon = parts[1],
                    defaultShelfLifeDays = parts[2].toIntOrNull() ?: 7,
                    category = parts[3],
                    defaultQuantity = parts.getOrNull(4) ?: "1",
                    defaultUnit = parts.getOrNull(5) ?: "шт"
                )
            } else null
        }
    }

    fun getAllTemplates(): List<ProductTemplate> {
        return loadTemplates() + ProductTemplates.templates
    }

    fun deleteTemplate(template: ProductTemplate) {
        val existing = loadTemplates().toMutableList()
        existing.removeAll { it.name.equals(template.name, ignoreCase = true) }
        val serialized = existing.joinToString(DELIMITER) {
            "${it.name}$FIELD_SEP${it.icon}$FIELD_SEP${it.defaultShelfLifeDays}$FIELD_SEP${it.category}$FIELD_SEP${it.defaultQuantity}$FIELD_SEP${it.defaultUnit}"
        }
        prefs.edit().putString(KEY_TEMPLATES, serialized).apply()
    }

    fun isUserTemplate(template: ProductTemplate): Boolean {
        return loadTemplates().any { it.name.equals(template.name, ignoreCase = true) }
    }
}
