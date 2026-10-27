package ca.cgagnier.wlednativeandroid.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.ui.brand.FieldErrorText
import ca.cgagnier.wlednativeandroid.ui.brand.LocalSinagExtras
import ca.cgagnier.wlednativeandroid.ui.brand.SinagColors
import ca.cgagnier.wlednativeandroid.ui.brand.SinagFieldType
import ca.cgagnier.wlednativeandroid.ui.brand.SinagLabeledDivider
import ca.cgagnier.wlednativeandroid.ui.brand.SinagLinks
import ca.cgagnier.wlednativeandroid.ui.brand.SinagLockup
import ca.cgagnier.wlednativeandroid.ui.brand.SinagPrimaryButton
import ca.cgagnier.wlednativeandroid.ui.brand.SinagSecondaryButton
import ca.cgagnier.wlednativeandroid.ui.brand.SinagTextField
import ca.cgagnier.wlednativeandroid.ui.brand.SinagTextLink
import ca.cgagnier.wlednativeandroid.ui.brand.SinagTheme
import ca.cgagnier.wlednativeandroid.util.openUriSafely

private val ScreenPadding = 24.dp
private val MaxFormWidth = 480.dp
private const val LINK_MARKER = "\u0000"

// region Splash

@Composable
fun SplashRoute(onFinished: (SplashDestination) -> Unit, viewModel: SplashViewModel = hiltViewModel()) {
    val destination = viewModel.destination
    LaunchedEffect(destination) { destination?.let(onFinished) }
    SplashScreen()
}

/** Always dark: reversed lockup and the tagline in Gold Tint on Lantern Navy, centered. */
@Composable
fun SplashScreen() {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .background(SinagColors.LanternNavy)
            .safeDrawingPadding()
            .padding(ScreenPadding),
    ) {
        SinagLockup(reversed = true, width = 260.dp)
        Text(
            text = stringResource(R.string.sinag_tagline),
            color = SinagColors.GoldTint,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

// endregion

// region Log in

@Composable
fun LoginRoute(onLoggedIn: () -> Unit, onSignUp: () -> Unit, viewModel: LoginViewModel = hiltViewModel()) {
    LaunchedEffect(viewModel.state.loggedIn) { if (viewModel.state.loggedIn) onLoggedIn() }
    LoginScreen(
        state = viewModel.state,
        onEmailChange = viewModel::setEmail,
        onPasswordChange = viewModel::setPassword,
        onSubmit = viewModel::submit,
        onForgotPassword = viewModel::forgotPassword,
        onSignUp = onSignUp,
    )
}

@Composable
@Suppress("LongMethod", "LongParameterList") // One stateless screen: layout plus one callback per action.
fun LoginScreen(
    state: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onForgotPassword: () -> Unit,
    onSignUp: () -> Unit,
) {
    val focus = LocalFocusManager.current
    val uriHandler = LocalUriHandler.current
    AuthScaffold(
        top = {
            SinagLockup()
            Text(
                text = stringResource(R.string.sinag_tagline),
                color = LocalSinagExtras.current.tagline,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
        },
        bottom = {
            SinagTextField(
                value = state.email,
                onValueChange = onEmailChange,
                label = stringResource(R.string.sinag_email),
                type = SinagFieldType.EMAIL,
                error = state.errors.email?.let { stringResource(it.message()) },
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
            )
            SinagTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = stringResource(R.string.sinag_password),
                type = SinagFieldType.PASSWORD,
                error = state.errors.password?.let { stringResource(it.message()) },
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = {
                    focus.clearFocus()
                    onSubmit()
                }),
            )
            FormMessageText(state.message)
            SinagPrimaryButton(
                text = stringResource(R.string.sinag_log_in),
                onClick = {
                    focus.clearFocus()
                    onSubmit()
                },
                loading = state.loading,
                modifier = Modifier.padding(top = 4.dp),
            )
            SinagTextLink(stringResource(R.string.sinag_forgot_password), onClick = onForgotPassword)
            SinagLabeledDivider(stringResource(R.string.sinag_new_to_sinag))
            SinagSecondaryButton(
                text = stringResource(R.string.sinag_sign_up_with_email),
                onClick = onSignUp,
                modifier = Modifier.padding(top = 8.dp),
            )
            SinagTextLink(
                text = stringResource(R.string.sinag_privacy_policy),
                onClick = { uriHandler.openUriSafely(SinagLinks.PRIVACY_POLICY_URL) },
                underline = true,
            )
        },
    )
}

// endregion

// region Sign up

@Composable
fun SignUpRoute(onSignedUp: () -> Unit, onLogIn: () -> Unit, viewModel: SignUpViewModel = hiltViewModel()) {
    LaunchedEffect(viewModel.state.signedUp) { if (viewModel.state.signedUp) onSignedUp() }
    SignUpScreen(
        state = viewModel.state,
        actions = SignUpActions(
            onNameChange = viewModel::setName,
            onEmailChange = viewModel::setEmail,
            onPasswordChange = viewModel::setPassword,
            onConfirmPasswordChange = viewModel::setConfirmPassword,
            onSubmit = viewModel::submit,
            onLogIn = onLogIn,
        ),
    )
}

data class SignUpActions(
    val onNameChange: (String) -> Unit = {},
    val onEmailChange: (String) -> Unit = {},
    val onPasswordChange: (String) -> Unit = {},
    val onConfirmPasswordChange: (String) -> Unit = {},
    val onSubmit: () -> Unit = {},
    val onLogIn: () -> Unit = {},
)

@Composable
@Suppress("LongMethod") // One stateless screen.
fun SignUpScreen(state: SignUpUiState, actions: SignUpActions) {
    val focus = LocalFocusManager.current
    val next = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) })
    AuthScaffold(
        topAlignment = Alignment.Start,
        top = {
            Text(
                text = stringResource(R.string.sinag_create_account_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.sinag_create_account_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalSinagExtras.current.muted,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )
            SinagTextField(
                value = state.name,
                onValueChange = actions.onNameChange,
                label = stringResource(R.string.sinag_name),
                type = SinagFieldType.NAME,
                error = state.errors.name?.let { stringResource(it.message()) },
                keyboardActions = next,
            )
            SinagTextField(
                value = state.email,
                onValueChange = actions.onEmailChange,
                label = stringResource(R.string.sinag_email),
                type = SinagFieldType.EMAIL,
                error = state.errors.email?.let { stringResource(it.message()) },
                keyboardActions = next,
            )
            SinagTextField(
                value = state.password,
                onValueChange = actions.onPasswordChange,
                label = stringResource(R.string.sinag_password),
                type = SinagFieldType.NEW_PASSWORD,
                error = state.errors.password?.let { stringResource(it.message()) },
                keyboardActions = next,
            )
            SinagTextField(
                value = state.confirmPassword,
                onValueChange = actions.onConfirmPasswordChange,
                label = stringResource(R.string.sinag_confirm_password),
                type = SinagFieldType.NEW_PASSWORD,
                error = state.errors.confirmPassword?.let { stringResource(it.message()) },
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = {
                    focus.clearFocus()
                    actions.onSubmit()
                }),
            )
            FormMessageText(state.message)
            SinagPrimaryButton(
                text = stringResource(R.string.sinag_create_account),
                onClick = {
                    focus.clearFocus()
                    actions.onSubmit()
                },
                loading = state.loading,
                modifier = Modifier.padding(top = 8.dp),
            )
            AgreementText()
        },
        bottom = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.sinag_have_account),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalSinagExtras.current.muted,
                )
                SinagTextLink(stringResource(R.string.sinag_log_in), onClick = actions.onLogIn)
            }
        },
    )
}

