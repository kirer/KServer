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

fun normalizePath(path: String): String {
    return path.replace("\\", "/")
}

/**
 * 生成客户端专用的CMakeLists.txt内容（支持OpenCV）
 */
fun generateClientCMakeContent(sourceDir: File, binDir: File): String {
    val srcPath = normalizePath(sourceDir.absolutePath)
    val outPath = normalizePath(binDir.absolutePath)
    return """
cmake_minimum_required(VERSION 3.22.1)

project("k-client" C CXX)

# 设置C标准
set(CMAKE_C_STANDARD 99)
set(CMAKE_C_STANDARD_REQUIRED ON)

# 设置C++标准
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 添加编译选项
add_compile_options(-Wall -Wextra -Wno-unused-parameter -Wno-unused-function -O2)

# 定义宏，禁用Android和JNI相关代码
add_definitions(-UANDROID -U__ANDROID__ -DDISABLE_JNI)

# 设置OpenCV路径
set(OpenCV_DIR "${srcPath}/client/opencv")
set(OpenCV_INCLUDE_DIRS "${srcPath}/client/opencv/include")
set(OpenCV_STATIC_LIBS_DIR "${srcPath}/client/opencv/libs")

# 包含OpenCV头文件
include_directories(${'$'}{OpenCV_INCLUDE_DIRS})

# 包含嵌入文件头文件
include_directories("${srcPath}/client/embedded")

# 手动设置OpenCV库（按依赖顺序）
set(OpenCV_LIBS
    ${'$'}{OpenCV_STATIC_LIBS_DIR}/libopencv_imgcodecs.a
    ${'$'}{OpenCV_STATIC_LIBS_DIR}/libopencv_imgproc.a
    ${'$'}{OpenCV_STATIC_LIBS_DIR}/libopencv_core.a
)

# 添加第三方依赖库
set(OpenCV_3RDPARTY_LIBS_DIR "${srcPath}/client/opencv/3rdparty")
set(OpenCV_3RDPARTY_LIBS
    ${'$'}{OpenCV_3RDPARTY_LIBS_DIR}/libtegra_hal.a
    ${'$'}{OpenCV_3RDPARTY_LIBS_DIR}/libtbb.a
    ${'$'}{OpenCV_3RDPARTY_LIBS_DIR}/libittnotify.a
    ${'$'}{OpenCV_3RDPARTY_LIBS_DIR}/liblibjpeg-turbo.a
    ${'$'}{OpenCV_3RDPARTY_LIBS_DIR}/liblibpng.a
    ${'$'}{OpenCV_3RDPARTY_LIBS_DIR}/liblibwebp.a
    ${'$'}{OpenCV_3RDPARTY_LIBS_DIR}/liblibtiff.a
    ${'$'}{OpenCV_3RDPARTY_LIBS_DIR}/libIlmImf.a
    ${'$'}{OpenCV_3RDPARTY_LIBS_DIR}/liblibopenjp2.a
)

message(STATUS "✅ Using local OpenCV Android SDK")
message(STATUS "OpenCV include dirs: ${'$'}{OpenCV_INCLUDE_DIRS}")
message(STATUS "OpenCV static libs: ${'$'}{OpenCV_STATIC_LIBS_DIR}")
message(STATUS "OpenCV libraries: ${'$'}{OpenCV_LIBS}")

# 创建客户端可执行文件
add_executable(k-client
    ${srcPath}/client/main.c
    ${srcPath}/client/client_opencv.cpp
    ${srcPath}/client/core/client_core.c
    ${srcPath}/client/transport/client_shared_memory.c
    ${srcPath}/client/transport/client_unix.c
    ${srcPath}/client/transport/client_tcp.c
    ${srcPath}/common/ashmem.c
    ${srcPath}/common/log.c
)

# 链接OpenCV静态库和依赖库
target_link_libraries(k-client ${'$'}{OpenCV_LIBS} ${'$'}{OpenCV_3RDPARTY_LIBS})

# 链接Android系统库
if(ANDROID)
    find_library(log-lib log)
    find_library(z-lib z)
    if(log-lib)
        target_link_libraries(k-client ${'$'}{log-lib})
    endif()
    if(z-lib)
        target_link_libraries(k-client ${'$'}{z-lib})
    endif()
    # Android上pthread是内置的，不需要单独链接
else()
    find_library(pthread-lib pthread)
    if(pthread-lib)
        target_link_libraries(k-client ${'$'}{pthread-lib})
    else()
        target_link_libraries(k-client pthread)
    endif()
endif()

# 链接C++标准库
target_link_libraries(k-client -static-libstdc++)

# 设置输出目录
set_target_properties(k-client PROPERTIES
    RUNTIME_OUTPUT_DIRECTORY "$outPath"
)
""".trimIndent()
}

