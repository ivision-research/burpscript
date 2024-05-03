package com.carvesystems.burpscript

import java.nio.file.Path
import java.util.*

sealed class ScriptEvent(val id: UUID) {

    /**
     * Published to set a script
     *
     * This is used for both new scripts and updates to scripts
     */
    class SetScript(id: UUID, val path: Path, val language: Language, val opts: Script.Options) : ScriptEvent(id)

    /**
     * Published to permanently remove a script
     */
    class RemoveScript(id: UUID) : ScriptEvent(id)

    /**
     * Published to update the options for the given script
     */
    class OptionsUpdated(id: UUID, val opts: Script.Options) : ScriptEvent(id)

}