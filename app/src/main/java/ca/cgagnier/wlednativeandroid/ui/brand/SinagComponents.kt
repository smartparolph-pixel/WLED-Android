package ca.cgagnier.wlednativeandroid.ui.brand

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ca.cgagnier.wlednativeandroid.R

/** 12 dp corners for fields and buttons. */
val SinagShape = RoundedCornerShape(12.dp)

/** Fields and buttons are 50 dp tall at default font size and grow with font scaling. */
val SinagControlHeight = 50.dp
private val FieldPadding = PaddingValues(horizontal = 16.dp, vertical = 13.dp)
private const val LOCKUP_ASPECT = 727f / 350f

/** The supplied lockup (wordmark + "BY SINAGTECH LABS INC."): color on Paper, reversed on navy. */
@Composable
fun SinagLockup(
    modifier: Modifier = Modifier,
    width: Dp = 240.dp,
    reversed: Boolean = LocalSinagExtras.current.isDark,
) {
    Image(
        painter = painterResource(if (reversed) R.drawable.sinag_lockup_reversed else R.drawable.sinag_lockup_color),
        contentDescription = stringResource(R.string.sinag_logo_description),
        modifier = modifier
            .width(width)
            .aspectRatio(LOCKUP_ASPECT),
    )
}

/** Field kinds: sets the keyboard, autofill hint and password behaviour together. */
enum class SinagFieldType(
    val keyboardType: KeyboardType,
    val contentType: ContentType,
    val isPassword: Boolean = false,
    val capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
) {
    NAME(KeyboardType.Text, ContentType.PersonFullName, capitalization = KeyboardCapitalization.Words),
    EMAIL(KeyboardType.Email, ContentType.EmailAddress),
    PASSWORD(KeyboardType.Password, ContentType.Password, isPassword = true),
    NEW_PASSWORD(KeyboardType.Password, ContentType.NewPassword, isPassword = true),
}

/**
 * Text field with a visible placeholder, an accessible label, a show/hide toggle for passwords and an
 * inline error (icon + text) underneath.
 */
@Composable
@Suppress("LongParameterList", "LongMethod") // Mirrors Material's text field API; one decoration block.
fun SinagTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    type: SinagFieldType,
    modifier: Modifier = Modifier,
    error: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val extras = LocalSinagExtras.current
    val colors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = extras.fieldFill,
        unfocusedContainerColor = extras.fieldFill,
        errorContainerColor = extras.fieldFill,
        focusedBorderColor = MaterialTheme.colorScheme.onBackground,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        focusedPlaceholderColor = extras.muted,
        unfocusedPlaceholderColor = extras.muted,
    )
    val transformation = if (type.isPassword && !visible) PasswordVisualTransformation() else VisualTransformation.None
    Column(modifier = modifier.fillMaxWidth()) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
            visualTransformation = transformation,
            keyboardOptions = KeyboardOptions(
                keyboardType = type.keyboardType,
                imeAction = imeAction,
                capitalization = type.capitalization,
                autoCorrectEnabled = type == SinagFieldType.NAME,
            ),
            keyboardActions = keyboardActions,
            interactionSource = interaction,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SinagControlHeight)
                .semantics {
                    contentDescription = label
                    contentType = type.contentType
                },
            decorationBox = { inner ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = value,
                    innerTextField = inner,
                    enabled = true,
                    singleLine = true,
                    visualTransformation = transformation,
                    interactionSource = interaction,
                    isError = error != null,
                    placeholder = { Text(label, style = MaterialTheme.typography.bodyLarge) },
                    trailingIcon = if (type.isPassword) {
                        { PasswordToggle(visible) { visible = !visible } }
                    } else {
                        null
                    },
                    colors = colors,
                    contentPadding = FieldPadding,
                    container = {
                        OutlinedTextFieldDefaults.Container(
                            enabled = true,
                            isError = error != null,
                            interactionSource = interaction,
                            colors = colors,
                            shape = SinagShape,
                        )
                    },
                )
            },
        )
        error?.let { FieldErrorText(it) }
    }
}

@Composable
private fun PasswordToggle(visible: Boolean, onToggle: () -> Unit) {
    TextButton(onClick = onToggle) {
        Text(
            text = stringResource(if (visible) R.string.sinag_hide_password else R.string.sinag_show_password),
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/** Error under a field: icon and text, never color alone. Announced by TalkBack when it appears. */
@Composable
fun FieldErrorText(message: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .padding(top = 4.dp, start = 4.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp),
        )
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

/** Signal Gold with Lantern Navy text. Shows a spinner and ignores taps while [loading]. */
@Composable
fun SinagPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, loading: Boolean = false) {
    Button(
        onClick = onClick,
        enabled = !loading,
        shape = SinagShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = SinagColors.SignalGold,
            contentColor = SinagColors.LanternNavy,
            disabledContainerColor = SinagColors.SignalGold.copy(alpha = 0.6f),
            disabledContentColor = SinagColors.LanternNavy,
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SinagControlHeight),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (loading) {
                CircularProgressIndicator(
                    color = SinagColors.LanternNavy,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(22.dp),
                )
            } else {
                Text(text, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** 1.5 dp outline in the text color (Lantern Navy on Paper, Paper on navy). */
@Composable
fun SinagSecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        shape = SinagShape,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.onBackground),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SinagControlHeight),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Text link with a 48 dp touch target. Never gold on Paper: uses the text color. */
@Composable
fun SinagTextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, underline: Boolean = false) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.bodySmall.copy(
                textDecoration = if (underline) TextDecoration.Underline else TextDecoration.None,
            ),
        )
    }
}

/** "———— New to Sinag? ————" */
@Composable
fun SinagLabeledDivider(text: String, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = LocalSinagExtras.current.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
    }
}
