package com.carvesystems.burpscript

import com.carvesystems.burpscript.interop.fromJsonAs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists

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
data class LangOptions(val contextOptions: List<LangOpt>)

@Serializable
data class PythonLangOptions(
    @SerialName("executablePath") val executable: String? = null,
    @SerialName("pythonPath") val pythonPath: String? = null,
    val contextOptions: List<LangOpt>? = null,
)

@Serializable
data class ScriptConfig(
    val python: PythonLangOptions? = null,
    val js: LangOptions? = null,
)


