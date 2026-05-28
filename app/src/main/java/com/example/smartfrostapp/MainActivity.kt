package com.example.smartfrostapp

import android.Manifest
import android.app.DatePickerDialog
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.smartfrostapp.data.model.ActionHistoryEntry
import com.example.smartfrostapp.data.model.ActionType
import com.example.smartfrostapp.data.model.BarcodeProduct
import com.example.smartfrostapp.data.model.Product
import com.example.smartfrostapp.data.model.ProductTemplate
import com.example.smartfrostapp.data.repository.ActionHistoryRepository
import com.example.smartfrostapp.data.repository.BarcodeRepository
import com.example.smartfrostapp.data.repository.ProductRepository
import com.example.smartfrostapp.data.repository.ProductTemplates
import com.example.smartfrostapp.data.repository.UserProductTemplates
import com.example.smartfrostapp.databinding.ActivityMainBinding
import com.example.smartfrostapp.databinding.DialogAddCategoryBinding
import com.example.smartfrostapp.databinding.DialogAddProductNewBinding
import com.example.smartfrostapp.databinding.DialogCategoryPickerBinding
import com.example.smartfrostapp.databinding.DialogIconPickerBinding
import com.example.smartfrostapp.databinding.DialogQuantitySelectorBinding
import com.example.smartfrostapp.databinding.ScreenLoginBinding
import com.example.smartfrostapp.databinding.ScreenProductsBinding
import com.example.smartfrostapp.databinding.ScreenHistoryBinding
import com.example.smartfrostapp.databinding.ScreenScannerBinding
import com.example.smartfrostapp.databinding.ScreenSettingsBinding
import com.example.smartfrostapp.databinding.ScreenRegisterBinding
import com.example.smartfrostapp.ui.history.HistoryAdapter
import com.example.smartfrostapp.ui.adapters.SuggestionAdapter
import com.example.smartfrostapp.ui.dialogs.IconAdapter
import com.example.smartfrostapp.ui.products.ProductAdapter
import com.example.smartfrostapp.utils.Constants
import com.example.smartfrostapp.utils.calculateDaysRemaining
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import com.example.smartfrostapp.utils.NetworkConstants

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences

    // Данные сессии
    private var isLoggedIn = false
    private var userId: Int = 0
    private var userToken: String = ""

    private var currentProductsBinding: ScreenProductsBinding? = null
    private val products = mutableListOf<Product>()
    private val categories = mutableListOf("Молочное", "Мясо", "Рыба", "Овощи", "Фрукты", "Бакалея", "Напитки", "Замороженное", "Соусы и приправы", "Прочее")
    private val units = listOf("кг", "л", "шт", "г", "уп")

    private var searchQuery: String = ""
    private var selectedSort: String = "default"
    private val selectedCategoryFilters = mutableSetOf<String>()

    private lateinit var cameraExecutor: ExecutorService
    private var barcodeScanner: BarcodeScanner? = null
    private var isScanningActive = false
    private var lastScanTime: Long = 0
    private val SCAN_INTERVAL_MS = 300L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        applyTheme()

        // ВОССТАНАВЛИВАЕМ СЕССИЮ ПРИ ЗАПУСКЕ И ПЕРЕВОРОТЕ ЭКРАНА!
        isLoggedIn = prefs.getBoolean("is_logged_in", false)
        userId = prefs.getInt("user_id", 0)
        userToken = prefs.getString("user_token", "") ?: ""

        /*isLoggedIn = true
        userId = 1
        userToken = ""*/

        UserProductTemplates.init(this)
        ProductRepository.init(this)
        ActionHistoryRepository.init(this)
        BarcodeRepository.init(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        // СНАЧАЛА настраиваем слушатель нажатий нижнего меню
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_stocks -> {
                    showProducts()
                    true
                }
                R.id.nav_scanner -> {
                    showScanner()
                    true
                }
                R.id.nav_settings -> {
                    showSettings()
                    true
                }
                else -> true
            }
        }

        // ПОТОМ решаем, что рисовать
        if (!isLoggedIn) {
            showLogin()
        } else {
            // Достаем сохраненную вкладку (если экран перевернули) или по умолчанию берем "Продукты"
            val targetTab = savedInstanceState?.getInt("SAVED_TAB") ?: R.id.nav_stocks
            showMain(targetTab)
        }

        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_CODE_39,
                Barcode.FORMAT_CODABAR,
                Barcode.FORMAT_ITF,
                Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_DATA_MATRIX
            )
            .build()
        barcodeScanner = BarcodeScanning.getClient(options)
    }

    private fun showLogin() {
        binding.bottomNavigation.visibility = View.GONE
        val loginBinding = ScreenLoginBinding.inflate(layoutInflater, binding.fragmentContainer, false)
        binding.fragmentContainer.removeAllViews()
        binding.fragmentContainer.addView(loginBinding.root)

        loginBinding.loginButton.setOnClickListener {
            val email = loginBinding.emailEditText.text.toString().trim()
            val password = loginBinding.passwordEditText.text.toString().trim()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                performBackendLogin(email, password) { id, token, message ->
                    runOnUiThread {
                        if (id != null && token != null) {
                            userId = id
                            userToken = token
                            isLoggedIn = true

                            prefs.edit().apply {
                                putBoolean("is_logged_in", true)
                                putInt("user_id", id)
                                putString("user_token", token)
                                putString("user_email", email)
                                apply()
                            }

                            loadUserInfoFromBackend(token)
                            showMain()
                        } else {
                            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                Toast.makeText(this, "Заполните все поля", Toast.LENGTH_SHORT).show()
            }
        }
        loginBinding.registerLink.setOnClickListener {
            showRegister()
        }
    }

    private fun showRegister() {
        binding.bottomNavigation.visibility = View.GONE
        val registerBinding = ScreenRegisterBinding.inflate(layoutInflater, binding.fragmentContainer, false)
        binding.fragmentContainer.removeAllViews()
        binding.fragmentContainer.addView(registerBinding.root)

        registerBinding.registerButton.setOnClickListener {
            val name = registerBinding.editRegisterName.text.toString().trim()
            val email = registerBinding.editRegisterEmail.text.toString().trim()
            val password = registerBinding.editRegisterPassword.text.toString().trim()

            if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
                registerBinding.txtRegisterError.visibility = View.VISIBLE
                registerBinding.txtRegisterError.text = "Заполните все поля"
                return@setOnClickListener
            }

            registerBinding.txtRegisterError.visibility = View.VISIBLE
            registerBinding.txtRegisterError.text = "Отправляем код подтверждения..."

            lifecycleScope.launch(Dispatchers.IO) {
                val result = com.example.smartfrostapp.network.ApiClient.sendVerificationCode(email)
                runOnUiThread {
                    result.fold(
                        onSuccess = { message ->
                            val intent = android.content.Intent(this@MainActivity, VerificationActivity::class.java)
                            intent.putExtra("email", email)
                            intent.putExtra("name", name)
                            intent.putExtra("password", password)
                            startActivity(intent)
                        },
                        onFailure = { error ->
                            registerBinding.txtRegisterError.visibility = View.VISIBLE
                            registerBinding.txtRegisterError.text = error.message ?: "Ошибка отправки кода"
                        }
                    )
                }
            }
        }

        registerBinding.btnBackToLogin.setOnClickListener {
            showLogin()
        }
    }

    private fun showMain(targetTab: Int = R.id.nav_stocks) {
        binding.bottomNavigation.visibility = View.VISIBLE

        products.clear()

        val saved = ProductRepository.loadProducts()
        if (saved.isEmpty()) {
            products.addAll(listOf(
                Product("1", "Молоко 3.2%", "1 л", "Молочное", 3, "", manufactureDate = "20.10.23", expiryDate = "27.10.23", addedDate = "20.10.23"),
                Product("2", "Куриная грудка", "500 г", "Мясо", 2, "", manufactureDate = "22.10.23", expiryDate = "24.10.23", addedDate = "22.10.23"),
                Product("3", "Яблоки", "1.5 кг", "Фрукты", 14, "🍎", manufactureDate = "15.10.23", expiryDate = "29.10.23", addedDate = "15.10.23")
            ))
        } else {
            products.addAll(saved)
        }

        if (binding.bottomNavigation.selectedItemId == targetTab) {
            when (targetTab) {
                R.id.nav_scanner -> showScanner()
                R.id.nav_settings -> showSettings()
                else -> showProducts()
            }
        } else {
            binding.bottomNavigation.selectedItemId = targetTab
        }

        if (userToken.isNotEmpty()) {
            syncProductsFromBackend()
        }
    }

    private fun syncProductsFromBackend() {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = com.example.smartfrostapp.network.ApiClient.getItems(userToken)
            runOnUiThread {
                result.fold(
                    onSuccess = { backendProducts ->
                        if (backendProducts.isEmpty()) return@fold

                        val dateFormat = java.text.SimpleDateFormat("dd.MM.yy", Locale.getDefault())
                        var changed = false

                        for (bp in backendProducts) {
                            if (bp.deleted) continue

                            val expiryDateStr = bp.expiration?.let {
                                val parts = it.split("-")
                                if (parts.size == 3) "${parts[2].takeLast(2)}.${parts[1]}.${parts[0]}" else ""
                            } ?: ""

                            var existingIndex = products.indexOfFirst { it.backendId == bp.id }

                            if (existingIndex == -1) {
                                existingIndex = products.indexOfFirst {
                                    it.backendId == 0 && it.name == bp.name && it.quantity == "${if (bp.quantity % 1.0 == 0.0) bp.quantity.toInt() else bp.quantity} ${bp.unit}"
                                }
                            }

                            if (existingIndex != -1) {
                                val local = products[existingIndex]
                                val localHasDate = local.expiryDate.isNotEmpty()
                                val backendHasDate = expiryDateStr.isNotEmpty()

                                val needsUpdate = local.backendId != bp.id ||
                                    (backendHasDate && !localHasDate) ||
                                    (!backendHasDate && localHasDate)

                                if (needsUpdate) {
                                    val expiryDays = if (expiryDateStr.isNotEmpty()) {
                                        com.example.smartfrostapp.utils.calculateDaysRemaining(expiryDateStr)
                                    } else {
                                        local.expiryDays
                                    }
                                    products[existingIndex] = local.copy(
                                        backendId = bp.id,
                                        name = bp.name,
                                        quantity = "${if (bp.quantity % 1.0 == 0.0) bp.quantity.toInt() else bp.quantity} ${bp.unit}",
                                        category = bp.category ?: local.category,
                                        expiryDays = expiryDays,
                                        expiryDate = if (backendHasDate) expiryDateStr else local.expiryDate,
                                        manufactureDate = if (backendHasDate) {
                                            bp.expiration?.let {
                                                try {
                                                    dateFormat.format(java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(it) ?: java.util.Date())
                                                } catch (e: Exception) { local.manufactureDate }
                                            } ?: local.manufactureDate
                                        } else local.manufactureDate
                                    )
                                    changed = true
                                }
                            } else {
                                val expiryDays = if (expiryDateStr.isNotEmpty()) {
                                    com.example.smartfrostapp.utils.calculateDaysRemaining(expiryDateStr)
                                } else 7
                                val quantityStr = if (bp.quantity % 1.0 == 0.0) bp.quantity.toInt().toString() else bp.quantity.toString()
                                products.add(
                                    Product(
                                        id = UUID.randomUUID().toString(),
                                        name = bp.name,
                                        quantity = "$quantityStr ${bp.unit}",
                                        category = bp.category ?: "Прочее",
                                        expiryDays = expiryDays,
                                        icon = com.example.smartfrostapp.utils.Constants.suggestIcon(bp.name),
                                        manufactureDate = bp.expiration?.let {
                                            try {
                                                dateFormat.format(java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(it) ?: java.util.Date())
                                            } catch (e: Exception) { "" }
                                        } ?: "",
                                        expiryDate = expiryDateStr,
                                        addedDate = dateFormat.format(java.util.Date()),
                                        backendId = bp.id
                                    )
                                )
                                changed = true
                            }
                        }

                        if (changed) {
                            ProductRepository.saveProducts(products)
                            currentProductsBinding?.let { applyFiltersAndSort(it) }
                        }
                    },
                    onFailure = { error ->
                        android.util.Log.e("SyncProducts", "Failed to load from backend: ${error.message}")
                    }
                )
            }
        }
    }

    private fun showProducts() {
        val productsBinding = ScreenProductsBinding.inflate(layoutInflater, binding.fragmentContainer, false)
        binding.fragmentContainer.removeAllViews()
        binding.fragmentContainer.addView(productsBinding.root)
        currentProductsBinding = productsBinding

        setupSearch(productsBinding)
        productsBinding.btnSortFilter.setOnClickListener {
            showSortFilterSheet(productsBinding)
        }
        applyFiltersAndSort(productsBinding)

        productsBinding.fabAddProduct.setOnClickListener {
            showProductDialog()
        }
    }

    private fun setupSearch(productsBinding: ScreenProductsBinding) {
        productsBinding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim() ?: ""
                applyFiltersAndSort(productsBinding)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun applyFiltersAndSort(productsBinding: ScreenProductsBinding) {
        var filtered = products.filter { product ->
            val matchesSearch = searchQuery.isEmpty() ||
                    product.name.contains(searchQuery, ignoreCase = true) ||
                    product.quantity.contains(searchQuery, ignoreCase = true)
            val matchesCategory = selectedCategoryFilters.isEmpty() || selectedCategoryFilters.contains(product.category)
            matchesSearch && matchesCategory
        }

        filtered = when (selectedSort) {
            "name" -> filtered.sortedBy { it.name }
            "expiry" -> filtered.sortedBy { it.expiryDays }
            "category" -> filtered.sortedBy { it.category }
            "expired" -> filtered.filter { it.expiryDays < 0 }.sortedBy { it.expiryDays }
            else -> filtered
        }

        val productAdapter = ProductAdapter(
            products = filtered,
            onUpdateClick = { updated ->
                val index = products.indexOfFirst { it.id == updated.id }
                if (index != -1) {
                    products[index] = updated
                    ProductRepository.saveProducts(products)
                    logAction(ActionType.EDITED, updated)
                    syncUpdateProductToBackend(updated)
                    applyFiltersAndSort(productsBinding)
                }
            },
            onDeleteClick = { productToDelete ->
                logAction(ActionType.DELETED, productToDelete)
                syncDeleteProductToBackend(productToDelete.backendId)
                products.remove(productToDelete)
                ProductRepository.saveProducts(products)
                applyFiltersAndSort(productsBinding)
            },
            onEditClick = { productToEdit ->
                showProductDialog(productToEdit)
            }
        )

        productsBinding.productsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = productAdapter
        }
    }

    private fun showSortFilterSheet(productsBinding: ScreenProductsBinding) {
        val sheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_sort_filter, null)
        val bottomSheet = BottomSheetDialog(this)
        bottomSheet.setContentView(sheetView)

        val chipGroup = sheetView.findViewById<com.google.android.material.chip.ChipGroup>(R.id.sort_chip_group)
        val btnClearFilters = sheetView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_clear_filters)
        val categoryContainer = sheetView.findViewById<LinearLayout>(R.id.category_filter_container)

        when (selectedSort) {
            "name" -> chipGroup.check(R.id.chip_name)
            "expiry" -> chipGroup.check(R.id.chip_expiry)
            "category" -> chipGroup.check(R.id.chip_category)
            "expired" -> chipGroup.check(R.id.chip_expired)
            else -> chipGroup.check(R.id.chip_default)
        }

        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                selectedSort = when (checkedIds[0]) {
                    R.id.chip_name -> "name"
                    R.id.chip_expiry -> "expiry"
                    R.id.chip_category -> "category"
                    R.id.chip_expired -> "expired"
                    else -> "default"
                }
                applyFiltersAndSort(productsBinding)
            }
        }

        buildCategoryFilterChips(categoryContainer, btnClearFilters, productsBinding)

        btnClearFilters.setOnClickListener {
            selectedCategoryFilters.clear()
            btnClearFilters.visibility = View.GONE
            applyFiltersAndSort(productsBinding)
            buildCategoryFilterChips(categoryContainer, btnClearFilters, productsBinding)
        }

        sheetView.findViewById<ImageButton>(R.id.btn_close_sheet).setOnClickListener {
            bottomSheet.dismiss()
        }

        bottomSheet.show()
    }

    private fun buildCategoryFilterChips(container: LinearLayout, btnClear: com.google.android.material.button.MaterialButton, productsBinding: ScreenProductsBinding) {
        container.removeAllViews()
        btnClear.visibility = if (selectedCategoryFilters.isNotEmpty()) View.VISIBLE else View.GONE

        val allChip = Chip(this).apply {
            text = "Все"
            isCheckable = true
            isChecked = selectedCategoryFilters.isEmpty()
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedCategoryFilters.clear()
                    btnClear.visibility = View.GONE
                    applyFiltersAndSort(productsBinding)
                    buildCategoryFilterChips(container, btnClear, productsBinding)
                }
            }
        }
        container.addView(allChip)

        categories.forEach { category ->
            val chip = Chip(this).apply {
                text = category
                isCheckable = true
                isChecked = selectedCategoryFilters.contains(category)
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedCategoryFilters.add(category)
                    } else {
                        selectedCategoryFilters.remove(category)
                    }
                    btnClear.visibility = if (selectedCategoryFilters.isNotEmpty()) View.VISIBLE else View.GONE
                    applyFiltersAndSort(productsBinding)
                    buildCategoryFilterChips(container, btnClear, productsBinding)
                }
            }
            container.addView(chip)
        }
    }

    private fun showProductDialog(productToEdit: Product? = null, barcode: String? = null, barcodeProduct: BarcodeProduct? = null) {
        val dialogBinding = DialogAddProductNewBinding.inflate(LayoutInflater.from(this))
        val isEdit = productToEdit != null
        var selectedIcon = if (isEdit) productToEdit!!.icon else (barcodeProduct?.icon ?: "📦")
        var selectedProductCategory = if (isEdit) productToEdit!!.category else (barcodeProduct?.category ?: categories[0])
        var suggestionAdapter: SuggestionAdapter? = null

        fun applyTemplateToUI(template: ProductTemplate) {
            selectedIcon = template.icon
            selectedProductCategory = template.category
            dialogBinding.selectedIcon.text = selectedIcon
            dialogBinding.editProductName.setText(template.name)
            dialogBinding.btnSelectCategory.text = template.category

            dialogBinding.unitDropdown.setText(template.defaultUnit, false)
            dialogBinding.btnOpenQuantitySelector.text = "${template.defaultQuantity} ${template.defaultUnit}"
            dialogBinding.editProductQuantity.setText(template.defaultQuantity)

            val cal = Calendar.getInstance()
            val dateFormat = SimpleDateFormat("dd.MM.yy", Locale.getDefault())
            dialogBinding.editManufactureDate.setText(dateFormat.format(cal.time))
            cal.add(Calendar.DAY_OF_YEAR, template.defaultShelfLifeDays)
            dialogBinding.editExpiryDate.setText(dateFormat.format(cal.time))

            dialogBinding.productDetailsScroll.visibility = View.VISIBLE
            dialogBinding.suggestionsRecyclerView.visibility = View.GONE
        }

        dialogBinding.suggestionsRecyclerView.layoutManager = LinearLayoutManager(this)
        suggestionAdapter = SuggestionAdapter(emptyList(),
            onItemClick = { template ->
                applyTemplateToUI(template)
            },
            onDeleteClick = { template ->
                UserProductTemplates.deleteTemplate(template)
                val currentSuggestions = UserProductTemplates.getAllTemplates().filter { t ->
                    t != template && t.name.contains(dialogBinding.editProductName.text.toString(), ignoreCase = true)
                }.take(20)
                suggestionAdapter?.updateSuggestions(currentSuggestions)
            }
        )
        dialogBinding.suggestionsRecyclerView.adapter = suggestionAdapter

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setCancelable(true)
            .create()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dialog.show()

        dialogBinding.btnClose.setOnClickListener {
            dialog.dismiss()
        }

        if (isEdit) {
            dialogBinding.editProductName.setText(productToEdit?.name)
            dialogBinding.selectedIcon.text = selectedIcon
            val qtyParts = productToEdit?.quantity?.split(" ") ?: listOf("1", "кг")
            val amount = qtyParts[0]
            val unit = qtyParts.getOrNull(1) ?: "кг"
            dialogBinding.editProductQuantity.setText(amount)
            dialogBinding.unitDropdown.setText(unit, false)
            dialogBinding.btnOpenQuantitySelector.text = "$amount $unit"
            dialogBinding.btnSelectCategory.text = selectedProductCategory
            dialogBinding.editManufactureDate.setText(productToEdit?.manufactureDate)
            dialogBinding.editExpiryDate.setText(productToEdit?.expiryDate)
            dialogBinding.productDetailsScroll.visibility = View.VISIBLE
            dialogBinding.suggestionsRecyclerView.visibility = View.GONE
        } else if (barcodeProduct != null) {
            dialogBinding.editProductName.setText(barcodeProduct.productName)
            dialogBinding.selectedIcon.text = selectedIcon
            dialogBinding.editProductQuantity.setText(barcodeProduct.quantity)
            dialogBinding.unitDropdown.setText(barcodeProduct.unit, false)
            dialogBinding.btnOpenQuantitySelector.text = "${barcodeProduct.quantity} ${barcodeProduct.unit}"
            dialogBinding.btnSelectCategory.text = selectedProductCategory

            val cal = Calendar.getInstance()
            val dateFormat = SimpleDateFormat("dd.MM.yy", Locale.getDefault())
            dialogBinding.editManufactureDate.setText(dateFormat.format(cal.time))
            cal.add(Calendar.DAY_OF_YEAR, barcodeProduct.expiryDays)
            dialogBinding.editExpiryDate.setText(dateFormat.format(cal.time))

            dialogBinding.productDetailsScroll.visibility = View.VISIBLE
            dialogBinding.suggestionsRecyclerView.visibility = View.GONE
        }

        dialogBinding.editProductName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                if (query.isNotEmpty()) {
                    dialogBinding.productDetailsScroll.visibility = View.VISIBLE
                } else {
                    if (!isEdit) dialogBinding.productDetailsScroll.visibility = View.GONE
                }

                var templateMatched = false
                if (query.length >= 2) {
                    val filtered = UserProductTemplates.getAllTemplates().filter {
                        it.name.equals(query, ignoreCase = true) || it.name.contains(query, ignoreCase = true)
                    }.take(20)

                    if (filtered.isNotEmpty()) {
                        suggestionAdapter?.updateSuggestions(filtered)
                        dialogBinding.suggestionsRecyclerView.visibility = View.VISIBLE

                        val exactMatch = filtered.firstOrNull { it.name.equals(query, ignoreCase = true) }
                        val firstMatch = exactMatch ?: filtered.firstOrNull()
                        if (firstMatch != null && !isEdit) {
                            selectedIcon = firstMatch.icon
                            selectedProductCategory = firstMatch.category
                            dialogBinding.selectedIcon.text = selectedIcon
                            dialogBinding.btnSelectCategory.text = firstMatch.category
                            dialogBinding.unitDropdown.setText(firstMatch.defaultUnit, false)
                            dialogBinding.btnOpenQuantitySelector.text = "${firstMatch.defaultQuantity} ${firstMatch.defaultUnit}"
                            dialogBinding.editProductQuantity.setText(firstMatch.defaultQuantity)
                            templateMatched = true
                        }
                    } else {
                        dialogBinding.suggestionsRecyclerView.visibility = View.GONE
                    }
                } else {
                    dialogBinding.suggestionsRecyclerView.visibility = View.GONE
                }

                val suggestedIcon = Constants.suggestIcon(query)
                if (suggestedIcon != "📦" && !templateMatched) {
                    selectedIcon = suggestedIcon
                    dialogBinding.selectedIcon.text = selectedIcon
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        dialogBinding.iconPickerButton.setOnClickListener {
            val pickerBinding = DialogIconPickerBinding.inflate(LayoutInflater.from(this))
            val alertDialog = AlertDialog.Builder(this).setView(pickerBinding.root).create()

            pickerBinding.iconRecyclerView.adapter = IconAdapter(Constants.ALL_ICONS) { icon ->
                selectedIcon = icon
                dialogBinding.selectedIcon.text = selectedIcon
                alertDialog.dismiss()
            }
            alertDialog.show()
        }

        dialogBinding.btnOpenQuantitySelector.setOnClickListener {
            showQuantitySelector(dialogBinding.editProductQuantity, dialogBinding.unitDropdown) { amount, unit ->
                dialogBinding.btnOpenQuantitySelector.text = "$amount $unit"
            }
        }

        val unitAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, units)
        dialogBinding.unitDropdown.setAdapter(unitAdapter)
        if (!isEdit && barcodeProduct == null) dialogBinding.unitDropdown.setText(units[0], false)

        dialogBinding.btnSelectCategory.setOnClickListener {
            showCategoryPicker(selectedProductCategory) { selected ->
                selectedProductCategory = selected
                dialogBinding.btnSelectCategory.text = selected
            }
        }

        val dateSetListener = { dateField: com.google.android.material.textfield.TextInputEditText ->
            val calendar = Calendar.getInstance()
            DatePickerDialog(this, { _, year, month, day ->
                val selectedDate = Calendar.getInstance()
                selectedDate.set(year, month, day)
                val format = SimpleDateFormat("dd.MM.yy", Locale.getDefault())
                dateField.setText(format.format(selectedDate.time))
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        dialogBinding.editManufactureDate.setOnClickListener { dateSetListener(dialogBinding.editManufactureDate) }
        dialogBinding.editExpiryDate.setOnClickListener { dateSetListener(dialogBinding.editExpiryDate) }

        dialogBinding.btnSaveProduct.setOnClickListener {
            val name = dialogBinding.editProductName.text.toString()
            val qty = dialogBinding.editProductQuantity.text.toString()
            val unit = dialogBinding.unitDropdown.text.toString()
            val mDate = dialogBinding.editManufactureDate.text.toString()
            val eDate = dialogBinding.editExpiryDate.text.toString()

            val expiryDays = calculateDaysRemaining(eDate)

            if (name.isNotEmpty()) {
                if (isEdit) {
                    val index = products.indexOfFirst { it.id == productToEdit?.id }
                    if (index != -1) {
                        products[index] = productToEdit!!.copy(
                            name = name,
                            quantity = "$qty $unit",
                            category = selectedProductCategory,
                            icon = selectedIcon,
                            manufactureDate = mDate,
                            expiryDate = eDate,
                            expiryDays = expiryDays
                        )
                    }
                    ProductRepository.saveProducts(products)
                    syncUpdateProductToBackend(products[index])
                    currentProductsBinding?.let { applyFiltersAndSort(it) }
                    dialog.dismiss()
                } else {
                    val cal = Calendar.getInstance()
                    val dateFormat = SimpleDateFormat("dd.MM.yy", Locale.getDefault())
                    val addedDate = dateFormat.format(cal.time)
                    val newProduct = Product(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        quantity = "$qty $unit",
                        category = selectedProductCategory,
                        expiryDays = expiryDays,
                        icon = selectedIcon,
                        manufactureDate = mDate,
                        expiryDate = eDate,
                        addedDate = addedDate
                    )
                    products.add(newProduct)
                    ProductRepository.saveProducts(products)
                    logAction(ActionType.ADDED, newProduct)
                    syncAddProductToBackend(newProduct)
                    UserProductTemplates.saveTemplate(
                        ProductTemplate(name, selectedIcon, expiryDays, selectedProductCategory),
                        qty,
                        unit
                    )

                    if (barcode != null) {
                        val barcodeProductSave = BarcodeProduct(
                            barcode = barcode,
                            productName = name,
                            quantity = qty,
                            unit = unit,
                            category = selectedProductCategory,
                            icon = selectedIcon,
                            expiryDays = expiryDays
                        )
                        BarcodeRepository.saveBarcodeProduct(barcodeProductSave)
                    }

                    currentProductsBinding?.let { applyFiltersAndSort(it) }

                    selectedIcon = "📦"
                    selectedProductCategory = categories[0]
                    dialogBinding.selectedIcon.text = "📦"
                    dialogBinding.editProductName.setText("")
                    dialogBinding.editProductQuantity.setText("1")
                    dialogBinding.unitDropdown.setText("шт", false)
                    dialogBinding.btnOpenQuantitySelector.text = "1 шт"
                    dialogBinding.btnSelectCategory.text = categories[0]
                    dialogBinding.editManufactureDate.setText("")
                    dialogBinding.editExpiryDate.setText("")
                    dialogBinding.productDetailsScroll.visibility = View.GONE
                    dialogBinding.suggestionsRecyclerView.visibility = View.GONE

                    dialogBinding.editProductName.requestFocus()
                }
            }
        }

        dialogBinding.editProductName.post {
            dialogBinding.editProductName.requestFocus()
        }
    }

    private fun showQuantitySelector(
        targetEditText: android.widget.EditText,
        unitDropdown: android.widget.AutoCompleteTextView,
        onSelected: (String, String) -> Unit
    ) {
        val qtyBinding = DialogQuantitySelectorBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this).setView(qtyBinding.root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val values = listOf("0.1", "0.2", "0.25", "0.3", "0.4", "0.5", "1", "2", "5", "10", "100", "200", "500")
        val unitsList = listOf(getString(R.string.unit_gram), getString(R.string.unit_kilo), getString(R.string.unit_liter), getString(R.string.unit_ml), getString(R.string.unit_pcs))

        val currentAmount = targetEditText.text.toString()
        val currentUnit = unitDropdown.text.toString()

        qtyBinding.editAmount.setText(currentAmount)
        qtyBinding.txtUnit.text = if (currentUnit.isEmpty()) getString(R.string.unit_kilo) else currentUnit

        values.forEach { value ->
            val tv = TextView(this).apply {
                text = value
                textSize = 18f
                setPadding(0, 16, 0, 16)
                gravity = android.view.Gravity.CENTER
                setOnClickListener {
                    qtyBinding.editAmount.setText(value)
                }
            }
            qtyBinding.valuesColumn.addView(tv)
        }

        unitsList.forEach { unit ->
            val tv = TextView(this).apply {
                text = unit
                textSize = 18f
                setPadding(0, 16, 0, 16)
                gravity = android.view.Gravity.CENTER
                setOnClickListener {
                    qtyBinding.txtUnit.text = unit
                }
            }
            qtyBinding.unitsColumn.addView(tv)
        }

        qtyBinding.btnSave.setOnClickListener {
            val amount = qtyBinding.editAmount.text.toString()
            val unit = qtyBinding.txtUnit.text.toString()
            targetEditText.setText(amount)
            unitDropdown.setText(unit, false)
            onSelected(amount, unit)
            dialog.dismiss()
        }

        qtyBinding.btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showCategoryPicker(current: String, onSelected: (String) -> Unit) {
        val pickerBinding = DialogCategoryPickerBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this).setView(pickerBinding.root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val scroll = android.widget.ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }
        scroll.addView(container)

        pickerBinding.categoryRecyclerView.visibility = View.GONE
        (pickerBinding.root.getChildAt(0) as LinearLayout).addView(scroll, 2, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        fun updateList(query: String = "") {
            container.removeAllViews()
            val primaryColor = try {
                getColor(com.google.android.material.R.color.material_dynamic_primary50)
            } catch (e: Exception) {
                android.graphics.Color.parseColor("#6750A4")
            }
            categories.filter { it.contains(query, ignoreCase = true) }.forEach { category ->
                val item = TextView(this).apply {
                    text = category
                    textSize = 18f
                    setPadding(32, 48, 32, 48)
                    setTextColor(if (category == current) primaryColor else android.graphics.Color.BLACK)
                    setOnClickListener {
                        onSelected(category)
                        dialog.dismiss()
                    }
                }
                container.addView(item)
                container.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
                    setBackgroundColor(android.graphics.Color.parseColor("#EEEEEE"))
                })
            }
        }

        updateList()

        pickerBinding.editSearchCategory.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateList(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        pickerBinding.btnClosePicker.setOnClickListener { dialog.dismiss() }

        pickerBinding.btnAddNewCategory.setOnClickListener {
            val entryBinding = DialogAddCategoryBinding.inflate(LayoutInflater.from(this))
            AlertDialog.Builder(this)
                .setTitle(R.string.add_category)
                .setView(entryBinding.root)
                .setPositiveButton(R.string.save) { _, _ ->
                    val newCat = entryBinding.editCategoryName.text.toString()
                    if (newCat.isNotBlank() && !categories.contains(newCat)) {
                        categories.add(newCat)
                        onSelected(newCat)
                        currentProductsBinding?.let {
                            applyFiltersAndSort(it)
                        }
                        dialog.dismiss()
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        dialog.show()
    }

    private fun showHistory(scrollPosition: Int = 0) {
        val historyBinding = ScreenHistoryBinding.inflate(layoutInflater, binding.fragmentContainer, false)
        binding.fragmentContainer.removeAllViews()
        binding.fragmentContainer.addView(historyBinding.root)

        val history = ActionHistoryRepository.loadHistory()
        historyBinding.emptyHistoryText.visibility = if (history.isEmpty()) View.VISIBLE else View.GONE
        historyBinding.historyRecyclerView.visibility = if (history.isEmpty()) View.GONE else View.VISIBLE

        val adapter = HistoryAdapter(history) { entry ->
            if (entry.type == ActionType.DELETED) {
                restoreDeletedProduct(entry)
            }
        }
        historyBinding.historyRecyclerView.adapter = adapter
        historyBinding.historyRecyclerView.layoutManager = LinearLayoutManager(this)

        historyBinding.historyRecyclerView.post {
            historyBinding.historyRecyclerView.scrollToPosition(scrollPosition)
        }

        historyBinding.toolbarHistory.setNavigationOnClickListener {
            binding.bottomNavigation.selectedItemId = R.id.nav_stocks
        }
    }

    private fun restoreDeletedProduct(entry: ActionHistoryEntry) {
        try {
            android.util.Log.d("RestoreProduct", "productJson: ${entry.productJson}")
            val parts = entry.productJson.split("::")
            android.util.Log.d("RestoreProduct", "parts count: ${parts.size}, parts: $parts")
            if (parts.size < 3) {
                Toast.makeText(this, "Некорректные данные для восстановления", Toast.LENGTH_SHORT).show()
                return
            }
            val originalId = parts[0]
            val alreadyExists = products.any { it.id == originalId }
            if (alreadyExists) {
                Toast.makeText(this, "Продукт уже восстановлен", Toast.LENGTH_SHORT).show()
                ActionHistoryRepository.removeEntry(entry.id)
                showHistory()
                return
            }
            val expiryDate = if (parts.size > 8) parts[8] else ""
            val expiryDays = if (expiryDate.isNotEmpty()) {
                com.example.smartfrostapp.utils.calculateDaysRemaining(expiryDate)
            } else {
                parts.getOrNull(4)?.toIntOrNull() ?: 0
            }
            val restoredProduct = Product(
                id = originalId,
                name = parts.getOrNull(1) ?: "Восстановленный",
                quantity = parts.getOrNull(2) ?: "1 шт",
                category = parts.getOrNull(3) ?: "Прочее",
                expiryDays = expiryDays,
                icon = parts.getOrNull(5) ?: "📦",
                isLocked = parts.getOrNull(6)?.toBoolean() ?: false,
                manufactureDate = if (parts.size > 7) parts[7] else "",
                expiryDate = expiryDate,
                addedDate = if (parts.size > 9) parts[9] else "",
                backendId = if (parts.size > 10) parts[10].toIntOrNull() ?: 0 else 0
            )
            products.add(restoredProduct)
            ProductRepository.saveProducts(products)
            logAction(ActionType.ADDED, restoredProduct)
            syncAddProductToBackend(restoredProduct)
            ActionHistoryRepository.removeEntry(entry.id)
            showHistory()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Ошибка восстановления: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun logAction(type: ActionType, product: Product) {
        val productJson = listOf(
            product.id,
            product.name,
            product.quantity,
            product.category,
            product.expiryDays.toString(),
            product.icon,
            product.isLocked.toString(),
            product.manufactureDate,
            product.expiryDate,
            product.addedDate,
            product.backendId.toString()
        ).joinToString("::")
        ActionHistoryRepository.addEntry(ActionHistoryEntry(
            id = UUID.randomUUID().toString(),
            type = type,
            productName = product.name,
            productIcon = product.icon,
            productJson = productJson,
            timestamp = System.currentTimeMillis()
        ))
    }

    private fun showSettings() {
        val settingsBinding = ScreenSettingsBinding.inflate(layoutInflater, binding.fragmentContainer, false)
        binding.fragmentContainer.removeAllViews()
        binding.fragmentContainer.addView(settingsBinding.root)

        loadUserInfo(settingsBinding)

        val currentTheme = prefs.getString("theme", "system") ?: "system"
        settingsBinding.currentThemeText.text = when (currentTheme) {
            "light" -> "Светлая"
            "dark" -> "Тёмная"
            else -> "Системная"
        }

        settingsBinding.themeSelector.setOnClickListener {
            showThemePicker(settingsBinding.currentThemeText)
        }

        settingsBinding.historyItem.setOnClickListener {
            showHistory()
        }

        settingsBinding.btnLogout.setOnClickListener {
            prefs.edit().apply {
                putBoolean("is_logged_in", false)
                putInt("user_id", 0)
                putString("user_token", "")
                remove("user_name")
                remove("user_email")
                apply()
            }

            isLoggedIn = false
            userId = 0
            userToken = ""
            products.clear()

            showLogin()
        }
    }

    private fun loadUserInfo(settingsBinding: ScreenSettingsBinding) {
        val cachedName = prefs.getString("user_name", "")
        val cachedEmail = prefs.getString("user_email", "")

        if (!cachedName.isNullOrEmpty()) {
            settingsBinding.userNameText.text = cachedName
        }
        if (!cachedEmail.isNullOrEmpty()) {
            settingsBinding.userEmailText.text = cachedEmail
        }

        if (userToken.isEmpty()) {
            if (cachedName.isNullOrEmpty()) {
                settingsBinding.userNameText.text = "Пользователь"
            }
            if (cachedEmail.isNullOrEmpty()) {
                settingsBinding.userEmailText.text = ""
            }
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val result = com.example.smartfrostapp.network.ApiClient.getUser(userToken)
            runOnUiThread {
                result.fold(
                    onSuccess = { userInfo ->
                        settingsBinding.userNameText.text = userInfo.name
                        settingsBinding.userEmailText.text = userInfo.email
                        prefs.edit().apply {
                            putString("user_name", userInfo.name)
                            putString("user_email", userInfo.email)
                            apply()
                        }
                    },
                    onFailure = { error ->
                        if (cachedName.isNullOrEmpty()) {
                            settingsBinding.userNameText.text = "Пользователь"
                        }
                        if (cachedEmail.isNullOrEmpty()) {
                            settingsBinding.userEmailText.text = ""
                        }
                        android.util.Log.e("UserInfo", "Failed to load: ${error.message}")
                    }
                )
            }
        }
    }

    private fun applyTheme() {
        val theme = prefs.getString("theme", "system") ?: "system"
        when (theme) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    private fun showThemePicker(currentThemeText: TextView) {
        val themes = arrayOf("Системная", "Светлая", "Тёмная")
        val themeValues = arrayOf("system", "light", "dark")
        val currentTheme = prefs.getString("theme", "system") ?: "system"
        val currentIndex = themeValues.indexOf(currentTheme)

        AlertDialog.Builder(this)
            .setTitle("Выберите тему")
            .setSingleChoiceItems(themes, currentIndex) { dialog, which ->
                prefs.edit().putString("theme", themeValues[which]).apply()
                currentThemeText.text = themes[which]
                applyTheme()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Разрешение камеры необходимо для сканера", Toast.LENGTH_SHORT).show()
            binding.bottomNavigation.selectedItemId = R.id.nav_stocks
        }
    }

    private fun showScanner() {
        val scannerBinding = ScreenScannerBinding.inflate(layoutInflater, binding.fragmentContainer, false)
        binding.fragmentContainer.removeAllViews()
        binding.fragmentContainer.addView(scannerBinding.root)

        scannerBinding.scannerToolbar.setNavigationOnClickListener {
            isScanningActive = false
            binding.bottomNavigation.selectedItemId = R.id.nav_stocks
        }

        scannerBinding.fabManualEntry.setOnClickListener {
            showManualBarcodeDialog()
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera(scannerBinding)
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera(scannerBinding: ScreenScannerBinding? = null) {
        val bindingToUse = scannerBinding ?: run {
            val scannerBinding2 = ScreenScannerBinding.inflate(layoutInflater, binding.fragmentContainer, false)
            binding.fragmentContainer.removeAllViews()
            binding.fragmentContainer.addView(scannerBinding2.root)

            scannerBinding2.scannerToolbar.setNavigationOnClickListener {
                isScanningActive = false
                binding.bottomNavigation.selectedItemId = R.id.nav_stocks
            }

            scannerBinding2.fabManualEntry.setOnClickListener {
                showManualBarcodeDialog()
            }
            scannerBinding2
        }

        val previewView = bindingToUse.scannerPreview
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(android.util.Size(640, 480))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
            processImageProxy(imageProxy, bindingToUse)
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
                isScanningActive = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy, scannerBinding: ScreenScannerBinding) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastScanTime < SCAN_INTERVAL_MS) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            lastScanTime = currentTime
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            barcodeScanner?.process(image)
                ?.addOnSuccessListener { barcodes ->
                    if (isScanningActive && barcodes.isNotEmpty()) {
                        val barcode = barcodes.first()
                        val rawValue = barcode.rawValue
                        if (!rawValue.isNullOrEmpty()) {
                            isScanningActive = false
                            runOnUiThread {
                                // 👇 НАПРЯМУЮ проверяем, какая кнопка нажата на экране в данный момент!
                                val isReceiptMode = scannerBinding.toggleScanMode.checkedButtonId == R.id.btn_scan_receipt

                                if (isReceiptMode) {
                                    handleScannedReceiptQr(rawValue, scannerBinding)
                                } else {
                                    handleScannedBarcode(rawValue, scannerBinding)
                                }
                            }
                        }
                    }
                }
                ?.addOnFailureListener { e ->
                    e.printStackTrace()
                }
                ?.addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    private fun handleScannedReceiptQr(qrRaw: String, scannerBinding: ScreenScannerBinding) {
        Toast.makeText(this, "Отправляем чек на бэкенд...", Toast.LENGTH_SHORT).show()
        sendQrToBackend(qrRaw)
    }

    private fun sendQrToBackend(qrRaw: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = com.example.smartfrostapp.network.ApiClient.scanReceiptQr(userToken, userId, qrRaw)
            runOnUiThread {
                result.fold(
                    onSuccess = { backendItems ->
                        val cal = Calendar.getInstance()
                        val dateFormat = SimpleDateFormat("dd.MM.yy", Locale.getDefault())
                        val addedDate = dateFormat.format(cal.time)
                        val manufactureDate = addedDate
                        cal.add(Calendar.DAY_OF_YEAR, 7)
                        val expiryDate = dateFormat.format(cal.time)

                        val newProducts = mutableListOf<Product>()
                        for (item in backendItems) {
                            val qtyString = if (item.quantity.toDoubleOrNull()?.let { it % 1.0 == 0.0 } == true) {
                                item.quantity.toDoubleOrNull()?.toInt().toString()
                            } else {
                                item.quantity
                            }
                            val fullQuantity = "$qtyString ${item.unit}"
                            newProducts.add(
                                Product(
                                    id = UUID.randomUUID().toString(),
                                    name = item.name,
                                    quantity = fullQuantity,
                                    category = item.category ?: "Прочее",
                                    expiryDays = 7,
                                    icon = Constants.suggestIcon(item.name),
                                    manufactureDate = manufactureDate,
                                    expiryDate = expiryDate,
                                    addedDate = addedDate
                                )
                            )
                        }

                        products.addAll(newProducts)
                        ProductRepository.saveProducts(products)
                        newProducts.forEach { logAction(ActionType.ADDED, it) }

                        val backendItemsForSync = newProducts.map { np ->
                            val qtyParts = np.quantity.split(" ")
                            val qtyDouble = qtyParts[0].toDoubleOrNull() ?: 1.0
                            val unit = if (qtyParts.size > 1) qtyParts[1] else "шт"
                            val expirationStr = if (np.expiryDate.isNotEmpty()) {
                                val parts = np.expiryDate.split(".")
                                if (parts.size == 3) "20${parts[2]}-${parts[1]}-${parts[0]}" else null
                            } else null
                            com.example.smartfrostapp.network.ApiClient.BackendProductItem(
                                name = np.name,
                                quantity = qtyDouble.toString(),
                                unit = unit,
                                category = np.category,
                                expiration = expirationStr
                            )
                        }

                        lifecycleScope.launch(Dispatchers.IO) {
                            com.example.smartfrostapp.network.ApiClient.addItems(userToken, backendItemsForSync)
                        }

                        Toast.makeText(this@MainActivity, "Распознано продуктов: ${newProducts.size}", Toast.LENGTH_LONG).show()
                        binding.bottomNavigation.selectedItemId = R.id.nav_stocks
                        isScanningActive = true
                    },
                    onFailure = { error ->
                        Toast.makeText(this@MainActivity, "Ошибка: ${error.message}", Toast.LENGTH_LONG).show()
                        isScanningActive = true
                    }
                )
            }
        }
    }

    private fun handleScannedBarcode(barcode: String, scannerBinding: ScreenScannerBinding) {
        val existingProduct = BarcodeRepository.getBarcodeProduct(barcode)
        if (existingProduct != null) {
            isScanningActive = false
            runOnUiThread {
                binding.bottomNavigation.selectedItemId = R.id.nav_stocks
                showProductDialogFromBarcode(existingProduct)
            }
        } else {
            isScanningActive = false
            runOnUiThread {
                showProductDialogForNewBarcode(barcode)
            }
        }
    }

    private fun showManualBarcodeDialog() {
        val input = EditText(this).apply {
            hint = "Введите штрихкод"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }

        AlertDialog.Builder(this)
            .setTitle("Ручной ввод штрихкода")
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val barcode = input.text.toString().trim()
                if (barcode.isNotEmpty()) {
                    val existingProduct = BarcodeRepository.getBarcodeProduct(barcode)
                    if (existingProduct != null) {
                        showProductDialogFromBarcode(existingProduct)
                    } else {
                        showProductDialogForNewBarcode(barcode)
                    }
                } else {
                    Toast.makeText(this, "Введите номер штрихкода", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showProductDialogFromBarcode(barcodeProduct: BarcodeProduct) {
        val dialogBinding = DialogAddProductNewBinding.inflate(LayoutInflater.from(this))
        var selectedIcon = barcodeProduct.icon
        var selectedProductCategory = barcodeProduct.category

        var suggestionAdapter: SuggestionAdapter? = null
        dialogBinding.suggestionsRecyclerView.layoutManager = LinearLayoutManager(this)
        suggestionAdapter = SuggestionAdapter(emptyList(),
            onItemClick = { template ->
                selectedIcon = template.icon
                selectedProductCategory = template.category
                dialogBinding.selectedIcon.text = selectedIcon
                dialogBinding.editProductName.setText(template.name)
                dialogBinding.btnSelectCategory.text = template.category
                dialogBinding.unitDropdown.setText(template.defaultUnit, false)
                dialogBinding.btnOpenQuantitySelector.text = "${template.defaultQuantity} ${template.defaultUnit}"
                dialogBinding.editProductQuantity.setText(template.defaultQuantity)
                dialogBinding.productDetailsScroll.visibility = View.VISIBLE
                dialogBinding.suggestionsRecyclerView.visibility = View.GONE
            },
            onDeleteClick = { template ->
                UserProductTemplates.deleteTemplate(template)
                val currentSuggestions = UserProductTemplates.getAllTemplates().filter { t ->
                    t != template && t.name.contains(dialogBinding.editProductName.text.toString(), ignoreCase = true)
                }.take(20)
                suggestionAdapter?.updateSuggestions(currentSuggestions)
            }
        )
        dialogBinding.suggestionsRecyclerView.adapter = suggestionAdapter

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setCancelable(true)
            .create()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dialog.show()

        dialogBinding.btnClose.setOnClickListener { dialog.dismiss() }

        dialogBinding.editProductName.setText(barcodeProduct.productName)
        dialogBinding.selectedIcon.text = selectedIcon
        dialogBinding.editProductQuantity.setText(barcodeProduct.quantity)
        dialogBinding.unitDropdown.setText(barcodeProduct.unit, false)
        dialogBinding.btnOpenQuantitySelector.text = "${barcodeProduct.quantity} ${barcodeProduct.unit}"
        dialogBinding.btnSelectCategory.text = selectedProductCategory

        val cal = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("dd.MM.yy", Locale.getDefault())
        dialogBinding.editManufactureDate.setText(dateFormat.format(cal.time))
        cal.add(Calendar.DAY_OF_YEAR, barcodeProduct.expiryDays)
        dialogBinding.editExpiryDate.setText(dateFormat.format(cal.time))

        dialogBinding.productDetailsScroll.visibility = View.VISIBLE
        dialogBinding.suggestionsRecyclerView.visibility = View.GONE

        dialogBinding.editProductName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                if (query.isNotEmpty()) {
                    dialogBinding.productDetailsScroll.visibility = View.VISIBLE
                } else {
                    dialogBinding.productDetailsScroll.visibility = View.GONE
                }

                if (query.length >= 2) {
                    val filtered = UserProductTemplates.getAllTemplates().filter {
                        it.name.equals(query, ignoreCase = true) || it.name.contains(query, ignoreCase = true)
                    }.take(20)

                    if (filtered.isNotEmpty()) {
                        suggestionAdapter.updateSuggestions(filtered)
                        dialogBinding.suggestionsRecyclerView.visibility = View.VISIBLE
                    } else {
                        dialogBinding.suggestionsRecyclerView.visibility = View.GONE
                    }
                } else {
                    dialogBinding.suggestionsRecyclerView.visibility = View.GONE
                }

                val suggestedIcon = Constants.suggestIcon(query)
                if (suggestedIcon != "📦") {
                    selectedIcon = suggestedIcon
                    dialogBinding.selectedIcon.text = selectedIcon
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        dialogBinding.iconPickerButton.setOnClickListener {
            val pickerBinding = DialogIconPickerBinding.inflate(LayoutInflater.from(this))
            val iconDialog = AlertDialog.Builder(this).setView(pickerBinding.root).create()
            pickerBinding.iconRecyclerView.adapter = IconAdapter(Constants.ALL_ICONS) { icon ->
                selectedIcon = icon
                dialogBinding.selectedIcon.text = selectedIcon
                iconDialog.dismiss()
            }
            iconDialog.show()
        }

        dialogBinding.btnOpenQuantitySelector.setOnClickListener {
            showQuantitySelector(dialogBinding.editProductQuantity, dialogBinding.unitDropdown) { amount, unit ->
                dialogBinding.btnOpenQuantitySelector.text = "$amount $unit"
            }
        }

        val unitAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, units)
        dialogBinding.unitDropdown.setAdapter(unitAdapter)

        dialogBinding.btnSelectCategory.setOnClickListener {
            showCategoryPicker(selectedProductCategory) { selected ->
                selectedProductCategory = selected
                dialogBinding.btnSelectCategory.text = selected
            }
        }

        val dateSetListener = { dateField: TextInputEditText ->
            val calendar = Calendar.getInstance()
            DatePickerDialog(this, { _, year, month, day ->
                val selectedDate = Calendar.getInstance()
                selectedDate.set(year, month, day)
                val format = SimpleDateFormat("dd.MM.yy", Locale.getDefault())
                dateField.setText(format.format(selectedDate.time))
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        dialogBinding.editManufactureDate.setOnClickListener { dateSetListener(dialogBinding.editManufactureDate) }
        dialogBinding.editExpiryDate.setOnClickListener { dateSetListener(dialogBinding.editExpiryDate) }

        dialogBinding.btnSaveProduct.setOnClickListener {
            val name = dialogBinding.editProductName.text.toString()
            val qty = dialogBinding.editProductQuantity.text.toString()
            val unit = dialogBinding.unitDropdown.text.toString()
            val mDate = dialogBinding.editManufactureDate.text.toString()
            val eDate = dialogBinding.editExpiryDate.text.toString()
            val expiryDays = calculateDaysRemaining(eDate)

            if (name.isNotEmpty()) {
                val addedDate = dateFormat.format(Calendar.getInstance().time)
                val newProduct = Product(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    quantity = "$qty $unit",
                    category = selectedProductCategory,
                    expiryDays = expiryDays,
                    icon = selectedIcon,
                    manufactureDate = mDate,
                    expiryDate = eDate,
                    addedDate = addedDate
                )
                products.add(newProduct)
                ProductRepository.saveProducts(products)
                logAction(ActionType.ADDED, newProduct)

                val updatedBarcodeProduct = BarcodeProduct(
                    barcode = barcodeProduct.barcode,
                    productName = name,
                    quantity = qty,
                    unit = unit,
                    category = selectedProductCategory,
                    icon = selectedIcon,
                    expiryDays = barcodeProduct.expiryDays
                )
                BarcodeRepository.saveBarcodeProduct(updatedBarcodeProduct)

                UserProductTemplates.saveTemplate(
                    ProductTemplate(name, selectedIcon, expiryDays, selectedProductCategory),
                    qty,
                    unit
                )

                currentProductsBinding?.let { applyFiltersAndSort(it) }
                dialog.dismiss()
                binding.bottomNavigation.selectedItemId = R.id.nav_stocks
            }
        }

        dialogBinding.editProductName.post {
            dialogBinding.editProductName.requestFocus()
        }
    }

    private fun showProductDialogForNewBarcode(barcode: String) {
        val dialogBinding = DialogAddProductNewBinding.inflate(LayoutInflater.from(this))
        var selectedIcon = "📦"
        var selectedProductCategory = categories[0]
        var suggestionAdapter: SuggestionAdapter? = null

        fun applyTemplateToUI(template: ProductTemplate) {
            selectedIcon = template.icon
            selectedProductCategory = template.category
            dialogBinding.selectedIcon.text = selectedIcon
            dialogBinding.editProductName.setText(template.name)
            dialogBinding.btnSelectCategory.text = template.category
            dialogBinding.unitDropdown.setText(template.defaultUnit, false)
            dialogBinding.btnOpenQuantitySelector.text = "${template.defaultQuantity} ${template.defaultUnit}"
            dialogBinding.editProductQuantity.setText(template.defaultQuantity)
            val cal = Calendar.getInstance()
            val dateFormat = SimpleDateFormat("dd.MM.yy", Locale.getDefault())
            dialogBinding.editManufactureDate.setText(dateFormat.format(cal.time))
            cal.add(Calendar.DAY_OF_YEAR, template.defaultShelfLifeDays)
            dialogBinding.editExpiryDate.setText(dateFormat.format(cal.time))
            dialogBinding.productDetailsScroll.visibility = View.VISIBLE
            dialogBinding.suggestionsRecyclerView.visibility = View.GONE
        }

        dialogBinding.suggestionsRecyclerView.layoutManager = LinearLayoutManager(this)
        suggestionAdapter = SuggestionAdapter(emptyList(),
            onItemClick = { template ->
                applyTemplateToUI(template)
            },
            onDeleteClick = { template ->
                UserProductTemplates.deleteTemplate(template)
                val currentSuggestions = UserProductTemplates.getAllTemplates().filter { t ->
                    t != template && t.name.contains(dialogBinding.editProductName.text.toString(), ignoreCase = true)
                }.take(20)
                suggestionAdapter?.updateSuggestions(currentSuggestions)
            }
        )
        dialogBinding.suggestionsRecyclerView.adapter = suggestionAdapter

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setCancelable(true)
            .create()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dialog.show()

        dialogBinding.btnClose.setOnClickListener { dialog.dismiss() }

        dialogBinding.selectedIcon.text = selectedIcon
        dialogBinding.editProductQuantity.setText("1")
        dialogBinding.unitDropdown.setText(units[0], false)
        dialogBinding.btnOpenQuantitySelector.text = "1 ${units[0]}"
        dialogBinding.btnSelectCategory.text = selectedProductCategory

        val cal = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("dd.MM.yy", Locale.getDefault())
        dialogBinding.editManufactureDate.setText(dateFormat.format(cal.time))
        cal.add(Calendar.DAY_OF_YEAR, 7)
        dialogBinding.editExpiryDate.setText(dateFormat.format(cal.time))

        dialogBinding.productDetailsScroll.visibility = View.VISIBLE
        dialogBinding.suggestionsRecyclerView.visibility = View.GONE

        dialogBinding.editProductName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                if (query.isNotEmpty()) {
                    dialogBinding.productDetailsScroll.visibility = View.VISIBLE
                } else {
                    dialogBinding.productDetailsScroll.visibility = View.GONE
                }

                var templateMatched = false
                if (query.length >= 2) {
                    val filtered = UserProductTemplates.getAllTemplates().filter {
                        it.name.equals(query, ignoreCase = true) || it.name.contains(query, ignoreCase = true)
                    }.take(20)

                    if (filtered.isNotEmpty()) {
                        suggestionAdapter?.updateSuggestions(filtered)
                        dialogBinding.suggestionsRecyclerView.visibility = View.VISIBLE

                        val exactMatch = filtered.firstOrNull { it.name.equals(query, ignoreCase = true) }
                        val firstMatch = exactMatch ?: filtered.firstOrNull()
                        if (firstMatch != null) {
                            selectedIcon = firstMatch.icon
                            selectedProductCategory = firstMatch.category
                            dialogBinding.selectedIcon.text = selectedIcon
                            dialogBinding.btnSelectCategory.text = firstMatch.category
                            dialogBinding.unitDropdown.setText(firstMatch.defaultUnit, false)
                            dialogBinding.btnOpenQuantitySelector.text = "${firstMatch.defaultQuantity} ${firstMatch.defaultUnit}"
                            dialogBinding.editProductQuantity.setText(firstMatch.defaultQuantity)
                            templateMatched = true
                        }
                    } else {
                        dialogBinding.suggestionsRecyclerView.visibility = View.GONE
                    }
                } else {
                    dialogBinding.suggestionsRecyclerView.visibility = View.GONE
                }

                val suggestedIcon = Constants.suggestIcon(query)
                if (suggestedIcon != "📦" && !templateMatched) {
                    selectedIcon = suggestedIcon
                    dialogBinding.selectedIcon.text = selectedIcon
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        dialogBinding.iconPickerButton.setOnClickListener {
            val pickerBinding = DialogIconPickerBinding.inflate(LayoutInflater.from(this))
            val iconDialog = AlertDialog.Builder(this).setView(pickerBinding.root).create()
            pickerBinding.iconRecyclerView.adapter = IconAdapter(Constants.ALL_ICONS) { icon ->
                selectedIcon = icon
                dialogBinding.selectedIcon.text = selectedIcon
                iconDialog.dismiss()
            }
            iconDialog.show()
        }

        dialogBinding.btnOpenQuantitySelector.setOnClickListener {
            showQuantitySelector(dialogBinding.editProductQuantity, dialogBinding.unitDropdown) { amount, unit ->
                dialogBinding.btnOpenQuantitySelector.text = "$amount $unit"
            }
        }

        val unitAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, units)
        dialogBinding.unitDropdown.setAdapter(unitAdapter)

        dialogBinding.btnSelectCategory.setOnClickListener {
            showCategoryPicker(selectedProductCategory) { selected ->
                selectedProductCategory = selected
                dialogBinding.btnSelectCategory.text = selected
            }
        }

        val dateSetListener = { dateField: TextInputEditText ->
            val calendar = Calendar.getInstance()
            DatePickerDialog(this, { _, year, month, day ->
                val selectedDate = Calendar.getInstance()
                selectedDate.set(year, month, day)
                val format = SimpleDateFormat("dd.MM.yy", Locale.getDefault())
                dateField.setText(format.format(selectedDate.time))
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        dialogBinding.editManufactureDate.setOnClickListener { dateSetListener(dialogBinding.editManufactureDate) }
        dialogBinding.editExpiryDate.setOnClickListener { dateSetListener(dialogBinding.editExpiryDate) }

        dialogBinding.btnSaveProduct.setOnClickListener {
            val name = dialogBinding.editProductName.text.toString()
            val qty = dialogBinding.editProductQuantity.text.toString()
            val unit = dialogBinding.unitDropdown.text.toString()
            val mDate = dialogBinding.editManufactureDate.text.toString()
            val eDate = dialogBinding.editExpiryDate.text.toString()
            val expiryDays = calculateDaysRemaining(eDate)

            if (name.isNotEmpty()) {
                val addedDate = dateFormat.format(Calendar.getInstance().time)
                val newProduct = Product(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    quantity = "$qty $unit",
                    category = selectedProductCategory,
                    expiryDays = expiryDays,
                    icon = selectedIcon,
                    manufactureDate = mDate,
                    expiryDate = eDate,
                    addedDate = addedDate
                )
                products.add(newProduct)
                ProductRepository.saveProducts(products)
                logAction(ActionType.ADDED, newProduct)

                val barcodeProduct = BarcodeProduct(
                    barcode = barcode,
                    productName = name,
                    quantity = qty,
                    unit = unit,
                    category = selectedProductCategory,
                    icon = selectedIcon,
                    expiryDays = expiryDays
                )
                BarcodeRepository.saveBarcodeProduct(barcodeProduct)

                UserProductTemplates.saveTemplate(
                    ProductTemplate(name, selectedIcon, expiryDays, selectedProductCategory),
                    qty,
                    unit
                )

                currentProductsBinding?.let { applyFiltersAndSort(it) }
                dialog.dismiss()
                binding.bottomNavigation.selectedItemId = R.id.nav_stocks

                selectedIcon = "📦"
                selectedProductCategory = categories[0]
            }
        }

        dialogBinding.editProductName.post {
            dialogBinding.editProductName.requestFocus()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isScanningActive = false
        if (::cameraExecutor.isInitialized) {
            cameraExecutor.shutdown()
        }
    }

    private fun loadProductsFromBackend() {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = com.example.smartfrostapp.network.ApiClient.getItems(userToken)
            runOnUiThread {
                result.fold(
                    onSuccess = { backendProducts ->
                        products.clear()
                        if (backendProducts.isNotEmpty()) {
                            val dateFormat = java.text.SimpleDateFormat("dd.MM.yy", Locale.getDefault())
                            for (bp in backendProducts) {
                                if (bp.deleted) continue
                                val expiryDays = if (bp.expiration != null) {
                                    com.example.smartfrostapp.utils.calculateDaysRemaining(bp.expiration.replace("-", ".").let {
                                        val parts = it.split(".")
                                        if (parts.size == 3) "${parts[2].takeLast(2)}.${parts[1]}.${parts[0].takeLast(2)}" else ""
                                    })
                                } else 7
                                val quantityStr = if (bp.quantity % 1.0 == 0.0) bp.quantity.toInt().toString() else bp.quantity.toString()
                                products.add(
                                    Product(
                                        id = UUID.randomUUID().toString(),
                                        name = bp.name,
                                        quantity = "$quantityStr ${bp.unit}",
                                        category = bp.category ?: "Прочее",
                                        expiryDays = expiryDays,
                                        icon = com.example.smartfrostapp.utils.Constants.suggestIcon(bp.name),
                                        manufactureDate = bp.expiration?.let {
                                            dateFormat.format(java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(it) ?: java.util.Date())
                                        } ?: "",
                                        expiryDate = bp.expiration?.replace("-", ".")?.let {
                                            val parts = it.split(".")
                                            if (parts.size == 3) "${parts[2].takeLast(2)}.${parts[1]}.${parts[0].takeLast(2)}" else ""
                                        } ?: "",
                                        addedDate = dateFormat.format(java.util.Date()),
                                        backendId = bp.id
                                    )
                                )
                            }
                            ProductRepository.saveProducts(products)
                        } else {
                            products.addAll(listOf(
                                Product("1", "Молоко 3.2%", "1 л", "Молочное", 3, "", manufactureDate = "20.10.23", expiryDate = "27.10.23", addedDate = "20.10.23"),
                                Product("2", "Куриная грудка", "500 г", "Мясо", 2, "", manufactureDate = "22.10.23", expiryDate = "24.10.23", addedDate = "22.10.23"),
                                Product("3", "Яблоки", "1.5 кг", "Фрукты", 14, "🍎", manufactureDate = "15.10.23", expiryDate = "29.10.23", addedDate = "15.10.23")
                            ))
                            ProductRepository.saveProducts(products)
                        }
                        showMain()
                    },
                    onFailure = { error ->
                        Toast.makeText(this@MainActivity, "Ошибка загрузки продуктов: ${error.message}", Toast.LENGTH_SHORT).show()
                        showMain()
                    }
                )
            }
        }
    }

    private fun syncAddProductToBackend(product: Product) {
        if (userToken.isEmpty()) return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val expirationStr = if (product.expiryDate.isNotEmpty()) {
                    val parts = product.expiryDate.split(".")
                    if (parts.size == 3) "20${parts[2]}-${parts[1]}-${parts[0]}" else null
                } else null

                val qtyParts = product.quantity.split(" ")
                val qtyDouble = qtyParts[0].toDoubleOrNull() ?: 1.0
                val unit = if (qtyParts.size > 1) qtyParts[1] else "шт"

                val backendItem = com.example.smartfrostapp.network.ApiClient.BackendProductItem(
                    name = product.name,
                    quantity = qtyDouble.toString(),
                    unit = unit,
                    category = product.category,
                    expiration = expirationStr
                )

                val result = com.example.smartfrostapp.network.ApiClient.addItems(userToken, listOf(backendItem))
                result.fold(
                    onSuccess = {
                        val backendProducts = com.example.smartfrostapp.network.ApiClient.getItems(userToken).getOrNull()
                        backendProducts?.let { bpList ->
                            val latestProduct = bpList.maxByOrNull { it.id }
                            latestProduct?.let { lp ->
                                val index = products.indexOfFirst { it.id == product.id }
                                if (index != -1) {
                                    products[index] = product.copy(backendId = lp.id)
                                    ProductRepository.saveProducts(products)
                                }
                            }
                        }
                    },
                    onFailure = { error ->
                        android.util.Log.e("SyncAdd", "Failed: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("SyncAdd", "Error: ${e.message}")
            }
        }
    }

    private fun syncUpdateProductToBackend(product: Product) {
        if (userToken.isEmpty() || product.backendId == 0) return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val expirationStr = if (product.expiryDate.isNotEmpty()) {
                    val parts = product.expiryDate.split(".")
                    if (parts.size == 3) "20${parts[2]}-${parts[1]}-${parts[0]}" else null
                } else null

                val qtyParts = product.quantity.split(" ")
                val qtyDouble = qtyParts[0].toDoubleOrNull() ?: 1.0
                val unit = if (qtyParts.size > 1) qtyParts[1] else "шт"

                val changes = mutableMapOf<String, Any>()
                changes["name"] = product.name
                changes["quantity"] = qtyDouble
                changes["unit"] = unit
                changes["category"] = product.category
                if (expirationStr != null) changes["expiration"] = expirationStr

                val result = com.example.smartfrostapp.network.ApiClient.updateItems(userToken, listOf(Pair(product.backendId, changes)))
                result.fold(
                    onSuccess = { android.util.Log.d("SyncUpdate", "Updated: $it") },
                    onFailure = { error -> android.util.Log.e("SyncUpdate", "Failed: ${error.message}") }
                )
            } catch (e: Exception) {
                android.util.Log.e("SyncUpdate", "Error: ${e.message}")
            }
        }
    }

    private fun syncDeleteProductToBackend(backendId: Int) {
        if (userToken.isEmpty() || backendId == 0) return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = com.example.smartfrostapp.network.ApiClient.deleteItems(userToken, listOf(backendId))
                result.fold(
                    onSuccess = { android.util.Log.d("SyncDelete", "Deleted: $it") },
                    onFailure = { error -> android.util.Log.e("SyncDelete", "Failed: ${error.message}") }
                )
            } catch (e: Exception) {
                android.util.Log.e("SyncDelete", "Error: ${e.message}")
            }
        }
    }

    private fun performBackendLogin(
        emailValue: String,
        passwordValue: String,
        onResult: (Int?, String?, String) -> Unit
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = com.example.smartfrostapp.network.ApiClient.login(emailValue, passwordValue)
            runOnUiThread {
                result.fold(
                    onSuccess = { authResponse ->
                        onResult(authResponse.userId, authResponse.token, "success")
                    },
                    onFailure = { error ->
                        onResult(null, null, error.message ?: "Ошибка сети")
                    }
                )
            }
        }
    }

    private fun loadUserInfoFromBackend(token: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = com.example.smartfrostapp.network.ApiClient.getUser(token)
            runOnUiThread {
                result.fold(
                    onSuccess = { userInfo ->
                        prefs.edit().apply {
                            putString("user_name", userInfo.name)
                            putString("user_email", userInfo.email)
                            apply()
                        }
                    },
                    onFailure = { error ->
                        android.util.Log.e("LoadUserInfo", "Failed: ${error.message}")
                    }
                )
            }
        }
    }

    // Запоминаем открытую вкладку перед тем, как экран перевернется
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("SAVED_TAB", binding.bottomNavigation.selectedItemId)
    }

} // Конец класса MainActivity

