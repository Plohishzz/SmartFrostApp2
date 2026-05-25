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

const val BASE_URL = "http://10.178.45.117:8010/"

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var isLoggedIn = false
    private lateinit var prefs: SharedPreferences

    enum class ScanMode {
        PRODUCT, // Сканирование штрихкода товара
        RECEIPT  // Сканирование QR-кода чека
    }

    private var currentScanMode = ScanMode.PRODUCT // Режим по умолчанию

    private val products = mutableListOf<Product>()
    private var userId: Int = 0
    private var userToken: String = ""
    private var currentProductsBinding: ScreenProductsBinding? = null
    private val categories = mutableListOf("Молочное", "Мясо", "Рыба", "Овощи", "Фрукты", "Бакалея", "Напитки", "Замороженное", "Соусы и приправы", "Прочее")
    private val units = listOf("кг", "л", "шт", "г", "уп")

    private var searchQuery: String = ""
    private var selectedSort: String = "default"
    private val selectedCategoryFilters = mutableSetOf<String>()

    private lateinit var cameraExecutor: ExecutorService
    private var barcodeScanner: BarcodeScanner? = null
    private var isScanningActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        applyTheme()
        UserProductTemplates.init(this)
        ProductRepository.init(this)
        ActionHistoryRepository.init(this)
        BarcodeRepository.init(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!isLoggedIn) {
            showLogin()
        } else {
            showMain()
        }

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

        cameraExecutor = Executors.newSingleThreadExecutor()

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
                    // 👇 ДОБАВЛЯЕМ RUN ON UI THREAD, ЧТОБЫ НЕ КРАШИЛОСЬ!
                    runOnUiThread {
                        if (id != null && token != null) {
                            userId = id
                            userToken = token
                            isLoggedIn = true
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

        // 👇 Теперь обращаемся к ссылке регистрации напрямую, так же как к полям!
        loginBinding.registerLink.setOnClickListener {
            showRegister()
        }
    }

    private fun showRegister() {
        binding.bottomNavigation.visibility = View.GONE

        // Надуваем наш новый экран регистрации
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

            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                registerBinding.txtRegisterError.visibility = View.VISIBLE
                registerBinding.txtRegisterError.text = "Некорректный email"
                return@setOnClickListener
            }

            if (password.length < 6) {
                registerBinding.txtRegisterError.visibility = View.VISIBLE
                registerBinding.txtRegisterError.text = "Пароль должен быть не менее 6 символов"
                return@setOnClickListener
            }

            registerBinding.txtRegisterError.visibility = View.VISIBLE
            registerBinding.txtRegisterError.text = "Регистрация..."

            performBackendRegister(name, email, password) { id, token, message ->
                runOnUiThread {
                    if (id != null && token != null) {
                        userId = id
                        userToken = token
                        isLoggedIn = true
                        showMain()
                        Toast.makeText(this@MainActivity, "Регистрация успешна!", Toast.LENGTH_SHORT).show()
                    } else {
                        registerBinding.txtRegisterError.visibility = View.VISIBLE
                        registerBinding.txtRegisterError.text = message
                    }
                }
            }
        }

        registerBinding.btnBackToLogin.setOnClickListener {
            showLogin() // Возвращаемся ко входу
        }
    }

    private fun showMain() {
        binding.bottomNavigation.visibility = View.VISIBLE
        binding.bottomNavigation.selectedItemId = R.id.nav_stocks
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
        showProducts()
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
                    applyFiltersAndSort(productsBinding)
                }
            },
            onDeleteClick = { productToDelete ->
                logAction(ActionType.DELETED, productToDelete)
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
            categories.filter { it.contains(query, ignoreCase = true) }.forEach { category ->
                val item = TextView(this).apply {
                    text = category
                    textSize = 18f
                    setPadding(32, 48, 32, 48)
                    setTextColor(if (category == current) getColor(com.google.android.material.R.color.material_dynamic_primary50) else android.graphics.Color.BLACK)
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
            val parts = entry.productJson.split("::")
            if (parts.size >= 10) {
                val restoredProduct = Product(
                    id = UUID.randomUUID().toString(),
                    name = parts[1],
                    quantity = parts[2],
                    category = parts[3],
                    expiryDays = parts[4].toIntOrNull() ?: 0,
                    icon = parts[5],
                    isLocked = parts[6].toBoolean(),
                    manufactureDate = parts[7],
                    expiryDate = parts[8],
                    addedDate = parts[9]
                )
                products.add(restoredProduct)
                ProductRepository.saveProducts(products)
                logAction(ActionType.ADDED, restoredProduct)
                showHistory()
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
            product.addedDate
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

        // 👇 Слушаем нажатия на кнопки переключателя режимов
        scannerBinding.toggleScanMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                currentScanMode = if (checkedId == R.id.btn_scan_receipt) {
                    ScanMode.RECEIPT
                } else {
                    ScanMode.PRODUCT
                }
            }
        }

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
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
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
            try {
                // Используем IP твоего компьютера и порт 8001
                val url = URL("${BASE_URL}qr-text")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                // Формируем JSON-тело по структуре бэкенда (передаем мок-данные Ромы)
                val jsonParam = JSONObject().apply {
                    put("user_id", 1) // id мок-пользователя Ромы из init_user()
                    put("qrraw", qrRaw)
                    put("token", "mysecretpassphrase") // Токен зашивается на бэке, можно слать заглушку
                }

                val os = OutputStreamWriter(connection.outputStream)
                os.write(jsonParam.toString())
                os.flush()
                os.close()

                if (connection.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = reader.readText()
                    reader.close()

                    // Бэкенд возвращает нам JSON-список объектов с 'name' и 'quantity'
                    val jsonArray = JSONArray(response)
                    val newProducts = mutableListOf<Product>()

                    val cal = Calendar.getInstance()
                    val dateFormat = SimpleDateFormat("dd.MM.yy", Locale.getDefault())
                    val addedDate = dateFormat.format(cal.time)

                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val name = obj.getString("name")
                        val qty = obj.getString("quantity")

                        newProducts.add(
                            Product(
                                id = UUID.randomUUID().toString(),
                                name = name,
                                quantity = qty,
                                category = "Прочее", // По умолчанию падает в "Прочее"
                                expiryDays = 7,     // Дефолтный срок
                                icon = Constants.suggestIcon(name),
                                addedDate = addedDate
                            )
                        )
                    }

                    withContext(Dispatchers.Main) {
                        // Добавляем все распознанные продукты в наш общий список продуктов
                        products.addAll(newProducts)
                        ProductRepository.saveProducts(products)

                        // Логируем добавление в историю
                        newProducts.forEach { logAction(ActionType.ADDED, it) }

                        Toast.makeText(this@MainActivity, "Распознано и добавлено продуктов: ${newProducts.size}", Toast.LENGTH_LONG).show()

                        // Перекидываем пользователя на главный экран со списком продуктов
                        binding.bottomNavigation.selectedItemId = R.id.nav_stocks
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Ошибка сервера: ${connection.responseCode}", Toast.LENGTH_SHORT).show()
                        isScanningActive = true // Возобновляем работу камеры
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Не удалось обработать QR чека", Toast.LENGTH_SHORT).show()
                    isScanningActive = true // Возобновляем работу камеры
                }
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

    // Запрос регистрации на FastAPI бэкенд
// Запрос регистрации на FastAPI бэкенд
    private fun performBackendRegister(
        nameValue: String,
        emailValue: String,
        passwordValue: String,
        onResult: (Int?, String?, String) -> Unit
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = URL("${BASE_URL}users") // POST /users
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true

                val jsonParam = JSONObject().apply {
                    put("name", nameValue)
                    put("email", emailValue)
                    put("password", passwordValue)
                }

                val os = OutputStreamWriter(connection.outputStream)
                os.write(jsonParam.toString())
                os.flush()
                os.close()

                if (connection.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = reader.readText()
                    reader.close()

                    val jsonResponse = JSONObject(response)
                    val message = jsonResponse.optString("message")
                    if (message == "success") {
                        val id = jsonResponse.getInt("user_id")
                        val token = jsonResponse.getString("token")
                        onResult(id, token, "success")
                    } else if (message == "email exists") {
                        onResult(null, null, "Этот Email уже зарегистрирован")
                    } else {
                        onResult(null, null, "Ошибка при регистрации")
                    }
                } else {
                    onResult(null, null, "Ошибка сервера: ${connection.responseCode}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(null, null, "Ошибка сети: ${e.message}")
            }
        }
    } // 👈 ВОТ ТУТ закрывается функция регистрации!

    // 👇 А здесь начинается независимая функция логина!
    private fun performBackendLogin(
        emailValue: String,
        passwordValue: String,
        onResult: (Int?, String?, String) -> Unit
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = URL("${BASE_URL}login") // POST /login
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true

                val jsonParam = JSONObject().apply {
                    put("email", emailValue)
                    put("password", passwordValue)
                }

                val os = OutputStreamWriter(connection.outputStream)
                os.write(jsonParam.toString())
                os.flush()
                os.close()

                if (connection.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = reader.readText()
                    reader.close()

                    val jsonResponse = JSONObject(response)
                    val message = jsonResponse.optString("message")
                    if (message == "success") {
                        val id = jsonResponse.getInt("user_id")
                        val token = jsonResponse.getString("token")
                        onResult(id, token, "success")
                    } else {
                        onResult(null, null, "Неправильная почта или пароль")
                    }
                } else {
                    onResult(null, null, "Ошибка сервера: ${connection.responseCode}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(null, null, "Ошибка сети: ${e.message}")
            }
        }
    }

} // Конец класса MainActivity

