# QA-01 Todo recovery-list failure case — `manyFailuresDoNotHideTasks`

**Current status:** RESOLVED in the fix worktree. The exact connected regression now passes 1/1 on API 36 after the list layout fix.

**Historical failure:** candidate `072f43d7f2fd37377cd6664a3083de43e505f26f` failed the assertion recorded below. The original JUnit XML is retained as failure evidence.

**Environment:** `Medium_Phone_API_36.1`, Android 16 / API 36, 1080×2400 px at 420 dpi, portrait system configuration; test content is overridden to 640×320 dp landscape with font scale 2.0.

**Fixture:** synthetic Todo UI state with 12 terminal conflict recovery cards and one visible open task.

## Original failure

The original implementation placed the search and filter controls in a fixed column above a weighted `LazyColumn`. At the short, large-text viewport those controls consumed the available height, leaving no usable list viewport.

The test implementation is in [TodoAccessibilityTest.kt](../../../app/src/androidTest/java/com/luma/lifeos/todo/TodoAccessibilityTest.kt). The failing assertion expected the twelfth recovery card, tagged `todo_row_error_c72dd6c6-e0c2-3df7-b08d-94cf0afbeca5`, to be displayed after scrolling to item index 11:

```
java.lang.AssertionError: Assert failed: The component with TestTag = 'todo_row_error_c72dd6c6-e0c2-3df7-b08d-94cf0afbeca5' is not displayed!
at com.luma.lifeos.todo.TodoAccessibilitySemanticsTest.manyFailuresDoNotHideTasks(TodoAccessibilityTest.kt:288)
```

The original run exited with code 1 and completed as an assertion failure, not an instrumentation crash.

## Fix and verification

The Todo search, filters, count and loading feedback now live in the same scrollable `LazyColumn` as recovery and task rows. When the list has no data yet, the controls retain their separate scrollable fallback and the saved list state remains uncomposed. Focus restoration indices include the controls item. The existing regression now scrolls to stable recovery and task tags instead of relying on fixed item indices.

Exact rerun on base revision `1e63510e1de0c6747f149723af1ae2b2d58a2350` with the source/test fix in the working tree:

```sh
./gradlew :app:connectedProductionDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.todo.TodoAccessibilitySemanticsTest#manyFailuresDoNotHideTasks
```

**Result:** PASS, 1/1 on `Medium_Phone_API_36.1` (Android 16 / API 36). The test still renders at 640×320 dp and font scale 2.0; both the final recovery card and visible task action are reachable.

## Evidence

- Historical failing run: [original JUnit XML](qa-01-TodoAccessibilitySemanticsTest-manyFailuresDoNotHideTasks.junit.xml)
- Passing fix rerun: [passing JUnit XML](qa-01-TodoAccessibilitySemanticsTest-manyFailuresDoNotHideTasks-rerun.junit.xml)
