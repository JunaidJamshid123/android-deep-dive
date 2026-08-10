# SECTION 2.2 — Fragments

**Concepts covered:** fragment lifecycle, transactions, communication, Navigation Component, DialogFragment, BottomSheetDialogFragment

**Project:** RecipeBook App (Views Version) — a list fragment shows recipes, tapping one opens a detail fragment, a bottom sheet filters by cuisine/time/difficulty, and a dialog confirms deletion.

---

## 1. Theory: What is a Fragment?

A `Fragment` is a reusable, self-contained piece of UI and behavior that lives **inside** a host — almost always an `Activity` (or another Fragment, for nesting). Fragments have their own lifecycle, their own layout, and their own logic, but they cannot exist independently on screen — they're always hosted.

### Why Fragments exist

- **Modularity:** build a screen out of composable pieces (a list pane + a detail pane) that can be combined differently on phones vs tablets.
- **Reuse:** the same `RecipeListFragment` can be reused inside different host Activities.
- **Navigation within a single Activity:** modern Android apps typically use a **single-Activity architecture**, where one Activity hosts many Fragments and the Navigation Component swaps them in and out — this is exactly the pattern this project builds.

```
┌───────────────────────────────────────────────┐
│                MainActivity                     │
│  ┌─────────────────────────────────────────┐  │
│  │      FragmentContainerView (host)         │  │
│  │  ┌───────────────────────────────────┐   │  │
│  │  │   currently displayed Fragment      │   │  │
│  │  │   (RecipeListFragment, then         │   │  │
│  │  │    RecipeDetailFragment, etc.)       │   │  │
│  │  └───────────────────────────────────┘   │  │
│  └─────────────────────────────────────────┘  │
└───────────────────────────────────────────────┘
```

---

## 2. Fragment Lifecycle — Full Diagram

A Fragment's lifecycle is **richer** than an Activity's because it has to account for two extra things an Activity doesn't have: a separate **View lifecycle** (the Fragment object can exist before/after its View does) and **attachment/detachment** from its host.

```
                        ┌───────────────┐
                        │  onAttach()    │  ← Fragment linked to its host
                        └───────┬───────┘     Context (Activity)
                                │
                                ▼
                        ┌───────────────┐
                        │  onCreate()    │  ← Fragment instance created,
                        └───────┬───────┘     no view yet (init non-UI state)
                                │
                                ▼
                    ┌───────────────────────┐
                    │  onCreateView()        │  ← inflate & RETURN the layout
                    └───────────┬───────────┘
                                │
                                ▼
                    ┌───────────────────────┐
                    │  onViewCreated()       │  ← view exists now — safe to
                    └───────────┬───────────┘     bind widgets, set listeners
                                │
                                ▼
                    ┌───────────────────────┐
                    │  onViewStateRestored() │  ← saved view state re-applied
                    └───────────┬───────────┘
                                │
                                ▼
                        ┌───────────────┐
              ┌─────────┤  onStart()     │
              │         └───────┬───────┘
              │                 ▼
              │         ┌───────────────┐
              │         │  onResume()    │  ← interactive, RUNNING
              │         └───────┬───────┘
              │                 ▼
              │         ┌───────────────┐
              └─────────┤  onPause()     │
                        └───────┬───────┘
                                ▼
                        ┌───────────────┐
                        │  onStop()      │
                        └───────┬───────┘
                                ▼
                    ┌───────────────────────┐
                    │  onDestroyView()       │  ← VIEW is destroyed, but the
                    └───────────┬───────────┘     Fragment INSTANCE may live on
                                │                   (e.g. on the back stack!)
                                ▼
                        ┌───────────────┐
                        │  onDestroy()   │  ← Fragment instance itself is
                        └───────┬───────┘     being discarded
                                ▼
                        ┌───────────────┐
                        │  onDetach()    │  ← unlinked from host Context
                        └───────────────┘
```

