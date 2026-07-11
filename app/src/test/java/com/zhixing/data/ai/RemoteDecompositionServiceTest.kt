package com.zhixing.data.ai

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.zhixing.data.DecompositionException
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * JVM TDD: RemoteDecompositionService 的请求构造 + 响应解析契约。
 *
 * 用 MockWebServer 模拟后端代理（DeepSeek OpenAI 兼容格式），验证：
 *   - 正确的 endpoint / method / Authorization header / request body
 *   - choices[0].message.content 内的 JSON 数组被解析为 List<DecompositionResult>
 *   - 非 2xx 抛 DecompositionException
 *   - 后端返回错误体时 message 应带上错误体（让用户看见真实原因）
 */
class RemoteDecompositionServiceTest {

    private val server = MockWebServer()
    private lateinit var service: RemoteDecompositionService
    private val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    @Before
    fun setUp() {
        server.start()
        service = RemoteDecompositionService.create(
            baseUrl = server.url("/").toString(),
            token = "test-token",
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun decompose_parses_chatCompletions_content_into_results() {
        // DeepSeek OpenAI 兼容响应
        val body = """
            {"choices":[{"message":{"role":"assistant","content":"[{\"title\":\"选书目\",\"estimatedDuration\":30},{\"title\":\"划重点\",\"estimatedDuration\":45}]"}}]}
        """.trimIndent()
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(body)
                .addHeader("Content-Type", "application/json"),
        )

        val results = runBlocking { service.decompose("读书笔记", "读完第一章") }

        assertThat(results).hasSize(2)
        assertThat(results[0].title).isEqualTo("选书目")
        assertThat(results[0].estimatedDuration).isEqualTo(30)
        assertThat(results[1].title).isEqualTo("划重点")
        assertThat(results[1].estimatedDuration).isEqualTo(45)

        // 请求契约
        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/chat/completions")
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-token")
        val requestBody = request.body.readUtf8()
        assertThat(requestBody).contains("\"model\":\"deepseek-chat\"")
        assertThat(requestBody).contains("读书笔记")
        assertThat(requestBody).contains("读完第一章")
    }

    @Test
    fun decompose_throws_on_non_2xx() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("{\"error\":\"boom\"}"))

        assertThatThrownBy { runBlocking { service.decompose("读书笔记", null) } }
            .isInstanceOf(DecompositionException::class.java)
    }

    /**
     * 后端返回 400（Bad Request，常见"不支持的模型名"等参数错误）时，错误信息应带上
     * 响应体内容，让用户/开发者能看见真实原因而非只有 HTTP code。
     */
    @Test
    fun decompose_includes_error_body_on_400() {
        val errorBody = "{\"error\":{\"message\":\"Unsupported model (model=deepseek-chat)\",\"type\":\"invalid_request_error\"}}"
        server.enqueue(MockResponse().setResponseCode(400).setBody(errorBody))

        assertThatThrownBy { runBlocking { service.decompose("读书笔记", null) } }
            .isInstanceOf(DecompositionException::class.java)
            .hasMessageContaining("400")
            .hasMessageContaining("Unsupported model")
    }

    /**
     * 当网络层抛出未预期的 RuntimeException（例如缺少 INTERNET 权限时 OkHttp 抛
     * SecurityException），service 应把它转为 DecompositionException 而不是让
     * 异常穿透到调用方（穿透到协程会闪退）。
     */
    @Test
    fun decompose_wraps_unexpected_runtimeException_into_decompositionException() {
        val service = RemoteDecompositionService(
            api = object : DecompositionApi {
                override suspend fun chatCompletions(request: ChatCompletionRequest): ChatCompletionResponse {
                    throw SecurityException("missing INTERNET permission")
                }
            },
            moshi = moshi,
            model = "deepseek-chat",
        )

        assertThatThrownBy { runBlocking { service.decompose("任务", "描述") } }
            .isInstanceOf(DecompositionException::class.java)
            .hasMessageContaining("调用")
    }

    /**
     * 用户误把完整 endpoint URL 填进 base URL 时，create() 应能归一化：
     * 把末段 "chat/completions" 剥离，使 Retrofit 拼出的请求路径仍为
     * "/chat/completions" 而不是 "/chat/completions/chat/completions"。
     */
    @Test
    fun create_normalizes_full_endpoint_baseUrl() {
        val endpointServer = MockWebServer()
        endpointServer.start()
        try {
            val url = endpointServer.url("/v1/chat/completions").toString()
            val svc = RemoteDecompositionService.create(baseUrl = url, token = "t")

            val body = """
                {"choices":[{"message":{"role":"assistant","content":"[{\"title\":\"x\",\"estimatedDuration\":10}]"}}]}
            """.trimIndent()
            endpointServer.enqueue(
                MockResponse().setResponseCode(200).setBody(body)
                    .addHeader("Content-Type", "application/json"),
            )

            val results = runBlocking { svc.decompose("任务", null) }
            assertThat(results).hasSize(1)

            val request = endpointServer.takeRequest()
            assertThat(request.path).isEqualTo("/v1/chat/completions")
        } finally {
            endpointServer.shutdown()
        }
    }
}
