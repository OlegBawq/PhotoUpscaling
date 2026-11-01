package com.matuncnn.app.ui.navigation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.matuncnn.app.BuildConfig
import com.matuncnn.app.ui.screens.DownloadScreen
import com.matuncnn.app.ui.screens.HomeScreen
import com.matuncnn.app.ui.screens.SettingsScreen
import com.matuncnn.app.ui.theme.MatUnCnnTheme
import com.matuncnn.app.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { mainViewModel.setInputImage(it) }
    }

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // Permission granted
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestStoragePermissions()
        handleIntent(intent)

        setContent {
            MatUnCnnTheme {
                MatUnCnnNavHost(
                    mainViewModel = mainViewModel,
                    onLaunchImagePicker = { launchImagePicker() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        when {
            intent.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true -> {
                val imageUri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                if (imageUri != null) {
                    mainViewModel.setInputImage(imageUri)
                }
            }
            intent.action == Intent.ACTION_SEND_MULTIPLE && intent.type?.startsWith("image/") == true -> {
                val uris = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                if (!uris.isNullOrEmpty()) {
                    mainViewModel.setInputImage(uris.first())
                }
            }
        }
    }

    private fun requestStoragePermissions() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    private fun launchImagePicker() {
        imagePickerLauncher.launch("image/*")
    }
}

@Composable
fun MatUnCnnNavHost(
    mainViewModel: MainViewModel,
    onLaunchImagePicker: () -> Unit
) {
    val navController = rememberNavController()

    val mainState by mainViewModel.uiState.collectAsState()
    if (mainState.downloadProgress != null) {
        DownloadScreen(
            progress = mainState.downloadProgress!!,
            onRetry = { mainViewModel.retryDownload() }
        )
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                Screen.items.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                contentDescription = stringResource(screen.titleRes)
                            )
                        },
                        label = { if (selected) Text(stringResource(screen.titleRes)) else null },
                        selected = selected,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        var showBeta by remember { mutableStateOf(false) }
        if (showBeta) {
            AlertDialog(
                onDismissRequest = { showBeta = false },
                title = { Text("PhotoForge") },
                text = {
                    Text("PhotoForge v${BuildConfig.VERSION_NAME}\n\nУлучшение фотографий на устройстве.")
                },
                confirmButton = {
                    TextButton(onClick = { showBeta = false }) { Text("Понятно") }
                }
            )
        }
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    viewModel = mainViewModel,
                    onLaunchImagePicker = onLaunchImagePicker
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(viewModel = mainViewModel)
            }
        }
    }
}
