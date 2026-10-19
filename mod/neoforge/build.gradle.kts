plugins {
    id("net.neoforged.moddev") version "1.0.21"
}

val modVersion: String = project.property("mod_version").toString()
val minecraftVersion: String = project.property("minecraft_version").toString()

version = modVersion
group = "com.wayback09.campfire"

java.toolchain.languageVersion.set(JavaLanguageVersion.of(21))

// ModDevGradle forces PREFER_PROJECT, so all repos must be declared here (settings repos are ignored)
repositories {
    mavenCentral()
    maven { url = uri("https://maven.neoforged.net/releases") }
    maven { url = uri("https://maven.isxander.dev/releases") }
    // Quilt maven — required for YACL's transitive deps (quiltmc.parsers:json/gson)
    maven { url = uri("https://maven.quiltmc.org/repository/release") }
}

// ModDevGradle handles NeoForge + MC dependencies via this extension
neoForge {
    version = "21.1.172"
}

dependencies {
    // YACL -- NeoForge flavour (matches 1.21.1 / NeoForge 21.1.x)
    implementation("dev.isxander:yet-another-config-lib:3.8.2+1.21.1-neoforge")

    // Shared common module (pure Java, no MC deps)
    implementation(project(":mod:common"))
}

base {
    archivesName.set("Campfire-NeoForge")
}

tasks.withType<ProcessResources>().configureEach {
    val props = mapOf(
        "version"           to modVersion,
        "minecraft_version" to minecraftVersion,
        "neoforge_version"  to "21.1.172"
    )
    inputs.properties(props)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand(props)
    }
}

tasks.jar {
    from(project(":mod:common").sourceSets["main"].output)
    manifest {
        attributes(mapOf(
            "Specification-Title"    to "campfire",
            "Specification-Version"  to modVersion,
            "Implementation-Title"   to "campfire-neoforge",
            "Implementation-Version" to modVersion
        ))
    }
}
