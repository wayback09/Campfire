pluginManagement {
    repositories {
        gradlePluginPortal()
        maven { url = uri("https://maven.fabricmc.net/") }
        maven { url = uri("https://maven.neoforged.net/releases") }
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven { url = uri("https://maven.fabricmc.net/") }
        maven { url = uri("https://maven.neoforged.net/releases") }
        maven { url = uri("https://api.modrinth.com/maven") }
        maven { url = uri("https://maven.isxander.dev/releases") }
    }
}

rootProject.name = "campfire"
include("mod:common")
include("mod:fabric")
include("mod:neoforge")
include("backend:social")
include("backend:relay")
