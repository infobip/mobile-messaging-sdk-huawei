/*
 * MobileMessagingTestable.kt
 * Mobile Messaging SDK
 *
 * Copyright (c) 2016-2025 Infobip Limited
 * Licensed under the Apache License, Version 2.0
 */
package org.infobip.mobile.messaging.chat.mobileapi

import android.content.Context
import org.infobip.mobile.messaging.MobileMessagingCore
import org.infobip.mobile.messaging.mobileapi.MobileApiResourceProvider
import org.infobip.mobile.messaging.platform.Broadcaster
import org.infobip.mobile.messaging.platform.Platform
import org.infobip.mobile.messaging.util.ModuleLoader
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MobileMessagingTestable private constructor(
    context: Context,
    broadcaster: Broadcaster,
    executorService: ExecutorService,
) : MobileMessagingCore(context, broadcaster, executorService, ModuleLoader(context)) {

    companion object {

        fun create(
            context: Context,
            broadcaster: Broadcaster,
            mobileApiResourceProvider: MobileApiResourceProvider,
        ): MobileMessagingTestable {
            val instance = MobileMessagingTestable(context, broadcaster, Executors.newSingleThreadExecutor())
            Platform.reset(instance)
            MobileMessagingCore.mobileApiResourceProvider = mobileApiResourceProvider
            return instance
        }

    }

}