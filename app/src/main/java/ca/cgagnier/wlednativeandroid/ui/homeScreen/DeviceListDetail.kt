package ca.cgagnier.wlednativeandroid.ui.homeScreen

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.model.AP_MODE_MAC_ADDRESS
import ca.cgagnier.wlednativeandroid.service.direct.DirectConnection
import ca.cgagnier.wlednativeandroid.service.direct.DirectJoin
import ca.cgagnier.wlednativeandroid.service.direct.inProgress
import ca.cgagnier.wlednativeandroid.service.direct.ssid
import ca.cgagnier.wlednativeandroid.service.lanternwifi.LanternWifiCredentials
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.service.websocket.getApModeDeviceWithState
import ca.cgagnier.wlednativeandroid.ui.NavigationEvent
import ca.cgagnier.wlednativeandroid.ui.applock.rememberSettingsGuard
import ca.cgagnier.wlednativeandroid.ui.homeScreen.detail.DeviceDetail
import ca.cgagnier.wlednativeandroid.ui.homeScreen.deviceAdd.DeviceAdd
import ca.cgagnier.wlednativeandroid.ui.homeScreen.deviceEdit.DeviceEdit
import ca.cgagnier.wlednativeandroid.ui.homeScreen.lanternwifi.rememberSaveWifiToPhone
import ca.cgagnier.wlednativeandroid.ui.homeScreen.list.DeviceList
import ca.cgagnier.wlednativeandroid.ui.homeScreen.list.DeviceWebsocketListViewModel
import ca.cgagnier.wlednativeandroid.ui.homeScreen.list.DirectBannerActions
import ca.cgagnier.wlednativeandroid.ui.homeScreen.list.DirectConnectionBanner
import ca.cgagnier.wlednativeandroid.ui.homeScreen.provision.ProvisionDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Suppress("LongMethod") // Navigator requires type inference, limiting extraction options
@Composable
fun DeviceListDetail(
    modifier: Modifier = Modifier,
    initialDeviceMacAddress: NavigationEvent? = null,
    openSettings: () -> Unit,
    viewModel: DeviceListDetailViewModel = hiltViewModel(),
    deviceWebsocketListViewModel: DeviceWebsocketListViewModel = hiltViewModel(),
) {
    val coroutineScope = rememberCoroutineScope()
    val defaultScaffoldDirective = calculatePaneScaffoldDirective(currentWindowAdaptiveInfo())
    val customScaffoldDirective = defaultScaffoldDirective.copy(
        horizontalPartitionSpacerSize = 0.dp,
    )
    val navigator =
        rememberListDetailPaneScaffoldNavigator<Any>(scaffoldDirective = customScaffoldDirective)
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    LaunchedEffect(initialDeviceMacAddress) {
        if (initialDeviceMacAddress != null) {
            navigator.navigateTo(
                pane = ListDetailPaneScaffoldRole.Detail,
                contentKey = initialDeviceMacAddress.macAddress,
            )
        }
    }

    val devices by deviceWebsocketListViewModel.allDevicesWithState.collectAsStateWithLifecycle()
    val selectedDeviceMacAddress = navigator.currentDestination?.contentKey as? String
    val selectedDevice = rememberSelectedDevice(devices, selectedDeviceMacAddress)

    val showHiddenDevices by viewModel.showHiddenDevices.collectAsStateWithLifecycle()
    val isWLEDCaptivePortal by viewModel.isWLEDCaptivePortal.collectAsStateWithLifecycle()
    val isAddDeviceDialogVisible by viewModel.isAddDeviceDialogVisible.collectAsStateWithLifecycle()
    val isProvisionDialogVisible by viewModel.isProvisionDialogVisible.collectAsStateWithLifecycle()
    DirectJoinMessages(viewModel)

    val navigateToDetail: (DeviceWithState) -> Unit = { device ->
        coroutineScope.launch {
            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, device.device.macAddress)
        }
    }
    val settingsGuard = rememberSettingsGuard()
    val navigateToEdit: (DeviceWithState) -> Unit = { device ->
        settingsGuard {
            coroutineScope.launch {
                navigator.navigateTo(ListDetailPaneScaffoldRole.Extra, device.device.macAddress)
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet {
                DrawerSheetContent(coroutineScope, drawerState, showHiddenDevices, viewModel, openSettings)
            }
        },
    ) {
        Scaffold { innerPadding ->
            NavigableListDetailPaneScaffold(
                modifier = modifier
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .imePadding(),
                navigator = navigator,
                defaultBackBehavior = BackNavigationBehavior.PopUntilScaffoldValueChange,
                listPane = {
                    AnimatedPane {
                        DeviceList(
                            selectedDevice,
                            isWLEDCaptivePortal = isWLEDCaptivePortal,
                            onItemClick = navigateToDetail,
                            onAddDevice = { settingsGuard { viewModel.showAddDeviceDialog() } },
                            onShowHiddenDevices = { viewModel.toggleShowHiddenDevices() },
                            onRefresh = { viewModel.startDiscoveryServiceTimed() },
                            onItemEdit = {
                                navigateToDetail(it)
                                navigateToEdit(it)
                            },
                            onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                            header = { DirectStatus(viewModel) },
                        )
                    }
                },
                detailPane = {
                    AnimatedPane {
                        selectedDevice?.let { device ->
                            DeviceDetail(
                                device = device,
                                onItemEdit = { navigateToEdit(device) },
                                canNavigateBack = navigator.canNavigateBack(),
                                navigateUp = { coroutineScope.launch { navigator.navigateBack() } },
                            )
                        } ?: SelectDeviceView()
                    }
                },
                extraPane = {
                    AnimatedPane {
                        selectedDevice?.let { device ->
                            DeviceEdit(
                                device = device,
                                canNavigateBack = navigator.canNavigateBack(),
                                navigateUp = { coroutineScope.launch { navigator.navigateBack() } },
                            )
                        }
                    }
                },
            )
        }

        BackHandler(enabled = drawerState.isOpen) {
            coroutineScope.launch { drawerState.close() }
        }
    }

    if (isAddDeviceDialogVisible) {
        DeviceAdd(onDismissRequest = { viewModel.hideAddDeviceDialog() })
    }
    if (isProvisionDialogVisible) {
        ProvisionDevice(onDismissRequest = { viewModel.hideProvisionDialog() })
    }
}

