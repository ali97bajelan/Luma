#!/usr/bin/env python3
"""Run the pure FI05 setup reducer tests with the locally installed Kotlin/JUnit tools."""
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
    domain = ROOT / 'core/domain/src/main/kotlin/com/luma/lifeos/domain'
    app = ROOT / 'app/src/main/java/com/luma/lifeos/ui/focus'
    tests = ROOT / 'app/src/test/java/com/luma/lifeos/ui/focus'
    sources = (
        sorted((domain / 'focus').glob('*.kt'))
        + [domain / 'time/TimeRange.kt']
        + sorted((domain / 'model').glob('*.kt'))
        + [app / 'FocusSetupState.kt', app / 'FocusInsightsState.kt']
        + [tests / 'FocusSetupStateTest.kt', tests / 'FocusInsightsStateTest.kt']
    )
    coroutines = kotlin_lib / 'kotlinx-coroutines-core-jvm.jar'
    if not coroutines.is_file():
        raise SystemExit('Bundled coroutines jar missing; nothing downloaded.')
    classpath = os.pathsep.join(map(str, [junit, hamcrest, coroutines]))
    environment = os.environ.copy()
    environment['JAVA_HOME'] = str(Path(java).resolve().parent.parent)
    with tempfile.TemporaryDirectory(prefix='luma-focus-setup-jvm-') as output:
        print(f'Compiling {len(sources) - 2} production files and two test files.', flush=True)
        subprocess.run([
            compiler, *map(str, sources), '-jvm-target', '17',
            '-classpath', classpath, '-d', output,
        ], cwd=ROOT, env=environment, check=True)
        runtime_cp = os.pathsep.join([output, classpath, str(kotlin_lib / '*')])
        subprocess.run([
            java, '-cp', runtime_cp, 'org.junit.runner.JUnitCore',
            'com.luma.lifeos.ui.focus.FocusSetupStateTest',
            'com.luma.lifeos.ui.focus.FocusInsightsStateTest',
        ], cwd=ROOT, env=environment, check=True)


if __name__ == '__main__':
    main()
