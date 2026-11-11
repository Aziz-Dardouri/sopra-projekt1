import edu.udo.cs.sopra.util.ignoreClassesInCoverageReport
import edu.udo.cs.sopra.util.sonatypeSnapshots
import org.gradle.kotlin.dsl.application

plugins {
    kotlin("jvm") version "2.3.0"
    application
    id("edu.udo.cs.sopra") version "1.0.3"
}

group = "edu.udo.cs.sopra"
version = "1.0"

/* Change this to the version of the BGW you want to use */
val bgwVersion = "0.11"

kotlin {
    jvmToolchain(11)
}

application {
    mainClass.set("MainKt")
    /* The SoPra plugin adds these flags only when the build runs on a Mac. The CI builds on Linux,
     * so they were missing in the distribution.zip and JCEF did not start on macOS.
     * On Windows and Linux they only print a warning ("package ... not in java.desktop"). */
    applicationDefaultJvmArgs += listOf(
        "--add-opens", "java.desktop/sun.awt=ALL-UNNAMED",
        "--add-opens", "java.desktop/sun.lwawt=ALL-UNNAMED",
        "--add-opens", "java.desktop/sun.lwawt.macosx=ALL-UNNAMED",
    )
}

repositories {
    mavenCentral()
    sonatypeSnapshots()
}

dependencies {
    testImplementation(kotlin("test-junit5"))
    implementation(group = "tools.aqua", name = "bgw-gui", version = bgwVersion)
}

/* This is how you can ignore additional classes from test coverage */
/* All classes in gui, entity and service.bot package are already excluded. */

/* To ignore a class Foo in the package foo.bar.baz you would use the following line */
// this.ignoreClassesInCoverageReport("foo.bar.baz.Foo")

/* To ignore all classes in the foo.bar.baz package use a wildcard like this */
// this.ignoreClassesInCoverageReport("foo.bar.baz.*")

tasks.clean {
    delete.add("public")
}
