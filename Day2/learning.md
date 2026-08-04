# Advanced Kotlin — Coroutines & Flow Deep Dive
### Section 1.2 | Project: LiveScorePoller App

> **Goal:** Understand every concept deeply — the *why*, the *how*, and the
> *mental model* behind each tool. Not just syntax. Not just copy-paste.

---

## Table of Contents

| # | Topic | Difficulty |
|---|-------|------------|
| 1 | [Suspend Functions](#1-suspend-functions) | Intermediate |
| 2 | [Coroutine Builders — launch vs async](#2-coroutine-builders--launch-vs-async) | Intermediate |
| 3 | [Dispatchers](#3-dispatchers) | Intermediate |
| 4 | [Structured Concurrency](#4-structured-concurrency) | Advanced |
| 5 | [StateFlow](#5-stateflow) | Advanced |
| 6 | [SharedFlow](#6-sharedflow) | Advanced |
| 7 | [Flow Operators](#7-flow-operators) | Advanced |
| 8 | [Channels](#8-channels) | Advanced |
| 9 | [Project — LiveScorePoller App](#9-project--livescorepoller-app) | Advanced |

---

## 1. Suspend Functions

### The Problem First — Why Do We Need This?

Android apps run on a single main thread for UI. That thread must render frames
every 16ms (~60fps). If you block it for even 100ms, the user sees a frozen screen.

The obvious solution is "just use another thread". But threads are expensive:
each one costs ~1MB of stack memory. On a phone with 2GB RAM, you can manage
maybe 1000 threads before running out. Callbacks and `RxJava` help, but they
make code deeply nested and hard to follow.

```
THE CLASSIC PROBLEM:

fun loadScores() {
    val scores = networkCall()   // ← takes 500ms. Main thread FROZEN for 500ms.
    updateUI(scores)
}

  Main Thread Timeline:
  ███████████████████████████████████████████
  │ render │   BLOCKED on network...   │ render │
  0ms     16ms                        516ms

  30 dropped frames = app appears frozen = 1-star review
```

```
TRADITIONAL CALLBACK HELL (the old solution):

fetchUser(userId,
    onSuccess = { user ->
        fetchPosts(user.id,
            onSuccess = { posts ->
                fetchComments(posts.first().id,
                    onSuccess = { comments ->
                        updateUI(comments)   // 3 levels deep, hard to read
                    },
                    onError = { handleError() })
            },
            onError = { handleError() })
    },
    onError = { handleError() })

  Every new async step = one more level of nesting.
  Error handling duplicated everywhere.
  Impossible to add a loop or try/catch around the whole thing.
```

### Theory — What is a Suspend Function?

A **suspend function** is a function that can pause its execution at a
*suspension point*, release the current thread to do other work, and
automatically resume from exactly where it left off when the result is ready.

The key insight: **"paused" ≠ "thread blocked"**. The thread is FREE while the
function is suspended. The function's local state is saved in a heap-allocated
**Continuation** object — like a bookmark in a book.

```
MENTAL MODEL — The Continuation as a Bookmark:

  suspend fun fetchAndDisplay() {
      // Line A
      val score = fetchScore()     ← SUSPEND POINT — bookmark placed here
      // Line B
      showScore(score)             ← execution resumes here when result arrives
  }

  When fetchScore() suspends:
  ┌──────────────────────────────────────────────────────────────────┐
  │  Continuation Object  (lives on HEAP, not on thread stack)       │
  │  ┌────────────────────────────────────────────────────────────┐  │
  │  │  resumeAt: Line B                                          │  │
  │  │  locals:   { score = <pending> }                           │  │
  │  │  dispatcher: Dispatchers.Main                              │  │
  │  └────────────────────────────────────────────────────────────┘  │
  └──────────────────────────────────────────────────────────────────┘

  Main thread is NOW FREE — it can render frames, handle button clicks, etc.
  When the network result arrives → Continuation.resume(score) is called
  → Main thread picks up the bookmark → executes Line B with the result.
```

### Thread Timeline — Blocking vs Suspending

```
BLOCKING APPROACH (Thread.sleep / blocking IO):
───────────────────────────────────────────────
  Main Thread:
  ─[render]─────[BLOCKED waiting for network 500ms]──────────[render]─
                │                                             │
             frame drops                               can finally render

SUSPEND APPROACH (delay / coroutines):
───────────────────────────────────────
  Main Thread:
  ─[render]──[suspend]──[render]──[render]──[render]──[resume]──[render]─
                  │                                        ▲
                  └── coroutine suspended ─────────────────┘
                      thread kept working the whole time ✅

  Each box = 16ms frame.
  Blocking = frames dropped.
  Suspend = frames delivered, coroutine picks up when ready.
```

### Rules

```
┌─────────────────────────────────────────────────────────────────────┐
│  RULE 1: suspend functions can ONLY be called from                  │
│           another suspend function OR a coroutine builder.          │
│                                                                     │
│  RULE 2: The 'suspend' keyword is a COMPILER marker —               │
│           it does NOT create a thread. Threads are managed by       │
│           the Dispatcher.                                           │
│                                                                     │
│  RULE 3: Suspension is COOPERATIVE — the function itself decides    │
│           when to yield (at delay(), withContext(), channel ops,    │
│           etc.). The runtime cannot force-pause a coroutine.        │
│                                                                     │
│  RULE 4: CancellationException must NEVER be swallowed.            │
│           Catching it and ignoring it breaks coroutine cancellation.│
└─────────────────────────────────────────────────────────────────────┘
```

### What Suspension Points Look Like

Any call to another suspend function is a potential suspension point:

```kotlin
delay(1000)                   // suspend for 1 second, thread is free
withContext(Dispatchers.IO)   // suspend while switching context
yield()                       // voluntarily give up thread, then resume
channel.send(item)            // suspend if buffer is full
channel.receive()             // suspend if channel is empty
flow.collect { }              // suspend for each item
deferred.await()              // suspend until Deferred completes
mutex.lock()                  // suspend until lock is acquired
```

### What the Compiler Does — The State Machine

You write suspend functions like normal sequential code. The Kotlin compiler
secretly transforms them into a state machine. Each suspension point is a "state".
The function's local variables are stored between states.

```
WHAT YOU WRITE:
  suspend fun loadDashboard(): Dashboard {
      val user   = fetchUser()     // suspend point 1
      val scores = fetchScores()   // suspend point 2
      val news   = fetchNews()     // suspend point 3
      return Dashboard(user, scores, news)
  }

WHAT THE COMPILER GENERATES (simplified state machine):

  State 0 → call fetchUser() → suspend → wait for result
  State 1 → store user, call fetchScores() → suspend → wait for result
  State 2 → store scores, call fetchNews() → suspend → wait for result
  State 3 → build Dashboard(user, scores, news) → resume caller

  ┌──────────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐
  │ State 0  │────►│ State 1  │────►│ State 2  │────►│ State 3  │
  │fetchUser │     │fetchScore│     │fetchNews │     │  return  │
  └──────────┘     └──────────┘     └──────────┘     └──────────┘
  suspend/resume   suspend/resume   suspend/resume
  saves: nothing   saves: user      saves: user,scores

  Each time the coroutine resumes, the state machine picks up from the
  right state with all the saved local variables restored.
```

### Sequential vs Parallel Suspend Calls

```
SEQUENTIAL (default behavior):
  suspend fun fetchAll(): List<Score> {
      val football = fetchScore("Football")   // wait 500ms
      val tennis   = fetchScore("Tennis")     // wait 500ms
      val cricket  = fetchScore("Cricket")    // wait 500ms
      return listOf(football, tennis, cricket)
  }

  Timeline: │── Football 500ms ──│── Tennis 500ms ──│── Cricket 500ms ──│
  Total: ~1500ms


PARALLEL (using async — see Section 2):
  suspend fun fetchAll(): List<Score> = coroutineScope {
      val f = async { fetchScore("Football") }
      val t = async { fetchScore("Tennis") }
      val c = async { fetchScore("Cricket") }
      listOf(f.await(), t.await(), c.await())
  }

  Timeline: │── Football ──│
            │── Tennis   ──│  ← all three run at the same time
            │── Cricket  ──│
  Total: ~500ms  (3× faster!)
```

### Full Code — Patterns & Mistakes

```kotlin
// ── Basic declaration ─────────────────────────────────────────────────
suspend fun fetchScore(sport: String): Score {
    delay(500)   // suspend point — thread is free during this wait
    return Score(sport, homeGoals = 1, awayGoals = 0)
}

// ── Calling from a coroutine ──────────────────────────────────────────
fun main() = runBlocking {
    val score = fetchScore("Football")   // ✅ inside coroutine
    println(score)
}

// ── Error handling — IMPORTANT: never swallow CancellationException ───
suspend fun fetchScoreSafely(sport: String): Result<Score> {
    return try {
        Result.success(fetchScore(sport))
    } catch (e: CancellationException) {
        throw e                     // ✅ ALWAYS rethrow this
    } catch (e: IOException) {
        Result.failure(e)           // handle network errors
    }
}

// ── Retry with exponential backoff ────────────────────────────────────
suspend fun fetchWithRetry(sport: String): Score {
    repeat(3) { attempt ->
        try {
            return fetchScore(sport)
        } catch (e: CancellationException) {
            throw e                  // don't retry on cancellation
        } catch (e: IOException) {
            if (attempt == 2) throw e
            delay(1000L * (attempt + 1))   // 1s, 2s, 3s
        }
    }
    error("unreachable")
}

// ── Common mistake 1: blocking inside a suspend function ─────────────
suspend fun badFetch(): Score {
    Thread.sleep(1000)   // ❌ BLOCKS the thread! Use delay() instead.
    return Score(...)
}

// ── Common mistake 2: not using the coroutine scope ──────────────────
class MyViewModel : ViewModel() {
    fun loadScores() {
        val score = fetchScore("Football")   // ❌ COMPILE ERROR
    }
}
// ✅ CORRECT:
class MyViewModel : ViewModel() {
    fun loadScores() {
        viewModelScope.launch {
            val score = fetchScore("Football")   // inside a coroutine ✅
            _scores.value = score
        }
    }
}

---

## 2. Coroutine Builders — launch vs async

### Theory — What is a Coroutine Builder?

A **coroutine builder** is a function that creates and *starts* a new coroutine.
It bridges the gap between normal code and the suspend-function world.

```
NORMAL CODE WORLD                        COROUTINE WORLD
─────────────────                        ───────────────
  fun startWork() {
      viewModelScope.launch { ──────────►  suspend funs are callable here
          val score = fetchScore()          delay(), withContext() work here
          _scores.value = score
      }   ◄─────────────────────────────── returns a Job immediately
      println("still here")   ← runs while coroutine is running
  }
```

```
┌───────────────────────────────────────────────────────────────────────────┐
│  COROUTINE BUILDERS                                                       │
├──────────────┬────────────────────────────────────────────────────────────┤
│  launch {}   │  Fire-and-forget. Returns: Job                             │
│              │  Block return type: Unit (no result value)                 │
│              │  Use when: UI updates, polling, logging, navigation        │
├──────────────┼────────────────────────────────────────────────────────────┤
│  async {}    │  Returns a future result. Returns: Deferred<T>             │
│              │  Block return type: T (any value)                          │
│              │  Use when: parallel fetches, computation with a result     │
├──────────────┼────────────────────────────────────────────────────────────┤
│  runBlocking │  Blocks the CURRENT thread until coroutine finishes.       │
│              │  Returns: T (the result of the block)                      │
│              │  Use ONLY in: unit tests, JVM main() functions             │
│              │  ❌ NEVER in Android ViewModel, Activity, or Fragment       │
└──────────────┴────────────────────────────────────────────────────────────┘
```

### launch{} — Fire and Forget

```
TIMELINE — launch{} does NOT block the caller:

  T=0ms:  startPolling() is called
  T=0ms:  viewModelScope.launch { ... }  ← coroutine created, scheduled to run
  T=0ms:  println("polling started")     ← runs IMMEDIATELY on the caller
  T=1ms:  coroutine actually starts executing fetchScore()
  T=501ms: fetchScore() returns → _scores.value updated
  T=501ms: delay(3000) starts
  T=3501ms: next iteration...

  Caller thread is NEVER blocked. launch{} returns a Job reference at T=0ms.
```

```kotlin
// ── Basic launch{} ────────────────────────────────────────────────────
class ScoreViewModel : ViewModel() {

    fun startPolling() {
        val job: Job = viewModelScope.launch {
            while (isActive) {
                val score = fetchScore("Football")
                _scores.value = score
                delay(3000)
            }
        }
        // This runs IMMEDIATELY — the coroutine hasn't even started yet
        println("Polling job launched: ${job.isActive}")   // true
    }
}

// ── Job control — everything you can do with the returned Job ─────────
val job = viewModelScope.launch { doLongWork() }

job.cancel()            // request cancellation (cooperative, see Section 4)
job.join()              // suspend until this coroutine finishes
job.cancelAndJoin()     // cancel, then suspend until it fully stops

job.isActive            // true: running right now
job.isCancelled         // true: cancel() was called
job.isCompleted         // true: finished normally or was cancelled

// ── Checking isActive inside the coroutine ────────────────────────────
viewModelScope.launch {
    while (isActive) {        // ← turns false when job.cancel() is called
        fetchAndUpdate()
        delay(3000)           // ← delay() ALSO checks isActive internally
    }
    // Coroutine exits the loop cleanly when cancelled
}
```

### async{} — Concurrent Work with a Result

```
TIMING COMPARISON:

SEQUENTIAL — one after another (3 suspend calls, ~1500ms total):
  │─── fetchFootball (500ms) ───│─── fetchTennis (500ms) ───│─── fetchCricket (500ms) ───│
  Total: ~1500ms

PARALLEL — with async (all 3 run at the same time, ~500ms total):
  │─── fetchFootball (500ms) ───│
  │─── fetchTennis   (500ms) ───│   ← all three started simultaneously
  │─── fetchCricket  (500ms) ───│
  Total: ~500ms  (3× faster!)

  The .await() calls DON'T START the work — the work starts at async{}.
  .await() just WAITS for already-running work to finish.
```

```kotlin
// ── async{} basics ────────────────────────────────────────────────────
suspend fun fetchAllScores(): List<Score> = coroutineScope {
    // Start all three at the same time
    val footballD: Deferred<Score> = async { fetchScore("Football") }
    val tennisD:   Deferred<Score> = async { fetchScore("Tennis") }
    val cricketD:  Deferred<Score> = async { fetchScore("Cricket") }

    // All three are running NOW. No suspensions yet.

    val football = footballD.await()   // suspend until football is done
    val tennis   = tennisD.await()     // likely already done, returns instantly
    val cricket  = cricketD.await()    // likely already done, returns instantly

    listOf(football, tennis, cricket)
}

// ── awaitAll() — shorthand for a list of Deferreds ────────────────────
suspend fun fetchAllSports(sports: List<String>): List<Score> = coroutineScope {
    sports.map { sport ->
        async { fetchScore(sport) }
    }.awaitAll()   // waits for ALL of them, returns List<Score>
}

// ── Deferred<T> is a lazy result box ──────────────────────────────────
//
//  async { fetchScore() }  → returns Deferred<Score> immediately
//
//  Deferred<Score>
//  ┌────────────────────────────────────────────────────────┐
//  │  Status: ACTIVE (coroutine running in background)      │
//  │  .await()        → suspends until result ready         │
//  │  .cancel()       → cancel the running work             │
//  │  .getCompleted() → get result if done, throws if not   │
//  └────────────────────────────────────────────────────────┘
//
//  After 500ms, fetchScore() returns:
//  ┌────────────────────────────────────────────────────────┐
//  │  Status: COMPLETED                                     │
//  │  .await()        → returns Score instantly             │
//  │  .getCompleted() → Score("Football", 2, 1)             │
//  └────────────────────────────────────────────────────────┘
```

### Exception Handling — launch vs async

```kotlin
// launch{} — exception crashes the coroutine, propagates to scope
viewModelScope.launch {
    throw IOException("network failed")
    // ↑ Exception propagates up to viewModelScope
    // viewModelScope uses SupervisorJob, so other coroutines survive
    // But THIS coroutine is dead
}

// To handle exceptions in launch{}, use CoroutineExceptionHandler:
val handler = CoroutineExceptionHandler { _, throwable ->
    println("Coroutine crashed: $throwable")
    _uiState.update { it.copy(error = throwable.message) }
}
viewModelScope.launch(handler) {
    fetchScore("Football")   // if this throws, handler catches it
}

// async{} — exception is STORED in the Deferred, thrown on .await()
viewModelScope.launch {
    val d = async { throw IOException("failed") }
    // d is "active" but internally holds the exception

    try {
        d.await()   // ← IOException is thrown HERE
    } catch (e: IOException) {
        println("Caught: $e")   // safe to handle here
    }
}
```

### runBlocking — Only for Tests

```kotlin
// ❌ NEVER in Android UI code:
class ScoreViewModel : ViewModel() {
    fun loadScore() {
        runBlocking { fetchScore() }   // blocks Main thread → ANR after 5s
    }
}

// ✅ In unit tests:
@Test
fun `fetchScore returns correct sport name`() = runBlocking {
    val score = fetchScore("Football")
    assertEquals("Football", score.sport)
}

// ✅ In JVM main():
fun main() = runBlocking {
    val score = fetchScore("Football")
    println(score)
}

---

## 3. Dispatchers

### Theory — What is a Dispatcher?

A **Dispatcher** answers one question: *"When this coroutine is ready to run,
which thread or thread pool should it run on?"*

Think of it like departments in a company. The CEO's desk (Main thread) handles
only high-priority decisions (UI updates). The warehouse (IO pool) does bulk work
(network, disk). The lab (Default pool) runs experiments (CPU calculations).

```
JVM PROCESS — Thread Pool Architecture:

  ┌──────────────────────────────────────────────────────────────────┐
  │  Main Thread  (exactly 1 thread)                                 │
  │  ──────────────────────────────                                  │
  │  [Render Frame] [Handle Click] [Update View] [Render Frame] ...  │
  │  Must stay free! Any work > 16ms = dropped frame = jank.         │
  │                                                                  │
  │  Dispatchers.IO Thread Pool  (up to 64 threads)                  │
  │  ────────────────────────────────────────────                    │
  │  Thread-01: [ HTTP call to api.example.com   ]                   │
  │  Thread-02: [ Room database query            ]                   │
  │  Thread-03: [ File read from disk            ]                   │
  │  Thread-04: [ Another HTTP call              ]                   │
  │  ...  (threads grow/shrink dynamically)                          │
  │                                                                  │
  │  Dispatchers.Default Thread Pool  (= number of CPU cores)        │
  │  ──────────────────────────────────────────────────────          │
  │  Thread-01: [ Parsing 5MB JSON response      ]                   │
  │  Thread-02: [ Sorting 100k score records     ]                   │
  │  Thread-03: [ Bitmap decoding                ]                   │
  └──────────────────────────────────────────────────────────────────┘
```

### All Four Dispatchers

```
┌──────────────────────────────────────────────────────────────────────────┐
│  Dispatchers.Main                                                        │
│  Thread count: 1  (the Android UI thread)                                │
│  Use for:  Updating Views, reading/writing StateFlow, Compose recompose  │
│  Avoid:    Any blocking operation, even 10ms can cause visible jank      │
│                                                                          │
│  Dispatchers.IO                                                          │
│  Thread count: max(64, numCpuCores) — grows and shrinks dynamically      │
│  Use for:  Retrofit/Ktor calls, Room, SharedPreferences, DataStore,      │
│            any operation that waits on external I/O                      │
│  Why 64+:  IO threads spend most time WAITING (not using CPU),           │
│            so many can be "in flight" without fighting over CPU           │
│                                                                          │
│  Dispatchers.Default                                                     │
│  Thread count: numCpuCores  (e.g., 8 on a modern phone)                 │
│  Use for:  JSON parsing, sorting large lists, encryption, image          │
│            processing — anything CPU-intensive                           │
│  Why limited: CPU work USES the CPU; more threads just cause             │
│            context switching, not more speed                             │
│                                                                          │
│  Dispatchers.Unconfined                                                  │
│  Thread: Starts in caller thread, resumes wherever it was resumed        │
│  Use for:  Unit tests, never in production Android code                  │
└──────────────────────────────────────────────────────────────────────────┘
```

### withContext() — The Thread Switcher

`withContext()` switches the dispatcher for a block of code, then switches BACK.
It does NOT create a new coroutine — it's the same coroutine, just temporarily
running on a different thread pool.

```
WITHCONTEXT THREAD TIMELINE:

  viewModelScope.launch {          ← starts on Dispatchers.Main (default for viewModelScope)
  │
  ├── withContext(Dispatchers.IO) {      ← thread switches to IO pool
  │       val rawJson = httpClient.get(url)   ← runs on IO
  │   }                                       ← thread switches BACK to Main
  │
  ├── withContext(Dispatchers.Default) {  ← thread switches to Default pool
  │       val scores = parseJson(rawJson)     ← runs on Default (CPU work)
  │   }                                       ← thread switches BACK to Main
  │
  └── _scores.value = scores          ← runs on Main ✅

  Thread timeline:
  Main:    ████                              ████           ████████████
  IO:          ████████████
  Default:                   ████████████
           ─────────────────────────────────────────────────────────────►
           t=0              t=300ms          t=600ms         t=700ms
```

```kotlin
// ── withContext() in a repository ─────────────────────────────────────
class ScoreRepository(private val httpClient: HttpClient) {

    suspend fun getScores(): List<Score> {
        // Step 1: network fetch on IO
        val rawJson = withContext(Dispatchers.IO) {
            httpClient.get("https://api.example.com/scores").bodyAsText()
        }

        // Step 2: JSON parsing on Default (CPU-intensive)
        val scores = withContext(Dispatchers.Default) {
            Json.decodeFromString<List<Score>>(rawJson)
        }

        return scores   // resumes on whatever thread called this function
    }
}

// ── ViewModel uses it — resumes automatically on Main ─────────────────
class ScoreViewModel(private val repo: ScoreRepository) : ViewModel() {
    fun loadScores() {
        viewModelScope.launch {             // starts on Main
            val scores = repo.getScores()  // suspends (IO + Default inside), resumes on Main
            _scores.value = scores         // back on Main ✅
        }
    }
}
```

### Dispatcher Decision Flowchart

```
What does this code do?
       │
       ├── Touches Android Views / Compose / StateFlow.value?
       │         └──► Dispatchers.Main
       │
       ├── Network call / Room / File / SharedPreferences / DataStore?
       │         └──► Dispatchers.IO
       │
       ├── Sorting large list / Parsing JSON / Heavy math / Bitmap decode?
       │         └──► Dispatchers.Default
       │
       └── Unit test / Don't care about threads?
                 └──► Dispatchers.Unconfined  (or TestCoroutineDispatcher)
```

### flowOn() vs withContext() — Know the Difference

```kotlin
// withContext() — single operation: switches there and back
suspend fun loadScore(): Score = withContext(Dispatchers.IO) {
    httpClient.get(url)   // runs on IO, result returned to caller's thread
}

// flowOn() — for Flow pipelines: changes dispatcher for everything UPSTREAM
fun getScoreFlow(): Flow<Score> = flow {
    while (true) {
        val score = httpClient.get(url)   // ← runs on IO (because of flowOn below)
        emit(score)
        delay(3000)
    }
}
.map { parseScore(it) }       // ← also runs on IO
.flowOn(Dispatchers.IO)       // ← EVERYTHING above this line runs on IO
// .collect{} runs on the scope that calls it (usually Main)

// flowOn does NOT affect operators below it — only above it.
```

---

## 4. Structured Concurrency

### The Problem Without Structure

```
GlobalScope.launch { pollingLoop() }
// User navigates away → ViewModel destroyed
// BUT pollingLoop is still running (GlobalScope has NO lifecycle)
// It tries to update _scores.value on a dead ViewModel
// Result: memory leak, undefined behavior, hard-to-reproduce crashes
```

`GlobalScope` is almost always wrong in Android. Structured concurrency is the fix.

### Theory — Every Coroutine Has a Parent

In structured concurrency, every coroutine belongs to a **scope**. The scope
owns the coroutines launched in it. When the scope is cancelled, ALL its
coroutines are cancelled. When a child fails, the failure propagates to the parent.

```
COROUTINE PARENT/CHILD TREE:

  viewModelScope  (root — tied to ViewModel lifecycle)
       │
       ├─── Job A: launch { pollingLoop() }
       │              │
       │              ├─── Job A1: launch { retryOnFailure() }
       │              │
       │              └─── Job A2: async { fetchScore() }   ← Deferred<Score>
       │
       ├─── Job B: launch { eventCollector() }
       │
       └─── Job C: launch { analyticsTracker() }

  THREE INVARIANTS:
  ① Parent waits for ALL children to finish before it finishes.
  ② Cancellation flows DOWN: cancel viewModelScope → A, B, C all cancel → A1, A2 cancel.
  ③ Failure flows UP:   Job A crashes → parent (viewModelScope) notified → B, C cancelled.
     (unless SupervisorJob is used — see below)
```

### What Actually Happens on Cancellation

```
job.cancel() is called
       │
       ▼
  The coroutine's isActive property = false
       │
       ▼
  At the next suspension point, CancellationException is thrown:
       │
       ├── delay(3000)         → throws CancellationException ✅
       ├── withContext(IO)     → throws CancellationException ✅
       ├── channel.receive()   → throws CancellationException ✅
       └── yield()             → throws CancellationException ✅
       │
       ▼
  CancellationException unwinds the call stack
       │
       ▼
  finally{} blocks run (for cleanup — close connections, release resources)
       │
       ▼
  Coroutine is fully terminated ✅

  KEY: If your code has NO suspension points (tight CPU loop),
       cancellation won't be detected until you check isActive manually.
```

### CoroutineContext — The Composition Model

A coroutine's behaviour is determined by its **CoroutineContext**, which is a
map of elements. You build it by combining elements with `+`:

```
CoroutineContext is a MAP:
┌──────────────────────┬───────────────────────────────────────────────┐
│  Key                 │  Value                                        │
├──────────────────────┼───────────────────────────────────────────────┤
│  Job                 │  Lifecycle handle, parent/child linking       │
│  CoroutineDispatcher │  Which thread(s) to use                       │
│  CoroutineName       │  Debug label                                  │
│  CoroutineExceptionHandler │ What to do on uncaught exceptions       │
└──────────────────────┴───────────────────────────────────────────────┘

Building a context with +:
  val ctx = Job() + Dispatchers.IO + CoroutineName("ScorePoller")
  val scope = CoroutineScope(ctx)

  Later values override earlier ones for the same key:
  Dispatchers.IO + Dispatchers.Main   → Main wins (same key = CoroutineDispatcher)
```

### Code — Scopes and Lifecycle

```kotlin
// ── viewModelScope — most common in Android ───────────────────────────
// Provided by: androidx.lifecycle:lifecycle-viewmodel-ktx
// Cancelled automatically when ViewModel.onCleared() is called
class ScoreViewModel : ViewModel() {

    fun startPolling() {
        viewModelScope.launch {      // child of viewModelScope
            while (isActive) {
                _scores.value = fetchScore()
                delay(3000)
            }
        }
        // When onCleared() fires → viewModelScope cancelled → loop stops ✅
    }
}

// ── viewLifecycleOwner.lifecycleScope — for Fragments ─────────────────
// Cancelled when the Fragment's VIEW is destroyed
// Use this instead of lifecycleScope when working with Views
class ScoreFragment : Fragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        // ✅ CORRECT: repeatOnLifecycle stops collection when app is backgrounded
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // This block STARTS when app enters STARTED (foreground)
                // This block STOPS when app leaves STARTED (background)
                // Prevents processing data when UI is not visible
                viewModel.scores.collect { scores ->
                    adapter.submitList(scores)
                }
            }
        }

        // ❌ WRONG: collects even in background, wastes battery + can crash
        // lifecycleScope.launch { viewModel.scores.collect { ... } }
    }
}
```

### try/finally — Guaranteed Cleanup

```kotlin
// CancellationException guarantees that finally{} always runs
// Use this to release resources (connections, files, etc.)
viewModelScope.launch {
    var connection: WebSocketConnection? = null
    try {
        connection = openWebSocket("wss://api.example.com/scores")
        while (isActive) {
            val update = connection.receive()   // suspend point
            _scores.value = parseUpdate(update)
        }
    } finally {
        // This runs EVEN IF the coroutine is cancelled
        connection?.close()
        println("WebSocket closed cleanly ✅")
    }
}
```

### Job vs SupervisorJob — Failure Isolation

```
REGULAR JOB (default behaviour):
  ┌────────────────────────────────────────────────────┐
  │  viewModelScope (SupervisorJob ← built in)         │
  │  ┌──────────┐  ┌──────────┐  ┌──────────┐         │
  │  │ Job A    │  │ Job B    │  │ Job C    │         │
  │  │ CRASHED  │  │ CANCELLED│  │ CANCELLED│         │
  │  └──────────┘  └──────────┘  └──────────┘         │
  │                                                    │
  │  Note: viewModelScope already uses SupervisorJob,  │
  │  so siblings DON'T cancel each other by default!   │
  └────────────────────────────────────────────────────┘

REGULAR JOB (inside coroutineScope{}):
  coroutineScope {
      async { fetchFootball() }   // ← if this throws...
      async { fetchTennis() }     // ← ...this is cancelled too (fail-fast)
  }
  Use when: ALL data is needed, failure of one = failure of all

SUPERVISOR JOB (inside supervisorScope{}):
  supervisorScope {
      launch { fetchFootball() }   // ← crashes silently (after error handling)
      launch { fetchTennis() }     // ← keeps running ✅
  }
  Use when: items are independent, some can fail without killing others
```

```kotlin
// ── coroutineScope — all or nothing ───────────────────────────────────
suspend fun fetchAllRequired(): List<Score> = coroutineScope {
    val f = async { fetchScore("Football") }   // if ANY throws...
    val t = async { fetchScore("Tennis") }     // ...ALL are cancelled
    val c = async { fetchScore("Cricket") }
    listOf(f.await(), t.await(), c.await())
}

// ── supervisorScope — independent failures ────────────────────────────
suspend fun fetchBestEffort(): List<Score> = supervisorScope {
    val sports = listOf("Football", "Tennis", "Cricket")
    sports.map { sport ->
        async {
            try {
                fetchScore(sport)
            } catch (e: Exception) {
                Score.empty(sport)   // fallback if one sport fails
            }
        }
    }.awaitAll()
}
```

---

## 5. StateFlow

### The Problem StateFlow Solves

Your UI must always display the *current state*. When the user rotates their
phone, the Fragment is destroyed and recreated, but the ViewModel survives.
The new Fragment needs to immediately show current scores — not wait 3 seconds
for the next poll.

```
ROTATION WITHOUT STATEFLOW (using a cold Flow):
  T=0:   App starts. Polling starts. Scores = [1-0, 2-1, 0-0]
  T=10:  User rotates phone. Fragment recreated.
  T=10:  New Fragment collects from cold Flow — flow restarts from scratch!
  T=10:  UI shows NOTHING until next emission at T=13 ❌ (3s of blank UI)

WITH STATEFLOW:
  T=10:  New Fragment collects from StateFlow.
  T=10:  StateFlow immediately emits current value: [1-0, 2-1, 0-0] ✅
  T=10:  UI populated instantly. No blank screen.
```

### Hot vs Cold — The Core Distinction

```
COLD FLOW:
  ─────────────────────────────────────────────────────────────────
  • Does nothing until .collect{} is called
  • Each collector gets its OWN independent run of the producer
  • Like a YouTube video: every viewer watches from the beginning

  flow { emit(1); emit(2); emit(3) }

  Collector A: .collect{} → runs producer → gets 1, 2, 3
  Collector B: .collect{} → runs producer → gets 1, 2, 3  (separate run)


HOT FLOW (StateFlow, SharedFlow):
  ─────────────────────────────────────────────────────────────────
  • Runs INDEPENDENTLY of collectors
  • Collectors tap into an ongoing stream
  • Like a live TV channel: join midstream, see current state

  StateFlow emits: 1 ... 2 ... 3 ... 4 ... 5 ...
  Collector A joins at 1: gets 1, 2, 3, 4, 5
  Collector B joins at 3: gets [3 replayed] 4, 5  ← latest value given immediately
```

### StateFlow Properties

```
┌──────────────────────────────────────────────────────────────────────┐
│  StateFlow<T>                                                        │
│                                                                      │
│  ① Always has a value (requires initialValue at creation)            │
│     _scores.value is never null, never throws                        │
│                                                                      │
│  ② Conflation (deduplication with equals()):                         │
│     _scores.value = listOf(score1)                                   │
│     _scores.value = listOf(score1)   ← same! emission is DROPPED.   │
│     Collector won't receive the second emission.                     │
│     → This prevents pointless recompositions in Compose.             │
│                                                                      │
│  ③ Thread-safe:                                                       │
│     .value can be read/written from ANY thread without locks.        │
│                                                                      │
│  ④ Replay of 1:                                                       │
│     New collectors always get the CURRENT value immediately.         │
└──────────────────────────────────────────────────────────────────────┘
```

### MutableStateFlow — The Encapsulation Pattern

```
  ViewModel                              UI
  ────────                               ──
  private val _scores =
    MutableStateFlow<List<Score>>(        val scores: StateFlow<List<Score>>
      emptyList()                    ──►      = _scores.asStateFlow()
    )
  │                                      │
  │  can write: _scores.value = list     │  can only read: scores.value
  │             _scores.update { ... }   │                 scores.collect { }
  ▼                                      ▼

  Only the ViewModel modifies state.
  UI can only observe. No accidental mutations from outside.
```

### .update{} — Thread-Safe State Modification

```kotlin
// ❌ WRONG — race condition possible if called from multiple coroutines:
_uiState.value = _uiState.value.copy(isLoading = true)
// Read → modify → write: another coroutine can write between read and write

// ✅ CORRECT — .update{} is atomic:
_uiState.update { currentState ->
    currentState.copy(isLoading = true)
}
// The lambda runs atomically. No race conditions.
```

### Complete StateFlow Example with UI State Pattern

```kotlin
// ── Define a single UI state class ────────────────────────────────────
data class ScoreUiState(
    val scores: List<Score> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val selectedSport: SportCategory = SportCategory.ALL
)

// ── ViewModel ──────────────────────────────────────────────────────────
class ScoreViewModel(private val repo: ScoreRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ScoreUiState())
    val uiState: StateFlow<ScoreUiState> = _uiState.asStateFlow()

    init { startPolling() }

    private fun startPolling() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            repo.getScoresFlow()
                .catch { e ->
                    _uiState.update { it.copy(error = e.message, isLoading = false) }
                }
                .collect { scores ->
                    _uiState.update { it.copy(scores = scores, isLoading = false, error = null) }
                }
        }
    }

    fun selectSport(sport: SportCategory) {
        _uiState.update { it.copy(selectedSport = sport) }
    }
}

// ── Compose UI ─────────────────────────────────────────────────────────
@Composable
fun ScoreScreen(viewModel: ScoreViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()   // recomposes on each change

    when {
        uiState.isLoading  -> LoadingSpinner()
        uiState.error != null -> ErrorMessage(uiState.error!!)
        else -> ScoreList(scores = uiState.scores)
    }
}

// ── Fragment (traditional View) ────────────────────────────────────────
class ScoreFragment : Fragment() {
    private val viewModel: ScoreViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressBar.isVisible = state.isLoading
                    binding.errorText.isVisible = state.error != null
                    adapter.submitList(state.scores)
                }
            }
        }
    }
}
```

### stateIn() — Converting a Cold Flow into a StateFlow

```kotlin
// Your repository returns a cold Flow. You want to expose it as StateFlow.
class ScoreViewModel(private val repo: ScoreRepository) : ViewModel() {

    // stateIn() converts Flow<T> → StateFlow<T>
    val scores: StateFlow<List<Score>> = repo.getScoresFlow()
        .stateIn(
            scope = viewModelScope,                        // who owns the StateFlow
            started = SharingStarted.WhileSubscribed(5_000),
            // ↑ keep the upstream flow running for 5 seconds after last subscriber
            //   leaves (survives config changes without restarting network call)
            initialValue = emptyList()
        )
}

// SharingStarted options:
// Eagerly         → start immediately, never stop
// Lazily          → start on first subscriber, never stop after that
// WhileSubscribed(5000) → start on first sub, stop 5s after last sub leaves ← recommended
```

### StateFlow vs LiveData

```
┌──────────────────┬────────────────────────────┬────────────────────────┐
│                  │  StateFlow                  │  LiveData              │
├──────────────────┼────────────────────────────┼────────────────────────┤
│  Language        │  Pure Kotlin               │  Android-specific      │
│  Initial value   │  Required                  │  Optional (nullable)   │
│  Lifecycle-aware │  Manual (repeatOnLifecycle) │  Built-in              │
│  Compose         │  First-class (collectAsState)│  Via .observeAsState()│
│  Transformations │  All Flow operators        │  map{}, switchMap{}    │
│  Testing         │  Easy (Turbine library)    │  Needs InstantExecutor │
│  Null safety     │  No nulls (has value)      │  Can be null           │
└──────────────────┴────────────────────────────┴────────────────────────┘
Modern Android apps prefer StateFlow. LiveData is legacy but still valid.
```

---

## 6. SharedFlow

### Why Not Use StateFlow for Everything?

StateFlow always replays its latest value to new collectors. That's perfect for
state, but catastrophic for one-time events.

```
THE ROTATION BUG with StateFlow for events:

  T=1:  Goal scored! _eventFlow.value = GoalScoredEvent("Football")
        UI: shows "GOAL!" toast ✅

  T=5:  User rotates phone. Fragment recreated. Collects _eventFlow.
        StateFlow REPLAYS its latest value: GoalScoredEvent("Football")
        UI: shows "GOAL!" toast AGAIN ❌ (event from 4 seconds ago)

  This is wrong. The goal already happened. Don't re-show the toast.
```

SharedFlow with `replay=0` is the fix. New collectors receive ONLY future
emissions — past events are gone and won't replay.

### Theory

```
┌──────────────────────────────────────────────────────────────────────┐
│  SharedFlow<T>                                                       │
│                                                                      │
│  • No fixed "current value" (unlike StateFlow)                      │
│  • Configurable replay cache (0 = no replay, 1 = last event, N = N) │
│  • Multiple collectors each receive their OWN copy of each emission  │
│  • Does NOT deduplicate — same value emitted twice = received twice  │
│  • Hot stream (runs independently of collectors)                     │
└──────────────────────────────────────────────────────────────────────┘
```

### StateFlow vs SharedFlow — Visual

```
STATEFLOW — "What is the current score?"
  emit(1-0) → [1-0]
  emit(2-0) → [2-0]   ← new collector joins here → immediately gets [2-0]
  emit(2-1) → [2-1]

  New collector ALWAYS gets latest value. Good for state.


SHAREDFLOW replay=0 — "A goal was just scored!"
  emit(GoalA) → active collector A gets GoalA ✅
              → collector B (joins 1s later) MISSES GoalA ← intentional!
  emit(GoalB) → collector A gets GoalB ✅
              → collector B gets GoalB ✅  (now subscribed)

  Late collectors miss past events. Correct for one-time events.


SHAREDFLOW replay=1 — "Show latest notification even after rotation"
  emit(GoalA) → active collector gets GoalA ✅
  emit(GoalB) → active collector gets GoalB ✅
  → new collector joins → replays GoalB (last 1 event) ✅

  Survives config changes but shows last event once.
```

### emit() vs tryEmit()

```kotlin
// emit() — suspend function. If buffer is full, it SUSPENDS until space opens.
// Use inside a coroutine.
viewModelScope.launch {
    _events.emit(ScoreEvent.GoalScored("Football", "Home"))   // suspends if full
}

// tryEmit() — NOT a suspend function. Returns false if buffer is full.
// Use when you're NOT inside a coroutine (e.g., from a callback).
fun notifyFromCallback(event: ScoreEvent) {
    val delivered = _events.tryEmit(event)
    if (!delivered) println("Buffer full, event dropped")
}
```

### Multiple Collectors — Each Gets Their Own Copy

```
SharedFlow with 2 collectors:

  ViewModel emits: [GoalA] ── [GoalB] ── [GoalC]
                      │           │           │
  Collector 1:      GoalA       GoalB       GoalC    ← receives ALL
  Collector 2:                  GoalB       GoalC    ← joined late, missed GoalA

  Unlike Channel (which routes each item to ONE consumer),
  SharedFlow BROADCASTS each item to ALL active collectors independently.
```

### Complete Event System

```kotlin
// ── Sealed class for all possible UI events ────────────────────────────
sealed class ScoreEvent {
    data class GoalScored(
        val sport: SportCategory,
        val scoringTeam: String,
        val newScore: String
    ) : ScoreEvent()

    data class MatchEnded(
        val sport: SportCategory,
        val finalScore: String
    ) : ScoreEvent()

    data class RedCard(
        val sport: SportCategory,
        val player: String
    ) : ScoreEvent()

    object NetworkError : ScoreEvent()
    object NetworkRestored : ScoreEvent()
}

// ── ViewModel ──────────────────────────────────────────────────────────
class ScoreViewModel : ViewModel() {

    // replay=0: no history for new collectors (one-shot events)
    // extraBufferCapacity=10: can hold 10 events in buffer before suspending
    private val _events = MutableSharedFlow<ScoreEvent>(
        replay = 0,
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<ScoreEvent> = _events.asSharedFlow()

    private val _scores = MutableStateFlow<List<Score>>(emptyList())
    val scores: StateFlow<List<Score>> = _scores.asStateFlow()

    fun startPolling() {
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val newScores = fetchAllScores()

                // Detect changes → emit events
                newScores.forEach { newScore ->
                    val prev = _scores.value.find { it.id == newScore.id }
                    if (prev != null && newScore.homeGoals > prev.homeGoals) {
                        _events.emit(ScoreEvent.GoalScored(
                            sport = newScore.sport,
                            scoringTeam = newScore.homeTeam,
                            newScore = "${newScore.homeGoals}-${newScore.awayGoals}"
                        ))
                    }
                }

                _scores.value = newScores
                delay(3000)
            }
        }
    }
}

// ── Compose UI — collecting events ─────────────────────────────────────
@Composable
fun ScoreScreen(viewModel: ScoreViewModel = viewModel()) {
    val scores by viewModel.scores.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // LaunchedEffect with Unit key = runs once when Composable enters composition
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ScoreEvent.GoalScored ->
                    snackbarHostState.showSnackbar("⚽ GOAL! ${event.scoringTeam}: ${event.newScore}")
                is ScoreEvent.MatchEnded ->
                    snackbarHostState.showSnackbar("Final score: ${event.finalScore}")
                is ScoreEvent.RedCard ->
                    snackbarHostState.showSnackbar("🟥 Red card: ${event.player}")
                ScoreEvent.NetworkError ->
                    snackbarHostState.showSnackbar("Connection lost. Retrying...")
                ScoreEvent.NetworkRestored ->
                    snackbarHostState.showSnackbar("Connected ✅")
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        ScoreList(scores = scores, modifier = Modifier.padding(padding))
    }
}
```

---

## 7. Flow Operators

### Theory — What is a Flow?

A **Flow** is a cold, asynchronous stream of values. It's like a recipe, not a meal:
the recipe does nothing until you start cooking (collect). Every time you collect,
the recipe runs fresh.

```
Flow PIPELINE — think of it as a series of pipes:

  ┌──────────────────────────────────────────────────────────────────┐
  │  SOURCE: flow { while(true) { emit(rawScore); delay(3000) } }    │
  │                                                                  │
  │    │                                                             │
  │    ▼                                                             │
  │  .filter { it.sport == "Football" }   ← drop non-Football       │
  │    │                                                             │
  │    ▼                                                             │
  │  .map { score -> score.toDisplayModel() }  ← transform type     │
  │    │                                                             │
  │    ▼                                                             │
  │  .distinctUntilChanged()   ← skip if identical to last emit     │
  │    │                                                             │
  │    ▼                                                             │
  │  .collect { display(it) }   ← TERMINAL: starts the whole flow   │
  └──────────────────────────────────────────────────────────────────┘

  IMPORTANT: operators are LAZY — building the pipeline does NOTHING.
  Only .collect{} (or .stateIn, .launchIn) actually starts execution.
```

### filter{} — The Gatekeeper

```
INPUT:   [Football 1-0] [Tennis 0-0] [Football 2-0] [Cricket 1-0] [Football 2-1]
FILTER:  it.sport == "Football"
OUTPUT:  [Football 1-0] [Football 2-0] [Football 2-1]

  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐
  │ Football │ │  Tennis  │ │ Football │ │ Cricket  │ │ Football │
  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘
       ✅           ❌            ✅            ❌            ✅
       │                          │                          │
       ▼                          ▼                          ▼
  [Football 1-0]            [Football 2-0]            [Football 2-1]

  filter{} can REDUCE the number of items in the stream.
  Items that don't pass the predicate are silently dropped.
```

```kotlin
// ── filter{} examples ─────────────────────────────────────────────────
allScoresFlow.filter { it.sport == SportCategory.FOOTBALL }

allScoresFlow.filter { score -> (score.homeGoals + score.awayGoals) > 2 }

allScoresFlow.filter { it.isLive }

// filterNot — inverse of filter
allScoresFlow.filterNot { it.sport == SportCategory.CRICKET }

// filterIsInstance — filter and cast at the same time
eventsFlow.filterIsInstance<ScoreEvent.GoalScored>()
    .collect { goalEvent -> showGoalAnimation(goalEvent) }
```

### map{} — The Transformer

```
INPUT:  Score("Football", home=Man City, homeGoals=2, away=Chelsea, awayGoals=1)
MAP:    { score -> ScoreDisplayModel(title="...", score="...") }
OUTPUT: ScoreDisplayModel(title="Man City vs Chelsea", score="2 - 1")

  map{} ALWAYS outputs the same number of items as input (unlike filter{}).
  It changes the TYPE or SHAPE of each item.

  INPUT count:  5 items
  OUTPUT count: 5 items  (every item gets transformed)
```

```kotlin
// ── map{} examples ────────────────────────────────────────────────────

// Data transformation: Score → ScoreDisplayModel
allScoresFlow
    .map { score ->
        ScoreDisplayModel(
            id = score.id,
            title = "${score.homeTeam} vs ${score.awayTeam}",
            scoreLine = "${score.homeGoals} - ${score.awayGoals}",
            sportEmoji = score.sport.emoji,
            isLive = score.isLive
        )
    }
    .collect { show(it) }

// Type transformation: List<Score> → Count
allScoresFlow
    .map { scores -> scores.count { it.homeGoals > it.awayGoals } }
    .collect { homeWins -> println("Home wins: $homeWins") }

// Calling suspend functions inside map (allowed!):
allScoresFlow
    .map { score ->
        val teamDetails = fetchTeamDetails(score.homeTeam)   // suspend call ✅
        score.copy(teamBadgeUrl = teamDetails.badgeUrl)
    }
    .collect { display(it) }
```

### debounce{} — The Noise Reducer

```
WITHOUT DEBOUNCE — typing "Football" triggers 8 API calls:
  F    Fo    Foo    Foot    Footb    Footba    Footbal    Football
  │    │     │      │       │        │         │          │
  API  API   API    API     API      API       API        API   ← 8 calls!

WITH DEBOUNCE(300ms) — only 1 call after user stops typing:
  F    Fo    Foo    Foot    Footb    Footba    Footbal    Football
  │    │     │      │       │        │         │          │
 reset reset reset  reset  reset    reset     reset      ├──── 300ms pause ────►
                                                         API  ← 1 call ✅

TIMELINE:
  T=0:    user types 'F'   → debounce timer starts (300ms)
  T=50:   user types 'Fo'  → timer RESETS
  T=100:  user types 'Foo' → timer RESETS
  T=450:  user types 'Football' → timer RESETS
  T=750:  no more typing → timer fires → emit('Football')
  Total: 1 API call instead of 8.
```

```kotlin
// ── debounce{} for search ─────────────────────────────────────────────
class SearchViewModel(private val repo: ScoreRepository) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")

    val searchResults: StateFlow<List<Score>> = _searchQuery
        .debounce(300)                        // wait 300ms of silence
        .filter { query -> query.length >= 2 } // ignore short queries
        .distinctUntilChanged()               // skip if query didn't change
        .flatMapLatest { query ->             // cancel previous search
            repo.searchScores(query)
        }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onQueryChanged(query: String) {
        _searchQuery.value = query
    }
}
```

### combine{} — The Multi-Source Merger

```
combine() takes N flows and emits whenever ANY of them emits.
It uses the LATEST value from each flow in the lambda.

REQUIRES: all flows must have emitted at least once before combine emits.

  Flow A (sport filter):  ─[ALL]──────────────────[FOOTBALL]──────────
  Flow B (all scores):    ──────[A,B,C]──[A,B,D]────────────[A,D]────
                                  │          │                    │
  combine():                    emit        emit                emit
  lambda(sport, scores):  [ALL,A,B,C]  [ALL,A,B,D]     [FOOTBALL,D only]

  Every emission from EITHER flow triggers the lambda with the latest of each.
```

```kotlin
// ── combine{} for sport filtering ─────────────────────────────────────
class ScoreViewModel(private val repo: ScoreRepository) : ViewModel() {

    private val _selectedSport = MutableStateFlow(SportCategory.ALL)
    private val _allScores = MutableStateFlow<List<Score>>(emptyList())

    // Auto-updates whenever EITHER the sport changes OR new scores arrive
    val filteredScores: StateFlow<List<Score>> = combine(
        _selectedSport,
        _allScores
    ) { sport, scores ->
        if (sport == SportCategory.ALL) scores
        else scores.filter { it.sport == sport }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectSport(sport: SportCategory) {
        _selectedSport.value = sport   // triggers combine → filtered list updates
    }
}

// ── combine{} with 3+ flows ────────────────────────────────────────────
val uiState: Flow<ScoreUiState> = combine(
    _scoresFlow,
    _selectedSportFlow,
    _isLoadingFlow
) { scores, sport, isLoading ->
    val filtered = if (sport == SportCategory.ALL) scores
                   else scores.filter { it.sport == sport }
    ScoreUiState(scores = filtered, selectedSport = sport, isLoading = isLoading)
}
```

### flatMapLatest{} — Cancel Old, Start New

```
PROBLEM: User changes sport filter while previous data is still loading.
         Old results arrive AFTER new filter selected → wrong data shown!

flatMapLatest{}: when a new value arrives, CANCEL the old inner flow,
                 start a new one with the new value.

  sportFilter: ─[Football]─────────────────[Tennis]──────────────────
  inner flow:    ──[loading Football...] cancelled!
                                              ──[loading Tennis...]──
  collector sees:                              Tennis results ✅
                              Football results NEVER arrive (cancelled)

  ONLY Tennis results reach the UI.
```

```kotlin
// ── flatMapLatest{} for filter changes ────────────────────────────────
val liveScores: Flow<List<Score>> = _selectedSportFlow
    .flatMapLatest { sport ->
        // Every time sport changes, previous flow is cancelled, new one starts
        if (sport == SportCategory.ALL) {
            repo.getAllScoresFlow()
        } else {
            repo.getScoresFlow(sport)
        }
    }

// ── Compare: flatMapLatest vs flatMapMerge vs flatMapConcat ──────────
// flatMapLatest:  cancel old, start new  ← user input / filter changes
// flatMapMerge:   run ALL concurrently    ← parallel independent operations
// flatMapConcat:  queue them, one at a time, in order ← ordered processing
```

### More Essential Operators

```kotlin
// ── distinctUntilChanged — skip duplicates ────────────────────────────
scoreFlow
    .distinctUntilChanged()   // only emit if value changed from last emission
    .collect { updateUI(it) } // prevents unnecessary recompositions

// ── onEach — side effects without changing the stream ─────────────────
scoreFlow
    .onEach { score -> logAnalytics("Score update: $score") }  // log every score
    .filter { it.sport == SportCategory.FOOTBALL }
    .collect { updateUI(it) }
// onEach doesn't transform the item — it passes it through unchanged

// ── catch — handle errors mid-pipeline ────────────────────────────────
scoreFlow
    .catch { e ->
        println("Flow error: $e")
        emit(emptyList())   // emit a fallback value and continue
    }
    .collect { display(it) }

// ── retry — automatic retry on error ──────────────────────────────────
scoreFlow
    .retry(3) { cause -> cause is IOException }   // retry up to 3 times on IO errors
    .collect { display(it) }

// ── retryWhen — retry with backoff strategy ───────────────────────────
scoreFlow
    .retryWhen { cause, attempt ->
        if (cause is IOException && attempt < 3) {
            delay(1000L * (attempt + 1))   // 1s, 2s, 3s exponential backoff
            true   // retry
        } else false  // give up
    }
    .collect { display(it) }

// ── scan — running accumulator, emits every step ─────────────────────
// Like fold{} but emits intermediate results
goalEventsFlow
    .scan(0) { totalGoals, event ->
        if (event is ScoreEvent.GoalScored) totalGoals + 1 else totalGoals
    }
    .collect { total -> binding.totalGoalsText.text = "Total goals today: $total" }

// ── take / takeWhile — limit items collected ──────────────────────────
scoreFlow.take(10).collect { display(it) }   // stop after 10 items

scoreFlow
    .takeWhile { score -> score.homeGoals < 5 }   // stop when any team hits 5
    .collect { display(it) }

// ── buffer — decouple producer speed from consumer speed ─────────────
fastScoreProducerFlow         // emits every 100ms
    .buffer(50)               // buffer up to 50 items
    .collect { score ->
        slowDbInsert(score)   // takes 800ms, no longer blocks producer
    }

// ── zip — pair items from two flows one-to-one ────────────────────────
val homeTeams = flowOf("Man City", "Arsenal", "Liverpool")
val awayTeams = flowOf("Chelsea", "Spurs", "Everton")

homeTeams.zip(awayTeams) { home, away ->
    "$home vs $away"
}.collect { println(it) }
// Man City vs Chelsea
// Arsenal vs Spurs
// Liverpool vs Everton
// (zip stops when the SHORTER flow ends)

// ── merge — interleave multiple flows ─────────────────────────────────
val allUpdates: Flow<Score> = merge(
    footballRepo.getScoreFlow(),
    tennisRepo.getScoreFlow(),
    cricketRepo.getScoreFlow()
)
// Items from all three flows arrive in the order they are emitted
allUpdates.collect { score -> display(score) }
```

### flowOn() — Set the Upstream Dispatcher

```kotlin
// Everything ABOVE flowOn() runs on the specified dispatcher.
// The .collect{} block runs on the scope's dispatcher (usually Main).

repo.getScoreFlow()         // produces on IO (because of flowOn below)
    .map { parseScore(it) } // map runs on IO
    .filter { it.isLive }   // filter runs on IO
    .flowOn(Dispatchers.IO) // ← EVERYTHING above this runs on IO
    .collect { score ->     // ← collect runs on Main (viewModelScope's dispatcher)
        _scores.value = score
    }

// flowOn does NOT affect operators placed BELOW it.
// withContext() is for one-shot operations; flowOn is for whole-flow pipelines.
```

---

## 8. Channels

### Theory — What is a Channel?

Flow is designed for UI-facing data streams. Channel is a lower-level
primitive for **coroutine-to-coroutine communication** — like a thread-safe queue.
One coroutine `send()`s items in; another `receive()`s items out.

```
CHANNEL AS A PIPE:

  PRODUCER COROUTINE              CHANNEL BUFFER           CONSUMER COROUTINE
  ──────────────────         ┌──────────────────────┐     ─────────────────────
  channel.send(Score A) ──►  │  [A] [  ] [  ] [  ] │ ──► val x = channel.receive()
  channel.send(Score B) ──►  │  [A] [B] [  ] [  ] │     (suspends if empty)
  channel.send(Score C) ──►  │  [A] [B] [C] [  ] │
  channel.send(Score D) ──►  │  [A] [B] [C] [D] │     ← full! next send() suspends
                             └──────────────────────┘
  (send() suspends if buffer full)           (items delivered FIFO)
```

### The Four Channel Types

```
RENDEZVOUS  (capacity = 0, the default)
  ─────────────────────────────────────
  Zero buffer. send() MUST wait for receive() and vice versa.
  They rendezvous (meet) at the same moment.
  Perfect for: tight synchronization between exactly two coroutines.

  Producer: ─[send A]─── waiting ───►[matched]─[send B]── waiting ──►[matched]
  Consumer: ──────────[receive A]◄───             ─────[receive B]◄──
  (Each send/receive is paired)

BUFFERED  (capacity = N, e.g., 10 or 64)
  ──────────────────────────────────────
  Producer can send up to N items without waiting.
  Only suspends when buffer is full.
  Consumer gets items in FIFO order.
  Use when: producer bursts faster than consumer, or you want to decouple speed.

CONFLATED  (capacity = Channel.CONFLATED)
  ────────────────────────────────────────
  Buffer holds only the MOST RECENT item. Old items are overwritten.
  send() never suspends (old value is dropped to make room).
  Consumer always gets the latest value — like StateFlow but as a Channel.
  Use when: only the latest value matters (live score display).

UNLIMITED  (capacity = Channel.UNLIMITED)
  ────────────────────────────────────────
  Grows indefinitely. send() never suspends.
  Risk: if producer is much faster than consumer, memory will grow unbounded.
  Use sparingly when production rate is definitely bounded.
```

### Code — Producer / Consumer

```kotlin
// ── Channel as internal pipeline ──────────────────────────────────────
class ScoreViewModel : ViewModel() {

    // Buffered: producer can send 10 items without waiting for consumer
    private val scoreChannel = Channel<Score>(capacity = 10)

    init {
        startProducer()
        startConsumer()
    }

    // PRODUCER — fetches data on IO, sends to channel
    private fun startProducer() {
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val score = fetchFromNetwork()   // suspend: waits for data
                scoreChannel.send(score)         // suspend: waits if buffer full
                delay(3000)
            }
        }
    }

    // CONSUMER — receives on Default, does heavy processing
    private fun startConsumer() {
        viewModelScope.launch(Dispatchers.Default) {
            for (score in scoreChannel) {        // suspend: waits for next item
                val processed = heavyProcess(score)
                withContext(Dispatchers.Main) {
                    _scores.value = processed    // update UI on Main
                }
            }
            // for-loop ends when channel.close() is called
        }
    }

    override fun onCleared() {
        super.onCleared()
        scoreChannel.close()   // tells consumer's for-loop to stop iterating
        // viewModelScope.cancel() stops producer + consumer coroutines
    }
}
```

### produce{} Builder — Coroutine-Owned Channel

```kotlin
// produce{} creates a coroutine AND a ReceiveChannel together.
// When the coroutine finishes, the channel is closed automatically.
fun CoroutineScope.scoreProducer(sport: String): ReceiveChannel<Score> = produce {
    while (isActive) {
        val score = fetchScore(sport)
        send(score)                // push to channel
        delay(3000)
    }
}

// Usage:
viewModelScope.launch {
    val channel = scoreProducer("Football")
    for (score in channel) {       // iterates until producer coroutine ends
        _scores.value = score
    }
}
```

### Fan-In — Multiple Producers, One Consumer

```
  [Producer: Football] ──────┐
  [Producer: Tennis]   ──────┤──► [Merged Channel] ──► [Consumer: Display All]
  [Producer: Cricket]  ──────┘

  All updates flow into one place. Consumer handles them in arrival order.
  Simpler than managing 3 separate collect{} calls.
```

```kotlin
// Fan-In using merge() on Flows (cleaner than channels for this pattern):
val allScores: Flow<Score> = merge(
    footballRepo.getScoreFlow(),    // Flow 1
    tennisRepo.getScoreFlow(),      // Flow 2
    cricketRepo.getScoreFlow()      // Flow 3
)
allScores.collect { score -> _scores.update { list -> list + score } }

// Fan-In using Channels directly:
fun CoroutineScope.mergeScoreChannels(
    vararg channels: ReceiveChannel<Score>
): ReceiveChannel<Score> = produce {
    channels.forEach { channel ->
        launch {
            for (score in channel) send(score)   // forward from each channel
        }
    }
}
```

### Channel vs Flow Decision

```
┌────────────────────────────────┬──────────────────────┬────────────────────────┐
│  Need                          │  Use Channel         │  Use Flow              │
├────────────────────────────────┼──────────────────────┼────────────────────────┤
│  Multiple consumers for one    │  Channel             │  SharedFlow            │
│  stream                        │  (each item → 1 consumer)│ (each item → all) │
├────────────────────────────────┼──────────────────────┼────────────────────────┤
│  Lifecycle-unaware pipeline    │  Channel             │                        │
│  between two coroutines        │                      │                        │
├────────────────────────────────┼──────────────────────┼────────────────────────┤
│  UI data exposed to the View   │                      │  StateFlow / Flow      │
├────────────────────────────────┼──────────────────────┼────────────────────────┤
│  Rich transformations needed   │                      │  Flow (map/filter/etc.)│
├────────────────────────────────┼──────────────────────┼────────────────────────┤
│  Backpressure: producer faster │  Channel.BUFFERED    │  .buffer() operator    │
│  than consumer                 │                      │                        │
├────────────────────────────────┼──────────────────────┼────────────────────────┤
│  Only latest value matters     │  Channel.CONFLATED   │  StateFlow             │
└────────────────────────────────┴──────────────────────┴────────────────────────┘
Rule of thumb: prefer Flow + operators for most things.
Use Channel when you explicitly need point-to-point coroutine communication.
```

---

## 9. Project — LiveScorePoller App

### What You're Building

```
┌─────────────────────────────────────────────────────────────────────────┐
│                      LIVESCOREPOLLER APP                                │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │  SPORT FILTER                                                     │  │
│  │  ┌──────┐ ┌──────────┐ ┌────────┐ ┌─────────┐ ┌────────────┐   │  │
│  │  │  ALL │ │ Football │ │ Tennis │ │ Cricket │ │ Basketball │   │  │
│  │  └──────┘ └──────────┘ └────────┘ └─────────┘ └────────────┘   │  │
│  ├───────────────────────────────────────────────────────────────────┤  │
│  │  LIVE SCORE TICKER                                                │  │
│  │  ┌─────────────────────────────────────────────────────────────┐ │  │
│  │  │ ⚽ Football                                   LIVE          │ │  │
│  │  │    Man City  2 ──────── 1  Chelsea      ← 🟡 CHANGED       │ │  │
│  │  ├─────────────────────────────────────────────────────────────┤ │  │
│  │  │ 🎾 Tennis                                    LIVE          │ │  │
│  │  │    Djokovic  6 ──────── 4  Federer                         │ │  │
│  │  ├─────────────────────────────────────────────────────────────┤ │  │
│  │  │ 🏏 Cricket                                   LIVE          │ │  │
│  │  │    India    245/6 ─── 198/8  Australia                     │ │  │
│  │  └─────────────────────────────────────────────────────────────┘ │  │
│  ├───────────────────────────────────────────────────────────────────┤  │
│  │  [Snackbar: ⚽ GOAL! Man City scores! Now 2-1]                    │  │
│  └───────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
```

### Architecture — Every Layer Explained

```
┌──────────────────────────────────────────────────────────────────────────┐
│  LAYER 1: Data Source  (FakeScoreDataSource.kt)                          │
│  ─────────────────────────────────────────────                           │
│  Owns:  A list of hardcoded matches                                      │
│  Does:  Randomly increments goals every 3 seconds to simulate live data  │
│  API:   getScoresFlow(): Flow<List<Score>>                                │
│         = flow { while(true) { mutateRandomly(); emit(scores); delay(3s)}}│
│                                                                          │
│  Concepts used: flow{} builder, emit(), delay()                          │
│                                                                          │
├──────────────────────────────────────────────────────────────────────────┤
│  LAYER 2: Repository  (ScoreRepository.kt)                               │
│  ─────────────────────────────────────────                               │
│  Owns:  Nothing — delegates to FakeScoreDataSource                       │
│  Does:  Adds .flowOn(Dispatchers.IO) — marks this as "IO work"           │
│  API:   getScoresFlow(): Flow<List<Score>>                                │
│         = dataSource.getScoresFlow().flowOn(Dispatchers.IO)              │
│                                                                          │
│  Concepts used: flowOn(), Dispatchers.IO                                 │
│  Why it exists: ViewModel should not know about IO details.              │
│                 Repository is the single source of truth.                │
│                                                                          │
├──────────────────────────────────────────────────────────────────────────┤
│  LAYER 3: ViewModel  (ScoreViewModel.kt)                                 │
│  ─────────────────────────────────────────                               │
│  Owns:                                                                   │
│  • _allScores:     MutableStateFlow<List<Score>>(emptyList())            │
│  • _selectedSport: MutableStateFlow<SportCategory>(ALL)                  │
│  • _events:        MutableSharedFlow<ScoreEvent>(replay=0)               │
│                                                                          │
│  Derives:                                                                │
│  • filteredScores = combine(_selectedSport, _allScores) { s, l -> ... } │
│                     .stateIn(viewModelScope, WhileSubscribed(5000), [])  │
│                                                                          │
│  Does:                                                                   │
│  • viewModelScope.launch { repo.getScoresFlow().collect { ... } }        │
│  • On each emission: detect goal changes → _events.emit(GoalScored)      │
│  • On each emission: update _allScores.value = newList                   │
│                                                                          │
│  Concepts used: viewModelScope, launch{}, StateFlow, SharedFlow,         │
│                 combine{}, stateIn(), .update{}, structured concurrency  │
│                                                                          │
├──────────────────────────────────────────────────────────────────────────┤
│  LAYER 4: UI  (ScoreListScreen.kt — Jetpack Compose)                     │
│  ─────────────────────────────────────────────────                       │
│  Reads:                                                                  │
│  • val scores by viewModel.filteredScores.collectAsState()               │
│                                                                          │
│  Reacts to events:                                                       │
│  • LaunchedEffect(Unit) { viewModel.events.collect { show snackbar } }   │
│                                                                          │
│  Sends user actions:                                                     │
│  • Chip click → viewModel.selectSport(sport)                             │
│                                                                          │
│  Renders:                                                                │
│  • LazyColumn of ScoreCard composables                                   │
│  • ScoreCard highlights if score changed (compare prev vs current)       │
│  • Color animation with animateColorAsState()                            │
│                                                                          │
│  Concepts used: collectAsState, LaunchedEffect, SharedFlow collection    │
└──────────────────────────────────────────────────────────────────────────┘
```

### Data Models

```kotlin
// ── Enums ─────────────────────────────────────────────────────────────
enum class SportCategory(val displayName: String, val emoji: String) {
    ALL("All Sports",   "🏆"),
    FOOTBALL("Football","⚽"),
    TENNIS("Tennis",    "🎾"),
    CRICKET("Cricket",  "🏏"),
    BASKETBALL("Basketball", "🏀")
}

// ── Core data ─────────────────────────────────────────────────────────
data class Score(
    val id: String,                               // unique per match
    val sport: SportCategory,
    val homeTeam: String,
    val awayTeam: String,
    val homeGoals: Int,
    val awayGoals: Int,
    val isLive: Boolean = true,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    val displayScore: String get() = "$homeGoals - $awayGoals"
    val displayTitle: String get() = "$homeTeam vs $awayTeam"
}

// ── One-time events ────────────────────────────────────────────────────
sealed class ScoreEvent {
    data class GoalScored(
        val sport: SportCategory,
        val scoringTeam: String,
        val matchTitle: String,
        val newScore: String
    ) : ScoreEvent()

    data class MatchEnded(val sport: SportCategory, val finalScore: String) : ScoreEvent()
    object NetworkError    : ScoreEvent()
    object NetworkRestored : ScoreEvent()
}

// ── UI state (single object for the whole screen) ─────────────────────
data class ScoreUiState(
    val scores: List<Score>          = emptyList(),
    val selectedSport: SportCategory = SportCategory.ALL,
    val isLoading: Boolean           = true,
    val errorMessage: String?        = null
)
```

### Step-by-Step Build Plan

```
STEP 1 — FakeScoreDataSource
─────────────────────────────
  Goal: Produce a realistic-feeling live score stream without any network.

  Starting matches to hardcode (varies by sport):
  • Football: Man City vs Chelsea, Arsenal vs Liverpool
  • Tennis:   Djokovic vs Federer
  • Cricket:  India vs Australia
  • Basketball: Lakers vs Bulls

  getScoresFlow() skeleton:
    fun getScoresFlow(): Flow<List<Score>> = flow {
        val matches = buildInitialMatchList()    // mutable list
        while (true) {
            // Every tick: randomly pick a match and increment goals
            val randomMatch = matches.random()
            val updatedMatch = randomMatch.copy(
                homeGoals = randomMatch.homeGoals + if (Random.nextFloat() < 0.1f) 1 else 0,
                awayGoals = randomMatch.awayGoals + if (Random.nextFloat() < 0.08f) 1 else 0,
                lastUpdated = System.currentTimeMillis()
            )
            matches[matches.indexOf(randomMatch)] = updatedMatch
            emit(matches.toList())    // emit a snapshot (not the mutable list)
            delay(3000)
        }
    }

  Things to practice:
  • flow {} builder
  • emit() — how to push values into a flow
  • delay() as a suspend function inside flow
  • Emitting immutable snapshots (toList()) of mutable state

──────────────────────────────────────────────────────────────────────────
STEP 2 — ScoreRepository
──────────────────────────
  Goal: Clean interface for the ViewModel. Hides data source details.

  class ScoreRepository(private val dataSource: FakeScoreDataSource) {
      fun getScoresFlow(): Flow<List<Score>> =
          dataSource.getScoresFlow()
              .flowOn(Dispatchers.IO)   // upstream runs on IO pool
  }

  Things to practice:
  • flowOn() — when to use it and where to put it
  • Why the repository, not the ViewModel, applies flowOn

──────────────────────────────────────────────────────────────────────────
STEP 3 — ScoreViewModel
─────────────────────────
  Goal: Bridge between repository data and UI-ready state.

  class ScoreViewModel(private val repo: ScoreRepository) : ViewModel() {

      // PRIVATE mutable state
      private val _allScores     = MutableStateFlow<List<Score>>(emptyList())
      private val _selectedSport = MutableStateFlow(SportCategory.ALL)
      private val _events        = MutableSharedFlow<ScoreEvent>(replay = 0, extraBufferCapacity = 10)

      // PUBLIC read-only state
      val events: SharedFlow<ScoreEvent> = _events.asSharedFlow()

      // DERIVED state — combine filter + all scores automatically
      val filteredScores: StateFlow<List<Score>> = combine(
          _selectedSport,
          _allScores
      ) { sport, scores ->
          if (sport == SportCategory.ALL) scores
          else scores.filter { it.sport == sport }
      }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

      init { startCollecting() }

      private fun startCollecting() {
          viewModelScope.launch {
              repo.getScoresFlow()
                  .catch { e -> _events.emit(ScoreEvent.NetworkError) }
                  .collect { newScores ->
                      detectGoals(newScores)           // check for goals
                      _allScores.value = newScores     // update state
                  }
          }
      }

      private fun detectGoals(newScores: List<Score>) {
          newScores.forEach { new ->
              val old = _allScores.value.find { it.id == new.id }
              if (old != null) {
                  if (new.homeGoals > old.homeGoals) {
                      viewModelScope.launch {
                          _events.emit(ScoreEvent.GoalScored(
                              sport = new.sport,
                              scoringTeam = new.homeTeam,
                              matchTitle = new.displayTitle,
                              newScore = new.displayScore
                          ))
                      }
                  }
              }
          }
      }

      fun selectSport(sport: SportCategory) {
          _selectedSport.value = sport   // triggers combine → filteredScores updates
      }
  }

  Things to practice:
  • MutableStateFlow with private backing + public StateFlow
  • MutableSharedFlow for one-time events
  • combine{} for derived state
  • stateIn() to expose Flow as StateFlow
  • launch{} inside init {}
  • Detecting changes (old vs new comparison)

──────────────────────────────────────────────────────────────────────────
STEP 4 — ScoreListScreen (Compose UI)
───────────────────────────────────────
  Goal: Reactive UI that auto-updates without manual refresh.

  @Composable
  fun ScoreListScreen(viewModel: ScoreViewModel = viewModel()) {
      val scores by viewModel.filteredScores.collectAsState()
      val snackbarHostState = remember { SnackbarHostState() }

      // Collect one-time events
      LaunchedEffect(Unit) {
          viewModel.events.collect { event ->
              when (event) {
                  is ScoreEvent.GoalScored ->
                      snackbarHostState.showSnackbar("⚽ GOAL! ${event.scoringTeam}: ${event.newScore}")
                  ScoreEvent.NetworkError ->
                      snackbarHostState.showSnackbar("Connection lost...")
                  else -> {}
              }
          }
      }

      Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
          Column(modifier = Modifier.padding(padding)) {
              SportFilterRow(onSportSelected = { viewModel.selectSport(it) })
              ScoreList(scores = scores)
          }
      }
  }

  Highlight logic:
  @Composable
  fun ScoreCard(score: Score, previousScore: Score?) {
      val hasChanged = previousScore != null &&
          (score.homeGoals != previousScore.homeGoals ||
           score.awayGoals != previousScore.awayGoals)

      val backgroundColor by animateColorAsState(
          targetValue = if (hasChanged) Color(0xFFFFF176) else Color.White,
          animationSpec = tween(durationMillis = 600)
      )

      Card(
          colors = CardDefaults.cardColors(containerColor = backgroundColor)
      ) {
          // display score.displayTitle and score.displayScore
      }
  }

  Things to practice:
  • collectAsState() — observe StateFlow in Compose
  • LaunchedEffect(Unit) — run a side effect once in Compose
  • collect{} on SharedFlow inside LaunchedEffect
  • animateColorAsState() — animate background on score change
  • Passing previous state into ScoreCard for highlight comparison
```

### Full Coroutine Lifecycle — What Happens When

```
APP LAUNCH:
  ScoreViewModel created
       │
       ├──► viewModelScope = SupervisorJob() + Dispatchers.Main
       │
       ├──► init { startCollecting() }
       │         └── viewModelScope.launch {           [Job A starts]
       │                 repo.getScoresFlow()           [subscribed to flow]
       │                     .collect { ... }           [suspending here]
       │             }
       │
       └──► filteredScores StateFlow → starts watching _selectedSport + _allScores

EVERY 3 SECONDS:
  FakeScoreDataSource emits new List<Score>
       │
       ▼
  .flowOn(Dispatchers.IO) → emission arrives on IO thread
       │
       ▼
  collect { newScores -> ... } runs
       │
       ├──► detectGoals(newScores)
       │         └── found goal → viewModelScope.launch { _events.emit(GoalScored) }
       │                               └── UI LaunchedEffect receives it → snackbar ✅
       │
       └──► _allScores.value = newScores
                 └── combine triggers → filteredScores recalculates → UI recomposes ✅

USER CHANGES FILTER (tap "Football" chip):
  viewModel.selectSport(SportCategory.FOOTBALL)
       │
       ▼
  _selectedSport.value = FOOTBALL
       │
       ▼
  combine() triggers → filteredScores recalculates → only Football scores ✅
  UI automatically recomposes with filtered list

USER PRESSES BACK:
  Fragment/Activity destroyed
       │
       ▼
  ViewModel.onCleared()
       │
       ▼
  viewModelScope.cancel()
       │
       ├──► Job A (collect loop) → CancellationException at next delay()
       │       └── loop exits cleanly
       ├──► filteredScores stateIn stops
       └──► All coroutines terminated. No leaks. ✅
```

### Concept → Location Mapping

```
┌──────────────────────────┬────────────────────────────────────────────────┐
│  Concept                 │  Where in the project                          │
├──────────────────────────┼────────────────────────────────────────────────┤
│  suspend fun             │  Repository methods, FakeScoreDataSource helper │
│  flow { }                │  FakeScoreDataSource.getScoresFlow()           │
│  emit()                  │  Inside flow{} in FakeScoreDataSource           │
│  launch {}               │  ViewModel.startCollecting(), detectGoals()     │
│  async {}                │  If you extend to fetch multiple endpoints      │
│  Dispatchers.IO          │  .flowOn(Dispatchers.IO) in Repository          │
│  Dispatchers.Main        │  Implicit in viewModelScope (default)           │
│  withContext             │  If you need to switch inside a suspend fun     │
│  Structured Concurrency  │  viewModelScope ties all jobs to ViewModel life │
│  StateFlow               │  _allScores, _selectedSport, filteredScores     │
│  SharedFlow              │  _events (GoalScored, NetworkError events)      │
│  filter {}               │  Inside combine lambda: filter by sport         │
│  map {}                  │  Score → ScoreDisplayModel transformation       │
│  combine {}              │  filteredScores = combine(sport, allScores)     │
│  stateIn()               │  Expose filteredScores as StateFlow             │
│  debounce {}             │  If search box added to filter by team name     │
│  Channel                 │  Internal producer→consumer if you want to      │
│                          │  decouple score fetching from processing         │
│  flowOn()                │  Repository: mark upstream as IO work           │
│  catch {}                │  Handle network errors in flow pipeline         │
└──────────────────────────┴────────────────────────────────────────────────┘
```

---

## Quick Reference Card

```
┌─────────────────────────────────────────────────────────────────────────┐
│  WHEN TO USE WHAT — FULL CHEAT SHEET                                    │
├─────────────────────────────┬───────────────────────────────────────────┤
│  Async work, need result    │  async { }.await()                        │
│  Async work, no result      │  launch { }                               │
│  Block thread (tests only)  │  runBlocking { }                          │
├─────────────────────────────┼───────────────────────────────────────────┤
│  UI / View updates          │  Dispatchers.Main                         │
│  Network / DB / File        │  Dispatchers.IO                           │
│  CPU-heavy computation      │  Dispatchers.Default                      │
│  Switch mid-coroutine       │  withContext(Dispatcher)                  │
│  Switch upstream Flow       │  .flowOn(Dispatcher)                      │
├─────────────────────────────┼───────────────────────────────────────────┤
│  Current observable state   │  StateFlow (has .value, replays latest)   │
│  One-time events            │  SharedFlow(replay=0)                     │
│  Events surviving rotation  │  SharedFlow(replay=1)                     │
├─────────────────────────────┼───────────────────────────────────────────┤
│  Filter stream items        │  .filter { condition }                    │
│  Transform each item        │  .map { transform }                       │
│  Throttle rapid input       │  .debounce(300)                           │
│  Merge N flows              │  combine(f1, f2) { a, b -> ... }          │
│  Cancel old on new value    │  .flatMapLatest { flow }                  │
│  Skip duplicate emissions   │  .distinctUntilChanged()                  │
│  Running total / accumulate │  .scan(initial) { acc, item -> }          │
│  Side effect in pipeline    │  .onEach { sideEffect() }                 │
│  Handle errors in flow      │  .catch { e -> emit(fallback) }           │
│  Auto-retry on error        │  .retry(N) { it is IOException }          │
│  Cold Flow → StateFlow      │  .stateIn(scope, started, initial)        │
│  Interleave N flows         │  merge(f1, f2, f3)                        │
│  Pair items 1-to-1          │  f1.zip(f2) { a, b -> ... }               │
│  Limit items collected      │  .take(N) or .takeWhile { }               │
│  Decouple producer speed    │  .buffer(N)                               │
├─────────────────────────────┼───────────────────────────────────────────┤
│  Coroutine-to-coroutine     │  Channel<T>                               │
│  Latest value only          │  Channel.CONFLATED or StateFlow           │
│  Parallel consumers         │  Channel.BUFFERED + multiple launch{}     │
│  Merge multiple producers   │  merge(flow1, flow2, flow3)               │
├─────────────────────────────┼───────────────────────────────────────────┤
│  Scope to ViewModel         │  viewModelScope (auto-cancelled)          │
│  Scope to Fragment view     │  viewLifecycleOwner.lifecycleScope         │
│  Only when foreground       │  repeatOnLifecycle(Lifecycle.State.STARTED)│
│  Siblings survive crashes   │  SupervisorJob / supervisorScope          │
│  All-or-nothing siblings    │  coroutineScope (default Job)             │
│  Thread-safe state update   │  _stateFlow.update { it.copy(...) }       │
└─────────────────────────────┴───────────────────────────────────────────┘
```

---

*Next:* Day 3 — Dependency Injection with Hilt

│                                                                      │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │                         UI Layer                            │    │
│  │  ScoreListScreen (Compose)                                  │    │
│  │  ┌────────────────┐   ┌──────────────────────────────────┐  │    │
│  │  │  Sport Filter  │   │     Live Score Ticker (LazyCol)  │  │    │
│  │  │  Chip Group    │   │     ScoreCard (highlight changed)│  │    │
│  │  └────────────────┘   └──────────────────────────────────┘  │    │
│  └──────────────────────────────┬────────────────────────────┘    │
│                                 │ collectAsState() / collect {}     │
│  ┌──────────────────────────────▼────────────────────────────┐    │
│  │                       ViewModel Layer                      │    │
│  │  ScoreViewModel                                            │    │
│  │  ├── scores: StateFlow<List<Score>>   (current scores)    │    │
│  │  ├── events: SharedFlow<ScoreEvent>  (one-time alerts)    │    │
│  │  ├── selectedSport: StateFlow<String> (filter state)      │    │
│  │  └── filteredScores = combine(selectedSport, allScores)   │    │
│  └──────────────────────────────┬────────────────────────────┘    │
│                                 │ suspend fun                       │
│  ┌──────────────────────────────▼────────────────────────────┐    │
│  │                      Repository Layer                      │    │
│  │  ScoreRepository                                           │    │
│  │  └── getScoresFlow(): Flow<List<Score>>                    │    │
│  │       (fake data, emits every 3 seconds)                   │    │
│  └──────────────────────────────┬────────────────────────────┘    │
│                                 │                                   │
│  ┌──────────────────────────────▼────────────────────────────┐    │
│  │                      Data Layer                            │    │
│  │  FakeScoreDataSource                                       │    │
│  │  └── generates random score updates (simulates network)   │    │
│  └────────────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────────────┘
```

### Coroutine Flow Through the App

```
User opens screen
       │
       ▼
viewModelScope created (tied to ViewModel lifecycle)
       │
       ├──► launch(IO) { scorePollingLoop }
       │         │
       │         └── every 3s: fetchScore() ──► _allScores.value = newList
       │                                               │
       │                                    (if goal detected)
       │                                               │
       │                                     _events.emit(GoalScored)
       │
       ├──► combine(selectedSport, allScores)
       │         └── filteredScores Flow (auto-updates)
       │
       └──► UI collects filteredScores + events separately

User presses Back
       │
       ▼
ViewModel.onCleared()
       │
       ▼
viewModelScope.cancel()
       │
       ├──► scorePollingLoop cancelled (at next suspend point)
       └──► All child coroutines cleaned up ✅
```

### Key Design Decisions

```
DECISION 1: Why StateFlow for scores, SharedFlow for events?
─────────────────────────────────────────────────────────────
  Scores:  User rotates screen → should see current scores immediately.
           StateFlow replays latest value. ✅

  Events:  User rotates screen → should NOT see "Goal!" toast again.
           SharedFlow(replay=0) does not replay. ✅


DECISION 2: Why launch for polling, async for parallel fetches?
────────────────────────────────────────────────────────────────
  Polling loop: fire-and-forget, updates state as a side effect → launch{}
  Fetching all sports at once: need all results → async{} + await()


DECISION 3: Why combine() for filtered scores?
───────────────────────────────────────────────
  Both the filter AND the score data can change independently.
  combine() reacts to EITHER change automatically.
  No need to manually re-query when filter changes. ✅


DECISION 4: Why Dispatchers.IO for fetching?
─────────────────────────────────────────────
  Even though it's fake data, real apps fetch from network.
  Model the correct pattern from day one. ✅
```

### Score Data Model

```kotlin
// The core data types you'll work with
data class Score(
    val sport: String,
    val homeTeam: String,
    val awayTeam: String,
    val homeGoals: Int,
    val awayGoals: Int,
    val lastUpdated: Long = System.currentTimeMillis()
)

sealed class ScoreEvent {
    data class GoalScored(val sport: String, val team: String) : ScoreEvent()
    data class MatchEnded(val sport: String) : ScoreEvent()
    object NetworkError : ScoreEvent()
}

enum class SportCategory {
    ALL, FOOTBALL, TENNIS, CRICKET, BASKETBALL
}
```

### Feature Breakdown (What to Build Step by Step)

```
STEP 1 — Fake Data Source
  ┌─────────────────────────────────────────────────────┐
  │  FakeScoreDataSource                                │
  │  • List of hardcoded matches                        │
  │  • getScoresFlow(): Flow<List<Score>>               │
  │  • Use flow { while(true) { emit(...); delay(3000)}}│
  │  • Random score increments to simulate live updates │
  └─────────────────────────────────────────────────────┘

STEP 2 — Repository
  ┌─────────────────────────────────────────────────────┐
  │  ScoreRepository                                    │
  │  • Wraps FakeScoreDataSource                        │
  │  • Applies .flowOn(Dispatchers.IO)                  │
  │  • Exposes clean Flow<List<Score>>                  │
  └─────────────────────────────────────────────────────┘

STEP 3 — ViewModel
  ┌─────────────────────────────────────────────────────┐
  │  ScoreViewModel                                     │
  │  • _allScores: MutableStateFlow<List<Score>>        │
  │  • _selectedSport: MutableStateFlow<SportCategory>  │
  │  • _events: MutableSharedFlow<ScoreEvent>           │
  │  • filteredScores = combine(sport, scores) { ... }  │
  │  • collectFlow() in init{} or startPolling()        │
  │  • Detect goal changes → emit to _events            │
  └─────────────────────────────────────────────────────┘

STEP 4 — UI (Compose)
  ┌─────────────────────────────────────────────────────┐
  │  ScoreListScreen                                    │
  │  • Collect filteredScores with collectAsState()     │
  │  • Collect events with LaunchedEffect + collect{}   │
  │  • SportFilter chip row → updates selectedSport     │
  │  • LazyColumn of ScoreCard items                    │
  │  • Highlight changed scores (compare to previous)   │
  └─────────────────────────────────────────────────────┘
```

### What "Highlight Score Changes" Means

```kotlin
// Keep track of previous scores to detect changes
@Composable
fun ScoreCard(score: Score, previousScore: Score?) {
    val isChanged = previousScore != null &&
                    (score.homeGoals != previousScore.homeGoals ||
                     score.awayGoals != previousScore.awayGoals)

    val backgroundColor = if (isChanged) Color.Yellow else Color.White
    // Animate the color change for a visual pulse effect
}
```

### Concepts Checklist

```
 Concept                  │ Where used in project
─────────────────────────────────────────────────────────────────
 suspend fun              │ fetchScore(), repository methods
 launch{}                 │ Score polling loop in ViewModel
 async{}                  │ Parallel fetches for multiple sports
 Dispatchers.IO           │ All network/data operations
 Dispatchers.Main         │ UI updates (via collect)
 Structured concurrency   │ viewModelScope ties all jobs to lifecycle
 StateFlow                │ Scores state, selected sport state
 SharedFlow               │ One-time events (GoalScored, etc.)
 filter{}                 │ Filter scores by sport
 map{}                    │ Score → ScoreDisplayModel
 debounce{}               │ Sport filter input throttle
 combine{}                │ Merge sport filter + score stream
 Channel                  │ Internal score update pipeline
```
