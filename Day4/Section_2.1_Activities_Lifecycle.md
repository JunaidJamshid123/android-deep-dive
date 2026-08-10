# SECTION 2.1 — Activities & Lifecycle

**Concepts covered:** lifecycle callbacks, lifecycle-aware components, configuration changes, task & back stack, launch modes, intent filters

**Project:** LifecycleLogger App — an app with multiple screens that visually logs every lifecycle event to an on-screen timeline, plus a media player that pauses/resumes correctly with lifecycle changes.

---

## 1. Theory: What is an Activity?

An `Activity` is a single, focused screen a user can interact with. Android manages Activities through a **stack** (the back stack) and moves each Activity through a well-defined **lifecycle** as the user navigates, rotates the device, switches apps, or the system reclaims memory.

You never call lifecycle methods yourself — the Android OS calls them on your Activity at the right moment. Your job is to **override** them and put the right work in the right callback.

### Why the lifecycle matters

- Release resources (camera, media player, sensors, listeners) when the screen isn't visible, to save battery.
- Save and restore UI state across configuration changes (rotation, language change, multi-window resize).
- Avoid memory leaks (e.g., a running thread holding a reference to a destroyed Activity).
- Correctly resume playback, network calls, or animations only when the user can actually see them.

---

## 2. The Full Lifecycle — Diagram

```
                         ┌─────────────────┐
                         │   Activity       │
                         │   Launched       │
                         └────────┬─────────┘
                                  │
                                  ▼
                           ┌────────────┐
                           │ onCreate() │  ← one-time setup: inflate layout,
                           └─────┬──────┘     bind views, restore saved state
                                 │
                                 ▼
                           ┌────────────┐
                           │  onStart() │  ← Activity becomes visible
                           └─────┬──────┘     (not yet interactive)
                                 │
                                 ▼
                           ┌────────────┐
              ┌───────────┤ onResume() │  ← Activity is in foreground,
              │           └─────┬──────┘     user can interact with it
              │                 │
              │           ┌─────▼──────┐
              │           │  RUNNING   │  ← app in "Resumed" state
              │           └─────┬──────┘     (the only state where the user
              │                 │              is actively interacting)
              │                 ▼
              │           ┌────────────┐
              │           │ onPause()  │  ← partially obscured (dialog shown,
              │           └─────┬──────┘     multi-window loses focus, etc.)
              │                 │
              │        ┌────────┴────────┐
              │        │                 │
              │   user returns      Activity no longer visible
              │        │                 │
              │        ▼                 ▼
              │  ┌────────────┐   ┌────────────┐
              └──┤ onResume() │   │  onStop()  │  ← Activity fully hidden
                 └────────────┘   └─────┬──────┘     (another app in front,
                                         │              home button pressed)
                                  ┌──────┴───────┐
                                  │              │
                            user returns    system needs
                                  │           memory / user
                                  ▼           finishes Activity
                            ┌────────────┐         │
                            │onRestart() │         ▼
                            └─────┬──────┘   ┌─────────────┐
                                  │           │ onDestroy() │  ← final cleanup,
                                  ▼           └─────────────┘     Activity object
                            ┌────────────┐                          is gone
                            │ onStart()  │
                            └────────────┘
```

### The three "loops" to remember

| Loop | Callbacks | Meaning |
|---|---|---|
| **Entire lifetime** | `onCreate()` → `onDestroy()` | Everything between creation and destruction |
| **Visible lifetime** | `onStart()` → `onStop()` | User can see the Activity (may not be in front) — can repeat many times |
| **Foreground lifetime** | `onResume()` → `onPause()` | User is actively interacting — the shortest, most frequent loop |

### Callback responsibilities cheat sheet

