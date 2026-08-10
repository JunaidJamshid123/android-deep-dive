# SECTION 2.3 — Services & Background Work

**Concepts covered:** foreground services, WorkManager, constraints, work chaining, broadcast receivers, content providers

**Project:** FileSync App — syncs files to a fake "cloud" in the background, shows a persistent progress notification, resumes after process death, and reacts to connectivity changes.

---

## 1. Theory: Why Background Work Needs Special Tools

An Activity or Fragment only runs while the user can see it. The moment the user leaves, the OS is free to stop or kill that code at any time. But real apps need work that outlives the screen — uploading a file, syncing data, downloading in the background. Android gives you different tools depending on **how urgent and how visible** that work needs to be:

```
                     How visible / time-critical is the work?
                     ─────────────────────────────────────────►

  IMMEDIATE,               IMMEDIATE,              DEFERRABLE,
  user-aware                background-only         guaranteed eventually
  ┌────────────────┐      ┌───────────────────┐    ┌─────────────────────┐
  │ Foreground       │      │ (rarely used        │    │  WorkManager          │
  │ Service           │      │  directly anymore    │    │  (Worker +            │
  │ + notification     │      │  — background        │    │   Constraints)        │
  │ (file sync,        │      │  services are        │    │  (periodic sync,      │
  │  music playback,    │      │  heavily restricted   │    │   deferred upload,    │
  │  navigation)        │      │  since Android 8)     │    │   retry-on-failure)   │
  └────────────────┘      └───────────────────┘    └─────────────────────┘

  BroadcastReceiver — not "work" itself, but a trigger/entry point that
  wakes your app in response to a system or app event (e.g. connectivity change)

  ContentProvider — not background work at all; a structured, permission-gated
  interface for OTHER apps (or your own) to read/write your app's data
```

This project deliberately uses **all four** together: a `PeriodicWorkRequest` handles the recurring 15-minute sync with constraints, a `BroadcastReceiver` reacts to connectivity changes to trigger an *immediate* sync attempt, a `ForegroundService` (started by the Worker while it runs) shows the live progress notification, and a `ContentProvider` exposes the synced files to other apps.

---

## 2. Foreground Services

### Theory

A **foreground service** is a service the user is actively aware of — it **must** show a persistent notification (non-dismissible while running) so the user always knows something is happening. In exchange, the OS gives it much stronger protection from being killed than a normal background service. Since Android 9 (API 28), the `FOREGROUND_SERVICE` permission is required; since Android 14 (API 34), you must also declare a **foreground service type** (e.g. `dataSync`) and request the matching permission.

```
              App starts a sync
                     │
                     ▼
        ┌───────────────────────────┐
        │  startForegroundService()  │  ← tells the OS "this is about
        └─────────────┬─────────────┘     to become foreground-important"
                       │
                       ▼
        ┌───────────────────────────┐
        │   FileSyncService.onCreate │
        │   .onStartCommand()         │
        └─────────────┬─────────────┘
                       │  MUST call within a few seconds, or the
                       │  system throws ANR / crashes the service
                       ▼
        ┌───────────────────────────┐
        │   startForeground(          │  ← notification appears, service
        │     notificationId,          │     promoted to foreground priority
        │     notification,             │
        │     FOREGROUND_SERVICE_TYPE_ │
        │     DATA_SYNC)                │
        └─────────────┬─────────────┘
                       │
                       ▼
        ┌───────────────────────────┐
        │  do sync work, periodically  │  ← notification updated with
        │  call notify() to update      │     live progress (e.g. "42%")
        │  progress                     │
        └─────────────┬─────────────┘
                       │
                       ▼
        ┌───────────────────────────┐
        │   stopForeground() +         │  ← notification removed (or
        │   stopSelf()                  │     demoted), service stops
        └───────────────────────────┘
```

### Manifest

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<service
    android:name=".sync.FileSyncService"
    android:foregroundServiceType="dataSync"
    android:exported="false" />
```

### Code

```kotlin
class FileSyncService : Service() {

