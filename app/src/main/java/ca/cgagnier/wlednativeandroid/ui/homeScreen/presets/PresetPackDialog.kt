package ca.cgagnier.wlednativeandroid.ui.homeScreen.presets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.service.presets.PackInstallResult
import ca.cgagnier.wlednativeandroid.service.presets.PresetPacks

/** Lists the preset packs (see [PresetPacks.ALL]) with a button to add each to [device]. */
@Composable
fun PresetPackDialog(device: Device, onDismiss: () -> Unit, viewModel: PresetPackViewModel = hiltViewModel()) {
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.preset_pack_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Text(
                    text = stringResource(R.string.preset_pack_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                PresetPacks.ALL.forEach { pack ->
                    OutlinedButton(
                        onClick = { viewModel.install(device, pack) },
                        enabled = viewModel.working == null,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        if (viewModel.working == pack) {
                            CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).size(18.dp))
                            Text(stringResource(R.string.preset_pack_adding, pack.playlist.name))
                        } else {
                            Text(stringResource(R.string.preset_pack_add, pack.playlist.name))
                        }
                    }
                }
                viewModel.result?.let {
                    Text(
                        text = presetPackResultText(it),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) }
                }
            }
        }
    }
}

/** One line describing what adding a pack did, e.g. "✨ Twinkle Tonight was added and is playing now." */
@Composable
fun presetPackResultText(result: PackInstallResult): String = when (result) {
    is PackInstallResult.Done -> {
        val main = stringResource(
            if (result.created.isEmpty()) R.string.preset_pack_already else R.string.preset_pack_added,
            result.playlistName,
        )
        val unavailable = result.unavailable.takeIf { it.isNotEmpty() }
            ?.let { stringResource(R.string.preset_pack_unavailable, it.joinToString()) }
        val unverified = if (result.verified) null else stringResource(R.string.preset_pack_unverified)
        listOfNotNull(main, unavailable, unverified).joinToString(" ")
    }

    PackInstallResult.NothingAvailable -> stringResource(R.string.preset_pack_nothing)

    PackInstallResult.Unreachable -> stringResource(R.string.preset_pack_unreachable)
}
