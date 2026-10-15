plugins {
    java
}

val modVersion = project.findProperty("mod_version") ?: "0.1.0"
val mcVersion = project.findProperty("minecraft_version") ?: "1.21.8"

allprojects {
    group = "com.wayback09.campfire"
    version = "$modVersion+$mcVersion"
}

subprojects {
    apply(plugin = "java")
    java {
        toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
    }
}