    private val binder = LocalBinder()
    private var syncJob: Job? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    inner class LocalBinder : Binder() {
        fun getService(): FileSyncService = this@FileSyncService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildProgressNotification(progress = 0)
        startForeground(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
        startSync()
        // START_STICKY: if the system kills the process to reclaim memory,
        // recreate the service and redeliver a null Intent so it can restart work
        return START_STICKY
    }

    private fun startSync() {
        syncJob = serviceScope.launch {
            val files = fileRepository.getPendingFiles()
            files.forEachIndexed { index, file ->
                uploadFile(file)
                val progress = ((index + 1) * 100) / files.size
                updateNotification(progress)
            }
            stopSelf()
        }
    }

    private fun updateNotification(progress: Int) {
        val notification = buildProgressNotification(progress)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildProgressNotification(progress: Int): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Syncing files")
            .setContentText("$progress% complete")
            .setSmallIcon(R.drawable.ic_sync)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        syncJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "file_sync_channel"
    }
}
```

> **In this project, WorkManager (Section 3) actually starts the foreground service, not the Activity directly.** This is the modern recommended pattern (`setForeground()` inside a `CoroutineWorker`) — see Section 3.4.

---

## 3. WorkManager: Periodic Sync

### Theory

**WorkManager** is the recommended API for **deferrable, guaranteed** background work — work that should run even if the app is closed or the device restarts, and that Android is free to schedule intelligently (batching with other apps' work to save battery) as long as your constraints and timing are eventually honored. It's built on top of `JobScheduler`, `AlarmManager`, and a persistent internal database, so **scheduled work survives process death and even device reboot** (with the right setup).

```
   Your code                    WorkManager                     System
   ┌──────────┐         ┌───────────────────────┐        ┌──────────────┐
   │ enqueue()  │───────►│  Persists request to     │───────►│ JobScheduler / │
   │ Periodic-   │         │  internal Room database   │         │ AlarmManager    │
   │ WorkRequest │         │  (survives process death,  │         │ (OS decides       │
   └──────────┘         │  reboot if configured)     │         │  exact timing)    │
                         └───────────┬───────────┘        └───────┬──────┘
                                     │                              │
                                     │◄─────────────────────────────┘
                                     │   OS wakes the app when constraints
                                     │   are met and it's an efficient time
                                     ▼
                         ┌───────────────────────┐
                         │   Worker.doWork()        │  ← YOUR sync logic runs here,
                         │   runs on a background     │     off the main thread
                         │   thread pool                │
                         └───────────────────────┘
```

### Minimum interval note

`PeriodicWorkRequest` has a **minimum repeat interval of 15 minutes** — this is a hard OS-level floor to prevent battery drain, which conveniently matches this project's requirement exactly.

### Code

```kotlin
class FileSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            setForeground(createForegroundInfo(progress = 0))

