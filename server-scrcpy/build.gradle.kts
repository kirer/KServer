plugins {
    id("java-library")
}
java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

val androidJar = "${System.getenv("ANDROID_HOME")}/platforms/android-35/android.jar"
dependencies {
    compileOnly(files(androidJar))
}