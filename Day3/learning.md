# Advanced Kotlin — Generics & Delegation Deep Dive
### Section 1.3 | Project: SmartCache Library

> **Goal:** Understand how Kotlin's type system becomes a *tool* for writing
> safer, more reusable code — and how delegation replaces boilerplate
> with clean, declarative property logic.

---

## Table of Contents

| # | Topic | Difficulty |
|---|-------|------------|
| 1 | [Generics — Cache\<K, V\>](#1-generics) | Intermediate |
| 2 | [Variance — in, out, invariance](#2-variance--in-out-and-invariance) | Advanced |
| 3 | [Reified Types — inline fun \<reified T\>](#3-reified-types) | Advanced |
| 4 | [Delegated Properties — by keyword](#4-delegated-properties) | Intermediate |
| 5 | [lazy{} — Deferred Initialization](#5-lazy--deferred-initialization) | Intermediate |
| 6 | [Observable Delegate — Watching Changes](#6-observable-delegate) | Intermediate |
| 7 | [Custom Delegates — CacheDelegate](#7-custom-delegates) | Advanced |
| 8 | [Operator Overloading — cache\["key"\]](#8-operator-overloading) | Intermediate |
| 9 | [Annotations — @CacheTTL](#9-annotations) | Intermediate |
| 10 | [Project — SmartCache Library](#10-project--smartcache-library) | Advanced |

---

## 1. Generics

### The Problem Without Generics

Without generics you'd need a separate cache class for every type, OR
use `Any` and cast everywhere — losing all type safety.

```
WITHOUT GENERICS — one class per type (not scalable):

  class StringCache {
      private val map = mutableMapOf<String, String>()
      fun put(key: String, value: String) { ... }
      fun get(key: String): String? { ... }
  }

  class UserCache {
      private val map = mutableMapOf<String, User>()
      fun put(key: String, value: User) { ... }
      fun get(key: String): User? { ... }
  }
  // Duplicated forever. Every new type = new class.

──────────────────────────────────────────────────────────────────────

WITHOUT GENERICS — using Any (type unsafe):

  class AnyCache {
      private val map = mutableMapOf<String, Any>()
      fun put(key: String, value: Any) { map[key] = value }
      fun get(key: String): Any? { return map[key] }
  }

  val cache = AnyCache()
  cache.put("user", User("Alice"))
  val user = cache.get("user") as User   // ← unsafe cast! Can throw ClassCastException
  val score = cache.get("user") as Score // ← compiles fine, crashes at runtime ❌
```

### Theory — What Are Generics?

Generics let you write a class or function *once* and parameterize it
with a type placeholder. The compiler fills in the real type at each
usage site, giving you full type safety without code duplication.

```
GENERIC CLASS ANATOMY:

  class Cache<K, V> {
         ↑  ↑
         │  └── Value type parameter (placeholder for the value type)
         └───── Key type parameter   (placeholder for the key type)

  Usage sites fill in real types:

  Cache<String, User>      →  K = String,  V = User
  Cache<Int, Score>        →  K = Int,     V = Score
  Cache<UUID, List<Match>> →  K = UUID,    V = List<Match>

  The COMPILER enforces these types. No casts needed. No runtime surprises.
```

### Type Parameters — Constraints

You can restrict what types are allowed using **upper bounds** with `:`.

```
WITHOUT BOUND — any type allowed:
  class Cache<K, V>
  Cache<String, User>    ✅
  Cache<String, Int>     ✅
  Cache<String, Any>     ✅

WITH BOUND — only Comparable keys allowed (enables sorting):
  class Cache<K : Comparable<K>, V>
  Cache<String, User>    ✅  (String implements Comparable)
  Cache<Int, Score>      ✅  (Int implements Comparable)
  Cache<Any, Score>      ❌  COMPILE ERROR (Any is not Comparable)

WITH MULTIPLE BOUNDS — key must be both Comparable and Serializable:
  class Cache<K, V> where K : Comparable<K>, K : Serializable
```

### Code — Generic Cache Skeleton

```kotlin
// ── Generic class with two type parameters ────────────────────────────
class Cache<K : Any, V : Any> {
    //        ↑         ↑
    //        │         └── V must be non-null (Any = not nullable)
    //        └── K must be non-null

    private val store = mutableMapOf<K, V>()

    fun put(key: K, value: V) {
        store[key] = value
    }

    fun get(key: K): V? {
        return store[key]
    }

    fun remove(key: K): V? {
        return store.remove(key)
    }

    fun containsKey(key: K): Boolean {
        return key in store
    }

    val size: Int get() = store.size
}

// ── Usage — compiler enforces types at each usage ─────────────────────
val userCache  = Cache<String, User>()
val scoreCache = Cache<String, Score>()

userCache.put("alice", User("Alice"))
userCache.put("alice", Score(...))   // ❌ COMPILE ERROR: Score is not User

val user: User? = userCache.get("alice")   // ✅ no cast needed, type is known

// ── Generic functions ─────────────────────────────────────────────────
fun <K : Any, V : Any> buildCache(vararg pairs: Pair<K, V>): Cache<K, V> {
    val cache = Cache<K, V>()
    pairs.forEach { (key, value) -> cache.put(key, value) }
    return cache
}

val c = buildCache("user1" to User("Alice"), "user2" to User("Bob"))
// Compiler infers: Cache<String, User>
```

### Generic Interfaces and Inheritance

```kotlin
// ── Define behaviour through a generic interface ───────────────────────
interface CacheStore<K : Any, V : Any> {
    fun put(key: K, value: V)
    fun get(key: K): V?
    fun evict(key: K)
    val size: Int
}

// ── Implement the interface with concrete types ────────────────────────
class InMemoryCache<K : Any, V : Any> : CacheStore<K, V> {
    private val store = LinkedHashMap<K, V>()
    override fun put(key: K, value: V) { store[key] = value }
    override fun get(key: K): V? = store[key]
    override fun evict(key: K) { store.remove(key) }
    override val size: Int get() = store.size
}

// ── Fix one type parameter, leave the other open ──────────────────────
class StringKeyCache<V : Any> : CacheStore<String, V> {
    // K is fixed to String, V is still generic
    private val store = mutableMapOf<String, V>()
    override fun put(key: String, value: V) { store[key] = value }
    override fun get(key: String): V? = store[key]
    override fun evict(key: String) { store.remove(key) }
    override val size: Int get() = store.size
}
```

---

## 2. Variance — in, out, and Invariance

### Why Variance Exists

You might expect that if `Dog` is a subtype of `Animal`, then
`Cache<Dog>` is a subtype of `Cache<Animal>`. But in Kotlin (and Java),
this is NOT true by default. Understanding why requires understanding variance.

```
THE CORE QUESTION:
  Dog    is a subtype of  Animal       ← true (inheritance)
  Is Cache<Dog> a subtype of Cache<Animal>?

  INVARIANT (default):   NO  — Cache<Dog> is not related to Cache<Animal>
  COVARIANT (out):       YES — Cache<Dog> can be used where Cache<Animal> expected
  CONTRAVARIANT (in):    REVERSE — Cache<Animal> can be used where Cache<Dog> expected
```

### Invariance (Default) — Why It Must Be

By default, Kotlin generics are invariant. This prevents a class of bugs:

```
INVARIANT — why it's the safe default:

  Imagine if Cache<Dog> were assignable to Cache<Animal>:

  val dogCache: Cache<Dog> = Cache()
  dogCache.put("rex", Dog())          // fine

  val animalCache: Cache<Animal> = dogCache  // hypothetically allowed

  animalCache.put("cat", Cat())       // Cat is an Animal, so allowed...
                                      // BUT dogCache now has a Cat in it!
                                      // Getting "cat" from dogCache returns Cat
                                      // typed as Dog → ClassCastException 💥

  Invariance prevents this. Cache<Dog> and Cache<Animal> are UNRELATED types.
```

### Covariance (`out`) — Producer Types

Use `out T` when a class only ever *produces* (returns) values of type T,
never *consumes* (accepts) them as input. Such a class is "read-only" with T.

```
COVARIANCE with 'out':

  interface Source<out T> {
      fun next(): T   // T only appears in OUTPUT position ✅
      // fun accept(item: T)  ← COMPILE ERROR: T can't be in input position
  }

  val dogs: Source<Dog> = DogFactory()
  val animals: Source<Animal> = dogs   // ✅ allowed because Source is covariant

  WHY IT'S SAFE:
  Source<Dog> only produces Dogs. Dogs ARE Animals.
  Reading from it as Source<Animal> is perfectly safe — every Dog is an Animal.
```

```kotlin
// ── Covariant interface — CacheReader ─────────────────────────────────
interface CacheReader<out V : Any> {
    fun get(key: String): V?       // V in OUTPUT position ✅
    val size: Int
    // fun put(key: String, value: V)  ← would be COMPILE ERROR
}

// ── Usage of covariance ────────────────────────────────────────────────
val userReader: CacheReader<User> = InMemoryCache()
val anyReader: CacheReader<Any> = userReader   // ✅ User is a subtype of Any

fun printCacheSize(reader: CacheReader<Any>) {
    println("Cache has ${reader.size} entries")
}
printCacheSize(userReader)   // ✅ works because CacheReader<User> is CacheReader<Any>
```

### Contravariance (`in`) — Consumer Types

Use `in T` when a class only ever *consumes* (accepts) values of type T.
It's like a "write-only" relationship.

```
CONTRAVARIANCE with 'in':

  interface Sink<in T> {
      fun accept(item: T)   // T only appears in INPUT position ✅
      // fun get(): T  ← COMPILE ERROR: T can't be in output position
  }

  val animalSink: Sink<Animal> = AnimalPrinter()
  val dogSink: Sink<Dog> = animalSink   // ✅ allowed: reversed direction

  WHY IT'S SAFE:
  Sink<Animal> accepts any Animal. Dogs are Animals.
  Using it as Sink<Dog> is safe — it can accept Dogs just fine.

  Variance direction is REVERSED from the type hierarchy:
  Dog IS-A Animal    →    Sink<Animal> IS-A Sink<Dog>
```

```kotlin
// ── Contravariant interface — CacheWriter ────────────────────────────
interface CacheWriter<in V : Any> {
    fun put(key: String, value: V)   // V in INPUT position ✅
    fun evict(key: String)
}

// ── Usage of contravariance ────────────────────────────────────────────
val anyWriter: CacheWriter<Any> = InMemoryCache()
val userWriter: CacheWriter<User> = anyWriter   // ✅ Animal sink can accept Dogs

fun saveUser(writer: CacheWriter<User>, user: User) {
    writer.put(user.id, user)
}
saveUser(anyWriter, User("Alice"))   // ✅ CacheWriter<Any> works where CacheWriter<User> needed
```

### Variance Summary

```
┌──────────────────────────────────────────────────────────────────────────┐
│  VARIANCE CHEAT SHEET                                                    │
├─────────────────┬────────────────────────────────────────────────────────┤
│  Invariant      │  Default. No subtyping relationship.                   │
│  Cache<K,V>     │  Cache<Dog> ≠ Cache<Animal>. Safest.                  │
│                 │  T appears in both in and out positions.               │
├─────────────────┼────────────────────────────────────────────────────────┤
│  Covariant      │  out T. Read-only producer.                           │
│  Source<out T>  │  Source<Dog> IS-A Source<Animal>                      │
│                 │  T only in output (return) positions.                 │
│                 │  Mnemonic: "out = can go OUT to a wider type"         │
├─────────────────┼────────────────────────────────────────────────────────┤
│  Contravariant  │  in T. Write-only consumer.                           │
│  Sink<in T>     │  Sink<Animal> IS-A Sink<Dog>                          │
│                 │  T only in input (parameter) positions.               │
│                 │  Mnemonic: "in = can go IN from a wider type"         │
└─────────────────┴────────────────────────────────────────────────────────┘

  The PECS rule (from Java):  Producer Extends, Consumer Super
  Kotlin equivalent:          Producer out,     Consumer in
```

---

## 3. Reified Types

### The Problem — Type Erasure

At runtime, generic type information is erased. The JVM only sees `Object`
where you wrote `T`. This makes it impossible to use `T` in is-checks or
when calling reflection APIs at runtime.

```
TYPE ERASURE — what the JVM sees:

  COMPILE TIME                      RUNTIME (JVM)
  ─────────────                     ─────────────
  Cache<String, User>          →    Cache (type params erased)
  Cache<String, Score>         →    Cache (same! no difference)
  List<User>                   →    List
  List<Score>                  →    List (same!)

  Consequence:
  fun <T> isInstance(value: Any): Boolean = value is T   // ❌ COMPILE ERROR
  // Cannot check against erased type parameter T at runtime

  fun <T> getFromCache(cache: Cache<*, *>, key: String): T? {
      return cache.get(key) as T?   // ← unchecked cast warning! T is unknown at runtime
  }
```

### Theory — Reified Types Fix Erasure (for inline functions)

When you mark a function `inline` and a type parameter `reified`, the compiler
*copies the function body* into every call site, substituting the actual type.
The real type is now available at runtime inside the function body.

```
REIFIED — how the compiler handles it:

  inline fun <reified T> Cache.getAs(key: String): T? {
      val value = this.get(key) ?: return null
      return if (value is T) value else null
      //              ↑ T is KNOWN at this point because the function is inlined
  }

  CALL SITE:
  val user = cache.getAs<User>("alice")

  WHAT THE COMPILER ACTUALLY GENERATES (at the call site):
  val user: User? = run {
      val value = cache.get("alice") ?: return@run null
      if (value is User) value as User else null
      //            ↑ compiler replaces T with User here
  }

  The function body is COPIED and T is replaced with User.
  No erasure problem because the type is baked in at the call site.
```

### Rules of Reified

```
┌──────────────────────────────────────────────────────────────────────────┐
│  REIFIED RULES                                                           │
│                                                                          │
│  ① The function MUST be marked inline                                   │
│     inline fun <reified T> ...                                           │
│     Without inline, the compiler cannot copy the body to the call site.  │
│                                                                          │
│  ② reified type params CAN be used in:                                  │
│     • is-checks:         value is T                                      │
│     • as-casts:          value as T                                      │
│     • reflection:        T::class, T::class.java                         │
│     • type-safe creation: gson.fromJson(json, T::class.java)             │
│                                                                          │
│  ③ reified type params CANNOT:                                           │
│     • Be used in non-inline lambda bodies that outlive the function      │
│     • Be called from Java code (inline functions are Kotlin-only)        │
│                                                                          │
│  ④ The function body is inlined — keep it small to avoid code bloat     │
└──────────────────────────────────────────────────────────────────────────┘
```

### Code Examples

```kotlin
// ── Basic reified — type-safe cache retrieval ─────────────────────────
class Cache<K : Any>(private val store: MutableMap<K, Any> = mutableMapOf()) {

    fun put(key: K, value: Any) { store[key] = value }
    fun getRaw(key: K): Any? = store[key]

    // reified lets us check the type at runtime
    inline fun <reified T> getAs(key: K): T? {
        val value = store[key] ?: return null
        return if (value is T) value else null
        //              ↑ is-check using the real type T
    }

    // Type mismatch returns null instead of throwing ClassCastException
    inline fun <reified T> getOrThrow(key: K): T {
        return getAs<T>(key) ?: throw CacheTypeMismatchException(
            "Expected ${T::class.simpleName} for key '$key'"
            //                 ↑ T::class.simpleName works! Runtime type info available.
        )
    }
}

// ── Usage ─────────────────────────────────────────────────────────────
val cache = Cache<String>()
cache.put("user", User("Alice"))
cache.put("score", Score(2, 1))

val user: User?  = cache.getAs<User>("user")    // ✅ returns User("Alice")
val score: Score? = cache.getAs<Score>("score") // ✅ returns Score(2, 1)
val wrong: Score? = cache.getAs<Score>("user")  // returns null (type mismatch)
val throws: User  = cache.getOrThrow<User>("score")  // throws CacheTypeMismatchException

// ── Reified + Class reflection ────────────────────────────────────────
inline fun <reified T : Any> Cache<String>.getOrLoad(
    key: String,
    loader: () -> T
): T {
    return getAs<T>(key) ?: run {
        val value = loader()
        put(key, value)
        println("Loaded and cached: ${T::class.simpleName}")
        value
    }
}

// ── Reified for JSON deserialization ──────────────────────────────────
inline fun <reified T> fromJson(json: String): T {
    return Gson().fromJson(json, T::class.java)
    //                          ↑ T::class.java is valid because T is reified
}

val user: User = fromJson("""{"name": "Alice"}""")   // no Class<T> parameter needed!

// ── Without reified — you'd have to pass the class manually ───────────
fun <T> fromJsonClassic(json: String, clazz: Class<T>): T {
    return Gson().fromJson(json, clazz)
}
val user2 = fromJsonClassic("""{"name": "Alice"}""", User::class.java)  // more verbose
```

---

## 4. Delegated Properties

### Theory — What is Delegation?

Normally, a property in Kotlin is backed by a field. Delegation lets you
replace that backing field with a **delegate object** that provides the
`getValue` and `setValue` logic.

```
NORMAL PROPERTY:
  var name: String = "Alice"
  ┌───────────────────────────────────────────────────────┐
  │  getter:  return field                                │
  │  setter:  field = value                               │
  └───────────────────────────────────────────────────────┘

DELEGATED PROPERTY:
  var name: String by SomeDelegate()
  ┌────────────────────────────────────────────────────────┐
  │  getter:  delegate.getValue(thisRef, property)         │
  │  setter:  delegate.setValue(thisRef, property, value)  │
  └────────────────────────────────────────────────────────┘

  The delegate OBJECT handles reading and writing.
  You can put any logic there — caching, logging, validation, expiry.
```

### The by Keyword

```
  class MyClass {
      var score: Int by ScoreDelegate()
      //               ↑
      //   'by' tells Kotlin: "use this object to back the property"
      //   ScoreDelegate must have getValue() and setValue() operators
  }

  READ:  myClass.score     → calls ScoreDelegate.getValue(myClass, ::score)
  WRITE: myClass.score = 5 → calls ScoreDelegate.setValue(myClass, ::score, 5)

  The property name and containing class are passed automatically.
  You can use this metadata for logging, error messages, etc.
```

### What a Delegate Must Implement

```kotlin
// ── A delegate must provide getValue (for val) or both (for var) ──────
import kotlin.reflect.KProperty

class MyDelegate<T>(private var value: T) {

    // Called when you READ the property
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        println("Getting '${property.name}' from ${thisRef?.javaClass?.simpleName}")
        return value
    }

    // Called when you WRITE the property (only needed for var)
    operator fun setValue(thisRef: Any?, property: KProperty<*>, newValue: T) {
        println("Setting '${property.name}' = $newValue")
        value = newValue
    }
}

// ── Using MyDelegate ──────────────────────────────────────────────────
class Config {
    var timeout: Int by MyDelegate(30)
    val maxRetries: Int by MyDelegate(3)
}

val config = Config()
println(config.timeout)     // prints: Getting 'timeout' from Config → 30
config.timeout = 60         // prints: Setting 'timeout' = 60
println(config.timeout)     // prints: Getting 'timeout' from Config → 60
```

---

## 5. lazy{} — Deferred Initialization

### Theory — Why Deferred Loading?

Some values are expensive to compute and not always needed. `lazy{}` defers
computation until the first access, then caches the result forever.

```
EAGER (computed at creation, wasteful if never used):
  class ScoreService {
      val heavyReport = loadAllHistoricalData()   // runs immediately on construction
      // Even if this object is created but report is never read, we paid the cost
  }

LAZY (computed on first access, cached forever after):
  class ScoreService {
      val heavyReport by lazy { loadAllHistoricalData() }
      // Nothing runs at construction time
      // First access to heavyReport → runs loadAllHistoricalData() once
      // All subsequent accesses → returns cached result instantly
  }
```

### How lazy{} Works Internally

```
  val result by lazy { expensiveComputation() }

  First access (result):
  ┌──────────────────────────────────────────────────────────────┐
  │  Lazy delegate checks: is value initialized?                 │
  │  NO → run the lambda: expensiveComputation()                 │
  │     → store result in internal field                         │
  │     → return result                                          │
  └──────────────────────────────────────────────────────────────┘

  All subsequent accesses (result):
  ┌──────────────────────────────────────────────────────────────┐
  │  Lazy delegate checks: is value initialized?                 │
  │  YES → return stored value immediately (no lambda runs)      │
  └──────────────────────────────────────────────────────────────┘

  STATE MACHINE:
  UNINITIALIZED ──(first access)──► INITIALIZING ──(lambda done)──► INITIALIZED
                                                                           │
                                                          all future reads ─┘
```

### LazyThreadSafetyMode

```
lazy{} has three threading modes:

  ┌──────────────────────┬──────────────────────────────────────────────────┐
  │  SYNCHRONIZED        │  Default. Only ONE thread initializes. Others    │
  │  (default)           │  wait. Safe but has a lock overhead.             │
  │                      │  val x by lazy { ... }  ← uses this             │
  ├──────────────────────┼──────────────────────────────────────────────────┤
  │  PUBLICATION         │  Multiple threads may initialize, but only the   │
  │                      │  first result is used. No lock, but may compute  │
  │                      │  multiple times. Safe for idempotent lambdas.    │
  ├──────────────────────┼──────────────────────────────────────────────────┤
  │  NONE                │  No synchronization. Fastest. Only use if you    │
  │                      │  KNOW single-threaded access only.               │
  └──────────────────────┴──────────────────────────────────────────────────┘
```

```kotlin
// ── lazy{} for expensive cache population ─────────────────────────────
class SmartCache<K : Any, V : Any>(private val loader: (K) -> V) {

    private val store = mutableMapOf<K, V>()

    // The entire populated dataset loaded lazily on first access
    val warmData: Map<K, V> by lazy {
        println("Loading warm data — this runs ONCE")
        loadInitialData()
    }

    private fun loadInitialData(): Map<K, V> {
        // imagine reading from a file or seeded data
        return emptyMap()
    }

    // Load-on-miss pattern using lazy-like logic
    fun getOrLoad(key: K): V {
        return store.getOrPut(key) { loader(key) }
        //                    ↑ only calls loader if key is missing
    }
}

// ── lazy{} in Android ─────────────────────────────────────────────────
class ScoreRepository(private val context: Context) {

    // Database is expensive to open — delay until first query
    private val database: ScoreDatabase by lazy {
        Room.databaseBuilder(context, ScoreDatabase::class.java, "scores.db")
            .build()
    }

    // SharedPreferences opened lazily
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("score_prefs", Context.MODE_PRIVATE)
    }

    suspend fun getScores(): List<Score> {
        return database.scoreDao().getAll()   // database opened HERE on first call
    }
}
```

---

## 6. Observable Delegate

### Theory — Watching Property Changes

`Delegates.observable()` is a built-in delegate that fires a callback
every time the property is written. Perfect for logging, syncing, or
triggering UI updates when a cached value changes.

```
  var score: Score by Delegates.observable(initialValue) { prop, old, new ->
      // This lambda runs AFTER every write to 'score'
      //   prop  = KProperty (the property metadata, e.g. name = "score")
      //   old   = the value BEFORE the change
      //   new   = the value AFTER the change
  }

  WRITE TIMELINE:
  score = Score(2, 1)
      │
      ▼
  Field is updated to Score(2, 1)
      │
      ▼
  Callback fires: (prop=score, old=Score(1,0), new=Score(2,1))
      │
      ▼
  Your code runs: log, notify UI, invalidate other caches, etc.
```

### observable vs vetoable

```
  observable — notified AFTER the change, cannot prevent it
  ┌────────────────────────────────────────────────────────────────┐
  │  old value → NEW VALUE WRITTEN → callback(prop, old, new)     │
  └────────────────────────────────────────────────────────────────┘

  vetoable — notified BEFORE the change, CAN prevent it (return false)
  ┌────────────────────────────────────────────────────────────────┐
  │  new value proposed → callback(prop, old, new) → return true? │
  │      if true:  value IS written                               │
  │      if false: value is NOT written (old value kept)          │
  └────────────────────────────────────────────────────────────────┘
```

```kotlin
import kotlin.properties.Delegates

// ── observable — log every cache entry change ──────────────────────────
class ObservableCache<K : Any, V : Any> {

    private val listeners = mutableListOf<(K, V?, V) -> Unit>()

    fun addChangeListener(listener: (key: K, old: V?, new: V) -> Unit) {
        listeners.add(listener)
    }

    private val store = mutableMapOf<K, V>()

    fun put(key: K, value: V) {
        val old = store[key]
        store[key] = value
        listeners.forEach { it(key, old, value) }
    }

    fun get(key: K): V? = store[key]
}

// ── observable delegate on a simple property ──────────────────────────
class CacheConfig {
    var maxSize: Int by Delegates.observable(100) { prop, old, new ->
        println("${prop.name} changed: $old → $new")
        if (new < old) println("WARNING: cache size reduced, entries may be evicted")
    }

    // vetoable — reject negative values
    var ttlSeconds: Long by Delegates.vetoable(60L) { prop, old, new ->
        val valid = new > 0
        if (!valid) println("Rejected invalid TTL: $new (keeping $old)")
        valid   // returning false keeps the old value
    }
}

val config = CacheConfig()
config.maxSize = 200    // prints: maxSize changed: 100 → 200
config.maxSize = 50     // prints: maxSize changed: 200 → 50 + WARNING
config.ttlSeconds = -1  // prints: Rejected invalid TTL: -1 (keeping 60)
println(config.ttlSeconds)  // still 60
```

---

## 7. Custom Delegates

### Theory — Building Your Own Delegate

Any class that provides the `getValue` (and optionally `setValue`) operator
functions can be used as a delegate. This is how you build domain-specific
property behaviour like TTL-expiry, access counting, or encrypted storage.

```
CUSTOM DELEGATE ANATOMY:

  class MyDelegate<T>(private val default: T) {

      private var storedValue: T = default
      private var lastSet: Long = 0

      operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
          // custom GET logic here
          return storedValue
      }

      operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
          // custom SET logic here
          storedValue = value
          lastSet = System.currentTimeMillis()
      }
  }

  Parameters:
  ┌──────────────────────────────────────────────────────────────────┐
  │  thisRef         The object containing the property              │
  │                  (null if the property is top-level)             │
  │                                                                  │
  │  property        KProperty — metadata about the property:        │
  │                  property.name  → "myScore", "username", etc.    │
  │                  property.returnType → the type                  │
  │                                                                  │
  │  value           The new value being assigned (setValue only)    │
  └──────────────────────────────────────────────────────────────────┘
```

### TTL-Expiring Cache Delegate

```kotlin
// ── CacheDelegate — property that auto-expires after a given TTL ───────
class CacheDelegate<T>(
    private val ttlMs: Long,             // time-to-live in milliseconds
    private val loader: () -> T          // how to reload an expired value
) {
    private var cachedValue: T? = null
    private var cachedAt: Long = 0L

    private val isExpired: Boolean
        get() = System.currentTimeMillis() - cachedAt > ttlMs

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        if (cachedValue == null || isExpired) {
            println("Cache MISS — reloading '${property.name}'")
            cachedValue = loader()
            cachedAt = System.currentTimeMillis()
        } else {
            println("Cache HIT — '${property.name}' (${remainingTtl()}ms remaining)")
        }
        return cachedValue!!
    }

    private fun remainingTtl(): Long = ttlMs - (System.currentTimeMillis() - cachedAt)
}

// ── Helper function for clean syntax ──────────────────────────────────
fun <T> cached(ttlMs: Long, loader: () -> T) = CacheDelegate(ttlMs, loader)

// ── Usage — properties that expire automatically ───────────────────────
class ScoreService {

    // Auto-reloads from network if older than 30 seconds
    val liveScores: List<Score> by cached(ttlMs = 30_000) {
        fetchScoresFromNetwork()
    }

    // Auto-reloads every 5 minutes
    val leaderboard: List<User> by cached(ttlMs = 5 * 60_000) {
        fetchLeaderboard()
    }

    // First access: loads from network, caches for 30s
    // Access at 35s: cache expired, reloads
    // Access at 40s: still fresh, returns cached value
}

// ── Read/write expiring delegate ───────────────────────────────────────
class ExpirableDelegate<T : Any>(
    private val ttlMs: Long,
    private val default: T
) {
    private var value: T = default
    private var setAt: Long = 0L

    private val isValid: Boolean
        get() = setAt > 0 && System.currentTimeMillis() - setAt <= ttlMs

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T =
        if (isValid) value else default

    operator fun setValue(thisRef: Any?, property: KProperty<*>, newValue: T) {
        value = newValue
        setAt = System.currentTimeMillis()
    }
}

class AuthCache {
    // Token is valid for 1 hour, then reads return empty string
    var authToken: String by ExpirableDelegate(ttlMs = 3_600_000L, default = "")

    // User session expires after 30 minutes of being set
    var currentUser: User? by ExpirableDelegate(ttlMs = 1_800_000L, default = null)
}

val auth = AuthCache()
auth.authToken = "Bearer xyz123"   // stored at T=0
// At T + 50min: auth.authToken = "Bearer xyz123"  ✅ still valid
// At T + 61min: auth.authToken = ""               ← expired, returns default
```

---

## 8. Operator Overloading

### Theory — Operators as Functions

Kotlin allows you to define what standard operators (`+`, `-`, `[]`, `in`, etc.)
mean for your custom classes. This makes your APIs read like natural language
instead of verbose method calls.

```
WITHOUT OPERATOR OVERLOADING:
  cache.put("user1", alice)
  val user = cache.get("user1")
  cache.remove("user1")
  val exists = cache.containsKey("user1")

WITH OPERATOR OVERLOADING:
  cache["user1"] = alice       // operator fun set(key, value)
  val user = cache["user1"]    // operator fun get(key)
  cache -= "user1"             // operator fun minusAssign(key)
  val exists = "user1" in cache  // operator fun contains(key)
```

### The Operator Map

```
┌──────────────────────────────────────────────────────────────────────────┐
│  OPERATOR          │  FUNCTION SIGNATURE                                 │
├──────────────────────────────────────────────────────────────────────────┤
│  a[i]              │  operator fun get(index: I): T                      │
│  a[i] = b          │  operator fun set(index: I, value: T)               │
│  a + b             │  operator fun plus(other: B): A                     │
│  a - b             │  operator fun minus(other: B): A                    │
│  a += b            │  operator fun plusAssign(other: B)                  │
│  a -= b            │  operator fun minusAssign(other: B)                 │
│  b in a            │  operator fun contains(element: B): Boolean         │
│  a == b            │  operator fun equals(other: Any?): Boolean          │
│  a > b, a < b etc  │  operator fun compareTo(other: A): Int              │
│  a()               │  operator fun invoke(): R                           │
│  a(x, y)           │  operator fun invoke(x: X, y: Y): R                 │
└──────────────────────────────────────────────────────────────────────────┘
```

### Code Examples

```kotlin
// ── Operator overloading on Cache ─────────────────────────────────────
class Cache<K : Any, V : Any> {
    private val store = mutableMapOf<K, V>()

    // cache["key"]
    operator fun get(key: K): V? = store[key]

    // cache["key"] = value
    operator fun set(key: K, value: V) {
        store[key] = value
    }

    // "key" in cache  (or  cache.contains("key"))
    operator fun contains(key: K): Boolean = key in store

    // cache += Pair("key", value)
    operator fun plusAssign(entry: Pair<K, V>) {
        store[entry.first] = entry.second
    }

    // cache -= "key"
    operator fun minusAssign(key: K) {
        store.remove(key)
    }

    // cache + otherCache  (merge, returns new cache)
    operator fun plus(other: Cache<K, V>): Cache<K, V> {
        val merged = Cache<K, V>()
        store.forEach { (k, v) -> merged[k] = v }
        other.store.forEach { (k, v) -> merged[k] = v }
        return merged
    }

    // cache()  — invoke as a function to get a snapshot
    operator fun invoke(): Map<K, V> = store.toMap()
}

// ── How it reads at the call site ─────────────────────────────────────
val userCache = Cache<String, User>()

userCache["alice"] = User("Alice", 30)   // set operator
userCache["bob"]   = User("Bob", 25)

val alice: User? = userCache["alice"]    // get operator
val exists = "alice" in userCache        // contains operator

userCache += "charlie" to User("Charlie", 22)   // plusAssign operator
userCache -= "bob"                              // minusAssign operator

val snapshot: Map<String, User> = userCache()   // invoke operator

val scoreCache = Cache<String, Score>()
val combined = userCache + userCache   // plus operator (Cache<String, User>)

// ── Operator on a CacheEntry wrapper ──────────────────────────────────
data class CacheEntry<V>(val value: V, val cachedAt: Long) {

    val age: Long get() = System.currentTimeMillis() - cachedAt

    // entry > 30_000  means "is this entry older than 30 seconds?"
    operator fun compareTo(thresholdMs: Long): Int = age.compareTo(thresholdMs)

    operator fun component1(): V = value
    operator fun component2(): Long = cachedAt
}

val entry = CacheEntry(User("Alice"), System.currentTimeMillis() - 5000)
if (entry > 3000) println("Entry is stale")    // compareTo: 5000 > 3000 ✅
val (user, time) = entry   // destructuring with component1/component2
```

---

## 9. Annotations

### Theory — What Are Annotations?

Annotations are **metadata** attached to code elements (classes, functions,
properties, parameters). They don't change behaviour themselves — they are
read by annotation processors, reflection code, or the Kotlin compiler.

```
ANNOTATION ANATOMY:

  @Target(AnnotationTarget.CLASS)     ← where this annotation can be placed
  @Retention(AnnotationRetention.RUNTIME)  ← when the annotation is visible
  annotation class CacheTTL(
      val seconds: Int = 60,          ← parameters (must be primitive/String/enum/class)
      val key: String = ""
  )

  TARGET options:
  ┌─────────────────────┬──────────────────────────────────────────────────┐
  │  CLASS              │  On classes, objects, interfaces                 │
  │  FUNCTION           │  On functions, constructors                      │
  │  PROPERTY           │  On properties                                   │
  │  FIELD              │  On backing fields                               │
  │  VALUE_PARAMETER    │  On function/constructor parameters              │
  │  LOCAL_VARIABLE     │  On local variables                              │
  │  ANNOTATION_CLASS   │  On other annotation classes                     │
  └─────────────────────┴──────────────────────────────────────────────────┘

  RETENTION options:
  ┌─────────────────────┬──────────────────────────────────────────────────┐
  │  SOURCE             │  Discarded after compilation (IDE hints only)    │
  │  BINARY             │  In .class file but not accessible via reflection│
  │  RUNTIME (default)  │  In .class file AND accessible via reflection    │
  └─────────────────────┴──────────────────────────────────────────────────┘
```

### Defining and Reading Annotations at Runtime

```kotlin
// ── Define the annotation ─────────────────────────────────────────────
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class CacheTTL(
    val seconds: Int = 60,
    val maxSize: Int = 1000,
    val strategy: EvictionStrategy = EvictionStrategy.LRU
)

enum class EvictionStrategy { LRU, LFU, FIFO, TTL_ONLY }

// ── Apply the annotation to data classes ─────────────────────────────
@CacheTTL(seconds = 30, maxSize = 500)
data class UserProfile(
    val id: String,
    val name: String,
    val score: Int
)

@CacheTTL(seconds = 300, strategy = EvictionStrategy.LFU)
data class LeaderboardEntry(
    val rank: Int,
    val userId: String,
    val points: Long
)

// No annotation — will use default TTL
data class TemporaryScore(val value: Int)

// ── Read annotations via reflection ──────────────────────────────────
inline fun <reified T : Any> getTtlSeconds(): Int {
    val annotation = T::class.annotations
        .filterIsInstance<CacheTTL>()
        .firstOrNull()
    return annotation?.seconds ?: 60   // default 60s if no annotation
}

inline fun <reified T : Any> buildCacheForType(): CacheConfig {
    val annotation = T::class.annotations
        .filterIsInstance<CacheTTL>()
        .firstOrNull()

    return CacheConfig(
        ttlMs   = (annotation?.seconds ?: 60) * 1000L,
        maxSize = annotation?.maxSize ?: 1000,
        strategy = annotation?.strategy ?: EvictionStrategy.LRU
    )
}

// ── Usage ─────────────────────────────────────────────────────────────
val userTtl = getTtlSeconds<UserProfile>()          // 30
val lbTtl   = getTtlSeconds<LeaderboardEntry>()     // 300
val tempTtl = getTtlSeconds<TemporaryScore>()       // 60 (default)

val userCacheConfig   = buildCacheForType<UserProfile>()
// → CacheConfig(ttlMs=30_000, maxSize=500, strategy=LRU)

val lbCacheConfig     = buildCacheForType<LeaderboardEntry>()
// → CacheConfig(ttlMs=300_000, maxSize=1000, strategy=LFU)
```

### Built-in Kotlin Annotations You'll Use Often

```kotlin
// @JvmStatic   — makes companion object function callable as static from Java
class Cache {
    companion object {
        @JvmStatic fun create(): Cache = Cache()
    }
}

// @JvmField    — exposes a property as a public field (no getter/setter)
class Config {
    @JvmField val maxSize: Int = 100
}

// @Suppress    — suppress compiler warnings
@Suppress("UNCHECKED_CAST")
fun <T> unsafeCast(value: Any): T = value as T

// @Deprecated  — mark API as obsolete, guide users to replacement
@Deprecated(
    message = "Use getAs<T>() instead",
    replaceWith = ReplaceWith("getAs<T>(key)"),
    level = DeprecationLevel.ERROR
)
fun getUnsafe(key: String): Any? = store[key]

// @SerializedName (Gson) — control JSON key names
data class Score(
    @SerializedName("home_goals") val homeGoals: Int,
    @SerializedName("away_goals") val awayGoals: Int
)
```

---

## 10. Project — SmartCache Library

### What You're Building

A reusable Kotlin module (not an Android app — a pure Kotlin library)
that others can depend on. It provides a generic, configurable, observable
in-memory cache with TTL support, lazy loading, and operator-friendly API.

```
┌──────────────────────────────────────────────────────────────────────────┐
│                        SMARTCACHE LIBRARY                                │
│                                                                          │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │  Public API (what library users see)                               │  │
│  │                                                                    │  │
│  │  @CacheTTL(seconds = 30)        ← annotate your data class        │  │
│  │  data class UserProfile(...)                                       │  │
│  │                                                                    │  │
│  │  val cache = SmartCache.forType<UserProfile>()                     │  │
│  │  ← uses annotation to configure TTL and maxSize automatically     │  │
│  │                                                                    │  │
│  │  cache["alice"] = UserProfile(...)   ← operator overloading       │  │
│  │  val user = cache["alice"]           ← auto-expiry via delegate   │  │
│  │  val loaded = cache.getOrLoad("alice") { fetchFromDb("alice") }   │  │
│  │  ← lazy-loads and caches if missing                               │  │
│  │                                                                    │  │
│  │  cache.onEvict { key, value ->                                     │  │
│  │      println("Evicted: $key")        ← observable invalidation    │  │
│  │  }                                                                 │  │
│  └────────────────────────────────────────────────────────────────────┘  │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

### Architecture

```
SmartCache Library — Module Structure:

  smartcache/
  ├── annotations/
  │   └── CacheTTL.kt          ← @CacheTTL annotation definition
  │
  ├── core/
  │   ├── CacheStore.kt        ← Generic interface: CacheStore<K, V>
  │   ├── CacheEntry.kt        ← Data class wrapping value + timestamp
  │   └── CacheConfig.kt       ← Configuration: TTL, maxSize, strategy
  │
  ├── delegates/
  │   ├── CacheDelegate.kt     ← Custom delegate: auto-expiry property
  │   └── ObservableCache.kt   ← Wraps store, fires callbacks on change
  │
  ├── impl/
  │   └── SmartCache.kt        ← Main implementation: Cache<K,V>
  │
  └── extensions/
      └── SmartCacheExt.kt     ← Extension functions: getOrLoad, forType<T>

  ─────────────────────────────────────────────────────────────────────
  HOW CONCEPTS MAP TO FILES:

  Generics          → CacheStore<K,V>, SmartCache<K,V>, CacheEntry<V>
  Variance          → CacheReader<out V>, CacheWriter<in V> in CacheStore.kt
  Reified types     → SmartCacheExt.kt: inline fun <reified T> getOrLoad()
                      SmartCacheExt.kt: fun <reified T> SmartCache.forType()
  lazy{}            → SmartCache.kt: lazy-loaded default values
  Observable        → ObservableCache.kt: Delegates.observable on internal map
  Custom delegate   → CacheDelegate.kt: TTL-aware property delegate
  Operator overload → SmartCache.kt: get/set/contains/plusAssign/minusAssign
  Annotations       → CacheTTL.kt + SmartCacheExt.kt: read annotation on T
```

### Core Data Types

```kotlin
// ── CacheTTL.kt ────────────────────────────────────────────────────────
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class CacheTTL(
    val seconds: Int = 60,
    val maxSize: Int = 1000,
    val strategy: EvictionStrategy = EvictionStrategy.LRU
)

enum class EvictionStrategy { LRU, LFU, FIFO }

// ── CacheEntry.kt ─────────────────────────────────────────────────────
data class CacheEntry<V : Any>(
    val value: V,
    val expiresAt: Long,
    val accessCount: Int = 0
) {
    val isExpired: Boolean get() = System.currentTimeMillis() > expiresAt
    val isAlive:   Boolean get() = !isExpired

    operator fun component1(): V    = value
    operator fun component2(): Long = expiresAt
}

// ── CacheConfig.kt ────────────────────────────────────────────────────
data class CacheConfig(
    val ttlMs: Long = 60_000L,
    val maxSize: Int = 1000,
    val strategy: EvictionStrategy = EvictionStrategy.LRU,
    val onEvict: ((key: Any, value: Any) -> Unit)? = null
)

// ── CacheStore.kt — generic interface with variance ───────────────────
interface CacheStore<K : Any, V : Any> {
    fun put(key: K, value: V)
    fun get(key: K): V?
    fun evict(key: K): V?
    fun clear()
    val size: Int
    val keys: Set<K>
}

// Split into read-only and write-only views:
interface CacheReader<out V : Any> {
    fun get(key: String): V?
    val size: Int
}

interface CacheWriter<in V : Any> {
    fun put(key: String, value: V)
    fun evict(key: String)
}
```

### Feature Breakdown — What to Build, Step by Step

```
STEP 1 — Annotation (@CacheTTL)
────────────────────────────────
  Goal: Allow data classes to declare their own cache policy.

  @CacheTTL(seconds = 30)
  data class UserProfile(val id: String, val name: String)

  Things to define:
  • The annotation class with parameters: seconds, maxSize, strategy
  • @Target, @Retention on the annotation
  • EvictionStrategy enum

  Key question: Why RUNTIME retention?
  → So reflection can read it when building the cache.

──────────────────────────────────────────────────────────────────────
STEP 2 — CacheEntry (Generic Data Holder + Operator Overloading)
─────────────────────────────────────────────────────────────────
  Goal: Wrap a cached value with its expiry timestamp.

  data class CacheEntry<V : Any>(val value: V, val expiresAt: Long)

  Things to add:
  • isExpired computed property
  • component1()/component2() for destructuring
  • compareTo(Long) operator for "entry > thresholdMs" syntax

──────────────────────────────────────────────────────────────────────
STEP 3 — SmartCache (Core Generic Class + Operator Overloading)
────────────────────────────────────────────────────────────────
  Goal: The main cache class users interact with.

  class SmartCache<K : Any, V : Any>(private val config: CacheConfig)

  Things to add:
  • Internal store: MutableMap<K, CacheEntry<V>>
  • get(key): V?  — check isExpired before returning
  • set(key, value): Unit  — wrap in CacheEntry with TTL
  • contains(key): Boolean
  • minusAssign(key): Unit — evict
  • plusAssign(Pair<K,V>): Unit
  • Auto-eviction on put when size > maxSize (based on strategy)
  • onEvict callback firing via observable delegate or manual call

  Key design: store CacheEntry, not raw V. That's how TTL works.

──────────────────────────────────────────────────────────────────────
STEP 4 — CacheDelegate (Custom Property Delegate)
──────────────────────────────────────────────────
  Goal: A property delegate that acts as a self-expiring single value.

  var score: Score by CacheDelegate(ttlMs = 30_000) { fetchScore() }

  When you READ the property:
  • If no value, or expired → run the loader, store, return
  • If valid → return cached value

  When you WRITE the property:
  • Store the new value with a fresh timestamp

  Things to implement:
  • getValue operator
  • setValue operator (for read/write variant)
  • Internal: cachedValue, cachedAt, isExpired logic

──────────────────────────────────────────────────────────────────────
STEP 5 — ObservableCache (Wraps SmartCache with Change Notifications)
───────────────────────────────────────────────────────────────────────
  Goal: Notify external listeners when entries are added/evicted.

  cache.onEvict { key, value ->
      println("$key was evicted")
  }
  cache.onChange { key, old, new ->
      auditLog.record(key, old, new)
  }

  Implementation options:
  • Wrap SmartCache and add listener lists
  • OR use Delegates.observable inside the store map wrapper

──────────────────────────────────────────────────────────────────────
STEP 6 — Extension Functions (Reified Type Magic)
───────────────────────────────────────────────────
  Goal: Ergonomic, type-safe API using reified generics.

  // Build a cache pre-configured from the data class annotation
  inline fun <reified T : Any> SmartCache.Companion.forType(): SmartCache<String, T> {
      val config = buildCacheForType<T>()  // reads @CacheTTL annotation
      return SmartCache(config)
  }

  // Get or load — type-safe, no casts
  inline fun <reified V : Any, K : Any> SmartCache<K, V>.getOrLoad(
      key: K,
      loader: () -> V
  ): V {
      return get(key) ?: loader().also { put(key, it) }
  }

  // Type-safe get with runtime type checking (no ClassCastException)
  inline fun <reified T : Any, K : Any> SmartCache<K, *>.getAs(key: K): T? {
      val raw = get(key) ?: return null
      return if (raw is T) raw else null
  }
```

### How Every Concept Appears in the Project

```
┌──────────────────────┬────────────────────────────────────────────────────┐
│  Concept             │  Where in SmartCache                               │
├──────────────────────┼────────────────────────────────────────────────────┤
│  Generics            │  Cache<K,V>, CacheEntry<V>, CacheStore<K,V>        │
│                      │  Type-safe keys and values throughout               │
├──────────────────────┼────────────────────────────────────────────────────┤
│  Variance (out)      │  CacheReader<out V> — read-only view of cache      │
│  Variance (in)       │  CacheWriter<in V>  — write-only view of cache     │
├──────────────────────┼────────────────────────────────────────────────────┤
│  Reified types       │  forType<UserProfile>() reads the @CacheTTL        │
│                      │  annotation on UserProfile without passing Class<T> │
│                      │  getAs<T>() — type-safe runtime is-check           │
├──────────────────────┼────────────────────────────────────────────────────┤
│  lazy{}              │  Deferred loading of default warm data              │
│                      │  Lazy initialization of expensive resources          │
├──────────────────────┼────────────────────────────────────────────────────┤
│  Observable delegate │  ObservableCache fires onChange, onEvict callbacks  │
│                      │  Delegates.vetoable to reject invalid TTL values    │
├──────────────────────┼────────────────────────────────────────────────────┤
│  Custom delegate     │  CacheDelegate — var score by CacheDelegate(30_000)│
│                      │  Property auto-reloads when TTL expires             │
├──────────────────────┼────────────────────────────────────────────────────┤
│  Operator overload   │  cache["key"] = value  (set)                       │
│                      │  val x = cache["key"]  (get)                       │
│                      │  "key" in cache         (contains)                  │
│                      │  cache -= "key"         (minusAssign = evict)      │
│                      │  cache += "key" to val  (plusAssign)               │
│                      │  cache()                (invoke = snapshot)        │
├──────────────────────┼────────────────────────────────────────────────────┤
│  Annotations         │  @CacheTTL(seconds=30) on data class               │
│                      │  Read via T::class.annotations at runtime           │
│                      │  forType<T>() configures cache from annotation      │
└──────────────────────┴────────────────────────────────────────────────────┘
```

### The Full Intended API (What Users of Your Library Write)

```kotlin
// 1. Annotate your data class with its caching policy
@CacheTTL(seconds = 30, maxSize = 500)
data class UserProfile(val id: String, val name: String, val score: Int)

// 2. Create a cache — annotation is read automatically
val userCache = SmartCache.forType<UserProfile>()
// ← builds SmartCache<String, UserProfile> with TTL=30s, maxSize=500

// 3. Use operator syntax
userCache["alice"] = UserProfile("alice", "Alice", 1500)
userCache["bob"]   = UserProfile("bob", "Bob", 1200)

val alice = userCache["alice"]          // UserProfile? (null if expired or missing)
val exists = "alice" in userCache       // true

// 4. Load-on-miss
val charlie = userCache.getOrLoad("charlie") {
    fetchUserFromDatabase("charlie")    // only called if not cached
}

// 5. Observe evictions
userCache.onEvict { key, value ->
    println("Evicted: $key (score was ${value.score})")
}

// 6. Use CacheDelegate on a property
class LeaderboardViewModel {
    // Automatically refreshes from DB every 5 minutes
    val topPlayers: List<UserProfile> by cached(ttlMs = 5 * 60_000) {
        fetchTopPlayersFromDatabase()
    }
}

// 7. Type-safe multi-type cache
val mixedCache = SmartCache<String, Any>(CacheConfig(ttlMs = 60_000))
mixedCache["user"]  = UserProfile("x", "X", 100)
mixedCache["score"] = Score(2, 1)

val user:  UserProfile? = mixedCache.getAs<UserProfile>("user")
val score: Score?        = mixedCache.getAs<Score>("score")
val wrong: Score?        = mixedCache.getAs<Score>("user")   // null, type mismatch
```

---

## Quick Reference Card

```
┌─────────────────────────────────────────────────────────────────────────┐
│  GENERICS & DELEGATION — CHEAT SHEET                                    │
├──────────────────────────────┬──────────────────────────────────────────┤
│  Generic class               │  class Foo<T>                            │
│  Constrained type param      │  class Foo<T : Comparable<T>>            │
│  Multiple bounds             │  where T : A, T : B                      │
│  Generic function            │  fun <T> process(t: T): T                │
├──────────────────────────────┼──────────────────────────────────────────┤
│  Invariant (default)         │  class Box<T>     ← no subtyping         │
│  Covariant (producer/out)    │  interface Src<out T>  ← T in output only│
│  Contravariant (consumer/in) │  interface Snk<in T>   ← T in input only │
├──────────────────────────────┼──────────────────────────────────────────┤
│  Reified type param          │  inline fun <reified T> foo()            │
│  is-check with reified       │  if (value is T)                         │
│  Class from reified          │  T::class  /  T::class.java              │
│  Type-safe JSON parse        │  Gson().fromJson(json, T::class.java)    │
├──────────────────────────────┼──────────────────────────────────────────┤
│  Delegated property          │  var x: T by SomeDelegate()              │
│  Lazy initialization         │  val x by lazy { expensiveOp() }         │
│  Observable changes          │  var x by Delegates.observable(v) { ... }│
│  Vetoable (reject on false)  │  var x by Delegates.vetoable(v) { ... }  │
│  Custom delegate             │  class D { operator fun getValue() ... } │
├──────────────────────────────┼──────────────────────────────────────────┤
│  Array-style get             │  operator fun get(key: K): V             │
│  Array-style set             │  operator fun set(key: K, value: V)      │
│  in operator                 │  operator fun contains(key: K): Boolean  │
│  -= operator                 │  operator fun minusAssign(key: K)        │
│  += operator                 │  operator fun plusAssign(pair: Pair<K,V>)│
│  () invoke                   │  operator fun invoke(): R                 │
├──────────────────────────────┼──────────────────────────────────────────┤
│  Annotation definition       │  annotation class Foo(val x: Int)        │
│  Target                      │  @Target(AnnotationTarget.CLASS)         │
│  Runtime retention           │  @Retention(AnnotationRetention.RUNTIME) │
│  Read at runtime             │  T::class.annotations.filterIsInstance<> │
└──────────────────────────────┴──────────────────────────────────────────┘
```

---

*Next:* Day 4 — Android Architecture Components (ViewModel, LiveData, Room)
