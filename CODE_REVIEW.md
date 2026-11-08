# OpenPeripheral Core — Code Review, Dependency Audit & Test Hardening

Review date: 2026-11-08
Scope: entire `src/main/java` tree, resources, Gradle build, CI workflows, and the existing unit test suite.

This document records what was changed, what was found but left alone, and how to reproduce the checks.
The companion changes are in the working tree; run `git diff` for the exact patches.

---

## 1. Executive summary

* **Dependencies were brought up to the newest versions that are compatible with a 1.7.10 / Java 8 mod.**
  ComputerCraft-Legacy 3.3.1, NotEnoughItems 2.9.2-GTNH, GTNH settings convention 2.0.34, Mockito 4.11.0,
  JaCoCo 0.8.16. OpenModsLib stays at 0.10.15 on purpose (see §2).
* **The unit-test suite grew from 109 tests in 11 classes to 300 tests in 29 classes.**
  Line coverage is **72.4 %**, branch coverage **64.2 %** (JaCoCo 0.8.16). The build now enforces a
  65 % line / 50 % branch floor via `jacocoTestCoverageVerification`, wired into `check`.
* **Headless boot tests now exist.** `runServer25 -PsmokeTest` and `runClient25 -PsmokeTest` boot the real
  game, wait for FML load-complete, then exit themselves. Both were run locally (server: pass, client: pass)
  and are wired into `.github/workflows/smoke-test.yml`.
* **In-game game tests now exist.** `./gradlew gameTestServer` boots a dedicated server with a test-only mod
  that exercises the real ComputerCraft provider, peripheral attach/detach, a real CC computer running Lua
  against the peripheral, inline properties, live `ItemStack`/entity metadata, IMC blacklisting, and
  `/op_dump`; the client half opens the real config GUI under Xvfb. The test mod lives in
  `src/gameTest/java` and is never shipped in the mod jar.
* **40+ defects were fixed**, including several that were user-visible:
  * Lua `nil` could not be passed to nullable reference arguments, and `nil` was accepted for primitives.
  * `@IndexedProperty(valueType = ...)` documentation was ignored.
  * `IIndexedCustomProperty` callbacks received the container object instead of the owning TE.
  * `IEntityCustomMetaProvider.canApply` filtering was inverted.
  * The `interfaces.*` config toggles were read *after* architecture modules were initialized.
  * `addToBlacklist()` from IMC never reached the live blacklist.
  * Generated OpenComputers interface bypasses mis-indexed `long`/`double` arguments (`VerifyError`).
  * Adapter methods inherited from superclasses were silently dropped.
  * `OutOfMemoryError`/`AssertionError` were converted into Lua exceptions.
  * The ComputerCraft dependency was declared as `[1.70,]` even though the code requires CC 3.0+.

---

## 2. Dependency audit

| Dependency | Previous | Current | Newest available | Rationale |
|---|---:|---:|---:|---|
| Minecraft Forge | 10.13.4.1614 | 10.13.4.1614 | 10.13.4.1614 | Final 1.7.10 Forge build. |
| MCP mappings | stable / 12 | stable / 12 | stable / 12 | Final 1.7.10 MCP channel/version. |
| ComputerCraft-Legacy | 3.3.0 | **3.3.1** | 3.3.1 | Newest tag. 3.3.1 changes are Lua ROM/BIOS/test fixes only; no Java API change. |
| OpenModsLib | 0.10.15 | **0.10.15 (pinned)** | 0.11.0 | Deliberate pin. GTNH packs ship 0.10.x; building against 0.11.0 makes FML require `[0.11.0,0.12.0)` and refuses to start with 0.10.15. The project note says 0.10.15 and 0.11.0 are the same commit. |
| OpenComputers (GTNH) | 1.13.0-GTNH | **1.13.0-GTNH** | 1.13.0-GTNH | Already latest. |
| NotEnoughItems (GTNH) | 2.9.0-GTNH | **2.9.2-GTNH** | 2.9.2-GTNH | Runtime-only dev dependency; latest. |
| GTNH settings convention | 2.0.33 | **2.0.34** | 2.0.34 | Auto-updated by `autoUpdateBuildScript`. |
| Gradle wrapper | 9.7.1 | **9.7.1** | 9.7.1 | Already current. |
| JUnit | 4.13.2 | **4.13.2** | 4.13.2 (JUnit 4) | Newest 4.x. JUnit 5 would be a separate migration; see §5. |
| Mockito | 1.10.19 | **4.11.0** | 5.24.0 | 4.11.0 is the newest Mockito that still runs on Java 8. Mockito 5 requires Java 11. Added `mockito-inline` so final Minecraft classes (`ItemStack`) can be mocked. |
| JaCoCo | — | **0.8.16** | 0.8.16 | Added for coverage; works with the Java 8 test launcher. |
| GTNHLib | 0.11.48 (transitive) | 0.11.48 (transitive) | 0.12.0 | Constraint comes from ComputerCraft-Legacy. Not a direct dependency; the runtime pack supplies the final version. |

