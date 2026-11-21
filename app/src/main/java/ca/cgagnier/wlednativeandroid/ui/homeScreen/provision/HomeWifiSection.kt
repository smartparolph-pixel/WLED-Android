package ca.cgagnier.wlednativeandroid.ui.homeScreen.provision

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.service.provisioning.WifiChoice

private val SIGNAL_ICONS = listOf(
    R.drawable.twotone_signal_wifi_0_bar_24,
    R.drawable.twotone_signal_wifi_1_bar_24,
    R.drawable.twotone_signal_wifi_2_bar_24,
    R.drawable.twotone_signal_wifi_3_bar_24,
    R.drawable.twotone_signal_wifi_4_bar_24,
)

/**
 * The home WiFi the lantern joins: picked from a scan of nearby networks (the default), or typed in with
 * "Enter network name manually".
 */
@Composable
internal fun HomeWifiSection(state: ProvisionState, step: ProvisionStep.Form, viewModel: ProvisionViewModel) {
    val wifi = state.homeWifi
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // Only precise location lets Android return scan results.
        viewModel.onWifiPermissionResult(viewModel.canScanWifi())
    }
    val scan = {
        if (viewModel.canScanWifi()) viewModel.scanWifi() else permission.launch(viewModel.wifiScanPermissions)
    }
    // Scan as soon as the form opens.
    LaunchedEffect(Unit) {
        val waiting = wifi.scanned || wifi.scanning || wifi.problem != null
        if (!wifi.manual && !waiting) scan()
    }

    Text(
        text = stringResource(R.string.provision_home_wifi),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
    )
    when {
        wifi.manual -> {
            OutlinedTextField(
                value = state.homeSsid,
                onValueChange = viewModel::setSsid,
                label = { Text(stringResource(R.string.provision_home_wifi_name)) },
                isError = step.ssidError != null,
                supportingText = { step.ssidError?.let { Text(stringResource(it)) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { viewModel.useWifiScan() }) { Text(stringResource(R.string.provision_wifi_scan)) }
        }

        wifi.chosen != null -> ChosenWifi(wifi.chosen, onChange = viewModel::changeWifi)

        else -> WifiList(
            wifi,
            step,
            onScan = scan,
            onChoose = viewModel::chooseWifi,
            onManual = viewModel::enterWifiManually,
        )
    }
}

@Composable
private fun ChosenWifi(choice: WifiChoice, onChange: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
            WifiRowContent(choice, modifier = Modifier.weight(1f))
            TextButton(onClick = onChange) { Text(stringResource(R.string.provision_wifi_change)) }
        }
    }
    if (!choice.secured) {
        Text(
            text = stringResource(R.string.provision_wifi_open),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun WifiList(
    wifi: HomeWifiState,
    step: ProvisionStep.Form,
    onScan: () -> Unit,
    onChoose: (WifiChoice) -> Unit,
    onManual: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            if (wifi.scanning) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    Text(
                        text = stringResource(R.string.provision_wifi_scanning),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
            wifi.problem?.let {
                Text(
                    text = stringResource(it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp),
                )
                SettingsShortcut(it)
            }
            wifi.networks.forEach { choice ->
                WifiRowContent(
                    choice,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = choice.supported) { onChoose(choice) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
                HorizontalDivider()
            }
            ActionRow(
                icon = R.drawable.outline_refresh_24,
                text = R.string.provision_wifi_scan_again,
                enabled = !wifi.scanning,
                onClick = onScan,
            )
            HorizontalDivider()
            ActionRow(icon = null, text = R.string.provision_wifi_manual, enabled = true, onClick = onManual)
        }
    }
    step.ssidError?.let {
        Text(
            text = stringResource(it),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Opens the phone setting that the problem asks for (Location, WiFi, or Sinag's permissions). */
@Composable
private fun SettingsShortcut(problem: Int) {
    val context = LocalContext.current
    val action = when (problem) {
        R.string.provision_wifi_location_off -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
        R.string.provision_wifi_off -> Settings.ACTION_WIFI_SETTINGS
        R.string.provision_wifi_permission -> Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        else -> return
    }
    TextButton(
        onClick = {
            val intent = Intent(action).apply {
                if (action == Settings.ACTION_APPLICATION_DETAILS_SETTINGS) {
                    data = Uri.fromParts("package", context.packageName, null)
                }
            }
            runCatching { context.startActivity(intent) }
        },
        modifier = Modifier.padding(start = 4.dp),
    ) {
        Text(stringResource(R.string.provision_wifi_open_settings))
    }
}

@Composable
private fun WifiRowContent(choice: WifiChoice, modifier: Modifier = Modifier) {
    val alpha = if (choice.supported) 1f else DISABLED_ALPHA
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Icon(
            painter = painterResource(SIGNAL_ICONS[choice.bars.coerceIn(0, SIGNAL_ICONS.lastIndex)]),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = choice.ssid,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            if (!choice.supported) {
                Text(
                    text = stringResource(R.string.provision_wifi_5ghz),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (choice.secured) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = stringResource(R.string.provision_wifi_secured),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ActionRow(icon: Int?, text: Int, enabled: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        if (icon != null) {
            Icon(painter = painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        } else {
            Icon(imageVector = Icons.Filled.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Text(
            text = stringResource(text),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

private const val DISABLED_ALPHA = 0.45f