### The critical distinction: Fragment lifecycle vs View lifecycle

This is the single most important — and most tested — Fragment concept.

```
Fragment INSTANCE lifetime:      onAttach ───────────────────────────► onDetach
                                              (can span MULTIPLE view creations
                                               if the fragment sits on the back
                                               stack and its view is torn down)

Fragment VIEW lifetime:                onCreateView ──► onDestroyView
                                        (recreated every time the fragment
                                         becomes visible again after being
                                         replaced-but-backstacked)
```

**Why it matters:** if a `RecipeListFragment` is replaced by `RecipeDetailFragment` via a transaction that's added to the back stack, `RecipeListFragment`'s **view** is destroyed (`onDestroyView`) but the **Fragment object itself stays alive** in memory (no `onDestroy`/`onDetach`) so it can be brought back cheaply when the user presses Back. Any `View` references (binding, RecyclerView adapter, listeners) held past `onDestroyView()` **leak the old view** — this is the #1 Fragment bug beginners hit.

```kotlin
class RecipeListFragment : Fragment(R.layout.fragment_recipe_list) {

    private var _binding: FragmentRecipeListBinding? = null
    private val binding get() = _binding!!   // only valid between onCreateView/onDestroyView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecipeListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerView.adapter = RecipeAdapter(recipes) { recipe -> onRecipeClicked(recipe) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null   // ← CRITICAL: release the view reference to avoid leaking it
    }
}
```

---

## 3. Fragment Lifecycle vs Activity Lifecycle — Side by Side

This is exactly what Project 5 asks you to log and visualize.

```
 ACTIVITY (host)                         FRAGMENT (hosted)
 ───────────────                         ─────────────────
 onCreate()          ─────────────────►  onAttach()
                                          onCreate()
                                          onCreateView()
                                          onViewCreated()
 onStart()           ─────────────────►  onStart()
 onResume()          ─────────────────►  onResume()

                     [ Activity RUNNING; Fragment RUNNING ]

 onPause()           ─────────────────►  onPause()
 onStop()            ─────────────────►  onStop()
                                          onDestroyView()   ← if fragment removed
                                                                or back-stacked
 onDestroy()         ─────────────────►  onDestroy()
                                          onDetach()
```

**Key rule:** the Activity's callbacks always **bracket** the Fragment's. The Activity is created before any Fragment inside it, and destroyed after. When you rotate the device, you'll see in your side-by-side timeline: `Activity.onPause → Fragment.onPause → Activity.onStop → Fragment.onStop → Fragment.onDestroyView → ... → Activity.onDestroy → Fragment.onDestroy → Fragment.onDetach`, then the whole sequence again in reverse (attach→create→...) for the recreated pair.

```kotlin
// Reusable base Fragment that logs into the same shared timeline from Section 2.1
abstract class BaseLoggingFragment(layoutId: Int) : Fragment(layoutId) {
    private val tag get() = this::class.simpleName ?: "Fragment"

    override fun onAttach(context: Context) {
        super.onAttach(context); LifecycleTimelineStore.log(tag, "onAttach")
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); LifecycleTimelineStore.log(tag, "onCreate")
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        LifecycleTimelineStore.log(tag, "onCreateView")
        return super.onCreateView(inflater, container, savedInstanceState)
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState); LifecycleTimelineStore.log(tag, "onViewCreated")
    }
    override fun onStart() { super.onStart(); LifecycleTimelineStore.log(tag, "onStart") }
    override fun onResume() { super.onResume(); LifecycleTimelineStore.log(tag, "onResume") }
    override fun onPause() { LifecycleTimelineStore.log(tag, "onPause"); super.onPause() }
    override fun onStop() { LifecycleTimelineStore.log(tag, "onStop"); super.onStop() }
    override fun onDestroyView() {
        LifecycleTimelineStore.log(tag, "onDestroyView"); super.onDestroyView()
    }
    override fun onDestroy() { LifecycleTimelineStore.log(tag, "onDestroy"); super.onDestroy() }
    override fun onDetach() { LifecycleTimelineStore.log(tag, "onDetach"); super.onDetach() }
}
```

