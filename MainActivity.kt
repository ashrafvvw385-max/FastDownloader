package com.fastdownloader.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FastDownloaderScreen() }
    }
}

class DownloaderViewModel : ViewModel() {
    var baseUrl by mutableStateOf("http://YOUR_VPS_IP/")
    var token by mutableStateOf("")
    var url by mutableStateOf("")
    var message by mutableStateOf("")
    var response by mutableStateOf(DownloadsResponse())
    var files by mutableStateOf(emptyList<FileItem>())

    private var api: Api? = null

    fun connect() {
        val normalized = baseUrl.trim().let {
            if (it.endsWith("/")) it else "$it/"
        }
        api = Retrofit.Builder()
            .baseUrl(normalized)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(Api::class.java)

        refresh()
    }

    fun refresh() {
        val service = api ?: return
        viewModelScope.launch {
            runCatching {
                response = service.downloads("Bearer ${token.trim()}")
                files = service.files("Bearer ${token.trim()}").files
            }.onFailure {
                message = "خطأ في الاتصال: ${it.message ?: "Unknown"}"
            }
        }
    }

    fun add() {
        val service = api ?: return
        if (url.isBlank()) return
        viewModelScope.launch {
            runCatching {
                service.add("Bearer ${token.trim()}", AddRequest(url.trim()))
                message = "تمت إضافة المهمة"
                url = ""
                refresh()
            }.onFailure {
                message = "فشل الإضافة: ${it.message ?: "Unknown"}"
            }
        }
    }

    fun pause(gid: String) {
        val service = api ?: return
        viewModelScope.launch {
            runCatching {
                service.pause("Bearer ${token.trim()}", gid)
                refresh()
            }
        }
    }

    fun resume(gid: String) {
        val service = api ?: return
        viewModelScope.launch {
            runCatching {
                service.resume("Bearer ${token.trim()}", gid)
                refresh()
            }
        }
    }

    fun remove(gid: String) {
        val service = api ?: return
        viewModelScope.launch {
            runCatching {
                service.remove("Bearer ${token.trim()}", gid)
                refresh()
            }
        }
    }

    fun startPolling() {
        viewModelScope.launch {
            while (true) {
                refresh()
                delay(3000)
            }
        }
    }
}

@Composable
fun FastDownloaderScreen() {
    val vm = remember { DownloaderViewModel() }
    LaunchedEffect(Unit) { vm.startPolling() }

    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("FastDownloader", style = MaterialTheme.typography.headlineMedium)

            OutlinedTextField(
                value = vm.baseUrl,
                onValueChange = { vm.baseUrl = it },
                label = { Text("عنوان VPS") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = vm.token,
                onValueChange = { vm.token = it },
                label = { Text("API Token") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.connect() }) { Text("اتصال") }
                OutlinedButton(onClick = { vm.refresh() }) { Text("تحديث") }
            }

            OutlinedTextField(
                value = vm.url,
                onValueChange = { vm.url = it },
                label = { Text("رابط الملف المباشر") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Button(
                onClick = { vm.add() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("بدء التحميل")
            }

            if (vm.message.isNotBlank()) Text(vm.message)

            Text("التحميلات", style = MaterialTheme.typography.titleLarge)

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(vm.response.active + vm.response.waiting + vm.response.stopped) { item ->
                    DownloadCard(item, vm)
                }

                item {
                    Text(
                        "الملفات الجاهزة",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }

                items(vm.files) { file ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(file.name, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(file.url))
                            // Browser/Download manager can handle the file URL.
                        }) {
                            Text("الرابط")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadCard(item: DownloadItem, vm: DownloaderViewModel) {
    val total = item.totalLength.toDoubleOrNull() ?: 0.0
    val done = item.completedLength.toDoubleOrNull() ?: 0.0
    val progress = if (total > 0) (done / total).coerceIn(0.0, 1.0).toFloat() else 0f

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("GID: ${item.gid}")
        Text("الحالة: ${item.status}")
        Text("السرعة: ${item.downloadSpeed} B/s")
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (item.status == "active") {
                OutlinedButton(onClick = { vm.pause(item.gid) }) {
                    Text("إيقاف مؤقت")
                }
            } else if (item.status == "paused") {
                OutlinedButton(onClick = { vm.resume(item.gid) }) {
                    Text("استئناف")
                }
            }

            OutlinedButton(onClick = { vm.remove(item.gid) }) {
                Text("حذف")
            }
        }
    }
}
