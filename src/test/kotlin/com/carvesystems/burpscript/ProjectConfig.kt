package com.carvesystems.burpscript

import burp.api.montoya.internal.ObjectFactoryLocator
import io.kotest.core.config.AbstractProjectConfig

class ProjectConfig : AbstractProjectConfig() {

    override suspend fun beforeProject() {
        ObjectFactoryLocator.FACTORY = mockObjectFactory()
    }

}