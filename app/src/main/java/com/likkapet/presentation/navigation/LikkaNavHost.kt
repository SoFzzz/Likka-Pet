package com.likkapet.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.likkapet.presentation.dashboard.DashboardScreen
import com.likkapet.presentation.onboarding.OnboardingScreen
import com.likkapet.presentation.permissions.PermissionsReviewScreen
import com.likkapet.presentation.privacy.PrivacyScreen
import com.likkapet.presentation.settings.AboutScreen
import com.likkapet.presentation.settings.SettingsScreen

/** The two graphs of design system §3.2: `onboarding` (once) and `main` (dashboard + Settings). */
@Composable
fun LikkaNavHost(
    factories: ViewModelFactories,
    start: StartDestination,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = start.graph, modifier = modifier) {
        navigation(startDestination = Routes.ONBOARDING, route = Routes.ONBOARDING_GRAPH) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    viewModel = viewModel(factory = remember { factories.onboarding() }),
                    // Back must not return to onboarding once it is done.
                    onFinished = {
                        navController.navigate(Routes.MAIN_GRAPH) {
                            popUpTo(Routes.ONBOARDING_GRAPH) { inclusive = true }
                        }
                    },
                )
            }
        }
        navigation(startDestination = start.mainStart, route = Routes.MAIN_GRAPH) {
            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    viewModel = viewModel(factory = remember { factories.dashboard() }),
                    onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                    onGrantPermissionClick = { navController.navigate(Routes.PERMISSIONS) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    viewModel = viewModel(factory = remember { factories.settings() }),
                    onBackClick = navController::popBackStack,
                    onPrivacyClick = { navController.navigate(Routes.PRIVACY) },
                    onReviewPermissionsClick = { navController.navigate(Routes.PERMISSIONS) },
                    onAboutClick = { navController.navigate(Routes.ABOUT) },
                )
            }
            composable(Routes.PRIVACY) {
                PrivacyScreen(
                    viewModel = viewModel(factory = remember { factories.privacy() }),
                    onBackClick = navController::popBackStack,
                )
            }
            composable(Routes.PERMISSIONS) { PermissionsRoute(factories, navController, isGate = false) }
            composable(Routes.PERMISSIONS_GATE) { PermissionsRoute(factories, navController, isGate = true) }
            composable(Routes.ABOUT) {
                AboutScreen(
                    viewModel = viewModel(factory = remember { factories.about() }),
                    onBackClick = navController::popBackStack,
                )
            }
        }
    }
}

@Composable
private fun PermissionsRoute(
    factories: ViewModelFactories,
    navController: NavHostController,
    isGate: Boolean,
) {
    PermissionsReviewScreen(
        viewModel = viewModel(factory = remember { factories.permissions() }),
        isGate = isGate,
        onBackClick = navController::popBackStack,
        onContinueClick = {
            navController.navigate(Routes.DASHBOARD) { popUpTo(Routes.PERMISSIONS_GATE) { inclusive = true } }
        },
    )
}
