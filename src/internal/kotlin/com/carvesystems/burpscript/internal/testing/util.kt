package com.carvesystems.burpscript.internal.testing

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively


@OptIn(ExperimentalPathApi::class)
inline fun tempdir(block: (Path) -> Unit) {
    val dir = Files.createTempDirectory("burpscript-tests").toAbsolutePath()

    try {
        block(dir)
    } finally {
        dir.deleteRecursively()
    }
}

inline fun tempfile(filename: String, block: (Path) -> Unit) {
    tempfiles(filename) {
        block(it.first())
    }
}

inline fun tempfiles(vararg filenames: String, block: (Array<out Path>) -> Unit) {
    val files = filenames.map { File(it) }
    val paths =
        files.map {
            Files.createTempFile(it.nameWithoutExtension, ".${it.extension}").toAbsolutePath()
        }.toTypedArray()

    try {
        block(paths)
    } finally {
        paths.forEach { it.deleteIfExists() }
    }
}