@Composable
private fun rememberSelectedDevice(
    devices: List<DeviceWithState>,
    selectedDeviceMacAddress: String?,
): DeviceWithState? {
    return remember(devices, selectedDeviceMacAddress) {
        if (selectedDeviceMacAddress == AP_MODE_MAC_ADDRESS) {
            return@remember getApModeDeviceWithState()
        }
        devices.firstOrNull { it.device.macAddress == selectedDeviceMacAddress }
    }
}

@Composable
private fun DrawerSheetContent(
    coroutineScope: CoroutineScope,
    drawerState: DrawerState,
    showHiddenDevices: Boolean,
    viewModel: DeviceListDetailViewModel,
    openSettings: () -> Unit,
) {
    val settingsGuard = rememberSettingsGuard()
    var showDirectChooser by remember { mutableStateOf(false) }
    if (showDirectChooser) {
        val wifis by viewModel.lanternWifis.collectAsStateWithLifecycle()
        val join by viewModel.directJoin.collectAsStateWithLifecycle()
        DirectConnectChooser(
            wifis = wifis.values.sortedBy { it.ssid },
            state = join,
            onChoose = { wifi ->
                showDirectChooser = false
                viewModel.connectDirectly(wifi)
            },
            onDismiss = { showDirectChooser = false },
        )
    }
    DrawerContent(
        showHiddenDevices = showHiddenDevices,
        addDevice = {
            coroutineScope.launch { drawerState.close() }
            settingsGuard { viewModel.showAddDeviceDialog() }
        },
        provisionDevice = {
            coroutineScope.launch { drawerState.close() }
            settingsGuard { viewModel.showProvisionDialog() }
        },
        toggleShowHiddenDevices = {
            coroutineScope.launch {
                viewModel.toggleShowHiddenDevices()
                drawerState.close()
            }
        },
        openSettings = {
            coroutineScope.launch { drawerState.close() }
            settingsGuard { openSettings() }
        },
        directConnection = if (viewModel.canConnectDirectly) {
            val join by viewModel.directJoin.collectAsStateWithLifecycle()
            val wifis by viewModel.lanternWifis.collectAsStateWithLifecycle()
            DirectDrawerAction(join) {
                coroutineScope.launch { drawerState.close() }
                when {
                    // In progress: cancel. Connected: leave.
                    join.inProgress || join is DirectJoin.Connected -> viewModel.stopDirectConnection()

                    wifis.isEmpty() -> viewModel.connectDirectly()

                    else -> showDirectChooser = true
                }
            }
        } else {
            null
        },
    )
}

