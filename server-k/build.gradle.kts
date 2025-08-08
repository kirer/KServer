@file:Suppress("UnstableApiUsage")

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
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
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

/**
 * 查找Android NDK路径
 */
fun findAndroidNdk(): String {
    // 1. 检查环境变量
    val ndkFromEnv = System.getenv("ANDROID_NDK") ?: System.getenv("ANDROID_NDK_ROOT")
    if (ndkFromEnv != null && File(ndkFromEnv).exists()) {
        return ndkFromEnv
    }
    // 2. 从Android SDK路径查找
    val androidHome = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
    if (androidHome != null) {
        val ndkDir = File(androidHome, "ndk")
        if (ndkDir.exists()) {
            // 查找最新版本的NDK
            val ndkVersions = ndkDir.listFiles()?.filter {
                it.isDirectory && it.name.matches(Regex("\\d+\\..*"))
            }?.sortedByDescending { it.name }

            if (ndkVersions?.isNotEmpty() == true) {
                return ndkVersions.first().absolutePath
            }
        }
    }
    throw GradleException("未找到Android NDK。请设置环境变量 ANDROID_NDK 或 ANDROID_NDK_ROOT")
}

/**
 * 查找dx工具路径
 */
fun findDxTool(androidHome: String): String {
    // 新版本的dx工具路径
    val newDxPath = File(androidHome, "build-tools").listFiles()
        ?.filter { it.isDirectory && it.name.matches(Regex("\\d+\\..*")) }
        ?.maxByOrNull { it.name }
        ?.let { File(it, "dx") }
    if (newDxPath?.exists() == true) {
        return newDxPath.absolutePath
    }
    // 旧版本的dx工具路径
    val oldDxPath = File(androidHome, "platform-tools/dx")
    if (oldDxPath.exists()) {
        return oldDxPath.absolutePath
    }
    // 在build-tools中查找任何版本的dx
    val buildToolsDir = File(androidHome, "build-tools")
    if (buildToolsDir.exists()) {
        buildToolsDir.listFiles()?.forEach { versionDir ->
            if (versionDir.isDirectory) {
                val dxFile = File(versionDir, "dx")
                if (dxFile.exists()) {
                    return dxFile.absolutePath
                }
            }
        }
    }

    throw GradleException("未找到dx工具，请检查Android SDK安装")
}

/**
 * 查找CMake工具路径
 */
fun findCMakePath(): String {
    val osName = System.getProperty("os.name").lowercase()
    val isWindows = osName.contains("windows")

    // 根据操作系统确定可能的CMake路径
    val possiblePaths = when {
        isWindows -> listOfNotNull(
            "cmake.exe",                                    // PATH中的cmake
            "C:\\Program Files\\CMake\\bin\\cmake.exe",     // 默认安装路径
            "C:\\Program Files (x86)\\CMake\\bin\\cmake.exe", // 32位安装路径
            "C:\\Tools\\cmake\\bin\\cmake.exe",             // Chocolatey安装路径
            System.getenv("ProgramFiles")?.let { "$it\\CMake\\bin\\cmake.exe" }, // 动态Program Files路径
            System.getenv("ProgramFiles(x86)")?.let { "$it\\CMake\\bin\\cmake.exe" } // 动态Program Files (x86)路径
        )
        else -> listOf(
            "cmake",                        // PATH中的cmake
            "/opt/homebrew/bin/cmake",      // Homebrew on Apple Silicon
            "/usr/local/bin/cmake",         // Homebrew on Intel Mac / Linux
            "/usr/bin/cmake",               // System installation
            "/snap/bin/cmake"               // Snap package on Linux
        )
    }

    println("正在查找CMake，操作系统: $osName")
    println("候选路径: ${possiblePaths.joinToString(", ")}")

    for (path in possiblePaths) {
        try {
            println("尝试路径: $path")
            val process = ProcessBuilder(path, "--version")
                .redirectErrorStream(true)
                .start()
            val result = process.waitFor()
            if (result == 0) {
                val output = process.inputStream.bufferedReader().readText()
                println("✅ 找到CMake: $path")
                println("版本信息: ${output.lines().firstOrNull() ?: "未知版本"}")
                return path
            } else {
                println("❌ 路径无效: $path (退出码: $result)")
            }
        } catch (e: Exception) {
            println("❌ 路径测试失败: $path (${e.message})")
        }
    }

    val installInstructions = when {
        isWindows -> "请安装CMake: https://cmake.org/download/ 或使用 choco install cmake"
        osName.contains("mac") -> "请安装CMake: brew install cmake"
        else -> "请安装CMake: sudo apt-get install cmake 或 sudo yum install cmake"
    }

    throw GradleException("未找到CMake工具，$installInstructions")
}

