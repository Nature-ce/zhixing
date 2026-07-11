package com.zhixing.data.ai

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import com.zhixing.data.DecompositionException
import com.zhixing.data.DecompositionResult
import com.zhixing.data.DecompositionService
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 通过自建后端代理调用大模型做任务拆解（ADR-0001）。
 *
 * 使用 DeepSeek OpenAI 兼容格式：POST /chat/completions，messages 数组，模型只返回
 * 一段 JSON 数组文本放在 choices[0].message.content 里。本类的职责是：
 *   - 构造请求（system prompt 限定只输出 JSON + user prompt 携带任务信息）
 *   - 解析 content 为 List<DecompositionResult>
 *   - 任何失败（网络非 2xx / 解析）统一转 DecompositionException
 */
class RemoteDecompositionService(
    private val api: DecompositionApi,
    private val moshi: Moshi,
    private val model: String,
) : DecompositionService {

    override suspend fun decompose(taskTitle: String, taskDescription: String?): List<DecompositionResult> {
        val request = ChatCompletionRequest(
            model = model,
            messages = listOf(
                ChatMessage(role = "system", content = SYSTEM_PROMPT),
                ChatMessage(role = "user", content = buildUserPrompt(taskTitle, taskDescription)),
            ),
        )

        val response: ChatCompletionResponse = try {
            api.chatCompletions(request)
        } catch (e: IOException) {
            throw DecompositionException("网络连接失败：${e.message}")
        } catch (e: HttpException) {
            val errorBody = try {
                e.response()?.errorBody()?.string()?.take(200)?.trim()
            } catch (ex: Exception) { null }
            val msg = if (!errorBody.isNullOrBlank()) "HTTP ${e.code()}：$errorBody"
                      else "拆解服务返回错误（HTTP ${e.code()}）"
            throw DecompositionException(msg)
        }

        val content: String = response.choices
            .firstOrNull()?.message?.content
            ?: throw DecompositionException("拆解服务返回内容为空")

        return parseContent(content)
    }

    /** 把 choices[0].message.content（JSON 数组字符串）解析为子项目列表。 */
    private fun parseContent(content: String): List<DecompositionResult> {
        val type = Types.newParameterizedType(List::class.java, DecompositionResult::class.java)
        val adapter = moshi.adapter<List<DecompositionResult>>(type)
        return try {
            adapter.fromJson(content) ?: throw DecompositionException("拆解结果为空")
        } catch (e: Exception) {
            // Moshi 解析失败或 LLM 返回非预期格式
            throw DecompositionException("拆解结果解析失败：${e.message}")
        }
    }

    private fun buildUserPrompt(title: String, description: String?): String =
        if (description.isNullOrBlank()) "任务：$title"
        else "任务：$title\n描述：$description"

    companion object {
        private val SYSTEM_PROMPT = """
你是任务拆解助手。请把用户给出的任务拆解成若干可执行的子项目。

严格只返回一个 JSON 数组，每个元素格式：
  {"title":"子项目标题","estimatedDuration":预估分钟数}
- title：简短可执行的子项目标题
- estimatedDuration：整数，预估完成所需分钟数

不要返回任何解释、注释或 Markdown 代码块，只返回 JSON 数组本身。
        """.trimIndent()

        private const val CHAT_COMPLETIONS_PATH = "chat/completions"

        /**
         * 创建服务实例。
         *
         * 容错处理用户在"设置"页填写 base URL 的两种常见形式：
         *   - 目录形式（正确）：`https://api.example.com/` 或 `https://api.example.com/v1`
         *   - 完整 endpoint（常见误填）：`https://api.example.com/v1/chat/completions`
         * 末段若已是 "chat/completions" 则主动剥离，交给 Retrofit 再由相对路径
         * [DecompositionApi] 拼接出正确地址。
         */
        fun create(baseUrl: String, token: String, model: String = "deepseek-chat"): RemoteDecompositionService {
            var url = baseUrl.trimEnd('/')
            if (url.endsWith("chat/completions", ignoreCase = true)) {
                // 剥掉末段 "chat/completions"，保留前缀目录让 Retrofit 重新拼相对路径
                url = url.removeSuffix("chat/completions").removeSuffix("/").trimEnd('/')
            }
            // Retrofit 要求 baseUrl 以 '/' 结尾
            val normalizedBaseUrl = if (url.endsWith("/")) url else "$url/"
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val original: Request = chain.request()
                    val authorized = original.newBuilder()
                        .header("Authorization", "Bearer $token")
                        .build()
                    chain.proceed(authorized)
                }
                .build()

            val moshi = Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(normalizedBaseUrl)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()

            val api = retrofit.create(DecompositionApi::class.java)
            return RemoteDecompositionService(api, moshi, model)
        }
    }
}

/** Retrofit 接口：OpenAI 兼容 chat/completions。 */
interface DecompositionApi {
    @POST("chat/completions")
    suspend fun chatCompletions(@Body request: ChatCompletionRequest): ChatCompletionResponse
}

/** 请求体。 */
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
)

data class ChatMessage(
    val role: String,
    val content: String,
)

/** 响应体。 */
data class ChatCompletionResponse(
    val choices: List<Choice> = emptyList(),
)

data class Choice(
    val message: ChatMessage,
)