Build-file side effects:

* `settings.gradle.kts` moved to `2.0.34`.
* `gradle/gradle-daemon-jvm.properties` toolchain URLs were regenerated for Java 25 by the GTNH plugin.
* `gradle.properties` has no content change (line-ending normalization only).

The Gradle alias logic in `addon.gradle.kts` was updated to say “ComputerCraft 3.3.x requires lwjgl3ify”
(was 3.2.0).

---

## 3. Build & test harness changes

### 3.1 JaCoCo

`addon.gradle.kts` now:

* applies the `jacoco` plugin, pins JaCoCo 0.8.16;
* enables the JaCoCo agent on every `Test` task;
* configures `jacocoTestReport` (HTML + XML, CSV disabled) and excludes the generated
  `openperipheral/Tags.class`;
* configures `jacocoTestCoverageVerification` with a 65 % line / 50 % branch floor;
* makes `check` depend on `jacocoTestCoverageVerification`, so CI fails on coverage regressions.

Reports land in `build/reports/jacoco/test/html/index.html` and
`build/reports/jacoco/test/jacocoTestReport.xml`.

### 3.2 Headless smoke tests

`openperipheral.SmokeTestHook` is a tiny, property-gated hook called from `OpenPeripheralCore.loadComplete`:

* enabled only by `-Dopenperipheral.smokeTest=true`;
* sleeps for `-Dopenperipheral.smokeTest.delay` ms (default 2000);
* prints `OPENPERIPHERAL_SMOKE_TEST_OK`;
* calls `FMLCommonHandler.instance().exitJava(0, false)`.

`addon.gradle.kts` forwards `-PsmokeTest` (and optional `-PsmokeTestDelay`) to every `JavaExec` task, so:

```sh
mkdir -p run/server && echo "eula=true" > run/server/eula.txt
./gradlew runServer25 -PsmokeTest
./gradlew runClient25 -PsmokeTest
```

Both were run locally and reached `OPENPERIPHERAL_SMOKE_TEST_OK`.

`.github/workflows/smoke-test.yml` adds:

* `server-smoke` (required): boots the dedicated server under a 900 s timeout and greps for `Done (` and
  the marker;
* `client-smoke` (non-blocking): installs Xvfb + Mesa, boots the client under Xvfb, and greps for
  `Setting user:` and the marker. Both jobs upload their logs.
* `game-test` (required): runs `./gradlew gameTestServer`, greps for `GAMETEST_ASSERTIONS_OK` and
  `OPENPERIPHERAL_GAMETEST_OK`, and uploads `game-test.log`.
* `client-game-test` (non-blocking): runs `./gradlew runClient25 -PgameTest` under Xvfb, opens the real config
  screen, greps for `OPENPERIPHERAL_CLIENT_GAMETEST_OK`, and uploads the log.

### 3.2b In-game game tests

`src/gameTest/java/openperipheral/gametest/GameTestMod.java` is a separate, non-shipped `@Mod`. It is placed on
the run classpath only for `./gradlew gameTestServer` / `./gradlew runClient25 -PgameTest` (or
`runServer25 -PgameTest`). The server assertions run on a worker thread after `FMLServerStartedEvent` so the
server tick loop can execute the `SynchronousExecutor` main-thread tasks that the real ComputerCraft computer
is waiting for. The client half opens `ConfigGuiFactory.ConfigScreen` after `FMLLoadCompleteEvent` and exits.

### 3.3 Mockito migration

All test imports were migrated from the Mockito 1 API:

* `org.mockito.Matchers` → `org.mockito.ArgumentMatchers`;
* `org.mockito.runners.MockitoJUnitRunner` → `org.mockito.junit.MockitoJUnitRunner`;
* `anyObject()` → `any()`;
* `InvocationOnMock#getArgumentAt(0, X)` → `getArgument(0, X)`;
* one test had to change `anyInt()` to `anyFloat()` because the method under test takes a `float`
  (Mockito 4 is stricter about primitive matcher types).

