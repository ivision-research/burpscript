package com.carvesystems.burpscript

import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.equality.shouldBeEqualToComparingFields
import io.kotest.matchers.nulls.shouldBeNull
import io.mockk.every
import io.mockk.spyk
import java.nio.file.Files
import java.nio.file.Paths


class ConfigTest : StringSpec() {
    lateinit var cfg: Config

    init {
        val configDir = tempdir()
        val valid = configDir.toPath().resolve("config.json")
        Files.writeString(valid, """
            {
                "python": {
                    "executablePath": "/usr/bin/python3",
                    "contextOptions": [
                        {"opt": "opt1", "value": "val1"},
                        {"opt": "opt2", "value": "val2"}
                    ]
                }
            }
        """)

        val invalid = configDir.toPath().resolve("invalid.json")
        Files.writeString(invalid, "asdf")

        beforeSpec {
            cfg = spyk<Config>()
        }

        "configs" {
            every {
                cfg.configFile()
            } returns valid

            cfg.readConfig()!!.shouldBeEqualToComparingFields(
                ScriptConfig(
                    python = PythonLangOptions(
                        executable = "/usr/bin/python3",
                        contextOptions = listOf(
                            LangOpt("opt1", "val1"),
                            LangOpt("opt2", "val2")
                        )
                    )
                )
            )
        }

        "fails okay" {
            every {
                cfg.configFile()
            } returns null
            Config.readConfig().shouldBeNull()

            every {
                cfg.configFile()
            } returns Paths.get("nonexistent")
            Config.readConfig().shouldBeNull()

            every {
                cfg.configFile()
            } returns invalid
            Config.readConfig().shouldBeNull()
        }

        "deserialization" {
            val cfg1 = Config.parse("""
                {
                    "python": {
                        "executablePath": "/usr/bin/python3",
                        "contextOptions": [
                            {"opt": "opt1", "value": "val1"},
                            {"opt": "opt2", "value": "val2"}
                        ]
                    }
                }
            """)!!
            cfg1.shouldBeEqualToComparingFields(
                ScriptConfig(
                    python = PythonLangOptions(
                        executable = "/usr/bin/python3",
                        contextOptions = listOf(
                            LangOpt("opt1", "val1"),
                            LangOpt("opt2", "val2")
                        )
                    ),
                    js = null
                )
            )

            val cfg2 = Config.parse("""
                {
                    "js": {
                        "contextOptions": [
                            {"opt": "opt1", "value": "val1"},
                            {"opt": "opt2", "value": "val2"}
                        ]
                    }
                }
            """)!!
            cfg2.shouldBeEqualToComparingFields(
                ScriptConfig(
                    python = null,
                    js = LangOptions(
                        contextOptions = listOf(
                            LangOpt("opt1", "val1"),
                            LangOpt("opt2", "val2")
                        )
                    )
                )
            )

            val empty = Config.parse("{}")!!
            empty.shouldBeEqualToComparingFields(ScriptConfig())

            Config.parse("asdf").shouldBeNull()
            Config.parse("""{"wrong": "field"}""").shouldBeNull()
        }
    }
}