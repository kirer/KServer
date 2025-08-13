@file:Suppress("UnstableApiUsage")

import java.util.Locale

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.github.kirer.server"
    compileSdk = 35

    defaultConfig {
        minSdk = 24

        consumerProguardFiles("consumer-rules.pro")

        ndk {
            //noinspection ChromeOsAbiSupport
            abiFilters += listOf("arm64-v8a")  // 只构建 64-bit 架构，与app-server保持一致
        }

        ndkVersion = "29.0.13113456"

        // 配置CMake
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += listOf("-DANDROID_STL=c++_shared")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    // 配置native库
    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }

    // 配置CMake构建
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    // 引用scrcpy的包装类来处理隐藏API
    implementation(project(":server-scrcpy"))
}

val isWindows = System.getProperty("os.name").lowercase().contains("windows")
val androidHome: String? =
    System.getenv("ANDROID_HOME") ?: throw GradleException("ANDROID_HOME 未设置")
val ndkFromEnv: String? = System.getenv("ANDROID_NDK") ?: System.getenv("ANDROID_NDK_ROOT")
val javaHome = System.getenv("JAVA_HOME") ?: throw GradleException("JAVA_HOME 未设置")

/**
 * 查找Android NDK路径
 */
fun findAndroidNdk(): String {
    if (ndkFromEnv != null && File(ndkFromEnv).exists()) {
        return ndkFromEnv
    }
    val ndkDir = File(androidHome, "ndk")
    if (ndkDir.exists()) {
        val ndkVersions = ndkDir.listFiles()?.filter {
            it.isDirectory && it.name.matches(Regex("\\d+\\..*"))
        }?.sortedByDescending { it.name }
        if (ndkVersions?.isNotEmpty() == true) {
            println("✅ 找到NDK: ${ndkVersions.first().absolutePath}")
            return ndkVersions.first().absolutePath
        }
    }
    throw GradleException("❌ 未找到Android NDK。请设置环境变量 ANDROID_NDK 或 ANDROID_NDK_ROOT")
}

/**
 * 查找CMake工具路径
 */
fun findCMakePath(): String {
    val possiblePaths = when {
        isWindows -> File("${androidHome}\\cmake").listFiles()
            .map { "${it.absolutePath}\\bin\\cmake.exe" }

        else -> File("${androidHome}/cmake").listFiles().map { "${it.absolutePath}/bin/cmake" }
    }
    for (path in possiblePaths) {
        try {
            val process = ProcessBuilder(path, "--version").redirectErrorStream(true).start()
            val result = process.waitFor()
            if (result == 0) {
                val output = process.inputStream.bufferedReader().readText()
                println("✅ 找到CMake: $path ${output.lines().firstOrNull() ?: "未知版本"}")
                return path
            }
        } catch (ignored: Exception) {
        }
    }
    throw GradleException("❌ 未找到CMake工具，Android SDK MANAGER 中未安装")
}

/**
 * 查找d8工具路径
 */
fun findD8Tool(): String {
    val path = File(androidHome, "build-tools").listFiles()
        ?.filter { it.isDirectory && it.name.matches(Regex("\\d+\\..*")) }?.maxByOrNull { it.name }
        ?.let { File(it, (if (isWindows) "d8.bat" else "d8")) }
    if (path?.exists() == true) {
        println("✅ 找到d8: ${path.absolutePath}")
        return path.absolutePath
    }
    throw GradleException("❌ 未找到d8工具，请检查Android SDK安装")
}

/**
 * 查找JAR
 * */
fun findJar(): String {
    val path = File(javaHome, "bin/${if (isWindows) "jar.exe" else "jar"}")
    if (path.exists() == true) {
        println("✅ 找到jar: ${path.absolutePath}")
        return path.absolutePath
    }
    throw GradleException("❌ 未找到jar，请检查JDK安装")
}

/**
 * 将DEX和SO文件转换为C头文件
 */