### 3.4 New test coverage

New test classes (in `src/test/java/openperipheral/tests`):

| Class | Focus |
|---|---|
| `TypeConverterTest` | nil semantics, numeric/enum/UUID/struct conversion, arrays/lists/maps/sets incl. concrete types, outbound converters, registration order, ignored types, nullable helpers, item-stack input validation. |
| `PropertyExecutorTest` | single/indexed/merged properties, nullable setters, custom `ISingleCustomProperty`/`IIndexedCustomProperty`, listener/owner identity, doc-type overrides, negative indices. |
| `AdapterFrameworkTest` | `ArchitectureChecker`, `FeatureGroupManager`, `MethodSelector`, inherited adapter methods, constrained adapters, deterministic method order, composer/factory caching + concurrency, `AnnotationMetaExtractor` accumulation. |
| `MetadataTest` | entity basic metadata, custom-provider filtering, provider registry duplicate/invalid targets, item metadata null/empty paths, item proxy. |
| `MethodDeclarationExtraTest` | nullable/optional arguments, aliases, duplicate `@Optionals`, env argument naming. |
| `DocumentationTest` | `DocUtils` signatures/doc/describe, help text, XML/XHTML `DocBuilder` output. |
| `EnvironmentTest` | ComputerCraft/OpenComputers architecture access, env injection, `SynchronousExecutor` success/failure/error handling. |
| `EnvironmentWideArgsTest` | generated OpenComputers interface bypass with `long`/`double` arguments. |
| `EnvironmentMethodsFactoryTest` | `@ExposeInterface` validation message. |
| `StructEdgeTest` | negative table indices, missing/unknown fields, optional fields, non-static inner class, output offsets. |
| `MountAndUtilityTest` | `StringMount`/`UtilsResourceMount`, `PeripheralTypeProvider`, `TileEntityBlacklist`, `NameUtils`, `Index`. |
| `ConfigurationTest` | config toggles are applied before module startup and loading is idempotent. |
| `MethodsListerHelperTest` | `listMethods`/`listSources`/`doc`/`getAdvancedMethodsData`. |
| `ComputerCraftWrapperTest` | `LuaObjectWrapper`, `PeripheralProvider`, inline methods, proxy for `@ExposeInterface`, `IndexedMethodMap` bounds. |
| `SignallingAndMetadataTest` | signalling callback IDs, daemon worker threads, `mcmod.info` URL integrity, dependency ranges. |
| `ImcTest` | IMC `ignoreTileEntity` and `registerClassType` parsing, including malformed values. |
| `SafePeripheralFactoryTest` | null tiles, stable broken-peripheral method names, placeholder factory. |
| `ConfigGuiFactoryTest` | feature-group config element reads/writes the GUI's config copy. |

The in-game suite (`src/gameTest/java/openperipheral/gametest/GameTestMod.java`) is separate from JaCoCo. It
asserts:

* `TileEntityChest` discovery through `PeripheralProvider`, attach/detach, docs mount, real method calls with
  a fake `IComputerAccess`/`ILuaContext` (including the `MethodResult` task-completion path).
* A real `ServerComputer` running a generated `startup` Lua program that calls the peripheral, reads/writes a
  real `ItemStack`, and prints machine-checked markers.
* Inline `@Property` get/set and `@ScriptCallable` methods on a plain object.
* Live `IItemStackMetaBuilder`/`IEntityMetaBuilder` output, `IPeripheralBlacklist`/`TileEntityBlacklist`,
  `/op_dump`, and the `ApiAccess` interfaces.
* The test mod runs assertions on a worker thread after `FMLServerStartedEvent`, so `SynchronousExecutor`'s
  main-thread tasks can execute while the server tick loop continues.

Coverage movement:

| Counter | Before | After |
|---|---:|---:|
| Tests | 109 | **300** |
| Test classes | 11 | **29** |
| Instruction | 39.5 % | **71.2 %** |
| Branch | 34.3 % | **64.2 %** |
| Line | 40.9 % | **72.4 %** |
| Method | 37.0 % | **71.4 %** |
| Class | 51.3 % | **83.8 %** |

Coverage numbers refer to the JUnit suite. The in-game suite is pass/fail and intentionally not part of the
JaCoCo counters; its assertions are listed above.

