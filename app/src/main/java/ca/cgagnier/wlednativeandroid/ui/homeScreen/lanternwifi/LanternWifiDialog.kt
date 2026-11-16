package ca.cgagnier.wlednativeandroid.ui.homeScreen.lanternwifi

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.service.lanternwifi.LanternWifiCredentials
import ca.cgagnier.wlednativeandroid.service.lanternwifi.LanternWifiStatus

private const val PIN_LENGTH = 4

/** The lantern's own WiFi name and password, with Show and Copy. */
@Composable
fun LanternWifiCard(credentials: LanternWifiCredentials, modifier: Modifier = Modifier) {
    var showPassword by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(stringResource(R.string.lantern_wifi_title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.lantern_wifi_name, credentials.ssid),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                stringResource(
                    R.string.lantern_wifi_password,
                    if (showPassword) credentials.password else "••••••••",
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
            )
            Row {
                TextButton(onClick = { showPassword = !showPassword }) {
                    Text(
                        stringResource(
                            if (showPassword) R.string.provision_hide_password else R.string.provision_show_password,
                        ),
                    )
                }
                TextButton(onClick = { copyPassword(context, credentials) }) {
                    Text(stringResource(R.string.lantern_wifi_copy))
                }
            }
            Text(
                stringResource(R.string.lantern_wifi_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Copies the password, marked sensitive so Android 13+ doesn't show it in the copy preview. */
private fun copyPassword(context: Context, credentials: LanternWifiCredentials) {
    val clip = ClipData.newPlainText(credentials.ssid, credentials.password)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
}

/** "Parol WiFi" in the lantern's settings. */
@Composable
fun LanternWifiDialog(device: Device, onDismiss: () -> Unit, viewModel: LanternWifiViewModel = hiltViewModel()) {
    LaunchedEffect(device.macAddress) { viewModel.load(device) }
    val state = viewModel.state
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.lantern_wifi_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                if (state.loading) {
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                } else {
                    LanternWifiContent(state, onPinChange = viewModel::setPin)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) }
                    if (!state.loading && state.status != LanternWifiStatus.Unknown) {
                        Button(onClick = { viewModel.secure(device) }, enabled = !state.saving) {
                            Text(stringResource(secureLabel(state.status)))
                        }
                    }
                }
            }
        }
    }
}

private fun secureLabel(status: LanternWifiStatus) = when (status) {
    LanternWifiStatus.Shared -> R.string.lantern_wifi_secure
    else -> R.string.lantern_wifi_new_password
}

@Composable
private fun LanternWifiContent(state: LanternWifiUiState, onPinChange: (String) -> Unit) {
    val message = when (state.status) {
        LanternWifiStatus.Unknown -> null
        LanternWifiStatus.Shared -> R.string.lantern_wifi_status_shared
        LanternWifiStatus.Secured -> if (state.changed) R.string.lantern_wifi_status_changed else null
        LanternWifiStatus.NotOnThisPhone -> R.string.lantern_wifi_status_not_saved
    }
    message?.let {
        val text = if (state.status == LanternWifiStatus.Secured) {
            stringResource(it)
        } else {
            stringResource(it, state.lanternSsid.orEmpty())
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 12.dp),
        )
    }
    if (state.status == LanternWifiStatus.Secured) {
        state.saved?.let { LanternWifiCard(it, modifier = Modifier.padding(bottom = 8.dp)) }
    }
    if (state.needsPin) {
        OutlinedTextField(
            value = state.pin,
            onValueChange = { if (it.length <= PIN_LENGTH) onPinChange(it) },
            label = { Text(stringResource(R.string.timezone_pin)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    state.error?.let {
        Text(
            stringResource(it),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
