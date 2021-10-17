import org.apache.batik.anim.dom.SAXSVGDocumentFactory
import org.apache.batik.bridge.BridgeContext
import org.apache.batik.bridge.GVTBuilder
import org.apache.batik.bridge.UserAgentAdapter
import org.apache.batik.ext.awt.image.GraphicsUtil
import org.apache.batik.util.XMLResourceDescriptor
import org.apache.tools.ant.filters.ReplaceTokens
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.stream.FileImageOutputStream


buildscript {
    val batikVersion = "1.14"
    val twelveMonkeysVersion = "3.7.0"
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.apache.xmlgraphics", "batik-bridge", batikVersion)
        classpath("org.apache.xmlgraphics", "batik-codec", batikVersion)
        // For writing Windows ICO icon files:
        classpath("com.twelvemonkeys.imageio", "imageio-bmp", twelveMonkeysVersion)
        // For writing Mac OS ICNS icon files:
        classpath("com.twelvemonkeys.imageio", "imageio-icns", twelveMonkeysVersion)
    }
}

plugins {
    kotlin("jvm") version "1.5.30"
}

group = "com.loadingbyte"
version = "1.2.0"

val slf4jVersion = "1.7.32"
val batikVersion = "1.14"
val javacppVersion = "1.5.6"
val ffmpegVersion = "4.4-$javacppVersion"

enum class Platform(val label: String, val slug: String) {
    WINDOWS("Windows", "windows-x86_64"),
    MAC_OS("MacOS", "macosx-x86_64"),
    LINUX("Linux", "linux-x86_64")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx", "kotlinx-collections-immutable", "0.3.4")

    // Log to java.util.logging
    implementation("org.slf4j", "slf4j-jdk14", slf4jVersion)
    // Redirect other logging frameworks to slf4j.
    // Batik & PDFBox use Jakarta Commons Logging. JExcelApi uses log4j.
    implementation("org.slf4j", "jcl-over-slf4j", slf4jVersion)
    implementation("org.slf4j", "log4j-over-slf4j", slf4jVersion)

    // Spreadsheet Reading and Writing
    implementation("ch.rabanti", "nanoxlsx4j", "1.2.8")
    implementation("net.sourceforge.jexcelapi", "jxl", "2.6.12")
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
    implementation("com.formdev", "flatlaf", "1.6.1")

    // Testing
    testImplementation("org.junit.jupiter", "junit-jupiter", "5.8.1")
}

configurations.all {
    // Batik & PDFBox: We replace this commons-logging dependency by the slf4j bridge.
    exclude("commons-logging", "commons-logging")
    // JExcelApi: We replace this log4j dependency by the slf4j bridge.
    exclude("log4j", "log4j")

    // Batik:
    // The Java XML APIs are part of the JDK itself since Java 5.
    exclude("xml-apis", "xml-apis")
    // Batik functions well without the big xalan dependency.
    exclude("xalan", "xalan")
}