Lowest-coverage remaining areas (good next targets): the client-only GUI screen factory, `ApiSetup`,
`OpenPeripheralCore` event lifecycle, `ItemStackMetadataBuilder$Proxy`/partial-builder overloads,
`LuaReflectionHelper` (dev-only), and the OC factory wrappers (OC is intentionally out of scope for now).

---

## 4. Defects found and fixed

Severity: **H** = user-visible/crash/data-loss, **M** = wrong behaviour or compatibility risk,
**L** = cleanup/latent.

### 4.1 Conversion layer

| # | Sev | File | Problem | Fix / test |
|---|---|---|---|---|
| 1 | H | `converter/TypeConverter.java` | Nil handling was inverted: `toJava(null, String.class)` threw, while `toJava(null, int.class)` returned `null`. | Reference types accept `null`; primitives reject it. The `Class` overload also allows a `null` result for reference types and unboxes primitive classes correctly. `TypeConverterTest`. |
| 2 | H | `converter/inbound/ContainerConverterHelper.java` | `isNullable` was set to `componentType.isPrimitive()` — backwards. | `!isPrimitive()`. Prevents nulls being pushed into primitive arrays when the helper is extended. |
| 3 | H | `converter/outbound/ConverterGameProfileOutbound.java` | Offline-mode `GameProfile` has a null UUID; `profile.getId().toString()` NPEs. | Emit `uuid = null`. `TypeConverterTest`. |
| 4 | M | `converter/inbound/ConverterItemStackInbound.java` | Unknown `mod:item` id passed `null` to `new ItemStack` → NPE. | Validate the lookup and throw a descriptive `IllegalArgumentException`. |
| 5 | M | `converter/inbound/ConverterListInbound.java`, `ConverterMapInbound`, `ConverterSetInbound` | Only the exact raw types `List`, `Map`, `Set` were handled. Methods accepting `Collection`, `HashMap`, `HashSet` (and assignable concrete types) failed even though the type classifier advertised them. | Use `isAssignableFrom(ArrayList/HashMap/HashSet)`. `TypeConverterTest` covers `Collection`, `HashMap`, `HashSet`. |
| 6 | L | `converter/outbound/ConverterArrayOutbound.java` | Direct call with `null` NPEs (`o.getClass()`). | Null guard. |
| 7 | L | `converter/outbound/ConverterEnumOutbound.java` | `toLowerCase()` used the JVM default locale; Turkish locales produce dotless `ı`. | `toLowerCase(Locale.ENGLISH)`. Regression test switches the default locale to `tr-TR`. |
| 8 | L | `converter/outbound/ConverterItemStackOutbound.java` | `BUILDER` was a per-converter instance. | `static final`. |
| 9 | L | `converter/inbound/ConverterStructInbound.java` | `expected` could be null in direct calls. | Null guard. |

### 4.2 Adapter / property system

| # | Sev | File | Problem | Fix / test |
|---|---|---|---|---|
| 10 | H | `adapter/property/IndexedManipulatorProvider.java` | `INDEXED_TARGET_DELEGATING_MANIPULATOR` passed the container object (`target`) as the *owner* to `IIndexedCustomProperty.get/set`. A TE-backed custom property saw the wrong owner. | Pass `owner`. `PropertyExecutorTest` asserts owner identity. |
| 11 | M | `adapter/property/PropertyListBuilder.java` | `addProperty(IndexedProperty)` ignored `valueType()` and always used `ArgType.AUTO`, so `@IndexedProperty(valueType = BOOLEAN)` documented the field's deduced type instead. | Pass `property.valueType()`. `PropertyExecutorTest`. |
| 12 | L | `adapter/property/PropertyListBuilder.java` | Merged setter description fallback checked `singleParameters.setterDescription` twice. | Fall back to `indexedParameters.setterDescription`. `PropertyExecutorTest`. |
| 13 | L | `adapter/property/IndexedManipulatorProvider.java` | `getIndex(null)` NPE'd inside the error message (`index.getClass()`). | Format the value itself. `PropertyExecutorTest` expects `IllegalArgumentException`. |
| 14 | H | `adapter/wrappers/AdapterWrapper.java` | Only `adapterClass.getDeclaredMethods()` was scanned, so `@ScriptCallable` methods inherited from a base adapter were silently missing. | Walk the superclass chain, most-derived first, skip bridge/synthetic and shadowed signatures. `AdapterFrameworkTest`. |
| 15 | M | `adapter/method/MethodDeclaration.java` | Adapter methods on non-public classes/methods were not `setAccessible`, so invocation could fail. | `method.setAccessible(true)` (SecurityException ignored; invoke will report). |
| 16 | L | `adapter/method/MethodDeclaration.java` | A second `@Optionals` was silently accepted despite the error message claiming duplicates are rejected. | `sawOptionals` guard. `MethodDeclarationExtraTest`. |
| 17 | L | `adapter/method/MethodDeclaration.java` | `nameEnv` format string had two `%s` but only one argument. | Pass `index` and `name`. |
| 18 | L | `adapter/method/Argument.java` | Conversion failures dropped the original cause. | Attach the cause to the `IllegalArgumentException`. |

