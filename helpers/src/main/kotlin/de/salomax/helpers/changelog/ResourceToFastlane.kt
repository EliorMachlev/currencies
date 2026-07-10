package de.salomax.helpers.changelog

import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.File
import java.io.FileWriter
import java.nio.charset.Charset
import java.util.stream.IntStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Generate fastlane changelog files from android resource xml files.
 */
fun main() {
    ResourceToFastlane().run()
}

private class ResourceToFastlane {

    companion object {
        private const val SEMVER_MAJOR_MULTIPLIER = 10_000
        private const val SEMVER_MINOR_MULTIPLIER = 100
    }

    fun run() {
        File("app/src/de.salomax.helpers.currencies.main/res")
            // language directory
            .listFiles { f -> f.isDirectory && (f.name.startsWith("values-") || f.name == "values") }
            ?.sortedBy { it.name }
            ?.forEach { ff -> ff.getChangelog() }
    }

    private fun File.getChangelog() {
        this.listFiles { f -> f.name == "changelog.xml" }
            ?.firstOrNull()
            ?.parseXmlAndWriteToFiles()
    }

    private fun File.parseXmlAndWriteToFiles() {
        val language = this.parent.substringAfter("values-")
            .replace(".*values".toRegex(), "en-US")
        val targetDir = "fastlane/metadata/android/$language/changelogs"

        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(this)
        val changelogs = document.getElementsByTagName("string-array").toList()
        for (changelog in changelogs) {
            val sb = StringBuilder()
            val version = changelog
                .attributes
                .item(0)
                .textContent
                .substringAfter("changelog_")
                .semVerToVer()
            val versionEntries = changelog.childNodes.toList()
                .map { it.textContent.trim() }
                .filterNot { it.isEmpty() }
            for (versionEntry in versionEntries) {
                sb.appendLine("- ${versionEntry.replace("\\'", "'")}")
            }
            // write
            val targetFile = File("$targetDir/$version.txt")
            targetFile.parentFile.mkdirs()
            FileWriter(targetFile, Charset.forName("UTF-8"), false).apply {
                write(sb.toString())
                flush()
                close()
            }
        }
    }

    private fun NodeList.toList(): List<Node> {
        return this.let { IntStream.range(0, it.length).mapToObj(it::item).toList() }
    }

    private fun String.semVerToVer(): Int {
        val parts = this.split(".").mapNotNull { it.toIntOrNull() }
        return if (parts.size >= 3) parts[0] * SEMVER_MAJOR_MULTIPLIER + parts[1] * SEMVER_MINOR_MULTIPLIER + parts[2] else -1
    }
}
