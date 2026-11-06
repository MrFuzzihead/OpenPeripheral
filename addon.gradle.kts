// Extra build logic, applied on top of build.gradle.kts (see dependencies.gradle for the main dependency list).
// Accessors such as testImplementation(...) are not generated for addon scripts, so configurations are
// addressed by name when needed.

import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService

import com.gtnewhorizons.retrofuturagradle.MinecraftExtension

// Mockito 1.x generates mocks with a shaded cglib, which only works up to Java 8. The mod targets Java 8
// anyway, so run the tests on a Java 8 toolchain instead of the Gradle daemon's JVM.
tasks.withType(Test::class.java).configureEach {
    javaLauncher = project.extensions.getByType(JavaToolchainService::class.java)
        .launcherFor {
            languageVersion = JavaLanguageVersion.of(8)
        }
}

// Version constants referenced by openperipheral.ModInfo / openperipheral.api.ApiAccess, injected into the
// generated openperipheral.Tags class (see generateGradleTokenClass in gradle.properties).
// Unsubstituted placeholders end up in the @Mod annotation, which makes FML reject the OpenMods version range.
val opApiVersion = (findProperty("opApiVersion") as String?) ?: "3.4.2"

/** "0.10.15" -> "0.11.0", i.e. the exclusive upper bound used by the FML "required-after" range. */
fun nextMinorVersion(version: String): String {
    val parts = version.split('.')
    val major = parts.getOrNull(0) ?: "0"
    val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
    return "$major.${minor + 1}.0"
}

// Resolved after evaluation, once dependencies.gradle has contributed to the "api" configuration
afterEvaluate {
    val openModsLibVersion = configurations.getByName("api").dependencies
        .filterIsInstance<ModuleDependency>()
        .firstOrNull { it.group == "com.github.GTNewHorizons" && it.name == "OpenModsLib" }
        ?.version
        ?: error("No OpenModsLib dependency found, cannot build the OpenMods version range")

    extensions.configure<MinecraftExtension> {
        injectedTags.put("OP_API_VERSION", opApiVersion)
        injectedTags.put("OPENMODS_LIB_VERSION", openModsLibVersion)
        injectedTags.put("OPENMODS_LIB_NEXT_VERSION", nextMinorVersion(openModsLibVersion))
    }
}

// ComputerCraft 3.2.0 declares `required-after:lwjgl3ify` in its mcmod.info, and lwjgl3ify needs a modern
// JVM. GTNHGradle's plain runServer/runClient use the Java 8 toolchain and carry no lwjgl3ify on the
// classpath, so they die at MissingModsException before the game starts. Forward them to the modern variants
// (which GTNHGradle wires to lwjgl3ify and a Java 17+/21/25 JVM), so both `./gradlew runClient`/`runServer`
// and CI's server smoke test keep working.
mapOf("runServer" to "runServer25", "runClient" to "runClient25").forEach { (plain, modern) ->
    tasks.matching { it.name == plain }.configureEach {
        if (project.tasks.names.contains(modern)) {
            description = "Alias for $modern - ComputerCraft 3.2.0 requires lwjgl3ify, which needs Java 17+"
            dependsOn(modern)
            onlyIf("the plain task forwards to $modern") { false }
        }
    }
}