### 4.3 Metadata

| # | Sev | File | Problem | Fix / test |
|---|---|---|---|---|
| 19 | H | `meta/EntityMetadataBuilder.java` | `filterCustomProviders` removed providers when `canApply()` was **true** and kept them when **false** — exactly inverted. | Remove when `!canApply`. `MetadataTest` covers both cases. |
| 20 | M | `meta/ItemStackMetadataBuilder.java` | `getBasicItemStackMetadata`/`getItemStackMetadata` NPE on an `ItemStack` whose `getItem()` is null; `getItemStackMetadata(key, null)` and `getKeys(null)` NPE. | Return the `invalid` map/empty set/null; `getRawNameForStack` null guard. `MetadataTest`. |

### 4.4 Structures

| # | Sev | File | Problem | Fix / test |
|---|---|---|---|---|
| 21 | M | `converter/StructHandlerProvider.java` | A negative numeric table index passed the `< size` check, then crashed with `IndexOutOfBoundsException`. | Require `index >= 0`. `StructEdgeTest`. |

### 4.5 Architecture / configuration / blacklist

| # | Sev | File | Problem | Fix / test |
|---|---|---|---|---|
| 22 | H | `OpenPeripheralCore.java` | `ConfigProcessing` ran in `preInit`, but `construct()` already called `apiSetup.setupApis()` and initialized modules using the default values. `interfaces.ComputerCraft=false` / `interfaces.OpenComputers=false` were therefore ignored at startup. | Load config in `construct()` from `Loader.getConfigDir()`, keep `loadConfig` idempotent for `preInit`, install the config listener once. `ConfigurationTest`. |
| 23 | M | `adapter/AnnotationMetaExtractor.java` | `@ExcludeArchitecture` on a class/method *replaced* the package/class value instead of accumulating. | Union values. `AdapterFrameworkTest`. |
| 24 | M | `adapter/FeatureGroupManager.java` | One malformed `feature:architecture` config entry threw during startup. | Log and skip malformed entries. `AdapterFrameworkTest`. |
| 25 | H | `adapter/TileEntityBlacklist.java` | `addToBlacklist()` (used by the IMC handler) only wrote to `imcBlacklist`; `fullBlacklist` was refreshed only on a later config-change event. IMC blacklisting therefore did not take effect until a config change. | Write through to the live set; use a concurrent set; normalize null config entries. `MountAndUtilityTest`. |
| 26 | M | `ModInfo.java` | ComputerCraft dependency was `[1.70,]`, but the code uses `MethodResult` (CC 3.0+) and the 3.3 API. Loading with CC 1.7x would fail with `NoClassDefFoundError`. | `after:ComputerCraft@[3.0.0,]`. `SignallingAndMetadataTest`. |
| 27 | L | `ConfigGuiFactory.java` | `CONFIG_FEATURE_GROUP_MANAGER` could be null if a config change arrived before the GUI was initialized. | Lazily copy the live manager. |
| 28 | L | `ApiSetup.java`, `OpenPeripheralCore.java` | Typos (`provideded`, `severStart`). | Fixed. |

### 4.6 ComputerCraft / OpenComputers integration

