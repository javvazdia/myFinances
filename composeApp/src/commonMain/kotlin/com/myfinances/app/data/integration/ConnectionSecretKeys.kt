package com.myfinances.app.data.integration

import com.myfinances.app.domain.model.integration.ExternalProviderId

internal fun buildConnectionSecretKey(
    providerId: ExternalProviderId,
    connectionId: String,
): String = "${providerId.name}:$connectionId"
