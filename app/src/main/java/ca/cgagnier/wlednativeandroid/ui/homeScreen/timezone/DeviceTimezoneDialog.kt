package ca.cgagnier.wlednativeandroid.ui.homeScreen.timezone

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.service.timezone.TimezoneOption
import ca.cgagnier.wlednativeandroid.service.timezone.WledTimezones

private const val PIN_LENGTH = 4

/**
 * Lets the user pick any IANA timezone for a device. WLED only understands 25 built-in zones, so the
 * choice is mapped onto the closest one (plus a fixed offset) and sent with `POST /json/cfg`.
 */
@Composable
fun DeviceTimezoneDialog(device: Device, onDismiss: () -> Unit, viewModel: TimezoneViewModel = hiltViewModel()) {
    val state = viewModel.state
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp).imePadding()) {
                Text(
                    text = stringResource(R.string.timezone_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                state.selected?.let {
                    Text(
                        text = stringResource(R.string.timezone_selected, "${it.offsetLabel} ${it.city}"),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text(stringResource(R.string.timezone_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
                TimezoneList(state.visibleOptions, state.selectedId, viewModel::select)
                if (state.needsPin) {
                    OutlinedTextField(
                        value = state.pin,
                        onValueChange = { if (it.length <= PIN_LENGTH) viewModel.setPin(it) },
                        label = { Text(stringResource(R.string.timezone_pin)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
                ApplyStatus(state.apply)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Button(
                        onClick = { viewModel.apply(device) },
                        enabled = state.selected != null && state.apply !is TimezoneApplyState.Applying,
                    ) {
                        Text(stringResource(R.string.timezone_apply))
                    }
                }
            }
        }
    }
}

@Composable
private fun TimezoneList(options: List<TimezoneOption>, selectedId: String, onSelect: (String) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(options.size) {
        val index = options.indexOfFirst { it.id == selectedId }
        if (index >= 0) listState.scrollToItem(index)
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
        items(options, key = { it.id }) { option ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { onSelect(option.id) },
            ) {
                RadioButton(selected = option.id == selectedId, onClick = { onSelect(option.id) })
                Column {
                    Text(text = "${option.offsetLabel}  ${option.city}", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "${option.region} · ${option.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun ApplyStatus(apply: TimezoneApplyState) {
    when (apply) {
        TimezoneApplyState.Idle -> Unit

        TimezoneApplyState.Applying -> CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp).size(24.dp))

        is TimezoneApplyState.Applied -> {
            val zone = WledTimezones.ALL.getOrNull(apply.setting.index)?.label.orEmpty()
            Text(
                text = stringResource(R.string.timezone_applied, zone),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (!apply.setting.exact) {
                Text(
                    text = stringResource(R.string.timezone_not_exact),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        is TimezoneApplyState.Failed -> Text(
            text = stringResource(apply.message),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
