package ca.cgagnier.wlednativeandroid.ui.homeScreen.provision

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.service.provisioning.ProvisioningProgress
import ca.cgagnier.wlednativeandroid.ui.components.deviceName

@Composable
fun ProvisionDevice(onDismissRequest: () -> Unit, viewModel: ProvisionViewModel = hiltViewModel()) {
    val state = viewModel.state
    val dismiss = {
        onDismissRequest()
        viewModel.clear()
    }

    Dialog(onDismissRequest = dismiss) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.provision_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                when (val step = state.step) {
                    is ProvisionStep.Form -> FormStep(state, step, viewModel, dismiss)
                    is ProvisionStep.Working -> WorkingStep(step.progress)
                    is ProvisionStep.Success -> SuccessStep(step, dismiss)
                }
            }
        }
    }
}

@Composable
private fun FormStep(
    state: ProvisionState,
    step: ProvisionStep.Form,
    viewModel: ProvisionViewModel,
    onDismiss: () -> Unit,
) {
    var showPassword by remember { mutableStateOf(false) }

    Text(
        text = stringResource(R.string.provision_instructions),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(bottom = 12.dp),
    )
    step.errorMessage?.let {
        Text(
            text = stringResource(it),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 12.dp),
        )
    }
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
    OutlinedTextField(
        value = state.homePassword,
        onValueChange = viewModel::setPassword,
        label = { Text(stringResource(R.string.provision_home_wifi_password)) },
        singleLine = true,
        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
    TextButton(onClick = { showPassword = !showPassword }) {
        Text(stringResource(if (showPassword) R.string.provision_hide_password else R.string.provision_show_password))
    }
    SetupNetworkFields(state, step, viewModel, showPassword)
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        Button(onClick = viewModel::start) { Text(stringResource(R.string.provision_start)) }
    }
}

/**
 * Name and password of the device's own setup network. Hidden by default; only needed for devices
 * flashed with a custom access point (e.g. a product name instead of WLED-AP).
 */
@Composable
private fun SetupNetworkFields(
    state: ProvisionState,
    step: ProvisionStep.Form,
    viewModel: ProvisionViewModel,
    showPassword: Boolean,
) {
    var expanded by remember { mutableStateOf(step.apSsidError != null || step.apPasswordError != null) }
    TextButton(onClick = { expanded = !expanded }) {
        Text(stringResource(R.string.provision_setup_network_options))
    }
    if (!expanded) return
    Text(
        text = stringResource(R.string.provision_setup_network_hint),
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(bottom = 4.dp),
    )
    OutlinedTextField(
        value = state.apSsidPrefix,
        onValueChange = viewModel::setApSsidPrefix,
        label = { Text(stringResource(R.string.provision_setup_network_name)) },
        isError = step.apSsidError != null,
        supportingText = { step.apSsidError?.let { Text(stringResource(it)) } },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.apPassword,
        onValueChange = viewModel::setApPassword,
        label = { Text(stringResource(R.string.provision_setup_network_password)) },
        isError = step.apPasswordError != null,
        supportingText = { step.apPasswordError?.let { Text(stringResource(it)) } },
        singleLine = true,
        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun WorkingStep(progress: ProvisioningProgress) {
    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
    val label = when (progress) {
        ProvisioningProgress.JoiningSetupNetwork -> R.string.provision_step_joining
        ProvisioningProgress.SendingCredentials -> R.string.provision_step_sending
        ProvisioningProgress.RebootingDevice -> R.string.provision_step_rebooting
        else -> R.string.provision_step_waiting
    }
    Text(text = stringResource(label), style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun SuccessStep(step: ProvisionStep.Success, onDismiss: () -> Unit) {
    Text(
        text = stringResource(R.string.add_device_added, deviceName(step.device)),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(bottom = 12.dp),
    )
    Text(
        text = stringResource(R.string.provision_secure_tip),
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(bottom = 16.dp),
    )
    Button(onClick = onDismiss) { Text(stringResource(R.string.done)) }
}