| Callback | Typical use |
|---|---|
| `onCreate(savedInstanceState)` | Inflate layout, initialize view references, restore small UI state, one-time setup (ViewModel creation, observers) |
| `onStart()` | Start things that should run while visible (e.g., register a broadcast receiver, start UI animations) |
| `onResume()` | Start camera preview, resume media playback, start location updates, grab exclusive resources |
| `onPause()` | Pause media playback, release camera, commit unsaved data, stop animations — **must be fast**, it blocks the next Activity from resuming |
| `onStop()` | Unregister listeners, stop heavier background work, save data to persistent storage |
| `onRestart()` | Called only when coming back from Stopped state, right before `onStart()` again |
| `onDestroy()` | Final cleanup — cancel coroutines/threads, release all remaining resources |

---

## 3. Code: Logging Every Lifecycle Event

This is the core of Project 4 — a `BaseLoggingActivity` that every screen in the app extends, so every screen automatically reports its lifecycle events to a shared, on-screen timeline.

```kotlin
// LifecycleEvent.kt
data class LifecycleEvent(
    val activityName: String,
    val event: String,
    val timestamp: Long = System.currentTimeMillis()
)

// LifecycleTimelineStore.kt
// A simple in-memory singleton (backed by a Flow) that every Activity writes to,
// and the on-screen timeline overlay reads from.
object LifecycleTimelineStore {
    private val _events = MutableStateFlow<List<LifecycleEvent>>(emptyList())
    val events: StateFlow<List<LifecycleEvent>> = _events

    fun log(activityName: String, event: String) {
        _events.value = _events.value + LifecycleEvent(activityName, event)
    }
}
```

```kotlin
// BaseLoggingActivity.kt
abstract class BaseLoggingActivity : AppCompatActivity() {

    private val tag get() = this::class.simpleName ?: "Activity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LifecycleTimelineStore.log(tag, "onCreate")
    }

    override fun onStart() {
        super.onStart()
        LifecycleTimelineStore.log(tag, "onStart")
    }

    override fun onResume() {
        super.onResume()
        LifecycleTimelineStore.log(tag, "onResume")
    }

    override fun onPause() {
        LifecycleTimelineStore.log(tag, "onPause")
        super.onPause()
    }

    override fun onStop() {
        LifecycleTimelineStore.log(tag, "onStop")
        super.onStop()
    }

    override fun onRestart() {
        super.onRestart()
        LifecycleTimelineStore.log(tag, "onRestart")
    }

    override fun onDestroy() {
        LifecycleTimelineStore.log(tag, "onDestroy")
        super.onDestroy()
    }
}
```

Every screen in the app then just does:

```kotlin
class HomeActivity : BaseLoggingActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState) // logs "onCreate" automatically
        setContentView(R.layout.activity_home)
    }
}
```

The on-screen timeline overlay (a `RecyclerView` in a floating `Fragment` or a Compose overlay) simply collects `LifecycleTimelineStore.events` and renders a scrolling log with timestamps — this is what makes the lifecycle **visible** instead of theoretical.

---

## 4. Lifecycle-Aware Components: the "Fake Battery Monitor"

Instead of manually calling `start()`/`stop()` on a helper class from every lifecycle callback (error-prone — easy to forget one), Android's **Lifecycle** library lets a class observe the Activity's lifecycle directly and react on its own.

### Diagram: Observer pattern

```
   Activity/Fragment (LifecycleOwner)
   ┌───────────────────────────────┐
   │  lifecycle.addObserver(obs)   │
   └───────────────┬───────────────┘
                    │ dispatches ON_CREATE, ON_START,
                    │ ON_RESUME, ON_PAUSE, ON_STOP, ON_DESTROY
                    ▼
   ┌───────────────────────────────┐
   │   BatteryMonitorObserver       │
   │   (implements DefaultLifecycle │
   │    Observer)                   │
   │                                 │
   │  onStart()  -> start polling   │
   │  onStop()   -> stop polling    │
   └───────────────────────────────┘
```

### Code

