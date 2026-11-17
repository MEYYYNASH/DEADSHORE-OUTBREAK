package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SurvivorSave
import com.example.data.SurvivorSaveRepository
import com.example.game.audio.SoundEngine
import com.example.game.simulation.GameEngine
import com.example.game.simulation.HudUiState
import com.example.game.world.TownWorld
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface CloudSaveUiState {
  data object Loading : CloudSaveUiState
  data class Loaded(val save: SurvivorSave?) : CloudSaveUiState
  data class Error(val message: String) : CloudSaveUiState
}

class SurvivalViewModel(
  private val repository: SurvivorSaveRepository,
  private val currentUserId: String,
  private val displayName: String,
  private val soundEngine: SoundEngine,
) : ViewModel() {

  val world = TownWorld()
  val engine = GameEngine(world, soundEngine)

  private val _hudState = MutableStateFlow(engine.snapshotHudState())
  val hudState: StateFlow<HudUiState> = _hudState.asStateFlow()

  private val _showTitleMenu = MutableStateFlow(true)
  val showTitleMenu: StateFlow<Boolean> = _showTitleMenu.asStateFlow()

  private val _showInventory = MutableStateFlow(false)
  val showInventory: StateFlow<Boolean> = _showInventory.asStateFlow()

  private val _showPauseMenu = MutableStateFlow(false)
  val showPauseMenu: StateFlow<Boolean> = _showPauseMenu.asStateFlow()

  private val _cloudStatusMessage = MutableStateFlow<String?>(null)
  val cloudStatusMessage: StateFlow<String?> = _cloudStatusMessage.asStateFlow()

  private val _isMuted = MutableStateFlow(false)
  val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

  val cloudSaveState: StateFlow<CloudSaveUiState> =
    repository
      .observeSave(currentUserId)
      .map<SurvivorSave?, CloudSaveUiState> { CloudSaveUiState.Loaded(it) }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing cloud save", rootError)
        emit(CloudSaveUiState.Error(rootError.message ?: "Failed to sync cloud save"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = CloudSaveUiState.Loading,
      )

  init {
    engine.isPaused = true
    engine.setOnWaveClearedListener {
      saveProgressSilently()
    }
  }

  fun refreshHudSnapshot() {
    _hudState.value = engine.snapshotHudState()
  }

  fun startNewRun() {
    engine.resetNewRun()
    engine.isPaused = false
    _showTitleMenu.value = false
    _showInventory.value = false
    _showPauseMenu.value = false
    refreshHudSnapshot()
  }

  fun resumeWithCloudSave() {
    val loaded = (cloudSaveState.value as? CloudSaveUiState.Loaded)?.save
    if (loaded != null) {
      engine.applyCloudSave(loaded)
    } else {
      engine.resetNewRun()
    }
    engine.isPaused = false
    _showTitleMenu.value = false
    _showInventory.value = false
    _showPauseMenu.value = false
    refreshHudSnapshot()
  }

  fun saveProgressToCloud() {
    viewModelScope.launch {
      _cloudStatusMessage.value = "Saving backpack & progress..."
      val save = engine.toSurvivorSave(currentUserId, displayName)
      val result = repository.saveProgress(save)
      _cloudStatusMessage.value =
        if (result.isSuccess) {
          engine.showToast("Cloud Save Updated!")
          refreshHudSnapshot()
          "Saved Wave ${save.wave} & ${save.backpackItems.size} backpack stacks"
        } else {
          "Cloud save failed: ${result.exceptionOrNull()?.localizedMessage ?: "Unknown error"}"
        }
    }
  }

  private fun saveProgressSilently() {
    viewModelScope.launch {
      val save = engine.toSurvivorSave(currentUserId, displayName)
      repository.saveProgress(save)
    }
  }

  fun toggleInventory() {
    val next = !_showInventory.value
    _showInventory.value = next
    _showPauseMenu.value = false
    engine.isPaused = next || _showPauseMenu.value || _showTitleMenu.value
    if (next) {
      engine.fireHeld = false
      engine.adsActive = false
    }
    refreshHudSnapshot()
  }

  fun togglePauseMenu() {
    val next = !_showPauseMenu.value
    _showPauseMenu.value = next
    _showInventory.value = false
    engine.isPaused = next || _showTitleMenu.value
    if (next) {
      engine.fireHeld = false
      engine.adsActive = false
    }
    refreshHudSnapshot()
  }

  fun toggleDayNight() {
    engine.dayMode = !engine.dayMode
    engine.showToast(if (engine.dayMode) "Coastal Day Mode" else "Coastal Night Mode")
    refreshHudSnapshot()
  }

  fun toggleFlashlight() {
    engine.flashlightOn = !engine.flashlightOn
    refreshHudSnapshot()
  }

  fun toggleMute() {
    val next = !_isMuted.value
    _isMuted.value = next
    soundEngine.muted = next
  }

  fun useBackpackSlot(slotIndex: Int) {
    engine.useBackpackSlot(slotIndex)
    refreshHudSnapshot()
  }

  fun dropBackpackSlot(slotIndex: Int) {
    engine.dropBackpackSlot(slotIndex)
    refreshHudSnapshot()
  }

  fun assignBackpackToHotbar(backpackSlot: Int, hotbarIndex: Int) {
    engine.assignBackpackItemToHotbar(backpackSlot, hotbarIndex)
    refreshHudSnapshot()
  }

  fun clearCloudStatus() {
    _cloudStatusMessage.value = null
  }

  override fun onCleared() {
    super.onCleared()
    soundEngine.release()
  }

  private companion object {
    const val TAG = "SurvivalVM"
    const val STOP_TIMEOUT_MILLIS = 5000L
  }
}
