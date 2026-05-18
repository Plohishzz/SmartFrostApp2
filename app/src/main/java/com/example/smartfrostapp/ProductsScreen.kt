package com.example.smartfrostapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.annotation.OptIn as AndroidOptIn
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.util.concurrent.Executors
import android.util.Log

data class Product(
    val id: String,
    val name: String,
    val quantity: String,
    val location: String,
    val expiryDays: Int,
    val icon: String,
    val isLocked: Boolean = false,
    val manufactureDate: String = "",
    val expiryDate: String = ""
)

data class ProductCategory(
    val name: String,
    val count: Int,
    val products: List<Product>,
    val icon: String
)

@Composable
fun ProductsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("barcode_prefs", android.content.Context.MODE_PRIVATE) }
    
    var searchQuery by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf("NAME") }
    var selectedCategoryFilter by remember { mutableStateOf("Все") }
    var selectedNavIndex by remember { mutableIntStateOf(1) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<Product?>(null) }
    
    // Load initial mappings
    var barcodeMappings by remember { 
        mutableStateOf(
            sharedPrefs.all.mapValues { it.value.toString() }
        ) 
    }

    val initialCategories = remember {
        listOf(
            ProductCategory(
                name = "Молочное",
                count = 3,
                icon = "🥛",
                products = listOf(
                    Product("1", "Молоко", "1 л", "Холодильник", 0, "🥛", manufactureDate = "10.05.25", expiryDate = "10.05.25"),
                    Product("2", "Масло сливочное", "200 г", "Холодильник", 7, "🧈", manufactureDate = "01.05.25", expiryDate = "17.05.25"),
                    Product("3", "Сыр Гауда", "300 г", "Холодильник", 2, "🧀", manufactureDate = "05.05.25", expiryDate = "12.05.25")
                )
            ),
            ProductCategory(
                name = "Прочее",
                count = 1,
                icon = "📦",
                products = listOf(
                    Product("4", "Яйца", "2 шт", "Холодильник", 10, "🥚", manufactureDate = "10.05.25", expiryDate = "20.05.25")
                )
            ),
            ProductCategory(
                name = "Мясо",
                count = 2,
                icon = "🥩",
                products = listOf(
                    Product("5", "Говядина", "1 кг", "Морозильник", 28, "🥩", manufactureDate = "01.05.25", expiryDate = "29.05.25"),
                    Product("6", "Куриная грудка", "500 г", "Холодильник", 0, "🍗", manufactureDate = "10.05.25", expiryDate = "10.05.25")
                )
            )
        )
    }

    var categories by remember { mutableStateOf(initialCategories) }

    val allCategoryNames = remember(categories) {
        listOf("Все") + categories.map { it.name }.distinct()
    }

    val filteredCategories = remember(searchQuery, sortBy, selectedCategoryFilter, categories) {
        categories.filter { category ->
            selectedCategoryFilter == "Все" || category.name == selectedCategoryFilter
        }.map { category ->
            val filteredProducts = category.products.filter { product ->
                product.name.contains(searchQuery, ignoreCase = true)
            }.let { products ->
                if (sortBy == "NAME") products.sortedBy { it.name }
                else products.sortedBy { it.expiryDays }
            }
            category.copy(products = filteredProducts, count = filteredProducts.size)
        }.filter { it.products.isNotEmpty() }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (selectedNavIndex == 1) ProductsTopBar()
        },
        bottomBar = {
            ProductsBottomNavigation(
                selectedIndex = selectedNavIndex,
                onIndexSelected = { selectedNavIndex = it }
            )
        },
        floatingActionButton = {
            if (selectedNavIndex == 1) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = Color(0xFF1976D2),
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_product))
                }
            }
        },
        containerColor = Color(0xFFF8FAFC)
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedNavIndex) {
                1 -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        SearchBar(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it }
                        )
                        
                        SortingChips(
                            selectedSort = sortBy,
                            onSortSelected = { sortBy = it }
                        )

                        CategoryFilterChips(
                            categories = allCategoryNames,
                            selectedCategory = selectedCategoryFilter,
                            onCategorySelected = { selectedCategoryFilter = it }
                        )

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 80.dp)
                        ) {
                            if (selectedCategoryFilter == "Все") {
                                val allProducts = filteredCategories.flatMap { it.products }.let { products ->
                                    if (sortBy == "NAME") products.sortedBy { it.name }
                                    else products.sortedBy { it.expiryDays }
                                }
                                
                                item {
                                    Card(
                                        modifier = Modifier
                                            .padding(16.dp)
                                            .fillMaxWidth(),
                                        shape = RoundedCornerShape(24.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF0F2F5))
                                    ) {
                                        Column {
                                            allProducts.forEachIndexed { index, product ->
                                                ProductItem(
                                                    product = product,
                                                    onUpdate = { updatedProduct ->
                                                        categories = categories.map { cat ->
                                                            cat.copy(products = cat.products.map { if (it.id == updatedProduct.id) updatedProduct else it })
                                                        }
                                                    },
                                                    onDelete = { 
                                                        categories = categories.map { cat ->
                                                            cat.copy(products = cat.products.filter { it.id != product.id })
                                                        }
                                                    },
                                                    onEdit = { editingProduct = product }
                                                )
                                                if (index < (allProducts.size - 1)) {
                                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = Color(0xFFF0F2F5))
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                items(filteredCategories) { category ->
                                    CategorySection(
                                        category = category,
                                        onUpdateProduct = { updatedProduct ->
                                            categories = categories.map { cat ->
                                                cat.copy(products = cat.products.map { if (it.id == updatedProduct.id) updatedProduct else it })
                                            }
                                        },
                                        onDeleteProduct = { productId ->
                                            categories = categories.map { cat ->
                                                cat.copy(products = cat.products.filter { it.id != productId })
                                            }
                                        },
                                        onEditProduct = { editingProduct = it }
                                    )
                                }
                            }
                        }
                    }
                }
                3 -> RecipesTab()
                4 -> SettingsTab()
                2 -> ScannerTab(
                    barcodeMappings = barcodeMappings,
                    onProductIdentified = { name, barcode ->
                        // Show add dialog with pre-filled name if needed
                        // Or just log for now
                    },
                    onNewBarcodeScanned = { barcode ->
                        // Show add dialog with this barcode context
                    },
                    categories = categories,
                    onAddProduct = { newProduct, categoryName, barcode ->
                        categories = categories.map { cat ->
                            if (cat.name == categoryName) {
                                cat.copy(products = cat.products + newProduct, count = cat.count + 1)
                            } else cat
                        }
                        if (barcode != null) {
                            barcodeMappings = barcodeMappings + (barcode to newProduct.name)
                            sharedPrefs.edit().putString(barcode, newProduct.name).apply()
                        }
                    },
                    onAddCategory = { newName ->
                        if (!categories.any { it.name == newName }) {
                            categories = categories + ProductCategory(
                                name = newName,
                                count = 0,
                                icon = "📁",
                                products = emptyList()
                            )
                        }
                    }
                )
                else -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("В разработке", color = Color.Gray)
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddProductDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, icon, quantity, location, expiry, categoryName, mDate, eDate ->
                val newProduct = Product(
                    id = java.util.UUID.randomUUID().toString(),
                    name = name,
                    quantity = quantity,
                    location = location,
                    expiryDays = expiry,
                    icon = icon,
                    manufactureDate = mDate,
                    expiryDate = eDate
                )
                categories = categories.map { cat ->
                    if (cat.name == categoryName) {
                        cat.copy(products = cat.products + newProduct, count = cat.count + 1)
                    } else cat
                }
                showAddDialog = false
            },
            categories = categories.map { it.name },
            onAddCategory = { newName ->
                if (!categories.any { it.name == newName }) {
                    categories = categories + ProductCategory(
                        name = newName,
                        count = 0,
                        icon = "📁",
                        products = emptyList()
                    )
                }
            }
        )
    }

    editingProduct?.let { product ->
        val currentCategory = categories.find { it.products.contains(product) }?.name ?: ""
        EditProductDialog(
            product = product,
            currentCategory = currentCategory,
            categories = categories.map { it.name },
            onDismiss = { editingProduct = null },
            onConfirm = { updatedProduct, newCategoryName ->
                categories = categories.map { cat ->
                    // Remove from old category if changed
                    var newProducts = cat.products.filter { it.id != updatedProduct.id }
                    // Add to new category
                    if (cat.name == newCategoryName) {
                        newProducts = newProducts + updatedProduct
                    }
                    cat.copy(products = newProducts, count = newProducts.size)
                }
                editingProduct = null
            },
            onAddCategory = { newName ->
                if (!categories.any { it.name == newName }) {
                    categories = categories + ProductCategory(
                        name = newName,
                        count = 0,
                        icon = "📁",
                        products = emptyList()
                    )
                }
            }
        )
    }
}