```kotlin
// FakeBatteryMonitor.kt
class FakeBatteryMonitor(
    private val onLevelChanged: (Int) -> Unit
) : DefaultLifecycleObserver {

    private var handler: Handler? = null
    private var runnable: Runnable? = null
    private var level = 100

    override fun onStart(owner: LifecycleOwner) {
        LifecycleTimelineStore.log("BatteryMonitor", "started polling")
        handler = Handler(Looper.getMainLooper())
        runnable = object : Runnable {
            override fun run() {
                level = (level - 1).coerceAtLeast(0)
                onLevelChanged(level)
                handler?.postDelayed(this, 2000)
            }
        }
        handler?.post(runnable!!)
    }

    override fun onStop(owner: LifecycleOwner) {
        LifecycleTimelineStore.log("BatteryMonitor", "stopped polling")
        runnable?.let { handler?.removeCallbacks(it) }
    }
}
```

```kotlin
// Attaching it inside any Activity — no manual lifecycle calls needed
class HomeActivity : BaseLoggingActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val monitor = FakeBatteryMonitor { level ->
            batteryTextView.text = "Battery: $level%"
        }
        lifecycle.addObserver(monitor)
    }
}
```

**Key idea:** the observer decides what "start"/"stop" means for itself. The Activity doesn't need to know the monitor exists after registering it — it will automatically start polling in `onStart()` and stop in `onStop()`, and it can never leak because it's tied to the owner's lifecycle.

---

## 5. Configuration Changes (Rotation)

### What actually happens on rotation

```
   Screen rotated
        │
        ▼
┌──────────────────┐
│   onPause()       │
│   onStop()        │
│   onDestroy()     │   ← the ENTIRE Activity is destroyed...
└─────────┬─────────┘
          │
          ▼
┌──────────────────┐
│   onCreate()      │   ← ...and recreated from scratch, with a
│   onStart()       │      new Configuration (new orientation,
│   onResume()      │      screen size, etc.)
└──────────────────┘
```

By default, Android destroys and recreates the Activity on rotation so it can reload resources for the new configuration (e.g., a different `layout-land/` XML). This is why **unsaved state is lost on rotation unless you explicitly preserve it**.

### Two ways to preserve state

**A) `onSaveInstanceState` / `savedInstanceState` (small, transient UI state — e.g. text field content, scroll position)**

```kotlin
class CounterActivity : BaseLoggingActivity() {
    private var count = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_counter)

        count = savedInstanceState?.getInt("count") ?: 0
        updateCounterText()

        incrementButton.setOnClickListener {
            count++
            updateCounterText()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("count", count)
    }

    private fun updateCounterText() {
        counterTextView.text = "Count: $count"
    }
}
```

**B) `ViewModel` (survives configuration changes automatically, no Bundle needed — the right tool for anything beyond trivial primitives)**

```kotlin
class CounterViewModel : ViewModel() {
    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count

    fun increment() { _count.value++ }
}

class CounterActivity : BaseLoggingActivity() {
    private val viewModel: CounterViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_counter)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.count.collect { counterTextView.text = "Count: $it" }
            }
        }
        incrementButton.setOnClickListener { viewModel.increment() }
    }
}
```

> A `ViewModel` survives because it's stored in the `ViewModelStore`, which is retained by the framework across the destroy/recreate cycle triggered by a config change — it is **not** retained across a "true" destruction (e.g., user presses Back, or the system kills the process).

**How to verify in the project:** rotate the device with the counter running. With `ViewModel`, the count keeps incrementing correctly across rotation; without it (a plain `var`), the count silently resets to 0 on every rotation — a great before/after demo for the timeline log too, since you'll visibly see `onDestroy` → `onCreate` fire.

---

## 6. Launch Modes: `standard` vs `singleTask`

Launch mode controls **whether a new instance of an Activity is created**, or an existing instance is reused, when you start it.

### Diagram: `standard` (default)

Every `startActivity()` call creates a **new instance**, even if one already exists on the stack.

```
Back stack (top = most recent, visible screen)

  startActivity(CounterActivity) x3
  ─────────────────────────────────►

  [Home] ─► [Home, Counter#1] ─► [Home, Counter#1, Counter#2] ─► [Home, Counter#1, Counter#2, Counter#3]

  Pressing Back pops ONE instance at a time:
  Counter#3 → Counter#2 → Counter#1 → Home
```

