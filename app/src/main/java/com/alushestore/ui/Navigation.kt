package com.alushestore.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.alushestore.data.local.AlusheDatabase
import com.alushestore.data.repository.AlusheRepository
import com.alushestore.ui.screens.bases.BasesScreen
import com.alushestore.ui.screens.clientes.ClienteDetalleScreen
import com.alushestore.ui.screens.clientes.ClientesScreen
import com.alushestore.ui.screens.estadisticas.EstadisticasScreen
import com.alushestore.ui.screens.home.HomeScreen
import com.alushestore.ui.screens.splash.SplashScreen
import com.alushestore.ui.screens.tianguis.TianguisScreen
import com.alushestore.viewmodel.BasesViewModelFactory
import com.alushestore.viewmodel.ClienteDetalleViewModelFactory
import com.alushestore.viewmodel.ClientesViewModelFactory
import com.alushestore.viewmodel.EstadisticasViewModelFactory
import com.alushestore.viewmodel.HomeViewModelFactory
import com.alushestore.viewmodel.TianguisViewModelFactory

object Destinations {
    const val SPLASH = "splash"
    const val HOME = "home"
    const val BASES = "bases"
    const val ESTADISTICAS = "estadisticas?baseId={baseId}"
    fun estadisticas(baseId: Long? = null) = if (baseId != null && baseId > 0) "estadisticas?baseId=$baseId" else "estadisticas"
    const val TIANGUIS = "tianguis"
    const val CLIENTES = "clientes"
    const val CLIENTE_DETALLE = "cliente/{clienteId}"
    fun clienteDetalle(id: Long) = "cliente/$id"
}

@Composable
fun AlusheNavGraph() {
    val navController = rememberNavController()
    val context = LocalContext.current

    val repository = remember {
        val db = AlusheDatabase.getDatabase(context)
        val savedAccount = context
            .getSharedPreferences("alushe_prefs", android.content.Context.MODE_PRIVATE)
            .getString("google_account", null)
        val drive = savedAccount?.let {
            com.alushestore.data.repository.DriveBackupService(context, it)
        }
        AlusheRepository(db, drive)
    }

    NavHost(
        navController = navController,
        startDestination = Destinations.SPLASH,
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)
            ) + fadeIn(animationSpec = tween(220))
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                targetOffset = { it / 3 },
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)
            ) + fadeOut(animationSpec = tween(180))
        },
        popEnterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                initialOffset = { -it / 3 },
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)
            ) + fadeIn(animationSpec = tween(220))
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)
            ) + fadeOut(animationSpec = tween(180))
        }
    ) {

        composable(Destinations.SPLASH) {
            SplashScreen(
                onSplashComplete = {
                    navController.navigate(Destinations.HOME) {
                        popUpTo(Destinations.SPLASH) { inclusive = true }
                    }
                }
            )
        }

        composable(Destinations.HOME) {
            val vm: com.alushestore.viewmodel.HomeViewModel = viewModel(
                factory = HomeViewModelFactory(repository)
            )
            HomeScreen(
                viewModel = vm,
                onNavigateToBases = { navController.navigate(Destinations.BASES) },
                onNavigateToEstadisticas = { navController.navigate(Destinations.estadisticas()) },
                onNavigateToTianguis = { navController.navigate(Destinations.TIANGUIS) },
                onNavigateToClientes = { navController.navigate(Destinations.CLIENTES) }
            )
        }

        composable(Destinations.BASES) {
            val vm: com.alushestore.viewmodel.BasesViewModel = viewModel(
                factory = BasesViewModelFactory(repository)
            )
            BasesScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onNavigateToEstadisticas = { baseId -> navController.navigate(Destinations.estadisticas(baseId)) }
            )
        }

        composable(
            route = Destinations.ESTADISTICAS,
            arguments = listOf(
                navArgument("baseId") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { backStackEntry ->
            val baseId = backStackEntry.arguments?.getLong("baseId")?.takeIf { it > 0 }
            val vm: com.alushestore.viewmodel.EstadisticasViewModel = viewModel(
                factory = EstadisticasViewModelFactory(repository, baseId)
            )
            EstadisticasScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Destinations.TIANGUIS) {
            val vm: com.alushestore.viewmodel.TianguisViewModel = viewModel(
                factory = TianguisViewModelFactory(repository)
            )
            TianguisScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Destinations.CLIENTES) {
            val vm: com.alushestore.viewmodel.ClientesViewModel = viewModel(
                factory = ClientesViewModelFactory(repository)
            )
            ClientesScreen(
                viewModel = vm,
                onClienteClick = { navController.navigate(Destinations.clienteDetalle(it)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Destinations.CLIENTE_DETALLE,
            arguments = listOf(navArgument("clienteId") { type = NavType.LongType })
        ) { backStackEntry ->
            val clienteId = backStackEntry.arguments?.getLong("clienteId") ?: return@composable
            val vm: com.alushestore.viewmodel.ClienteDetalleViewModel = viewModel(
                factory = ClienteDetalleViewModelFactory(repository, clienteId)
            )
            ClienteDetalleScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