@Composable
fun ProductsTopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1976D2)),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.size(24.dp).background(Color.White.copy(alpha = 0.3f)))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.products_title),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1A1C1E)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(query: String, onQueryChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.search_placeholder), color = Color.Gray) },
            leadingIcon = { 
                Icon(
                    painter = painterResource(id = R.drawable.ic_search),
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            },
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = Color.White,
                focusedContainerColor = Color.White,
                unfocusedBorderColor = Color(0xFFE0E2E5),
                focusedBorderColor = Color(0xFF1976D2)
            ),
            singleLine = true
        )
    }
}

@Composable
fun SortingChips(selectedSort: String, onSortSelected: (String) -> Unit) {
    val options = listOf(
        "NAME" to stringResource(R.string.sort_name),
        "EXPIRY" to stringResource(R.string.sort_expired)
    )

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(options) { (id, label) ->
            val isSelected = id == selectedSort
            Surface(
                onClick = { onSortSelected(id) },
                shape = RoundedCornerShape(20.dp),
                color = if (isSelected) Color(0xFF1976D2) else Color.White,
                border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E2E5))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (id == "NAME") Icons.Default.Menu else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isSelected) Color.White else Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = label,
                        color = if (isSelected) Color.White else Color(0xFF1A1C1E),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryFilterChips(
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories) { category ->
            val isSelected = category == selectedCategory
            Surface(
                onClick = { onCategorySelected(category) },
                shape = RoundedCornerShape(20.dp),
                color = if (isSelected) Color(0xFF1A1C1E) else Color.White,
                border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E2E5))
            ) {
                Text(
                    text = category,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = if (isSelected) Color.White else Color(0xFF1A1C1E),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun CategorySection(
    category: ProductCategory,
    onUpdateProduct: (Product) -> Unit,
    onDeleteProduct: (String) -> Unit,
    onEditProduct: (Product) -> Unit
) {
    var isExpanded by remember { mutableStateOf(true) }

    Column(modifier = Modifier.padding(top = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clickable { isExpanded = !isExpanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(category.icon, fontSize = 18.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = category.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A1C1E)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = category.count.toString(),
                    fontSize = 16.sp,
                    color = Color.Gray
                )
            }
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        }
        
        if (isExpanded) {
            Card(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF0F2F5))
            ) {
                Column {
                    category.products.forEachIndexed { index, product ->
                        ProductItem(
                            product = product,
                            onUpdate = onUpdateProduct,
                            onDelete = { onDeleteProduct(product.id) },
                            onEdit = { onEditProduct(product) }
                        )
                        if (index < (category.products.size - 1)) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = Color(0xFFF0F2F5))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProductItem(
    product: Product,
    onUpdate: (Product) -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(Color(0xFFF8FAFC), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(product.icon, fontSize = 20.sp)
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = product.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF1A1C1E)
                )
                
                val expiryText = if (product.expiryDays == 0) {
                    stringResource(R.string.today)
                } else if (product.expiryDays < 0) {
                    stringResource(R.string.expired)
                } else {
                    stringResource(R.string.days_left, product.expiryDays)
                }
                
                val expiryColor = if (product.expiryDays <= 2) Color(0xFFF44336) else Color(0xFF4CAF50)
                val expiryBg = if (product.expiryDays <= 2) Color(0xFFFFEBEE) else Color(0xFFE8F5E9)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = expiryBg
                    ) {
                        Text(
                            text = expiryText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            fontSize = 11.sp,
                            color = expiryColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        "-",
                        color = Color.Gray,
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .clickable {
                                val parts = product.quantity.split(" ")
                                if (parts.size == 2) {
                                    val amount = parts[0].toIntOrNull() ?: 0
                                    if (amount > 0) {
                                        onUpdate(product.copy(quantity = "${amount - 1} ${parts[1]}"))
                                    }
                                }
                            }
                    )
                    Text(
                        text = product.quantity,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Text(
                        "+",
                        color = Color.Gray,
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .clickable {
                                val parts = product.quantity.split(" ")
                                if (parts.size == 2) {
                                    val amount = parts[0].toIntOrNull() ?: 0
                                    onUpdate(product.copy(quantity = "${amount + 1} ${parts[1]}"))
                                }
                            }
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = R.drawable.ic_delete),
                contentDescription = null,
                tint = Color(0xFFBDBDBD),
                modifier = Modifier
                    .size(18.dp)
                    .clickable { onDelete() }
            )
        }
    }
}


fun suggestIcon(name: String): String {
    val lowName = name.lowercase()
    return when {
        lowName.contains("молоко") -> "🥛"
        lowName.contains("масло") -> "🧈"
        lowName.contains("сыр") -> "🧀"
        lowName.contains("яйц") -> "🥚"
        lowName.contains("говядина") || lowName.contains("мясо") -> "🥩"
        lowName.contains("курица") || lowName.contains("грудка") -> "🍗"
        lowName.contains("яблоко") -> "🍎"
        lowName.contains("банан") -> "🍌"
        lowName.contains("хлеб") || lowName.contains("булка") -> "🍞"
        lowName.contains("рыба") -> "🐟"
        lowName.contains("вода") || lowName.contains("сок") -> "🥤"
        lowName.contains("пиво") -> "🍺"
        lowName.contains("вино") -> "🍷"
        lowName.contains("овощ") || lowName.contains("салат") -> "🥗"
        lowName.contains("фрукт") -> "🍎"
        lowName.contains("горох") -> "🫛"
        lowName.contains("картофель") || lowName.contains("картошка") -> "🥔"
        lowName.contains("морковь") -> "🥕"
        lowName.contains("огурец") -> "🥒"
        lowName.contains("томат") || lowName.contains("помидор") -> "🍅"
        lowName.contains("лимон") -> "🍋"
        lowName.contains("колбаса") || lowName.contains("сосиски") -> "🌭"
        lowName.contains("йогурт") -> "🍦"
        else -> "📦"
    }
}

fun calculateDaysRemaining(expiryDateStr: String): Int {
    if (expiryDateStr.isEmpty()) return 0
    return try {
        val format = java.text.SimpleDateFormat("dd.MM.yy", java.util.Locale.getDefault())
        val expiryDate = format.parse(expiryDateStr) ?: return 0
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val today = calendar.time
        
        val diff = expiryDate.time - today.time
        (diff / (1000 * 60 * 60 * 24)).toInt()
    } catch (e: Exception) {
        0
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { },
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("ДД.ММ.ГГ") },
            readOnly = true,
            trailingIcon = {
                IconButton(onClick = { showPicker = true }) {
                    Icon(Icons.Default.DateRange, contentDescription = "Select Date")
                }
            }
        )
        // Overlay to catch clicks on the whole field
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { showPicker = true }
        )
    }

    if (showPicker) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = java.util.Date(millis)
                        val format = java.text.SimpleDateFormat("dd.MM.yy", java.util.Locale.getDefault())
                        onValueChange(format.format(date))
                    }
                    showPicker = false
                }) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