### Diagram: `singleTask`

Only **one instance** of the Activity can exist in the task. If it already exists, Android brings it to the front and **clears everything above it** (calling `onNewIntent()` on the existing instance instead of creating a new one).

```
Back stack

  [Home, Counter#1, ScreenB, ScreenC]

  startActivity(CounterActivity)  ← Counter is singleTask, already exists
  ────────────────────────────────►

  [Home, Counter#1]   ← ScreenB and ScreenC are destroyed/popped,
                          Counter#1.onNewIntent() is called, no new instance
```

### Manifest declaration

```xml
<!-- AndroidManifest.xml -->
<activity
    android:name=".StandardCounterActivity"
    android:launchMode="standard"
    android:exported="false" />

<activity
    android:name=".SingleTaskCounterActivity"
    android:launchMode="singleTask"
    android:exported="false" />
```

### Code: handling reuse with `onNewIntent`

```kotlin
class SingleTaskCounterActivity : BaseLoggingActivity() {
    private var launchCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_counter)
        launchCount++
        updateUi()
    }

    // Called instead of onCreate() when the existing singleTask instance
    // is reused rather than a new one created.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        LifecycleTimelineStore.log("SingleTaskCounter", "onNewIntent (reused instance)")
        launchCount++
        updateUi()
    }

    private fun updateUi() {
        counterTextView.text = "Instance reused $launchCount time(s)"
    }
}
```

**Project demo:** put two buttons on the Home screen — "Launch Standard Counter" and "Launch SingleTask Counter" — tap each three times, then look at the on-screen back-stack overlay (Section 7) to see standard stacking 3 deep while singleTask stays at 1 instance and just logs `onNewIntent` repeatedly.

### Other launch modes (for completeness)

| Mode | Behavior |
|---|---|
| `standard` | Always creates a new instance |
| `singleTop` | Reuses the instance only if it's already at the **top** of the stack; otherwise creates new |
| `singleTask` | Only one instance in the whole task; clears everything above it when reused |
| `singleInstance` | Like `singleTask`, but the Activity gets its **own separate task**, exclusively |

---

## 7. Task & Back Stack Visualization

### Theory

A **task** is a collection of Activities arranged in a stack (LIFO — last in, first out). Pressing the system Back button pops the top Activity off the stack and destroys it, revealing the one beneath. The Home button doesn't destroy the task — it just moves it to the background, preserving the whole stack.

```
             ┌─────────────────────────┐
   Top ───►  │  ScreenC  (visible,     │  ← onResume()
             │            interactive) │
             ├─────────────────────────┤
             │  ScreenB  (stopped)     │  ← onStop() already called
             ├─────────────────────────┤
             │  ScreenA  (stopped)     │  ← onStop() already called
             ├─────────────────────────┤
             │  Home     (stopped)     │
             └─────────────────────────┘
                     TASK STACK

  Back pressed on ScreenC:
    ScreenC.onPause() → ScreenC.onStop() → ScreenC.onDestroy()
    ScreenB.onRestart() → ScreenB.onStart() → ScreenB.onResume()
```

### Code: a custom overlay that visualizes the stack in real time

