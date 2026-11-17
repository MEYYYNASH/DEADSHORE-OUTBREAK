package com.example.update

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun UpdateDialog(
  state: UpdateState,
  onUpdateNow: () -> Unit,
  onLater: () -> Unit,
  onInstallNow: () -> Unit,
  onRetry: () -> Unit,
  onCheckNow: () -> Unit,
  buildPermissionIntent: () -> android.content.Intent,
  onReturnedFromPermissionSettings: () -> Unit,
) {
  val isBusy = state is UpdateState.Downloading || state is UpdateState.Verifying

  Dialog(
    onDismissRequest = { if (!isBusy) onLater() },
    properties =
      DialogProperties(
        usePlatformDefaultWidth = false,
        dismissOnBackPress = !isBusy,
        dismissOnClickOutside = !isBusy,
      ),
  ) {
    Box(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
      contentAlignment = Alignment.Center,
    ) {
      UpdateCard(
        state = state,
        onUpdateNow = onUpdateNow,
        onLater = if (isBusy) null else onLater,
        onInstallNow = onInstallNow,
        onRetry = onRetry,
        onCheckNow = onCheckNow,
        buildPermissionIntent = buildPermissionIntent,
        onReturnedFromPermissionSettings = onReturnedFromPermissionSettings,
        modifier = Modifier.widthIn(max = 520.dp),
      )
    }
  }
}