/** "By creating an account, you agree to our Privacy Policy. ..." with the policy as a link. */
@Composable
private fun AgreementText() {
    val full = stringResource(R.string.sinag_signup_agreement, LINK_MARKER)
    val linkText = stringResource(R.string.sinag_privacy_policy)
    val (before, after) = full.split(LINK_MARKER).let { it.first() to it.getOrElse(1) { "" } }
    val text = buildAnnotatedString {
        append(before)
        withLink(
            LinkAnnotation.Url(
                SinagLinks.PRIVACY_POLICY_URL,
                TextLinkStyles(SpanStyle(fontWeight = FontWeight.Medium, textDecoration = TextDecoration.Underline)),
            ),
        ) { append(linkText) }
        append(after)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = LocalSinagExtras.current.muted,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    )
}

// endregion

// region Shared layout

/**
 * Paper (light) or navy (dark) screen. [top] sits at the top and [bottom] at the bottom when there is
 * room; everything scrolls above the keyboard when there is not.
 */
@Composable
private fun AuthScaffold(
    top: @Composable () -> Unit,
    bottom: @Composable () -> Unit,
    topAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding(),
        ) {
            Column(
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .widthIn(max = MaxFormWidth)
                    .padding(ScreenPadding),
            ) {
                Column(
                    horizontalAlignment = topAlignment,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                ) { top() }
                Spacer(Modifier.height(32.dp))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) { bottom() }
            }
        }
    }
}

@Composable
private fun FormMessageText(message: FormMessage?) {
    message ?: return
    if (message.isError) {
        FieldErrorText(stringResource(message.text), modifier = Modifier.fillMaxWidth())
    } else {
        Text(
            text = stringResource(message.text),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// endregion

// region Previews

@Preview(name = "Splash")
@Composable
private fun SplashPreview() = SinagTheme(darkTheme = true) { SplashScreen() }

@Preview(name = "Log in - light", showBackground = true)
@Composable
private fun LoginLightPreview() = SinagTheme(darkTheme = false) { LoginScreen(LoginUiState(), {}, {}, {}, {}, {}) }

@Preview(name = "Log in - dark", showBackground = true)
@Composable
private fun LoginDarkPreview() = SinagTheme(darkTheme = true) {
    LoginScreen(LoginUiState(email = "juan", showErrors = true), {}, {}, {}, {}, {})
}

@Preview(name = "Sign up - light", showBackground = true)
@Composable
private fun SignUpLightPreview() = SinagTheme(darkTheme = false) { SignUpScreen(SignUpUiState(), SignUpActions()) }

@Preview(name = "Sign up - dark", showBackground = true)
@Composable
private fun SignUpDarkPreview() = SinagTheme(darkTheme = true) {
    SignUpScreen(
        SignUpUiState(name = "Juan", email = "juan@sinag.ph", password = "parol2026", confirmPassword = "parol"),
        SignUpActions(),
    )
}

// endregion
