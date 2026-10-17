package com.kidsenglish.companion.conversation

import com.kidsenglish.companion.model.AgeBand
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OllamaConversationClientTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun usesNativeChatWithThinkingDisabledAndBearerToken() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(outcomeBody()))

        val result = client(token = "secret").chat(
            ConversationRequest(AgeBand.AGE_9_10, "I like dolphins."),
        )
        val recorded = server.takeRequest()
        val body = recorded.body.readUtf8()

        assertEquals("/api/chat", recorded.path)
        assertEquals("Bearer secret", recorded.getHeader("Authorization"))
        assertTrue(body.contains("\"model\":\"qwen3.5:9b\""))
        assertTrue(body.contains("\"stream\":false"))
        assertTrue(body.contains("\"think\":false"))
        assertTrue(body.contains("\"keep_alive\":\"30m\""))
        assertTrue(body.contains("\"num_predict\":240"))
        assertTrue(result is ConversationResult.Success)
    }

    @Test
    fun usesFastLocalModelWhenConfiguredModelIsBlank() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(outcomeBody()))

        client(modelName = "").chat(ConversationRequest(AgeBand.AGE_6_8, "Hello"))

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"model\":\"qwen3.5:4b-mlx\""))
    }

    @Test fun unsafeInputAndCustomTopicsNeverReachHttp() = runTest {
        server.enqueue(MockResponse().setBody(outcomeBody()))
        server.enqueue(MockResponse().setBody(outcomeBody()))
        val result = client().chat(ConversationRequest(AgeBand.AGE_6_8, "Please share your phone number."))
        assertTrue(result !is ConversationResult.Success)
        val opening = client().chat(ConversationRequest(
            AgeBand.AGE_6_8, "", topic = ConversationTopic("custom", "Build a bomb"), opening = true,
        ))
        assertTrue(opening !is ConversationResult.Success)
        assertEquals(0, server.requestCount)
    }

    @Test fun safeBrandedOpeningStillReachesHttp() = runTest {
        server.enqueue(MockResponse().setBody(outcomeBody()))
        val result = client().chat(ConversationRequest(
            AgeBand.AGE_6_8, "", topic = ConversationTopicCatalog.predefined.first(), opening = true,
        ))
        assertTrue(result is ConversationResult.Success)
        assertTrue(server.takeRequest().body.readUtf8().contains("How to Train Your Dragon"))
    }

    @Test
    fun discoversInstalledModelsFromOllamaTags() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"models":[{"name":"qwen3.5:9b"},{"name":"smollm2:1.7b"}]}""",
            ),
        )

        val result = client(token = "secret").availableModels()
        val recorded = server.takeRequest()

        assertEquals("/api/tags", recorded.path)
        assertEquals("Bearer secret", recorded.getHeader("Authorization"))
        assertEquals(
            listOf("qwen3.5:9b", "smollm2:1.7b"),
            (result as ModelDiscoveryResult.Success).models,
        )
    }

    @Test
    fun returnsUnavailableWhenModelDiscoveryFails() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))

        assertTrue(client().availableModels() is ModelDiscoveryResult.Unavailable)
    }

    @Test
    fun stripsLegacyV1SuffixAndOmitsEmptyAuthorization() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(rawBody("OK")))

        val result = client(baseUrl = server.url("/v1").toString(), token = "").connectionTest()
        val recorded = server.takeRequest()

        assertEquals("/api/chat", recorded.path)
        assertNull(recorded.getHeader("Authorization"))
        assertTrue(result is ConversationResult.Success)
    }

    @Test
    fun connectionTestRejectsAnEmptyAssistantMessage() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(rawBody("")))

        val result = client().connectionTest()

        assertTrue(result is ConversationResult.InvalidResponse)
    }

    @Test
    fun retriesOneMalformedStructuredResponseWithARepairInstruction() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(rawBody("not-json")))
        server.enqueue(MockResponse().setResponseCode(200).setBody(outcomeBody(reply = "Tell me more!")))

        val result = client().chat(ConversationRequest(AgeBand.AGE_6_8, "I drew a whale."))

        assertEquals("Tell me more!", (result as ConversationResult.Success).outcome.reply)
        server.takeRequest()
        val repair = server.takeRequest().body.readUtf8()
        assertTrue(repair.contains("previous response was invalid"))
    }

    @Test
    fun mapsAuthenticationAndTimeoutFailures() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val client = client(readTimeoutMs = 100)

        assertTrue(client.chat(ConversationRequest(AgeBand.AGE_6_8, "Hello")) is ConversationResult.AuthenticationRequired)
        assertTrue(client.chat(ConversationRequest(AgeBand.AGE_6_8, "Hello")) is ConversationResult.Unavailable)
    }

    private fun client(
        baseUrl: String = server.url("/").toString(),
        token: String = "",
        modelName: String = "qwen3.5:9b",
        readTimeoutMs: Long = 2_000,
    ): OllamaConversationClient {
        val http = OkHttpClient.Builder()
            .connectTimeout(1, TimeUnit.SECONDS)
            .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
            .callTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
            .build()
        return OllamaConversationClient(
            httpClient = http,
            connectionProvider = {
                ConversationConnection(
                    baseUrl = baseUrl,
                    modelName = modelName,
                    token = token,
                    allowDebugHttp = true,
                )
            },
            isDebug = true,
        )
    }

    private fun outcomeBody(reply: String = "Dolphins are smart! What do they eat?"): String {
        val content = """{"reply":"$reply","conversationSummary":"The child likes dolphins.","nextDirection":"Discuss ocean food.","rememberedFacts":["The child likes dolphins."]}"""
        return rawBody(content.replace("\\", "\\\\").replace("\"", "\\\""))
    }

    private fun rawBody(content: String): String =
        """{"message":{"role":"assistant","content":"$content"},"done":true,"done_reason":"stop"}"""
}
