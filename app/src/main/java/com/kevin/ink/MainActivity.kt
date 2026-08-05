package com.kevin.ink

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kevin.ink.ui.home.HomeScreen
import com.kevin.ink.ui.home.HomeViewModel
import com.kevin.ink.ui.detail.DetailScreen
import com.kevin.ink.ui.library.LibraryScreen
import com.kevin.ink.ui.library.LibraryViewModel
import com.kevin.ink.ui.reader.ReaderScreen
import com.kevin.ink.ui.reader.ReadingPrefs
import com.kevin.ink.ui.rhythm.RitmoScreen
import com.kevin.ink.ui.rhythm.RitmoViewModel
import com.kevin.ink.ui.theme.InkTheme

class MainActivity : ComponentActivity() {

    private val libraryViewModel: LibraryViewModel by viewModels()
    private val homeViewModel: HomeViewModel by viewModels()
    private val rhythmViewModel: RitmoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Solo en el primer arranque (no al rotar) procesamos un posible "Abrir con Ink".
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            val context = LocalContext.current
            val prefs = remember { ReadingPrefs(context) }
            var dark by remember { mutableStateOf(prefs.darkMode) }

            // Iconos de la barra de estado/navegación según el tema.
            val view = LocalView.current
            SideEffect {
                val window = (view.context as Activity).window
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }

            InkTheme(dark = dark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val navController = rememberNavController()
                    val goTab: (String) -> Unit = { key ->
                        when (key) {
                            "home", "library", "rhythm" -> navController.navigate(key) {
                                popUpTo("home") { inclusive = false }
                                launchSingleTop = true
                            }
                            else -> Toast.makeText(
                                context,
                                "${key.replaceFirstChar { it.uppercase() }} · próximamente",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                viewModel = homeViewModel,
                                onOpenBook = { id -> navController.navigate("detail/$id") },
                                onOpenLibrary = { goTab("library") },
                                onSelectTab = goTab,
                                darkMode = dark,
                                onSetDark = { dark = it; prefs.darkMode = it },
                            )
                        }
                        composable("library") {
                            LibraryScreen(
                                viewModel = libraryViewModel,
                                onOpenBook = { book -> navController.navigate("detail/${book.id}") },
                                onSelectTab = goTab,
                            )
                        }
                        composable("rhythm") {
                            RitmoScreen(
                                viewModel = rhythmViewModel,
                                onOpenBook = { id -> navController.navigate("detail/$id") },
                                onSelectTab = goTab,
                                onComingSoon = { what ->
                                    Toast.makeText(context, "$what · próximamente", Toast.LENGTH_SHORT).show()
                                },
                            )
                        }
                        composable(
                            route = "detail/{bookId}",
                            arguments = listOf(navArgument("bookId") { type = NavType.StringType }),
                        ) { entry ->
                            val bookId = entry.arguments?.getString("bookId").orEmpty()
                            DetailScreen(
                                bookId = bookId,
                                onBack = { navController.popBackStack() },
                                onOpenReader = { id -> navController.navigate("reader/$id") },
                                onOpenBook = { id -> navController.navigate("detail/$id") },
                                onOpenLibrary = { goTab("library") },
                            )
                        }
                        composable(
                            route = "reader/{bookId}",
                            arguments = listOf(navArgument("bookId") { type = NavType.StringType }),
                        ) { entry ->
                            val bookId = entry.arguments?.getString("bookId").orEmpty()
                            ReaderScreen(bookId = bookId, onBack = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** Importa un libro que llega desde "Abrir con" / "Compartir" de otra app. */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND ->
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        }
        if (uri != null) libraryViewModel.importFrom(uri)
    }
}