/**
 * 生成客户端专用的CMakeLists.txt内容（支持OpenCV）
 */
fun generateClientCMakeContent(sourceDir: File, binDir: File): String {
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
set(OpenCV_DIR "${sourceDir.absolutePath}/client/opencv")
set(OpenCV_INCLUDE_DIRS "${sourceDir.absolutePath}/client/opencv/include")
set(OpenCV_STATIC_LIBS_DIR "${sourceDir.absolutePath}/client/opencv/libs")

# 包含OpenCV头文件
include_directories(${'$'}{OpenCV_INCLUDE_DIRS})

# 包含嵌入文件头文件
include_directories("${sourceDir.absolutePath}/client/embedded")

# 手动设置OpenCV库（按依赖顺序）
set(OpenCV_LIBS
    ${'$'}{OpenCV_STATIC_LIBS_DIR}/libopencv_imgcodecs.a
    ${'$'}{OpenCV_STATIC_LIBS_DIR}/libopencv_imgproc.a
    ${'$'}{OpenCV_STATIC_LIBS_DIR}/libopencv_core.a
)

# 添加第三方依赖库
set(OpenCV_3RDPARTY_LIBS_DIR "${sourceDir.absolutePath}/client/opencv/3rdparty")
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
    ${sourceDir.absolutePath}/client/main.c
    ${sourceDir.absolutePath}/client/client_opencv.cpp
    ${sourceDir.absolutePath}/client/core/client_core.c
    ${sourceDir.absolutePath}/client/transport/client_shared_memory.c
    ${sourceDir.absolutePath}/client/transport/client_unix.c
    ${sourceDir.absolutePath}/client/transport/client_tcp.c
    ${sourceDir.absolutePath}/common/ashmem.c
    ${sourceDir.absolutePath}/common/log.c
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
    RUNTIME_OUTPUT_DIRECTORY "${binDir.absolutePath}"
)
""".trimIndent()
}

/**
 * 生成服务器专用的CMakeLists.txt内容
 */
fun generateServerCMakeContent(sourceDir: File, binDir: File): String {
    return """
cmake_minimum_required(VERSION 3.22.1)

project("server-k-server" C)

# 设置C标准
set(CMAKE_C_STANDARD 99)

# 添加编译选项
add_compile_options(-Wall -Wextra -Wno-unused-parameter -Wno-unused-function -O2)

