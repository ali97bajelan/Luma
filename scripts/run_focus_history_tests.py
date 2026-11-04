#!/usr/bin/env python3
"""Run FI06 pure Kotlin/JVM filter tests without Gradle or downloads."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
HOME_DIR = Path.home()


def cached_jar(pattern: str) -> Path:
    candidates = sorted(
        path for path in (HOME_DIR / '.gradle/caches/modules-2/files-2.1').glob(pattern)
        if not path.name.endswith(('-sources.jar', '-javadoc.jar'))
    )
    if not candidates:
        raise SystemExit(f'Required local dependency missing: {pattern}; nothing downloaded.')
    return candidates[-1]


def main() -> None:
    compiler = shutil.which('kotlinc')
    if not compiler:
        candidates = sorted((HOME_DIR / '.local/share/JetBrains/Toolbox/apps').glob(
            '*/plugins/Kotlin/kotlinc/bin/kotlinc'
        ))
        compiler = str(candidates[-1]) if candidates else None
    if not compiler:
        raise SystemExit('Local kotlinc not found; nothing installed.')
    kotlin_lib = Path(compiler).resolve().parent.parent / 'lib'
    java = shutil.which('java')
    if not java:
        java_candidate = kotlin_lib.parents[3] / 'jbr/bin/java'
        java = str(java_candidate) if java_candidate.is_file() else None
    if not java:
        raise SystemExit('Local Java not found; nothing installed.')

    junit = cached_jar('junit/junit/4.13.2/*/junit-4.13.2.jar')
    hamcrest = cached_jar('org.hamcrest/hamcrest-core/*/*/hamcrest-core-*.jar')
    coroutines = kotlin_lib / 'kotlinx-coroutines-core-jvm.jar'
    if not coroutines.is_file():
        raise SystemExit('Bundled coroutines jar missing; nothing downloaded.')

    domain = ROOT / 'core/domain/src/main/kotlin/com/luma/lifeos/domain'
    domain_tests = ROOT / 'core/domain/src/test/kotlin/com/luma/lifeos/domain/focus'
    data = ROOT / 'core/data/src/main/kotlin/com/luma/lifeos/data/focus/FocusHistoryFilter.kt'
    data_test = ROOT / 'core/data/src/test/kotlin/com/luma/lifeos/data/focus/FocusHistoryFilterTest.kt'
    app = ROOT / 'app/src/main/java/com/luma/lifeos/ui/focus/FocusHistoryFilterState.kt'
    app_state = ROOT / 'app/src/main/java/com/luma/lifeos/ui/focus/FocusHistoryUiState.kt'
    app_test = ROOT / 'app/src/test/java/com/luma/lifeos/ui/focus/FocusHistoryFilterStateTest.kt'
    sources = sorted((domain / 'focus').glob('*.kt')) + [domain / 'time/TimeRange.kt']
    sources += sorted((domain / 'model').glob('*.kt')) + [data, app, app_state]
    tests = sorted(domain_tests.glob('*Test.kt')) + [data_test, app_test]
    classpath = os.pathsep.join(map(str, [junit, hamcrest, coroutines]))
    environment = os.environ.copy()
    environment['JAVA_HOME'] = str(Path(java).resolve().parent.parent)
    with tempfile.TemporaryDirectory(prefix='luma-focus-history-jvm-') as output:
        print(f'Compiling {len(sources)} production files and {len(tests)} test files.', flush=True)
        subprocess.run([
            compiler, *map(str, sources + tests), '-jvm-target', '17',
            '-classpath', classpath, '-d', output,
        ], cwd=ROOT, env=environment, check=True)
        runtime_cp = os.pathsep.join([output, classpath, str(kotlin_lib / '*')])
        test_classes = [
            f'com.luma.lifeos.domain.focus.{test.stem}' for test in sorted(domain_tests.glob('*Test.kt'))
        ]
        test_classes += [
            'com.luma.lifeos.data.focus.FocusHistoryFilterTest',
            'com.luma.lifeos.ui.focus.FocusHistoryFilterStateTest',
        ]
        subprocess.run([java, '-cp', runtime_cp, 'org.junit.runner.JUnitCore', *test_classes],
                       cwd=ROOT, env=environment, check=True)


if __name__ == '__main__':
    main()
