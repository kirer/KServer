@file:Suppress("UnstableApiUsage")

import org.gradle.internal.os.OperatingSystem

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

    kotlinOptions {
        jvmTarget = "1.8"
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

// Android Library不需要这些配置

dependencies {
    // 引用scrcpy的包装类来处理隐藏API
    implementation(project(":server-scrcpy"))
}

// Android Library会自动通过CMake构建native库并打包到AAR中

// 自定义任务：生成独立的dex和so文件用于部署
tasks.register("publish") {
    group = "kserver"
    description = "Build server-k dex and so files for deployment"

    // 设置任务依赖关系
    dependsOn(
        "compileDebugJavaWithJavac",    // 编译Java代码
        "buildCMakeDebug",              // 编译Native库
        ":server-scrcpy:compileJava"    // 编译依赖模块
    )

    // 原来的完整构建（包含Lint检查）
    // dependsOn("assembleDebug")

    doLast {
        val buildDir = layout.buildDirectory.get().asFile
        val outputDir = File(buildDir, "server-k-output")
        outputDir.mkdirs()

        // 复制so文件 - 确保复制正确架构的文件
        val cxxDebugDir = File(buildDir, "intermediates/cxx/Debug")
        val soTargetDir = File(outputDir, "lib")
        soTargetDir.mkdirs()

        var soFilesCopied = 0
        val supportedAbis = listOf("arm64-v8a", "armeabi-v7a")

        if (cxxDebugDir.exists()) {
            // 查找所有构建变体目录
            cxxDebugDir.listFiles()?.forEach { variantDir ->
                val objDir = File(variantDir, "obj")
                if (objDir.exists()) {
                    // 遍历支持的ABI
                    supportedAbis.forEach { abi ->
                        val abiDir = File(objDir, abi)
                        if (abiDir.exists()) {
                            abiDir.listFiles()?.forEach { file ->
                                if (file.name.endsWith(".so") && file.name.startsWith("libashmem")) {
                                    val targetAbiDir = File(soTargetDir, abi)
                                    targetAbiDir.mkdirs()
                                    val targetFile = File(targetAbiDir, file.name)
                                    file.copyTo(targetFile, overwrite = true)

                                    // 验证文件大小和架构信息
                                    val fileSize = targetFile.length()
                                    val is64bit = abi == "arm64-v8a"
                                    println("✅ Copied SO: ${file.name} ($abi, ${fileSize} bytes) -> ${targetFile.absolutePath}")

                                    // 简单验证：arm64文件通常比arm32文件大
                                    if (is64bit && fileSize < 40000) {
                                        println("⚠️  Warning: arm64-v8a file seems too small (${fileSize} bytes), might be incorrect")
                                    } else if (!is64bit && fileSize > 50000) {
                                        println("⚠️  Warning: armeabi-v7a file seems too large (${fileSize} bytes), might be incorrect")
                                    }

                                    soFilesCopied++
                                }
                            }
                        }
                    }
                }
            }
        }

        if (soFilesCopied == 0) {
            println("⚠️  Warning: No SO files found in ${cxxDebugDir.absolutePath}")
            // 列出实际存在的目录结构以便调试
            if (cxxDebugDir.exists()) {
                println("Available directories:")
                cxxDebugDir.walkTopDown().maxDepth(3).forEach { dir ->
                    if (dir.isDirectory) {
                        println("  ${dir.relativeTo(cxxDebugDir)}")
                    }
                }
            }
        } else {
            println("✅ Successfully copied $soFilesCopied SO files")
        }

        // 直接从编译的类文件创建JAR（包含依赖）
        val classesDir = File(buildDir, "intermediates/javac/debug/compileDebugJavaWithJavac/classes")
        val scrcpyClassesDir = File(rootProject.projectDir, "server-scrcpy/build/classes/java/main")

        if (classesDir.exists()) {
            val jarFile = File(outputDir, "server-k-classes.jar")

            // 使用copy任务创建临时目录合并所有类
            val tempClassesDir = File(buildDir, "temp-all-classes")
            tempClassesDir.mkdirs()

            // 复制server-k的类
            copy {
                from(classesDir)
                into(tempClassesDir)
            }

            // 复制server-scrcpy的类
            if (scrcpyClassesDir.exists()) {
                copy {
                    from(scrcpyClassesDir)
                    into(tempClassesDir)
                }
                println("Included server-scrcpy classes from: ${scrcpyClassesDir.absolutePath}")
            } else {
                println("Warning: server-scrcpy classes not found at: ${scrcpyClassesDir.absolutePath}")
            }

            // 创建JAR文件
            ant.withGroovyBuilder {
                "jar"("destfile" to jarFile.absolutePath) {
                    "fileset"("dir" to tempClassesDir.absolutePath) {
                        "include"("name" to "**/*.class")
                    }
                }
            }

            println("Created JAR with dependencies: ${jarFile.absolutePath}")
            println("JAR size: ${jarFile.length()} bytes")

            // 列出JAR中的主要类
            println("JAR contents preview:")
            tempClassesDir.walkTopDown().forEach { file ->
                if (file.name.endsWith(".class") && file.name.contains("Launcher")) {
                    val relativePath = file.relativeTo(tempClassesDir).path.replace("\\", "/").replace(".class", "")
                    println("  Found class: $relativePath")
                }
            }

            // 清理临时目录
            tempClassesDir.deleteRecursively()

            // 强制使用d8工具转换为dex（优先产出DEX而不是JAR）
            val androidHome = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
            var dexCreated = false

            if (androidHome != null) {
                // 查找包含d8工具的build-tools版本
                val buildToolsDir = File(androidHome, "build-tools").listFiles()
                    ?.sortedByDescending { it.name }
                    ?.find { File(it, if (OperatingSystem.current().isWindows) "d8.bat" else "d8").exists() }
                val d8Tool = buildToolsDir?.let { File(it, if (OperatingSystem.current().isWindows) "d8.bat" else "d8") }

                println("Looking for d8 tool at: ${d8Tool?.absolutePath}")

                if (d8Tool?.exists() == true) {
                    try {
                        val dexFile = File(outputDir, "classes.dex")
                        println("Converting JAR to DEX using d8...")
                        val execResult = project.exec {
                            commandLine(d8Tool.absolutePath, "--output", outputDir.absolutePath, jarFile.absolutePath)
                            isIgnoreExitValue = true
                        }
                        if (execResult.exitValue == 0 && dexFile.exists()) {
                            println("✅ Successfully generated DEX: ${dexFile.absolutePath}")
                            println("DEX size: ${dexFile.length()} bytes")
                            dexCreated = true

                            // DEX创建成功，删除JAR文件（优先使用DEX）
                            if (jarFile.exists()) {
                                jarFile.delete()
                                println("Removed JAR file (DEX is preferred)")
                            }
                        } else {
                            println("❌ DEX file was not created (exit code: ${execResult.exitValue})")
                        }
                    } catch (e: Exception) {
                        println("❌ d8 conversion failed: ${e.message}")
                        e.printStackTrace()
                    }
                } else {
                    println("d8 tool not found, trying dx as fallback...")
                    // 尝试查找dx工具作为备选
                    val dxTool = buildToolsDir?.let { File(it, if (OperatingSystem.current().isWindows) "dx.bat" else "dx") }
                    if (dxTool?.exists() == true) {
                        try {
                            val dexFile = File(outputDir, "classes.dex")
                            println("Converting JAR to DEX using dx...")
                            val dxResult = project.exec {
                                commandLine(dxTool.absolutePath, "--dex", "--output=${dexFile.absolutePath}", jarFile.absolutePath)
                                isIgnoreExitValue = true
                            }
                            if (dxResult.exitValue == 0 && dexFile.exists()) {
                                println("✅ Successfully generated DEX using dx: ${dexFile.absolutePath}")
                                println("DEX size: ${dexFile.length()} bytes")
                                dexCreated = true

                                // DEX创建成功，删除JAR文件
                                if (jarFile.exists()) {
                                    jarFile.delete()
                                    println("Removed JAR file (DEX is preferred)")
                                }
                            } else {
                                println("❌ dx conversion failed (exit code: ${dxResult.exitValue})")
                            }
                        } catch (e: Exception) {
                            println("❌ dx conversion also failed: ${e.message}")
                        }
                    } else {
                        println("❌ Neither d8 nor dx tool found")
                    }
                }
            } else {
                println("❌ ANDROID_HOME not set, cannot convert to DEX")
            }

            if (!dexCreated) {
                println("⚠️  DEX conversion failed, keeping JAR file as fallback")
                println("JAR file: ${jarFile.absolutePath}")
            }
        } else {
            println("Compiled classes directory not found: ${classesDir.absolutePath}")
        }

        println("Build completed. Output directory: ${outputDir.absolutePath}")
    }
}
