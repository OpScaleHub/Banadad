package com.github.opscalehub.dong.ui

import androidx.compose.ui.unit.dp

import androidx.core.view.drawToBitmap


import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.github.opscalehub.dong.i18n.MoneyFormat
import com.github.opscalehub.dong.ui.screens.BuildingsScreen
import com.github.opscalehub.dong.ui.screens.CategoriesScreen
import com.github.opscalehub.dong.ui.screens.ChargeItemsScreen
import com.github.opscalehub.dong.ui.screens.ExpenseScreen
import com.github.opscalehub.dong.ui.screens.ExpensesScreen
import com.github.opscalehub.dong.ui.screens.FinanceScreen
import com.github.opscalehub.dong.ui.screens.HomeScreen
import com.github.opscalehub.dong.ui.screens.MeterScreen
import com.github.opscalehub.dong.ui.screens.MoreScreen
import com.github.opscalehub.dong.ui.screens.ParkingScreen
import com.github.opscalehub.dong.ui.screens.PeriodScreen
import com.github.opscalehub.dong.ui.screens.PeriodsScreen
import com.github.opscalehub.dong.ui.screens.ReadingsScreen
import com.github.opscalehub.dong.ui.screens.ReportsScreen
import com.github.opscalehub.dong.ui.screens.SettingsScreen
import com.github.opscalehub.dong.ui.screens.SetupWizard
import com.github.opscalehub.dong.ui.screens.UnitScreen
import com.github.opscalehub.dong.ui.screens.SendBillsScreen
import com.github.opscalehub.dong.ui.screens.FeedbackScreen
import com.github.opscalehub.dong.ui.screens.UnitsScreen
import com.github.opscalehub.dong.ui.screens.WaterScreen
import com.github.opscalehub.dong.ui.screens.WelcomeScreen

