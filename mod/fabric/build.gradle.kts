plugins {
    id("fabric-loom") version "1.19.2"
}

repositories {
    mavenCentral()
    maven { url = uri("https://maven.fabricmc.net/") }
    maven { url = uri("https://maven.isxander.dev/releases") }
    maven { url = uri("https://maven.terraformersmc.com/") }
    maven { url = uri("https://api.modrinth.com/maven") }
}

// Every version below is driven by gradle.properties so the Fabric target can
// be moved to a new Minecraft release by editing one file.
val minecraftVersion: String = providers.gradleProperty("minecraft_version").get()
val yarnVersion: String = providers.gradleProperty("yarn_version").get()
val yarnMappings: String = providers.gradleProperty("yarn_mappings").getOrElse("v2")
val fabricLoaderVersion: String = providers.gradleProperty("fabric_loader_version").get()
val fabricApiVersion: String = providers.gradleProperty("fabric_api_version").get()
val yaclVersion: String = providers.gradleProperty("yacl_version").get()
val modmenuVersion: String = providers.gradleProperty("modmenu_version").get()

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings("net.fabricmc:yarn:$yarnVersion:$yarnMappings")
    modImplementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    modImplementation("dev.isxander:yet-another-config-lib:$yaclVersion")
    modCompileOnly("com.terraformersmc:modmenu:$modmenuVersion")
    modRuntimeOnly("com.terraformersmc:modmenu:$modmenuVersion")
    implementation(project(":mod:common"))
}

base {
    archivesName.set("Campfire")
}

tasks.processResources {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    inputs.property("version", project.version)
    inputs.property("minecraft_version", minecraftVersion)
    filesMatching("fabric.mod.json") {
        expand(mapOf(
            "version" to project.version,
            "minecraft_version" to minecraftVersion
        ))
    }
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(project(":mod:common").sourceSets["main"].output)
}