Rather than reading the real OS task stack (which apps can't introspect directly), the project simulates it by pushing/popping onto a shared list every time an Activity starts or is destroyed — driven by the same `LifecycleTimelineStore` events already being logged.

```kotlin
// BackStackVisualizer.kt
object BackStackVisualizer {
    private val _stack = MutableStateFlow<List<String>>(emptyList())
    val stack: StateFlow<List<String>> = _stack

    fun push(activityName: String) {
        _stack.value = _stack.value + activityName
    }

    fun pop(activityName: String) {
        _stack.value = _stack.value.dropLastWhile { it == activityName }
    }
}
```

```kotlin
// Extend BaseLoggingActivity to also drive the visualizer
abstract class BaseLoggingActivity : AppCompatActivity() {
    private val tag get() = this::class.simpleName ?: "Activity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LifecycleTimelineStore.log(tag, "onCreate")
        BackStackVisualizer.push(tag)
    }

    override fun onDestroy() {
        LifecycleTimelineStore.log(tag, "onDestroy")
        BackStackVisualizer.pop(tag)
        super.onDestroy()
    }
    // ...other callbacks unchanged
}
```

A small floating overlay `View` (or a Compose `Box` with `elevation`) collects `BackStackVisualizer.stack` and draws it as a vertical list of cards, redrawing every time the flow emits — giving a live, visual "X-ray" of the back stack as you navigate.

---

## 8. Intent Filters & Deep Links

### Theory

An **Intent Filter** declares what implicit Intents (including deep-link URLs) an Activity can respond to. When the user taps a link — in a browser, another app, or a notification — Android checks all installed apps' intent filters and offers to open the matching Activity directly.

### Diagram

```
   User taps:  lifecyclelogger://open/counter?startAt=5

                        │
                        ▼
        ┌───────────────────────────────┐
        │  Android checks all installed  │
        │  apps' <intent-filter> tags     │
        └───────────────┬────────────────┘
                        │ match found: scheme "lifecyclelogger",
                        │ host "open", path "/counter"
                        ▼
        ┌───────────────────────────────┐
        │   DeepLinkCounterActivity      │
        │   onCreate() receives the      │
        │   Intent with the full URI     │
        └───────────────────────────────┘
```

### Manifest declaration

```xml
<activity
    android:name=".DeepLinkCounterActivity"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />

        <!-- lifecyclelogger://open/counter?startAt=5 -->
        <data
            android:scheme="lifecyclelogger"
            android:host="open"
            android:pathPrefix="/counter" />
    </intent-filter>
</activity>
```

### Code: reading the deep link data

```kotlin
class DeepLinkCounterActivity : BaseLoggingActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_counter)
        handleDeepLink(intent)
    }

    // If this Activity is singleTop/singleTask and already open,
    // a new deep link arrives here instead of a new onCreate().
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent) {
        val uri: Uri? = intent.data
        if (uri != null) {
            val startAt = uri.getQueryParameter("startAt")?.toIntOrNull() ?: 0
            LifecycleTimelineStore.log("DeepLinkCounter", "opened via deep link, startAt=$startAt")
            counterTextView.text = "Started via deep link at $startAt"
        }
    }
}
```

### Testing the deep link from adb (no browser needed)

```bash
adb shell am start -a android.intent.action.VIEW \
  -d "lifecyclelogger://open/counter?startAt=5" \
  com.example.lifecyclelogger
```

---

## 9. Bonus: Media Player That Survives Lifecycle Changes Correctly

Ties together lifecycle callbacks + lifecycle-aware components for the "media player" requirement.

```kotlin
class MediaPlayerActivity : BaseLoggingActivity() {
    private lateinit var player: ExoPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_player)
        player = ExoPlayer.Builder(this).build().also {
            playerView.player = it
            it.setMediaItem(MediaItem.fromUri(SAMPLE_AUDIO_URI))
            it.prepare()
        }
    }

    override fun onStart() {
        super.onStart()
        // Android 7+ supports multi-window / picture-in-picture where the app
        // can be visible but not resumed — start playback here, not in onResume,
        // so PiP playback isn't interrupted unnecessarily.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) player.play()
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) player.play()
    }

    override fun onPause() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) player.pause()
        super.onPause()
    }

    override fun onStop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) player.pause()
        super.onStop()
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }
}
```

**Why the SDK-version branching:** on pre-N devices, only one app can be visible at a time, so `onPause()`/`onResume()` is the correct pause/resume boundary. From Android 7 (API 24) onward, multi-window mode means an app can be visible-but-not-focused, so pausing media in `onPause()` would wrongly interrupt a video the user can still see — `onStart()`/`onStop()` is the correct boundary instead. This nuance is exactly the kind of thing the on-screen timeline makes obvious when you test it in split-screen mode.

---

## 10. Common Pitfalls (things that trip people up in practice)

| Pitfall | Why it happens | Fix |
|---|---|---|
| Doing heavy work in `onPause()` | `onPause()` blocks the incoming Activity's `onResume()` from firing until it returns — a slow `onPause()` makes the whole app feel janky | Keep `onPause()` fast (pause a player, save a flag); do real persistence in `onStop()` on a background thread/coroutine |
| Storing large objects (Bitmaps, DB cursors, lists) in `onSaveInstanceState` | The Bundle is transported through Binder IPC with a strict size limit (~1MB, but practically much less is safe); oversized bundles crash with `TransactionTooLargeException` | Use a `ViewModel` for anything beyond small primitives/strings |
| Assuming `onDestroy()` always runs | The system can kill a backgrounded process without calling `onDestroy()` at all when it needs memory | Don't rely on `onDestroy()` for critical saves — do them in `onPause()`/`onStop()` instead |
| Leaking the Activity via a long-lived listener or singleton | A static reference, a Handler with delayed messages, or a listener registered with a system service can outlive the Activity that created it | Always unregister in the mirrored callback (`onStart`↔`onStop`, `onResume`↔`onPause`); prefer `lifecycle.addObserver()` so this happens automatically |
| Treating "not visible" (`onStop`) the same as "config change destroy" | Both call `onDestroy()`, but for very different reasons | Call `isChangingConfigurations()` inside `onDestroy()` if you need to distinguish "really going away" from "about to be recreated" |
| Forgetting `android:exported="false"` on non-deep-link activities | Since Android 12, every activity with an intent filter **must** explicitly declare `android:exported`; omitting it is a build error, and setting it `true` unnecessarily exposes the Activity to any app on the device | Only set `exported="true"` on the Activity that actually needs to handle external Intents/deep links |
| Confusing `singleTask` with `singleInstance` | Both limit to one instance, but `singleTask` still shares its task with other activities below it, while `singleInstance` gets an isolated task all to itself | Use `singleTask` for "main hub" screens (e.g. a home/dashboard); reserve `singleInstance` for rare cases like a call-screen UI that must never share a task |

---

## 11. Interview Q&A

Short, direct answers — the kind expected in a live technical interview. Good for a quick review pass before the real thing.

**Q: Walk me through what happens, callback by callback, when an app is launched, backgrounded by pressing Home, and then returned to.**
> `onCreate → onStart → onResume` (running). Home pressed: `onPause → onStop` (visible-lifetime ends, app is now backgrounded). User returns: `onRestart → onStart → onResume`. Note `onCreate` is **not** called again — the same Activity instance is reused because the process wasn't killed.

**Q: What's the difference between `onStop()` and `onDestroy()`?**
> `onStop()` means the Activity is no longer visible but its instance and state are still alive in memory — it can come back via `onRestart()`. `onDestroy()` means the Activity object is being removed entirely, either because it finished normally, the user pressed Back, or the system needs to reclaim memory. After `onDestroy()`, a "return" to that screen means a brand-new instance and a fresh `onCreate()`.

**Q: Why can't you rely on `onDestroy()` to save critical data?**
> Because the system is allowed to kill the process outright (e.g., low memory) without ever calling `onDestroy()` — it's not guaranteed to run. `onPause()`/`onStop()` are the reliable places to persist data, since at least one of them is guaranteed to run before the process can be killed.

**Q: Why does rotating the screen destroy and recreate the Activity by default?**
> Because a configuration change (orientation, screen size, locale, etc.) can mean different resources apply — e.g. a `layout-land/` XML, different drawables, different string resources for a new locale. Recreating the Activity is the simplest way to guarantee all of those are reloaded correctly.

**Q: How would you preserve a large list of objects across a rotation, and why not just use `onSaveInstanceState`?**
> Use a `ViewModel` scoped to the Activity. `ViewModel` survives the destroy/recreate cycle caused by a config change because it lives in a `ViewModelStore` retained by the framework independently of the Activity instance. `onSaveInstanceState`'s `Bundle` is transported via Binder IPC with a small size ceiling, so it's only appropriate for small primitives (an `Int`, a `String`, a scroll position) — not large or complex objects.

**Q: What's the practical difference between `standard` and `singleTask` launch modes?**
> `standard` creates a brand-new instance on every `startActivity()` call, even for an Activity that's already on the stack, so you can end up with duplicates. `singleTask` guarantees at most one instance across the whole task — if it already exists, Android brings it to front, clears everything stacked above it, and delivers the new Intent via `onNewIntent()` instead of creating another instance.

**Q: What is `onNewIntent()` and when is it called instead of `onCreate()`?**
> It's called when an existing Activity instance is reused to handle a new Intent — happens with `singleTop` (if it's already at the top of the stack) and `singleTask`/`singleInstance` (if an instance already exists anywhere). You must override it and manually update the UI/state from the new Intent, since `onCreate()` won't run again.

