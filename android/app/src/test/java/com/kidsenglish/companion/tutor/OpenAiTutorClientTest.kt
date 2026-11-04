package com.kidsenglish.companion.tutor

import com.kidsenglish.companion.domain.PassageRetriever
import com.kidsenglish.companion.model.AgeBand
import com.kidsenglish.companion.sampleStory
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

class OpenAiTutorClientTest {
    private lateinit var server: MockWebServer
    private lateinit var request: TutorRequest

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        request = TutorRequest(AgeBand.AGE_6_8, sampleStory(), "p2", "Where is the kite?")
    }

    @After fun tearDown() = server.shutdown()

    @Test fun sendsModelMessagesAndBearerToken() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(successBody("The kite is in the tree.")))
        val client = client(token = "secret")

        val result = client.chat(request)
        val recorded = server.takeRequest()

        assertEquals("Bearer secret", recorded.getHeader("Authorization"))
        assertTrue(recorded.body.readUtf8().contains("qwen3:4b"))
        assertEquals("The kite is in the tree.", (result as TutorResult.Success).text)
    }

    @Test fun omitsAuthorizationWhenTokenIsEmpty() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(successBody("Ready")))
        val client = client(token = "")

        client.connectionTest()

        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test fun reservesEnoughOutputTokensForQwenThinkingResponse() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(successBody("OK")))

        client().connectionTest()

        assertTrue(server.takeRequest().body.readUtf8().contains("\"max_tokens\":512"))
    }

    @Test fun mapsAuthenticationAndMalformedResponse() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"choices\":[]}"))
        val client = client()

        assertTrue(client.chat(request) is TutorResult.AuthenticationRequired)
        assertTrue(client.chat(request) is TutorResult.InvalidResponse)
    }

    @Test fun mapsTimeoutToUnavailable() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val client = client(readTimeoutMs = 100)

        assertTrue(client.chat(request) is TutorResult.Unavailable)
    }

    @Test fun rootAndLegacyBaseUrlsUseOllamaOpenAiEndpoint() = runTest {
        for (path in listOf("/", "/v1", "/v1/")) {
            server.enqueue(MockResponse().setBody(successBody("Ready")))
            assertTrue(client(basePath = path).chat(request) is TutorResult.Success)
            assertEquals("/v1/chat/completions", server.takeRequest().path)
        }
    }

    private fun client(token: String = "", readTimeoutMs: Long = 2_000, basePath: String = "/v1"): OpenAiTutorClient {
        val http = OkHttpClient.Builder()
            .connectTimeout(1, TimeUnit.SECONDS)
            .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
            .callTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
            .build()
        return OpenAiTutorClient(
            httpClient = http,
            promptBuilder = PromptBuilder(PassageRetriever()),
            connectionProvider = {
                ModelConnection(
                    baseUrl = server.url(basePath).toString(),
                    modelName = "qwen3:4b",
                    token = token,
                    allowDebugHttp = true,
                )
            },
            isDebug = true,
        )
    }

    private fun successBody(text: String) =
        """{"choices":[{"message":{"role":"assistant","content":"$text"}}]}"""
}