---

## 4. Fragment Transactions: `add`, `replace`, `addToBackStack`

### Theory

A **FragmentTransaction** is a set of operations (add/remove/replace/show/hide) on the `FragmentManager`'s container, executed atomically. `FragmentManager` maintains its own **back stack**, separate from (but layered underneath) the Activity's back stack.

```
add() — stacks fragments on top of each other in the SAME container
────────────────────────────────────────────────────────────────────
  Container: [ RecipeListFragment ]
       │  add(FilterFragment)
       ▼
  Container: [ RecipeListFragment, FilterFragment ]   ← BOTH exist, both
                                                          may overlap visually
                                                          unless one hides


replace() — removes existing fragment(s) in the container, adds the new one
──────────────────────────────────────────────────────────────────────────
  Container: [ RecipeListFragment ]
       │  replace(container, RecipeDetailFragment)
       ▼
  Container: [ RecipeDetailFragment ]     ← RecipeListFragment's VIEW is
                                              destroyed (onDestroyView)


addToBackStack("tag") — records the transaction so Back can reverse it
───────────────────────────────────────────────────────────────────────
  FragmentManager back stack:  [ ] (empty initially)
       │ replace(..., DetailFragment).addToBackStack("to_detail")
       ▼
  FragmentManager back stack:  [ "to_detail" ]
       │ user presses Back
       ▼
  Transaction is REVERSED: DetailFragment removed, ListFragment's view
  is recreated (onCreateView runs again!) — NOT the same as onResume
  from a simple stop/start; the whole view lifecycle replays.
```

### Code: List → Detail transaction

```kotlin
// Inside RecipeListFragment, on a recipe click
private fun onRecipeClicked(recipe: Recipe) {
    parentFragmentManager.commit {
        setReorderingAllowed(true)
        replace(R.id.fragment_container, RecipeDetailFragment.newInstance(recipe.id))
        addToBackStack("list_to_detail")   // enables system Back to return to the list
    }
}
```

```kotlin
// Manual add()/hide()/show() — used for the filter panel that should sit ON TOP
// of the list without destroying the list's view or scroll position
private fun showFilterPanel() {
    parentFragmentManager.commit {
        setReorderingAllowed(true)
        add(R.id.fragment_container, FilterFragment(), "filter")
        hide(recipeListFragmentInstance)   // list survives, just hidden — state intact
        addToBackStack("show_filter")
    }
}
```

**`add`+`hide`/`show` vs `replace`:** `replace` is simpler but always tears down and rebuilds the view of whatever was there — losing scroll position and any un-persisted view state unless you save it. `add`+`hide`/`show` keeps the previous fragment's view alive underneath (no `onDestroyView`), which is why it's the better choice for a filter panel the user expects to snap back to exactly where they left off.

---

## 5. Fragment Communication: Shared `ViewModel`

### Theory — the old way vs the modern way

```
OLD (fragile) way — direct fragment-to-fragment callbacks
────────────────────────────────────────────────────────
  ListFragment ──implements interface, cast to──► requireActivity()
                    "callback" pattern                    │
                                                            ▼
                                                    DetailFragment
  Tightly coupled, easy to NPE if the other fragment isn't attached yet.


MODERN way — a ViewModel scoped to the shared Activity
────────────────────────────────────────────────────────
                     ┌─────────────────────────┐
                     │   MainActivity            │
                     │   (ViewModelStoreOwner)   │
                     └────────────┬──────────────┘
                                  │ owns
                                  ▼
                     ┌─────────────────────────┐
                     │   RecipeSharedViewModel   │  ← ONE instance, survives
                     └──────┬─────────────┬──────┘     fragment transactions
                            │             │              and rotation
              activityViewModels()  activityViewModels()
                            │             │
                   ┌────────▼───┐   ┌─────▼──────────┐
                   │ ListFragment│   │ DetailFragment  │
                   └────────────┘   └────────────────┘

  Neither fragment references the other directly — both only know about
  the shared ViewModel. This is how the filter selection flows from the
  BottomSheetDialogFragment back into the list too.
```

