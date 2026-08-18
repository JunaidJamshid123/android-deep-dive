# SECTION 3.2 — Compose Layouts & Modifiers

**Concepts:** `Column`, `Row`, `Box`, the Modifier system, `ConstraintLayout` in Compose, custom `Layout` composables, `SubcomposeLayout`
**Project:** ProfileCard UI Kit

---

## Table of Contents

1. [Column, Row, Box](#1-column-row-box)
2. [The Modifier System & Chain Order](#2-the-modifier-system--chain-order)
3. [The `weight` Modifier](#3-the-weight-modifier)
4. [Spacer](#4-spacer)
5. [ConstraintLayout in Compose](#5-constraintlayout-in-compose)
6. [Custom Layout Composable](#6-custom-layout-composable)
7. [SubcomposeLayout](#7-subcomposelayout)
8. [Modifier.graphicsLayer](#8-modifiergraphicslayer)
9. [Project Walkthrough — ProfileCard UI Kit](#9-project-walkthrough--profilecard-ui-kit)
10. [Full Code Example](#10-full-code-example)
11. [Common Pitfalls](#11-common-pitfalls)
12. [Interview Q&A](#12-interview-qa)

---

## 1. Column, Row, Box

### Theory

Compose has exactly **three** foundational layout composables. Everything else (LazyColumn, ConstraintLayout, custom layouts) is built on the same underlying `Layout` primitive, but for 90% of UI you only need these three:

| Composable | Arranges children | Analogous to (View system) |
|---|---|---|
| `Column` | Vertically, top → bottom | `LinearLayout(orientation = vertical)` |
| `Row` | Horizontally, left → right | `LinearLayout(orientation = horizontal)` |
| `Box` | Stacked on top of each other (z-order) | `FrameLayout` |

Each one exposes **scoped alignment/arrangement parameters**:

- `Column` → `verticalArrangement` (how children are spaced along the main axis) + `horizontalAlignment` (cross axis)
- `Row` → `horizontalArrangement` (main axis) + `verticalAlignment` (cross axis)
- `Box` → `contentAlignment` (both axes) — and each child can override with `Modifier.align()`

**Arrangement** controls spacing/distribution (`SpaceBetween`, `SpaceEvenly`, `SpaceAround`, `Center`, `Top`/`Bottom`, or a fixed `spacedBy(8.dp)`).
**Alignment** controls positioning on the cross axis (`Start`, `CenterHorizontally`, `End` for Column; `Top`, `CenterVertically`, `Bottom` for Row).

### Diagram

```
COLUMN (main axis = vertical)                ROW (main axis = horizontal)
┌─────────────────────┐                      ┌───────────────────────────────┐
│  ┌───────────────┐  │  ↑                   │ ┌────┐ ┌────┐ ┌────┐          │
│  │   Child 1     │  │  │ main               │ │ C1 │ │ C2 │ │ C3 │  ← main │
│  └───────────────┘  │  │ axis               │ └────┘ └────┘ └────┘  axis  │
│  ┌───────────────┐  │  │ (vertical)         └───────────────────────────────┘
│  │   Child 2     │  │  │                     ←── cross axis (horizontal) ──→
│  └───────────────┘  │  ↓
└─────────────────────┘
 ←── cross axis ──→
 (horizontal)

BOX (z-order stacking)
┌─────────────────────────┐
│  ┌───────────────────┐  │   Children stack on top of
│  │ Child 1 (bottom)   │  │   each other. Later children
│  │  ┌───────────────┐ │  │   drawn ON TOP of earlier ones.
│  │  │ Child 2 (top)  │ │  │   contentAlignment / Modifier.align()
│  │  └───────────────┘ │  │   positions each child within
│  └───────────────────┘  │   the Box's bounds.
└─────────────────────────┘
```

### Code Example

```kotlin
@Composable
fun LayoutBasicsDemo() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Header", style = MaterialTheme.typography.titleLarge)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Left label")
            Text("Right label")
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(Color.LightGray),
            contentAlignment = Alignment.Center
        ) {
            Text("Centered in Box")
            Icon(
                Icons.Default.Star,
                contentDescription = null,
                modifier = Modifier.align(Alignment.TopEnd) // overrides Box's default
            )
        }
    }
}
```

---

## 2. The Modifier System & Chain Order

### Theory

A `Modifier` is an **immutable, ordered chain** of elements that decorate or transform a composable — think of it like a pipeline (very similar to Kotlin's `Sequence` chain or middleware in Express). Each `.modifierFunction()` call wraps the previous one.

**Critically: order matters.** Compose applies modifiers as **nested wrapping**, from outer (first in chain) to inner (last in chain). Each modifier only "sees" the space/constraints passed down by the one before it.

Think of it as **Russian nesting dolls** — the first modifier in the chain is the outermost doll.

```
Modifier.background(Red).padding(16.dp)
   → Red box drawn FIRST at full size, padding applied INSIDE it
   → Result: red border/frame visible around the content

Modifier.padding(16.dp).background(Red)
   → padding applied FIRST (shrinks available space)
   → Red background drawn only in the shrunken area
   → Result: NO visible red border — background hugs the content
```

This is the single most common Compose bug source for people coming from XML/View systems, where background is just a "property" with no ordering concept.

### Diagram

```
Modifier.background(Color.Red).padding(16.dp) { Text("Hi") }

┌──────────────────────────────┐  ← Red background = OUTER layer
│ RED                          │     (sized to full available space)
│    ┌───────────────────┐     │
│    │   padding (16dp)  │     │  ← padding pushes content inward
│    │   ┌───────────┐   │     │     WITHIN the red area
│    │   │    Hi     │   │     │
│    │   └───────────┘   │     │
│    └───────────────────┘     │
└──────────────────────────────┘
  Result: visible red "border" around Hi


Modifier.padding(16.dp).background(Color.Red) { Text("Hi") }

┌──────────────────────────────┐
│  (transparent - no bg here)  │  ← padding reserves space FIRST
│    ┌───────────────────┐     │
│    │ RED               │     │  ← background only wraps
│    │   ┌───────────┐   │     │     the REMAINING (post-padding) area
│    │   │    Hi     │   │     │
│    │   └───────────┘   │     │
│    └───────────────────┘     │
└──────────────────────────────┘
  Result: NO visible border — red hugs "Hi" tightly
```

### Code Example

```kotlin
@Composable
fun ModifierOrderDemo() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

        // Case A: background OUTSIDE padding -> visible red frame
        Text(
            text = "Case A",
            color = Color.White,
            modifier = Modifier
                .background(Color.Red)   // 1. draw red over full width
                .padding(16.dp)          // 2. push text inward
        )

        // Case B: padding OUTSIDE background -> red hugs text tightly
        Text(
            text = "Case B",
            color = Color.White,
            modifier = Modifier
                .padding(16.dp)          // 1. reserve outer margin (transparent)
                .background(Color.Red)   // 2. red only around remaining text box
        )

        // clickable + padding order also matters for TOUCH TARGET size
        Text(
            text = "Click me (large touch target)",
            modifier = Modifier
                .clickable { /* ripple + touch target covers padding too */ }
                .padding(24.dp)
        )
    }
}
```

**Rule of thumb:** `size/padding` modifiers that come *before* `background`/`border` shrink the canvas those draw into. Modifiers that come *before* `clickable` are included in the clickable region; modifiers *after* `clickable` are not.

---

## 3. The `weight` Modifier

### Theory

`Modifier.weight(Float)` is only available **inside a `RowScope` or `ColumnScope`** (it's a scoped extension function — you literally cannot call it on a `Box`'s children). It tells the parent: *"After laying out all non-weighted siblings, divide the REMAINING space among weighted children proportionally to their weight value."*

Two-pass algorithm:
1. Measure all children **without** `weight` at their natural size.
2. Subtract that from total available space → remaining space.
3. Divide remaining space among weighted children proportional to `weight / sum(all weights)`.

`fill: Boolean = true` parameter controls whether the child is forced to fill its allotted weighted space, or just capped at it.

### Diagram

```
Row(width = 300dp) {
    Box(Modifier.weight(1f))   // gets 1/3 of remaining space
    Box(Modifier.width(50dp))  // fixed, measured first
    Box(Modifier.weight(2f))   // gets 2/3 of remaining space
}

Step 1: measure fixed child → 50dp used
Step 2: remaining = 300 - 50 = 250dp
Step 3: total weight units = 1 + 2 = 3 → 1 unit = 250/3 ≈ 83.3dp

┌──────────────┬────────┬────────────────────────┐
│  weight(1f)  │ fixed  │      weight(2f)         │
│   ~83.3dp    │  50dp  │       ~166.7dp           │
└──────────────┴────────┴────────────────────────┘
```

### Code Example — Avatar/Bio split (as used in ProfileCard)

```kotlin
@Composable
fun CompactProfileRow(name: String, bio: String, avatarUrl: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = "$name avatar",
            modifier = Modifier
                .size(56.dp)          // fixed — measured first
                .clip(CircleShape)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f) // takes ALL remaining row space
        ) {
            Text(name, fontWeight = FontWeight.Bold)
            Text(
                bio,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
```

---

## 4. Spacer

### Theory

`Spacer` is a composable with **no content and no visuals** — its only job is to occupy space, using a `Modifier` (usually `.width()`, `.height()`, or `.size()`) to define how much. It's the Compose equivalent of an empty `View` used as a gap in XML `LinearLayout`s.

Why not just use `.padding()` on the surrounding elements instead? Two reasons people prefer `Spacer`:
- It's **explicit and visually obvious** in the layout tree ("here is a deliberate 8dp gap").
- Arrangement's `spacedBy()` is often *cleaner* than manual Spacers for uniform gaps — `Spacer` is best for **one-off, non-uniform** gaps.

```kotlin
Spacer(modifier = Modifier.height(8.dp))     // vertical gap in a Column
Spacer(modifier = Modifier.width(8.dp))      // horizontal gap in a Row
Spacer(modifier = Modifier.weight(1f))       // "push" — flexible gap that eats remaining space
```

`Spacer(Modifier.weight(1f))` is a very common trick to push one item to the far end of a Row (e.g., pushing a trailing icon to the right edge) without needing `SpaceBetween`.

### Diagram

```
Row {
   Icon(...)
   Spacer(Modifier.weight(1f))   ← expands to eat all free space
   Text("Trailing")
}

┌──────┬─────────────────────────────┬───────────┐
│ Icon │      (flexible spacer)      │ Trailing  │
└──────┴─────────────────────────────┴───────────┘
    fixed          grows to fill            fixed
```

### Code Example

```kotlin
@Composable
fun StoryHeaderRow(username: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Person, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))       // fixed 8dp gap
        Text(username, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.weight(1f))         // pushes menu icon to the end
        Icon(Icons.Default.MoreVert, contentDescription = "Options")
    }
}
```

---

## 5. ConstraintLayout in Compose

### Theory

`ConstraintLayout` (from the `androidx.constraintlayout:constraintlayout-compose` artifact) solves a problem `Column`/`Row`/`Box` struggle with: **relative positioning between siblings that isn't strictly linear or stacked** — e.g., "put this badge at the bottom-right corner of the avatar, overlapping it by 25%."

Two ways to use it:

1. **Inline `createRefFor` + `Modifier.constrainAs`** — good for simple, static layouts.
2. **`ConstraintSet` (JSON-like DSL, separate from composable body)** — good when you need to **swap layouts** (e.g., different constraints for portrait vs landscape) without recomposing the whole tree — similar to `MotionLayout` in the View system.

Key building blocks:
- `createRefFor("id")` or `val (ref1, ref2) = createRefs()` → declare references for each child.
- `Modifier.constrainAs(ref) { ... }` → attach constraint rules to a specific child.
- Anchors: `top`, `bottom`, `start`, `end`, `centerTo`, `centerHorizontallyTo`, `linkTo(anchor, margin)`.
- `Barrier`, `Guideline`, `Chain` — advanced grouping constructs (same concepts as the View-based ConstraintLayout).

Why use it over nested Box+align? Because deeply nested Boxes for precise pixel offsets get messy and hurt performance (each nesting level is a measure/layout pass). ConstraintLayout flattens everything into **one measure pass** with a constraint solver.

### Diagram

```
ConstraintLayout {
    val (avatar, badge) = createRefs()

    Avatar --- constrainAs(avatar) { top.linkTo(parent.top); start.linkTo(parent.start) }
    Badge  --- constrainAs(badge)  {
                  bottom.linkTo(avatar.bottom, margin = (-4).dp)
                  end.linkTo(avatar.end, margin = (-4).dp)
               }

     ┌───────────────────────┐
     │                       │
     │      ┌─────────┐      │
     │      │ AVATAR  │      │
     │      │  (56dp) │      │
     │      │       ┌─┼─┐    │  ← badge anchored to
     │      └───────│●│─┘    │     avatar's bottom-end
     │              └──┘     │     corner with negative
     │                       │     margin = overlap
     └───────────────────────┘
```

### Code Example — Badge over Avatar (exact ProfileCard use case)

```kotlin
@Composable
fun AvatarWithOnlineBadge(avatarUrl: String, isOnline: Boolean) {
    ConstraintLayout(modifier = Modifier.size(72.dp)) {
        val (avatar, badge) = createRefs()

        AsyncImage(
            model = avatarUrl,
            contentDescription = "avatar",
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .constrainAs(avatar) {
                    top.linkTo(parent.top)
                    start.linkTo(parent.start)
                }
        )

        if (isOnline) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(Color.Green, CircleShape)
                    .border(2.dp, Color.White, CircleShape)
                    .constrainAs(badge) {
                        bottom.linkTo(avatar.bottom, margin = (-2).dp)
                        end.linkTo(avatar.end, margin = (-2).dp)
                    }
            )
        }
    }
}
```

---

## 6. Custom Layout Composable

### Theory

When even `ConstraintLayout` isn't flexible enough — e.g., you need an algorithm to decide placement (like a **staggered/masonry grid** where item heights vary and you must pick the shortest column) — you drop to the `Layout` composable, Compose's lowest-level layout primitive. This is what `Row`, `Column`, and `Box` are themselves built from.

`Layout` gives you two callbacks:

```kotlin
Layout(
    content = { /* children */ },
    modifier = modifier
) { measurables, constraints ->
    // 1. MEASURE PHASE: measure each `Measurable` -> get back a `Placeable`
    val placeables = measurables.map { it.measure(constraints) }

    // 2. LAYOUT PHASE: declare the layout's own size, then place children
    layout(width, height) {
        placeables.forEach { placeable ->
            placeable.placeRelative(x, y)
        }
    }
}
```

- **`Measurable`** = an unmeasured child (you decide what constraints to measure it with).
- **`Placeable`** = the result of measuring — has `.width`/`.height`, and you call `.placeRelative(x, y)` to position it.
- Compose enforces **single-pass measurement**: you normally can't measure the same child twice (this is what `SubcomposeLayout`, covered next, exists to work around).

A `StaggeredGrid` custom layout works like this:
1. Decide column count (e.g., 3 fixed columns).
2. Track a running height-per-column array.
3. For each child, measure it, then place it in whichever column currently has the smallest accumulated height, then bump that column's height.

### Diagram

```
StaggeredGrid (3 columns) — column heights tracked as you place:

columnHeights = [0, 0, 0]

place child1 (h=100) → shortest col = 0 → columnHeights = [100, 0, 0]
place child2 (h=150) → shortest col = 1 → columnHeights = [100, 150, 0]
place child3 (h=80)  → shortest col = 2 → columnHeights = [100, 150, 80]
place child4 (h=60)  → shortest col = 2 → columnHeights = [100, 150, 140]

┌────────┬────────┬────────┐
│ child1 │ child2 │ child3 │
│ 100dp  │        │  80dp  │
│        │ 150dp  ├────────┤
│        │        │ child4 │
│        │        │  60dp  │
└────────┴────────┴────────┘
```

### Code Example — StaggeredGrid for photo thumbnails

```kotlin
@Composable
fun StaggeredGrid(
    columns: Int = 3,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val columnWidth = constraints.maxWidth / columns
        val itemConstraints = constraints.copy(
            minWidth = columnWidth,
            maxWidth = columnWidth
        )

        // 1. MEASURE each child at fixed column width (height flexible)
        val placeables = measurables.map { it.measure(itemConstraints) }

        // 2. Track height accumulated in each column
        val columnHeights = IntArray(columns)
        val itemPositions = placeables.map { placeable ->
            val column = columnHeights.indices.minByOrNull { columnHeights[it] }!!
            val x = column * columnWidth
            val y = columnHeights[column]
            columnHeights[column] += placeable.height
            Triple(placeable, x, y)
        }

        val totalHeight = columnHeights.max().coerceAtLeast(constraints.minHeight)

        // 3. Report final size, then place every child
        layout(constraints.maxWidth, totalHeight) {
            itemPositions.forEach { (placeable, x, y) ->
                placeable.placeRelative(x, y)
            }
        }
    }
}

// Usage:
@Composable
fun PhotoGrid(photoUrls: List<String>) {
    StaggeredGrid(columns = 3, modifier = Modifier.fillMaxWidth()) {
        photoUrls.forEach { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier
                    .padding(2.dp)
                    .aspectRatio(if (url.hashCode() % 2 == 0) 1f else 0.7f) // vary height
            )
        }
    }
}
```

---

## 7. SubcomposeLayout

### Theory

Normal `Layout` measures each child **exactly once** — good enough for a static grid. But sometimes you need to **measure a child, use the result to decide what to compose next**, e.g.:

- "Measure this content; if it's taller than 200dp, compose a scrollable version instead."
- A card that adapts its layout based on the **actual measured size of its content**, not a fixed algorithm.
- Google's own `BoxWithConstraints` is built on `SubcomposeLayout`.

`SubcomposeLayout` breaks the "single measure pass" rule by letting you **compose and measure in multiple passes, keyed by an ID** — you call `subcompose(slotId) { content }` as many times as needed, each returning fresh `Measurable`s you can measure immediately and react to.

```kotlin
SubcomposeLayout { constraints ->
    // Pass 1: subcompose + measure the "content" slot to learn its size
    val contentPlaceables = subcompose("content") { Content() }
        .map { it.measure(constraints) }
    val contentHeight = contentPlaceables.maxOf { it.height }

    // Decision based on measured result
    val finalPlaceables = if (contentHeight > maxAllowed) {
        subcompose("scrollable") { ScrollableWrapper { Content() } }
            .map { it.measure(constraints) }
    } else {
        contentPlaceables
    }

    layout(constraints.maxWidth, finalPlaceables.maxOf { it.height }) {
        finalPlaceables.forEach { it.placeRelative(0, 0) }
    }
}
```

This is more expensive than `Layout` (multiple composition passes), so it's reserved for cases where you genuinely need "measure-then-decide" logic.

### Diagram

```
SubcomposeLayout — Two-Pass Adaptive Card

PASS 1: subcompose("probe") { CardContent() }
         → measure it → actualHeight = 340dp

         actualHeight (340dp) > maxHeight (250dp)?  → YES

PASS 2: subcompose("final") { CardContent(scrollable = true) }
         → measure with scroll wrapper
         → placed at final layout size (250dp, capped)

┌─────────────────────────┐        ┌─────────────────────────┐
│  PASS 1 (measure only,   │  ──▶   │  PASS 2 (real content,   │
│  discarded / probing)    │        │  scrollable, placed)     │
│  height = 340dp          │        │  height = 250dp (capped) │
└─────────────────────────┘        └─────────────────────────┘
```

### Code Example — MeasureCard that adapts based on content height

```kotlin
@Composable
fun MeasureCard(
    maxCollapsedHeight: Dp = 200.dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    SubcomposeLayout(modifier = modifier) { constraints ->
        val maxHeightPx = maxCollapsedHeight.roundToPx()

        // PASS 1 — probe: measure natural content height
        val probeMeasurables = subcompose(slotId = "probe", content = content)
        val probePlaceables = probeMeasurables.map { it.measure(constraints) }
        val naturalHeight = probePlaceables.maxOfOrNull { it.height } ?: 0

        val needsScroll = naturalHeight > maxHeightPx

        // PASS 2 — final: compose real content, wrapped in scroll if needed
        val finalPlaceables = subcompose(slotId = "final") {
            if (needsScroll) {
                Box(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    content()
                }
            } else {
                content()
            }
        }.map {
            it.measure(
                constraints.copy(
                    maxHeight = if (needsScroll) maxHeightPx else constraints.maxHeight
                )
            )
        }

        val finalHeight = finalPlaceables.maxOfOrNull { it.height }
            ?.coerceAtMost(if (needsScroll) maxHeightPx else Int.MAX_VALUE) ?: 0

        layout(constraints.maxWidth, finalHeight) {
            finalPlaceables.forEach { it.placeRelative(0, 0) }
        }
    }
}
```

---

## 8. Modifier.graphicsLayer

### Theory

`Modifier.graphicsLayer { ... }` gives you low-level access to the rendering layer of a composable — transformations that happen **during drawing, on the GPU**, without triggering a re-measure or re-layout. This makes it the most **performance-efficient** way to animate visual properties like rotation, scale, alpha, and shadow — because Compose can skip the measure/layout phases entirely and jump straight to re-drawing that layer.

Common properties inside the `graphicsLayer` lambda:

| Property | Effect |
|---|---|
| `rotationX` / `rotationY` / `rotationZ` | 3D / 2D rotation |
| `scaleX` / `scaleY` | Scale up/down |
| `alpha` | Transparency (0f–1f) |
| `translationX` / `translationY` | Pixel offset (post-layout) |
| `shadowElevation` + `shape` | Drop shadow (needs a shape to clip to) |
| `clip` | Whether to clip content to `shape` |
| `cameraDistance` | Controls perspective depth for 3D rotations |

Why prefer this over `Modifier.rotate()` / `Modifier.scale()` / `Modifier.alpha()` convenience modifiers? Those convenience modifiers are literally implemented using `graphicsLayer` under the hood — but using `graphicsLayer{}` directly with a **lambda** form (rather than passing static values) lets Compose skip recomposition entirely when the values change via animation — the block only re-executes during the draw phase, not full recomposition. This is the standard trick for **high-performance animations** (e.g., animating rotation via `Animatable` and reading `.value` inside `graphicsLayer{}`).

### Diagram

```
Normal Modifier pipeline:
  Composition → Measure → Layout → Draw

Modifier.graphicsLayer { rotationZ = angle }:
  Composition → Measure → Layout → Draw (GPU layer transform applied here)
                                     ↑
                            Only THIS phase re-runs
                            when `angle` changes via
                            an Animatable/animate*AsState
                            read INSIDE the lambda.

  ┌────────────┐        ┌────────────┐
  │   Card      │  rot   │  Card       │
  │  (flat)    │ ─────▶ │  (rotated)  │   shadowElevation adds
  └────────────┘        │    ⟍         │   depth without changing
                         └────────────┘   layout bounds
```

### Code Example — Rotating story ring + shadowed card

```kotlin
@Composable
fun RotatingStoryRing(isActive: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "story-ring")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
        label = "angle"
    )

    Box(
        modifier = Modifier
            .size(72.dp)
            .graphicsLayer {
                if (isActive) rotationZ = angle   // only redraws, no relayout
            }
            .border(
                width = 3.dp,
                brush = Brush.sweepGradient(listOf(Color.Magenta, Color.Yellow, Color.Magenta)),
                shape = CircleShape
            )
    )
}

@Composable
fun ShadowedProfileCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                shadowElevation = 12.dp.toPx()
                shape = RoundedCornerShape(16.dp)
                clip = true
            }
            .background(Color.White, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        content()
    }
}
```

---

## 9. Project Walkthrough — ProfileCard UI Kit

### What the project actually is

You're building a **small, self-contained component library** (not a full screen or feature) — four reusable pieces that could plausibly be dropped into a real social app:

1. **CompactCard** — avatar + name + one-line bio, laid out with `Row` + `weight` (Concept #1, #3).
2. **ExpandedCard** — a taller variant with more bio text, using `Column`, `Spacer`, and `MeasureCard` (`SubcomposeLayout`) so it gracefully scrolls if the bio is long instead of overflowing (Concept #7).
3. **StoryRing** — the circular avatar-with-gradient-ring-and-online-badge, combining `ConstraintLayout` (badge placement, Concept #5) with `graphicsLayer` (rotating gradient ring, Concept #8).
4. **PhotoGrid** — a Pinterest-style staggered grid of thumbnails using your own `StaggeredGrid` custom `Layout` (Concept #6).

And a **"UI Kit" screen** that composes all four into one scrollable showcase — this is the "integration" deliverable that proves each component works independently and together.

### Why this project maps well to the concepts

| Concept | Where it's used in the project |
|---|---|
| Column/Row/Box | Skeleton of every card |
| Modifier order | Card background/padding/border layering, click ripple boundaries |
| weight | Avatar vs. bio text split in CompactCard |
| Spacer | Gaps between avatar/text, and pushing trailing icons |
| ConstraintLayout | Badge pinned to avatar's corner |
| Custom Layout | StaggeredGrid for the photo thumbnails |
| SubcomposeLayout | ExpandedCard auto-decides scroll vs. no-scroll based on measured bio height |
| graphicsLayer | StoryRing rotation animation + card shadow/elevation |

### Suggested build order

```
Step 1: CompactCard (Row + weight + Spacer)          — warms up basics
Step 2: StoryRing (ConstraintLayout + graphicsLayer)  — isolated, testable alone
Step 3: PhotoGrid (custom Layout — StaggeredGrid)     — hardest measure logic
Step 4: ExpandedCard (SubcomposeLayout)               — needs Step 1's pieces
Step 5: UiKitScreen — LazyColumn assembling all four, each in its own section
Step 6: Extract into a Gradle module (":feature:profilecard-uikit")
         so it's independently reusable/importable, per the deliverable
```

### Architectural note on "export as a module"

Since you're already using a modular Gradle setup for Nimbus, treat this the same way: create a new Android library module (`profilecard-uikit`), move these composables + any small preview-only sample data into it, expose only the public composables (`CompactProfileCard`, `ExpandedProfileCard`, `StoryRing`, `PhotoGrid`) — keep `StaggeredGrid` and `MeasureCard` `internal` unless you want them reusable elsewhere too. Add `@Preview`-annotated composables inside the module for isolated component preview in Android Studio without needing to run the full app.

---

## 10. Full Code Example — Minimal UI Kit Screen

```kotlin
@Composable
fun ProfileCardUiKitScreen() {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Text("Compact Card", style = MaterialTheme.typography.titleMedium)
            ShadowedProfileCard {
                CompactProfileRow(
                    name = "Aisha Khan",
                    bio = "Android dev @ CareCloud. Building Nimbus 🚀",
                    avatarUrl = "https://example.com/avatar1.jpg"
                )
            }
        }

        item {
            Text("Story Ring", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RotatingStoryRing(isActive = true)
                RotatingStoryRing(isActive = false)
            }
        }

        item {
            Text("Avatar + Badge", style = MaterialTheme.typography.titleMedium)
            AvatarWithOnlineBadge(
                avatarUrl = "https://example.com/avatar2.jpg",
                isOnline = true
            )
        }

        item {
            Text("Expanded Card (adaptive)", style = MaterialTheme.typography.titleMedium)
            ShadowedProfileCard {
                MeasureCard(maxCollapsedHeight = 150.dp) {
                    Column {
                        Text("Bilal Ahmed", fontWeight = FontWeight.Bold)
                        Text(
                            "Backend engineer with a growing interest in event-driven " +
                            "systems, Kafka, and distributed architectures. Currently " +
                            "exploring Spring Boot microservices and Kotlin backend work. " +
                            "This bio is intentionally long to trigger the scroll fallback " +
                            "inside MeasureCard's SubcomposeLayout logic."
                        )
                    }
                }
            }
        }

        item {
            Text("Photo Grid", style = MaterialTheme.typography.titleMedium)
            StaggeredGrid(columns = 3) {
                repeat(9) { index ->
                    Box(
                        modifier = Modifier
                            .padding(2.dp)
                            .height((80 + (index % 3) * 40).dp)
                            .fillMaxWidth()
                            .background(Color(0xFFB0BEC5))
                    )
                }
            }
        }
    }
}
```

---

## 11. Common Pitfalls

| Pitfall | Symptom | Fix |
|---|---|---|
| `weight()` used outside `RowScope`/`ColumnScope` | Compile error: unresolved reference `weight` | Only call inside a `Row { }` or `Column { }` lambda body |
| `background()` before `padding()` when you *wanted* tight background | Visible colored "border" you didn't expect | Swap order: `padding()` then `background()` |
| Forgetting `fillMaxWidth()`/size constraints before `weight()` siblings | `IllegalStateException: Incompatible measurement` or infinite width crash | Make sure the parent Row/Column itself has bounded width (e.g. from `fillMaxWidth()`) |
| Using `ConstraintLayout` refs across recomposition scopes incorrectly | Crash: "Could not find id" or badge not appearing | Create refs with `createRefs()`/`createRefFor` *inside* the same `ConstraintLayout` composable call, not hoisted elsewhere |
| Measuring the same child twice in a custom `Layout` | Runtime crash: `Measurable has already been measured` | Use `SubcomposeLayout` instead if you need multi-pass measurement |
| Forgetting `.roundToPx()` when converting `Dp` inside a `Layout`/`SubcomposeLayout` measure block | Sizes off by orders of magnitude, layout looks broken | Always convert `Dp` → `Int` px via `Dp.roundToPx()` inside `Density`-scoped lambdas |
| Animating via `Modifier.rotate(angle)` (state-value form) instead of `graphicsLayer { rotationZ = angle }` | Unnecessary recomposition on every animation frame → jank on lower-end devices | Read animated values **inside** the `graphicsLayer{}` lambda so only the draw phase re-runs |
| `StaggeredGrid` not respecting `constraints.maxWidth` | Grid overflows screen width | Explicitly compute `columnWidth = constraints.maxWidth / columns` and pass fixed-width constraints to children |
| `SubcomposeLayout` "probe" pass never discarded, doubling composition cost | Performance regression / extra recompositions logged | Only call `subcompose("probe")` once per measure pass; avoid unnecessary re-invocation of the same slot ID across recompositions |
| Missing `key` when subcomposing dynamic content | Stale slot content or crashes when list size changes | Use stable, unique `slotId` values in every `subcompose()` call |

---

## 12. Interview Q&A

**Q1: What's the fundamental difference between how `Row`/`Column` size themselves and how `Box` sizes itself?**
A: `Row`/`Column` size along their main axis based on the sum (Row=width, Column=height) of children's measured sizes (unless constrained otherwise, e.g. `fillMaxWidth`), while cross-axis size is the max of children. `Box` sizes itself to the **largest child** on both axes simultaneously, since children stack rather than flow.

**Q2: Why does modifier order matter in Compose but property order doesn't matter in the old View system (e.g. XML attributes)?**
A: XML attributes are just declarative properties assigned to a single `View` object — order is irrelevant because they all mutate the same object. Compose `Modifier`s form a linked chain of wrapping elements, each applied to the *result* of the previous one — so it's fundamentally a nested-composition/pipeline model, not a flat property bag. Each modifier only receives the constraints/canvas handed down by the modifier before it in the chain.

**Q3: Why can't you use `Modifier.weight()` on a `Box`'s children?**
A: `weight()` is defined as an extension function on `RowScope`/`ColumnScope`, not on the base `Modifier` interface. This is a deliberate Kotlin scoping trick — since `weight()` only makes sense along a linear main axis, and `Box` has no main axis (children just stack), the compiler prevents you from using it there by construction (scoped extension functions).

**Q4: When would you reach for `ConstraintLayout` instead of nested `Box` + `Modifier.align()` / `.offset()`?**
A: When you have several children whose positions are relative to *each other* (not just relative to the parent), especially for precise overlap/anchoring like badges, or when you need to swap between different constraint arrangements (e.g., portrait vs landscape) via `ConstraintSet` without restructuring the composable tree. Deep Box nesting for such cases adds extra measure/layout passes and becomes hard to read; ConstraintLayout solves it in one flattened pass.

**Q5: Walk through what happens during the measure phase of a custom `Layout` composable.**
A: Compose calls your `MeasurePolicy` lambda with a list of `Measurable`s (unmeasured children) and the incoming `Constraints`. You call `.measure(constraints)` on each — once, since single-pass measurement is enforced — getting back `Placeable`s that report actual size. You then call `layout(width, height) { ... }` to declare your own composable's final size and, inside that trailing lambda, call `.placeRelative(x, y)` on each `Placeable` to position them.

**Q6: Why does `SubcomposeLayout` exist if `Layout` already lets you measure children?**
A: `Layout` only allows a **single measurement pass** per child, enforced by Compose. `SubcomposeLayout` lets you subcompose (and measure) content in **multiple passes**, keyed by slot IDs, so you can measure once to make a decision (e.g., "is this content taller than X?"), then subcompose *different* content based on that result. It trades some performance for this "measure-then-decide" flexibility. `BoxWithConstraints` is a built-in example built on top of it.

**Q7: Why is `Modifier.graphicsLayer { }` (lambda form) more performant for animations than passing static values to `Modifier.graphicsLayer(rotationZ = angle)` or convenience modifiers like `Modifier.rotate(angle)`?**
A: When you read an animated value **inside** the lambda, Compose can skip recomposition and even re-layout on every frame — only the **draw phase** re-executes to apply the new transform on the GPU layer. If you instead pass the animated value as a parameter directly, Compose treats it as a changed input to the modifier, potentially triggering a broader recomposition each frame, which is more expensive, especially for high-frequency animations like infinite rotations.

**Q8: What's a `Placeable` and why can a Placeable's `.place()` calls only happen inside the `layout { }` trailing lambda, not during measurement?**
A: A `Placeable` is the result of measuring a `Measurable` — it knows its own width/height but hasn't been positioned yet. Compose separates **measurement** (bottom-up: children report their sizes) from **placement** (top-down: parent decides x/y for each child) as two distinct phases so that a parent can only assign positions after it knows the sizes of *all* its children (needed e.g. to compute centering, or in the StaggeredGrid case, to know which column is currently shortest before placing the next child).

**Q9: In the StaggeredGrid example, why do we give every child the *same fixed width* but let height vary?**
A: The staggered/masonry effect only concerns the *vertical* dimension — columns must stay aligned to a grid horizontally (fixed `columnWidth = constraints.maxWidth / columns`), while each child's *height* is left to its natural/aspect-ratio-driven size so the layout can stagger. If width also varied, you couldn't cleanly assign items to columns.

**Q10: What's the risk of overusing `SubcomposeLayout` throughout an app instead of regular `Layout`?**
A: Since it allows multiple composition/measurement passes, overusing it can noticeably hurt performance — every extra `subcompose()` call is a real (if often cheap) composition pass. It should be reserved for genuine "must measure before deciding what to compose" scenarios (adaptive/responsive layouts), not used as a default replacement for `Layout` or `Column`/`Row`/`Box`.

---

## Quick Reference Cheat Sheet

```
Column / Row / Box         → 3 foundational layout composables
Modifier chain              → ordered, outer→inner wrapping; order changes behavior
weight()                    → RowScope/ColumnScope only; splits REMAINING space
Spacer                      → invisible, sized gap; Modifier.weight(1f) = flexible push
ConstraintLayout            → relative/anchored positioning, single measure pass
Layout { }                  → custom measure+place logic, single-pass per child
SubcomposeLayout            → multi-pass measure-then-decide, keyed by slotId
graphicsLayer { }           → GPU draw-phase transforms (rotate/scale/alpha/shadow),
                               skips recomposition when animated value read inside lambda
```