/**
 * "Connect directly": which lantern WiFi to join, those saved on this phone and WLED's shared one. The one in
 * use (or being connected) is highlighted with its status.
 */
@Composable
private fun DirectConnectChooser(
    wifis: List<LanternWifiCredentials>,
    state: DirectJoin,
    onChoose: (LanternWifiCredentials?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.direct_connection_connect)) },
        text = {
            Column {
                wifis.forEach { wifi -> ChooserRow(wifi.ssid, state, onClick = { onChoose(wifi) }) }
                ChooserRow(
                    stringResource(R.string.direct_connection_new_parol),
                    state,
                    ssid = DirectConnection.AP_SSID,
                    onClick = { onChoose(null) },
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ChooserRow(label: String, state: DirectJoin, ssid: String = label, onClick: () -> Unit) {
    val current = state.ssid == ssid && (state.inProgress || state is DirectJoin.Connected)
    val status = when {
        !current -> null
        state is DirectJoin.Connected -> R.string.direct_chooser_connected
        else -> R.string.direct_chooser_connecting
    }
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (current) colors.secondaryContainer else Color.Transparent,
        contentColor = if (current) colors.onSecondaryContainer else colors.primary,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                status?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall) }
            }
            when {
                state is DirectJoin.Connected && current -> Icon(Icons.Filled.CheckCircle, contentDescription = null)
                current -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }
    }
}

/** The "Connect directly" status banner above the lanterns, and the offer to save a Parol WiFi. */
@Composable
private fun DirectStatus(viewModel: DeviceListDetailViewModel) {
    val context = LocalContext.current
    val join by viewModel.directJoin.collectAsStateWithLifecycle()
    DirectConnectionBanner(
        state = join,
        actions = DirectBannerActions(
            onCancel = viewModel::stopDirectConnection,
            onLeave = viewModel::stopDirectConnection,
            onRetry = viewModel::retryDirectConnection,
            onDismiss = viewModel::acknowledgeDirectJoin,
            onWifiSettings = { runCatching { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) } },
        ),
    )
}

/** After the first "Connect directly" to a Parol WiFi, offers once to save it to the phone's WiFi. */
@Composable
private fun DirectJoinMessages(viewModel: DeviceListDetailViewModel) {
    val join by viewModel.directJoin.collectAsStateWithLifecycle()
    var saveOffer by remember { mutableStateOf<LanternWifiCredentials?>(null) }
    LaunchedEffect(join is DirectJoin.Connected) {
        if (join is DirectJoin.Connected) saveOffer = viewModel.takeSaveToPhoneOffer()
    }
    val saveToPhone = rememberSaveWifiToPhone()
    val offer = saveOffer
    if (offer != null && saveToPhone != null) {
        AlertDialog(
            onDismissRequest = { saveOffer = null },
            title = { Text(stringResource(R.string.lantern_wifi_save_offer_title, offer.ssid)) },
            text = { Text(stringResource(R.string.lantern_wifi_save_offer_text)) },
            confirmButton = {
                TextButton(onClick = {
                    saveOffer = null
                    saveToPhone(offer)
                }) { Text(stringResource(R.string.lantern_wifi_save)) }
            },
            dismissButton = {
                TextButton(onClick = { saveOffer = null }) { Text(stringResource(R.string.lantern_wifi_not_now)) }
            },
        )
    }
}
