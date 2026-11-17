package com.example

import android.opengl.GLSurfaceView
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.attemptAutoSignIn
import com.example.auth.authStateFlow
import com.example.auth.signOutUser
import com.example.data.SurvivorSaveRepository
import com.example.game.audio.SoundEngine
import com.example.game.render.SurvivalGlRenderer
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DeathOverlay
import com.example.ui.screens.HudOverlay
import com.example.ui.screens.InventoryOverlay
import com.example.ui.screens.PauseMenuOverlay
import com.example.ui.screens.TitleDeployOverlay
import com.example.ui.theme.PovSurvivalTheme
import com.example.ui.viewmodel.SurvivalViewModel
import com.example.update.UpdateDialog
import com.example.update.UpdateRepository
import com.example.update.UpdateScreen
import com.example.update.UpdateViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    controller.hide(WindowInsetsCompat.Type.systemBars())

    setContent {
      PovSurvivalTheme {
        AppNavigation()
      }
    }
  }
}

@Composable
fun AppNavigation() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember { CredentialManager.create(context) }
  val currentUser by Firebase.auth.authStateFlow().collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

  val updateViewModel: UpdateViewModel =
    viewModel(
      factory =
        viewModelFactory {
          initializer {
            val app = checkNotNull(this[APPLICATION_KEY]) { "APPLICATION_KEY missing" }
            UpdateViewModel(UpdateRepository(app.applicationContext))
          }
        },
    )
  val updateState by updateViewModel.updateState.collectAsStateWithLifecycle()
  val showUpdatePopup by updateViewModel.showPopupDialog.collectAsStateWithLifecycle()
  var showUpdateScreen by rememberSaveable { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    updateViewModel.checkForUpdates(force = false, showDialogIfAvailable = true)
    attemptAutoSignIn(
      context = context,
      credentialManager = credentialManager,
      onAuthSuccess = {},
      onUnauthenticated = {},
      scope = scope,
    )
  }

  val user = currentUser
  Box(modifier = Modifier.fillMaxSize()) {
    if (showUpdateScreen) {
      UpdateScreen(
        viewModel = updateViewModel,
        onBack = { showUpdateScreen = false },
      )
    } else if (user == null) {
      AuthScreen(onAuthSuccess = {})
    } else {
      val uid = user.uid
      val displayName = user.displayName ?: user.email?.substringBefore('@') ?: "Survivor"
      key(uid) {
        PovSurvivalGameScreen(
          currentUserId = uid,
          displayName = displayName,
          onOpenUpdates = { showUpdateScreen = true },
          onSignOut = {
            signOutUser(credentialManager, scope)
          },
        )
      }
    }

    if (showUpdatePopup && !showUpdateScreen) {
      UpdateDialog(
        state = updateState,
        onUpdateNow = { updateViewModel.startUpdateDownload() },
        onLater = { updateViewModel.dismissUpdateDialog() },
        onInstallNow = { updateViewModel.launchSystemInstaller() },
        onRetry = { updateViewModel.retry() },
        onCheckNow = { updateViewModel.checkForUpdates(force = true, showDialogIfAvailable = true) },
        buildPermissionIntent = { updateViewModel.buildPermissionSettingsIntent() },
        onReturnedFromPermissionSettings = { updateViewModel.onReturnedFromPermissionSettings() },
      )
    }
  }
}

@Composable
fun PovSurvivalGameScreen(
  currentUserId: String,
  displayName: String,
  onOpenUpdates: () -> Unit,
  onSignOut: () -> Unit,
) {
  val viewModel: SurvivalViewModel =
    viewModel(
      key = currentUserId,
      factory =
        viewModelFactory {
          initializer {
            val app = checkNotNull(this[APPLICATION_KEY]) { "APPLICATION_KEY missing" }
            val databaseId = app.getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(databaseId)
            val repo = SurvivorSaveRepository(db)
            val sound = SoundEngine(app.applicationContext)
            SurvivalViewModel(repo, currentUserId, displayName, sound)
          }
        },
    )

  val hudState by viewModel.hudState.collectAsStateWithLifecycle()
  val showTitle by viewModel.showTitleMenu.collectAsStateWithLifecycle()
  val showInventory by viewModel.showInventory.collectAsStateWithLifecycle()
  val showPause by viewModel.showPauseMenu.collectAsStateWithLifecycle()
  val cloudSaveState by viewModel.cloudSaveState.collectAsStateWithLifecycle()
  val cloudStatus by viewModel.cloudStatusMessage.collectAsStateWithLifecycle()
  val isMuted by viewModel.isMuted.collectAsStateWithLifecycle()

  BackHandler(enabled = showInventory || showPause) {
    if (showInventory) {
      viewModel.toggleInventory()
    } else if (showPause) {
      viewModel.togglePauseMenu()
    }
  }

  Box(modifier = Modifier.fillMaxSize()) {
    // 3D OpenGL ES 3.0 Viewport
    AndroidView(
      factory = { ctx ->
        GLSurfaceView(ctx).apply {
          setEGLContextClientVersion(3)
          setEGLConfigChooser(8, 8, 8, 8, 16, 0)
          preserveEGLContextOnPause = true
          setRenderer(
            SurvivalGlRenderer(
              engine = viewModel.engine,
              onHudSnapshot = { viewModel.refreshHudSnapshot() },
            )
          )
          renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
      },
      modifier = Modifier.fillMaxSize(),
    )

    // Active Gameplay HUD
    if (!showTitle && !hudState.isDead) {
      HudOverlay(
        state = hudState,
        engine = viewModel.engine,
        world = viewModel.world,
        onOpenBag = { viewModel.toggleInventory() },
        onOpenPause = { viewModel.togglePauseMenu() },
        onToggleDayNight = { viewModel.toggleDayNight() },
        onToggleFlashlight = { viewModel.toggleFlashlight() },
      )
    }

    // 24-Slot Inventory + Paper Doll Modal
    if (showInventory && !showTitle && !hudState.isDead) {
      InventoryOverlay(
        state = hudState,
        onClose = { viewModel.toggleInventory() },
        onUseSlot = { viewModel.useBackpackSlot(it) },
        onDropSlot = { viewModel.dropBackpackSlot(it) },
        onAssignHotbar = { bp, hb -> viewModel.assignBackpackToHotbar(bp, hb) },
      )
    }

    // Pause Menu
    if (showPause && !showTitle && !hudState.isDead) {
      PauseMenuOverlay(
        state = hudState,
        cloudStatus = cloudStatus,
        isMuted = isMuted,
        onResume = { viewModel.togglePauseMenu() },
        onSaveCloud = { viewModel.saveProgressToCloud() },
        onLoadCloud = { viewModel.resumeWithCloudSave() },
        onToggleDayNight = { viewModel.toggleDayNight() },
        onToggleMute = { viewModel.toggleMute() },
        onRestart = { viewModel.startNewRun() },
        onOpenUpdates = onOpenUpdates,
        onSignOut = onSignOut,
      )
    }

    // Death Screen
    if (hudState.isDead && !showTitle) {
      DeathOverlay(
        state = hudState,
        onRetry = { viewModel.startNewRun() },
        onLoadCloud = { viewModel.resumeWithCloudSave() },
      )
    }

    // Title / Deploy Overlay
    if (showTitle) {
      TitleDeployOverlay(
        displayName = displayName,
        cloudSaveState = cloudSaveState,
        onStartNewRun = { viewModel.startNewRun() },
        onResumeCloudSave = { viewModel.resumeWithCloudSave() },
        onOpenUpdates = onOpenUpdates,
        onSignOut = onSignOut,
      )
    }
  }
}