# 创建服务器共享库（只包含服务器功能，不包含客户端）
add_library(server-k SHARED
    ${sourceDir.absolutePath}/server/core/server_core.c
    ${sourceDir.absolutePath}/server/jni/server_jni_bridge.c
    ${sourceDir.absolutePath}/server/transport/server_shared_memory.c
    ${sourceDir.absolutePath}/server/transport/server_unix.c
    ${sourceDir.absolutePath}/server/transport/server_tcp.c
    ${sourceDir.absolutePath}/common/ashmem.c
    ${sourceDir.absolutePath}/common/log.c
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
    LIBRARY_OUTPUT_DIRECTORY "${binDir.absolutePath}"
)
""".trimIndent()
}

// ================================
// 服务器程序构建任务
// ================================

/**
 * 构建服务器程序，产出server-k-server.dex和libserver-k.so
 */
tasks.register("buildServer") {
    group = "server"
    description = "构建服务器程序，产出k-server.dex和libserver-k.so"
    // 依赖Java编译任务
    dependsOn("compileDebugJavaWithJavac")
    doLast {
        val projectRoot = project.rootDir
        val sourceDir = File(projectRoot, "server-k/src/main/cpp")
        val buildDir = File(projectRoot, "build/server-android")
        val binDir = File(projectRoot, "build/bin-server")
        val outputDir = File(projectRoot, "server-k/build/k-server-output")
        val libDir = File(outputDir, "lib/arm64-v8a")
        println("=== 构建服务器程序 ===")
        println("源码目录: ${sourceDir.absolutePath}")
        println("构建目录: ${buildDir.absolutePath}")
        println("输出目录: ${outputDir.absolutePath}")
        // 检查源码目录
        if (!sourceDir.exists()) {
            throw GradleException("源码目录不存在: ${sourceDir.absolutePath}")
        }
        // 清理并创建构建目录
        if (buildDir.exists()) {
            buildDir.deleteRecursively()
        }
        buildDir.mkdirs()
        binDir.mkdirs()
        outputDir.mkdirs()
        libDir.mkdirs()
        // 检查Android NDK
        val androidNdk = findAndroidNdk()
        println("使用NDK: $androidNdk")
        // 创建服务器专用的CMakeLists.txt
        val serverCMakeFile = File(buildDir, "CMakeLists.txt")
        serverCMakeFile.writeText(generateServerCMakeContent(sourceDir, binDir))
        // 查找CMake路径
        val cmakePath = findCMakePath()
        println("使用CMake: $cmakePath")
        // 配置CMake
        val cmakeConfigCmd = listOf(
            cmakePath,
            buildDir.absolutePath,
            "-DCMAKE_TOOLCHAIN_FILE=$androidNdk/build/cmake/android.toolchain.cmake",
            "-DANDROID_ABI=arm64-v8a",
            "-DANDROID_PLATFORM=android-24",
            "-DCMAKE_BUILD_TYPE=Release"
        )
        println("配置CMake...")
        val configResult = project.exec {
            workingDir = buildDir
            commandLine = cmakeConfigCmd
            isIgnoreExitValue = true
        }
        if (configResult.exitValue != 0) {
            throw GradleException("CMake配置失败")
        }
        // 编译
        println("开始编译...")
        val buildResult = project.exec {
            workingDir = buildDir
            commandLine = listOf("make", "-j${Runtime.getRuntime().availableProcessors()}")
            isIgnoreExitValue = true
        }
        if (buildResult.exitValue != 0) {
            throw GradleException("编译失败")
        }
        // 1. 复制SO库文件
        val serverBinary = File(binDir, "libserver-k.so")
        if (serverBinary.exists()) {
            val targetFile = File(libDir, "libserver-k.so")
            serverBinary.copyTo(targetFile, overwrite = true)
            println("✅ 复制SO库: ${serverBinary.name} -> ${targetFile.absolutePath}")
            println("   文件大小: ${targetFile.length()} 字节")
        } else {
            throw GradleException("未找到生成的SO文件: ${serverBinary.absolutePath}")
        }
        // 2. 创建服务器专用的DEX文件
        val classesDir = File(
            projectRoot,
            "server-k/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes"
        )
        val dexFile = File(outputDir, "k-server.dex")
        if (classesDir.exists()) {
            // 使用dx工具创建DEX文件
            val androidHome = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
            if (androidHome == null) {
                throw GradleException("未找到Android SDK，请设置ANDROID_HOME或ANDROID_SDK_ROOT环境变量")
            }

            // 查找dx工具
            val dxTool = findDxTool(androidHome)
            println("使用DX工具: $dxTool")

            // 收集所有需要的类文件和依赖
            val classpath = mutableListOf<String>()

            // 添加项目类文件
            classpath.add(classesDir.absolutePath)

            // 添加依赖的JAR文件（过滤掉有问题的文件）
            configurations.getByName("debugRuntimeClasspath").files.forEach { file ->
                if (file.name.endsWith(".jar") &&
                    !file.name.contains("kotlin-stdlib") &&
                    !file.name.contains("gson") &&
                    !file.name.contains("annotations")
                ) {
                    classpath.add(file.absolutePath)
                    println("添加依赖: ${file.name}")
                } else {
                    println("跳过JAR: ${file.name}")
                }
            }

            // 执行dx命令
            val dxCmd = listOf(
                dxTool,
                "--dex",
                "--min-sdk-version=24",
                "--output=${dexFile.absolutePath}"
            ) + classpath

            println("创建DEX文件...")
            val dxResult = project.exec {
                commandLine = dxCmd
                isIgnoreExitValue = true
            }

            if (dxResult.exitValue != 0) {
                throw GradleException("创建DEX文件失败")
            }

            if (dexFile.exists()) {
                println("✅ 创建DEX文件: ${dexFile.absolutePath}")
                println("   文件大小: ${dexFile.length()} 字节")
            } else {
                throw GradleException("DEX文件创建失败")
            }
        } else {
            throw GradleException("Java类文件目录不存在: ${classesDir.absolutePath}")
        }

        // 4. 显示使用说明
        println("\n=== 🎉 服务器构建完成 ===")
        println("📁 输出目录: ${outputDir.absolutePath}")
        println("📦 输出文件:")
        println("  - k-server.dex: ${dexFile.length()} 字节")
        println("  - libserver-k.so: ${File(libDir, "libserver-k.so").length()} 字节")
        println()
        println("🚀 部署到设备:")
        println("  adb push ${outputDir.absolutePath}/* /data/local/tmp/")
        println()
        println("▶️  启动服务器:")
        println("  adb shell 'chmod +x /data/local/tmp/k-server.dex && chmod +x /data/local/tmp/lib/arm64-v8a/libserver-k.so && CLASSPATH=/data/local/tmp/k-server.dex app_process /system/bin com.github.kirer.server.Launcher --mode=SHARED_MEMORY --libPath=/data/local/tmp/lib/arm64-v8a --debug > /data/local/tmp/k-server.log 2>&1 &'")
        println("  adb shell 'chmod +x /data/local/tmp/k-server.dex && chmod +x /data/local/tmp/lib/arm64-v8a/libserver-k.so && CLASSPATH=/data/local/tmp/k-server.dex app_process /system/bin com.github.kirer.server.Launcher --mode=UNIX_SOCKET --libPath=/data/local/tmp/lib/arm64-v8a --socketName=k.socket --debug > /data/local/tmp/k-server.log 2>&1 &'")
        println("  adb shell 'chmod +x /data/local/tmp/k-server.dex && chmod +x /data/local/tmp/lib/arm64-v8a/libserver-k.so && CLASSPATH=/data/local/tmp/k-server.dex app_process /system/bin com.github.kirer.server.Launcher --mode=TCP_SOCKET --libPath=/data/local/tmp/lib/arm64-v8a --tcpHost=127.0.0.1 --tcpPort=7777 --debug > /data/local/tmp/k-server.log 2>&1 &'")
        println()
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

        // 生成 k-server.dex 头文件
        generateFileHeader(dexFile, File(headerDir, "k_server_dex.h"), "k_server_dex")

        // 生成 libserver-k.so 头文件
        generateFileHeader(soFile, File(headerDir, "libserver_k_so.h"), "libserver_k_so")

        println("\n=== 🎉 嵌入文件生成完成 ===")
        println("📁 头文件目录: ${headerDir.absolutePath}")
        println("📦 生成的头文件:")
        println("  - k_server_dex.h: ${dexFile.length()} 字节")
        println("  - libserver_k_so.h: ${soFile.length()} 字节")
    }
}

fun generateFileHeader(inputFile: File, outputFile: File, varName: String) {
    if (!inputFile.exists()) {
        throw GradleException("文件不存在: ${inputFile.absolutePath}")
    }

    val bytes = inputFile.readBytes()

    outputFile.writeText("""