val ALL_ICONS = listOf(
    "🥛", "🧈", "🧀", "🥚", "🥩", "🍗", "🍖", "🌭", "🍔", "🍟", "🍕", "🥪", "🥙", "🌮", "🌯", "🥗", "🥘", "🍝", "🍜", "🍲", "🍛", "🍣", "🍱", "🥟", "🍤", "🍙", "🍚", "🍘", "🍥", "🍢", "🍡", "🍧", "🍨", "🍦", "🥧", "🍰", "🎂", "🍮", "🍭", "🍬", "🍫", "🍿", "🍩", "🍪", "🌰", "🥜", "🍯", "🥛", "☕", "🍵", "🥤", "🍶", "🍺", "🍻", "🥂", "🍷", "🥃", "🍸", "🍹", "🧉", "🍾", "🧊", "🥄", "🍴", "🍽", "🥣", "🥡", "🥢", "🧂",
    "🍏", "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🫐", "🍈", "🍒", "🍑", "🥭", "🍍", "🥥", "🥝", "🍅", "🍆", "🥑", "🥦", "🥬", "🥒", "🌶", "🫑", "🌽", "🥕", "🫒", "🧄", "🧅", "🥔", "🍠", "🥐", "🥯", "🍞", "🥖", "🥨", "🥞", "🧀", "🥩", "🍗", "🍖", "🦴", "🌭", "🍔", "🍟", "🍕", "🥪", "🥙", "🫓", "🌮", "🌯", "🥗", "🥘", "🫕", "🥣", "🍝", "🍜", "🍲", "🍛", "🍣", "🍱", "🥟", "🦪", "🍤", "🍙", "🍚", "🍘", "🍥", "🍢", "🍡", "🍧", "🍨", "🍦", "🥧", "🧁", "🍰", "🎂", "🍮", "🍭", "🍬", "🍫", "🍿", "🍩", "🍪", "🌰", "🥜", "🍯", "🥛", "☕", "🍵", "🧃", "🥤", "🧋", "🍶", "🍺", "🍻", "🥂", "🍷", "🥃", "🍸", "🍹", "🧉", "🍾", "🧊", "🥄", "🍴", "🍽", "🥣", "🥡", "🥢", "🧂", "📦", "📁"
).distinct()

