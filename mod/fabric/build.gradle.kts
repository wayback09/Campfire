plugins {
    id("fabric-loom") version "1.10-SNAPSHOT"
}

repositories {
    mavenCentral()
    maven { url = uri("https://maven.fabricmc.net/") }
    maven { url = uri("https://maven.isxander.dev/releases") }
    maven { url = uri("https://maven.terraformersmc.com/") }
    maven { url = uri("https://api.modrinth.com/maven") }
}

dependencies {
    minecraft("com.mojang:minecraft:1.21.8")
    mappings("net.fabricmc:yarn:1.21.8+build.1:v2")
    modImplementation("net.fabricmc:fabric-loader:0.16.14")
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.133.0+1.21.8")
    modImplementation("dev.isxander:yet-another-config-lib:3.6.5+1.21.4-fabric")
    modCompileOnly("com.terraformersmc:modmenu:14.0.0")
    modRuntimeOnly("com.terraformersmc:modmenu:14.0.0")
    implementation(project(":mod:common"))
}

base {
    archivesName.set("Campfire")
}

tasks.processResources {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand(mapOf("version" to project.version))
    }
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(project(":mod:common").sourceSets["main"].output)
}