            val files = fileRepository.getPendingFiles()
            files.forEachIndexed { index, file ->
                uploadFile(file)
                val progress = ((index + 1) * 100) / files.size
                setForeground(createForegroundInfo(progress))
            }
            Result.success()
        } catch (e: IOException) {
            // Transient failure (e.g. network dropped mid-upload) — retry with backoff
            Result.retry()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    private fun createForegroundInfo(progress: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, FileSyncService.CHANNEL_ID)
            .setContentTitle("Syncing files")
            .setContentText("$progress% complete")
            .setSmallIcon(R.drawable.ic_sync)
            .setProgress(100, progress, false)
            .build()
        return ForegroundInfo(FileSyncService.NOTIFICATION_ID, notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }
}
```

```kotlin
// Scheduling the periodic sync — typically in Application.onCreate() or a Repository init
fun schedulePeriodicSync(context: Context) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.UNMETERED)   // WiFi only
        .setRequiresBatteryNotLow(true)
        .build()

    val syncRequest = PeriodicWorkRequestBuilder<FileSyncWorker>(15, TimeUnit.MINUTES)
        .setConstraints(constraints)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
        .build()

    // uniqueWorkName ensures re-running this function (e.g. on every app launch)
    // doesn't stack up duplicate periodic requests
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "file_sync_periodic",
        ExistingPeriodicWorkPolicy.KEEP,
        syncRequest
    )
}
```

---

## 4. Constraints

### Theory

Constraints tell WorkManager the **conditions** that must be true before it's even allowed to run your Worker. WorkManager monitors these continuously — if a constraint stops being met mid-run (e.g. WiFi drops), the work is **stopped and rescheduled**, not just blocked from starting.

```
                    ┌─────────────────────────────┐
                    │   Constraints declared:        │
                    │   • NetworkType.UNMETERED        │
                    │   • requiresBatteryNotLow=true   │
                    └───────────────┬─────────────┘
                                    │
                     WorkManager continuously monitors
                     device state via system broadcasts
                                    │
              ┌─────────────────────┴─────────────────────┐
              │                                              │
     constraints met                              constraints NOT met
              │                                              │
              ▼                                              ▼
    ┌───────────────────┐                       ┌───────────────────────┐
    │  doWork() runs        │                       │  Work stays QUEUED,     │
    │                        │                       │  waits for constraints   │
    └───────────────────┘                       │  to become true again    │
              │                                    └───────────────────────┘
     WiFi drops mid-run
              │
              ▼
    ┌───────────────────┐
    │  Worker is STOPPED    │  ← onStopped() called; work re-queued to
    │  (not just paused)    │     retry once constraints are met again
    └───────────────────┘
```

### Code

```kotlin
val constraints = Constraints.Builder()
    .setRequiredNetworkType(NetworkType.UNMETERED)  // WiFi only, per project spec
    .setRequiresBatteryNotLow(true)
    .setRequiresStorageNotLow(true)
    .build()
```

```kotlin
// Reacting cleanly if the OS stops the work mid-flight (e.g. constraint lost)
class FileSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // ... upload logic ...
    }

    override fun onStopped() {
        super.onStopped()
        // Clean up partial state, e.g. mark an in-progress upload as "interrupted"
        // so the next run resumes rather than re-uploading from scratch.
        fileRepository.markInterrupted(currentUploadId)
    }
}
```

---

## 5. Work Chaining: Compress → Upload → Notify

### Theory

WorkManager lets you chain multiple `OneTimeWorkRequest`s so the **output of one becomes the input of the next**, and the whole chain is treated as a unit — if one link fails, downstream links don't run (unless you explicitly handle retries).

```
   enqueue chain:

   ┌─────────────────┐      ┌─────────────────┐      ┌─────────────────┐
   │ CompressWorker     │ ──►  │ UploadWorker        │ ──►  │ NotifyWorker        │
   │ (zips changed        │      │ (sends zip to        │      │ (shows "Sync         │
   │  files)               │      │  fake cloud API)      │      │  complete" toast/     │
   │                       │      │                       │      │  notification)         │
   └─────────────────┘      └─────────────────┘      └─────────────────┘
      Result.success()          Result.success()          Result.success()
      + output Data                + output Data
      (zip file path)               (upload confirmation)

   If CompressWorker fails → UploadWorker and NotifyWorker never run.
   If UploadWorker fails (Result.retry) → WorkManager retries UploadWorker
   alone with backoff; CompressWorker is NOT re-run.
```

### Code

```kotlin
class CompressWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val filePaths = inputData.getStringArray("files") ?: return Result.failure()
        val zipPath = fileCompressor.compress(filePaths.toList())
        val output = workDataOf("zipPath" to zipPath)
        return Result.success(output)
    }
}

class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val zipPath = inputData.getString("zipPath") ?: return Result.failure()
        val confirmationId = cloudApi.upload(zipPath)
        return Result.success(workDataOf("confirmationId" to confirmationId))
    }
}

class NotifyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val confirmationId = inputData.getString("confirmationId") ?: "unknown"
        notifier.showSyncComplete(confirmationId)
        return Result.success()
    }
}
```

```kotlin
fun enqueueSyncChain(context: Context, changedFilePaths: List<String>) {
    val compressRequest = OneTimeWorkRequestBuilder<CompressWorker>()
        .setInputData(workDataOf("files" to changedFilePaths.toTypedArray()))
        .build()

    val uploadRequest = OneTimeWorkRequestBuilder<UploadWorker>()
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .build()

    val notifyRequest = OneTimeWorkRequestBuilder<NotifyWorker>().build()

    // WorkManager automatically passes each worker's output Data as the next worker's inputData
    WorkManager.getInstance(context)
        .beginWith(compressRequest)
        .then(uploadRequest)
        .then(notifyRequest)
        .enqueue()
}
```

---

## 6. BroadcastReceiver: Reacting to Connectivity Changes

### Theory

A `BroadcastReceiver` is an entry point that lets your app respond to **system-wide or app-wide events** — here, a connectivity change — even from outside the context of any currently open screen. Since Android 8 (API 26), **implicit broadcasts registered in the manifest are heavily restricted** (most, including connectivity changes, no longer wake apps that aren't running) as a battery-saving measure. The modern, reliable pattern is a **context-registered ("dynamic") receiver** paired with WorkManager for anything that needs to survive the app being closed.

```
   Manifest-registered (STATIC)             Context-registered (DYNAMIC)
   ───────────────────────────             ────────────────────────────
   <receiver> in AndroidManifest.xml         registerReceiver() in code,
                                              e.g. in Application.onCreate()
        │                                           or a Service
        │  Android 8+: most implicit                    │
        │  broadcasts BLOCKED for apps                   │  Works reliably ONLY
        │  not currently running                          │  while the registering
        ▼                                                  │  component is alive
   ✗ Unreliable for connectivity                          ▼
     changes on modern Android              ✓ Used for immediate, in-session
                                               reactions; combine with WorkManager
                                               for anything that must survive
                                               the app being fully closed
```

```
        Connectivity actually changes (e.g. WiFi reconnects)
                            │
                            ▼
              ┌───────────────────────────┐
              │  ConnectivityManager.        │
              │  NetworkCallback fires         │  ← modern replacement for
              │  (registered via                │     CONNECTIVITY_ACTION broadcast
              │   registerDefaultNetworkCallback)│
              └─────────────┬─────────────┘
                            │
                            ▼
              ┌───────────────────────────┐
              │  If WiFi + unmetered:          │
              │  WorkManager.enqueue(            │
              │    OneTimeWorkRequest<FileSync   │  ← trigger an IMMEDIATE
              │    Worker>)                       │     sync attempt, on top of
              │                                    │     the 15-min periodic one
              └───────────────────────────┘
```

### Code

```kotlin
// Modern approach: NetworkCallback (survives configuration better than a
// manifest broadcast on Android 8+, and is the officially recommended API)
class NetworkMonitor(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            val isUnmetered = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true
            if (isUnmetered) {
                // Trigger an immediate sync on top of the periodic schedule
                val request = OneTimeWorkRequestBuilder<FileSyncWorker>().build()
                WorkManager.getInstance(context).enqueue(request)
            }
        }

        override fun onLost(network: Network) {
            LifecycleTimelineStore.log("NetworkMonitor", "connection lost — sync will pause")
        }
    }

    fun start() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)
    }

    fun stop() = connectivityManager.unregisterNetworkCallback(callback)
}
```

```kotlin
// Classic BroadcastReceiver — shown for the project's explicit "BroadcastReceiver"
// requirement, registered dynamically (NOT in the manifest) so it actually fires
// reliably on modern Android
class ConnectivityChangeReceiver(
    private val onConnected: () -> Unit
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        if (capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true) {
            onConnected()
        }
    }
}

// Registration — e.g. in a long-lived component, unregistered symmetrically
val receiver = ConnectivityChangeReceiver { schedulePeriodicSync(context) }
ContextCompat.registerReceiver(
    context, receiver,
    IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION),
    ContextCompat.RECEIVER_NOT_EXPORTED
)
// ...later
context.unregisterReceiver(receiver)
```

---

## 7. Content Provider: Exposing Synced Files to Other Apps

### Theory

A `ContentProvider` is a structured, permission-gated interface for sharing data **between apps** (or between components of your own app in a decoupled way), addressed by `content://` URIs rather than direct file paths or database access. It's the same mechanism `MediaStore`, `ContactsContract`, and `DocumentsProvider` use system-wide.

