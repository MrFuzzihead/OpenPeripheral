OpenPeripheral
=============

An open source addon/coremod for ComputerCraft (and OpenComputers) that turns many blocks into peripherals.

* Official thread: http://www.computercraft.info/forums2/index.php?/topic/13063-
* Website: http://openperipheral.info/
* Source: https://github.com/MrFuzzihead/OpenPeripheral

Building
========

Requirements:

* JDK 17+ to run Gradle (the GTNH Gradle plugin provisions Java 8/25 toolchains as needed).
* Git.

```sh
git clone https://github.com/MrFuzzihead/OpenPeripheral.git
cd OpenPeripheral
./gradlew build
```

The built jar is written to `build/libs/`. The API jar is produced alongside it.

Development runs
================

`runClient` / `runServer` are aliases for the modern (`runClient25` / `runServer25`) variants because
ComputerCraft 3.3.x requires lwjgl3ify, which needs a modern JVM.

```sh
./gradlew runClient
./gradlew runServer
```

Tests
=====

The unit test suite runs on a Java 8 toolchain so production bytecode is exercised on the oldest supported JVM.

```sh
./gradlew test
./gradlew jacocoTestReport                  # HTML/XML coverage report in build/reports/jacoco/test/
./gradlew jacocoTestCoverageVerification    # enforces the coverage floor
./gradlew spotbugsMain                     # static analysis (HTML/XML in build/reports/spotbugs/)
```

Coverage is also checked as part of `./gradlew check`. The current floor is 65% line / 50% branch; raise it as
coverage improves. Unit coverage is currently 75.1% line / 67.1% branch.

In-game tests
-------------

`gameTestServer` boots a dedicated server with a test-only mod (`src/gameTest/java`) that exercises the real
ComputerCraft provider, peripheral attach/detach, a real ComputerCraft computer running Lua against the
peripheral, inline properties, real `ItemStack`/entity metadata, IMC blacklisting, and `/op_dump`:

```sh
mkdir -p run/server && echo "eula=true" > run/server/eula.txt
./gradlew gameTestServer
```

The same test mod also has a client half that opens the real config screen and exits:

```sh
./gradlew runClient25 -PgameTest          # needs a display; use xvfb-run in CI
```

The test mod is compiled in its own source set and is only placed on the run classpath for these tasks, so it is
never shipped in `build/libs`. OpenComputers is intentionally not part of the in-game test suite yet.

Headless smoke tests
--------------------

`-PsmokeTest` makes the game shut itself down after FML reports load complete. This is used to boot a real
dedicated server (and, on Linux with Xvfb, a real client) in CI:

```sh
mkdir -p run/server && echo "eula=true" > run/server/eula.txt
./gradlew runServer25 -PsmokeTest
```

The GitHub Actions workflow `.github/workflows/smoke-test.yml` runs the server check as a required job and the
Xvfb client check as a non-blocking job. Both upload their logs as artifacts.

Code review notes
=================

See [CODE_REVIEW.md](CODE_REVIEW.md) for the dependency audit, the bug/optimization/refactor backlog, and the
notes on test coverage and the remaining known limitations.

License
=======

OpenPeripheral is distributed under the terms of the MIT License, which can be found under the name LICENSE in
your distribution.
