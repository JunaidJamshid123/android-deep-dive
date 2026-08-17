# SECTION 3.1 — Compose Fundamentals

**Concepts:** composable functions, state, remember, recomposition, side effects, composition locals
**Project:** CounterBoard App (Project 7)

---

## TABLE OF CONTENTS

1. Composable Functions
2. State in Compose
3. remember{} and mutableStateOf
4. rememberSaveable
5. Recomposition (why & how)
6. Side Effects: LaunchedEffect, DisposableEffect, SideEffect
7. CompositionLocal
8. derivedStateOf
9. Project Breakdown — CounterBoard App
10. Common Pitfalls
11. Interview Q&A

---

## 1. COMPOSABLE FUNCTIONS

### Theory

A **composable function** is a regular Kotlin function annotated with `@Composable`
that describes a piece of UI. Compose is **declarative**: instead of telling the
system "create a TextView, then update its text," you *describe what the UI
should look like for a given state*, and Compose figures out how to update the
actual UI tree when that state changes.

Key properties of composables:

- They can be called **only from other composables** (or from a Compose entry
  point like `setContent {}`).
- They can execute **in any order**, **skip execution** (skippability), or
  **run multiple times** (recomposition) — so they must be **free of side
  effects** in their direct body. Side effects belong in the effect APIs
  (Section 6).
- They are **idempotent**: calling them again with the same inputs should
  produce the same UI description.
- They emit UI by calling other composables — the "leaves" of that tree are
  things like `Text`, `Box`, `Row`, `Column`, `Canvas`, etc.

### Diagram — Composable Tree

```
setContent {
    CounterBoardApp()                 <- root composable
}

CounterBoardApp
 └── CounterList(counters)
      ├── Counter(counter = c1)
      │     ├── Text(c1.name)
      │     ├── Text(c1.count)
      │     └── HistoryChart(c1.history)
      ├── Counter(counter = c2)
      │     ├── Text(c2.name)
      │     ├── Text(c2.count)
      │     └── HistoryChart(c2.history)
      └── AddCounterButton()
```

Each node in this tree is a function call. Compose keeps an internal
representation called the **slot table**, which remembers what was emitted
last time, so it can diff and patch instead of rebuilding everything.

### Code Example

```kotlin
@Composable
fun CounterBoardApp() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            CounterList()
        }
    }
}

@Composable
fun Counter(
    counter: CounterState,
    onIncrement: () -> Unit,
    onLongPress: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(8.dp)
            .combinedClickable(
                onClick = onIncrement,
                onLongClick = onLongPress
            )
    ) {
        Text(text = counter.name, fontWeight = FontWeight.Bold)
        Text(text = "Count: ${counter.count}")
        HistoryChart(history = counter.history)
    }
}
```

