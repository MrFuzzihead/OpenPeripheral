// Extra build logic, applied on top of build.gradle.kts (see dependencies.gradle for the main dependency list).
// Accessors such as testImplementation(...) are not generated for addon scripts, so configurations are
// addressed by name.

import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService

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