tasks.withType<JavaCompile> {
    options.release.set(17)
    options.compilerArgs = listOf("--add-modules", "jdk.incubator.foreign")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "16"
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Generate a resource file storing the project version.
val generatedResourcesDir = buildDir.resolve("generated").resolve("resources")
val generateResources by tasks.registering {
    group = "Build"
    doLast {
        generatedResourcesDir.mkdirs()
        generatedResourcesDir.resolve("version").writeText(version.toString())
    }
}
sourceSets.main.get().output.dir(mapOf("builtBy" to generateResources), generatedResourcesDir)

val jar: Jar by tasks
jar.apply {
    from("LICENSE")
}


// Cinecred implements reflective code which accesses these packages.
val addOpens = listOf("java.base/java.lang", "java.desktop/java.awt.font", "java.desktop/sun.font")

// Build the universal JAR containing natives for all supported platforms.
val buildUniversalJar by tasks.registering(Jar::class) {
    group = "Build"
    archiveClassifier.set("universal")
    makeFatJar(Platform.WINDOWS, Platform.MAC_OS, Platform.LINUX)
}


// For each platform, build a JAR containing only natives for that platform.
val buildPlatformJarTasks = Platform.values().asSequence().associateWith { platform ->
    task<Jar>("build${platform.label}Jar") {
        group = "Build"
        archiveClassifier.set(platform.slug)
        makeFatJar(platform)
    }
}


val preparePackagingTasks = Platform.values().map { platform ->
    val pkgDir = buildDir.resolve("packaging").resolve(platform.slug)
    val platformJar = buildPlatformJarTasks[platform]!!
    // Copy all existing files needed for packaging into the packaging folder.
    val copyFiles = task<Copy>("copyFiles${platform.label}") {
        group = "Packaging Preparation"
        dependsOn("processResources")
        dependsOn(platformJar)
        into(pkgDir)
        // Copy the packaging scripts "filter" (fill in) some variables. Note that we
        // don't select the scripts by platform here because that's not worth the effort.
        from("packaging") {
            val tokens = mapOf(
                "VERSION" to version,
                "MAIN_JAR" to platformJar.archiveFileName.get(),
                "JAVA_OPTIONS" to "--add-modules jdk.incubator.foreign --enable-native-access=ALL-UNNAMED " +
                        addOpens.joinToString(" ") { "--add-opens $it=ALL-UNNAMED" },
                "DESCRIPTION" to "Create film credits---without pain",
                "DESCRIPTION_DE" to "Filmabspänne schmerzfrei erstellen"
            )
            filter<ReplaceTokens>("tokens" to tokens)
        }
        into("input") {
            from(platformJar)
            from(sourceSets.main.get().output.resourcesDir!!.resolve("splash.png"))
        }
        into("misc") {
            from("LICENSE")
        }
    }
    // Transcode the logo SVG to the platform-specific icon image format and put it into the packaging folder.
    // For Mac OS, additionally create an installer background image from the logo.
    val transcodeLogo = task("transcodeLogo${platform.label}") {
        group = "Packaging Preparation"
        dependsOn("processResources")
        doLast {
            when (platform) {
                Platform.WINDOWS ->
                    transcodeLogo(pkgDir.resolve("misc/icon.ico"), intArrayOf(16, 20, 24, 32, 40, 48, 64, 256))
                Platform.MAC_OS -> {
                    // Note: Currently, icons smaller than 128 written into an ICNS file by TwelveMonkeys cannot be
                    // properly parsed by Mac OS. We have to leave out those sizes to avoid glitches.
                    transcodeLogo(pkgDir.resolve("misc/icon.icns"), intArrayOf(128, 256, 512, 1024), margin = 0.055)
                    val bgFile1 = pkgDir.resolve("resources/Cinecred-background.png")
                    val bgFile2 = pkgDir.resolve("resources/Cinecred-background-darkAqua.png")
                    transcodeLogo(bgFile1, intArrayOf(180), margin = 0.13)
                    bgFile1.copyTo(bgFile2, overwrite = true)
                }
                Platform.LINUX ->
                    transcodeLogo(pkgDir.resolve("misc/icon.png"), intArrayOf(48))
            }
        }
    }
    // Put it all together.
    task("prepare${platform.label}Packaging") {
        group = "Packaging Preparation"
        dependsOn(copyFiles)
        dependsOn(transcodeLogo)
    }
}

// And add one final job that calls all packaging jobs.
task("preparePackaging") {
    group = "Packaging Preparation"
    dependsOn(preparePackagingTasks)
}


fun Jar.makeFatJar(vararg includePlatforms: Platform) {
    // Depend on the build task so that tests must run beforehand.
    dependsOn("build")

    with(jar)

    manifest.attributes(
        "Main-Class" to "com.loadingbyte.cinecred.MainKt",
        "SplashScreen-Image" to "splash.png",
        "Add-Opens" to addOpens.joinToString(" ")
    )

    val excludePlatforms = Platform.values().filter { it !in includePlatforms }

    for (platform in excludePlatforms)
        exclude("natives/${platform.slug}")

    for (dep in configurations.runtimeClasspath.get()) {
        // Exclude all natives that haven't been explicitly included.
        if (excludePlatforms.any { it.slug in dep.name })
            continue
        // Put the files from the dependency into the fat JAR.
        from(if (dep.isDirectory) dep else zipTree(dep)) {
            // Put all licenses and related files into a central "licenses/" directory and rename
            // them such that each file also carries the name of the JAR it originates from.
            eachFile {
                val filename = file.name
                when {
                    "DEPENDENCIES" in filename -> path = "licenses/libs/${dep.name}-${filename}"
                    "COPYRIGHT" in filename -> path = "licenses/libs/${dep.name}-${filename}"
                    "LICENSE" in filename -> path = "licenses/libs/${dep.name}-${filename}"
                    "NOTICE" in filename -> path = "licenses/libs/${dep.name}-${filename}"
                    "README" in filename -> path = "licenses/libs/${dep.name}-${filename}"
                }
            }
            // Note: The "license/" directory is excluded only if the eachFile instruction has completely emptied it
            // (which we hope it does, but let's better be sure).
            exclude("**/module-info.class", "checkstyle/**", "findbugs/**", "license", "META-INF/maven/**", "pmd/**")
        }
    }
}


fun transcodeLogo(to: File, sizes: IntArray, margin: Double = 0.0) {
    val (logo, logoWidth) = logoAndWidth
    val fileExt = to.name.substringAfterLast('.')
    val imageType = if (fileExt == "ico") BufferedImage.TYPE_4BYTE_ABGR else BufferedImage.TYPE_INT_ARGB

    val images = sizes.map { size ->
        val img = BufferedImage(size, size, imageType)
        val g2 = GraphicsUtil.createGraphics(img)
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val transl = margin * size
        val scaling = (size * (1 - 2 * margin)) / logoWidth
        g2.translate(transl, transl)
        g2.scale(scaling, scaling)
        logo.paint(g2)
        g2.dispose()
        img
    }

    to.delete()
    to.parentFile.mkdirs()
    FileImageOutputStream(to).use { stream ->
        val writer = ImageIO.getImageWritersBySuffix(fileExt).next()
        writer.output = stream
        if (images.size == 1)
            writer.write(images[0])
        else {
            writer.prepareWriteSequence(null)
            for (image in images)
                writer.writeToSequence(IIOImage(image, null, null), null)
            writer.endWriteSequence()
        }
    }
}

val logoAndWidth by lazy {
    val logoFile = sourceSets.main.get().output.resourcesDir!!.resolve("logo.svg")
    val doc = SAXSVGDocumentFactory(XMLResourceDescriptor.getXMLParserClassName())
        .createDocument(null, logoFile.bufferedReader())
    val ctx = BridgeContext(UserAgentAdapter())
    val logo = GVTBuilder().build(ctx, doc)
    val logoWidth = ctx.documentSize.width
    Pair(logo, logoWidth)
}
