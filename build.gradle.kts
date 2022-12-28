import com.loadingbyte.cinecred.DrawImages
import com.loadingbyte.cinecred.MergeServices
import com.loadingbyte.cinecred.Platform
import com.loadingbyte.cinecred.WriteFile
import org.apache.tools.ant.filters.ReplaceTokens
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.*


plugins {
    kotlin("jvm") version "1.7.20"
}

group = "com.loadingbyte"
version = "1.3.0-SNAPSHOT"

val jdkVersion = 17
val slf4jVersion = "1.7.32"
val poiVersion = "5.2.2"
val batikVersion = "1.14"
val javacppVersion = "1.5.6"
val ffmpegVersion = "4.4-$javacppVersion"

val javaProperties = Properties().apply { file("java.properties").reader().use { load(it) } }
val mainClass = javaProperties.getProperty("mainClass")!!
val addModules = javaProperties.getProperty("addModules").split(' ')
val addOpens = javaProperties.getProperty("addOpens").split(' ')
val splashScreen = javaProperties.getProperty("splashScreen")!!
val javaOptions = javaProperties.getProperty("javaOptions")!!


java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(jdkVersion))
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx", "kotlinx-collections-immutable", "0.3.4")

    // Log to java.util.logging
    implementation("org.slf4j", "slf4j-jdk14", slf4jVersion)
    // Redirect other logging frameworks to slf4j.
    // Batik & PDFBox use Jakarta Commons Logging. POI uses log4j2.
    implementation("org.slf4j", "jcl-over-slf4j", slf4jVersion)
    implementation("org.apache.logging.log4j", "log4j-to-slf4j", "2.17.2")

    // Spreadsheet Reading and Writing
    implementation("org.apache.poi", "poi", poiVersion)
    implementation("org.apache.poi", "poi-ooxml", poiVersion)
    implementation("com.github.miachm.sods", "SODS", "1.4.0")
    implementation("org.apache.commons", "commons-csv", "1.9.0")

    // SVG Reading and Writing
    implementation("org.apache.xmlgraphics", "batik-bridge", batikVersion)
    implementation("org.apache.xmlgraphics", "batik-svggen", batikVersion)
    // For pictures embedded in the SVG:
    implementation("org.apache.xmlgraphics", "batik-codec", batikVersion)

    // PDF Reading and Writing
    implementation("org.apache.pdfbox", "pdfbox", "2.0.24")
    implementation("de.rototor.pdfbox", "graphics2d", "0.33")

    // Video Encoding
    implementation("org.bytedeco", "javacpp", javacppVersion)
    implementation("org.bytedeco", "ffmpeg", ffmpegVersion)
    for (platform in Platform.values()) {
        implementation("org.bytedeco", "javacpp", javacppVersion, classifier = platform.slug)
        implementation("org.bytedeco", "ffmpeg", ffmpegVersion, classifier = "${platform.slug}-gpl")
    }

    // UI
    implementation("com.miglayout", "miglayout-swing", "11.0")
    implementation("com.formdev", "flatlaf", "2.6")
    implementation("org.commonmark", "commonmark", "0.19.0")

    // Testing
    testImplementation("org.junit.jupiter", "junit-jupiter", "5.8.1")
}

configurations.all {
    // POI:
    // We don't re-evaluate formulas, and as only that code calls Commons Math, we can omit the dependency.
    exclude("org.apache.commons", "commons-math3")
    // This is only required for adding pictures to workbooks via code, which we don't do.
    exclude("commons-codec", "commons-codec")

    // Batik & PDFBox: We replace this commons-logging dependency by the slf4j bridge.
    exclude("commons-logging", "commons-logging")

    // Batik:
    // The Java XML APIs are part of the JDK itself since Java 5.
    exclude("xml-apis", "xml-apis")
    // Batik functions well without the big xalan dependency.
    exclude("xalan", "xalan")
}


tasks.withType<JavaCompile>().configureEach {
    options.release.set(jdkVersion)
    options.compilerArgs = listOf("--add-modules") + addModules
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = jdkVersion.toString()
}

tasks.test {
    useJUnitPlatform()
}


val writeVersionFile by tasks.registering(WriteFile::class) {
    text.set(version.toString())
    outputFile.set(layout.buildDirectory.file("generated/version/version"))
}

tasks.processResources {
    from(writeVersionFile)
    from("CHANGELOG.md")
    into("licenses") {
        from("LICENSE")
        rename("LICENSE", "Cinecred-LICENSE")
    }
    // Collect all licenses (and related files) from the dependencies.
    // Rename these files such that each one carries the name of the JAR it originated from.
    for (dep in configurations.runtimeClasspath.get().resolvedConfiguration.resolvedArtifacts)
        from(zipTree(dep.file)) {
            include(listOf("COPYRIGHT", "LICENSE", "NOTICE", "README").map { "**/*$it*" })
            eachFile { path = "licenses/libraries/${dep.name}-${file.nameWithoutExtension}" }
            includeEmptyDirs = false
        }
}