### Code

```kotlin
// RecipeSharedViewModel.kt — scoped to the Activity, shared by all fragments
class RecipeSharedViewModel : ViewModel() {
    private val _selectedRecipeId = MutableStateFlow<String?>(null)
    val selectedRecipeId: StateFlow<String?> = _selectedRecipeId

    private val _filters = MutableStateFlow(RecipeFilters())
    val filters: StateFlow<RecipeFilters> = _filters

    val filteredRecipes: StateFlow<List<Recipe>> = combine(allRecipesFlow, _filters) { recipes, filters ->
        recipes.filter { it.matches(filters) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectRecipe(id: String) { _selectedRecipeId.value = id }
    fun applyFilters(filters: RecipeFilters) { _filters.value = filters }
}
```

```kotlin
class RecipeListFragment : BaseLoggingFragment(R.layout.fragment_recipe_list) {
    // scoped to the Activity — same instance every fragment in this Activity gets
    private val sharedViewModel: RecipeSharedViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val adapter = RecipeAdapter { recipe -> sharedViewModel.selectRecipe(recipe.id); navigateToDetail() }
        binding.recyclerView.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.filteredRecipes.collect { adapter.submitList(it) }
            }
        }
    }
}
```

```kotlin
class RecipeDetailFragment : BaseLoggingFragment(R.layout.fragment_recipe_detail) {
    private val sharedViewModel: RecipeSharedViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.selectedRecipeId.filterNotNull().collect { id ->
                    bindRecipe(repository.getRecipeById(id))
                }
            }
        }
    }
}
```

