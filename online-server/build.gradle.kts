plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}
application { mainClass.set("com.failureludo.server.MainKt") }
dependencies {
    implementation(project(":game-engine"))
    implementation(project(":online-protocol"))
    implementation("org.json:json:20240303")
    implementation("com.google.firebase:firebase-admin:9.11.0")
    testImplementation(libs.junit)
}

// Run explicitly against a local emulator; ordinary unit tests never use a backend.
tasks.test { exclude("**/FirestoreIntegrationTest.class") }
tasks.register<Test>("integrationTest") {
    description = "Verify transactions and security rules against the local Firestore emulator"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include("**/FirestoreIntegrationTest.class")
    environment("FIRESTORE_EMULATOR_HOST", providers.environmentVariable("FIRESTORE_EMULATOR_HOST").getOrElse(""))
    outputs.upToDateWhen { false }
}