Notice: `Counter` takes plain data + lambdas as parameters. It does not own
mutable state itself — that keeps it **stateless and reusable** (this is the
"state hoisting" pattern you'll lean on heavily in Project 7).

---

## 2. STATE IN COMPOSE

### Theory

"State" is any value that can change over time and that the UI needs to
reflect. In Compose, UI = f(state). If state changes, Compose re-invokes the
composables that read that state (recomposition) so the UI tree matches the
new state.

Compose observes state through `State<T>` objects (created via
`mutableStateOf`). When a composable **reads** a `State<T>`'s `.value` during
composition, Compose subscribes that composable to future changes of that
state. This is why *where* you read state matters — read it deep in the tree,
and only that part recomposes.

### Diagram — Unidirectional Data Flow (UDF)

```
        ┌─────────────────────────────┐
        │        State (source)        │
        │   count: Int, history: List   │
        └───────────────┬──────────────┘
                         │ read (State -> UI)
                         ▼
        ┌─────────────────────────────┐
        │         Composable UI        │
        │   Counter(count, onTap)      │
        └───────────────┬──────────────┘
                         │ event (UI -> Event)
                         ▼
        ┌─────────────────────────────┐
        │     Event handler updates     │
        │   count = count + step        │
        └───────────────┬──────────────┘
                         │
                         └──────► loops back to State
```

This one-way loop (state down, events up) is the backbone of every composable
you'll write in the CounterBoard app: `Counter` never mutates its own count;
it reports "I was tapped" upward, and the owner of the state decides what to
do.

---

## 3. remember{} AND mutableStateOf

### Theory

`remember` is a composition-scoped cache. Normally, every time a composable
function runs (recomposes), all local `val`/`var` inside it are recreated
from scratch — a plain `var count = 0` would reset to `0` on every
recomposition. `remember { ... }` tells Compose: "compute this once, store it
in the slot table, and hand me back the same instance on future
recompositions (as long as this call site survives)."

`mutableStateOf(initialValue)` creates an observable holder (`State<T>` /
`MutableState<T>`). Combine the two:

```kotlin
var count by remember { mutableStateOf(0) }
```

- `remember` → survives recomposition (but NOT configuration change / process
  death — see rememberSaveable).
- `mutableStateOf` → makes Compose aware that `count` changing should trigger
  recomposition of readers.
- `by` (Kotlin property delegate) → lets you use `count` directly instead of
  `count.value`.

### Diagram — Lifecycle of a `remember`ed value

```
First composition:
  remember{ mutableStateOf(0) }  ──► creates MutableState(0), stores in slot table

User taps → count++  ──► MutableState value becomes 1
                       │
                       ▼
Compose schedules recomposition of readers of `count`
                       │
                       ▼
Recomposition runs:
  remember{ ... }  ──► slot already has a value ──► returns EXISTING state (1)
                        (the lambda inside remember is NOT re-run)
```

### Code Example

```kotlin
@Composable
fun CounterHost(initial: CounterState) {
    var count by remember { mutableStateOf(initial.count) }
    var history by remember { mutableStateOf(initial.history) }

    Counter(
        counter = initial.copy(count = count, history = history),
        onIncrement = {
            count += initial.step
            history = history + count
        },
        onLongPress = { /* open edit dialog */ }
    )
}
```

**Gotcha:** `remember` is keyed to the *call site* (position in the
composition + any keys you pass in, e.g. `remember(counterId) { ... }`). If
you pass a `key`, changing the key forces `remember` to throw away the old
value and recompute — useful when a `Counter`'s identity changes (e.g. list
reordering) and you don't want to keep stale state.

---

## 4. rememberSaveable

### Theory

`remember` survives recomposition, but **not**:
- configuration changes (screen rotation, dark/light mode switch, language
  change) — these can destroy and recreate the Activity,
- process death (Android kills your app in the background to reclaim
  memory).

`rememberSaveable` fixes this by saving the value into the **Bundle-based
saved-instance-state** mechanism (the same one `onSaveInstanceState` uses),
via a `Saver`. For simple types (Int, String, Boolean, Parcelable, etc.) it
works automatically. For custom classes, you provide a `Saver` (or use
`@Parcelize` and a `Parcelable` data class).

### Diagram

```
                 remember                 rememberSaveable
Recomposition        ✅ survives              ✅ survives
Rotation              ❌ lost                  ✅ survives (saved to Bundle)
Process death         ❌ lost                  ✅ survives (saved to Bundle)
New navigation entry  ❌ lost                  ❌ lost (different backstack entry)
```

### Code Example

```kotlin
data class CounterState(
    val id: String,
    val name: String,
    val color: Long,
    val step: Int,
    val count: Int,
    val history: List<Int>
)

// Custom Saver for a class that isn't automatically Bundle-able
val CounterStateSaver = listSaver<CounterState, Any>(
    save = { listOf(it.id, it.name, it.color, it.step, it.count, it.history) },
    restore = {
        @Suppress("UNCHECKED_CAST")
        CounterState(
            id = it[0] as String,
            name = it[1] as String,
            color = it[2] as Long,
            step = it[3] as Int,
            count = it[4] as Int,
            history = it[5] as List<Int>
        )
    }
)

@Composable
fun CounterScreen(initial: CounterState) {
    var counter by rememberSaveable(stateSaver = CounterStateSaver) {
        mutableStateOf(initial)
    }
    // counter survives rotation now
}
```

