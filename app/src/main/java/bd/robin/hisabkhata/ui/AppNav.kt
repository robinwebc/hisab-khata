package bd.robin.hisabkhata.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import bd.robin.hisabkhata.R

private data class Tab(val route: String, val label: Int, val icon: ImageVector, val tag: String)

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val back by nav.currentBackStackEntryAsState()
    val route = back?.destination?.route.orEmpty()
    val tabs = listOf(
        Tab("home", R.string.nav_khata, Icons.Rounded.MenuBook, "nav_home"),
        Tab("cash", R.string.nav_cashbox, Icons.Rounded.AccountBalanceWallet, "nav_cash"),
        Tab("qr", R.string.nav_qr, Icons.Rounded.QrCode2, "nav_qr"),
        Tab("products", R.string.nav_stock, Icons.Rounded.Inventory2, "nav_products"),
        Tab("more", R.string.nav_menu, Icons.Rounded.Menu, "nav_more"),
    )
    val showBar = tabs.any { it.route == route } || route == "parties"
    fun go(dest: String) {
        val clean = dest.substringBefore("&popHome")
        val tab = tabs.any { it.route == clean }
        if (tab) {
            nav.navigate(clean) {
                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        } else if (dest.contains("popHome") || clean.startsWith("entry/") && dest.contains("popHome")) {
            nav.navigate(clean) { popUpTo("home") }
        } else {
            nav.navigate(clean)
        }
    }
    Scaffold(bottomBar = {
        if (showBar) {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
            ) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = route == tab.route,
                        onClick = { go(tab.route) },
                        icon = { Icon(tab.icon, null) },
                        label = { Text(stringResource(tab.label)) },
                        modifier = Modifier.tag(tab.tag),
                    )
                }
            }
        }
    }) { padding ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") { HomeScreen { go(it) } }
            composable("qr") { QrHubScreen { go(it) } }
            composable("quicksell") { QuickSellScreen { nav.popBackStack() } }
            composable("inbox") { InfoScreen(stringResource(R.string.inbox_title), stringResource(R.string.inbox_body)) { nav.popBackStack() } }
            composable("support") { InfoScreen(stringResource(R.string.support_title), stringResource(R.string.support_body)) { nav.popBackStack() } }
            composable("parties") { PartiesScreen({ go(it) }, { nav.popBackStack() }) }
            composable("cash") { CashScreen { go(it) } }
            composable("products") { ProductsScreen { go(it) } }
            composable("more") { MoreScreen { go(it) } }
            composable("share") { ShareAppScreen { nav.popBackStack() } }
            composable("profile") { ProfileScreen { nav.popBackStack() } }
            composable("reports") { ReportScreen { nav.popBackStack() } }
            composable("settings") { SettingsScreen({ go(it) }, { nav.popBackStack() }) }
            composable("pin") { PinSetupScreen { nav.popBackStack() } }
            composable("detail/{id}") { entry ->
                DetailScreen(entry.arguments?.getString("id").orEmpty(), { go(it) }, { nav.popBackStack() })
            }
            composable(
                "edit?id={id}&face={face}",
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType; defaultValue = "" },
                    navArgument("face") { type = NavType.IntType; defaultValue = 0 },
                ),
            ) { entry ->
                val existingId = entry.arguments?.getString("id").orEmpty()
                PartyEditScreen(existingId, entry.arguments?.getInt("face") ?: 0, onSaved = { newId ->
                    if (PendingCapture.slip != null) {
                        nav.navigate("entry/$newId?type=credit") { popUpTo("home") }
                    } else if (existingId.isBlank()) {
                        nav.popBackStack()
                        nav.navigate("detail/$newId")
                    } else {
                        nav.popBackStack()
                    }
                }, onBack = { nav.popBackStack() })
            }
            composable(
                "entry/{partyId}?type={type}&entryId={entryId}",
                arguments = listOf(
                    navArgument("partyId") { type = NavType.StringType },
                    navArgument("type") { type = NavType.StringType; defaultValue = "credit" },
                    navArgument("entryId") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                EntryScreen(
                    partyId = entry.arguments?.getString("partyId").orEmpty(),
                    initialType = entry.arguments?.getString("type").orEmpty(),
                    entryId = entry.arguments?.getString("entryId").orEmpty(),
                    onDone = { nav.popBackStack() },
                    onBack = { nav.popBackStack() },
                )
            }
            composable("pick/{purpose}") { entry ->
                val purpose = entry.arguments?.getString("purpose").orEmpty()
                PickScreen(purpose, onOpen = { dest ->
                    if (purpose == "slip") go("$dest&popHome=1") else go(dest)
                }, onBack = { nav.popBackStack() })
            }
            composable("scan/slip") { SlipScanScreen({ go(it) }, { nav.popBackStack() }) }
            composable("scan/barcode") { BarcodeScanScreen({ go(it) }, { nav.popBackStack() }) }
            composable(
                "scan/face?partyId={partyId}",
                arguments = listOf(navArgument("partyId") { type = NavType.StringType; defaultValue = "" }),
            ) { entry ->
                FaceScanScreen(entry.arguments?.getString("partyId").orEmpty(), { go(it) }, { nav.popBackStack() })
            }
            composable("scan/nid/{partyId}") { entry ->
                NidScanScreen(entry.arguments?.getString("partyId").orEmpty()) { nav.popBackStack() }
            }
            composable("verify/{partyId}") { entry ->
                VerifyScreen(entry.arguments?.getString("partyId").orEmpty()) { nav.popBackStack() }
            }
            composable(
                "cash/edit?id={id}&type={type}",
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType; defaultValue = "" },
                    navArgument("type") { type = NavType.StringType; defaultValue = "income" },
                ),
            ) { entry ->
                CashEditScreen(
                    entry.arguments?.getString("id").orEmpty(),
                    entry.arguments?.getString("type") ?: "income",
                    { nav.popBackStack() },
                    { nav.popBackStack() },
                )
            }
            composable(
                "product/edit?id={id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType; defaultValue = "" }),
            ) { entry ->
                ProductEditScreen(entry.arguments?.getString("id").orEmpty(), { nav.popBackStack() }, { nav.popBackStack() })
            }
        }
    }
}
