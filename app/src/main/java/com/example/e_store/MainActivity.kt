@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.e_store

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

// --------------------
// DATA MODEL
// --------------------
data class Product(
    val id: Int,
    val name: String,
    val price: Double,
    val imageUri: String? // store URI as String in DB
)

// --------------------
// DATABASE
// --------------------
class ProductDB(context: Context) : SQLiteOpenHelper(context, "estore.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE products (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                price REAL NOT NULL,
                imageUri TEXT
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Simple upgrade for beginners: recreate table
        db.execSQL("DROP TABLE IF EXISTS products")
        onCreate(db)
    }

    fun getAllProducts(): List<Product> {
        val list = mutableListOf<Product>()
        val cursor = readableDatabase.rawQuery("SELECT id, name, price, imageUri FROM products ORDER BY id DESC", null)
        while (cursor.moveToNext()) {
            list.add(
                Product(
                    id = cursor.getInt(0),
                    name = cursor.getString(1),
                    price = cursor.getDouble(2),
                    imageUri = cursor.getString(3)
                )
            )
        }
        cursor.close()
        return list
    }

    fun addProduct(name: String, price: Double, imageUri: String?) {
        val cv = ContentValues().apply {
            put("name", name)
            put("price", price)
            put("imageUri", imageUri)
        }
        writableDatabase.insert("products", null, cv)
    }

    fun updateProduct(id: Int, name: String, price: Double, imageUri: String?) {
        val cv = ContentValues().apply {
            put("name", name)
            put("price", price)
            put("imageUri", imageUri)
        }
        writableDatabase.update("products", cv, "id=?", arrayOf(id.toString()))
    }

    fun deleteProduct(id: Int) {
        writableDatabase.delete("products", "id=?", arrayOf(id.toString()))
    }
}

// --------------------
// MAIN ACTIVITY
// --------------------
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EStoreApp() }
    }
}

private enum class Screen { LIST, ADD, EDIT }

// --------------------
// APP UI
// --------------------
@Composable
fun EStoreApp() {
    val context = LocalContext.current
    val db = remember { ProductDB(context) }

    var screen by remember { mutableStateOf(Screen.LIST) }
    var selected by remember { mutableStateOf<Product?>(null) }

    var refreshKey by remember { mutableStateOf(0) }
    val products = remember(refreshKey) { db.getAllProducts() }

    when (screen) {
        Screen.LIST -> ProductListScreen(
            products = products,
            onAddClick = { screen = Screen.ADD },
            onTapProduct = {
                selected = it
                screen = Screen.EDIT
            }
        )

        Screen.ADD -> AddOrEditProductScreen(
            title = "Add Product",
            initialName = "",
            initialPrice = "",
            initialImageUri = null,
            onSave = { name, price, imageUri ->
                db.addProduct(name, price, imageUri)
                refreshKey++
                screen = Screen.LIST
            },
            onDelete = null,
            onCancel = { screen = Screen.LIST }
        )

        Screen.EDIT -> {
            val p = selected
            if (p == null) {
                screen = Screen.LIST
            } else {
                AddOrEditProductScreen(
                    title = "Edit Product",
                    initialName = p.name,
                    initialPrice = p.price.toString(),
                    initialImageUri = p.imageUri,
                    onSave = { name, price, imageUri ->
                        db.updateProduct(p.id, name, price, imageUri)
                        refreshKey++
                        screen = Screen.LIST
                    },
                    onDelete = {
                        db.deleteProduct(p.id)
                        refreshKey++
                        screen = Screen.LIST
                    },
                    onCancel = { screen = Screen.LIST }
                )
            }
        }
    }
}

@Composable
fun ProductListScreen(
    products: List<Product>,
    onAddClick: () -> Unit,
    onTapProduct: (Product) -> Unit
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Product List") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) { Text("+") }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
            items(products) { p ->
                Card(
                    modifier = Modifier
                        .padding(10.dp)
                        .fillMaxWidth()
                        .clickable { onTapProduct(p) }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Image
                        if (!p.imageUri.isNullOrBlank()) {
                            AsyncImage(
                                model = p.imageUri,
                                contentDescription = "product image",
                                modifier = Modifier.size(56.dp),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(modifier = Modifier.size(56.dp))
                        }

                        // Text
                        Column(modifier = Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium)
                            Text("$${"%.2f".format(p.price)}", style = MaterialTheme.typography.bodyMedium)
                            Text("Tap to edit", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddOrEditProductScreen(
    title: String,
    initialName: String,
    initialPrice: String,
    initialImageUri: String?,
    onSave: (String, Double, String?) -> Unit,
    onDelete: (() -> Unit)?,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var priceText by remember { mutableStateOf(initialPrice) }
    var imageUri by remember { mutableStateOf(initialImageUri) }
    var error by remember { mutableStateOf<String?>(null) }

    val imagePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        imageUri = uri?.toString()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(title) }) }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // Preview image
            if (!imageUri.isNullOrBlank()) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = "selected image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentScale = ContentScale.Crop
                )
            }

            Button(onClick = { imagePicker.launch("image/*") }) {
                Text("Pick Image")
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Product Name") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = priceText,
                onValueChange = { priceText = it },
                label = { Text("Product Price") },
                modifier = Modifier.fillMaxWidth()
            )

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val price = priceText.toDoubleOrNull()
                    when {
                        name.isBlank() -> error = "Name cannot be empty"
                        price == null || price < 0 -> error = "Enter a valid price"
                        else -> {
                            error = null
                            onSave(name.trim(), price, imageUri)
                        }
                    }
                }
            ) { Text("Save") }

            if (onDelete != null) {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onDelete
                ) { Text("Delete") }
            }

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onCancel
            ) { Text("Cancel") }
        }
    }
}
