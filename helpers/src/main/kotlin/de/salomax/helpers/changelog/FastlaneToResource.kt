package de.salomax.helpers.changelog

import java.io.File
import java.io.FileWriter
import java.nio.charset.Charset

/**
 * Generate android resource xml files from fastlane changelog files.
 */
fun main() {
    FastlaneToResource().run()
}

private class FastlaneToResource {

    companion object {
        private const val SEMVER_MAJOR_MULTIPLIER = 10_000
        private const val SEMVER_MINOR_MULTIPLIER = 100
    }

    fun run() {
        // read all fastlane changelogs
        File("fastlane/metadata/android")
            .listFiles { f -> f.isDirectory } // language directory
            ?.forEach { ff -> ff.getChangelogs() }
    }

    private fun File.getChangelogs() {
        this.listFiles { f -> f.name == "changelogs" }
            ?.forEach { changelog ->
                val language = changelog.path.substringBeforeLast('/').substringAfterLast('/')
                changelog.listFiles().createChangelogsForLanguage(language)
            }
    }

    private fun Array<File>?.createChangelogsForLanguage(language: String) {
        val languageDir =
            if (language == "en-US")
                "values"
            else if (language.matches("[a-z]{2}-[A-Z]{2}".toRegex()))
                "values-${language.substringBefore('-')}-r${language.substringAfter('-')}"
            else
                "values-$language"
        val targetFile = File("app/src/main/res/$languageDir/changelog.xml")
        targetFile.parentFile.mkdirs()
        val fileWriter = FileWriter(targetFile, Charset.forName("UTF-8"), false)

        fileWriter.apply {
            write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
            write("<resources>\n")
            flush()
        }

        this?.sortedByDescending { it.name.substringBefore(".txt").toInt() }?.forEachIndexed { index, file ->
            val version = file.name.substringBefore(".txt").toInt()
            val major = version / SEMVER_MAJOR_MULTIPLIER
            val minor = (version - (major * SEMVER_MAJOR_MULTIPLIER)) / SEMVER_MINOR_MULTIPLIER
            val patch = version - (major * SEMVER_MAJOR_MULTIPLIER) - (minor * SEMVER_MINOR_MULTIPLIER)

            // write
            fileWriter.apply {
                write("    <string-array name=\"changelog_$major.$minor.$patch\">\n")
                write(file.readLines().createVersionChangelog())
                write("    </string-array>\n")
                if (index != this@createChangelogsForLanguage.size - 1)
                    write("\n")
                flush()
            }
        }

        fileWriter.apply {
            write("</resources>\n")
            flush()
            close()
        }

    }

    private fun List<String>.createVersionChangelog(): String {
        val sb = StringBuilder()
        for (entry in this) {
            val escaped = entry.removePrefix("- ")
                .replace("'", "\\'")
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
            sb.appendLine("        <item>$escaped</item>")
        }
        return sb.toString()
    }
}
