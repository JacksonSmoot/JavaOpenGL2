import org.gradle.internal.os.OperatingSystem
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    application
    java
    // maintained Shadow plugin id
    id("com.gradleup.shadow") version "9.0.0"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

sourceSets {
    main {
        resources {
            // Keep everything by default…
            // …but exclude demos
            exclude( "demos/**")
        }
    }
}

group = "com.jrs"
version = "1.0"

val lwjglVersion = "3.4.1"
val javaCPPVersion = "1.5.8"

val os = OperatingSystem.current()
val arch = System.getProperty("os.arch").lowercase()

val lwjglNativesCurrent = when {
    os.isMacOsX && (arch.contains("aarch64") || arch.contains("arm64")) -> "natives-macos-arm64"
    os.isMacOsX -> "natives-macos"
    os.isWindows -> "natives-windows"
    else -> "natives-linux"
}

// --- Config used ONLY for the "all OS natives" fat jar ---
val runtimeAllNatives by configurations.creating {
    // include everything your app needs at runtime
    extendsFrom(configurations.runtimeClasspath.get())
}

// --- Per-platform runtime configs for platform-specific fat jars ---
val runtimeMacArm64 by configurations.creating { extendsFrom(configurations.runtimeClasspath.get()) }
val runtimeMacX64   by configurations.creating { extendsFrom(configurations.runtimeClasspath.get()) }
val runtimeWindows  by configurations.creating { extendsFrom(configurations.runtimeClasspath.get()) }
val runtimeLinux    by configurations.creating { extendsFrom(configurations.runtimeClasspath.get()) }

dependencies {
    // Align all LWJGL modules to the same version
    implementation(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))

    // LWJGL Implementation
    implementation("org.lwjgl:lwjgl")
    implementation("org.lwjgl:lwjgl-opengl")
    implementation("org.lwjgl:lwjgl-glfw")
    implementation("org.lwjgl:lwjgl-stb")
    implementation("org.lwjgl:lwjgl-openal")

    // FFmpeg Implementation (already cross-platform via -platform)
    implementation("org.bytedeco:javacpp-platform:$javaCPPVersion")
    implementation("org.bytedeco:ffmpeg-platform:5.1.2-$javaCPPVersion")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.12")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.13")

    // Math
    implementation("org.joml:joml:1.10.8")

    // -------- Dev/runtime: CURRENT OS natives only (normal run) --------
    runtimeOnly("org.lwjgl:lwjgl::$lwjglNativesCurrent")
    runtimeOnly("org.lwjgl:lwjgl-opengl::$lwjglNativesCurrent")
    runtimeOnly("org.lwjgl:lwjgl-glfw::$lwjglNativesCurrent")
    runtimeOnly("org.lwjgl:lwjgl-stb::$lwjglNativesCurrent")
    runtimeOnly("org.lwjgl:lwjgl-openal::$lwjglNativesCurrent")

    // -------- Shipping fat jar: ALL OS natives included --------
    // macOS arm64
    add(runtimeAllNatives.name, "org.lwjgl:lwjgl::natives-macos-arm64")
    add(runtimeAllNatives.name, "org.lwjgl:lwjgl-opengl::natives-macos-arm64")
    add(runtimeAllNatives.name, "org.lwjgl:lwjgl-glfw::natives-macos-arm64")
    add(runtimeAllNatives.name, "org.lwjgl:lwjgl-stb::natives-macos-arm64")
    add(runtimeAllNatives.name, "org.lwjgl:lwjgl-openal::natives-macos-arm64")

    // macOS x64
    add(runtimeAllNatives.name, "org.lwjgl:lwjgl::natives-macos")
    add(runtimeAllNatives.name, "org.lwjgl:lwjgl-opengl::natives-macos")
    add(runtimeAllNatives.name, "org.lwjgl:lwjgl-glfw::natives-macos")
    add(runtimeAllNatives.name, "org.lwjgl:lwjgl-stb::natives-macos")
    add(runtimeAllNatives.name, "org.lwjgl:lwjgl-openal::natives-macos")

    // Windows x64
    add(runtimeAllNatives.name, "org.lwjgl:lwjgl::natives-windows")
    add(runtimeAllNatives.name, "org.lwjgl:lwjgl-opengl::natives-windows")
    add(runtimeAllNatives.name, "org.lwjgl:lwjgl-glfw::natives-windows")
    add(runtimeAllNatives.name, "org.lwjgl:lwjgl-stb::natives-windows")
    add(runtimeAllNatives.name, "org.lwjgl:lwjgl-openal::natives-windows")

    // Linux x64
    add(runtimeAllNatives.name, "org.lwjgl:lwjgl::natives-linux")
    add(runtimeAllNatives.name, "org.lwjgl:lwjgl-opengl::natives-linux")
    add(runtimeAllNatives.name, "org.lwjgl:lwjgl-glfw::natives-linux")
    add(runtimeAllNatives.name, "org.lwjgl:lwjgl-stb::natives-linux")
    add(runtimeAllNatives.name, "org.lwjgl:lwjgl-openal::natives-linux")

    // --- Platform-specific jars ---
    // macOS arm64
    add(runtimeMacArm64.name, "org.lwjgl:lwjgl::natives-macos-arm64")
    add(runtimeMacArm64.name, "org.lwjgl:lwjgl-opengl::natives-macos-arm64")
    add(runtimeMacArm64.name, "org.lwjgl:lwjgl-glfw::natives-macos-arm64")
    add(runtimeMacArm64.name, "org.lwjgl:lwjgl-stb::natives-macos-arm64")
    add(runtimeMacArm64.name, "org.lwjgl:lwjgl-openal::natives-macos-arm64")

    // macOS x64
    add(runtimeMacX64.name, "org.lwjgl:lwjgl::natives-macos")
    add(runtimeMacX64.name, "org.lwjgl:lwjgl-opengl::natives-macos")
    add(runtimeMacX64.name, "org.lwjgl:lwjgl-glfw::natives-macos")
    add(runtimeMacX64.name, "org.lwjgl:lwjgl-stb::natives-macos")
    add(runtimeMacX64.name, "org.lwjgl:lwjgl-openal::natives-macos")

    // Windows x64
    add(runtimeWindows.name, "org.lwjgl:lwjgl::natives-windows")
    add(runtimeWindows.name, "org.lwjgl:lwjgl-opengl::natives-windows")
    add(runtimeWindows.name, "org.lwjgl:lwjgl-glfw::natives-windows")
    add(runtimeWindows.name, "org.lwjgl:lwjgl-stb::natives-windows")
    add(runtimeWindows.name, "org.lwjgl:lwjgl-openal::natives-windows")

    // Linux x64
    add(runtimeLinux.name, "org.lwjgl:lwjgl::natives-linux")
    add(runtimeLinux.name, "org.lwjgl:lwjgl-opengl::natives-linux")
    add(runtimeLinux.name, "org.lwjgl:lwjgl-glfw::natives-linux")
    add(runtimeLinux.name, "org.lwjgl:lwjgl-stb::natives-linux")
    add(runtimeLinux.name, "org.lwjgl:lwjgl-openal::natives-linux")
}

