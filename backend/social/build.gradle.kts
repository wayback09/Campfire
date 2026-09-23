dependencies {
    // v1 runs on JDK HttpServer (zero deps) so `javac` verifies locally.
    // Swap to Javalin + sqlite-jdbc + HikariCP when deploying (see docs/libraries.md).
}
