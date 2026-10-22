plugins {
    java
}

val modVersion: String = providers.gradleProperty("mod_version").getOrElse("0.1.0")
val fabricMcVersion: String = providers.gradleProperty("minecraft_version").getOrElse("1.21.11")
val neoForgeMcVersion: String = providers.gradleProperty("neoforge_minecraft_version").getOrElse("1.21.1")

allprojects {
    group = "com.wayback09.campfire"
    version = modVersion
}

subprojects {
    apply(plugin = "java")
    java {
        toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
    }
}

// Each loader ships its own Minecraft line, so each jar gets its own version
// suffix instead of inheriting a single global Minecraft version.
project(":mod:fabric") {
    version = "$modVersion+$fabricMcVersion"
}

project(":mod:neoforge") {
    version = "$modVersion+$neoForgeMcVersion"
}