fun registerPlatformShadowJar(taskName: String, classifier: String, cfg: Configuration) =
    tasks.register<ShadowJar>(taskName) {
        group = "distribution"
        description = "Fat jar for $classifier"

        archiveClassifier.set(classifier)
        manifest { attributes["Main-Class"] = application.mainClass.get() }

        // Ensure your own classes are included
        from(sourceSets.main.get().output)

        configurations = listOf(cfg)

        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
        mergeServiceFiles()
    }

val shadowJarMacArm64 = registerPlatformShadowJar("shadowJarMacArm64", "macos-arm64", runtimeMacArm64)
val shadowJarMacX64   = registerPlatformShadowJar("shadowJarMacX64",   "macos-x64",   runtimeMacX64)
val shadowJarWindows  = registerPlatformShadowJar("shadowJarWindows",  "windows-x64", runtimeWindows)
val shadowJarLinux    = registerPlatformShadowJar("shadowJarLinux",    "linux-x64",   runtimeLinux)

tasks.register("shipPerOs") {
    group = "distribution"
    description = "Build all per-OS fat jars"
    dependsOn(shadowJarMacArm64, shadowJarMacX64, shadowJarWindows, shadowJarLinux)
}

application {
    mainClass.set("com.jrs.engine.Main")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
}

tasks.named<JavaExec>("run") {
    if (os.isMacOsX) jvmArgs("-XstartOnFirstThread")
}

// Jar that defaults to the current OS
tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set("all")
    manifest { attributes["Main-Class"] = application.mainClass.get() }
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    mergeServiceFiles()
}

tasks.register<ShadowJar>("shadowJarAllOs") {
    group = "distribution"
    description = "Fat jar with ALL LWJGL natives for mac/win/linux."

    archiveClassifier.set("all-os")
    manifest { attributes["Main-Class"] = application.mainClass.get() }

    from(sourceSets.main.get().output)

    configurations = listOf(runtimeAllNatives)

    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    mergeServiceFiles()
}