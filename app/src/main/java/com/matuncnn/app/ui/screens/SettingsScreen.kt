package com.matuncnn.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.LocaleList
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.matuncnn.app.BuildConfig
import com.matuncnn.app.data.AppSettings
import com.matuncnn.app.util.DebugLog
import com.matuncnn.app.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            stringResource(com.matuncnn.app.R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp)
        )
        GeneralTab(state.settings, viewModel)
    }
}

// ─── General Tab ────────────────────────────────────────────────

@Composable
private fun GeneralTab(settings: AppSettings, viewModel: MainViewModel) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "language_simple") {
            Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(com.matuncnn.app.R.string.language_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    val tags = listOf("", "ru", "en", "de", "es", "zh")
                    val savedTag = context.getSharedPreferences("photoforge_language", Context.MODE_PRIVATE)
                        .getString("tag", "") ?: ""
                    SettingDropdown(
                        listOf(
                            stringResource(com.matuncnn.app.R.string.lang_system),
                            "Русский", "English", "Deutsch", "Español", "中文"
                        ),
                        tags.indexOf(savedTag).coerceAtLeast(0)
                    ) { index ->
                        val tag = tags[index]
                        context.getSharedPreferences("photoforge_language", Context.MODE_PRIVATE)
                            .edit().putString("tag", tag).apply()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            context.getSystemService(android.app.LocaleManager::class.java)
                                .applicationLocales = LocaleList.forLanguageTags(tag)
                        } else {
                            val locale = if (tag.isBlank()) java.util.Locale.getDefault()
                                else java.util.Locale.forLanguageTag(tag)
                            java.util.Locale.setDefault(locale)
                            val config = context.resources.configuration
                            config.setLocale(locale)
                            context.resources.updateConfiguration(config, context.resources.displayMetrics)
                            (context as? android.app.Activity)?.recreate()
                        }
                    }
                }
            }
        }
        item(key = "performance_simple") {
            Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(com.matuncnn.app.R.string.performance_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = settings.tileSize.toString(),
                        onValueChange = { value ->
                            viewModel.updateSettings(settings.copy(tileSize = value.toIntOrNull() ?: 0))
                        },
                        label = { Text(stringResource(com.matuncnn.app.R.string.tile_size_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    SettingToggle(stringResource(com.matuncnn.app.R.string.auto_save_results), settings.autoSave) {
                        viewModel.updateSettings(settings.copy(autoSave = it))
                    }
                    SettingToggle(stringResource(com.matuncnn.app.R.string.cpu_mode), settings.useCPU) {
                        viewModel.updateSettings(settings.copy(useCPU = it))
                    }
                }
            }
        }
        item(key = "about_simple") {
            Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PhotoForge", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("v${BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/OlegBawq"))) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(com.matuncnn.app.R.string.github_button)) }
                    OutlinedButton(
                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/niix3"))) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(com.matuncnn.app.R.string.telegram_button)) }
                    OutlinedButton(
                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/OlegBawq/PhotoUpscaling"))) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(com.matuncnn.app.R.string.source_button)) }
                }
            }
        }
    }
}

@Composable
private fun GeneralTabLegacy(settings: AppSettings, viewModel: MainViewModel) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "language") {
            Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Язык / Language", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    val selectedLanguage = remember {
                        when (context.getSharedPreferences("photoforge_language", Context.MODE_PRIVATE)
                            .getString("tag", "")) {
                            "ru" -> 1
                            "en" -> 2
                            else -> 0
                        }
                    }
                    SettingDropdown(
                        listOf("Системный / System", "Русский", "English"),
                        selectedLanguage
                    ) { index ->
                        val tag = listOf("", "ru", "en")[index]
                        context.getSharedPreferences("photoforge_language", Context.MODE_PRIVATE)
                            .edit().putString("tag", tag).apply()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            context.getSystemService(android.app.LocaleManager::class.java)
                                .applicationLocales = LocaleList.forLanguageTags(tag)
                        } else {
                            val locale = if (tag.isBlank()) java.util.Locale.getDefault()
                                else java.util.Locale.forLanguageTag(tag)
                            java.util.Locale.setDefault(locale)
                            val config = context.resources.configuration
                            config.setLocale(locale)
                            context.resources.updateConfiguration(config, context.resources.displayMetrics)
                            (context as? android.app.Activity)?.recreate()
                        }
                    }
                }
            }
        }

        item(key = "tile_size") {
            Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Tile Size", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = settings.tileSize.toString(),
                        onValueChange = { v ->
                            val n = v.toIntOrNull() ?: 0
                            viewModel.updateSettings(settings.copy(tileSize = n))
                        },
                        label = { Text("Tile size (0 = auto)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        }

        item(key = "extra_args") {
            Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Custom Arguments", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = settings.extraCommand,
                        onValueChange = { viewModel.updateSettings(settings.copy(extraCommand = it)) },
                        label = { Text("Extra command args (one per line)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2, maxLines = 4
                    )
                }
            }
        }

        item(key = "toggles") {
            Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Preferences", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    SettingToggle("Keep screen on", settings.keepScreen) {
                        viewModel.updateSettings(settings.copy(keepScreen = it))
                    }
                    SettingToggle("Preprocess to PNG", settings.prePng) {
                        viewModel.updateSettings(settings.copy(prePng = it))
                    }
                    SettingToggle("Auto-save to gallery", settings.autoSave) {
                        viewModel.updateSettings(settings.copy(autoSave = it))
                    }
                    SettingToggle("CPU mode (NCNN)", settings.useCPU) {
                        viewModel.updateSettings(settings.copy(useCPU = it))
                    }
                    SettingToggle("Show command input", settings.showSearchView) {
                        viewModel.updateSettings(settings.copy(showSearchView = it))
                    }
                    SettingToggle("Show final command", settings.showFinalCommand) {
                        viewModel.updateSettings(settings.copy(showFinalCommand = it))
                    }
                    SettingToggle("Use custom labels", settings.useCustomLabel) {
                        viewModel.updateSettings(settings.copy(useCustomLabel = it))
                    }
                }
            }
        }

        item(key = "processing") {
            Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Processing", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = settings.threadCount,
                        onValueChange = { viewModel.updateSettings(settings.copy(threadCount = it)) },
                        label = { Text("Thread count (load:proc:save)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Save format", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    SettingDropdown(
                        listOf("PNG", "JPG", "WEBP", "BMP"),
                        settings.format.coerceIn(0, 3)
                    ) { viewModel.updateSettings(settings.copy(format = it)) }
                    Spacer(Modifier.height(12.dp))
                    Text("Naming scheme", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    SettingDropdown(
                        listOf("Original", "Timestamp", "Sequential"),
                        settings.name.coerceIn(0, 2)
                    ) { viewModel.updateSettings(settings.copy(name = it)) }
                    Spacer(Modifier.height(12.dp))
                    Text("Notification mode", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    SettingDropdown(
                        listOf("Silent", "Result Only", "Detailed", "Auto Dismiss"),
                        settings.notify.coerceIn(0, 3)
                    ) { viewModel.updateSettings(settings.copy(notify = it)) }
                }
            }
        }

        item(key = "paths") {
            Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Paths", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = settings.extraPath,
                        onValueChange = { viewModel.updateSettings(settings.copy(extraPath = it)) },
                        label = { Text("Custom model path") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = settings.savePath,
                        onValueChange = { viewModel.updateSettings(settings.copy(savePath = it)) },
                        label = { Text("Save path") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        }

        item(key = "commands") {
            Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Commands", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = settings.defaultCommand,
                        onValueChange = { viewModel.updateSettings(settings.copy(defaultCommand = it)) },
                        label = { Text("Default command") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = settings.classicalFilters,
                        onValueChange = { viewModel.updateSettings(settings.copy(classicalFilters = it)) },
                        label = { Text("Classical interpolation filters") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = settings.magickFilters,
                        onValueChange = { viewModel.updateSettings(settings.copy(magickFilters = it)) },
                        label = { Text("Magick filters") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        }

        item(key = "footer") {
            val ctx = LocalContext.current
            Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Person, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("PhotoForge", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Android · v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW,
                                    Uri.parse("https://github.com/OlegBawq"))
                                ctx.startActivity(intent)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Person, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Мой GitHub")
                        }
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW,
                                    Uri.parse("https://t.me/niix3"))
                                ctx.startActivity(intent)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Send, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Telegram")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/OlegBawq/PhotoUpscaling"))
                            ctx.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Code, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Исходный код PhotoForge")
                    }
                }
            }
        }

        item(key = "spacer") { Spacer(Modifier.height(16.dp)) }
    }
}

// ─── Debug Tab ──────────────────────────────────────────────────

@Composable
private fun DebugTab() {
    val context = LocalContext.current
    val logs = DebugLog.logs

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val clip = ClipData.newPlainText("DebugLog", DebugLog.dump())
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.ContentCopy, null, Modifier.padding(end = 4.dp))
                Text("Copy")
            }
            OutlinedButton(
                onClick = { DebugLog.saveTo(context.getExternalFilesDir(null)!!) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.Download, null, Modifier.padding(end = 4.dp))
                Text("Save")
            }
        }

        Box(
            modifier = Modifier.fillMaxWidth().weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
            ) {
                if (logs.isEmpty()) {
                    Text("No log entries yet.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)

                } else {
                    logs.forEach { entry ->
                        Text(entry, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 1.dp))
                    }
                }
            }
        }
    }
}

// ─── Shared Components ──────────────────────────────────────────

@Composable
private fun SettingToggle(label: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChanged)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingDropdown(options: List<String>, selectedIndex: Int, onSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = options.getOrElse(selectedIndex) { "" },
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            singleLine = true
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { onSelected(index); expanded = false }
                )
            }
        }
    }
}
