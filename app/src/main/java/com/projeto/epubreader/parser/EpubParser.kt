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
    val extractedDir: File
)

data class Chapter(
    val title: String,
    val href: String,       // caminho relativo dentro do EPUB
    val filePath: String    // caminho absoluto no dispositivo
)

class EpubParser(private val context: Context) {

    fun parse(epubFile: File): EpubBook {
        // 1. Pasta de destino para os arquivos extraídos
        val destDir = File(context.filesDir, "books/${epubFile.nameWithoutExtension}")
        if (!destDir.exists()) {
            destDir.mkdirs()
            extractZip(epubFile.inputStream(), destDir)
        }

        // 2. Encontra o arquivo OPF (container.xml aponta para ele)
        val opfPath = findOpfPath(destDir)
        val opfFile = File(destDir, opfPath)
        val opfDir = opfFile.parentFile ?: destDir

        // 3. Lê metadados e spine (ordem dos capítulos)
        val opfData = parseOpf(opfFile, opfDir)

        return EpubBook(
            title = opfData.title,
            author = opfData.author,
            coverPath = opfData.coverPath,
            chapters = opfData.chapters,
            extractedDir = destDir
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

    private fun parseOpf(opfFile: File, opfDir: File): OpfData {
        var title = "Sem título"
        var author = "Autor desconhecido"
        var coverId: String? = null
        var coverPath: String? = null
        val manifestItems = mutableMapOf<String, String>() // id -> href
        val spineOrder = mutableListOf<String>()          // idrefs em ordem

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
                            // detecta capa
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

        // Resolve capa pelo id (EPUB 2)
        if (coverPath == null && coverId != null) {
            manifestItems[coverId]?.let { href ->
                coverPath = File(opfDir, href).absolutePath
            }
        }

        // Monta lista de capítulos na ordem do spine
        val chapters = spineOrder.mapIndexedNotNull { index, idref ->
            val href = manifestItems[idref] ?: return@mapIndexedNotNull null
            val file = File(opfDir, href)
            Chapter(
                title = "Capítulo ${index + 1}",
                href = href,
                filePath = file.absolutePath
            )
        }

        return OpfData(title, author, coverPath, chapters)
    }
}