For Project 7: use `rememberSaveable` for each counter's `count` and `step`
so rotating the phone mid-tap-fest doesn't reset progress. History (a list)
either needs a custom `Saver`, or you keep it in a `ViewModel` instead (more
realistic for "real" apps — Bundle has a ~1MB size limit and isn't meant for
large/growing lists).

---

## 5. RECOMPOSITION

### Theory

Recomposition is Compose re-running the composable functions that read a
piece of state that changed, so the UI reflects the new value. Critically:

- Recomposition is **not** "redraw the whole screen." Compose performs
  **smart recomposition**: it only re-invokes composables whose *inputs*
  (parameters + read state) actually changed. This is called **skipping**.
- A composable is **skippable** if the Compose compiler can prove that all
  of its parameters are stable and, on this recomposition, equal to last
  time's values (`Modifier`, primitives, `@Immutable`/`@Stable` classes,
  etc.). Mutable classes with `var` public fields are NOT stable by default,
  which disables skipping and hurts performance.
- Recomposition can happen **out of order** and **concurrently is not
  guaranteed** — never rely on side effects or ordering inside the body of a
  composable.
- Reading state **lower in the tree** scopes the invalidation to a smaller
  region ("state reads should be as narrow / as low as possible").

### Diagram — Why only ONE counter recomposes

```
CounterList(counters: List<CounterState>)
   │
   ├── Counter(c1)   <- reads c1.count only
   ├── Counter(c2)   <- reads c2.count only
   └── Counter(c3)   <- reads c3.count only

User taps Counter #2:
   c2.count changes (State object owned by c2's remember)

Recomposition scope:
   Counter(c1)  -> SKIPPED (its inputs unchanged)
   Counter(c2)  -> RECOMPOSED (the State it reads changed)
   Counter(c3)  -> SKIPPED (its inputs unchanged)
```

This is only true if each `Counter`'s state is **hoisted per-item** (each
counter owns its own `MutableState`), NOT if all counters share one big
mutable list stored in a single `MutableState<List<CounterState>>` — in that
case, replacing the list creates a **new list instance**, which is a new
input to `CounterList`, and (depending on how you iterate/key) can cause
broader recomposition. That's exactly the kind of thing Project 7 is
designed to make you feel firsthand.

### Code Example — using `key()` to scope recomposition correctly in a list

```kotlin
@Composable
fun CounterList(counters: List<CounterState>, viewModel: CounterBoardViewModel) {
    LazyColumn {
        items(items = counters, key = { it.id }) { counter ->
            // `key` tells Compose "this row IS counter.id" across
            // reorderings/insertions/removals, so remember{} state inside
            // Counter() for this id is preserved / correctly discarded.
            Counter(
                counter = counter,
                onIncrement = { viewModel.increment(counter.id) },
                onLongPress = { viewModel.startEditing(counter.id) }
            )
        }
    }
}
```

---

## 6. SIDE EFFECTS

### Theory — why we need effect APIs at all

Composable function bodies must be **side-effect free** because they can run
zero, one, or many times, in any order, and be abandoned mid-composition.
Anything that "reaches outside" the UI description — starting a coroutine,
registering a listener, logging analytics, playing a sound — is a **side
effect**, and must be run through one of Compose's effect APIs, which are
tied explicitly to the **composition lifecycle** rather than to "the function
ran."

### 6a. LaunchedEffect

Runs a **suspend** coroutine, scoped to the composition. It starts when it
first enters composition, and **restarts** (cancelling the old coroutine)
whenever any of its **keys** change. It's cancelled automatically when the
composable leaves composition.

```
Diagram — LaunchedEffect lifecycle

enters composition ──► launches coroutine block
        │
        ▼
key changes? ──yes──► cancel old coroutine ──► launch NEW coroutine
        │no
        ▼
   keeps running
        │
leaves composition ──► coroutine cancelled
```

**Use in Project 7:** play a sound / trigger a small animation when a
counter's count crosses a milestone (e.g. multiple of 10).

