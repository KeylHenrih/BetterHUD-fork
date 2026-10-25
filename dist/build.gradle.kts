plugins {
    alias(libs.plugins.conventions.standard)
}

dependencies {
    implementation(project(":api"))
    compileOnly(libs.bundles.adventure)
    compileOnly(libs.bundles.library)

    testImplementation(libs.bundles.library)
    testImplementation(libs.bundles.adventure)
    testImplementation("com.mojang:brigadier:1.0.18")

    compileOnly("me.lucko:jar-relocator:1.7")
}

tasks.register<Test>("shaderLint") {
    group = "verification"
    description = "Lints all generated shaders using glslangValidator"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform()
    filter {
        includeTestsMatching("*ShaderLintTest*")
    }
}
tasks.named<Test>("test") {
    dependsOn("shaderLint")
}