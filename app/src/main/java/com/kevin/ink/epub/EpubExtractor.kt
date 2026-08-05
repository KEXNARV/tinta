package com.kevin.ink.epub

import java.io.File
import java.util.zip.ZipFile

/** Descomprime un .epub a una carpeta para poder leer sus páginas. Idempotente. */
object EpubExtractor {

    fun extract(epubFile: File, targetDir: File): File {
        // Si ya está descomprimido (tiene el container.xml), reutilizamos.
        val marker = File(targetDir, "META-INF/container.xml")
        if (marker.exists()) return targetDir

        targetDir.mkdirs()
        ZipFile(epubFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val outFile = File(targetDir, entry.name)
                // Protección contra rutas maliciosas (zip-slip)
                if (!outFile.canonicalPath.startsWith(targetDir.canonicalPath + File.separator)) {
                    continue
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        outFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
        }
        return targetDir
    }
}