```kotlin
@Composable
fun Counter(counter: CounterState, onIncrement: () -> Unit, onLongPress: () -> Unit) {
    val milestoneHit = counter.count > 0 && counter.count % 10 == 0

    LaunchedEffect(counter.count) {
        // Restarts every time count changes; only acts on milestones.
        if (milestoneHit) {
            playMilestoneSound()
        }
    }

    // ... rest of UI
}
```

Note the key: `LaunchedEffect(counter.count)` — using `count` as the key
means the effect re-evaluates on every tap. If you only want it to fire
*once per milestone crossing* and not re-check on every unrelated
recomposition, keying on `count` is correct because count is exactly the
value that determines whether a milestone was hit.

### 6b. DisposableEffect

Like `LaunchedEffect`, but for **non-suspending** resources that need
explicit cleanup: listeners, callbacks, broadcast receivers, or (for Project
7) a manually managed ticker/timer. It **requires** an `onDispose {}` block.

```
Diagram — DisposableEffect lifecycle

enters composition ──► effect block runs ──► registers resource (e.g. ticker)
        │
key changes OR leaves composition
        │
        ▼
   onDispose { } runs ──► cleans up (stop ticker / unregister listener)
        │
   (if key changed) ──► effect block runs again with new key
```

**Use in Project 7:** a "live ticking" counter that auto-increments every
second until removed — cancel the ticker when the counter card is removed
from the board.

```kotlin
@Composable
fun Counter(counter: CounterState, onTick: () -> Unit, onRemoved: Boolean) {
    DisposableEffect(counter.id) {
        val ticker = Timer()
        ticker.scheduleAtFixedRate(1000, 1000) { onTick() }

        onDispose {
            // Runs when the Counter leaves composition (removed from board)
            // OR when counter.id changes (shouldn't happen here, but Compose
            // guarantees cleanup either way).
            ticker.cancel()
        }
    }
    // ... rest of UI
}
```

### 6c. SideEffect

Runs a block on **every successful recomposition**, synchronously, after
composition completes but as part of applying the changes. It is NOT
cancellable and does NOT survive across recompositions like `remember` does
— it literally just runs every time. Used for pushing Compose's state into
a **non-Compose object** that doesn't know about snapshots (e.g. an
analytics SDK, or Crashlytics custom keys).

```
Diagram — SideEffect vs LaunchedEffect vs DisposableEffect

                 runs on first     runs again on      runs on
                 composition       recomposition       leaving comp.
LaunchedEffect        ✅          only if key changed        cancels coroutine
DisposableEffect      ✅          only if key changed        onDispose runs
SideEffect            ✅          EVERY recomposition        (nothing to clean up)
```

**Use in Project 7:** log analytics whenever any counter's count changes.

```kotlin
@Composable
fun Counter(counter: CounterState, /* ... */) {
    SideEffect {
        // Fires every recomposition of THIS Counter — i.e. every time
        // counter.count (or any other read value) actually changed.
        AnalyticsLogger.log("counter_state", mapOf(
            "id" to counter.id,
            "count" to counter.count
        ))
    }
    // ... rest of UI
}
```

### Quick decision table

| Need to...                                   | Use                |
|-----------------------------------------------|---------------------|
| run a suspend function / coroutine tied to composition | `LaunchedEffect` |
| register/unregister a non-suspend resource (listener, timer, receiver) | `DisposableEffect` |
| sync Compose state to a non-Compose object, every recomposition | `SideEffect` |

---

## 7. COMPOSITIONLOCAL

### Theory

