package com.example.update

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class UpdateViewModel(
  private val repository: UpdateRepository,
) : ViewModel() {

  private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
  val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

  private val _showPopupDialog = MutableStateFlow(false)
  val showPopupDialog: StateFlow<Boolean> = _showPopupDialog.asStateFlow()

  val preferences: StateFlow<UpdatePreferencesSnapshot> =
    repository.preferencesFlow.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000L),
      initialValue = UpdatePreferencesSnapshot(),
    )

  private var activeJob: Job? = null
  private var lastAvailableRelease: UpdateState.UpdateAvailable? = null

  fun checkForUpdates(force: Boolean, showDialogIfAvailable: Boolean = true) {
    activeJob?.cancel()
    activeJob =
      viewModelScope.launch {
        _updateState.value = UpdateState.Checking
        val next = repository.checkForUpdate(force = force)
        _updateState.value = next
        if (next is UpdateState.UpdateAvailable) {
          lastAvailableRelease = next
          if (showDialogIfAvailable) {
            _showPopupDialog.value = true
          }
        }
      }
  }

  fun startUpdateDownload() {
    val available = (_updateState.value as? UpdateState.UpdateAvailable) ?: lastAvailableRelease ?: return
    activeJob?.cancel()
    activeJob =
      viewModelScope.launch {
        val resultState =
          repository.downloadAndVerify(
            release = available.release,
            apkAsset = available.apkAsset,
            shaAsset = available.shaAsset,
          ) { progressState ->
            _updateState.value = progressState
          }
        _updateState.value = resultState
        if (resultState is UpdateState.ReadyToInstall) {
          launchSystemInstaller()
        }
      }
  }

  fun launchSystemInstaller() {
    val current = _updateState.value
    val apkFile =
      when (current) {
        is UpdateState.ReadyToInstall -> current.apkFile
        is UpdateState.PermissionRequired -> current.apkFile
        else -> return
      }
    when (val res = repository.launchInstaller(apkFile)) {
      is InstallLaunchResult.Launched -> Unit
      is InstallLaunchResult.NeedsUnknownSourcesPermission -> {
        val release =
          when (current) {
            is UpdateState.ReadyToInstall -> current.release
            is UpdateState.PermissionRequired -> current.release
            else -> return
          }
        val verified =
          when (current) {
            is UpdateState.ReadyToInstall -> current.sha256Verified
            is UpdateState.PermissionRequired -> current.sha256Verified
            else -> false
          }
        val sha =
          when (current) {
            is UpdateState.ReadyToInstall -> current.computedSha256
            is UpdateState.PermissionRequired -> current.computedSha256
            else -> ""
          }
        _updateState.value = UpdateState.PermissionRequired(release, apkFile, verified, sha)
      }
      is InstallLaunchResult.Failed -> {
        _updateState.value =
          UpdateState.Error(
            errorType = UpdateErrorType.INSTALL_PERMISSION_DENIED,
            title = "Installer Failed",
            message = res.reason,
          )
      }
    }
  }

  fun buildPermissionSettingsIntent(): Intent = repository.installer.buildUnknownSourcesSettingsIntent()

  fun onReturnedFromPermissionSettings() {
    val current = _updateState.value as? UpdateState.PermissionRequired ?: return
    if (repository.installer.canRequestPackageInstalls()) {
      _updateState.value =
        UpdateState.ReadyToInstall(
          release = current.release,
          apkFile = current.apkFile,
          sha256Verified = current.sha256Verified,
          computedSha256 = current.computedSha256,
        )
      launchSystemInstaller()
    } else {
      _updateState.value =
        UpdateState.Error(
          errorType = UpdateErrorType.INSTALL_PERMISSION_DENIED,
          title = "Install Permission Denied",
          message = "Allow \"Install unknown apps\" for POV Survival in Android Settings to install the update.",
        )
    }
  }

  fun dismissUpdateDialog() {
    val available = _updateState.value as? UpdateState.UpdateAvailable
    _showPopupDialog.value = false
    if (available != null) {
      viewModelScope.launch {
        repository.dismissTag(available.release.tagName)
      }
    }
  }

  fun retry() {
    val last = lastAvailableRelease
    val err = _updateState.value as? UpdateState.Error
    if (last != null &&
      err != null &&
      err.errorType in
      setOf(
        UpdateErrorType.DOWNLOAD_FAILED,
        UpdateErrorType.INSUFFICIENT_STORAGE,
        UpdateErrorType.CHECKSUM_MISMATCH,
        UpdateErrorType.CORRUPTED_APK,
        UpdateErrorType.INSTALL_PERMISSION_DENIED,
      )
    ) {
      _updateState.value = last
      startUpdateDownload()
    } else {
      checkForUpdates(force = true, showDialogIfAvailable = false)
    }
  }

  fun saveRepoConfig(owner: String, repo: String) {
    viewModelScope.launch {
      repository.saveRepoConfig(owner, repo)
      checkForUpdates(force = true, showDialogIfAvailable = false)
    }
  }
}
