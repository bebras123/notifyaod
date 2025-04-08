package com.bebras123.notifyaod

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.bebras123.notifyaod.ui.theme.NotifyAODTheme

// Data model for each entry
data class NotificationData(val packageName: String, val importance: Int)

// Persistence functions for notification data
fun saveNotificationDataList(context: Context, dataList: List<NotificationData>) {
    val jsonArray = org.json.JSONArray().apply {
        dataList.forEach { data ->
            put(org.json.JSONObject().apply {
                put("packageName", data.packageName)
                put("importance", data.importance)
            })
        }
    }
    context.getSharedPreferences("notificationData", Context.MODE_PRIVATE)
        .edit {
            putString("data", jsonArray.toString())
        }
}

fun loadNotificationDataList(context: Context): List<NotificationData> {
    val sharedPref = context.getSharedPreferences("notificationData", Context.MODE_PRIVATE)
    val jsonStr = sharedPref.getString("data", "[]") ?: "[]"
    val jsonArray = org.json.JSONArray(jsonStr)
    return List(jsonArray.length()) { i ->
        val jsonObject = jsonArray.getJSONObject(i)
        NotificationData(
            packageName = jsonObject.getString("packageName"),
            importance = jsonObject.getInt("importance")
        )
    }
}

// Persistence functions for packagelist AOD preference
fun savePackageListWhitelist(context: Context, enabled: Boolean) {
    context.getSharedPreferences("notificationData", Context.MODE_PRIVATE)
        .edit { putBoolean("packagelist_whitelist", enabled) }
}

fun loadPackageListAODEnabled(context: Context): Boolean {
    return context.getSharedPreferences("notificationData", Context.MODE_PRIVATE)
        .getBoolean("packagelist_whitelist", false)
}

class MainActivity : ComponentActivity() {

    companion object {
        const val CHANNEL_ID = "aod_channel_id"
        const val NOTIFICATION_ID = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        enableEdgeToEdge()

        // Check if notification listener is enabled
        if (isNotificationServiceEnabled()) {
            log("NotificationListenerService is ENABLED.")
        } else {
            log("NotificationListenerService is DISABLED. Prompting user to enable it.")
            try {
                val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                startActivity(intent)
            } catch (e: Exception) {
                log("Failed to open notification access settings: ${e.message}")
            }
        }

        setContent {
            NotifyAODTheme {
                val context = LocalContext.current
                val dataList = remember { mutableStateListOf<NotificationData>() }
                LaunchedEffect(Unit) {
                    dataList.addAll(loadNotificationDataList(context))
                }
                // Load initial packagelist setting from SharedPreferences.
                var packagelistWhitelist by remember { mutableStateOf(loadPackageListAODEnabled(context)) }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top
                    ) {
                        Text(
                            "Notify AOD",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        // Data entry UI with integrated packagelist checkbox row.
                        NotificationDataEntry(
                            context = context,
                            dataList = dataList,
                            packagelistAODEnabled = packagelistWhitelist,
                            onPackageListAODEnabledChange = { newValue ->
                                packagelistWhitelist = newValue
                                savePackageListWhitelist(context, newValue)
                                PackageListCache.invalidate()
                                log("Added packages are whitelist (enables AOD only for these): $newValue")
                            }
                        )
                        // Wrap the list in a Box so it takes available space and scrolls if needed.
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            NotificationDataList(context = context, dataList = dataList)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        PostNotificationButton()
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("App Log:", style = MaterialTheme.typography.titleMedium)
                        LogViewer(logs = logMessages)
                    }
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Test Channel"
            val descriptionText = "Channel for testing notifications"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            log("Notification channel created.")
        } else {
            log("Notification channel not needed for API < 26.")
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val pkgName = packageName
        val enabledListeners = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return enabledListeners.contains(pkgName)
    }
}

fun sendTestNotification(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            log("Notification blocked: POST_NOTIFICATIONS permission not granted.")
            return
        }
    }

