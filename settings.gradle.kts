pluginManagement {
    repositories {
        gradlePluginPortal()
        maven { url = uri("https://maven.fabricmc.net/") }
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven { url = uri("https://maven.fabricmc.net/") }
        maven { url = uri("https://api.modrinth.com/maven") }
    }
}

rootProject.name = "campfire"
include("mod:common")
include("mod:fabric")
include("backend:social")
include("backend:relay")