```
        Your App (FileSync)                    Another App (test client)
   ┌───────────────────────────┐        ┌───────────────────────────┐
   │   FileSyncProvider            │        │   ContentResolver              │
   │   (extends ContentProvider)   │        │   .query(uri, ...)               │
   │                                │        │   .openInputStream(uri)          │
   │   Backed by: SQLite table of    │◄───────┤                                │
   │   synced file metadata +        │  Binder │   content://com.example.       │
   │   FileProvider for actual        │  IPC    │   filesync.provider/files       │
   │   file bytes                      │        │                                │
   └───────────────────────────┘        └───────────────────────────┘

   Access is gated by:
     • android:exported="true" in the manifest
     • android:permission="..." (custom permission other apps must hold)
     • URI-level grants via FLAG_GRANT_READ_URI_PERMISSION for one-off access
```

### Manifest

```xml
<permission
    android:name="com.example.filesync.permission.READ_SYNCED_FILES"
    android:protectionLevel="normal" />

<provider
    android:name=".sync.FileSyncProvider"
    android:authorities="com.example.filesync.provider"
    android:exported="true"
    android:readPermission="com.example.filesync.permission.READ_SYNCED_FILES" />
```

### Code — the Provider

```kotlin
class FileSyncProvider : ContentProvider() {

    private lateinit var dbHelper: SyncDatabaseHelper

    override fun onCreate(): Boolean {
        dbHelper = SyncDatabaseHelper(context!!)
        return true
    }

    override fun query(
        uri: Uri, projection: Array<String>?, selection: String?,
        selectionArgs: Array<String>?, sortOrder: String?
    ): Cursor {
        return when (uriMatcher.match(uri)) {
            FILES -> dbHelper.readableDatabase.query(
                "synced_files", projection, selection, selectionArgs, null, null, sortOrder
            )
            FILE_ID -> {
                val id = ContentUris.parseId(uri)
                dbHelper.readableDatabase.query(
                    "synced_files", projection, "_id=?", arrayOf(id.toString()), null, null, null
                )
            }
            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val filePath = filePathForUri(uri)
        return ParcelFileDescriptor.open(File(filePath), ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = when (uriMatcher.match(uri)) {
        FILES -> "vnd.android.cursor.dir/vnd.com.example.filesync.file"
        FILE_ID -> "vnd.android.cursor.item/vnd.com.example.filesync.file"
        else -> throw IllegalArgumentException("Unknown URI: $uri")
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? { /* not needed for read-only sharing */ return null }
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0

    companion object {
        const val AUTHORITY = "com.example.filesync.provider"
        private const val FILES = 1
        private const val FILE_ID = 2
        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "files", FILES)
            addURI(AUTHORITY, "files/#", FILE_ID)
        }
    }
}
```

### Code — querying from a second, separate test app via `ContentResolver`

```kotlin
// Inside a completely separate "TestClientApp" — proves cross-app access works
val uri = Uri.parse("content://com.example.filesync.provider/files")

val cursor = contentResolver.query(uri, arrayOf("_id", "file_name", "synced_at"), null, null, "synced_at DESC")
cursor?.use {
    while (it.moveToNext()) {
        val name = it.getString(it.getColumnIndexOrThrow("file_name"))
        val syncedAt = it.getLong(it.getColumnIndexOrThrow("synced_at"))
        Log.d("TestClient", "Synced file: $name at $syncedAt")
    }
}

// Reading the actual file bytes through the provider
val fileUri = Uri.parse("content://com.example.filesync.provider/files/7")
contentResolver.openInputStream(fileUri)?.use { input ->
    val bytes = input.readBytes()
    // ... display or save the file bytes in the test client app
}
```

---

## 8. How All Four Pieces Fit Together — End-to-End Flow