**Q: What's a task, and what's the difference between pressing Back and pressing Home?**
> A task is a stack of Activities the user navigates through as a coherent unit. Back pops and **destroys** the top Activity, revealing the one beneath. Home moves the **entire task** to the background without destroying anything — the whole stack (and all its Activity states) is preserved and can be restored exactly as it was.

**Q: How does Android decide which app opens for a given deep link URL?**
> It matches the Intent's action, category, and data (scheme/host/path) against every installed app's declared `<intent-filter>` elements. If exactly one app matches, it opens directly; if multiple apps match, the user is shown a disambiguation dialog (unless the app uses Android App Links with server-side verification, which grants exclusive default handling).

**Q: What is a `LifecycleObserver` and why is it preferable to putting logic directly in `onStart()`/`onStop()`?**
> A `LifecycleObserver` (typically implemented via `DefaultLifecycleObserver`) lets a separate class react to an owner's (Activity/Fragment) lifecycle without the owner needing to manually call methods on it in every callback. This decouples the "what to do on start/stop" logic from the Activity itself, avoids forgetting to unregister/stop something, and makes the component reusable across multiple screens — you just call `lifecycle.addObserver(component)` once.

**Q: A junior dev pauses video playback in `onPause()`. Why might that be wrong on a modern device?**
> On Android 7+ (API 24), multi-window/split-screen and picture-in-picture mean an Activity can be visible but not focused (`onPause()` fires) while the user can still see it playing. Pausing there interrupts something the user is actively watching. The correct boundary on those versions is `onStop()` (truly no longer visible) rather than `onPause()`.

