package com.matuncnn.app.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.IntentCompat
import com.matuncnn.app.MatUnCnnApp
import com.matuncnn.app.data.AppSettings
import com.matuncnn.app.data.SettingsRepository
import com.matuncnn.app.processor.CommandListManager
import com.matuncnn.app.processor.ImageProcessor
import com.matuncnn.app.processor.NaturalBlendProcessor
import com.matuncnn.app.util.AssetsCopyer
import com.matuncnn.app.util.AssetsDownloader
import com.matuncnn.app.util.DebugLog
import com.matuncnn.app.util.DownloadProgress
import com.matuncnn.app.util.ProgressLogHelper
import com.matuncnn.app.util.UpscaleCache
import com.matuncnn.app.util.UriUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

data class MainUiState(
    val isInitialized: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val commandManager: CommandListManager? = null,
    val selectedCommandIndex: Int = 0,
    val inputFilePath: String? = null,
    val inputUri: Uri? = null,
    val outputFilePath: String? = null,
    val isProcessing: Boolean = false,
    val logText: String = "",
    val progressText: String = "",
    val statusMessage: String = "",
    val inputImageExists: Boolean = false,
    val outputImageExists: Boolean = false,
    val inputFileName: String = "",
    val showCommandInput: Boolean = false,
    val commandText: String = "",
    val isMultipleFiles: Boolean = false,
    val selectedUris: List<Uri> = emptyList(),
    val downloadProgress: DownloadProgress? = null,
    val scaleText: String = ""
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)
    private val imageProcessor = ImageProcessor()
    private val progressLogHelper = ProgressLogHelper()

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val settingsFlow = settingsRepo.settingsFlow

    private val assetsReady = AtomicBoolean(false)

    init {
        viewModelScope.launch {
            initializeApp()
        }
    }

    private suspend fun initializeApp() {
        if (assetsReady.get()) return

        val context = getApplication<Application>()
        val app = getApplication<MatUnCnnApp>()

        val ok = withContext(Dispatchers.IO) {
            app.ensureWorkDir()
            DebugLog.log("Init", "Work dir: ${app.workDir}")
            AssetsCopyer.releaseAssets(context, "realsr", app.workDir, true)

            if (AssetsDownloader.needsDownload(app.workDir)) {
                DebugLog.log("Init", "Assets download needed, starting...")
                val result = AssetsDownloader.downloadAndExtract(app.workDir) { progress ->
                    _uiState.update { it.copy(downloadProgress = progress) }
                }
                _uiState.update { it.copy(downloadProgress = null) }
                if (result.isFailure) {
                    DebugLog.log("Init", "Asset download failed")
                    return@withContext false
                }
                DebugLog.log("Init", "Asset download complete")
            }

            DebugLog.log("Init", "Copying bundled assets...")
            AssetsCopyer.releaseAssets(context, "realsr", app.workDir, true)
            pruneLegacyAssets(app.workDir)
            DebugLog.log("Init", "Assets ready")
            true
        }
        if (!ok) return

        val settings = settingsRepo.settingsFlow.first()
        val manager = withContext(Dispatchers.Default) {
            val classicalFilters = settings.classicalFilters.split(",")
                .map { it.trim() }.filter { it.isNotBlank() }.toTypedArray()
            val magickFilters = settings.magickFilters.split(",")
                .map { it.trim() }.filter { it.isNotBlank() }.toTypedArray()

            val labels = context.resources.getStringArray(
                com.matuncnn.app.R.array.style_array
            )

            CommandListManager(
                presetLabels = labels,
                extraPath = settings.extraPath,
                extraCommand = settings.extraCommand,
                classicalFilters = classicalFilters,
                magickFilters = magickFilters
            ).also { it.loadCustomLabels(settings.customLabelsJson) }
        }

        val selectedIndex = settings.selectCommand
            .takeIf { it in 0 until manager.commandCount } ?: 1
        val defaultCommand = manager.getCommandAt(selectedIndex)

        assetsReady.set(true)

        _uiState.update {
            it.copy(
                isInitialized = true,
                settings = settings,
                commandManager = manager,
                selectedCommandIndex = selectedIndex,
                showCommandInput = settings.showSearchView,
                commandText = defaultCommand
            )
        }
    }

    private fun pruneLegacyAssets(workDir: String) {
        val obsolete = listOf(
            "models-ESRGAN-Nomos8kSC", "models-MNN", "models-nose", "models-pro",
            "models-Real-ESRGAN-anime", "models-Real-ESRGANv2-anime", "models-se",
            "Anime4k", "GetMNNInfo", "libMNN.so", "libMNN_CL.so",
            "libMNN_Express.so", "libMNN_GL.so", "libMNN_Vulkan.so", "libmnncore.so",
            "magick", "mnnsr-ncnn", "realcugan-ncnn", "resize-ncnn", "srmd-ncnn",
            "waifu2x-ncnn", "models-Real-ESRGAN-SourceBook",
            "models-Real-ESRGANv3-anime", "models-Real-ESRGANv3-general"
        )
        obsolete.forEach { relativePath ->
            val target = File(workDir, relativePath)
            if (target.exists()) target.deleteRecursively()
        }
    }

    fun handleSendIntent(intent: Intent?) {
        if (intent == null) return

        when {
            intent.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true -> {
                val imageUri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                if (imageUri != null) {
                    setInputImage(imageUri)
                }
            }
            intent.action == Intent.ACTION_SEND_MULTIPLE && intent.type?.startsWith("image/") == true -> {
                val uris = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                if (!uris.isNullOrEmpty()) {
                    _uiState.update {
                        it.copy(isMultipleFiles = true, selectedUris = uris)
                    }
                    setInputImage(uris.first())
                }
            }
        }
    }

    fun setInputImage(uri: Uri) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val fileName = UriUtils.getFileName(uri, context) ?: "image"

            _uiState.update {
                it.copy(
                    inputUri = uri,
                    inputFilePath = uri.toString(),
                    inputFileName = fileName,
                    inputImageExists = true,
                    outputFilePath = null,
                    outputImageExists = false,
                    logText = "",
                    statusMessage = ""
                )
            }
        }
    }

    fun selectCommand(index: Int) {
        val manager = _uiState.value.commandManager ?: return
        val cmd = manager.getCommandAt(index)
        _uiState.update {
            it.copy(
                selectedCommandIndex = index,
                commandText = cmd
            )
        }
        viewModelScope.launch {
            settingsRepo.update { it.copy(selectCommand = index) }
        }
    }

    fun updateCommandText(text: String) {
        _uiState.update { it.copy(commandText = text) }
    }

    fun toggleCommandInput() {
        _uiState.update { it.copy(showCommandInput = !it.showCommandInput) }
    }

    fun startProcessing() {
        val state = _uiState.value
        val inputFile = state.inputFilePath ?: return
        val cmd = state.commandText
        if (cmd.isBlank()) return

        viewModelScope.launch {
            val app = getApplication<MatUnCnnApp>()
            val context = getApplication<Application>()
            app.ensureWorkDir()

            val validationError = validateCommandAssets(cmd, app.workDir)
            if (validationError != null) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        statusMessage = validationError,
                        logText = validationError
                    )
                }
                return@launch
            }

            // Handle input file
            val inputForCommand: String
            if (state.inputUri != null) {
                val tempInput = File(app.workDir, "input.png")
                val copyResult = runCatching {
                    tempInput.delete()
                    val input = context.contentResolver.openInputStream(state.inputUri)
                        ?: error("Cannot open the selected image")
                    input.use {
                        FileOutputStream(tempInput).use { output -> it.copyTo(output) }
                    }
                    require(tempInput.length() > 0) { "The selected image is empty" }
                }
                if (copyResult.isFailure) {
                    _uiState.update {
                        it.copy(statusMessage = "Failed: ${copyResult.exceptionOrNull()?.message}")
                    }
                    return@launch
                }
                inputForCommand = tempInput.absolutePath
                DebugLog.log("Input", "Copied content:// to $inputForCommand")
            } else {
                inputForCommand = inputFile
            }

            val privatePictures = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                ?: context.filesDir
            val saveDir = File(privatePictures, "PhotoForge").absolutePath
            File(saveDir).mkdirs()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val outputFileName = "upscaled_${timestamp}.png"
            val outputPath = "$saveDir/$outputFileName"
            File(outputPath).delete()
            val aiStrength = listOf(0, 20, 35, 50)
                .getOrElse(state.selectedCommandIndex) { 35 }
            val aiOutputPath = File(app.workDir, "ai_${timestamp}.png").absolutePath
            val aiInputPath = File(app.workDir, "ai_input_${timestamp}.png").absolutePath
            val targetScale = runCatching {
                NaturalBlendProcessor.recommendedScale(inputForCommand)
            }.getOrElse { error ->
                _uiState.update { it.copy(statusMessage = "Failed: ${error.message}") }
                return@launch
            }
            val aiScale = when (targetScale) {
                4 -> 2
                2 -> 1
                else -> 1
            }

            _uiState.update { it.copy(scaleText = "x$targetScale") }

            // Inject -g -1 for CPU mode on Adreno
            // The bundled Vulkan path produces corrupted tiles on some Mali drivers.
            // The lightweight pipeline uses CPU inference for deterministic output.
            val cpuForced = isNcnnCommand(cmd)
            val selectedCmd = if (isNcnnCommand(cmd) && !cmd.contains(" -j ")) {
                "$cmd -j 1:4:2"
            } else cmd
            val preparedInput = if (aiStrength > 0) {
                val prepared = withContext(Dispatchers.IO) {
                    NaturalBlendProcessor.prepareAiInput(inputForCommand, aiInputPath, aiScale)
                }
                if (prepared.isFailure) {
                    _uiState.update { it.copy(statusMessage = "Failed: ${prepared.exceptionOrNull()?.message}") }
                    return@launch
                }
                aiInputPath
            } else inputForCommand
            val finalCmd = if (cpuForced) {
                val base = selectedCmd.replace("input.png", preparedInput)
                    .replace("output.png", aiOutputPath)
                "$base -g -1"
            } else {
                selectedCmd.replace("input.png", preparedInput)
                    .replace("output.png", aiOutputPath)
            }

            if (cpuForced) {
                DebugLog.log("Upscale", "Safe CPU inference enabled (-g -1)")
            }

            // Check cache
            val cacheKey = UpscaleCache.buildKey(
                (state.inputUri?.toString() ?: inputFile),
                "$cmd|strength=$aiStrength|aiScale=$aiScale",
                targetScale
            )
            val cached = UpscaleCache.get(cacheKey)
            if (cached != null && File(cached).exists()) {
                DebugLog.log("Cache", "HIT: $cacheKey -> $cached")
                _uiState.update {
                    it.copy(
                        outputFilePath = cached,
                        outputImageExists = true,
                        statusMessage = "Complete! (cached)"
                    )
                }
                if (state.settings.autoSave) saveOutputToGallery()
                return@launch
            }
            DebugLog.log("Cache", "MISS: $cacheKey")

            progressLogHelper.reset()
            var lastUiProgressUpdate = 0L
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    logText = "",
                    progressText = "",
                    statusMessage = "Processing...",
                    outputFilePath = outputPath
                )
            }

            if (aiStrength == 0) {
                val baselineResult = withContext(Dispatchers.IO) {
                    NaturalBlendProcessor.render(inputForCommand, null, outputPath, 0, targetScale)
                }
                val fileOk = baselineResult.isSuccess && File(outputPath).length() > 0
                if (fileOk) UpscaleCache.put(cacheKey, outputPath)
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        statusMessage = if (fileOk) "Complete!" else
                            "Failed: ${baselineResult.exceptionOrNull()?.message ?: "cannot create result"}",
                        outputImageExists = fileOk
                    )
                }
                if (fileOk && state.settings.autoSave) saveOutputToGallery()
                return@launch
            }

            imageProcessor.executeCommand(
                command = finalCmd,
                workingDir = app.workDir,
                callback = object : ImageProcessor.ProcessCallback {
                    override fun onProgress(line: String) {
                        progressLogHelper.appendLine(line)
                        val now = System.currentTimeMillis()
                        if (now - lastUiProgressUpdate < 250L) return
                        lastUiProgressUpdate = now
                        _uiState.update {
                            it.copy(
                                logText = progressLogHelper.displayText,
                                progressText = progressLogHelper.progressText
                            )
                        }
                    }

                    override fun onCompleted(result: String, success: Boolean) {
                        val blendResult = if (success) {
                            NaturalBlendProcessor.render(
                                inputPath = inputForCommand,
                                aiPath = aiOutputPath,
                                outputPath = outputPath,
                                aiStrength = aiStrength,
                                scale = targetScale
                            )
                        } else null
                        File(aiOutputPath).delete()
                        File(aiInputPath).delete()
                        val finalSuccess = success && blendResult?.isSuccess == true
                        if (finalSuccess) {
                            UpscaleCache.put(cacheKey, outputPath)
                            DebugLog.log("Cache", "Stored: $cacheKey -> $outputPath")
                        }
                        val outFile = File(outputPath)
                        val fileOk = outFile.exists() && outFile.length() > 0
                        if (!fileOk && success) {
                            DebugLog.log("Output", "File missing or empty: $outputPath")
                        } else if (fileOk) {
                            DebugLog.log("Output", "File OK: ${outFile.length()} bytes")
                        }
                        val summary = progressLogHelper.getCompletionSummary(
                            finalSuccess,
                            isNcnnCommand = true
                        )
                        val failureReason = result.lineSequence()
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .lastOrNull()
                            ?.take(160)
                        val blendFailure = blendResult?.exceptionOrNull()?.message
                        val cancelled = result.contains("Process cancelled")
                        _uiState.update {
                            it.copy(
                                isProcessing = false,
                                logText = progressLogHelper.displayText + summary,
                                statusMessage = if (cancelled) "Cancelled"
                                    else if (!finalSuccess) "Failed: ${blendFailure ?: failureReason ?: "unknown engine error"}"
                                    else if (!fileOk) "Failed: output file is empty"
                                    else "Complete!",
                                outputImageExists = !cancelled && finalSuccess && fileOk
                            )
                        }
                        if (finalSuccess && fileOk && state.settings.autoSave) {
                            saveOutputToGallery()
                        }
                    }

                    override fun onError(error: String) {
                        _uiState.update {
                            it.copy(
                                isProcessing = false,
                                statusMessage = "Error: $error"
                            )
                        }
                    }
                }
            )
        }
    }

    private fun validateCommandAssets(command: String, workDir: String): String? {
        val parts = command.trim().split("\\s+".toRegex())
        val binary = parts.firstOrNull()?.removePrefix("./")
            ?: return "Invalid processing command"
        if (!File(workDir, binary).isFile) {
            return "Engine is missing: $binary"
        }

        val modelIndex = parts.indexOf("-m")
        if (modelIndex >= 0 && modelIndex + 1 < parts.size) {
            val model = File(workDir, parts[modelIndex + 1])
            if (!model.exists()) return "Model is missing: ${model.name}"
            if (model.isDirectory) {
                val hasWeights = model.listFiles()?.any {
                    it.isFile && (it.extension.equals("bin", true) || it.extension.equals("mnn", true))
                } == true
                if (!hasWeights) return "Model weights are missing: ${model.name}"
            }
        }
        return null
    }

    fun cancelProcessing() {
        imageProcessor.cancelCurrentTask()
        _uiState.update {
            it.copy(
                isProcessing = false,
                statusMessage = "Cancelled"
            )
        }
    }

    fun shareOutput() {
        val path = _uiState.value.outputFilePath ?: return
        val context = getApplication<Application>()
        val file = File(path)
        if (!file.exists()) return

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share image"))
    }

    fun saveLogTo(uri: Uri) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(_uiState.value.logText.toByteArray())
                }
            } catch (e: Exception) {
                DebugLog.log("Error", "saveLogTo: ${e.message}")
                _uiState.update { it.copy(statusMessage = "Failed to save log") }
            }
        }
    }

    fun saveOutputTo(uri: Uri) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val path = _uiState.value.outputFilePath ?: return@launch
            try {
                val input = java.io.File(path).inputStream()
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    input.copyTo(out)
                }
                input.close()
                _uiState.update { it.copy(statusMessage = "Saved!") }
            } catch (e: Exception) {
                DebugLog.log("Error", "saveOutputTo: ${e.message}")
                _uiState.update { it.copy(statusMessage = "Failed to save") }
            }
        }
    }

    fun saveOutputToGallery() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val path = _uiState.value.outputFilePath ?: return@launch
            val file = File(path)
            if (!file.exists()) return@launch

            try {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, file.name)
                    put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        put(
                            android.provider.MediaStore.Images.Media.RELATIVE_PATH,
                            Environment.DIRECTORY_DCIM + File.separator + "PhotoForge"
                        )
                        put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }
                val uri = context.contentResolver.insert(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                )
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        file.inputStream().use { it.copyTo(out) }
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        values.clear()
                        values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                        context.contentResolver.update(uri, values, null, null)
                    }
                    _uiState.update { it.copy(statusMessage = "Saved to Gallery!") }
                }
            } catch (e: Exception) {
                DebugLog.log("Error", "saveOutputToGallery: ${e.message}")
                _uiState.update { it.copy(statusMessage = "Failed to save to gallery") }
            }
        }
    }

    fun updateSettings(newSettings: AppSettings) {
        _uiState.update { it.copy(settings = newSettings) }
        viewModelScope.launch {
            settingsRepo.update { newSettings }
            // Rebuild command manager
            val context = getApplication<Application>()
            val classicalFilters = newSettings.classicalFilters.split(",")
                .map { it.trim() }.filter { it.isNotBlank() }.toTypedArray()
            val magickFilters = newSettings.magickFilters.split(",")
                .map { it.trim() }.filter { it.isNotBlank() }.toTypedArray()
            val labels = context.resources.getStringArray(com.matuncnn.app.R.array.style_array)

            val manager = CommandListManager(
                presetLabels = labels,
                extraPath = newSettings.extraPath,
                extraCommand = newSettings.extraCommand,
                classicalFilters = classicalFilters,
                magickFilters = magickFilters
            ).also { it.loadCustomLabels(newSettings.customLabelsJson) }

            _uiState.update { it.copy(commandManager = manager) }
        }
    }

    fun retryDownload() {
        viewModelScope.launch {
            _uiState.update { it.copy(downloadProgress = DownloadProgress(message = "Retrying...")) }
            initializeApp()
        }
    }

    override fun onCleared() {
        super.onCleared()
        imageProcessor.shutdown()
    }

    companion object {
        private val NCNN_BINARIES = setOf(
            "realsr-ncnn", "realesrgan-ncnn", "reaalsr-ncnn",
            "srmd-ncnn", "waifu2x-ncnn", "realcugan-ncnn",
            "mnnsr-ncnn", "resize-ncnn", "anime4k"
        )

        private fun isNcnnCommand(cmd: String): Boolean {
            val name = cmd.trim().substringAfter("./").substringBefore(" ")
            return name in NCNN_BINARIES
        }
    }
}
