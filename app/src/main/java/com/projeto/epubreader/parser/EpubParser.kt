package com.projeto.epubreader.parser

import android.content.Context
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

data class EpubBook(
    val title: String,
    val author: String,
    val coverPath: String?,
    val chapters: List<Chapter>,
    val extractedDir: File,
    val opfDir: File
)

data class Chapter(
    val title: String,
    val href: String,
    val filePath: String
)

class EpubParser(private val context: Context) {

    fun parse(epubFile: File): EpubBook {
        val destDir = File(context.filesDir, "books/${epubFile.nameWithoutExtension}")

        val needsExtract = !destDir.exists() ||
                !File(destDir, "META-INF/container.xml").exists()

        if (needsExtract) {
            destDir.deleteRecursively()
            destDir.mkdirs()
            extractZip(epubFile.inputStream(), destDir)
        }

        val opfPath = findOpfPath(destDir)
        val opfFile = File(destDir, opfPath)
        val opfDir = opfFile.parentFile ?: destDir
        val opfData = parseOpf(opfFile, opfDir)

        return EpubBook(
            title = opfData.title,
            author = opfData.author,
            coverPath = opfData.coverPath,
            chapters = opfData.chapters,
            extractedDir = destDir,
            opfDir = opfDir
        )
    }

    private fun extractZip(input: InputStream, destDir: File) {
        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val outFile = File(destDir, entry.name)
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { out -> zip.copyTo(out) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    private fun findOpfPath(destDir: File): String {
        val containerFile = File(destDir, "META-INF/container.xml")
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(containerFile.inputStream(), "UTF-8")

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "rootfile") {
                return parser.getAttributeValue(null, "full-path") ?: "OEBPS/content.opf"
            }
            eventType = parser.next()
        }
        return "OEBPS/content.opf"
    }

    private data class OpfData(
        val title: String,
        val author: String,
        val coverPath: String?,
        val chapters: List<Chapter>
    )

