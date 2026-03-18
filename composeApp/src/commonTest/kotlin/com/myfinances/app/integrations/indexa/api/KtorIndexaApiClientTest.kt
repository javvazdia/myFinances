package com.myfinances.app.integrations.indexa.api

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KtorIndexaApiClientTest {
    @Test
    fun extractsPersonNameWhenPersonIsObject() {
        val person = Json.parseToJsonElement("""{"name":"Jane Doe"}""")

        assertEquals("Jane Doe", extractPersonName(person))
    }

    @Test
    fun ignoresPersonWhenIndexaReturnsEmptyArray() {
        val person = Json.parseToJsonElement("[]")

        assertNull(extractPersonName(person))
    }
}