fun generateFileHeader(inputFile: File, outputFile: File, varName: String) {
    if (!inputFile.exists()) {
        throw GradleException("❌ 文件不存在: ${inputFile.absolutePath}")
    }
    val bytes = inputFile.readBytes()
    outputFile.writeText(
        """
#ifndef ${varName.uppercase(Locale.getDefault())}_H
#define ${varName.uppercase(Locale.getDefault())}_H
unsigned char ${varName}_data[] = {
${
            bytes.withIndex().chunked(12) { chunk ->
                "  " + chunk.joinToString(", ") { (_, byte) ->
                    "0x%02x".format(byte.toInt() and 0xFF)
                }
            }.joinToString(",\n")
        }
};
unsigned int ${varName}_len = ${bytes.size};
#endif // ${varName.uppercase(Locale.getDefault())}_H
""".trimIndent()
    )
    println("✅ 生成 ${outputFile.name}: ${bytes.size} 字节")
}

/**
 * 构建独立服务器程序，产出k-server.dex和libserver-k.so
 */
tasks.register("buildServer") {
    group = "server"
    description = "构建独立服务器程序"
    dependsOn("compileDebugJavaWithJavac")
    doLast {
        val projectRoot = project.rootDir
        val sourceDir = File(projectRoot, "server-k/src/main/cpp")
        val buildDir = File(projectRoot, "build/server-build")
        val outputDir = File(projectRoot, "build/server")
        val libDir = File(outputDir, "lib/arm64-v8a")
        println("=== 🏗️ 构建独立服务器程序 ===")
        // 清理和创建目录
        if (buildDir.exists()) buildDir.deleteRecursively()
        buildDir.mkdirs()
        outputDir.mkdirs()
        libDir.mkdirs()
        // 步骤1: 使用统一CMakeLists.txt编译Server SO
        println("📦 步骤1: 使用统一CMakeLists.txt编译Server SO库...")
        val cmake = findCMakePath()
        val ninja = "${File(cmake).parent}${if (isWindows) "\\ninja.exe" else "/ninja"}"
        val ndk = findAndroidNdk()
        val cmakeBuildDir = File(buildDir, "cmake-build").apply { mkdirs() }
        // CMake配置 - 使用统一的CMakeLists.txt和BUILD_SERVER_ONLY选项
        if (providers.exec {
                workingDir(buildDir)
                commandLine(
                    listOf(
                        cmake,
                        "-G", "Ninja",
                        "-DCMAKE_MAKE_PROGRAM=${ninja}",
                        "-DCMAKE_TOOLCHAIN_FILE=${ndk}/build/cmake/android.toolchain.cmake",
                        "-DANDROID_ABI=arm64-v8a",
                        "-DANDROID_PLATFORM=android-24",
                        "-DCMAKE_BUILD_TYPE=Release",
                        "-DBUILD_SERVER_ONLY=ON",
                        "-S", sourceDir.absolutePath,
                        "-B", cmakeBuildDir.absolutePath
                    )
                )
                isIgnoreExitValue = true
            }.result.get().exitValue != 0) {
            throw GradleException("❌ CMake配置失败")
        }
        // 编译
        if (providers.exec {
                workingDir(cmakeBuildDir)
                commandLine(ninja, "-j${Runtime.getRuntime().availableProcessors()}")
                isIgnoreExitValue = true
            }.result.get().exitValue != 0) {
            throw GradleException("❌ 编译失败")
        }
        // 复制SO文件 - 从统一CMakeLists.txt的输出目录
        val serverSo = File(projectRoot, "server-k/build/server-libs/libserver-k-server.so")
        if (!serverSo.exists()) {
            throw GradleException("❌ 未找到编译的SO文件: ${serverSo.absolutePath}")
        }
        val targetSo = File(libDir, "lib.so")
        serverSo.copyTo(targetSo, overwrite = true)
        println("✅ SO库编译完成: ${targetSo.length()} 字节")
        // 步骤2: 编译Java类为DEX
        println("📦 步骤2: 编译Java类为DEX...")
        val classesDir = File(projectRoot, "server-k/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes")
        if (!classesDir.exists()) {
            throw GradleException("❌ Java类文件目录不存在: ${classesDir.absolutePath}")
        }
        // 创建临时JAR
        val tempJar = File(buildDir, "server-classes.jar")
        if (providers.exec {
                workingDir(classesDir)
                commandLine(findJar(), "cf", tempJar.absolutePath, ".")
                isIgnoreExitValue = true
            }.result.get().exitValue != 0) {
            throw GradleException("❌ 创建JAR失败")
        }
        // 收集依赖JAR
        val jars = mutableListOf<String>()
        configurations.getByName("debugRuntimeClasspath").files.forEach { file ->
            if (file.name.endsWith(".jar") && 
                !file.name.contains("kotlin-stdlib") && 
                !file.name.contains("gson") && 
                !file.name.contains("annotations")) {
                jars.add(file.absolutePath)
            }
        }
        // 生成DEX
        if (providers.exec {
                commandLine(
                    listOf(findD8Tool(), "--min-api", "24", "--output", outputDir.absolutePath, tempJar.absolutePath) + jars
                )
                isIgnoreExitValue = true
            }.result.get().exitValue != 0) {
            throw GradleException("❌ DEX生成失败")
        }
        // 重命名DEX文件
        val generatedDex = File(outputDir, "classes.dex")
        val targetDex = File(outputDir, "server.dex")
        if (generatedDex.exists()) {
            generatedDex.renameTo(targetDex)
        } else {
            throw GradleException("❌ DEX文件生成失败: ${generatedDex.absolutePath} 不存在")
        }
        // 验证输出文件
        if (!targetDex.exists()) {
            throw GradleException("❌ DEX文件不存在: ${targetDex.absolutePath}")
        }
        if (!targetSo.exists()) {
            throw GradleException("❌ SO文件不存在: ${targetSo.absolutePath}")
        }
        println("✅ DEX文件生成完成: ${targetDex.length()} 字节")
        // 显示结果
        println("=== 🎉 服务器构建完成 ===")
        println("📁 输出目录: ${outputDir.absolutePath}")
        println("📦 输出文件:")
        println("  - server.dex: ${targetDex.length()} 字节")
        println("  - lib.so: ${targetSo.length()} 字节")
        println("🚀 部署命令:")
        println("adb push ${outputDir.absolutePath}/* /data/local/tmp/")
        println("▶️  启动服务器:")
        println("adb shell 'chmod +x /data/local/tmp/server.dex && chmod +x /data/local/tmp/lib/arm64-v8a/lib.so && CLASSPATH=/data/local/tmp/server.dex app_process /system/bin com.github.kirer.server.Launcher --libPath=/data/local/tmp/lib/arm64-v8a ----socket-type=tcp --address=127.0.0.1:7777 --debug > /data/local/tmp/k-server.log 2>&1 &'")
        println("adb shell 'chmod +x /data/local/tmp/server.dex && chmod +x /data/local/tmp/lib/arm64-v8a/lib.so && CLASSPATH=/data/local/tmp/server.dex app_process /system/bin com.github.kirer.server.Launcher --libPath=/data/local/tmp/lib/arm64-v8a ----socket-type=unix --address=k.socket --debug > /data/local/tmp/k-server.log 2>&1 &'")
        println("❌ 停止服务器:")
        println("adb shell 'pkill -f com.github.kirer.server.Launcher'")

    }
}