```
 ┌───────────────┐  15 min elapses,        ┌────────────────────┐
 │  WorkManager     │  constraints met         │  FileSyncWorker       │
 │  (Periodic         ├─────────────────────►  │  .doWork()             │
 │   WorkRequest)     │                          │  setForeground(...)     │
 └───────────────┘                          └──────────┬─────────┘
         ▲                                              │
         │ enqueue(OneTimeWorkRequest)                    │ chain:
         │ on reconnect                                    │ Compress → Upload → Notify
 ┌───────────────┐                                    ▼
 │ NetworkMonitor /  │                          ┌────────────────────┐
 │ Connectivity        │                          │  Progress notification │
 │ Receiver             │                          │  updates live (fore-    │
 └───────────────┘                          │  ground service state)   │
                                              └──────────┬─────────┘
                                                          │ on success,
                                                          │ file metadata written
                                                          ▼
                                              ┌────────────────────┐
                                              │  FileSyncProvider      │
                                              │  exposes synced files   │
                                              │  to any app holding the  │
                                              │  READ_SYNCED_FILES perm  │
                                              └────────────────────┘
```

---

## 9. Common Pitfalls

| Pitfall | Why it happens | Fix |
|---|---|---|
| Calling `startForeground()` too late | The system expects it within a few seconds of `startForegroundService()`; missing the window throws `ForegroundServiceDidNotStartInTimeException` (crash) on modern Android | Build and post a minimal notification (even with 0% progress) immediately in `onStartCommand()`/at the very start of `doWork()`, before any slow work begins |
| Forgetting the foreground service **type** on Android 14+ | Since API 34, every foreground service must declare a type (`dataSync`, `mediaPlayback`, etc.) in the manifest and request the matching runtime permission, or startup throws `MissingForegroundServiceTypeException` | Declare `android:foregroundServiceType="dataSync"` and request `FOREGROUND_SERVICE_DATA_SYNC` |
| Relying on a manifest-registered `BroadcastReceiver` for `CONNECTIVITY_ACTION` | Android 8+ blocks most implicit broadcasts for apps not currently running, so the receiver silently never fires when the app is closed | Use `ConnectivityManager.NetworkCallback` (registered in code) for live monitoring, and WorkManager constraints for anything that must work even when the app is fully closed |
| Using `Result.failure()` for a transient error (e.g. a dropped connection) | Marks the whole work request as permanently failed — no automatic retry, downstream chained work never runs | Use `Result.retry()` for anything recoverable, paired with `setBackoffCriteria()`, and reserve `Result.failure()` for genuinely unrecoverable errors |
| Enqueuing a new `PeriodicWorkRequest` on every app launch without a unique name | Creates duplicate periodic jobs running independently, wasting battery and causing duplicate syncs | Always use `enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.KEEP, request)` |
| Exposing a `ContentProvider` with `exported="true"` and no permission | Any app on the device can query or read the provider's data with zero restriction | Set `android:readPermission` (and `writePermission` if applicable), or use URI-level grants (`grantUriPermission`) for one-off, scoped sharing instead of blanket export |
| Doing real work directly in `BroadcastReceiver.onReceive()` | `onReceive()` runs on the main thread and the receiver is only guaranteed to stay alive for a few seconds — anything long-running gets killed mid-execution | Use `onReceive()` only to kick off a `WorkManager` request or start a service; never do blocking I/O directly inside it |

---

## 10. Interview Q&A

**Q: When would you choose a foreground service over WorkManager?**
> When the work is happening *right now* and the user needs to be continuously aware of it via a persistent notification — active file sync, music playback, an ongoing navigation session, a phone call. WorkManager is for deferrable work where exact timing doesn't matter to the user and no persistent notification is required by itself (though as of recent WorkManager versions, a long-running worker can itself request foreground execution via `setForeground()`, which is exactly what this project's `FileSyncWorker` does).

**Q: Why does `PeriodicWorkRequest` have a 15-minute minimum interval?**
> It's a hard floor enforced by the underlying `JobScheduler` to prevent apps from draining battery with overly frequent background wakeups — the OS wants to batch and schedule background work efficiently across all apps, not honor arbitrary tight intervals.

