plugins {
    java
}

allprojects {
    group = "com.wayback09.campfire"
    version = "0.1.0"
}

subprojects {
    apply(plugin = "java")
    java {
        toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
    }
}