    val builder = NotificationCompat.Builder(context, MainActivity.CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("Test Notification")
        .setContentText("This is test notification.")
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)

    try {
        with(NotificationManagerCompat.from(context)) {
            notify(MainActivity.NOTIFICATION_ID, builder.build())
            log("Notification posted with ID: ${MainActivity.NOTIFICATION_ID}")
        }
    } catch (e: SecurityException) {
        log("SecurityException while sending notification: ${e.message}")
        e.printStackTrace()
    } catch (e: Exception) {
        log("Exception while sending notification: ${e.message}")
        e.printStackTrace()
    }
}

@Composable
fun PostNotificationButton() {
    val context = LocalContext.current
    var hasNotificationPermission by remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            mutableStateOf(
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            )
        } else {
            mutableStateOf(true)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasNotificationPermission = isGranted
            if (isGranted) {
                sendTestNotification(context)
            } else {
                log("Notification permission denied.")
            }
        }
    )

    Button(onClick = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (hasNotificationPermission) {
                log("Permission already granted. Sending notification.")
                sendTestNotification(context)
            } else {
                log("Permission not granted. Requesting POST_NOTIFICATIONS.")
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            log("API < 33. Sending notification directly.")
            sendTestNotification(context)
        }
    }) {
        Text("Send Test Notification")
    }
}

@Composable
fun LogViewer(logs: List<String>) {
    val listState = rememberLazyListState()
    val backgroundColor = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant

    // Scroll to the end when a new log is added
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    // Wrap the whole log view in a SelectionContainer to allow text copying.
    SelectionContainer {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(backgroundColor)
                .padding(8.dp)
        ) {
            items(logs) { line ->
                Text(text = line, color = textColor)
            }
        }
    }
}

@Composable
fun NotificationDataEntry(
    context: Context,
    dataList: SnapshotStateList<NotificationData>,
    packagelistAODEnabled: Boolean,
    onPackageListAODEnabledChange: (Boolean) -> Unit
) {
    val packageNameState = remember { mutableStateOf("") }
    val importanceState = remember { mutableStateOf("-1") }

    Column {
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = packageNameState.value,
            onValueChange = { packageNameState.value = it },
            label = { Text("Application Package Name") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = importanceState.value,
            onValueChange = { newValue ->
                if (newValue.isEmpty() || newValue.matches(Regex("-?\\d*"))) {
                    importanceState.value = newValue
                }
            },
            label = { Text("Notification Importance") },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "All notifications: -1. For explicit notifications look for importance in log",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                if (packageNameState.value.isNotBlank()) {
                    val importance = importanceState.value.toIntOrNull() ?: -1
                    val newEntry = NotificationData(packageNameState.value.trim(), importance)
                    dataList.add(0, newEntry)
                    saveNotificationDataList(context, dataList)
                    log("Data saved: Package=${packageNameState.value}, Importance=$importance")
                    packageNameState.value = ""
                    importanceState.value = "-1"
                    PackageListCache.invalidate()
                }
            }
        ) {
            Text("+")
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = packagelistAODEnabled,
                onCheckedChange = { newValue ->
                    onPackageListAODEnabledChange(newValue)
                }
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (packagelistAODEnabled)
                    "Only notifications from this list enable AOD"
                else
                    "Notifications from this list are ignored",
                style = MaterialTheme.typography.labelSmall
            )
        }

    }
}

@Composable
fun NotificationDataList(
    context: Context,
    dataList: SnapshotStateList<NotificationData>,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier) {
        items(dataList) { item ->
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                elevation = CardDefaults.elevatedCardElevation(
                    defaultElevation = 3.dp
                )
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Package: ${item.packageName}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "Importance: ${item.importance}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    IconButton(
                        onClick = {
                            dataList.remove(item)
                            log("Removed: Package=${item.packageName}, Importance=${item.importance}")
                            saveNotificationDataList(context, dataList)
                            PackageListCache.invalidate()
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove")
                    }
                }
            }
        }
    }
}
