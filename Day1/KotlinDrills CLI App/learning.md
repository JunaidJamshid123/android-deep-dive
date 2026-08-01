# Kotlin Fundamentals — Deep Dive Learning Guide

> **Goal:** Understand every concept used in the KotlinDrills CLI App — not just the syntax,
> but *why* it exists, *when* to use it, and *what mistakes to avoid*.

---

## Table of Contents

| # | Topic | Difficulty |
|---|-------|-----------|
| 1 | [Variables — `val`, `var`, Data Types](#1-variables--val-var-and-data-types) | Beginner |
| 2 | [Control Flow — if/else, when, for, while](#2-control-flow--ifelse-when-for-while) | Beginner |
| 3 | [Functions — Parameters & Return Types](#3-functions--parameters--return-types) | Beginner |
| 4 | [Higher-Order Functions & Lambdas](#4-higher-order-functions--lambdas) | Intermediate |
| 5 | [Data Classes](#5-data-classes) | Intermediate |
| 6 | [Null Safety](#6-null-safety) | Intermediate |
| 7 | [Collections — List, groupBy, sortBy](#7-collections--list-groupby-sortby) | Intermediate |
| 8 | [Scope Functions — apply, let, also, run, with](#8-scope-functions) | Intermediate |
| 9 | [Extension Functions](#9-extension-functions) | Intermediate |
| 10 | [Complete Worked Example](#10-complete-worked-example) | Advanced |

---

## 1. Variables — `val`, `var`, and Data Types

### Why This Matters

Every program stores data. Kotlin forces you to decide upfront whether that data
can change. This eliminates an entire class of bugs where something you expected
to stay constant gets accidentally modified.

### Theory

```
+-----------+----------------------------------------------------------+
|   val     |  Immutable (read-only). Assigned once, never again.     |
|           |  Equivalent to "final" in Java.                         |
|           |  The COMPILER enforces this — zero runtime cost.        |
+-----------+----------------------------------------------------------+
|   var     |  Mutable. Can be reassigned at any time.                |
|           |  Use only when reassignment is genuinely needed.        |
+-----------+----------------------------------------------------------+

  Golden Rule:  val  first,  var  only if you must.
```

### Type System

```
+--------------+-----------------+--------------+-------------------+
|  Type        |  Example        |  Size        |  Notes            |
+--------------+-----------------+--------------+-------------------+
|  Int         |  42             |  32-bit      |  Most common int  |
|  Long        |  1_000_000L     |  64-bit      |  Use for large #s |
|  Double      |  9.81           |  64-bit      |  Default decimal  |
|  Float       |  9.81f          |  32-bit      |  Less precise     |
|  Boolean     |  true / false   |  —           |  Logic flags      |
|  Char        |  'A'            |  16-bit      |  Single character |
|  String      |  "Hello"        |  —           |  Immutable text   |
|  Any         |  anything       |  —           |  Root of all types|
|  Unit        |  (nothing)      |  —           |  Like void        |
|  Nothing     |  (never returns)|  —           |  throw, TODO()    |
+--------------+-----------------+--------------+-------------------+
```

### Type Inference

```
  Explicit:    val score: Int = 100
                           ^^^
                    you write the type

  Inferred:    val score = 100
               compiler sees 100 -> knows it''s Int
               you write less, compiler does the work
```

### Code Example

```kotlin
// val — immutable
val appName: String = "KotlinDrills"   // explicit type
val maxScore = 100                     // inferred as Int
val passingRate = 0.75                 // inferred as Double
val isDarkMode = false                 // inferred as Boolean

// val appName = "Other"  // ERROR: Val cannot be reassigned

// var — mutable
var currentScore = 0
var playerName = "Junaid"
currentScore = 42     // OK
playerName   = "Ahmed" // OK

// Numeric literals — underscores improve readability
val population  = 1_000_000
val fileSize    = 4_294_967_295L
val pixelRatio  = 1.75f
val gravity     = 9.80665

// String Templates
val score = 87
println("Score: $score")
println("Result: ${if (score > 50) "Pass" else "Fail"}")
println("App: ${appName.uppercase()}")

// Multi-line strings
val helpText = """
    |Commands:
    |  play  — start a quiz session
    |  stats — view your scores
    |  quit  — exit the app
""".trimMargin()
```

### Common Mistakes

```
WRONG:  var pi = 3.14159        // should be val — pi never changes
WRONG:  val count = 0; count++  // count++ needs var
WRONG:  val x: Int = 3.14       // type mismatch — Double ≠ Int

RIGHT:  val pi = 3.14159
RIGHT:  var count = 0
RIGHT:  val x: Double = 3.14
```

---

## 2. Control Flow — if/else, when, for, while

### Why This Matters

Kotlin''s control flow is more powerful than Java''s — `if` and `when` are
**expressions** that return values, eliminating the need for ternary operators
or verbose switch statements.

### Theory

```
+------------+---------------------------------------------------------+
|  if/else   |  Standard branching. ALSO an expression.               |
|  when      |  Supercharged switch. ALSO an expression.               |
|  for       |  Iterate ranges, lists, maps.                           |
|  while     |  Loop while condition is true.                          |
|  do-while  |  Run once, then loop while condition is true.           |
+------------+---------------------------------------------------------+

  Statement vs Expression — the key Kotlin concept:

  Java (statement):              Kotlin (expression):
  String r;                      val r = if (x > 0) "pos"
  if (x > 0) { r = "pos"; }                else      "neg"
  else        { r = "neg"; }
  // temp variable + risk of     // one line, no uninitialized
  // forgetting to assign         // variable risk
```

### if / else

```kotlin
val score = 78

// Statement
if (score >= 50) println("Pass") else println("Fail")

// Expression — assign result directly
val result = if (score >= 50) "Pass" else "Fail"

// Multi-branch expression
val grade = if      (score >= 90) "A"
            else if (score >= 75) "B"
            else if (score >= 60) "C"
            else                  "F"

// Inside string template
println("Grade: ${if (score >= 50) "✅ Pass" else "❌ Fail"}")
```

### when

```kotlin
val difficulty = "hard"

// Match a value
when (difficulty) {
    "easy"           -> println("🟢 Beginner level")
    "medium"         -> println("🟡 Intermediate level")
    "hard", "expert" -> println("🔴 Advanced level")   // multiple values
    else             -> println("Unknown")
}

// As expression
val points = when (difficulty) {
    "easy"   -> 1
    "medium" -> 3
    "hard"   -> 5
    else     -> 0
}

// Without argument — cleaner if-else chain
when {
    score >= 90 -> println("Excellent!")
    score >= 70 -> println("Good job!")
    score >= 50 -> println("Keep trying!")
    else        -> println("Needs more practice")
}

// Type checking with smart cast
fun describe(obj: Any): String = when (obj) {
    is Int    -> "Integer: $obj"
    is String -> "String of length ${obj.length}"  // smart cast!
    else      -> "Unknown"
}

// Range checking
val medal = when (score) {
    in 90..100 -> "Gold"
    in 75..89  -> "Silver"
    in 60..74  -> "Bronze"
    else       -> "No medal"
}
```

### for

```kotlin
for (i in 1..5)         print("$i ")  // 1 2 3 4 5
for (i in 1 until 5)    print("$i ")  // 1 2 3 4  (excludes 5)
for (i in 5 downTo 1)   print("$i ")  // 5 4 3 2 1
for (i in 0..10 step 2) print("$i ")  // 0 2 4 6 8 10

val topics = listOf("Variables", "Functions", "Classes")
for (topic in topics) println("• $topic")

// With index
for ((index, topic) in topics.withIndex()) {
    println("${index + 1}. $topic")
}

// Map destructuring
val scoreMap = mapOf("Junaid" to 90, "Ahmed" to 75)
for ((name, s) in scoreMap) println("$name scored $s")
```

### while / do-while

```kotlin
var lives = 3
while (lives > 0) {
    println("Lives: $lives")
    lives--
}

// do-while — runs at least once
var answer: String
do {
    print("Enter answer: ")
    answer = readLine() ?: ""
} while (answer.isBlank())

// break and continue
for (i in 1..10) {
    if (i == 5) continue   // skip 5
    if (i == 8) break      // stop at 8
    print("$i ")           // 1 2 3 4 6 7
}
```

---

## 3. Functions — Parameters & Return Types

### Why This Matters

Functions are the building blocks of any program. Default parameters eliminate
overloads, named arguments make calls self-documenting, and single-expression
syntax removes ceremony.

### Theory

```
  fun greetPlayer ( name: String,  level: Int = 1 ): String
   |      |          |                 |              |
   |      |          |                 |              +-- return type
   |      |          |                 +-- default value
   |      |          +-- parameter: Type
   |      +-- function name
   +-- keyword

  Single-expression shorthand:
  fun double(x: Int): Int = x * 2   (no braces, no return keyword)

  Call styles:
    greetPlayer("Junaid")                      uses default level=1
    greetPlayer("Junaid", 5)                   positional
    greetPlayer(name="Junaid", level=5)        named args
    greetPlayer(level=5, name="Junaid")        order doesn''t matter
```

### Code Example

```kotlin
// Basic
fun add(a: Int, b: Int): Int { return a + b }

// Single-expression
fun multiply(a: Int, b: Int): Int = a * b
fun isPass(score: Int): Boolean = score >= 50

// Default parameters
fun greetPlayer(name: String, level: Int = 1, verbose: Boolean = false): String {
    val info = if (verbose) " (Level $level)" else ""
    return "Welcome, $name$info!"
}

greetPlayer("Junaid")
greetPlayer("Junaid", verbose = true)
greetPlayer("Junaid", level = 3, verbose = true)

// Unit — no return value
fun printDivider(char: Char = '-', width: Int = 40) {
    println(char.toString().repeat(width))
}

// Vararg
fun sumAll(vararg numbers: Int): Int = numbers.sum()
println(sumAll(1, 2, 3, 4, 5))  // 15

// Spread operator for arrays
val values = intArrayOf(10, 20, 30)
println(sumAll(*values))  // 60

// Infix function — reads like English
infix fun String.matchesAnswer(answer: String): Boolean =
    trim().lowercase() == answer.trim().lowercase()

"Immutable variable" matchesAnswer "immutable variable"  // true
```

### Common Mistakes

```
WRONG:  fun add(a: Int, b: Int): Int = { a + b }  // lambda not needed with =
RIGHT:  fun add(a: Int, b: Int): Int = a + b

WRONG:  greetPlayer(level = 3, "Junaid")  // positional after named arg
RIGHT:  greetPlayer("Junaid", level = 3)
```

---

## 4. Higher-Order Functions & Lambdas

### Why This Matters

Instead of writing separate loops for filtering, transforming, and searching,
you pass the *logic* as a parameter. This is the foundation of Kotlin''s entire
collection API and the most important concept in modern Kotlin.

### Theory

```
  A Higher-Order Function:
    • takes another function as a parameter, OR
    • returns a function

  Lambda = anonymous function written inline
  Syntax:   { parameters -> body }
  Type:     (ParamType) -> ReturnType

  Lambda Anatomy:
    cards.filter { it.score > 5 }
                    |      |   |
                   "it"    |   +-- return value (expression)
                           +-- property access
    "it" = implicit name when there''s exactly ONE parameter
    Two params: { a, b -> a.score > b.score }

  Trailing Lambda Rule:
    filter({ it > 5 })   ==   filter { it > 5 }
    If the LAST parameter is a function, move it outside the parens.
```

### Code Example

```kotlin
// Lambda in a variable
val square: (Int) -> Int     = { n -> n * n }
val isEven: (Int) -> Boolean = { it % 2 == 0 }
val greet:  (String) -> Unit = { println("Hello, $it!") }

// Your own HOF
fun applyTwice(x: Int, op: (Int) -> Int): Int = op(op(x))
println(applyTwice(3) { it * 2 })  // 12  (3*2=6, 6*2=12)

// Function references
fun isPositive(n: Int): Boolean = n > 0
val nums = listOf(-3, 0, 5, 2, -1)
val positives = nums.filter(::isPositive)  // [5, 2]

// ── Standard Library HOFs ────────────────────────────────
val scores = listOf(3, 7, 1, 9, 4, 6, 2, 8)

val high      = scores.filter    { it > 5 }           // [7, 9, 6, 8]
val doubled   = scores.map       { it * 2 }           // [6,14,2,18,8,12,4,16]
val firstHigh = scores.find      { it > 8 }           // 9
val hasNine   = scores.any       { it == 9 }          // true
val allPos    = scores.all       { it > 0 }           // true
val highCount = scores.count     { it > 5 }           // 4
val total     = scores.reduce    { acc, v -> acc + v } // 40
val from100   = scores.fold(100) { acc, v -> acc + v } // 140

// partition — two lists at once
val (above5, rest) = scores.partition { it > 5 }

// Chaining
val top3 = scores
    .filter  { it > 3 }
    .map     { it * 10 }
    .sortedDescending()
    .take(3)
println(top3)   // [90, 80, 70]

// Flashcard usage
val cards = listOf(
    Flashcard("What is val?",      "Immutable variable", "easy",   score = 3),
    Flashcard("What is a lambda?", "Anonymous function", "hard",   score = 8),
    Flashcard("What is when?",     "Pattern matching",   "medium", score = 6),
    Flashcard("Null safety op?",   "?. ?: !!",           "hard",   score = 7),
    Flashcard("What is let?",      "Scope function",     "medium", score = 5)
)

val hardAbove6 = cards.filter { it.difficulty == "hard" && it.score > 6 }
val questions  = cards.map { it.question }
val topCard    = cards.maxByOrNull { it.score }
val avg        = cards.map { it.score }.average()

println("Top: ${topCard?.question}")  // What is a lambda?
println("Avg: $avg")                  // 5.8
```

---

## 5. Data Classes

### Why This Matters

Every app models data — users, messages, cards, products. Without data classes
you write `equals()`, `hashCode()`, `toString()`, and `copy()` by hand every
time. Kotlin auto-generates all of it from your constructor.

### Theory

```
  Kotlin auto-generates for data class:

  equals()     Compare by CONTENT, not memory address
  hashCode()   Hash from all constructor properties
  toString()   "Flashcard(question=..., answer=..., ...)"  (useful!)
  copy(...)    Clone with optional field overrides
  componentN() Powers destructuring: val (q, a) = card

  Regular vs Data class toString():
    Regular:  "Card@5f4da5c3"           <- useless memory address
    Data:     "Flashcard(question=..)"  <- actual field values

  Equality:
    val c1 = Card("Q","A"); val c2 = Card("Q","A")
    Regular:  c1 == c2  ->  FALSE  (different objects)
    Data:     c1 == c2  ->  TRUE   (same content)
```

### Code Example

```kotlin
data class Flashcard(
    val question:   String,
    val answer:     String,
    val difficulty: String,         // "easy" | "medium" | "hard"
    val score:      Int     = 0,    // default — caller can omit
    val hint:       String? = null  // nullable — may be absent
)

// Creating instances
val card1 = Flashcard("What is val?", "Immutable variable", "easy")
val card2 = Flashcard(
    question   = "What is a lambda?",
    answer     = "Anonymous function",
    difficulty = "hard",
    score      = 8,
    hint       = "Think: function without a name"
)

// toString() — auto-generated
println(card1)
// Flashcard(question=What is val?, answer=Immutable variable, difficulty=easy, score=0, hint=null)

// equals() — structural equality
val card3 = Flashcard("What is val?", "Immutable variable", "easy")
println(card1 == card3)   // true   <- same content
println(card1 === card3)  // false  <- different objects in memory

// copy() — clone with selective overrides
val harder  = card1.copy(difficulty = "hard", score = 5)
val leveled = card2.copy(score = card2.score + 1)  // score = 9

// Destructuring
val (question, answer, difficulty) = card1
println("Q: $question | A: $answer | D: $difficulty")

// Skip fields with _
val (q, _, diff) = card1
println("$q is $diff")
```

### Common Mistakes

```
WRONG:  data class Flashcard(question: String)
        // Missing val/var — it''s a constructor param, not a property!
RIGHT:  data class Flashcard(val question: String)

NOTE:   Properties declared in the class body are NOT part of
        equals() / hashCode() — only primary constructor params are.
```

---

## 6. Null Safety

### Why This Matters

`NullPointerException` is the most common Android crash in Java.
Kotlin eliminates it at the **compiler level** — you cannot accidentally
pass null where null is not expected. The type system tracks nullability.

### Theory

```
  Non-nullable:  val name: String   ->  NEVER null (compiler enforced)
  Nullable:      val hint: String?  ->  CAN be null (note the ?)

  Null Operators:
  +------+------------------------------------------------------------+
  |  ?.  |  Safe call — call only if not null, else return null      |
  |      |  hint?.length  ->  length, or null (no crash)             |
  +------+------------------------------------------------------------+
  |  ?:  |  Elvis — fallback when null                               |
  |      |  hint ?: "No hint"  ->  hint value, or "No hint"          |
  +------+------------------------------------------------------------+
  |  !!  |  Force unwrap — throws if null. AVOID.                    |
  |      |  hint!!.length  ->  crashes with KotlinNPE if null        |
  +------+------------------------------------------------------------+
  | let  |  Run block only when value is non-null                    |
  |      |  hint?.let { use(it) }                                    |
  +------+------------------------------------------------------------+

  Decision flow for nullable value:

    val hint: String?
          |
      Is null?
      YES -> hint?.length    returns null (safe)
             hint ?: "X"     returns "X"
             hint?.let{...}  block skipped
      NO  -> hint?.length    returns actual length
             hint!!.length   returns length (risky)
             hint?.let{...}  block runs with "it" = hint
```

### Code Example

```kotlin
val card         = Flashcard("What is val?", "Immutable variable", "easy")
val cardWithHint = Flashcard("What is var?", "Mutable variable", "easy",
                              hint = "Think: can re-assign")

// Safe call ?.
println(card.hint?.length)          // null  (no crash!)
println(cardWithHint.hint?.length)  // 22

// Chain safe calls — short-circuits at first null
val upper = card.hint?.trim()?.uppercase()?.take(10)
println(upper)  // null

// Elvis ?:
val text = card.hint ?: "No hint available"
println(text)  // No hint available

// Elvis with throw — great for validation
fun requireHint(c: Flashcard): String =
    c.hint ?: throw IllegalArgumentException("No hint for: ${c.question}")

// let — null-safe block execution
card.hint?.let { hint ->
    println("Hint: $hint")
}  // skipped when null

cardWithHint.hint?.let {
    println("Clean hint: ${it.trim().lowercase()}")
}  // runs

// Smart cast — compiler tracks null check
if (card.hint != null) {
    println(card.hint.length)     // no ?. needed inside this block
    println(card.hint.uppercase())
}

// filterNotNull — remove nulls from a list
val hints: List<String?> = listOf("Think immutability", null, "Re-assignable", null)
val clean: List<String>  = hints.filterNotNull()
println(clean)  // [Think immutability, Re-assignable]

// lateinit — non-nullable var initialized later (Android views, DI)
lateinit var database: String
database = "FlashcardDB"
if (::database.isInitialized) println(database)
```

### Common Mistakes

```
WRONG:  val hint: String = null      // compile error
RIGHT:  val hint: String? = null

WRONG:  hint!!.length                // crashes on null
RIGHT:  hint?.length ?: 0

WRONG:  if (hint != null) { hint?.length }  // ?. redundant inside null check
RIGHT:  if (hint != null) { hint.length }   // smart cast active
```

---

## 7. Collections — List, groupBy, sortBy

### Why This Matters

Your quiz deck is a `List<Flashcard>`. Kotlin''s collection API lets you slice,
group, sort, and transform that data in one readable line instead of multiple loops.

### Theory

```
  Collection Types:
  +-----------------+--------------------------------------------+
  |  listOf()       |  Immutable ordered list (read-only)        |
  |  mutableListOf()|  Mutable ordered list (add/remove/set)     |
  |  setOf()        |  Immutable — no duplicates                 |
  |  mapOf()        |  Immutable key->value pairs                |
  |  mutableMapOf() |  Mutable map                               |
  +-----------------+--------------------------------------------+

  Rule: Default to immutable (listOf). Use mutable only when needed.

  groupBy — visualized:

  List<Flashcard>
  [easy, easy, medium, hard, hard, medium]
                 |
        .groupBy { it.difficulty }
                 v
  Map<String, List<Flashcard>>
  "easy"   -> [card1, card2]
  "medium" -> [card3, card6]
  "hard"   -> [card4, card5]
```

### Code Example

```kotlin
val flashcards = listOf(
    Flashcard("What is val?",      "Immutable variable", "easy",   score = 2),
    Flashcard("What is var?",      "Mutable variable",   "easy",   score = 4),
    Flashcard("What is when?",     "Pattern matching",   "medium", score = 6),
    Flashcard("What is a lambda?", "Anonymous function", "hard",   score = 9),
    Flashcard("Null safety ops?",  "?. ?: !!",           "hard",   score = 7),
    Flashcard("What is let?",      "Scope function",     "medium", score = 5),
    Flashcard("What is apply?",    "Scope function",     "medium", score = 3)
)

// Basic access
println(flashcards.size)             // 7
println(flashcards[0].question)
println(flashcards.getOrNull(99))    // null — safe, no crash

// filter
val hardCards   = flashcards.filter { it.difficulty == "hard" }
val highScoring = flashcards.filter { it.score > 5 }
val hardHigh    = flashcards.filter { it.difficulty == "hard" && it.score > 6 }

// map
val questions = flashcards.map { it.question }
val summaries = flashcards.map { "${it.difficulty.uppercase()}: ${it.question}" }

// groupBy -> Map<String, List<Flashcard>>
val byDifficulty = flashcards.groupBy { it.difficulty }
byDifficulty.forEach { (level, cards) ->
    val avg = "%.1f".format(cards.map { it.score }.average())
    println("$level -> ${cards.size} cards, avg: $avg")
}
val easyDeck = byDifficulty["easy"] ?: emptyList()

// sortedBy / sortedByDescending
val byScoreAsc  = flashcards.sortedBy { it.score }
val byScoreDesc = flashcards.sortedByDescending { it.score }

// Multi-level sort
val multiSort = flashcards.sortedWith(compareBy({ it.difficulty }, { -it.score }))

// Aggregations
val total   = flashcards.sumOf { it.score }           // 36
val avg     = flashcards.map { it.score }.average()   // ~5.14
val topCard = flashcards.maxByOrNull { it.score }     // Flashcard?

// take / drop / chunked / shuffled
val first3  = flashcards.take(3)
val skip2   = flashcards.drop(2)
val batches = flashcards.chunked(3)   // [[c1,c2,c3],[c4,c5,c6],[c7]]
val random5 = flashcards.shuffled().take(5)

// distinct
val levels = flashcards.map { it.difficulty }.distinct()  // [easy, medium, hard]

// Mutable list
val deck = mutableListOf<Flashcard>()
deck.add(Flashcard("New Q?", "New A", "easy"))
deck.addAll(flashcards)
deck.removeIf { it.score < 3 }
deck.sortBy { it.score }
deck[0] = deck[0].copy(score = 10)  // update via data class copy

// zip — pair two lists
val qs   = listOf("Q1", "Q2", "Q3")
val ans  = listOf("A1", "A2", "A3")
val pairs = qs.zip(ans)   // [(Q1,A1), (Q2,A2), (Q3,A3)]
```

---

## 8. Scope Functions

### Why This Matters

Scope functions remove repetitive variable references, enable fluent chaining,
and make null-handling expressive. They don''t add new capability — they add
*clarity and intent* to existing code.

### Theory

```
  +----------+-------------+---------------+-------------------------------+
  | Function | Context obj | Return value  | Primary use case              |
  +----------+-------------+---------------+-------------------------------+
  |  apply   |  this       |  the object   | Configure/build an object     |
  |  also    |  it         |  the object   | Side effects (logging, debug) |
  |  let     |  it         | lambda result | Transform or null-check       |
  |  run     |  this       | lambda result | Compute result on object      |
  |  with    |  this       | lambda result | Group ops (not extension fn)  |
  +----------+-------------+---------------+-------------------------------+

  Mental Models:

  apply  ->  "Configure me, return me"
             obj.apply { x = 1; y = 2 }  returns obj

  also   ->  "Do a side effect, return me unchanged"
             obj.also { log(it) }  returns obj

  let    ->  "Transform me, return the result"
             hint?.let { it.uppercase() }  returns String?

  run    ->  "Compute something using my fields, return result"
             card.run { "$question -> $answer" }  returns String

  with   ->  same as run but called as with(obj) { ... }
```

### Code Example

```kotlin
// apply{} — configure and return the same object
val deck = mutableListOf<Flashcard>().apply {
    add(Flashcard("Q1?", "A1", "easy"))
    add(Flashcard("Q2?", "A2", "hard"))
    add(Flashcard("Q3?", "A3", "medium"))
    sortBy { it.difficulty }
}

// Builder pattern with apply
class QuizConfig {
    var shuffled: Boolean  = false
    var maxCards: Int      = 10
    var difficulty: String = "all"
    var timeLimit: Int     = 0
}

val config = QuizConfig().apply {
    shuffled   = true
    maxCards   = 5
    difficulty = "hard"
    timeLimit  = 30
}

// also{} — side effect, returns original object unchanged
val result = flashcards
    .filter { it.score > 5 }
    .also { println("After filter: ${it.size} cards") }   // peek without breaking chain
    .sortedByDescending { it.score }
    .also { println("Top card: ${it.first().question}") }
    .take(3)

// let{} — transform, or null-safe block
val hint: String? = "Think immutability"

hint?.let { println("Hint: $it") }  // runs only when not null

val hintUpper = hint?.let { it.trim().uppercase() } ?: "NO HINT"

// Scoped computation — no outer variable pollution
val topSummary = flashcards
    .maxByOrNull { it.score }
    ?.let { card -> "Top: ''${card.question}'' scored ${card.score}" }
    ?: "No cards"

// run{} — compute a result inside the object
val summary = card2.run {
    buildString {
        appendLine("Q:  $question")
        appendLine("A:  $answer")
        appendLine("D:  $difficulty")
        appendLine("Sc: $score")
        hint?.let { appendLine("H:  $it") }
    }
}
println(summary)

// with{} — group operations
val display = with(card2) {
    "[$difficulty] $question (score: $score)"
}
```

---

## 9. Extension Functions

### Why This Matters

Extension functions let you add behavior to classes you don''t own — like
`String`, `List`, or Android''s `Context` — without subclassing or modifying
source code. This is how Kotlin''s own standard library is largely built.

### Theory

```
  Syntax:
  fun  ReceiverType . functionName ( params ) : ReturnType { body }
        |
        +-- The type you are extending.
            "this" inside the body = the receiver object.

  Before extension:              After extension:
  checkAnswer(userInput,         userInput.isCorrectAnswer(card.answer)
              card.answer)
  Detached utility function.     Reads naturally. IDE autocompletes it.
```

### Code Example

```kotlin
// String extensions
fun String.isCorrectAnswer(correct: String): Boolean =
    trim().lowercase() == correct.trim().lowercase()

fun String.toTitleCase(): String =
    split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

fun String.truncate(max: Int = 40): String =
    if (length <= max) this else "${take(max)}…"

fun String.boxed(width: Int = 50): String {
    val line = "─".repeat(width)
    return "┌$line┐\n│ ${padEnd(width - 1)}│\n└$line┘"
}

// Usage
println("  Immutable Variable  ".isCorrectAnswer("immutable variable")) // true
println("what is a data class".toTitleCase())   // What Is A Data Class
println("This is a very long question".truncate(20))  // This is a very long…

// Flashcard extensions
fun Flashcard.isHard()    = difficulty == "hard"
fun Flashcard.isPerfect() = score == 10

fun Flashcard.difficultyBadge(): String = when (difficulty) {
    "easy"   -> "🟢 Easy"
    "medium" -> "🟡 Medium"
    "hard"   -> "🔴 Hard"
    else     -> "⚪ Unknown"
}

fun Flashcard.scoreRating(): String = when {
    score >= 9 -> "★★★ Mastered"
    score >= 6 -> "★★☆ Good"
    score >= 3 -> "★☆☆ Learning"
    else       -> "☆☆☆ New"
}

fun Flashcard.displayRow(): String =
    "| ${difficultyBadge().padEnd(12)} | ${question.truncate(32).padEnd(34)} | ${scoreRating()} |"

// List<Flashcard> extensions
fun List<Flashcard>.averageScore(): Double =
    if (isEmpty()) 0.0 else sumOf { it.score }.toDouble() / size

fun List<Flashcard>.ofDifficulty(level: String) =
    filter { it.difficulty == level }

fun List<Flashcard>.top(n: Int = 5) =
    sortedByDescending { it.score }.take(n)

fun List<Flashcard>.printTable() {
    println("┌──────────────┬────────────────────────────────────┬──────────────────┐")
    println("│ Difficulty   │ Question                           │ Rating           │")
    println("├──────────────┼────────────────────────────────────┼──────────────────┤")
    forEach { println(it.displayRow()) }
    println("└──────────────┴────────────────────────────────────┴──────────────────┘")
    println("  Average score: ${"%.2f".format(averageScore())}")
}

// Usage
flashcards.ofDifficulty("hard").printTable()
println("Top 3: ${flashcards.top(3).map { it.question }}")
```

---

## 10. Complete Worked Example

This ties every concept together in a runnable KotlinDrills CLI session.

```kotlin
import kotlin.random.Random

// ─── Data Model (data class, null safety) ─────────────────────────────────
data class Flashcard(
    val question:   String,
    val answer:     String,
    val difficulty: String,
    val score:      Int     = 0,
    val hint:       String? = null
)

// ─── Extension Functions ──────────────────────────────────────────────────
fun String.isCorrectAnswer(correct: String) =
    trim().lowercase() == correct.trim().lowercase()

fun String.truncate(max: Int = 35) =
    if (length <= max) this else "${take(max)}…"

fun Flashcard.difficultyBadge() = when (difficulty) {
    "easy"   -> "🟢"
    "medium" -> "🟡"
    else     -> "🔴"
}

fun Flashcard.scoreRating() = when {
    score >= 9 -> "Mastered"
    score >= 6 -> "Good"
    score >= 3 -> "Learning"
    else       -> "New"
}

fun List<Flashcard>.averageScore() =
    if (isEmpty()) 0.0 else sumOf { it.score }.toDouble() / size

// ─── Deck (apply + mutableListOf) ─────────────────────────────────────────
val masterDeck = mutableListOf<Flashcard>().apply {
    add(Flashcard("What does val do?",      "Declares immutable variable",  "easy",   hint = "Think: constant"))
    add(Flashcard("What does var do?",      "Declares mutable variable",    "easy"))
    add(Flashcard("What is a lambda?",      "Anonymous function",           "medium", hint = "{ params -> body }"))
    add(Flashcard("Explain data class",     "Auto equals/hashCode/copy",    "medium"))
    add(Flashcard("What is ?. operator?",   "Safe call on nullable",        "hard",   hint = "Won''t crash on null"))
    add(Flashcard("What is ?: operator?",   "Elvis — fallback for null",    "hard"))
    add(Flashcard("What does apply{} do?",  "Configures object, returns it","hard",   hint = "Builder pattern"))
    add(Flashcard("What is an HOF?",        "Takes/returns a function",     "medium"))
    add(Flashcard("What does groupBy do?",  "Groups list into a Map",       "medium"))
    add(Flashcard("What is extension fun?", "Adds fn to existing class",    "hard",   hint = "fun Type.name()"))
}

// ─── Quiz Session (HOFs, control flow, null safety, scope functions) ──────
fun runQuizSession(
    deck:       List<Flashcard>,
    difficulty: String  = "all",
    maxCards:   Int     = 5,
    shuffled:   Boolean = true
) {
    // Filter + shuffle + take (HOFs + collections)
    val filtered = if (difficulty == "all") deck
                   else deck.filter { it.difficulty == difficulty }

    val session = (if (shuffled) filtered.shuffled() else filtered).take(maxCards)

    if (session.isEmpty()) {
        println("No cards found for difficulty: $difficulty")
        return
    }

    val results = mutableListOf<Pair<Flashcard, Boolean>>()

    println("\n${"═".repeat(55)}")
    println("  KotlinDrills  |  ${session.size} cards  |  $difficulty")
    println("${"═".repeat(55)}")

    // for loop + withIndex + when + null safety
    for ((index, card) in session.withIndex()) {
        println("\n  ${card.difficultyBadge()} Q${index + 1}: ${card.question}")

        // null safety — show hint only if present
        card.hint?.let { println("  💡 $it") }

        print("  Your answer: ")
        val input = readLine() ?: ""

        val correct = input.isCorrectAnswer(card.answer)

        val feedback = when {
            correct         -> "  ✅ Correct!"
            input.isBlank() -> "  ⏭  Skipped. Answer: ${card.answer}"
            else            -> "  ❌ Wrong. Answer: ${card.answer}"
        }
        println(feedback)

        results.add(card to correct)
    }

    // Summary (collections + scope functions)
    val passed = results.count { it.second }
    val pct    = (passed * 100) / results.size

    println("\n${"─".repeat(55)}")
    println("  Score: $passed / ${results.size}  ($pct%)")

    results
        .filter { !it.second }
        .also { wrong ->
            if (wrong.isNotEmpty()) {
                println("\n  Review these:")
                wrong.forEach { (card, _) ->
                    println("    • ${card.question.truncate()}")
                }
            }
        }
    println("${"─".repeat(55)}")
}

// ─── Stats (groupBy + collections + HOFs) ─────────────────────────────────
fun showStats(deck: List<Flashcard>) {
    println("\n  📊 Deck Statistics")
    println("  ${"─".repeat(45)}")

    deck.groupBy { it.difficulty }.forEach { (level, cards) ->
        val avg = "%.1f".format(cards.averageScore())
        println("  $level  (${cards.size} cards)  avg score: $avg")

        // scope function let — print top card if exists
        cards.maxByOrNull { it.score }?.let {
            println("    Top: ${it.question.truncate(35)}  [${it.scoreRating()}]")
        }
    }

    println("\n  Top 3 overall:")
    deck.sortedByDescending { it.score }
        .take(3)
        .forEachIndexed { i, card ->
            println("  ${i + 1}. ${card.difficultyBadge()} ${card.question.truncate(35)}  (score: ${card.score})")
        }
}

// ─── Main (while loop + when + control flow) ──────────────────────────────
fun main() {
    var running = true

    while (running) {
        println("\n  What do you want to do?")
        println("  [1] Quick quiz (5 random cards)")
        println("  [2] Hard cards only")
        println("  [3] Easy warm-up")
        println("  [4] Show stats")
        println("  [5] Quit")
        print("  > ")

        when (readLine()?.trim()) {
            "1" -> runQuizSession(masterDeck)
            "2" -> runQuizSession(masterDeck, difficulty = "hard")
            "3" -> runQuizSession(masterDeck, difficulty = "easy", maxCards = 3)
            "4" -> showStats(masterDeck)
            "5" -> { println("  Goodbye! Keep drilling. 💪"); running = false }
            else -> println("  Invalid choice.")
        }
    }
}
```

---

## Quick Reference Cheat Sheet

```
+----------------------+----------------------------------------------+
|  val / var           |  val = immutable, var = mutable              |
|  String template     |  "Hello $name, score: ${x+1}"               |
|  if expression       |  val r = if (x > 0) "pos" else "neg"         |
|  when expression     |  val p = when(d) { "easy"->1 ; "hard"->5 }   |
|  for range           |  for (i in 1..10 step 2) { }                 |
|  Lambda              |  { param -> body }  or  { it.field }         |
|  filter              |  list.filter { it.score > 5 }                |
|  map                 |  list.map { it.question }                    |
|  groupBy             |  list.groupBy { it.difficulty }              |
|  sortedBy            |  list.sortedBy { it.score }                  |
|  data class          |  data class X(val a: String, val b: Int)     |
|  copy                |  card.copy(score = 10)                       |
|  safe call           |  hint?.length                                |
|  elvis               |  hint ?: "default"                           |
|  let                 |  value?.let { use(it) }                      |
|  apply               |  obj.apply { x = 1; y = 2 }                  |
|  also                |  obj.also { log(it) }                        |
|  extension fn        |  fun String.shout() = uppercase() + "!"      |
+----------------------+----------------------------------------------+
```

---

> **Next step:** Open `Main.kt`, implement the worked example section by section,
> and run it with `./gradlew run`.
