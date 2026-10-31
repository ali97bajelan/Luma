package com.luma.lifeos.ui.privacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R

/** Preview-free shell shown while authorization is unknown, unavailable, or missing. */
@Composable
fun AppLockGate(
    canRequestUnlock: Boolean,
    focusEmergencyAvailable: Boolean = false,
    focusEmergencyExitReady: Boolean = false,
    onUnlock: () -> Unit,
    onBackToSafeDestination: () -> Unit,
    onRequestFocusEmergencyExit: () -> Unit = {},
    onConfirmFocusEmergencyExit: () -> Unit = {},
    onCancelFocusEmergencyExit: () -> Unit = {},
) {
    var confirmingFocusExit by remember { mutableStateOf(false) }
    var requestingFocusExit by remember { mutableStateOf(false) }
    LaunchedEffect(focusEmergencyExitReady, requestingFocusExit) {
        if (requestingFocusExit && focusEmergencyExitReady) confirmingFocusExit = true
    }
    BackHandler(enabled = !confirmingFocusExit, onBack = onBackToSafeDestination)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.app_lock_gate_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.app_lock_gate_body),
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(
                onClick = onUnlock,
                enabled = canRequestUnlock,
                modifier = Modifier.padding(top = 20.dp),
            ) { Text(stringResource(R.string.app_lock_gate_unlock)) }
            TextButton(onClick = onBackToSafeDestination, modifier = Modifier.padding(top = 4.dp)) {
                Text(stringResource(R.string.app_lock_gate_back))
            }
            if (focusEmergencyAvailable) {
                TextButton(
                    onClick = {
                        onRequestFocusEmergencyExit()
                        requestingFocusExit = true
                    },
                    modifier = Modifier.padding(top = 4.dp),
                ) { Text(stringResource(R.string.app_lock_focus_emergency_exit)) }
            }
        }
    }
    if (confirmingFocusExit) {
        AlertDialog(
            onDismissRequest = {
                confirmingFocusExit = false
                requestingFocusExit = false
                onCancelFocusEmergencyExit()
            },
            title = { Text(stringResource(R.string.app_lock_focus_emergency_title)) },
            text = { Text(stringResource(R.string.app_lock_focus_emergency_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingFocusExit = false
                    requestingFocusExit = false
                    onConfirmFocusEmergencyExit()
                }) { Text(stringResource(R.string.app_lock_focus_emergency_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmingFocusExit = false
                    requestingFocusExit = false
                    onCancelFocusEmergencyExit()
                }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
