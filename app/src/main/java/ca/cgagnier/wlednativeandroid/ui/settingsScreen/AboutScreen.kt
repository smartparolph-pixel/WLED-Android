package ca.cgagnier.wlednativeandroid.ui.settingsScreen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ca.cgagnier.wlednativeandroid.BuildConfig
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.ui.brand.LocalSinagExtras
import ca.cgagnier.wlednativeandroid.ui.brand.SinagLinks
import ca.cgagnier.wlednativeandroid.ui.brand.SinagShape
import ca.cgagnier.wlednativeandroid.ui.brand.SinagTheme
import ca.cgagnier.wlednativeandroid.util.openUriSafely

@Composable
fun AboutScreen(navigateUp: () -> Unit, version: String = BuildConfig.VERSION_NAME) {
    val uriHandler = LocalUriHandler.current
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.sinag_about), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = navigateUp) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.description_back_button),
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.widthIn(max = 480.dp),
            ) {
                AboutRow(label = stringResource(R.string.sinag_version), value = version)
                AboutRow(
                    label = stringResource(R.string.sinag_privacy_policy),
                    emphasized = true,
                    onClick = { uriHandler.openUriSafely(SinagLinks.PRIVACY_POLICY_URL) },
                )
                SinagLinks.TERMS_OF_USE_URL?.let { url ->
                    AboutRow(label = stringResource(R.string.sinag_terms), onClick = { uriHandler.openUriSafely(url) })
                }
            }
            Spacer(Modifier.height(48.dp))
            AboutFooter(onSourceClick = { uriHandler.openUriSafely(SinagLinks.SOURCE_CODE_URL) })
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String? = null, emphasized: Boolean = false, onClick: (() -> Unit)? = null) {
    Card(
        shape = SinagShape,
        colors = CardDefaults.cardColors(containerColor = LocalSinagExtras.current.fieldFill),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp)
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = label,
                style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            value?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge, color = LocalSinagExtras.current.muted)
            }
            if (onClick != null) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = LocalSinagExtras.current.muted,
                )
            }
        }
    }
}

@Composable
private fun AboutFooter(onSourceClick: () -> Unit) {
    val muted = LocalSinagExtras.current.muted
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 24.dp)) {
        Text(stringResource(R.string.sinag_footer_company), style = MaterialTheme.typography.bodySmall, color = muted)
        Text(stringResource(R.string.sinag_powered_by), style = MaterialTheme.typography.bodySmall, color = muted)
        // GPL-3.0 "Appropriate Legal Notices": license, no warranty, and where to get the source.
        Text(
            text = stringResource(R.string.sinag_license_notice),
            style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.Underline),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 8.dp)
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onSourceClick)
                .padding(8.dp),
        )
    }
}

@Preview(name = "About - light", showBackground = true)
@Composable
private fun AboutLightPreview() = SinagTheme(darkTheme = false) { AboutScreen(navigateUp = {}, version = "7.0.1") }

@Preview(name = "About - dark", showBackground = true)
@Composable
private fun AboutDarkPreview() = SinagTheme(darkTheme = true) { AboutScreen(navigateUp = {}, version = "7.0.1") }