Normally, data flows down through explicit function parameters ("state
hoisting" — Section 2). That's the right default. But some data is
**contextual** and needed by *many* composables deep in the tree, where
threading it through every single parameter list would be painful and
noisy — theme colors, typography, layout direction, content padding
conventions, etc. `CompositionLocal` provides an **implicit** way to pass
data down the tree, similar in spirit to `InheritedWidget` in Flutter or
React Context.

Two flavors:
- `compositionLocalOf { default }` — recomposes only the subtree reading the
  local when the value changes (fine-grained invalidation).
- `staticCompositionLocalOf { default }` — cheaper reads, but changing the
  value causes the **entire content under the provider to fully recompose**
  (no fine-grained tracking). Use this for values that basically never
  change at runtime (e.g. app-wide constants).

You "provide" a value with `CompositionLocalProvider`, and any descendant
reads it with `SomeLocal.current`.

### Diagram

```
val LocalCounterAccent = compositionLocalOf { Color.Gray }   // default

CounterBoardApp
 └── CompositionLocalProvider(LocalCounterAccent provides Color(counter.color))
      └── Counter(counter)                       <- doesn't read the local
           └── HistoryChart(history)              <- doesn't read the local
                └── ChartBar(value)                <- reads LocalCounterAccent.current
                                                        (deeply nested, no need to
                                                         pass color as a parameter
                                                         through 2 intermediate layers)
```

### Code Example

```kotlin
val LocalCounterAccent = compositionLocalOf { Color.Gray }

@Composable
fun Counter(counter: CounterState, onIncrement: () -> Unit) {
    CompositionLocalProvider(LocalCounterAccent provides Color(counter.color)) {
        Column {
            Text(counter.name)
            HistoryChart(history = counter.history) // color threaded implicitly
        }
    }
}

@Composable
fun HistoryChart(history: List<Int>) {
    Row {
        history.forEach { value -> ChartBar(value) }
    }
}

@Composable
fun ChartBar(value: Int) {
    val accent = LocalCounterAccent.current // read 2 levels below the provider
    Box(
        modifier = Modifier
            .width(4.dp)
            .height(value.dp)
            .background(accent)
    )
}
```

**Rule of thumb:** default to parameters (explicit, testable, traceable).
Reach for `CompositionLocal` only for genuinely cross-cutting, ambient
concerns (theming is the textbook example — `MaterialTheme.colorScheme` is
itself built on `CompositionLocal`s).

---

## 8. derivedStateOf

### Theory

`derivedStateOf` creates a `State<T>` whose value is **computed from other
State objects**, and — critically — only notifies its own readers when the
**computed result actually changes**, even if the underlying inputs change
more often. This avoids unnecessary recomposition when you'd otherwise
recompute something on every single keystroke/tick but the *derived* value
doesn't change every time.

Contrast with a plain `val total = counters.sumOf { it.count }` written
directly inside a composable body: that recomputes on every recomposition of
that composable regardless, and (worse) if you read `total` in a
**different, less frequently recomposing** composable via hoisted state, you
still want the change-detection benefit `derivedStateOf` gives you.

### Diagram

```
counters: List<MutableState<Int>>   [c1=3, c2=7, c3=1]

totalCount = remember {
    derivedStateOf { counters.sumOf { it.value } }
}                                        │
                                         ▼
                                total = 11 (this is the ONLY value
                                             totalCount's readers see)

Tap c1 (3 -> 4), then tap c2 (7 -> 6) in the same frame-ish window:
  intermediate sums: 12, then 11
  final settled sum: 11  -->  SAME as before!
  ⇒ derivedStateOf recomputes internally, sees result is unchanged (11),
    and does NOT trigger recomposition of composables reading totalCount.
```

If you'd used a plain computed `val` inside the composable, any change to
any `counters[i].value` would force recomposition of that composable — even
in the case above where the net total didn't change.

### Code Example

```kotlin
@Composable
fun CounterBoard(counters: List<CounterState>) {
    // counters is a snapshot-backed list of State — each counter's `count`
    // is individually observable.
    val totalCount by remember(counters) {
        derivedStateOf { counters.sumOf { it.count } }
    }

    Column {
        Text("Total across all counters: $totalCount")
        CounterList(counters)
    }
}
```

**Use in Project 7:** show a running "Grand Total" header above the counter
list, computed from every individual counter's count, without making the
header recompose on every single tap of every counter (only when the *sum*
changes — which, given increments, is basically every tap, but the pattern
generalizes to cases like "is any counter above 100" or "average step size,"
where the derived value changes far less often than the inputs).

---

## 9. PROJECT BREAKDOWN — "CounterBoard App"

### What you're actually building

A screen with a scrollable list of counter "cards." Each card has:
- a **name** (e.g. "Pushups", "Coffees", "Bugs Fixed")
- a **color** (visual accent, used via `CompositionLocal`)
- a **step value** (how much each tap adds — default 1, editable)
- a **count** (current value)
- a **history** (list of past values, rendered as a tiny bar chart)

Interactions:
- **Tap** a card → `count += step`, append new value to `history`.
- **Long-press** a card → open an edit dialog for `name` and `step`.
- **Add counter** button → appends a new `CounterState` with defaults.
- **Remove counter** (e.g. swipe or a delete icon) → removes it from the
  list and disposes any running effects tied to it.

### Suggested architecture

```
CounterBoardViewModel (holds source-of-truth list, survives rotation
                        naturally since ViewModel outlives config changes)
   │  exposes: StateFlow<List<CounterState>> or mutableStateListOf<CounterState>
   ▼
CounterBoardApp (root composable, collects the state)
   │
   ▼
CounterList (LazyColumn, keyed by counter.id)
   │
   ▼
Counter (per-item; owns short-lived UI-only state like "is editing")
   │        - LaunchedEffect(count) -> milestone sound
   │        - DisposableEffect(id)  -> ticker cleanup
   │        - SideEffect            -> analytics
   │        - CompositionLocalProvider -> accent color
   ▼
HistoryChart -> ChartBar (reads LocalCounterAccent.current)
```

### Mapping concepts → concrete features

| Concept              | Where it shows up in CounterBoard                              |
|-----------------------|-------------------------------------------------------------|
| Composable functions  | `Counter()`, `HistoryChart()`, `CounterList()`, `ChartBar()` |
| remember + mutableStateOf | transient per-counter UI state (e.g. "is edit dialog open") |
| rememberSaveable      | `count`/`step` surviving rotation if not using a ViewModel   |
| Recomposition scoping | only the tapped `Counter` recomposes, not the whole list     |
| LaunchedEffect        | milestone sound effect                                       |
| DisposableEffect      | cancel a per-counter ticker when the counter is removed       |
| SideEffect            | log analytics on every count change                           |
| CompositionLocal      | pass accent color down into `HistoryChart` → `ChartBar`       |
| derivedStateOf        | grand total across all counters, computed efficiently         |

### Suggested build order (matches your section-by-section study habit)

1. Static UI first: hardcode 2–3 counters, build `Counter` + `HistoryChart`
   layout with no interactivity.
2. Add tap-to-increment using `remember { mutableStateOf(...) }` per counter.
3. Swap to `rememberSaveable` (or move state to a `ViewModel` — recommended
   for the "add/remove counters" list itself, since a growing list is a poor
   fit for Bundle-based saving).
4. Add long-press → edit dialog (name + step).
5. Add add/remove counter buttons; wire `key = { it.id }` in `LazyColumn`.
6. Add `LaunchedEffect` milestone sound.
7. Add `DisposableEffect` ticker + removal cleanup.
8. Add `SideEffect` analytics logging.
9. Wrap `Counter` in `CompositionLocalProvider` for accent color; consume in
   `ChartBar`.
10. Add `derivedStateOf` grand total header.

---

## 10. COMMON PITFALLS

- **Mutating state outside remember**: `var count = 0` (no `remember`) inside
  a composable resets every recomposition — looks like a "the app doesn't
  respond to taps" bug, but it's actually responding correctly to a value
  that keeps getting reset.
- **Forgetting `by` vs `.value`**: `mutableStateOf(0)` without `by` means you
  must write `count.value += 1`, not `count += 1`. Mixing this up is a
  frequent compile error early on.
- **Putting side effects directly in the composable body** (e.g. calling
  `viewModel.logEvent()` directly, not inside `SideEffect`/`LaunchedEffect`)
  — causes duplicated/inconsistent side effects because the body can run
  multiple times or be skipped.
- **Wrong effect key**: `LaunchedEffect(Unit)` when you actually wanted it
  to restart on `counter.id` change (e.g. reused across list item recycling)
  — leads to a coroutine operating on stale/wrong data.
- **DisposableEffect without meaningful key**: if the key never changes but
  the resource genuinely needs to be tied to counter identity, use
  `counter.id` as the key, not `Unit`, or removals/re-additions won't clean
  up correctly.
- **Unstable parameter types disabling skipping**: passing a plain
  mutable `class Foo(var x: Int)` (not a `data class` with `val`, not
  `@Immutable`) as a parameter can make the Compose compiler treat the
  composable as **not skippable**, silently hurting performance across the
  whole list.
- **Using `compositionLocalOf` (or `staticCompositionLocalOf`) as a
  substitute for normal parameters** "because it's easier" — makes data flow
  hard to trace/test. Reserve it for truly ambient/cross-cutting values.
- **derivedStateOf overuse**: wrapping every trivial computation in
  `derivedStateOf` adds overhead for no benefit if the computation is cheap
  and changes as often as its inputs anyway. It shines specifically when the
  *derived* value changes **less often** than the inputs.

---

## 11. INTERVIEW Q&A

**Q: What's the difference between `remember` and `rememberSaveable`?**
A: Both cache a value across recomposition. `rememberSaveable` additionally
persists the value through configuration changes and process death by
saving it into the saved-instance-state Bundle (via a `Saver`), while
`remember` loses its value in those cases.

**Q: Why can composable functions not have side effects directly in their
body?**
A: Because Compose may call a composable zero, one, or many times, in any
order, and recomposition can be skipped or interrupted. A side effect placed
directly in the body could run an inconsistent number of times or at
unpredictable moments. Effect APIs (`LaunchedEffect`, `DisposableEffect`,
`SideEffect`) tie side effects explicitly to the composition lifecycle so
they run exactly when intended.

**Q: When would you choose `DisposableEffect` over `LaunchedEffect`?**
A: When the resource you're managing is not suspend-based and needs
explicit, guaranteed cleanup — e.g. registering a `BroadcastReceiver`,
`SensorEventListener`, or a manually managed `Timer`/ticker.
`LaunchedEffect` is for coroutine work; its "cleanup" is just coroutine
cancellation. `DisposableEffect` requires an explicit `onDispose {}`.

**Q: What triggers recomposition, and why doesn't the whole screen redraw
every time?**
A: Recomposition is triggered when a `State<T>` object that a composable
read during its last composition changes value. Compose performs smart
recomposition/skipping: it only re-invokes composables whose read state or
parameters actually changed (and are provably stable), so unrelated parts of
the UI tree are skipped.

**Q: What problem does `derivedStateOf` solve that a plain `val` computed in
the composable body doesn't?**
A: A plain computed `val` recalculates and can trigger recomposition every
time ANY input changes, even if the final derived value is unchanged.
`derivedStateOf` recomputes internally on input changes but only notifies
its readers when the resulting value actually differs, avoiding unnecessary
recomposition downstream.

**Q: What's the difference between `compositionLocalOf` and
`staticCompositionLocalOf`?**
A: `compositionLocalOf` supports fine-grained recomposition — only
composables that read `.current` recompose when the provided value changes.
`staticCompositionLocalOf` is cheaper to read but has no fine-grained
tracking: changing the provided value forces the entire content under the
`CompositionLocalProvider` to recompose. Use static for values that are
effectively constant at runtime.

**Q: In the CounterBoard app, why hoist each counter's state instead of
storing all counters in one big list-backed `MutableState`?**
A: Hoisting state per counter (or reading it via a stable per-item key from
a `ViewModel`) lets Compose scope invalidation to just the tapped counter.
If all counters live in one `MutableState<List<CounterState>>` and you
replace the whole list on every tap, you risk broader recomposition and lose
the fine-grained skipping benefit, unless you're careful with `key()` in
`LazyColumn` and structural equality of the list items.

---

*End of Section 3.1 study notes.*