val preparePlatformPackagingTasks = Platform.values().map { platform ->
    val excludePlatforms = Platform.values().toList() - platform

    // Build a JAR that excludes the other platforms' natives.
    val platformJar = tasks.register<Jar>("${platform.lowercaseLabel}Jar") {
        archiveClassifier.set(platform.slug)
        from(sourceSets.main.map { it.output })
        exclude(excludePlatforms.map { "natives/${it.slug}" })
    }

    // Draw the images that are needed for the platform.
    val drawPlatformImages = tasks.register<DrawImages>("draw${platform.uppercaseLabel}Images") {
        version.set(project.version.toString())
        forPlatform.set(platform)
        logoFile.set(tasks.processResources.map { it.destDirProvider.file("logo.svg") })
        semiFontFile.set(tasks.processResources.map { it.destDirProvider.file("fonts/Titillium-SemiboldUpright.otf") })
        boldFontFile.set(tasks.processResources.map { it.destDirProvider.file("fonts/Titillium-BoldUpright.otf") })
        outputDir.set(layout.buildDirectory.dir("generated/packaging/${platform.slug}"))
    }

    // Collect all files needed for packaging in a folder.
    val preparePlatformPackaging = tasks.register<Copy>("prepare${platform.uppercaseLabel}Packaging") {
        doFirst {
            if (!Regex("\\d+\\.\\d+\\.\\d+").matches(version.toString()))
                throw GradleException("Non-release versions cannot be packaged.")
        }
        group = "Packaging Preparation"
        description = "Prepares files for building a ${platform.uppercaseLabel} package on that platform."
        into(layout.buildDirectory.dir("packaging/${platform.slug}"))
        // Copy the packaging scripts and fill in some variables. Note that we
        // don't select the scripts by platform here because that's not worth the effort.
        from("packaging") {
            val mainJarName = platformJar.get().archiveFileName.get()
            val tokens = mapOf(
                "VERSION" to version,
                "MAIN_JAR" to mainJarName,
                "MAIN_CLASS" to mainClass,
                "JAVA_OPTIONS" to "--add-modules ${addModules.joinToString(",")} " +
                        addOpens.joinToString(" ") { "--add-opens $it=ALL-UNNAMED" } +
                        " -splash:\$APPDIR/$splashScreen $javaOptions",
                "DESCRIPTION" to "Create beautiful film credits without the pain",
                "DESCRIPTION_DE" to "Wunderschöne Filmabspänne schmerzfrei erstellen",
                "URL" to "https://loadingbyte.com/cinecred/",
                "VENDOR" to "Felix Mujkanovic",
                "EMAIL" to "hello@loadingbyte.com",
                "LEGAL_PATH_RUNTIME" to when (platform) {
                    Platform.WINDOWS -> "runtime\\legal"
                    Platform.MAC_OS -> "runtime/Contents/Home/legal"
                    Platform.LINUX -> "lib/runtime/legal"
                },
                "LEGAL_PATH_APP" to when (platform) {
                    Platform.WINDOWS -> "app\\$mainJarName"
                    Platform.MAC_OS -> "app/$mainJarName"
                    Platform.LINUX -> "lib/app/$mainJarName"
                }
            )
            filter<ReplaceTokens>("tokens" to tokens)
        }
        into("app") {
            from(platformJar)
            from(tasks.processResources.map { it.destDirProvider.file(splashScreen) })
            from(configurations.runtimeClasspath) {
                // Exclude all JARs with natives for excluded platforms.
                exclude(excludePlatforms.map { "*${it.slug}*" })
            }
        }
        into("images") {
            from(drawPlatformImages)
        }
    }

    return@map preparePlatformPackaging
}

val preparePackaging by tasks.registering {
    group = "Packaging Preparation"
    description = "For each platform, prepares files for building a package for that platform on that platform."
    dependsOn(preparePlatformPackagingTasks)
}


val mergeServices by tasks.registering(MergeServices::class) {
    configuration.set(configurations.runtimeClasspath)
    outputDir.set(layout.buildDirectory.dir("generated/universal/services"))
}

val universalJar by tasks.registering(Jar::class) {
    group = "Build"
    description = "Assembles a jar archive containing the main classes and all dependencies for all platforms."
    archiveClassifier.set("universal")
    manifest.attributes(
        "Main-Class" to mainClass,
        "SplashScreen-Image" to splashScreen,
        "Add-Opens" to addOpens.joinToString(" ")
    )
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.map { it.output })
    for (dep in configurations.runtimeClasspath.get().resolvedConfiguration.resolvedArtifacts)
        from(zipTree(dep.file)) {
            exclude("META-INF/**", "**/module-info.class")
        }
    into("META-INF/services") {
        from(mergeServices)
    }
}


// We need to retrofit this property in a hacky and not entirely compliant way because it's sadly not migrated yet.
val Copy.destDirProvider: Directory
    get() = layout.projectDirectory.dir(destinationDir.path)