/**
 * 将DEX和SO文件转换为C头文件
 */
tasks.register("generateEmbeddedFiles") {
    group = "build"
    description = "将server.dex和lib.so转换为C头文件"
    dependsOn("buildServer")
    doLast {
        val projectRoot = project.rootDir
        val outputDir = File(projectRoot, "build/server")
        val dexFile = File(outputDir, "server.dex")
        val soFile = File(outputDir, "lib/arm64-v8a/lib.so")
        val headerDir = File(projectRoot, "server-k/src/main/cpp/client/embedded")
        headerDir.mkdirs()
        generateFileHeader(dexFile, File(headerDir, "server_dex.h"), "server")
        generateFileHeader(soFile, File(headerDir, "lib_so.h"), "lib")
        println("=== 🎉 嵌入文件生成完成 ===")
        println("📁 头文件目录: ${headerDir.absolutePath}")
        println("📦 生成的头文件:")
        println("  - server_dex.h: ${dexFile.length()} 字节")
        println("  - lib_so.h: ${soFile.length()} 字节")
    }
}

/**
 * 构建客户端可执行程序
 */
tasks.register("buildClient") {
    group = "client"
    description = "构建客户端可执行程序"
    dependsOn("generateEmbeddedFiles")
    doLast {
        val projectRoot = project.rootDir
        val sourceDir = File(projectRoot, "server-k/src/main/cpp")
        val buildDir = File(projectRoot, "build/client-build")
        val outputDir = File(projectRoot, "build/client")
        println("=== 🏗️ 构建客户端程序 ===")
        // 清理和创建目录
        if (buildDir.exists()) buildDir.deleteRecursively()
        buildDir.mkdirs()
        outputDir.mkdirs()
        // 使用统一CMakeLists.txt编译客户端
        val cmake = findCMakePath()
        val ninja = "${File(cmake).parent}${if (isWindows) "\\ninja.exe" else "/ninja"}"
        val ndk = findAndroidNdk()
        val cmakeBuildDir = File(buildDir, "cmake-build").apply { mkdirs() }
        // CMake配置 - 使用统一的CMakeLists.txt和BUILD_CLIENT选项
        if (providers.exec {
                workingDir(buildDir)
                commandLine(
                    listOf(
                        cmake,
                        "-G", "Ninja",
                        "-DCMAKE_MAKE_PROGRAM=${ninja}",
                        "-DCMAKE_TOOLCHAIN_FILE=${ndk}/build/cmake/android.toolchain.cmake",
                        "-DANDROID_ABI=arm64-v8a",
                        "-DANDROID_PLATFORM=android-24",
                        "-DCMAKE_BUILD_TYPE=Release",
                        "-DBUILD_CLIENT=ON",
                        "-S", sourceDir.absolutePath,
                        "-B", cmakeBuildDir.absolutePath
                    )
                )
                isIgnoreExitValue = true
            }.result.get().exitValue != 0) {
            throw GradleException("❌ CMake配置失败")
        }
        // 编译
        if (providers.exec {
                workingDir(cmakeBuildDir)
                commandLine(ninja, "-j${Runtime.getRuntime().availableProcessors()}")
                isIgnoreExitValue = true
            }.result.get().exitValue != 0) {
            throw GradleException("❌ 编译失败")
        }
        // 复制可执行文件 - 从统一CMakeLists.txt的输出目录
        val clientBinary = File(projectRoot, "server-k/build/client-bin/k-client")
        if (!clientBinary.exists()) {
            throw GradleException("❌ 未找到编译的客户端文件: ${clientBinary.absolutePath}")
        }
        val targetBinary = File(outputDir, "client")
        clientBinary.copyTo(targetBinary, overwrite = true)
        // 验证输出文件
        if (!targetBinary.exists()) {
            throw GradleException("❌ 客户端文件复制失败: ${targetBinary.absolutePath}")
        }
        println("=== 🎉 客户端构建完成 ===")
        println("📁 输出目录: ${outputDir.absolutePath}")
        println("📦 输出文件:")
        println("  - client: ${targetBinary.length()} 字节")
        println("🚀 部署命令:")
        println("adb push ${targetBinary.absolutePath} /data/local/tmp/")
        println("▶️  运行示例:")
        println("adb shell 'cd /data/local/tmp && chmod +x client && ./client --socket-type=TCP --address=127.0.0.1:7777 --auto-start-server --debug'")
    }
}

