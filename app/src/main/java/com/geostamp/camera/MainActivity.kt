package com.geostamp.camera

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.geostamp.camera.data.model.CapturedPhoto
import com.geostamp.camera.ui.camera.CameraScreen
import com.geostamp.camera.ui.camera.CameraViewModel
import com.geostamp.camera.ui.gallery.GalleryScreen
import com.geostamp.camera.ui.gallery.PhotoDetailScreen
import com.geostamp.camera.ui.privacy.PrivacyScreen
import com.geostamp.camera.ui.settings.SettingsScreen
import com.geostamp.camera.ui.theme.GPSMapCameraTheme
import com.geostamp.camera.ui.theme.OnyxBackground

sealed interface Screen {
    object Camera : Screen
    object Gallery : Screen
    data class PhotoDetail(val photo: CapturedPhoto) : Screen
    object Settings : Screen
    object Privacy : Screen
}

class MainActivity : ComponentActivity() {

    private val cameraViewModel: CameraViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            GPSMapCameraTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = OnyxBackground
                ) {
                    AppNavigation(cameraViewModel)
                }
            }
        }
    }
}

@Composable
fun AppNavigation(cameraViewModel: CameraViewModel) {
    var backstack by remember { mutableStateOf(listOf<Screen>(Screen.Camera)) }
    val currentScreen = backstack.last()

    fun navigateTo(screen: Screen) {
        backstack = backstack + screen
    }

    fun navigateBack() {
        if (backstack.size > 1) {
            backstack = backstack.dropLast(1)
        }
    }

    BackHandler(enabled = backstack.size > 1) {
        navigateBack()
    }

    when (val screen = currentScreen) {
        is Screen.Camera -> {
            CameraScreen(
                viewModel = cameraViewModel,
                onNavigateToGallery = { navigateTo(Screen.Gallery) },
                onNavigateToSettings = { navigateTo(Screen.Settings) },
                onOpenPhotoDetail = { photo -> navigateTo(Screen.PhotoDetail(photo)) }
            )
        }
        is Screen.Gallery -> {
            GalleryScreen(
                onNavigateBack = { navigateBack() },
                onPhotoClick = { photo -> navigateTo(Screen.PhotoDetail(photo)) }
            )
        }
        is Screen.PhotoDetail -> {
            PhotoDetailScreen(
                photo = screen.photo,
                onNavigateBack = { navigateBack() }
            )
        }
        is Screen.Settings -> {
            SettingsScreen(
                onNavigateBack = { navigateBack() },
                onNavigateToPrivacy = { navigateTo(Screen.Privacy) }
            )
        }
        is Screen.Privacy -> {
            PrivacyScreen(
                onNavigateBack = { navigateBack() }
            )
        }
    }
}