#ifndef ${varName.toUpperCase()}_H
#define ${varName.toUpperCase()}_H

unsigned char ${varName}_data[] = {
${bytes.withIndex().chunked(12) { chunk ->
    "  " + chunk.joinToString(", ") { (_, byte) ->
        "0x%02x".format(byte.toInt() and 0xFF)
    }
}.joinToString(",\n")}
};

unsigned int ${varName}_len = ${bytes.size};

#endif // ${varName.toUpperCase()}_H
""".trimIndent())

    println("✅ 生成 ${outputFile.name}: ${bytes.size} 字节")
}

// ================================
// 客户端程序构建任务
// ================================

/**
 * 构建Android平台的客户端可执行程序
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
        // 检查源码目录
        if (!sourceDir.exists()) {
            throw GradleException("源码目录不存在: ${sourceDir.absolutePath}")
        }
        // 清理并创建构建目录
        if (buildDir.exists()) {
            buildDir.deleteRecursively()
        }
        buildDir.mkdirs()
        binDir.mkdirs()
        // 检查Android NDK
        val androidNdk = findAndroidNdk()
        println("使用NDK: $androidNdk")
        // 创建客户端专用的CMakeLists.txt
        val clientCMakeFile = File(buildDir, "CMakeLists.txt")
        clientCMakeFile.writeText(generateClientCMakeContent(sourceDir, binDir))
        // 查找CMake路径
        val cmakePath = try {
            findCMakePath()
        } catch (e: Exception) {
            println("❌ CMake查找失败: ${e.message}")
            throw GradleException("无法找到CMake工具，请确保CMake已正确安装并在PATH中", e)
        }
        println("✅ 使用CMake: $cmakePath")
        // 验证CMake是否可执行
        try {
            val testResult = ProcessBuilder(cmakePath, "--version")
                .start()
                .waitFor()
            if (testResult != 0) {
                throw GradleException("CMake版本检查失败，退出码: $testResult")
            }
        } catch (e: Exception) {
            throw GradleException("CMake无法执行: ${e.message}，路径: $cmakePath", e)
        }
        // 配置CMake
        val cmakeConfigCmd = listOf(
            cmakePath,
            buildDir.absolutePath,
            "-DCMAKE_TOOLCHAIN_FILE=$androidNdk/build/cmake/android.toolchain.cmake",
            "-DANDROID_ABI=arm64-v8a",
            "-DANDROID_PLATFORM=android-24",
            "-DCMAKE_BUILD_TYPE=Release",
            "-DDISABLE_JNI=ON"
        )
        println("配置CMake...")
        val configResult = project.exec {
            workingDir = buildDir
            commandLine = cmakeConfigCmd
            isIgnoreExitValue = true
        }
        if (configResult.exitValue != 0) {
            throw GradleException("CMake配置失败")
        }
        // 编译
        println("开始编译...")
        val buildResult = project.exec {
            workingDir = buildDir
            commandLine = listOf("make", "-j${Runtime.getRuntime().availableProcessors()}")
            isIgnoreExitValue = true
        }
        if (buildResult.exitValue != 0) {
            throw GradleException("编译失败")
        }
        // 检查生成的可执行文件
        val clientBinary = File(binDir, "k-client")
        if (clientBinary.exists()) {
            // 复制到输出目录
            outputDir.mkdirs()
            val outputBinary = File(outputDir, "k-client")
            clientBinary.copyTo(outputBinary, overwrite = true)

            println("\n=== 🎉 客户端构建完成 ===")
            println("📁 输出目录: ${outputDir.absolutePath}")
            println("📦 输出文件:")
            println("  - k-client: ${outputBinary.length()} 字节")
            println()
            println("🚀 部署到设备:")
            println("  adb push ${outputBinary.absolutePath} /data/local/tmp/")
            println()
            println("▶️  运行客户端:")
            println("  adb shell 'cd /data/local/tmp && chmod +x k-client && ./k-client --mode=SHARED_MEMORY --memorySize=16777216 --auto-start-server --debug'")
            println("  adb shell 'cd /data/local/tmp && chmod +x k-client && ./k-client --mode=UNIX_SOCKET --socketName=k.socket --auto-start-server --debug'")
            println("  adb shell 'cd /data/local/tmp && chmod +x k-client && ./k-client --mode=TCP_SOCKET --tcpHost=127.0.0.1 --tcpPort=7777 --auto-start-server --debug'")
        } else {
            throw GradleException("未找到生成的可执行文件: ${clientBinary.absolutePath}")
        }
    }
}


