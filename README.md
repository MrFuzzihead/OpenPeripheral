# OpenPeripheral

[![](https://jitpack.io/v/MrFuzzihead/OpenPeripheral.svg)](https://jitpack.io/#MrFuzzihead/OpenPeripheral)
[![Build status](https://github.com/MrFuzzihead/OpenPeripheral/actions/workflows/build-and-test.yml/badge.svg)](https://github.com/MrFuzzihead/OpenPeripheral/actions/workflows/build-and-test.yml)
[![Latest release](https://img.shields.io/github/v/release/MrFuzzihead/OpenPeripheral?include_prereleases&sort=semver)](https://github.com/MrFuzzihead/OpenPeripheral/releases/latest)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.7.10-62a34a)](https://minecraft.wiki/w/Java_Edition_1.7.10)
[![Forge](https://img.shields.io/badge/Forge-10.13.4.1614-1e2b4f)](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.7.10.html)
[![ComputerCraft](https://img.shields.io/badge/ComputerCraft-3.3.1-2ea44f)](https://github.com/MrFuzzihead/Computercraft-Legacy)

An open-scripting add-on for ComputerCraft (and OpenComputers) on Minecraft 1.7.10 that turns many blocks and items into peripherals automatically.

## What this is

This repository is a **port/continuation of [OpenPeripheral](https://github.com/OpenMods/OpenPeripheral)**,
the OpenMods add-on for ComputerCraft, targeting Minecraft 1.7.10 and the
[GTNH](https://www.gtnewhorizons.com/) modpack. It is built against
[Computercraft-Legacy](https://github.com/MrFuzzihead/Computercraft-Legacy) 3.3.1.

Instead of hand-writing a peripheral for every block type, OpenPeripheral uses an adapter system: any tile
entity, inventory, tank, or other object can be wrapped automatically when a computer attaches to it, and the
same adapters drive Lua-side documentation, in-game peripheral docs mounts, and item/entity metadata
exposure. The mod ships two artifacts: the `OpenPeripheralCore` mod itself and a separate
`OpenPeripheralApi` jar for other mods to implement adapters against.

See [CODE_REVIEW.md](CODE_REVIEW.md) for the dependency audit, the bug/optimization/refactor backlog, and the
test-coverage notes. The original project page, forums thread, and website are archived at
[openperipheral.info](http://openperipheral.info); development has moved to GitHub.

## Features

- **Automatic peripheral adapters** — inventories, chests, tanks, hoppers, and many other vanilla and modded
  blocks become computer peripherals without per-block code. Adapters can be added by other mods via the
  `OpenPeripheralApi` jar.
- **ComputerCraft and OpenComputers interfaces** — native CC peripherals today; OpenComputers support is
  compile-time and adapter-tested but is not part of the in-game test suite yet.
- **Lua type conversion** — a registry of inbound/outbound converters (primitives, strings, UUIDs, enums,
  structs, collections, `ItemStack`, `FluidStack`/`FluidTankInfo`, and more) with a recursion guard and
  strict `nil` handling for nullable vs primitive arguments.
- **Properties system** — single and indexed properties (`@PeripheralProperty` / `@IndexedProperty`) exposed
  to Lua with documentation metadata and custom property callbacks.
- **Item and entity metadata** — `ItemStackMetadataBuilder` / `EntityMetadataBuilder` expose structured
  metadata to Lua, including null-safe handling.
- **Tile-entity blacklist** — classes or tags can be blacklisted via config or IMC so a block never becomes
  a peripheral.
- **In-game docs** — bundled Lua programs in `src/main/resources/openperipheral/lua/` (`docs`, `github`,
  `multiperipheral`) are shipped as docs mounts and are covered by `LuaScriptsTest` (ComputerCraft's Cobalt
  parser plus real execution of the docs summary, multiperipheral dispatch, and GitHub download paths).
- **In-game config GUI** — every option is editable from the Mods list → OpenPeripheral → Config.
- **`/op_dump`** — operator command that dumps the adapter/peripheral state (output confined to the
  Minecraft directory).

## Requirements

| Dependency        | Version                                            |
|-------------------|----------------------------------------------------|
| Minecraft         | 1.7.10                                             |
| Minecraft Forge   | 10.13.4.1614                                       |
| ComputerCraft     | [3.3.1, ) (`Computercraft-Legacy`)                 |
| OpenModsLib       | [0.10.15, 0.11.0) — pinned; GTNH ships 0.10.x       |
| lwjgl3ify         | Required by ComputerCraft 3.x (Java 17+)           |
| Not Enough Items  | Optional (dev-run only)                            |
| OpenComputers     | Optional (compile-time / adapter tests only)       |

OpenModsLib is deliberately pinned to 0.10.15: building against 0.11.0 makes FML require
`[0.11.0,0.12.0)` and the mod refuses to start in packs shipping 0.10.15, even though the two versions are
the same commit. See [CODE_REVIEW.md](CODE_REVIEW.md) §2 for the full dependency audit.

## Compiling

Everything is handled by the `com.gtnewhorizons.gtnhconvention` plugin. Load the project in IntelliJ and run
`./gradlew build`, or use the wrapper from the command line:

```sh
./gradlew build              # compile, test, format check, coverage, SpotBugs
./gradlew test               # unit tests only (Java 8 toolchain)
./gradlew jacocoTestReport   # HTML coverage -> build/reports/jacoco/test/html
./gradlew spotbugsMain       # SpotBugs (MAX effort / HIGH confidence)
./gradlew runServer          # dev server (auto-forwards to runServer25)
./gradlew runClient          # dev client (auto-forwards to runClient25)
```

The Gradle daemon is pinned to Java 25 (see `.java-version`); the mod itself targets Java 8 bytecode (via
Jabel). The output jar is placed in `build/libs/`; the API jar is produced alongside it. `./gradlew test`
runs the headless JUnit 4 suite (331 tests / 38 classes, currently 75.1% line / 67.1% branch coverage) and
`check` enforces the coverage floor (65% line / 50% branch) plus SpotBugs.

`runClient` / `runServer` are aliases for the modern (`runClient25` / `runServer25`) variants because
ComputerCraft 3.3.x requires lwjgl3ify, which needs a modern JVM.

### In-game tests

`src/gameTest/java` is a separate, non-shipped test-only mod. It exercises the real ComputerCraft provider,
peripheral attach/detach, a real CC computer running Lua against the peripheral, inline properties, live
`ItemStack`/entity metadata, IMC blacklisting, and `/op_dump`:

```sh
mkdir -p run/server && echo "eula=true" > run/server/eula.txt
./gradlew gameTestServer
./gradlew runClient25 -PgameTest    # client half: opens the real config GUI; needs a display (Xvfb in CI)
```

### Headless smoke tests

`-PsmokeTest` makes the game shut itself down after FML reports load complete. This is used to boot a real
dedicated server (and, on Linux with Xvfb, a real client) in CI:

```sh
mkdir -p run/server && echo "eula=true" > run/server/eula.txt
./gradlew runServer25 -PsmokeTest
```

The GitHub Actions workflow `.github/workflows/smoke-test.yml` runs the server check and the in-game game
tests as required jobs, and the Xvfb client checks as non-blocking jobs. Both upload their logs as
artifacts.

## Configuration

All options are in `config/OpenPeripheralCore.cfg`, generated on first launch. In game they can be edited
from the Mods list → OpenPeripheral → Config. See [CODE_REVIEW.md](CODE_REVIEW.md) for the full option list
and the note on `dev.enableDevMethods`.

## Contributing

Bug reports and pull requests are welcome. If you find a bug that appears to relate to ComputerCraft itself,
please report it in the [Computercraft-Legacy](https://github.com/MrFuzzihead/Computercraft-Legacy)
repository rather than here — OpenPeripheral is now a thin adapter layer on ComputerCraft's public API and
is unlikely to be the cause.

Please read [CODE_REVIEW.md](CODE_REVIEW.md) before making structural changes: it records the dependency
pins, the known limitations (outbound number precision, the struct no-arg constructor requirement, and the
legacy `AdapterRegistryWrapper` facade), and the test-coverage state.

## License

**MIT License** — see [LICENSE](LICENSE). Copyright (c) 2013 OpenMods Team. ComputerCraft itself is
distributed under the ComputerCraft Public License; see
[Computercraft-Legacy](https://github.com/MrFuzzihead/Computercraft-Legacy) for its terms.

## Credit

OpenPeripheral was created by the [OpenMods Team](https://github.com/OpenMods). ComputerCraft was created by
[Daniel "dan200" Ratcliffe](https://github.com/dan200). This port is maintained by
[MrFuzzihead](https://github.com/MrFuzzihead) for Minecraft 1.7.10 / GTNH.
