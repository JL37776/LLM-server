package com.nzshores.llmserver.data.local

import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class LocalGgufFile(
    val path: String,
    val name: String,
    val sizeBytes: Long,
)

class LocalModelScanner {

    suspend fun scan(): List<LocalGgufFile> = withContext(Dispatchers.IO) {
        val found = mutableListOf<LocalGgufFile>()
        val visited = mutableSetOf<String>()

        for (root in scanRoots()) {
            if (!root.exists() || !root.canRead()) continue
            scanDirectory(root, found, visited, depth = 0)
        }

        found.sortedByDescending { it.sizeBytes }
    }

    private fun scanRoots(): List<File> {
        val roots = mutableListOf<File>()

        val internal = Environment.getExternalStorageDirectory()
        if (internal.exists()) {
            roots.add(File(internal, "Download"))
            roots.add(File(internal, "Downloads"))
            roots.add(File(internal, "Documents"))
            roots.add(internal)
        }

        val sdCards = File("/storage")
        if (sdCards.exists() && sdCards.canRead()) {
            sdCards.listFiles()?.forEach { mount ->
                if (mount.name != "emulated" && mount.name != "self" && mount.canRead()) {
                    roots.add(mount)
                }
            }
        }

        return roots
    }

    private fun scanDirectory(
        dir: File,
        found: MutableList<LocalGgufFile>,
        visited: MutableSet<String>,
        depth: Int,
    ) {
        if (depth > MAX_DEPTH) return
        val canonical = dir.canonicalPath
        if (!visited.add(canonical)) return
        if (!dir.canRead()) return

        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isFile && file.name.endsWith(".gguf", ignoreCase = true) && file.length() > 0) {
                found.add(LocalGgufFile(
                    path = file.absolutePath,
                    name = file.name,
                    sizeBytes = file.length(),
                ))
            } else if (file.isDirectory && !file.name.startsWith(".")) {
                scanDirectory(file, found, visited, depth + 1)
            }
        }
    }

    companion object {
        private const val MAX_DEPTH = 6
    }
}