/**
 * 生成服务器专用的CMakeLists.txt内容
 */
fun generateServerCMakeContent(sourceDir: File, binDir: File): String {
    val srcPath = normalizePath(sourceDir.absolutePath)
    val outPath = normalizePath(binDir.absolutePath)
    return """
cmake_minimum_required(VERSION 3.22.1)

project("server-k-server" C)

# 设置C标准
set(CMAKE_C_STANDARD 99)

# 添加编译选项
add_compile_options(-Wall -Wextra -Wno-unused-parameter -Wno-unused-function -O2)

# 创建服务器共享库（只包含服务器功能，不包含客户端）
add_library(server-k SHARED
    ${srcPath}/server/core/server_core.c
    ${srcPath}/server/jni/server_jni_bridge.c
    ${srcPath}/server/transport/server_shared_memory.c
    ${srcPath}/server/transport/server_unix.c
    ${srcPath}/server/transport/server_tcp.c
    ${srcPath}/common/ashmem.c
    ${srcPath}/common/log.c
)

# 链接Android日志库
if(ANDROID)
    find_library(log-lib log)
    if(log-lib)
        target_link_libraries(server-k ${'$'}{log-lib})
    endif()
endif()

# 在Android上，pthread功能内置在libc中，不需要单独链接
# 只在非Android平台上链接pthread库
if(NOT ANDROID)
    find_library(pthread-lib pthread)
    if(pthread-lib)
        target_link_libraries(server-k ${'$'}{pthread-lib})
    else()
        target_link_libraries(server-k pthread)
    endif()
endif()

# 设置输出目录
set_target_properties(server-k PROPERTIES
    LIBRARY_OUTPUT_DIRECTORY "$outPath"
)
""".trimIndent()
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
 * 构建服务器程序，产出server-k-server.dex和libserver-k.so
 */
tasks.register("buildServer") {
    group = "server"
    description = "构建服务器程序，产出k-server.dex和libserver-k.so"
    dependsOn("compileDebugJavaWithJavac")
    doLast {
        val projectRoot = project.rootDir
        val sourceDir = File(projectRoot, "server-k/src/main/cpp")
        val buildDir = File(projectRoot, "build/server-android")
        val binDir = File(projectRoot, "build/bin-server")
        val outputDir = File(projectRoot, "server-k/build/k-server-output")
        val libDir = File(outputDir, "lib/arm64-v8a")
        println("=== Server 构建服务器程序 ===")
        println("源码目录: ${sourceDir.absolutePath}")
        println("构建目录: ${buildDir.absolutePath}")
        println("输出目录: ${outputDir.absolutePath}")
        if (!sourceDir.exists()) {
            throw GradleException("❌ 源码目录不存在: ${sourceDir.absolutePath}")
        }
        if (buildDir.exists()) {
            buildDir.deleteRecursively()
        }
        buildDir.mkdirs()
        binDir.mkdirs()
        outputDir.mkdirs()
        libDir.mkdirs()
        val serverCMakeFile = File(buildDir, "CMakeLists.txt")
        serverCMakeFile.writeText(generateServerCMakeContent(sourceDir, binDir))
        // 配置CMake
        val cmakeBuildDir = File(buildDir, "cmake-out").apply { mkdirs() }
        val cmakeSourceDir = buildDir
        val cmake = findCMakePath()
        val ninja = "${File(cmake).parent}${if (isWindows) "\\ninja.exe" else "/ninja"}"
        val ndk = findAndroidNdk()
        if (providers.exec {
                workingDir(buildDir)
                commandLine(
                    listOf(
                        cmake,
                        "-G",
                        "Ninja", // 强制使用 Ninja
                        "-DCMAKE_MAKE_PROGRAM=${ninja}",
                        "-DCMAKE_TOOLCHAIN_FILE=${ndk}/build/cmake/android.toolchain.cmake",
                        "-DANDROID_ABI=arm64-v8a",
                        "-DANDROID_PLATFORM=android-24",
                        "-DCMAKE_BUILD_TYPE=Release",
                        "-S",
                        cmakeSourceDir.absolutePath,
                        "-B",
                        cmakeBuildDir.absolutePath
                    )
                )
                isIgnoreExitValue = true
            }.result.get().exitValue != 0) {
            throw GradleException("❌ CMake编译失败")
        }
        println("✅ CMake编译成功")
        // 编译
        if (providers.exec {
                workingDir(cmakeBuildDir)
                commandLine(
                    listOf(
                        ninja,
                        "-j${Runtime.getRuntime().availableProcessors()}"
                    )
                )
                isIgnoreExitValue = true
            }.result.get().exitValue != 0) {
            throw GradleException("❌ Make编译失败")
        }
        println("✅ Make编译成功")
        // 复制SO库文件
        val serverBinary = File(binDir, "libserver-k.so")
        if (!serverBinary.exists()) {
            throw GradleException("❌ 未找到生成的SO文件: ${serverBinary.absolutePath}")
        }
        val targetFile = File(libDir, "libserver-k.so")
        serverBinary.copyTo(targetFile, overwrite = true)
        println("✅ 复制SO库: ${serverBinary.absolutePath} -> ${targetFile.absolutePath}    文件大小: ${targetFile.length()} 字节")
        val jars = mutableListOf<String>()
        configurations.getByName("debugRuntimeClasspath").files.forEach { file ->
            if (file.name.endsWith(".jar") && !file.name.contains("kotlin-stdlib") && !file.name.contains(
                    "gson"
                ) && !file.name.contains("annotations")
            ) {
                jars.add(file.absolutePath)
                println("✅ 添加依赖: ${file.absolutePath}")
            }
        }
        // 创建服务器专用的DEX文件
        val classesDir = File(
            projectRoot,
            "server-k/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes"
        )
        if (!classesDir.exists()) {
            throw GradleException("❌ Java类文件目录不存在: ${classesDir.absolutePath}")
        }
        // 先将class文件打包成JAR，因为D8不能直接处理目录
        val tempJarFile = File(buildDir, "server-k-classes.jar")
        if (providers.exec {
                workingDir(classesDir)
                commandLine(listOf(findJar(), "cf", tempJarFile.absolutePath, "."))
                isIgnoreExitValue = true
            }.result.get().exitValue != 0) {
            throw GradleException("❌ 创建临时JAR文件失败")
        }
        println("✅ 创建临时JAR文件: ${tempJarFile.absolutePath}")
        if (providers.exec {
                commandLine(
                    listOf(
                        findD8Tool(),
                        "--min-api",
                        "24",
                        "--output",
                        outputDir.absolutePath,
                        tempJarFile.absolutePath
                    ) + jars
                )
                isIgnoreExitValue = true
            }.result.get().exitValue != 0) {
            throw GradleException("❌ D8工具编译失败")
        }
        val generatedDexFile = File(outputDir, "classes.dex")
        if (!generatedDexFile.exists()) {
            throw GradleException("❌ D8生成的DEX文件不存在: ${generatedDexFile.absolutePath}")
        }
        val dexFile = File(outputDir, "k-server.dex")
        generatedDexFile.renameTo(dexFile)
        if (!dexFile.exists()) {
            throw GradleException("❌ DEX文件重命名失败")
        }
        println("✅ 创建DEX文件: ${dexFile.absolutePath}   文件大小: ${dexFile.length()} 字节")
        // 显示使用说明
        println("=== 🎉 Server 构建完成 ===")
        println("📁 输出目录: ${outputDir.absolutePath}")
        println("📦 输出文件:")
        println("  - k-server.dex: ${dexFile.length()} 字节")
        println("  - libserver-k.so: ${File(libDir, "libserver-k.so").length()} 字节")
        println("🚀 部署到设备:")
        println("adb push ${outputDir.absolutePath}/* /data/local/tmp/")
        println("▶️  启动服务器:")
        println("adb shell 'chmod +x /data/local/tmp/k-server.dex && chmod +x /data/local/tmp/lib/arm64-v8a/libserver-k.so && CLASSPATH=/data/local/tmp/k-server.dex app_process /system/bin com.github.kirer.server.Launcher --mode=SHARED_MEMORY --libPath=/data/local/tmp/lib/arm64-v8a --debug > /data/local/tmp/k-server.log 2>&1 &'")
        println("adb shell 'chmod +x /data/local/tmp/k-server.dex && chmod +x /data/local/tmp/lib/arm64-v8a/libserver-k.so && CLASSPATH=/data/local/tmp/k-server.dex app_process /system/bin com.github.kirer.server.Launcher --mode=UNIX_SOCKET --libPath=/data/local/tmp/lib/arm64-v8a --socketName=k.socket --debug > /data/local/tmp/k-server.log 2>&1 &'")
        println("adb shell 'chmod +x /data/local/tmp/k-server.dex && chmod +x /data/local/tmp/lib/arm64-v8a/libserver-k.so && CLASSPATH=/data/local/tmp/k-server.dex app_process /system/bin com.github.kirer.server.Launcher --mode=TCP_SOCKET --libPath=/data/local/tmp/lib/arm64-v8a --tcpHost=127.0.0.1 --tcpPort=7777 --debug > /data/local/tmp/k-server.log 2>&1 &'")
    }
}

/**
 * 将DEX和SO文件转换为C头文件
 */
tasks.register("generateEmbeddedFiles") {
    group = "build"
    description = "将k-server.dex和libserver-k.so转换为C头文件"
    dependsOn("buildServer")
    doLast {
        val projectRoot = project.rootDir
        val outputDir = File(projectRoot, "server-k/build/k-server-output")
        val dexFile = File(outputDir, "k-server.dex")
        val soFile = File(outputDir, "lib/arm64-v8a/libserver-k.so")
        val headerDir = File(projectRoot, "server-k/src/main/cpp/client/embedded")
        headerDir.mkdirs()
        generateFileHeader(dexFile, File(headerDir, "k_server_dex.h"), "k_server_dex")
        generateFileHeader(soFile, File(headerDir, "libserver_k_so.h"), "libserver_k_so")
        println("=== 🎉 嵌入文件生成完成 ===")
        println("📁 头文件目录: ${headerDir.absolutePath}")
        println("📦 生成的头文件:")
        println("  - k_server_dex.h: ${dexFile.length()} 字节")
        println("  - libserver_k_so.h: ${soFile.length()} 字节")
    }
}

/**
 * 构建Android平台的客户端可执行程序, 产出k-client
 */
tasks.register("buildClient") {
    group = "client"
    description = "构建Android客户端可执行程序"
    dependsOn("generateEmbeddedFiles")
    doLast {
        val projectRoot = project.rootDir
        val sourceDir = File(projectRoot, "server-k/src/main/cpp")
        val buildDir = File(projectRoot, "build/client-android")
        val binDir = File(projectRoot, "build/bin-android")
        val outputDir = File(projectRoot, "server-k/build/k-client-output")
        println("=== 构建Android客户端程序 ===")
        println("源码目录: ${sourceDir.absolutePath}")
        println("构建目录: ${buildDir.absolutePath}")
        println("输出目录: ${binDir.absolutePath}")
        if (!sourceDir.exists()) {
            throw GradleException("❌ 源码目录不存在: ${sourceDir.absolutePath}")
        }
        if (buildDir.exists()) {
            buildDir.deleteRecursively()
        }
        buildDir.mkdirs()
        binDir.mkdirs()
        val clientCMakeFile = File(buildDir, "CMakeLists.txt")
        clientCMakeFile.writeText(generateClientCMakeContent(sourceDir, binDir))
        // 配置CMake
        val cmake = findCMakePath()
        val ninja = "${File(cmake).parent}${if (isWindows) "\\ninja.exe" else "/ninja"}"
        val ndk = findAndroidNdk()
        val cmakeBuildDir = File(buildDir, "cmake-out").apply { mkdirs() }
        val cmakeSourceDir = buildDir
        if (providers.exec {
                workingDir(buildDir)
                commandLine(
                    listOf(
                        cmake,
                        "-G",
                        "Ninja", // 强制使用 Ninja
                        "-DCMAKE_MAKE_PROGRAM=${ninja}",
                        "-DCMAKE_TOOLCHAIN_FILE=${ndk}/build/cmake/android.toolchain.cmake",
                        "-DANDROID_ABI=arm64-v8a",
                        "-DANDROID_PLATFORM=android-24",
                        "-DCMAKE_BUILD_TYPE=Release",
                        "-DDISABLE_JNI=ON",
                        "-S",
                        cmakeSourceDir.absolutePath,
                        "-B",
                        cmakeBuildDir.absolutePath
                    )
                )
                isIgnoreExitValue = true
            }.result.get().exitValue != 0) {
            throw GradleException("❌ CMake编译失败")
        }
        println("✅ CMake编译成功")
        // 编译
        if (providers.exec {
                workingDir(cmakeBuildDir)
                commandLine(
                    listOf(
                        ninja,
                        "-j${Runtime.getRuntime().availableProcessors()}"
                    )
                )
                isIgnoreExitValue = true
            }.result.get().exitValue != 0) {
            throw GradleException("❌ Make编译失败")
        }
        // 检查生成的可执行文件
        val clientBinary = File(binDir, "k-client")
        if (!clientBinary.exists()) {
            throw GradleException("❌ 未找到生成的可执行文件: ${clientBinary.absolutePath}")
        }
        // 复制到输出目录
        outputDir.mkdirs()
        val outputBinary = File(outputDir, "k-client")
        clientBinary.copyTo(outputBinary, overwrite = true)
        println("✅ 复制到输出目录: ${outputBinary.absolutePath}")
        println("=== 🎉 客户端构建完成 ===")
        println("📁 输出目录: ${outputDir.absolutePath}")
        println("📦 输出文件:")
        println("  - k-client: ${outputBinary.length()} 字节")
        println("🚀 部署到设备:")
        println("adb push ${outputBinary.absolutePath} /data/local/tmp/")
        println("▶️  运行客户端:")
        println("adb shell 'cd /data/local/tmp && chmod +x k-client && ./k-client --mode=SHARED_MEMORY --memorySize=16777216 --auto-start-server --debug'")
        println("adb shell 'cd /data/local/tmp && chmod +x k-client && ./k-client --mode=UNIX_SOCKET --socketName=k.socket --auto-start-server --debug'")
        println("adb shell 'cd /data/local/tmp && chmod +x k-client && ./k-client --mode=TCP_SOCKET --tcpHost=127.0.0.1 --tcpPort=7777 --auto-start-server --debug'")
    }
}

