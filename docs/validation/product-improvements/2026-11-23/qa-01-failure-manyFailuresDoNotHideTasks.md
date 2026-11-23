# QA-01 failure case — `manyFailuresDoNotHideTasks`

**Status:** FAIL on candidate `072f43d7f2fd37377cd6664a3083de43e505f26f`  
**Environment:** `Medium_Phone_API_36.1`, Android 16 / API 36, 1080×2400 px at 420 dpi, portrait system configuration; the test itself overrides the content to 640×320 dp landscape with font scale 2.0.  
**Fixture:** synthetic Todo UI state with 12 terminal conflict recovery cards and one visible open task.

## Exact reproduction

Run from the repository root:

```sh
bash gradlew :app:connectedProductionDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.todo.TodoAccessibilitySemanticsTest#manyFailuresDoNotHideTasks \
  --console=plain
```

The test implementation is in [TodoAccessibilityTest.kt](../../../app/src/androidTest/java/com/luma/lifeos/todo/TodoAccessibilityTest.kt#L257). It scrolls `todo_list_rows` to index 11, then expects the twelfth recovery card, tagged `todo_row_error_c72dd6c6-e0c2-3df7-b08d-94cf0afbeca5`, to be displayed. The assertion at line 288 fails:

```
java.lang.AssertionError: Assert failed: The component with TestTag = 'todo_row_error_c72dd6c6-e0c2-3df7-b08d-94cf0afbeca5' is not displayed!
at com.luma.lifeos.todo.TodoAccessibilitySemanticsTest.manyFailuresDoNotHideTasks(TodoAccessibilityTest.kt:288)
```

Gradle exit code: `1`. This run completed as an assertion failure; it did not crash the instrumentation process. Root cause remains untriaged. Keep this test failing until the recovery-card visibility expectation is satisfied and the exact method passes on the same layout.

## Evidence

- Preserved JUnit result: [QA-01-TodoAccessibilitySemanticsTest-manyFailuresDoNotHideTasks.junit.xml](qa-01-TodoAccessibilitySemanticsTest-manyFailuresDoNotHideTasks.junit.xml)
- Gradle log: [/home/bajelan/.android/studio/agent/conversations/Luma-fc9658513f7c6e6667848c164f3350/126b49d2-ad34-4c97-961c-e43948f9af4b/session/agent/shell-logs/shell-207.log](/home/bajelan/.android/studio/agent/conversations/Luma-fc9658513f7c6e6667848c164f3350/126b49d2-ad34-4c97-961c-e43948f9af4b/session/agent/shell-logs/shell-207.log)
