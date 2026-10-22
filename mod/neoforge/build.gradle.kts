plugins {
    id("net.neoforged.moddev") version "1.0.21"
}

val modVersion: String = providers.gradleProperty("mod_version").get()
val minecraftVersion: String = providers.gradleProperty("neoforge_minecraft_version").get()
val neoForgeVersion: String = providers.gradleProperty("neoforge_version").get()
val yaclVersion: String = providers.gradleProperty("yacl_neoforge_version").get()

// group / version are set by the root build script so each loader jar carries
// its own Minecraft suffix (e.g. 0.1.0+1.21.1).

java.toolchain.languageVersion.set(JavaLanguageVersion.of(21))

// ModDevGradle forces PREFER_PROJECT, so all repos must be declared here (settings repos are ignored)
repositories {
    mavenCentral()
    maven { url = uri("https://maven.neoforged.net/releases") }
    maven { url = uri("https://maven.isxander.dev/releases") }
    // Quilt maven - required for YACL's transitive deps (quiltmc.parsers:json/gson)
    maven { url = uri("https://maven.quiltmc.org/repository/release") }
}

// ModDevGradle handles NeoForge + MC dependencies via this extension
neoForge {
    version = neoForgeVersion
}

dependencies {
    // YACL -- NeoForge flavour
    implementation("dev.isxander:yet-another-config-lib:$yaclVersion")

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
        "neoforge_version"  to neoForgeVersion
    )
    inputs.properties(props)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand(props)
    }
}

tasks.jar {
    // The common module is shaded into the loader jar; shared assets such as the
    // flame font live only in :mod:common so the two copies never diverge.
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
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
