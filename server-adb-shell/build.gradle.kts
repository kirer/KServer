import org.gradle.internal.os.OperatingSystem
plugins {
    id("java-library")
    alias(libs.plugins.jetbrains.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}

val androidJar = "${System.getenv("ANDROID_HOME")}/platforms/android-35/android.jar"
val buildToolsDir = "${System.getenv("ANDROID_HOME")}/build-tools/35.0.0"
val d8 = if (OperatingSystem.current().isWindows) {
    "$buildToolsDir/d8.bat"
} else {
    "$buildToolsDir/d8"
}

dependencies{
    compileOnly(files(androidJar))
    implementation(project(":server-scrcpy"))
}

tasks.register<Exec>("buildDex") {
    dependsOn(tasks.jar)
    // 确保依赖项目的jar也被构建
    dependsOn(":server-scrcpy:jar")
    group = "build"
    description = "Convert jar to dex using d8"
    val dexOut = layout.buildDirectory.dir("dex").get().asFile
    doFirst {
        dexOut.mkdirs()
    }

    // 收集所有依赖的JAR文件
    val allJars = mutableListOf<String>()
    allJars.add(tasks.jar.get().archiveFile.get().asFile.absolutePath)

    // 添加项目依赖
    configurations.runtimeClasspath.get().files.forEach { file ->
        if (file.name.endsWith(".jar")) {
            allJars.add(file.absolutePath)
        }
    }

    commandLine(
        d8,
        "--lib", androidJar,
        "--min-api", "21",
        "--output", dexOut.absolutePath,
        *allJars.toTypedArray()
    )
}