@Composable
fun IconPicker(
    onIconSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Выберите иконку") },
        text = {
            LazyColumn(modifier = Modifier.height(300.dp)) {
                val chunkedIcons = ALL_ICONS.chunked(5)
                items(chunkedIcons) { rowIcons ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowIcons.forEach { icon ->
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFF8FAFC))
                                    .clickable { 
                                        onIconSelected(icon)
                                        onDismiss()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(icon, fontSize = 24.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProductDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, Int, String, String, String) -> Unit,
    categories: List<String>,
    onAddCategory: (String) -> Unit,
    initialName: String = ""
) {
    var name by remember { mutableStateOf(initialName) }
    var icon by remember { mutableStateOf(if (initialName.isNotEmpty()) suggestIcon(initialName) else "📦") }
    var quantityValue by remember { mutableStateOf("1") }
    var selectedUnit by remember { mutableStateOf("кг") }
    var manufactureDate by remember { mutableStateOf("") }
    var expiryDate by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(categories.firstOrNull() ?: "") }
    
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showIconPicker by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_product)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp))
                            .clickable { showIconPicker = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(icon, fontSize = 28.sp)
                    }
                    OutlinedTextField(
                        value = name,
                        onValueChange = { 
                            name = it 
                            icon = suggestIcon(it)
                        },
                        label = { Text(stringResource(R.string.product_name)) },
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quantityValue,
                        onValueChange = { quantityValue = it },
                        label = { Text(stringResource(R.string.product_quantity)) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    
                    val units = listOf(stringResource(R.string.unit_pcs), stringResource(R.string.unit_kg), stringResource(R.string.unit_l))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.product_unit), style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            units.forEach { unit ->
                                FilterChip(
                                    selected = selectedUnit == unit,
                                    onClick = { selectedUnit = unit },
                                    label = { Text(unit) }
                                )
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DatePickerField(
                        label = stringResource(R.string.manufacture_date),
                        value = manufactureDate,
                        onValueChange = { manufactureDate = it },
                        modifier = Modifier.weight(1f)
                    )
                    DatePickerField(
                        label = stringResource(R.string.expiry_date),
                        value = expiryDate,
                        onValueChange = { expiryDate = it },
                        modifier = Modifier.weight(1f)
                    )
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.product_category), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { showAddCategoryDialog = true }) {
                            Text("+ ${stringResource(R.string.add_category)}", fontSize = 12.sp)
                        }
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categories.forEach { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { 
                val quantityStr = if (quantityValue.isNotEmpty()) "$quantityValue $selectedUnit" else ""
                val calculatedExpiry = calculateDaysRemaining(expiryDate)
                onConfirm(name, icon, quantityStr, "Холодильник", calculatedExpiry, selectedCategory, manufactureDate, expiryDate)
            }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )

    if (showIconPicker) {
        IconPicker(
            onIconSelected = { icon = it },
            onDismiss = { showIconPicker = false }
        )
    }

    if (showAddCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            title = { Text(stringResource(R.string.add_category)) },
            text = {
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    label = { Text(stringResource(R.string.new_category_name)) }
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newCategoryName.isNotBlank()) {
                        onAddCategory(newCategoryName)
                        selectedCategory = newCategoryName
                        showAddCategoryDialog = false
                        newCategoryName = ""
                    }
                }) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCategoryDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun RecipesTab() {
    val recipes = listOf(
        "Омлет с сыром" to "🥚",
        "Стейк с овощами" to "🥩",
        "Греческий салат" to "🥗",
        "Куриный суп" to "🥣"
    )

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = stringResource(R.string.recipes_title),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(recipes) { (name, icon) ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(48.dp).background(Color(0xFFF8FAFC), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(icon, fontSize = 24.sp)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                text = stringResource(R.string.recipe_ingredients, (3..6).random()),
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFE8F5E9)
                        ) {
                            Text(
                                text = stringResource(R.string.recipe_ready),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = Color(0xFF4CAF50),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsTab() {
    val settingsItems = listOf(
        R.string.settings_profile to Icons.Default.Person,
        R.string.settings_notifications to Icons.Default.Notifications,
        R.string.settings_appearance to Icons.Default.Build,
        R.string.settings_help to Icons.Default.Info
    )

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(64.dp).background(Color(0xFF1976D2), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("Г", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(stringResource(R.string.user_name), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(stringResource(R.string.user_email), color = Color.Gray, fontSize = 14.sp)
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column {
                settingsItems.forEachIndexed { index, (resId, icon) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(icon, contentDescription = null, tint = Color(0xFF1976D2))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(stringResource(resId), modifier = Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.Gray)
                    }
                    if (index < settingsItems.size - 1) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = Color(0xFFF8FAFC))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        TextButton(
            onClick = { },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
        ) {
            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.settings_logout))
        }
    }
}

@Composable
fun ProductsBottomNavigation(selectedIndex: Int, onIndexSelected: (Int) -> Unit) {
    NavigationBar(
        containerColor = Color.White,
        tonalElevation = 8.dp
    ) {
        val items = listOf(
            Triple(stringResource(R.string.nav_overview), Icons.Default.Home, false),
            Triple(stringResource(R.string.nav_stocks), Icons.Default.ShoppingCart, true),
            Triple(stringResource(R.string.nav_scanner), Icons.Default.Search, true),
            Triple(stringResource(R.string.nav_recipes), Icons.Default.Menu, false),
            Triple(stringResource(R.string.nav_settings), Icons.Default.Settings, false)
        )
        
        items.forEachIndexed { index, (label, icon, hasBadge) ->
            NavigationBarItem(
                selected = index == selectedIndex,
                onClick = { onIndexSelected(index) },
                icon = {
                    BadgedBox(
                        badge = {
                            if (hasBadge) {
                                Badge(containerColor = Color(0xFFF44336)) {
                                    Text("4", color = Color.White)
                                }
                            }
                        }
                    ) {
                        Icon(icon, contentDescription = label)
                    }
                },
                label = { Text(label, fontSize = 10.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFF1976D2),
                    selectedTextColor = Color(0xFF1976D2),
                    unselectedIconColor = Color.Gray,
                    unselectedTextColor = Color.Gray,
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProductDialog(
    product: Product,
    currentCategory: String,
    categories: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (Product, String) -> Unit,
    onAddCategory: (String) -> Unit
) {
    var name by remember { mutableStateOf(product.name) }
    var icon by remember { mutableStateOf(product.icon) }
    val initialQuantityParts = product.quantity.split(" ")
    var quantityValue by remember { mutableStateOf(initialQuantityParts.getOrNull(0)?.ifEmpty { "1" } ?: "1") }
    var selectedUnit by remember { mutableStateOf(initialQuantityParts.getOrNull(1) ?: "кг") }
    var manufactureDate by remember { mutableStateOf(product.manufactureDate) }
    var expiryDate by remember { mutableStateOf(product.expiryDate) }
    var selectedCategory by remember { mutableStateOf(currentCategory) }

    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showIconPicker by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_product)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp))
                            .clickable { showIconPicker = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(icon, fontSize = 28.sp)
                    }
                    OutlinedTextField(
                        value = name,
                        onValueChange = { 
                            name = it 
                            icon = suggestIcon(it)
                        },
                        label = { Text(stringResource(R.string.product_name)) },
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quantityValue,
                        onValueChange = { quantityValue = it },
                        label = { Text(stringResource(R.string.product_quantity)) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    
                    val units = listOf(stringResource(R.string.unit_pcs), stringResource(R.string.unit_kg), stringResource(R.string.unit_l))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.product_unit), style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            units.forEach { unit ->
                                FilterChip(
                                    selected = selectedUnit == unit,
                                    onClick = { selectedUnit = unit },
                                    label = { Text(unit) }
                                )
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DatePickerField(
                        label = stringResource(R.string.manufacture_date),
                        value = manufactureDate,
                        onValueChange = { manufactureDate = it },
                        modifier = Modifier.weight(1f)
                    )
                    DatePickerField(
                        label = stringResource(R.string.expiry_date),
                        value = expiryDate,
                        onValueChange = { expiryDate = it },
                        modifier = Modifier.weight(1f)
                    )
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.product_category), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { showAddCategoryDialog = true }) {
                            Text("+ ${stringResource(R.string.add_category)}", fontSize = 12.sp)
                        }
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categories.forEach { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { 
                val quantityStr = if (quantityValue.isNotEmpty()) "$quantityValue $selectedUnit" else ""
                val calculatedExpiry = calculateDaysRemaining(expiryDate)
                val updatedProduct = product.copy(
                    name = name,
                    icon = icon,
                    quantity = quantityStr,
                    location = "Холодильник",
                    expiryDays = calculatedExpiry,
                    manufactureDate = manufactureDate,
                    expiryDate = expiryDate
                )
                onConfirm(updatedProduct, selectedCategory) 
            }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )

    if (showIconPicker) {
        IconPicker(
            onIconSelected = { icon = it },
            onDismiss = { showIconPicker = false }
        )
    }

    if (showAddCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            title = { Text(stringResource(R.string.add_category)) },
            text = {
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    label = { Text(stringResource(R.string.new_category_name)) }
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newCategoryName.isNotBlank()) {
                        onAddCategory(newCategoryName)
                        selectedCategory = newCategoryName
                        showAddCategoryDialog = false
                        newCategoryName = ""
                    }
                }) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCategoryDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun ScannerTab(
    barcodeMappings: Map<String, String>,
    onProductIdentified: (String, String) -> Unit,
    onNewBarcodeScanned: (String) -> Unit,
    categories: List<ProductCategory>,
    onAddProduct: (Product, String, String?) -> Unit,
    onAddCategory: (String) -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
        }
    )

    var isScanning by remember { mutableStateOf(false) }
    var scannedBarcode by remember { mutableStateOf<String?>(null) }
    var showAddDialogForBarcode by remember { mutableStateOf<String?>(null) }

    if (showAddDialogForBarcode != null) {
        val prefilledName = barcodeMappings[showAddDialogForBarcode] ?: ""
        AddProductDialog(
            onDismiss = { 
                showAddDialogForBarcode = null
                isScanning = true // Resume scanning
            },
            onConfirm = { name, icon, quantity, location, expiry, categoryName, mDate, eDate ->
                val newProduct = Product(
                    id = java.util.UUID.randomUUID().toString(),
                    name = name,
                    quantity = quantity,
                    location = location,
                    expiryDays = expiry,
                    icon = icon,
                    manufactureDate = mDate,
                    expiryDate = eDate
                )
                onAddProduct(newProduct, categoryName, showAddDialogForBarcode)
                showAddDialogForBarcode = null
                isScanning = true // Resume scanning
            },
            categories = categories.map { it.name },
            onAddCategory = onAddCategory,
            initialName = prefilledName
        )
    }

    if (!hasCameraPermission) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Нужно разрешение на камеру для работы сканера")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                Text("Разрешить")
            }
        }
    } else {
        if (!isScanning && showAddDialogForBarcode == null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(100.dp),
                    tint = Color.Gray
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Сканер штрих-кодов",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A1C1E)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Наведите камеру на штрих-код продукта",
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = { isScanning = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                ) {
                    Text("Запустить камеру")
                }
            }
        } else if (isScanning) {
            BarcodeCameraPreview(
                onBarcodeScanned = { barcode ->
                    isScanning = false
                    if (barcodeMappings.containsKey(barcode)) {
                        // Already known, but we still show add dialog pre-filled?
                        // Or just show result? 
                        // User said: "System automatically determines what product it is"
                        // Maybe show a confirmation to add THIS product again?
                        showAddDialogForBarcode = barcode
                    } else {
                        showAddDialogForBarcode = barcode
                    }
                },
                onClose = { isScanning = false }
            )
        }
    }
}

