package com.carvesystems.burpscript

/**
 * Marks a method or field as part of the scripting API
 */
annotation class ScriptApi

/**
 * Marks a method or field as an experimental part of the scripting API.
 * This means the api is subject to change or removal in future versions.
 */
annotation class ExperimentalScriptApi

/**
 * Marks a method as internal and not intended for use by scripts
 */
annotation class Internal
