// mod:common - pure Java, no Minecraft deps. Compiles with plain javac.
// Platform-specific modules (mod:fabric, mod:neoforge) depend on this.

plugins {
    id("java-library")
}

dependencies {
    // No external deps in common. Pure JDK 21.
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}