**Q: What happens to your Worker if a required constraint (e.g. WiFi) stops being true while `doWork()` is running?**
> WorkManager stops the Worker (calling `onStopped()`) rather than letting it continue on a network it's no longer supposed to use, and re-queues the work to run again once the constraint is satisfied again. It's proactive monitoring, not just a pre-check at start time.

**Q: What's the difference between `Result.retry()` and `Result.failure()`?**
> `Result.retry()` tells WorkManager the failure was likely transient (network blip, temporary server error) and to reschedule the Worker according to its backoff policy. `Result.failure()` marks the work permanently failed — no further retries, and if it's part of a chain, all downstream work in that chain is also marked failed and never runs.

**Q: How does WorkManager pass data between chained workers?**
> Each `Worker.doWork()` returns a `Result` that can carry an output `Data` object (`Result.success(workDataOf(...))`). When workers are chained with `.then()`, WorkManager automatically merges the previous worker's output `Data` into the next worker's `inputData`.

**Q: Why are manifest-declared (static) BroadcastReceivers unreliable for something like connectivity changes on modern Android?**
> Since Android 8 (API 26), the OS blocks most implicit broadcasts — including connectivity change broadcasts — from reaching apps that aren't currently running, specifically to stop apps from being woken up constantly in the background and draining battery. A statically registered receiver for `CONNECTIVITY_ACTION` will simply never fire if the app isn't already running.

**Q: What's the recommended replacement for listening to connectivity changes today?**
> `ConnectivityManager.NetworkCallback`, registered dynamically in code (e.g. `registerNetworkCallback` or `registerDefaultNetworkCallback`) while a relevant component is alive. For anything that must react even when the app is completely closed, pair it with WorkManager constraints (`setRequiredNetworkType`) rather than depending on a receiver waking the app up.

**Q: What is a ContentProvider actually for, and how is it different from just sharing a file path?**
> It's a structured, permission-gated abstraction layer over your app's data, addressed by `content://` URIs, that lets other apps (or your own components) query, read, and sometimes write data without needing direct file-system access or knowledge of your storage implementation (database vs. files vs. network). It enforces permissions at the framework level and lets you change your internal storage without breaking consumers, since they only ever see the URI-based contract.

**Q: If you set `android:exported="true"` on your ContentProvider with no permission attributes, what's the risk?**
> Any other app installed on the device can query, and potentially modify, your provider's data with no restriction at all — a significant privacy/security hole. You should set `android:readPermission`/`android:writePermission` to a custom permission other apps must declare and be granted, or scope access narrowly with temporary URI permission grants instead of a blanket export.

**Q: Why shouldn't you perform long-running work directly inside `BroadcastReceiver.onReceive()`?**
> `onReceive()` executes on the main thread and the OS only guarantees the receiver (and its process, if it has no other active components) stays alive for a short window — roughly 10 seconds — after which it may be killed as an ANR or simply torn down. Anything beyond quick, synchronous work should be handed off to WorkManager or used to start a (foreground, if needed) service instead.

---

## 11. Deliverable Checklist

- [ ] `FileSyncWorker` (`CoroutineWorker`) performs the sync and calls `setForeground()` to show a live progress notification
- [ ] `PeriodicWorkRequest` scheduled every 15 minutes via `enqueueUniquePeriodicWork` with `ExistingPeriodicWorkPolicy.KEEP`
- [ ] Constraints applied: `NetworkType.UNMETERED` (WiFi only) + `setRequiresBatteryNotLow(true)`
- [ ] Work chain implemented: `CompressWorker` → `UploadWorker` → `NotifyWorker`, output `Data` passed between each
- [ ] `ConnectivityManager.NetworkCallback` (or dynamically registered `BroadcastReceiver`) triggers an immediate `OneTimeWorkRequest` on reconnect
- [ ] Kill the app process manually (or via `adb shell am kill`) mid-sync and verify the scheduled periodic work still resumes on schedule
- [ ] `FileSyncProvider` exposes synced file metadata + bytes via `content://` URIs, gated by a custom read permission
- [ ] A second, separate test app queries `FileSyncProvider` via `ContentResolver.query()` and reads a file via `openInputStream()`, proving cross-app access works end to end
