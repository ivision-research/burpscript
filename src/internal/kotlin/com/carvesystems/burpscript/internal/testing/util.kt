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
    val fn = File(filename)
    val file = Files.createTempFile(fn.nameWithoutExtension, ".${fn.extension}").toAbsolutePath()

    try {
        block(file)
    } finally {
        file.deleteIfExists()
    }
}