object Routes {
    const val HOME = "home"
    const val UNITS = "units"
    const val EXPENSES = "expenses"
    const val REPORTS = "reports"
    const val MORE = "more"
    const val BUILDINGS = "buildings"
    const val SETUP = "setup"
    const val READINGS = "readings"
    const val PARKING = "parking"
    const val WATER = "water"
    const val CATEGORIES = "categories"
    const val CHARGE_ITEMS = "charge_items"
    const val PERIODS = "periods"
    const val FINANCE = "finance"
    const val SETTINGS = "settings"
    const val COLLECT = "collect"
    const val FEEDBACK = "feedback"
    fun send(periodId: String) = "send/$periodId"
    fun unit(id: String) = "unit/$id"
    fun meter(id: String) = "meter/$id"
    fun expense(id: String) = "expense/$id"
    fun period(id: String) = "period/$id"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, S.tabHome, Icons.Filled.Home),
    Tab(Routes.UNITS, S.tabUnits, Icons.Filled.AccountBox),
    Tab(Routes.EXPENSES, S.tabExpenses, Icons.Filled.ShoppingCart),
    Tab(Routes.REPORTS, S.tabReports, Icons.Filled.Star),
    Tab(Routes.MORE, S.tabMore, Icons.Filled.Menu),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DongRoot(vm: AppViewModel = viewModel()) {
    val buildings by vm.buildings.collectAsState()
    val building by vm.building.collectAsState()
    val busy by vm.busy.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) {
        vm.undos.collect { u ->
            // Long enough for an older user to read it and reach the button.
            val r = snackbar.showSnackbar(u.text, actionLabel = S.undo, duration = androidx.compose.material3.SnackbarDuration.Long)
            if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) vm.run(S.undone) { u.action() }
        }
    }
    val money = building?.let { MoneyFormat.of(it) } ?: MoneyFormat()

    CompositionLocalProvider(LocalMoney provides money) {
        val current = building
        if (buildings.isEmpty() || current == null) {
            // First run: welcome + setup wizard. The root switches to the app once a building exists.
            Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { pad ->
                // Edge-to-edge: make room for the keyboard so it never covers the field being filled.
                Column(Modifier.padding(pad).consumeWindowInsets(pad).imePadding().fillMaxSize()) { WelcomeScreen(vm) }
            }
            return@CompositionLocalProvider
        }
        val nav = rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        val route = entry?.destination?.route ?: Routes.HOME
        val isTab = tabs.any { it.route == route }
        var switcher by remember { mutableStateOf(false) }
    var help by remember { mutableStateOf<String?>(null) }
        help?.let { text ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { help = null },
                title = { Text(S.helpTitle) },
                text = { Text(text) },
                confirmButton = { TextButton(onClick = { help = null }) { Text(S.gotIt) } },
            )
        }
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            if (route == Routes.HOME) {
                                TextButton(onClick = { switcher = true }) {
                                    Text(current.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge)
                                    Icon(Icons.Filled.ArrowDropDown, S.switchBuilding)
                                }
                                DropdownMenu(expanded = switcher, onDismissRequest = { switcher = false }) {
                                    buildings.forEach { b -> DropdownMenuItem(text = { Text(b.name) }, onClick = { vm.select(b.id); switcher = false }) }
                                    DropdownMenuItem(text = { Text(S.buildingsAndSwitch) }, onClick = { switcher = false; nav.navigate(Routes.BUILDINGS) })
                                }
                            } else {
                                Text(titleFor(route), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        },
                        navigationIcon = {
                            if (!isTab) IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, S.back) }
                        },
                        actions = {
                            helpFor(route)?.let { text -> IconButton(onClick = { help = text }) { Icon(Icons.Filled.Info, S.helpTitle) } }
                        },
                    )
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            bottomBar = {
                if (isTab) {
                    NavigationBar {
                        tabs.forEach { t ->
                            NavigationBarItem(
                                selected = route == t.route,
                                onClick = {
                                    // «خانه» always shows Home: its step cards open other tabs' screens on top of
                                    // Home, and restoring Home's saved stack would bring those back instead.
                                    if (t.route == Routes.HOME) { if (!nav.popBackStack(Routes.HOME, inclusive = false)) nav.navigate(Routes.HOME) }
                                    else nav.navigate(t.route) { popUpTo(Routes.HOME) { saveState = true }; launchSingleTop = true; restoreState = true }
                                },
                                icon = { Icon(t.icon, null) },
                                label = { Text(t.label) },
                            )
                        }
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { pad ->
            // The keyboard shrinks the content instead of covering it; lists then scroll the focused
            // field into view (testers had to close the keyboard to see what they were filling).
            Column(Modifier.padding(pad).consumeWindowInsets(pad).imePadding().fillMaxSize()) {
                if (isTab) Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { UpdateCard(vm.updater) }
                DongNavHost(nav, vm)
            }
        }
    }
}

/** The «what is this page?» text for [route], or null where the page explains itself. */
private fun helpFor(route: String): String? = when {
    route == Routes.HOME -> S.helpHome
    route == Routes.UNITS || route.startsWith("unit/") -> S.helpUnits
    route == Routes.COLLECT -> S.helpCollect
    route.startsWith("send/") -> S.helpSend
    route == Routes.EXPENSES -> S.helpExpenses
    route.startsWith("expense/") -> S.helpExpense
    route == Routes.PERIODS -> S.helpPeriods
    route.startsWith("period/") -> S.helpPeriod
    route == Routes.WATER || route == Routes.READINGS || route.startsWith("meter/") -> S.helpMeters
    route == Routes.FINANCE -> S.helpFinance
    route == Routes.REPORTS -> S.helpReports
    route == Routes.CHARGE_ITEMS -> S.helpChargeItems
    route == Routes.CATEGORIES -> S.helpCategories
    else -> null
}

private fun titleFor(route: String): String = when {
    route == Routes.UNITS -> S.units
    route == Routes.EXPENSES -> S.expenses
    route == Routes.REPORTS -> S.reports
    route == Routes.MORE -> S.more
    route == Routes.READINGS -> S.readingsBatch
    route == Routes.PARKING -> S.parking
    route == Routes.WATER -> S.metersAndTariffs
    route == Routes.CATEGORIES -> S.categories
    route == Routes.CHARGE_ITEMS -> S.chargeItems
    route == Routes.PERIODS -> S.periods
    route == Routes.FINANCE -> S.transactions
    route == Routes.SETTINGS -> S.settings
    route == Routes.COLLECT -> S.collectTitle
    route == Routes.FEEDBACK -> S.feedbackLong
    route.startsWith("send/") -> S.stepSend
    route == Routes.BUILDINGS -> S.buildings
    route == Routes.SETUP -> S.setupTitle
    route.startsWith("unit/") -> S.unit
    route.startsWith("meter/") -> S.meters
    route.startsWith("expense/") -> S.expenses
    route.startsWith("period/") -> S.month
    else -> S.appName
}

@Composable
private fun DongNavHost(nav: NavHostController, vm: AppViewModel) {
    NavHost(nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) { HomeScreen(vm, nav) }
        composable(Routes.UNITS) { UnitsScreen(vm, nav) }
        composable(Routes.COLLECT) { UnitsScreen(vm, nav, collect = true) }
        composable("send/{id}") { SendBillsScreen(vm, it.arguments?.getString("id")!!) { nav.navigate(Routes.SETTINGS) } }
        composable(Routes.EXPENSES) { ExpensesScreen(vm, nav) }
        composable(Routes.REPORTS) { ReportsScreen(vm, nav) }
        composable(Routes.MORE) { MoreScreen(vm, nav) }
        composable(Routes.FEEDBACK) { FeedbackScreen(vm, nav) }
        composable(Routes.BUILDINGS) { BuildingsScreen(vm, nav) }
        composable(Routes.SETUP) { SetupWizard(vm, onDone = { nav.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } } }) }
        composable(Routes.READINGS) { ReadingsScreen(vm, nav) }
        composable("unit/{id}") { UnitScreen(vm, nav, it.arguments?.getString("id")!!) }
        composable(Routes.PARKING) { ParkingScreen(vm) }
        composable(Routes.WATER) { WaterScreen(vm, nav) }
        composable("meter/{id}") { MeterScreen(vm, it.arguments?.getString("id")!!) }
        composable("expense/{id}") { ExpenseScreen(vm, nav, it.arguments?.getString("id")!!) }
        composable(Routes.CATEGORIES) { CategoriesScreen(vm) }
        composable(Routes.CHARGE_ITEMS) { ChargeItemsScreen(vm) }
        composable(Routes.PERIODS) { PeriodsScreen(vm, nav) }
        composable("period/{id}") { PeriodScreen(vm, nav, it.arguments?.getString("id")!!) }
        composable(Routes.FINANCE) { FinanceScreen(vm, nav) }
        composable(Routes.SETTINGS) { SettingsScreen(vm) }
    }
}