@Composable
fun BarcodeCameraPreview(
    onBarcodeScanned: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                val previewView = PreviewView(context)
                val executor = ContextCompat.getMainExecutor(context)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = androidx.camera.core.Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val scanner = BarcodeScanning.getClient()
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    imageAnalysis.setAnalyzer(executor) { imageProxy ->
                        @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
                        val mediaImage = imageProxy.image
                        if (mediaImage != null) {
                            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                            scanner.process(image)
                                .addOnSuccessListener { barcodes ->
                                    for (barcode in barcodes) {
                                        val rawValue = barcode.rawValue
                                        if (rawValue != null) {
                                            onBarcodeScanned(rawValue)
                                            break
                                        }
                                    }
                                }
                                .addOnCompleteListener {
                                    imageProxy.close()
                                }
                        } else {
                            imageProxy.close()
                        }
                    }

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                    } catch (e: Exception) {
                        Log.e("CameraPreview", "Binding failed", e)
                    }
                }, executor)
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // UI Overlay for scanner
        IconButton(
            onClick = onClose,
            modifier = Modifier.padding(16.dp).align(Alignment.TopStart)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }
        
        // Scanning frame
        Box(
            modifier = Modifier
                .size(250.dp)
                .border(2.dp, Color.White, RoundedCornerShape(12.dp))
                .align(Alignment.Center)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ProductsScreenPreview() {
    MaterialTheme {
        ProductsScreen()
    }
}
