package com.carvesystems.burpscript

import com.carvesystems.burpscript.interop.*
import burp.api.montoya.core.Annotations
import burp.api.montoya.core.HighlightColor
import org.graalvm.polyglot.Value
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.regex.Pattern

/**
 * Result of executing an external command
 */
class ExecResult(
    @ScriptApi val code: Int,
    @ScriptApi val stdout: ByteArray,
    @ScriptApi val stderr: ByteArray,
) {
    /**
     * Helper to check that the status code is 0
     */
    @ScriptApi
    fun ok(): Boolean = code == 0

    /**
     * Gets standard output as a string in the default charset
     */
    @ScriptApi
    fun getStdoutString(): String = stdout.toString(Charset.defaultCharset())

    /**
     * Gets standard error as a string in the default charset
     */
    @ScriptApi
    fun getStderrString(): String = stderr.toString(Charset.defaultCharset())
}

/**
 * Helpers to pass to scripts to perform some common actions
 */
class ScriptHelpers {

    private val kvStore = ScriptMap()

    /**
     * Helper to compile a Java [Pattern]
     *
     * The only benefit to this over the native regular expression interfaces
     * is just a uniform regex interface
     */
    @ScriptApi
    fun compilePattern(regex: String): Pattern = Pattern.compile(regex)

    /**
     * Parse a filter expression that can be applied to requests or responses
     */
    @ScriptApi
    fun parseFilter(code: String): FilterExpression? = try {
        FilterExpression.parse(code)
    } catch (e: Exception) {
        LogManager.getLogger(this).error("failed to parse filter:\n$code", e)
        null
    }

    @ScriptApi
    fun b64(value: AnyBinary): String =
        Base64.getEncoder().encode(value.asAnyBinaryToByteArray()).decodeToString()

    @ScriptApi
    fun unb64(asB64: String): UnsignedByteArray =
        Base64.getDecoder().decode(asB64).toUnsignedByteArray()

    @ScriptApi
    fun hex(value: AnyBinary): String =
        value.asAnyBinaryToByteArray().toHex()

    @ScriptApi
    fun unhex(asHex: String): UnsignedByteArray =
        asHex.decodeHex().toUnsignedByteArray()

    @ScriptApi
    fun getCryptoHelper(): ScriptCryptoHelper {
        return ScriptCryptoHelper()
    }

    @ScriptApi
    fun appendNotes(annotation: Annotations, notes: String) {
        val currentNotes = annotation.notes() ?: ""
        val newNotes = if (currentNotes.isNotEmpty()) {
            "$currentNotes; $notes"
        } else {
            notes
        }
        annotation.setNotes(newNotes)
    }

    @ScriptApi
    fun appendNotes(req: ScriptHttpRequest, notes: String) {
        appendNotes(req.annotations(), notes)
    }

    @ScriptApi
    fun appendNotes(res: ScriptHttpResponse, notes: String) {
        appendNotes(res.annotations(), notes)
    }

    @ScriptApi
    fun setNotes(res: ScriptHttpResponse, notes: String) {
        res.annotations().apply {
            setNotes(notes)
        }
    }

    @ScriptApi
    fun setNotes(req: ScriptHttpRequest, notes: String) {
        req.annotations().apply {
            setNotes(notes)
        }
    }

    /**
     * Set the highlight color for the request
     */
    @ScriptApi
    fun setHighlight(req: ScriptHttpRequest, color: HighlightColor) {
        req.annotations().apply {
            setHighlightColor(color)
        }
    }

    /**
     * Set the highlight color for the request from a string
     *
     * Valid values for [color] are:
     *
     * - "Red"
     * - "Orange"
     * - "Yellow"
     * - "Green"
     * - "Cyan"
     * - "Blue"
     * - "Pink"
     * - "Magenta"
     * - "Gray"
     */
    @ScriptApi
    fun setHighlight(req: ScriptHttpRequest, color: String) {
        setHighlight(req, getColor(color))
    }

    /**
     * Set the highlight color for the response
     */
    @ScriptApi
    fun setHighlight(res: ScriptHttpResponse, color: HighlightColor) {
        res.annotations().apply {
            setHighlightColor(color)
        }
    }

    /**
     * Set the highlight color for the response from a string
     *
     * Valid values for [color] are:
     *
     * - "Red"
     * - "Orange"
     * - "Yellow"
     * - "Green"
     * - "Cyan"
     * - "Blue"
     * - "Pink"
     * - "Magenta"
     * - "Gray"
     */
    @ScriptApi
    fun setHighlight(res: ScriptHttpResponse, color: String) {
        setHighlight(res, getColor(color))
    }

    private fun doExecStdin(stdin: ByteArray?, program: String, vararg args: String): ExecResult {
        val pb = with(ProcessBuilder(program, *args)) {
            redirectError(ProcessBuilder.Redirect.PIPE)
            redirectOutput(ProcessBuilder.Redirect.PIPE)
            start()
        }
        val input = pb.outputStream
        stdin?.let {
            input.write(it)
            input.flush()
        }
        input.close()
        val out = pb.inputStream.readAllBytes()
        val err = pb.errorStream.readAllBytes()
        val code = pb.waitFor()
        return ExecResult(code, out, err)
    }

    private fun doExec(program: String, vararg args: String): ExecResult = doExecStdin(null, program, *args)

    /**
     * Get a Java [File] object for the given path
     */
    @ScriptApi
    fun getFile(fileName: String): File = File(fileName)

    /**
     * Read all bytes from the given [InputStream] into a byte array
     */
    @ScriptApi
    fun readBytes(inputStream: InputStream): ByteArray = inputStream.readAllBytes()

    /**
     * Read the given [InputStream] into a string
     */
    @ScriptApi
    fun readString(inputStream: InputStream): String = readBytes(inputStream).toString(Charset.defaultCharset())

    @ScriptApi
    fun appendFile(fileName: String, content: String) {
        appendFile(fileName, content.toByteArray(Charset.defaultCharset()))
    }

    @ScriptApi
    fun appendFile(fileName: String, content: ByteArray) {
        Files.write(
            Paths.get(fileName), content, StandardOpenOption.WRITE, StandardOpenOption.APPEND, StandardOpenOption.CREATE
        )
    }

    @ScriptApi
    fun writeFile(fileName: String, content: String) {
        writeFile(fileName, content.toByteArray(Charset.defaultCharset()))
    }

    @ScriptApi
    fun writeFile(fileName: String, content: ByteArray) {
        Files.write(
            Paths.get(fileName),
            content,
            StandardOpenOption.WRITE,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        )
    }

    /**
     * Execute an external command
     */
    @ScriptApi
    fun exec(program: String, vararg args: String): ExecResult = try {
        doExec(program, *args)
    } catch (e: Exception) {
        LogManager.getLogger(this).error("failed to exec $program $args", e)
        throw e
    }

    /**
     * Execute an external command with the given standard input
     */
    @ScriptApi
    fun execStdin(stdin: Value, program: String, vararg args: String): ExecResult = try {
        doExecStdin(stdin.asAnyBinaryToByteArray(), program, *args)
    } catch (e: Exception) {
        LogManager.getLogger(this).error("failed to exec $program $args", e)
        throw e
    }

    private fun getColor(color: String): HighlightColor = if (color[0].isLowerCase()) {
        HighlightColor.highlightColor("${color[0].uppercase()}${color.substring(1)}")
    } else {
        HighlightColor.highlightColor(color)
    }

    /**
     * Provide access to a key/value store that persists data across reloads
     */
    @ScriptApi
    fun getKVStore(): ScriptMap = kvStore
}
