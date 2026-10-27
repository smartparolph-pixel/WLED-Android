package ca.cgagnier.wlednativeandroid.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ca.cgagnier.wlednativeandroid.ui.auth.LoginRoute
import ca.cgagnier.wlednativeandroid.ui.auth.SignUpRoute
import ca.cgagnier.wlednativeandroid.ui.auth.SplashDestination
import ca.cgagnier.wlednativeandroid.ui.auth.SplashRoute
import ca.cgagnier.wlednativeandroid.ui.brand.SinagTheme
import ca.cgagnier.wlednativeandroid.ui.homeScreen.DeviceListDetail
import ca.cgagnier.wlednativeandroid.ui.settingsScreen.AboutScreen
import ca.cgagnier.wlednativeandroid.ui.settingsScreen.Settings
import kotlinx.serialization.Serializable

/**
 * Splash → Log in / Sign up → device list. An account is required before the device screens open;
 * signing out (Settings) returns to Log in with a cleared back stack.
 */
@Composable
@Suppress("LongMethod") // The whole navigation graph lives in one place on purpose.
fun MainNavHost(
    navController: NavHostController = rememberNavController(),
    startDeviceAddress: NavigationEvent? = null,
) {
    val openMain = {
        navController.navigate(DeviceListDetailScreen) {
            popUpTo(navController.graph.id) { inclusive = true }
        }
    }
    NavHost(
        navController = navController,
        startDestination = SplashScreenRoute,
    ) {
        composable<SplashScreenRoute> {
            SinagTheme(darkTheme = true) {
                SplashRoute(
                    onFinished = { destination ->
                        if (destination == SplashDestination.MAIN) {
                            openMain()
                        } else {
                            navController.navigate(LoginScreenRoute) {
                                popUpTo(navController.graph.id) { inclusive = true }
                            }
                        }
                    },
                )
            }
        }
        composable<LoginScreenRoute> {
            SinagTheme {
                LoginRoute(
                    onLoggedIn = openMain,
                    onSignUp = { navController.navigate(SignUpScreenRoute) },
                )
            }
        }
        composable<SignUpScreenRoute> {
            SinagTheme {
                SignUpRoute(
                    onSignedUp = openMain,
                    onLogIn = {
                        if (!navController.popBackStack()) navController.navigate(LoginScreenRoute)
                    },
                )
            }
        }
        composable<DeviceListDetailScreen> {
            DeviceListDetail(
                initialDeviceMacAddress = startDeviceAddress,
                openSettings = {
                    navController.navigate(SettingsScreen)
                },
            )
        }
        composable<SettingsScreen> {
            Settings(
                navigateUp = {
                    navController.navigateUp()
                },
                openAbout = { navController.navigate(AboutScreenRoute) },
                onSignedOut = {
                    navController.navigate(LoginScreenRoute) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
            )
        }
        composable<AboutScreenRoute> {
            SinagTheme {
                AboutScreen(navigateUp = { navController.navigateUp() })
            }
        }
    }
}

@Serializable
object SplashScreenRoute

@Serializable
object LoginScreenRoute

@Serializable
object SignUpScreenRoute

@Serializable
object DeviceListDetailScreen

@Serializable
object SettingsScreen

@Serializable
object AboutScreenRoute