| # | Sev | File | Problem | Fix / test |
|---|---|---|---|---|
| 29 | H | `interfaces/oc/asm/CommonMethodsBuilder.java` | Generated `@ExposeInterface` bypass methods advanced local slots by 1 per argument, ignoring the 2-slot size of `long`/`double`. Generated classes failed verification (`VerifyError`). | Advance by `Type.getSize()`. `EnvironmentWideArgsTest` reproduces the original `VerifyError`. |
| 30 | L | `interfaces/oc/asm/CommonMethodsBuilder.java` | `AnnotationVisitor.visitEnd()` was called twice. | Removed the duplicate. |
| 31 | L | `interfaces/oc/providers/EnvironmentMethodsFactory.java` | `@ExposeInterface` validation message had `%s` placeholders but no arguments. | Pass target class and interface. `EnvironmentMethodsFactoryTest` asserts the message. |
| 32 | M | `interfaces/cc/providers/SafePeripheralFactory.java` | `BrokenPeripheral.getMethodNames()` returned a *different random name on every call*. ComputerCraft maps method indices from `getMethodNames()` to `callMethod()`, so the mapping was unstable. | Choose the bogus name once in the constructor; renamed `BOGUS_METODS` → `BOGUS_METHODS`. |
| 33 | M | `interfaces/cc/providers/SafePeripheralFactory.java` | Every neighbourhood scan of a broken TE logged a SEVERE message (log spam), and the logging path dereferenced `tile.getWorldObj().provider` without a guard. | Log once per class with a concurrent set; safe dimension formatter. |
| 34 | H | `interfaces/cc/wrappers/AdapterPeripheral.java`, `LuaObjectWrapper.java`, `SynchronousExecutor.java` | `catch (Throwable)` converted `OutOfMemoryError`/`AssertionError` into Lua exceptions. | Re-throw `Error` before the generic catch. `EnvironmentTest` checks `AssertionError` escapes. |
| 35 | M | `interfaces/cc/providers/DriverOpenPeripheral.java` | `HashMap` cache accessed from potentially different threads. | `ConcurrentHashMap`. |
| 36 | L | `interfaces/cc/wrappers/AdapterPeripheral.java`, `interfaces/oc/asm/peripheral/PeripheralEnvironmentBase.java` | `CachedFactory` (a plain `HashMap`) was used without synchronization for computer-access caches. | Synchronize the `getOrCreate`/`remove` calls. |
| 37 | M | `interfaces/cc/wrappers/AdapterPeripheral.java` | Peripheral help text (`DocUtils.createPeripheralHelpText`) was rebuilt for every TE instance. | Cache per target class in a `ConcurrentHashMap`. |

### 4.7 Framework / concurrency / determinism

| # | Sev | File | Problem | Fix / test |
|---|---|---|---|---|
| 38 | M | `adapter/composed/ComposedMethodsFactory.java` | `getAdaptedClass` used unsynchronized `HashMap`/`HashSet` caches; concurrent first access could generate a class twice or corrupt the map. | `synchronized`; `listCollectedClasses` returns a snapshot. `AdapterFrameworkTest` exercises 8 concurrent callers. |
| 39 | M | `adapter/AdapterRegistry.java`, `ClassMethodsListBuilder.java`, `IndexedMethodMap.java`, `NamedMethodMap.java`, `ClassMethodsComposer.java` | Method order came from `HashMap`/`HashMultimap`, so script method indices and documentation order could differ between JVM runs. | `LinkedHashMap`/`LinkedHashMultimap`; sorted names in `IndexedMethodMap`/`NamedMethodMap`; sorted interface traversal. `AdapterFrameworkTest`. |
| 40 | M | `adapter/PeripheralTypeProvider.java` | `TreeMap` + file rewrites were unsynchronized; concurrent peripheral creation could corrupt names or the overlay file. | `synchronized` public operations. |
| 41 | M | `meta/MetaProvidersRegistry.java` | `HashMap` cache and `ArrayListMultimap` accessed without synchronization. | Synchronize add/get. |
| 42 | L | `util/NameUtils.java` | Lazy reflection caches were not synchronized. | Synchronize getters. |
| 43 | L | `adapter/types/classifier/TypeClassifier.java` | `ArrayList` of classifiers could be mutated while being iterated by another thread. | `CopyOnWriteArrayList`. |
| 44 | L | `converter/TypeConvertersProvider.java`, `ArchitectureChecker.java`, `FeatureGroupManager.java`, `CommandDump.java`, `StructHandlerProvider.java`, `DocUtils.java` | Shared maps/lists/caches were plain `HashMap`/`ArrayList`; several are reachable from CC/OC computer threads. | `ConcurrentHashMap`/`CopyOnWriteArrayList` or synchronized access. |
| 45 | M | `adapter/wrappers/SignallingGlobals.java` | A pool size of 0 or less crashed class initialization; worker threads were non-daemon; a saturated `SynchronousQueue` dropped tasks with `RejectedExecutionException`. | Clamp pool size to 1, daemon threads, `CallerRunsPolicy`, catch/log `RejectedExecutionException`. `SignallingAndMetadataTest`. |
| 46 | L | `adapter/composed/IndexedMethodMap.java` | `getMethod(-1)`/`getMethodName(-1)` threw `ArrayIndexOutOfBoundsException` before the caller could produce a clean error. | Bounds-check and return null. `ComputerCraftWrapperTest`. |