> **Important:** always collect flows using `viewLifecycleOwner.lifecycleScope`, **not** `lifecycleScope` (the Fragment's own scope) inside `onViewCreated`. The Fragment instance can outlive its view (back stack case from Section 2), so using the Fragment's own scope risks updating views that no longer exist. `viewLifecycleOwner` is scoped exactly to the current view's lifetime.

---

## 6. Navigation Component: NavGraph

### Theory

The Navigation Component replaces manual `FragmentTransaction` calls with a **declarative graph** (`nav_graph.xml`) describing every destination and the actions that connect them. A single `NavHostFragment` swaps destinations in and out, automatically manages the back stack, and integrates with the system Back button, deep links, and the app bar.

```
                        nav_graph.xml
        ┌──────────────────────────────────────────────┐
        │                                                 │
        │   ┌──────────────┐  action_list_to_detail        │
        │   │ RecipeList    │ ─────────────────────────►  │
        │   │ Fragment       │ ◄─────────────────────────  │
        │   │ (startDest.)   │      system Back              │
        │   └──────┬────────┘                               │
        │          │ action_list_to_filter (bottom sheet)     │
        │          ▼                                          │
        │   ┌──────────────┐                                  │
        │   │ FilterFragment│  (dialog destination)             │
        │   └──────────────┘                                  │
        │                                                 │
        │   ┌──────────────┐  action_detail_to_edit          │
        │   │ RecipeDetail  │ ─────────────────────────►  │
        │   │ Fragment       │                              │
        │   └──────┬────────┘                              │
        │          ▼                                        │
        │   ┌──────────────┐                                │
        │   │ RecipeEdit    │                                │
        │   │ Fragment       │                                │
        │   └──────────────┘                                │
        └──────────────────────────────────────────────┘

                    MainActivity
        ┌──────────────────────────────────────┐
        │  ┌──────────────────────────────────┐  │
        │  │  NavHostFragment                    │  │
        │  │  (hosts whichever destination is      │  │
        │  │   currently active, driven by the      │  │
        │  │   graph above)                          │  │
        │  └──────────────────────────────────┘  │
        └──────────────────────────────────────────┘
```

### XML

```xml
<!-- res/navigation/nav_graph.xml -->
<navigation xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/nav_graph"
    app:startDestination="@id/recipeListFragment">

    <fragment
        android:id="@+id/recipeListFragment"
        android:name="com.example.recipebook.RecipeListFragment"
        android:label="Recipes">
        <action
            android:id="@+id/action_list_to_detail"
            app:destination="@id/recipeDetailFragment" />
    </fragment>

    <fragment
        android:id="@+id/recipeDetailFragment"
        android:name="com.example.recipebook.RecipeDetailFragment"
        android:label="Recipe Detail">
        <argument
            android:name="recipeId"
            app:argType="string" />
        <action
            android:id="@+id/action_detail_to_edit"
            app:destination="@id/recipeEditFragment" />
    </fragment>

    <fragment
        android:id="@+id/recipeEditFragment"
        android:name="com.example.recipebook.RecipeEditFragment"
        android:label="Edit Recipe">
        <argument
            android:name="recipeId"
            app:argType="string" />
    </fragment>

    <dialog
        android:id="@+id/deleteConfirmDialog"
        android:name="com.example.recipebook.DeleteRecipeDialogFragment" />
</navigation>
```

```xml
<!-- activity_main.xml -->
<androidx.fragment.app.FragmentContainerView
    android:id="@+id/nav_host_fragment"
    android:name="androidx.navigation.fragment.NavHostFragment"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    app:defaultNavHost="true"
    app:navGraph="@navigation/nav_graph" />
```

---

## 7. Safe Args: Type-Safe Navigation Arguments

### Theory

Without Safe Args, passing data between destinations means manually building a `Bundle` with string keys — easy to typo a key or pass the wrong type, and it only fails at **runtime**. The Safe Args Gradle plugin reads `nav_graph.xml` and **code-generates** a strongly typed `Directions` class per action and an `Args` class per destination, so mismatches are caught at **compile time**.

```
   nav_graph.xml declares:
     <argument android:name="recipeId" app:argType="string" />

                    │  Safe Args plugin generates at build time
                    ▼
   RecipeListFragmentDirections.actionListToDetail(recipeId: String)
   RecipeDetailFragmentArgs.fromBundle(bundle) -> recipeId: String   (compile-time safe)
```

### Gradle setup

```kotlin
// project-level build.gradle.kts
plugins {
    id("androidx.navigation.safeargs.kotlin") version "2.8.0" apply false
}

// module-level build.gradle.kts
plugins {
    id("androidx.navigation.safeargs.kotlin")
}
```

### Code

```kotlin
// Navigating FROM the list, passing the recipe id
private fun onRecipeClicked(recipe: Recipe) {
    val action = RecipeListFragmentDirections.actionListToDetail(recipeId = recipe.id)
    findNavController().navigate(action)
}
```

```kotlin
// Reading the argument in the detail destination — no manual Bundle key lookups
class RecipeDetailFragment : BaseLoggingFragment(R.layout.fragment_recipe_detail) {
    private val args: RecipeDetailFragmentArgs by navArgs()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadRecipe(args.recipeId)   // type-checked at compile time — no getString("recipeId") typos
    }
}
```

---

## 8. DialogFragment — "Delete recipe?" Confirmation

### Theory

`DialogFragment` is a Fragment that **displays itself as a floating dialog** instead of filling the container view. It gets all the same lifecycle benefits (survives rotation, is managed by `FragmentManager`) that a raw `AlertDialog` does not — a plain `AlertDialog` shown from `onClick` would be silently dismissed and leaked on rotation, since nothing reattaches it to the recreated Activity.

```
        RecipeDetailFragment
                │  parentFragmentManager.commit or show()
                ▼
        ┌─────────────────────────┐
        │  DeleteRecipeDialogFrag  │  ← managed by FragmentManager just
        │  ┌─────────────────┐    │     like any other fragment — survives
        │  │ "Delete recipe?" │    │     rotation, appears above everything
        │  │  [Cancel] [Delete]│    │
        │  └─────────────────┘    │
        └─────────────────────────┘
                │  result communicated back via
                │  Fragment Result API (no direct
                │  reference to the parent needed)
                ▼
        RecipeDetailFragment reacts to result
```

### Code

```kotlin
class DeleteRecipeDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(requireContext())
            .setTitle("Delete recipe?")
            .setMessage("This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                // Fragment Result API — decoupled, no cast to a listener interface needed
                parentFragmentManager.setFragmentResult(
                    "delete_request", bundleOf("confirmed" to true)
                )
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .create()
    }

    companion object {
        const val TAG = "DeleteRecipeDialog"
    }
}
```

```kotlin
// Inside RecipeDetailFragment
private fun setupDeleteListener() {
    parentFragmentManager.setFragmentResultListener("delete_request", viewLifecycleOwner) { _, bundle ->
        if (bundle.getBoolean("confirmed")) {
            sharedViewModel.deleteRecipe(args.recipeId)
            findNavController().popBackStack()
        }
    }
}

deleteButton.setOnClickListener {
    DeleteRecipeDialogFragment().show(parentFragmentManager, DeleteRecipeDialogFragment.TAG)
}
```

> The **Fragment Result API** (`setFragmentResult` / `setFragmentResultListener`) is the modern replacement for defining a callback interface the parent must implement — it decouples the dialog completely from whichever fragment happens to be listening, keyed only by a string request key.

---

## 9. BottomSheetDialogFragment — Filter Panel

### Theory

`BottomSheetDialogFragment` is a specialized `DialogFragment` that slides up from the bottom edge, partially covering the screen, and can be dragged to expand/collapse or dismiss — the standard Android pattern for supplementary controls (filters, share sheets, quick actions) that shouldn't fully interrupt the current screen.

```
   Before tap:                    After tap "Filter":
   ┌─────────────────┐            ┌─────────────────┐
   │                   │            │                   │
   │   Recipe List      │            │   Recipe List      │  ← dimmed, still
   │                   │            │   (dimmed)         │     visible behind
   │                   │            ├─────────────────┤
   │                   │            │  ▬▬  (drag handle) │
   │                   │            │  Cuisine: [Italian▾]│  ← BottomSheetDialog
   │                   │            │  Time: [< 30 min ▾] │     Fragment
   │                   │            │  Difficulty: [Easy▾]│
   │                   │            │      [Apply]        │
   └─────────────────┘            └─────────────────┘
```

### Code

```kotlin
class FilterFragment : BottomSheetDialogFragment() {

    private val sharedViewModel: RecipeSharedViewModel by activityViewModels()
    private var _binding: FragmentFilterBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFilterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val current = sharedViewModel.filters.value
        binding.cuisineSpinner.setSelection(current.cuisineIndex)
        binding.timeSlider.value = current.maxTimeMinutes.toFloat()
        binding.difficultyGroup.check(current.difficultyChipId)

        binding.applyButton.setOnClickListener {
            val newFilters = RecipeFilters(
                cuisineIndex = binding.cuisineSpinner.selectedItemPosition,
                maxTimeMinutes = binding.timeSlider.value.toInt(),
                difficultyChipId = binding.difficultyGroup.checkedChipId
            )
            sharedViewModel.applyFilters(newFilters)
            dismiss()   // closes the bottom sheet; list updates reactively via the shared ViewModel
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
```

```kotlin
// Triggering it from the list screen
filterIconButton.setOnClickListener {
    FilterFragment().show(parentFragmentManager, "filter_sheet")
}
```

> Because `FilterFragment` reads and writes through the same `RecipeSharedViewModel` used by `RecipeListFragment`, no result callback is even needed here — applying filters updates the shared `StateFlow`, and the list (already collecting `filteredRecipes`) updates itself automatically the moment `dismiss()` is called.

---

## 10. Common Pitfalls

| Pitfall | Why it happens | Fix |
|---|---|---|
| Holding a `ViewBinding` reference past `onDestroyView()` | The Fragment instance can survive with its view destroyed (back stack); a stale binding reference leaks the old View tree | Null out the binding in `onDestroyView()`; use a nullable backing property |
| Collecting a Flow with `lifecycleScope` instead of `viewLifecycleOwner.lifecycleScope` inside `onViewCreated` | `lifecycleScope` is tied to the Fragment instance, which can outlive the view; collecting there after `onDestroyView()` touches destroyed views or leaks the collector | Always use `viewLifecycleOwner.lifecycleScope` (or `viewLifecycleOwner.lifecycle.repeatOnLifecycle`) for anything that updates the UI |
| Using `getFragmentManager()`/`activity!!` without null-checks | The Fragment can be in a state where it's detached from its host (e.g., after `onDetach()`, or during a config change window) | Use `requireContext()`, `requireActivity()`, `parentFragmentManager` and check `isAdded` before UI operations triggered from async callbacks |
| Defining a fragment-to-fragment listener interface and casting `getParentFragment()`/`getActivity()` to it | Tightly couples fragments, crashes with `ClassCastException` if the host doesn't implement it, breaks if fragment nesting changes | Use a shared `ViewModel` (`activityViewModels()`) for state, and the Fragment Result API for one-off events like dialog confirmations |
| Passing large/complex objects as Navigation arguments | Arguments are ultimately passed through a `Bundle` (Binder IPC limits apply), and doing so also creates tight coupling between destinations and object shapes | Pass an ID (as this project does with `recipeId`) and look up the full object from a repository/shared ViewModel on the receiving side |
| Calling `replace()` for the filter panel instead of `add()`+`hide()`/`show()` | `replace()` destroys the list fragment's view, discarding scroll position and any transient UI state | Use `add()` + `hide()`/`show()` (or better, a `BottomSheetDialogFragment`, as this project does) when the underlying content should be preserved |
| Forgetting `setReorderingAllowed(true)` on transactions | Without it, overlapping transactions run in strict submission order even when reordering would be more efficient and animate more correctly | Always call `setReorderingAllowed(true)` in `commit {}` blocks (the Kotlin DSL default recommendation) |

---

## 11. Interview Q&A

**Q: What's the difference between a Fragment's lifecycle and its View's lifecycle, and why does it matter?**
> A Fragment *instance* can exist without a View — e.g. when it's on the back stack after being replaced. `onCreateView`/`onDestroyView` bracket the View's lifetime, while `onCreate`/`onDestroy` bracket the Fragment object's lifetime, and the View lifetime can be shorter and can repeat multiple times within one Fragment instance's lifetime. It matters because holding onto View references (bindings, adapters) past `onDestroyView()` leaks the destroyed View tree — you must null them out.

**Q: Why do you use `viewLifecycleOwner` instead of the Fragment itself when collecting a Flow in `onViewCreated`?**
> Because the Fragment instance can outlive its View (e.g. when back-stacked), a coroutine scoped to the Fragment's own lifecycle could still be running and touching UI after the View was destroyed. `viewLifecycleOwner` is scoped exactly to the View's lifetime, so the collection is automatically cancelled at `onDestroyView()`.

**Q: What's the difference between `add()`, `replace()`, and `addToBackStack()` in a FragmentTransaction?**
> `add()` puts a new fragment into a container alongside whatever's already there (both instances exist). `replace()` removes existing fragment(s) from the container first, then adds the new one — the removed fragment's view is destroyed. `addToBackStack()` doesn't perform an operation itself; it records the transaction on the FragmentManager's back stack so pressing Back can reverse it.

**Q: How would you share state between two sibling Fragments without them referencing each other directly?**
> Scope a `ViewModel` to their common host (`by activityViewModels()` in both fragments). Both get the *same* instance because it's stored against the Activity's `ViewModelStore`. State flows both ways through the shared ViewModel's `StateFlow`/`LiveData` properties, and neither fragment needs a reference to, or even knowledge of, the other.

**Q: What problem does the Navigation Component solve that manual FragmentTransactions don't handle well?**
> It gives you a single declarative graph of destinations and actions, automatically manages the back stack and up/back button behavior, integrates deep linking and Safe Args, and centralizes all navigation logic instead of scattering `FragmentManager.commit {}` calls throughout the codebase.

**Q: What does Safe Args actually generate, and what problem does it solve?**
> For each navigation action it generates a `...Directions` class with a strongly typed method matching the action's declared arguments, and for each destination with arguments it generates an `...Args` class to read them back. It replaces manually building/reading a `Bundle` with string keys, turning a runtime `ClassCastException`/typo risk into a compile-time type check.

**Q: Why use `DialogFragment` instead of just building an `AlertDialog` directly in an `onClick` listener?**
> A raw `AlertDialog` shown outside the Fragment/Activity lifecycle system is dismissed and effectively lost on configuration changes like rotation — the app doesn't know to recreate it. `DialogFragment` is managed by `FragmentManager` like any other fragment, so it's automatically recreated across rotation and its lifecycle is tracked properly.

**Q: How does the Fragment Result API improve on defining a listener interface for dialog callbacks?**
> It decouples the dialog from any specific parent type — the dialog just calls `setFragmentResult(key, bundle)`, and whichever fragment is currently listening via `setFragmentResultListener(key, viewLifecycleOwner) { ... }` receives it. No casting `getParentFragment()`/`getActivity()` to an interface, no `ClassCastException` risk, and it naturally respects the listener's lifecycle.

**Q: What makes `BottomSheetDialogFragment` different from a regular `DialogFragment`?**
> It renders as a sheet anchored to the bottom edge of the screen (rather than a centered floating dialog), supports drag-to-expand/collapse and swipe-to-dismiss gestures via `BottomSheetBehavior`, and is the standard Material Design pattern for supplementary, dismissible controls like filters or action sheets.

**Q: If you pass a whole `Recipe` object as a Navigation argument instead of just its ID, what's the downside?**
> It has to go through a `Bundle`, so it must be `Parcelable`/`Serializable` and is subject to Binder IPC size limits — large or complex objects risk `TransactionTooLargeException`. It also couples every destination to that exact object shape and can hand the receiving fragment stale data if the source has since changed. Passing an ID and re-fetching from a repository/shared ViewModel avoids both problems.

---

## 12. Deliverable Checklist

- [ ] `BaseLoggingFragment` logs all Fragment callbacks (`onAttach` → `onDetach`) into the same shared timeline from Section 2.1, shown side by side with Activity callbacks
- [ ] `RecipeListFragment` set as the NavGraph start destination, backed by `RecipeAdapter` + `RecyclerView`
- [ ] List → Detail navigation via a generated Safe Args action (`actionListToDetail(recipeId)`)
- [ ] `RecipeSharedViewModel` (via `activityViewModels()`) shared between list, detail, and filter fragments — no direct fragment-to-fragment references
- [ ] `FilterFragment` implemented as a `BottomSheetDialogFragment`, applying filters through the shared ViewModel
- [ ] `DeleteRecipeDialogFragment` implemented as a `DialogFragment`, using the Fragment Result API to notify the detail screen
- [ ] `_binding = null` pattern applied in every fragment's `onDestroyView()` — verified with back-stack navigation (list → detail → back) that no crashes or leaks occur
- [ ] Rotation test on the detail screen: selected recipe persists via the shared `ViewModel`, no data loss