    private fun parseTocTitles(opfDir: File, manifestItems: Map<String, String>): Map<String, String> {
        val titles = mutableMapOf<String, String>()

        val ncxFile = manifestItems.values
            .firstOrNull { it.endsWith(".ncx") }
            ?.let { File(opfDir, it) }
            ?.takeIf { it.exists() }
            ?: File(opfDir, "toc.ncx").takeIf { it.exists() }
            ?: opfDir.parentFile?.let { File(it, "toc.ncx") }?.takeIf { it.exists() }

        val navFile = manifestItems.values
            .firstOrNull { it.endsWith("nav.xhtml") || it.endsWith("nav.html") }
            ?.let { File(opfDir, it) }
            ?.takeIf { it.exists() }
            ?: manifestItems.values
                .firstOrNull { it.contains("nav", ignoreCase = true) && (it.endsWith(".xhtml") || it.endsWith(".html")) }
                ?.let { File(opfDir, it) }
                ?.takeIf { it.exists() }

        if (ncxFile != null) {
            try {
                val factory = XmlPullParserFactory.newInstance()
                factory.isNamespaceAware = true
                val parser = factory.newPullParser()
                parser.setInput(ncxFile.inputStream(), "UTF-8")

                var currentSrc: String? = null
                var currentLabel: String? = null
                var insideNavPoint = false
                var insideText = false

                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            when (parser.name?.lowercase()) {
                                "navpoint" -> {
                                    insideNavPoint = true
                                    currentSrc = null
                                    currentLabel = null
                                }
                                "content" -> {
                                    if (insideNavPoint) {
                                        currentSrc = parser.getAttributeValue(null, "src")
                                            ?.split("#")?.firstOrNull()
                                    }
                                }
                                "text" -> insideText = true
                            }
                        }
                        XmlPullParser.TEXT -> {
                            if (insideText && insideNavPoint && currentLabel == null) {
                                currentLabel = parser.text?.trim()
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            when (parser.name?.lowercase()) {
                                "text" -> insideText = false
                                "navpoint" -> {
                                    if (currentSrc != null && currentLabel != null) {
                                        titles[currentSrc!!] = currentLabel!!
                                    }
                                    insideNavPoint = false
                                }
                            }
                        }
                    }
                    eventType = parser.next()
                }
            } catch (e: Exception) {
                // ignora erro no NCX
            }
        }

        if (titles.isEmpty() && navFile != null) {
            try {
                val content = navFile.readText()

                val tocBlock = Regex(
                    """<nav[^>]*epub:type=["\']toc["\'][^>]*>(.*?)</nav>""",
                    setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
                ).find(content)?.groupValues?.get(1) ?: content

                val pattern = Regex(
                    """<a[^>]+href=["\']([^"\']*?)["\'][^>]*>(.*?)</a>""",
                    setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
                )
                pattern.findAll(tocBlock).forEach { match ->
                    val fullHref = match.groupValues[1]
                    val href = fullHref.split("#").firstOrNull()?.trim() ?: return@forEach
                    if (href.isEmpty()) return@forEach
                    val label = match.groupValues[2]
                        .replace(Regex("<[^>]+>"), "")
                        .trim()
                    if (label.isNotEmpty() && !titles.containsKey(href)) {
                        titles[href] = label
                    }
                }
            } catch (e: Exception) {
                // ignora erro no NAV
            }
        }

        android.util.Log.d("TOC_TITLES", "Títulos encontrados: ${titles.size}")
        titles.forEach { (href, title) ->
            android.util.Log.d("TOC_TITLES", "  $href -> $title")
        }

        return titles
    }

    private fun parseOpf(opfFile: File, opfDir: File): OpfData {
        var title = "Sem título"
        var author = "Autor desconhecido"
        var coverId: String? = null
        var coverPath: String? = null
        val manifestItems = mutableMapOf<String, String>()
        val spineOrder = mutableListOf<String>()

        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(opfFile.inputStream(), "UTF-8")

        var insideMetadata = false
        var insideSpine = false
        var currentTag = ""

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name ?: ""
                    when (currentTag) {
                        "metadata" -> insideMetadata = true
                        "spine"    -> insideSpine = true
                        "item" -> {
                            val id   = parser.getAttributeValue(null, "id") ?: ""
                            val href = parser.getAttributeValue(null, "href") ?: ""
                            if (id.isNotEmpty()) manifestItems[id] = href
                            val props = parser.getAttributeValue(null, "properties") ?: ""
                            if (props.contains("cover-image")) coverPath =
                                File(opfDir, href).absolutePath
                        }
                        "itemref" -> {
                            val idref = parser.getAttributeValue(null, "idref") ?: ""
                            if (idref.isNotEmpty()) spineOrder.add(idref)
                        }
                        "meta" -> {
                            val name = parser.getAttributeValue(null, "name") ?: ""
                            if (name == "cover") coverId =
                                parser.getAttributeValue(null, "content")
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (insideMetadata) {
                        val text = parser.text?.trim() ?: ""
                        when {
                            currentTag.endsWith("title")   && text.isNotEmpty() -> title = text
                            currentTag.endsWith("creator") && text.isNotEmpty() -> author = text
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "metadata" -> insideMetadata = false
                        "spine"    -> insideSpine = false
                    }
                    currentTag = ""
                }
            }
            eventType = parser.next()
        }

        if (coverPath == null && coverId != null) {
            manifestItems[coverId]?.let { href ->
                coverPath = File(opfDir, href).absolutePath
            }
        }

        val tocTitles = parseTocTitles(opfDir, manifestItems)

        val chapters = spineOrder.mapIndexedNotNull { index, idref ->
            val href = manifestItems[idref] ?: return@mapIndexedNotNull null
            val file = File(opfDir, href)
            val hrefFileName = href.split("/").last()

            val chapterTitle = tocTitles[href]                          // match exato
                ?: tocTitles["text/$href"]                              // ← tenta com prefixo text/
                ?: tocTitles[hrefFileName]                              // match por nome do arquivo
                ?: tocTitles.entries.firstOrNull {
                    it.key.endsWith("/$hrefFileName") || it.key == hrefFileName
                }?.value
                ?: "Parte ${index + 1}"

            android.util.Log.d("TOC_CHAPTERS", "$href -> $chapterTitle")

            Chapter(
                title = chapterTitle,
                href = href,
                filePath = file.absolutePath
            )
        }

        return OpfData(title, author, coverPath, chapters)
    }
}