### 4.8 Mounts, resources, metadata files

| # | Sev | File | Problem | Fix / test |
|---|---|---|---|---|
| 47 | M | `interfaces/cc/UtilsResourceMount.java` | `getSize()` always returned 0; `list()` returned all files for every path; the `Scanner` leaked on errors; `openForRead` could return null. | Real UTF-8 byte size (cached), list only for the root, try-with-resources, IOException on missing resource. `MountAndUtilityTest`. |
| 48 | L | `interfaces/cc/StringMount.java` | `getSize()` used `String.length()` (UTF-16 chars), not UTF-8 bytes; null text was possible. | UTF-8 byte length and null-to-empty. `MountAndUtilityTest`. |
| 49 | M | `src/main/resources/mcmod.info` | Both URLs were duplicated: `https://github.com/MrFuzzihead/https://github.com/MrFuzzihead/OpenPeripheral`. | Correct GitHub URL/releases URL. `SignallingAndMetadataTest` parses the resource. |
| 50 | L | `ModInfo.java` | `PROXY_SERVER`/`PROXY_CLIENT` point at non-existent `openperipheral.core.*` classes. | Removed (they had no callers). |
| 51 | L | `ClassMethodsListBuilder`, `ConfigGuiFactory`, `DocBuilder`, `ConverterEnumOutbound` | `toLowerCase()` without a locale. | `Locale.ENGLISH`. |
| 52 | L | `DocUtils.java` | Used `joptsimple.internal.Strings` (an internal class of a transitive dependency). | Replaced with Guava `Strings`. |

---

## 5. Known limitations / recommendations (not changed)

These are real observations, but each is either an intentional compatibility choice, an API break, or
requires a live Minecraft/GameRegistry environment to test properly.

### 5.1 Conversion semantics

* **`ConverterStringInbound` is a catch-all.** Any object (including a Lua table or list) can be converted
  to `String` through `toString()`. This makes `f(String)` accept tables silently. Recommendation: restrict
  the catch-all to `String`/`Number`/`Boolean`/`Character` and let tables fail with “no known conversion”.
* **All outbound numbers become `double`.** `ConverterNumberOutbound` loses precision for `long` values
  above 2^53 and for some `BigDecimal`/`BigInteger` uses. This matches Lua 5.1 number semantics, but callers
  that round-trip 64-bit ids should be audited. Recommendation: keep `long` as a string for id-like values,
  or add an opt-in converter.
* **Concrete collection types are partially supported.** `ConverterListInbound`/`MapInbound`/`SetInbound`
  now accept `Collection`/`HashMap`/`HashSet` and any type assignable from those implementations, but not
  `LinkedList`, `TreeMap`, `LinkedHashSet`, etc. Recommendation: instantiate the requested concrete type
  reflectively when it has a no-arg constructor, and fall back to the interface implementation otherwise.
* **No recursion/cycle detection in `TypeConverter`.** A custom converter that calls `registry.toJava()` on
  the same value/type can loop forever. Recommendation: add a depth guard or a conversion context.
* **`StructHandlerProvider` requires public fields and a public no-arg constructor.** `field.setAccessible`
  is not called; private fields cannot be used. This is documented behaviour but worth making explicit in
  the API Javadoc.

### 5.2 Runtime / integration

* **Peripheral mount name collision.** `AdapterPeripheral.attach` mounts the docs at the constant name
  `openp` for every generated peripheral on a computer. Whether CC tolerates the same mount name twice
  depends on the CC version. Recommendation: mount once per computer (track in the access cache) or use a
  per-peripheral name.
* **`AdapterPeripheral.detach` does not unmount** the `openp`/help mounts. CC usually cleans up when the
  peripheral detaches, but explicit unmounting would be safer.
* **`CommandDump` writes a user-supplied filename** relative to the Minecraft directory. A server operator
  can accidentally write outside the intended folder (`../`). Recommendation: reject path separators or
  normalize/resolve against the MC directory.
