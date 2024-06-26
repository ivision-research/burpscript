package com.carvesystems.burpscript

import com.carvesystems.burpscript.interop.fromJsonAs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import java.nio.charset.Charset

object Config {
    val burpScript by lazy {
        readConfig() ?: ScriptConfig()
    }

    fun readConfig(): ScriptConfig? {
        val confFile = configFile() ?: return null

        if (!confFile.exists()) {
            return null
        }

        return try {
            parse(Files.readString(confFile))
        } catch (e: Exception) {
            null
        }
    }

    fun parse(json: String): ScriptConfig? =
        try {
            fromJsonAs<ScriptConfig>(json)
        } catch (e: Exception) {
            LogManager.getLogger("Config").error("failed to parse config: $json", e)
            null
        }

    fun configFile(): Path? {
        val cfgDir = System.getenv("XDG_CONFIG_HOME")?.let {
            Paths.get(it)
        } ?: System.getProperty("user.home")?.let {
            Paths.get(it, ".config")
        }

        return cfgDir?.resolve(Paths.get("burpscript", "conf.json"))
    }
}

@Serializable
data class LangOpt(
    @SerialName("opt") val option: String,
    val value: String
)

@Serializable
data class JsLangOptions(val contextOptions: List<LangOpt>? = null)

@Serializable
data class PythonLangOptions(
    @SerialName("executablePath") val executable: String? = defaultSystemPythonExe,
    @SerialName("pythonPath") val pythonPath: String? = defaultSystemPythonPath,
    val contextOptions: List<LangOpt>? = null,
) {
    companion object {
        // Get a path to the Python executable for locating packages. The priority is:
        //
        //  1. polyglot.python.Executable property
        //  2. `python` in $PATH
        //  3. `python3` in $PATH
        //  4. Output of `which python`
        //  5. Output of `which python3`
        private val defaultSystemPythonExe by lazy {
            getSystemPythonExePath()
        }

        // PYTHONPATH
        private val defaultSystemPythonPath by lazy {
            getSystemPythonPath()
        }

        private fun getSystemPythonExePath(): String? =
                System.getProperty("polyglot.python.Executable")
                ?: pythonExeFromPath("python")
                ?: pythonExeFromPath("python3")
                ?: pythonExeFromWhich("python")
                ?: pythonExeFromWhich("python3")

        private fun pythonExeFromPath(exeName: String): String? {
            return System.getenv("PATH")?.let { path ->
                val iter = path.split(':').iterator()
                while (iter.hasNext()) {
                    val dir = Paths.get(iter.next())
                    if (!dir.isAbsolute) {
                        continue
                    }
                    val python = dir.resolve(exeName)
                    if (python.exists()) {
                        return python.toString()
                    }
                }
                null
            }
        }

        private fun pythonExeFromWhich(exeName: String): String? {
            val proc = try {
                with(ProcessBuilder("which", exeName)) {
                    redirectError(ProcessBuilder.Redirect.PIPE)
                    redirectOutput(ProcessBuilder.Redirect.PIPE)
                    redirectInput(ProcessBuilder.Redirect.DISCARD)
                    start()
                }
            } catch (e: Exception) {
                return null
            }

            val status = proc.waitFor()
            if (status != 0) {
                return null
            }

            return proc.inputStream.readAllBytes().toString(Charset.defaultCharset()).trim()
        }

        private fun getSystemPythonPath(): String? =
                pythonPathFromEnv()

        private fun pythonPathFromEnv(): String? =
            System.getenv("PYTHONPATH")
    }
}

@Serializable
data class ScriptConfig(
    val python: PythonLangOptions = PythonLangOptions(),
    val js: JsLangOptions = JsLangOptions(),
)