**Q: What's the risk of an unregistered listener/callback tied to an Activity, and how does the lifecycle library help?**
> If a listener (e.g., a location callback, a `Handler` with a delayed `Runnable`, an event bus subscription) isn't unregistered when the Activity stops being visible, it can hold a strong reference to the Activity and prevent it from being garbage collected — a memory leak — and can also crash if it tries to touch destroyed views. Lifecycle-aware components solve this structurally: they hook into `onStart`/`onStop`/`onDestroy` themselves, so the cleanup can't be forgotten by a future edit to the Activity.

---

## 12. Deliverable Checklist

- [ ] `BaseLoggingActivity` logs all 7 callbacks with timestamps to a shared store
- [ ] On-screen scrolling timeline overlay renders the log live
- [ ] `FakeBatteryMonitor` implemented as a `DefaultLifecycleObserver`, attached via `lifecycle.addObserver()`
- [ ] Rotation test: `ViewModel`-backed counter survives rotation; plain-field counter resets (both demoed side by side)
- [ ] `standard` vs `singleTask` Counter activities, launched repeatedly, difference visible in stack overlay + timeline
- [ ] `BackStackVisualizer` overlay shows push/pop in real time as screens are opened and Back is pressed
- [ ] Deep link `lifecyclelogger://open/counter?startAt=N` opens `DeepLinkCounterActivity` directly, tested via `adb`
- [ ] Media player pauses/resumes correctly using the `onStart`/`onStop` vs `onPause`/`onResume` split, verified in split-screen/multi-window mode
