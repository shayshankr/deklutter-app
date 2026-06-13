package com.example.deklutter_app.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.deklutter_app.data.model.Item
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

class ItemViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance("gs://deklutter-67217.firebasestorage.app")
    private val auth = FirebaseAuth.getInstance()

    private val _items = MutableStateFlow<List<Item>>(emptyList())
    val items: StateFlow<List<Item>> = _items

    private val _userItems = MutableStateFlow<List<Item>>(emptyList())
    val userItems: StateFlow<List<Item>> = _userItems

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _uploadProgress = MutableStateFlow<Float?>(null)
    val uploadProgress: StateFlow<Float?> = _uploadProgress

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        loadItems()
    }

    fun loadItems() {
        db.collection("items")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) { _error.value = e.message; return@addSnapshotListener }
                _items.value = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Item::class.java)?.copy(id = doc.id)
                } ?: emptyList()
            }
    }

    fun loadUserItems() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("items")
            .whereEqualTo("sellerId", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                _userItems.value = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Item::class.java)?.copy(id = doc.id)
                } ?: emptyList()
            }
    }

    fun postItem(
        title: String,
        description: String,
        price: Double,
        category: String,
        city: String = "",
        imageUris: List<Uri>,
        phone: String = "",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val user = auth.currentUser ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _uploadProgress.value = null
            try {
                val imageUrls = if (imageUris.isNotEmpty()) {
                    val totalBytes = LongArray(imageUris.size)
                    val doneBytes = LongArray(imageUris.size)

                    imageUris.mapIndexed { index, uri ->
                        async {
                            val compressed = compressImage(uri)
                            totalBytes[index] = compressed.size.toLong()
                            val ref = storage.reference.child("items/${UUID.randomUUID()}.jpg")
                            val uploadTask = ref.putBytes(compressed)
                            uploadTask.addOnProgressListener { snap ->
                                doneBytes[index] = snap.bytesTransferred
                                val total = totalBytes.sum().takeIf { it > 0 } ?: 1L
                                _uploadProgress.value = doneBytes.sum().toFloat() / total.toFloat()
                            }
                            uploadTask.await()
                            ref.downloadUrl.await().toString()
                        }
                    }.awaitAll()
                } else emptyList()

                _uploadProgress.value = null
                val firstImageUrl = imageUrls.firstOrNull() ?: ""

                val itemMap = mapOf(
                    "title" to title,
                    "description" to description,
                    "price" to price,
                    "category" to category,
                    "city" to city,
                    "imageUrl" to firstImageUrl,
                    "imageUrls" to imageUrls,
                    "sellerId" to user.uid,
                    "sellerName" to (user.displayName ?: ""),
                    "sellerEmail" to (user.email ?: ""),
                    "sellerPhone" to phone,
                    "createdAt" to System.currentTimeMillis(),
                    "sold" to false
                )
                db.collection("items").add(itemMap).await()
                withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e.localizedMessage ?: "Failed to post item") }
            } finally {
                _isLoading.value = false
                _uploadProgress.value = null
            }
        }
    }

    private fun compressImage(uri: Uri): ByteArray {
        val input = getApplication<Application>().contentResolver.openInputStream(uri)
            ?: throw Exception("Could not read image")
        val original = BitmapFactory.decodeStream(input)
        input.close()

        val maxDim = 800
        val scale = minOf(maxDim.toFloat() / original.width, maxDim.toFloat() / original.height, 1f)
        val resized = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                original,
                (original.width * scale).toInt(),
                (original.height * scale).toInt(),
                true
            )
        } else original

        val out = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 50, out)
        return out.toByteArray()
    }

    fun markAsSold(itemId: String) {
        viewModelScope.launch {
            try {
                db.collection("items").document(itemId).update("sold", true).await()
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun markAsUnsold(itemId: String) {
        viewModelScope.launch {
            try {
                db.collection("items").document(itemId).update("sold", false).await()
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun deleteItem(itemId: String, onDone: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val item = _items.value.find { it.id == itemId }
                    ?: _userItems.value.find { it.id == itemId }

                // Delete all images from Storage
                val urls = item?.imageUrls?.filter { it.isNotEmpty() }?.toMutableList() ?: mutableListOf()
                if (item?.imageUrl?.isNotEmpty() == true && !urls.contains(item.imageUrl)) {
                    urls.add(item.imageUrl)
                }
                urls.forEach { url ->
                    try { storage.getReferenceFromUrl(url).delete().await() } catch (_: Exception) {}
                }

                db.collection("items").document(itemId).delete().await()
                withContext(Dispatchers.Main) { onDone() }
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun getItemById(itemId: String): Item? = _items.value.find { it.id == itemId }
}
