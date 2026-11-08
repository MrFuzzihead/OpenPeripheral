
plugins {
    id("com.gtnewhorizons.gtnhconvention")
    id("com.github.spotbugs") version "6.5.13"
}

spotbugs {
    effort.set(com.github.spotbugs.snom.Effort.MAX)
    reportLevel.set(com.github.spotbugs.snom.Confidence.HIGH)
    ignoreFailures.set(false)
    // The GTNH convention creates SpotBugs tasks for the patched MC/api source sets too; those are not
    // our code and can fail on the decompiled classes. Run only the main-mod analysis from `check`.
    runOnCheck.set(false)
    excludeFilter.set(layout.projectDirectory.file("gradle/spotbugs-exclude.xml"))
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    reports.create("html") {
        required.set(true)
    }
    reports.create("xml") {
        required.set(true)
    }
}

tasks.named("check").configure {
    dependsOn("spotbugsMain")
}
