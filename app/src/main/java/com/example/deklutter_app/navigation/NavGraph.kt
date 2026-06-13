package com.example.deklutter_app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.deklutter_app.ui.auth.LoginScreen
import com.example.deklutter_app.ui.detail.ItemDetailScreen
import com.example.deklutter_app.ui.home.HomeScreen
import com.example.deklutter_app.ui.post.PostItemScreen
import com.example.deklutter_app.ui.profile.ProfileScreen
import com.example.deklutter_app.viewmodel.AuthViewModel
import com.example.deklutter_app.viewmodel.ItemViewModel

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Home : Screen("home")
    data object Post : Screen("post")
    data object Profile : Screen("profile")
    data object Detail : Screen("detail/{itemId}") {
        fun createRoute(itemId: String) = "detail/$itemId"
    }
}

private data class BottomNavItem(val route: String, val icon: ImageVector, val label: String)

@Composable
fun DeKlutterApp() {
    val authViewModel: AuthViewModel = viewModel()
    val itemViewModel: ItemViewModel = viewModel()
    val user by authViewModel.user.collectAsState()

    val navController = rememberNavController()
    val startDestination = if (user != null) Screen.Home.route else Screen.Login.route

    val bottomNavItems = listOf(
        BottomNavItem(Screen.Home.route, Icons.Default.Home, "Home"),
        BottomNavItem(Screen.Post.route, Icons.Default.Add, "Post"),
        BottomNavItem(Screen.Profile.route, Icons.Default.Person, "Profile")
    )

    val showBottomBarRoutes = setOf(Screen.Home.route, Screen.Post.route, Screen.Profile.route)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    LaunchedEffect(user) {
        if (user == null) {
            navController.navigate(Screen.Login.route) {
                popUpTo(0) { inclusive = true }
            }
        } else if (currentRoute == Screen.Login.route) {
            navController.navigate(Screen.Home.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    Scaffold(
        bottomBar = {
            if (currentRoute in showBottomBarRoutes) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Login.route) {
                LoginScreen(viewModel = authViewModel)
            }
            composable(Screen.Home.route) {
                HomeScreen(
                    viewModel = itemViewModel,
                    onItemClick = { itemId ->
                        navController.navigate(Screen.Detail.createRoute(itemId))
                    }
                )
            }
            composable(Screen.Post.route) {
                PostItemScreen(
                    viewModel = itemViewModel,
                    onSuccess = {
                        navController.popBackStack(Screen.Home.route, inclusive = false)
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Profile.route) {
                ProfileScreen(
                    authViewModel = authViewModel,
                    itemViewModel = itemViewModel,
                    onItemClick = { itemId ->
                        navController.navigate(Screen.Detail.createRoute(itemId))
                    }
                )
            }
            composable(Screen.Detail.route) { backStackEntry ->
                val itemId = backStackEntry.arguments?.getString("itemId") ?: return@composable
                ItemDetailScreen(
                    itemId = itemId,
                    itemViewModel = itemViewModel,
                    authViewModel = authViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