* **`mcmod.info` still has `useDependencyInformation: false`** and an empty `requiredMods` list. FML uses
  the `@Mod(dependencies = ...)` string, so this is cosmetic, but setting `true` and listing OpenModsLib/CC
  would help third-party launchers.
* **`ItemStackMetadataBuilder.NULL`** only contains `id=invalid`; the non-null shape has `name`, `qty`,
  `dmg`, etc. Recommendation: return the same keys with neutral values, or document the reduced shape.
* **`EntityMetadataBuilder`** assumes `Entity#getUniqueID()` is non-null in the basic map. It currently
  survives because the outbound converter turns null into nil, but a defensive `if (id != null)` would be
  clearer.
* **`AdapterRegistryWrapper`** is now only a thin registration facade; the `registerInline` method is a
  deprecated no-op. It is fine to keep for binary compatibility, but a future API bump could remove it.
* **`LuaReflectionHelper`** exposes raw reflection metadata and is only enabled by `dev.enableDevMethods`.
  Keep it off in production packs; consider adding a warning when it is enabled.

### 5.3 Tests / tooling

* **Client smoke test is non-blocking** because software OpenGL under Xvfb is environment-dependent. It
  still uploads logs and greps for markers; promote it to required once the runner image is known-good.
* **`ItemStackMetadataBuilder$Proxy`/partial-builder methods** are not all exercised; the game test covers
  the non-null `getItemStackMetadata` path and the unit tests cover the null/empty paths.
* **Mockito 4 is the Java 8 ceiling.** If the test runtime is moved to Java 17 (the production class files
  still target Java 8), Mockito 5.24 and JUnit 5 can be adopted. That is a separate migration; the current
  setup deliberately exercises the Java 8 runtime.
* **Add static analysis.** The project currently relies on Spotless + Checkstyle (wildcard imports only).
  `spotbugs` or Error Prone would catch several of the patterns fixed here automatically. A
  `dependencyUpdates`/Renovate check would keep the version pins current.
* **OpenComputers is intentionally excluded from the in-game suite.** Its unit/ASM coverage remains, but
  its runtime driver/injector path is not booted. Add OC to a future game-test runtime configuration when it
  becomes a priority.
* **The in-game CC test uses a `ServerComputer` directly**, not a placed ComputerCraft block/TE. That covers
  the real `Computer`/`PeripheralAPI`/`MethodResult` bridge; a block-level test can be added if ComputerCraft
  changes its computer block internals.

---

## 6. Verification performed

All commands were run from the repository root on Windows with the project’s Java 25 daemon and Java 8
test launcher.

```text
./gradlew spotlessCheck                              PASS
./gradlew checkstyleMain checkstyleTest              PASS
./gradlew test                                       PASS (293 tests, 0 failures)
./gradlew jacocoTestReport                           PASS
./gradlew jacocoTestCoverageVerification             PASS (65 % line / 50 % branch floor)
./gradlew build                                      PASS
./gradlew runServer25 -PsmokeTest                    PASS (Done + OPENPERIPHERAL_SMOKE_TEST_OK)
./gradlew runClient25 -PsmokeTest                    PASS (Setting user + OPENPERIPHERAL_SMOKE_TEST_OK)
./gradlew gameTestServer                             PASS (GAMETEST_ASSERTIONS_OK + OPENPERIPHERAL_GAMETEST_OK)
./gradlew runClient25 -PgameTest                     PASS (OPENPERIPHERAL_CLIENT_GAMETEST_OK)
```

Generated reports:

* `build/reports/tests/test/index.html`
* `build/reports/jacoco/test/html/index.html`
* `build/reports/jacoco/test/jacocoTestReport.xml`
* `build/reports/checkstyle/main.html` / `test.html`

If you only want to run the fast feedback loop:

```sh
./gradlew test jacocoTestReport
```

If you want to reproduce the headless checks exactly as CI does:

```sh
mkdir -p run/server && echo "eula=true" > run/server/eula.txt
./gradlew runServer25 -PsmokeTest --console=plain --no-configuration-cache
./gradlew runClient25 -PsmokeTest --console=plain --no-configuration-cache   # needs a display/Xvfb
./gradlew gameTestServer --console=plain --no-configuration-cache            # in-game assertions
./gradlew runClient25 -PgameTest --console=plain --no-configuration-cache    # config GUI, needs Xvfb/display
```
