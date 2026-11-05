// Extra build logic, applied on top of build.gradle.kts (see dependencies.gradle for the main dependency list).
// Accessors such as testImplementation(...) are not generated for addon scripts, so configurations are
// addressed by name.

import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService

import com.gtnewhorizons.retrofuturagradle.MinecraftExtension

dependencies {
    // src/test uses JUnit 4 + the Mockito 1.x API (org.mockito.Matchers, InvocationOnMock#getArgumentAt)
    add("testImplementation", "junit:junit:4.13.2")
    add("testImplementation", "org.mockito:mockito-core:1.10.19")

    // OpenComputers is compileOnly for the mod itself, but the tests exercise the OC adapter directly
    add("testImplementation", "com.github.GTNewHorizons:OpenComputers:1.13.0-GTNH:dev")
}

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

/** "0.11.0" -> "0.12.0", i.e. the exclusive upper bound used by the FML "required-after" range. */
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
