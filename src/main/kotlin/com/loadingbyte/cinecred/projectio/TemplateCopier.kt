package com.loadingbyte.cinecred.projectio

import com.loadingbyte.cinecred.common.l10n
import java.nio.file.Files
import java.nio.file.Path
import java.util.*


fun copyStylingTemplate(destDir: Path, locale: Locale) {
    copyFillingPlaceholders("/template/styling.toml", destDir.resolve("Styling.toml"), locale)
}


fun copyCreditsTemplate(destDir: Path, locale: Locale) {
    copyFillingPlaceholders("/template/credits.csv", destDir.resolve("Credits.csv"), locale)

    val logoFile = destDir.resolve("Logos").resolve("Cinecred.svg")
    if (!Files.exists(logoFile)) {
        Files.createDirectories(logoFile.parent)
        Dummy.javaClass.getResourceAsStream("/logo.svg").use { stream -> Files.copy(stream, logoFile) }
    }
}


private val PLACEHOLDER_REGEX = Regex("\\{([a-zA-Z0-9.]+)}")

private fun copyFillingPlaceholders(resourceName: String, dest: Path, locale: Locale) {
    val content = Dummy.javaClass.getResourceAsStream(resourceName).use { stream ->
        stream.bufferedReader().readLines()
    }.map { line ->
        line.replace(PLACEHOLDER_REGEX) { match ->
            val key = match.groups[1]!!.value
            if (key == "locale") locale.toLanguageTag() else l10n(key, locale)
        }
    }
    Files.write(dest, content)
}


private object Dummy
