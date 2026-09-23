plugins {
    id("fabric-loom") version "1.10-SNAPSHOT"
}

dependencies {
    minecraft("com.mojang:minecraft:1.21.8")
    mappings("net.fabricmc:yarn:1.21.8+build.1:v2")
    modImplementation("net.fabricmc:fabric-loader:0.16.14")
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.133.0+1.21.8")
    modImplementation("dev.isxander:yet-another-config-lib:3.6.5+1.21.8-fabric")
    modRuntimeOnly("com.terraformersmc:modmenu:14.0.0")
    implementation(project(":mod:common"))
}

loom {
    splitEnvironmentSourceSets()
}
