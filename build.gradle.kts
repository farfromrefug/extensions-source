plugins {
    id("co.uzzu.dotenv.gradle") version "4.0.0"
}

allprojects {
    repositories {
        mavenCentral()
        google()
        maven(url = "https://jitpack.io")
    }
}
