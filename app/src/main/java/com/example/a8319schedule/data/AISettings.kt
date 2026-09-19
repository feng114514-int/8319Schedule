package com.example.a8319schedule.data

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.map

/**
 * AI助手设置
 */
data class AISettings(
    val apiKey: String = "",
    val model: String = "",
    val baseUrl: String = "",
    val enabled: Boolean = false,
    val systemPrompt: String = "",
    // 预设配置名称
    val presetName: String = ""
)

/**
 * 预设模型配置
 */
data class ModelPreset(
    val name: String,
    val baseUrl: String,
    val defaultModel: String,
    val description: String
)

/**
 * 内置预设模型
 */
object ModelPresets {
    val presets = listOf(
        // OpenAI
        ModelPreset(
            name = "OpenAI",
            baseUrl = "https://api.openai.com/v1",
            defaultModel = "gpt-3.5-turbo",
            description = "OpenAI官方API"
        ),
        // 硅基流动（很多模型）
        ModelPreset(
            name = "硅基流动",
            baseUrl = "https://api.siliconflow.cn/v1",
            defaultModel = "Qwen/Qwen2.5-7B-Instruct",
            description = "平价中转，支持多种模型"
        ),
        // 智谱AI
        ModelPreset(
            name = "智谱AI",
            baseUrl = "https://open.bigmodel.cn/api/paas/v4",
            defaultModel = "glm-4-flash",
            description = "智谱清言GLM-4"
        ),
        // 阿里云百炼
        ModelPreset(
            name = "阿里云百炼",
            baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            defaultModel = "qwen-turbo",
            description = "通义千问系列"
        ),
        // 百度千帆
        ModelPreset(
            name = "百度千帆",
            baseUrl = "https://qianfan.baidubce.com/v2/chat/completions",
            defaultModel = "ernie-4.0-8k-latest",
            description = "文心一言4.0"
        ),
        // 自定义
        ModelPreset(
            name = "自定义",
            baseUrl = "",
            defaultModel = "",
            description = "手动输入API地址和模型名"
        )
    )
}

/**
 * 聊天消息
 */
@Serializable
data class ChatMessage(
    val id: String = System.currentTimeMillis().toString(),
    val role: String,  // "user" | "assistant" | "system" | "tool"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val toolCalls: List<FunctionCallResult> = emptyList(),
    val toolCallId: String = ""  // role="tool"时需要
)

/**
 * 课程变更操作
 */
enum class ChangeAction {
    ADD, UPDATE, DELETE
}

/**
 * 课程变更
 */
data class CourseChange(
    val action: ChangeAction,
    val courseId: Long? = null,        // UPDATE/DELETE时需要
    val courseInstanceId: String? = null, // 用于定位特定课程实例
    val courseGroupId: String? = null,   // 用于批量操作同一门课程
    val course: Course? = null,          // ADD/UPDATE时需要
    val explanation: String = "",         // 变化说明
    val affectedWeeks: List<Int> = emptyList() // 受影响的周次列表
)

/**
 * AI课程变更解析结果
 */
data class AIParseResult(
    val success: Boolean,
    val changes: List<CourseChange> = emptyList(),
    val message: String = "",
    val rawResponse: String = ""
)

private val Context.aiDataStore: DataStore<Preferences> by preferencesDataStore(name = "ai_settings")

/**
 * AI助手设置管理器
 */
class AISettingsManager(private val ctx: Context) {

    companion object {
        private val API_KEY = stringPreferencesKey("api_key")
        private val MODEL = stringPreferencesKey("model")
        private val BASE_URL = stringPreferencesKey("base_url")
        private val ENABLED = booleanPreferencesKey("enabled")
        private val SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        private val PRESET_NAME = stringPreferencesKey("preset_name")
        private const val ENCRYPTED_PREFS_NAME = "ai_settings_encrypted"
        private const val ENC_KEY_API_KEY = "api_key"
        
        // 默认的系统提示词
        val DEFAULT_SYSTEM_PROMPT = """
你是一个专业的大学课表助手，帮助用户管理课程表。你必须精确理解用户的各种自然语言表达。

【课程时间体系 —— 必须牢记】
本课表每天有10个小节，组成5个大节。节次(period)是基本单位，范围为1-10。

大节与小节的对应关系：
  第1大节 = 第1-2节  →  上午第1大节 (08:30-10:05)
  第2大节 = 第3-4节  →  上午第2大节 (10:25-12:00)
  第3大节 = 第5-6节  →  下午第1大节 (14:00-15:35)
  第4大节 = 第7-8节  →  下午第2大节 (15:55-17:30)
  第5大节 = 第9-10节 →  晚上大节    (19:00-20:35)

各小节的具体时间：
  第1节 08:30-09:15  |  第2节 09:20-10:05
  第3节 10:25-11:10  |  第4节 11:15-12:00
  第5节 14:00-14:45  |  第6节 14:50-15:35
  第7节 15:55-16:40  |  第8节 16:45-17:30
  第9节 19:00-19:45  |  第10节 19:50-20:35

【自然语言理解规则 —— 必须遵循】
用户说的"大节"映射：
  "上午第一大节"/"上午第1大节"/"上午第一节课" → startPeriod=1, endPeriod=2
  "上午第二大节"/"上午第2大节"/"上午第二节课" → startPeriod=3, endPeriod=4
  "下午第一大节"/"下午第1大节"/"下午第一节课" → startPeriod=5, endPeriod=6
  "下午第二大节"/"下午第2大节"/"下午第二节课" → startPeriod=7, endPeriod=8
  "晚上大节"/"晚上"/"晚课" → startPeriod=9, endPeriod=10
  "第五大节"/"第5大节" → startPeriod=9, endPeriod=10

用户说的时间段映射：
  "上午" → 通常指第1-4节（上午1-2大节）
  "下午" → 通常指第5-8节（下午1-2大节）
  "晚上"/"晚课" → 第9-10节

用户说的"第X节"直接映射为period=X。
用户说"1-2节"表示 startPeriod=1, endPeriod=2。

【重要规则】
1. 添加/修改课程时，startPeriod和endPeriod必须是1-10的整数
2. 一门课通常占一个大节（2个小节），如 startPeriod=1, endPeriod=2
3. 有些课可能跨大节，如"1-4节"表示 startPeriod=1, endPeriod=4
4. 绝不要把"大节"编号直接当作startPeriod！"第五大节"不是startPeriod=5，而是startPeriod=9！
5. 当用户只说"下午"而未指定具体大节时，默认为下午第一大节(startPeriod=5, endPeriod=6)

【调用工具前的思考环节 —— 每次都必须先做完这四步，再决定调不调工具、调哪一个】
（这四步只在内部推演，不要把推演过程写给用户看，最终只输出结论和说明）
第1步 判断意图：属于 查询 / 新增 / 修改调课 / 删除 / 通知设置 / 考试管理 / 纯聊天 中的哪一类？
第2步 提取并换算参数：课程名、星期几、节次（大节必须先换算成 startPeriod/endPeriod）、周次、教师、教室。
第3步 校验信息是否足够：
  - 修改、删除类操作要求课程名与课表中的名称完全一致。只要你不确定（用户用了简称、你没查过课表），就必须先调用 query_schedule 拿到准确名称和相关周次，再执行修改；
  - 缺少星期、节次、周次等关键信息时，先用一句话向用户确认，此时不要调用任何修改类工具；
  - 判断这次操作是"只影响某几个周次"还是"影响这门课的所有周次"。
第4步 选择工具：按下面的工具选择表挑最匹配的一个；一次不够，可以按顺序调用多个工具。
  - 传参时参数名必须与工具定义完全一致（如 update_course 用 course_name、new_startPeriod），不要自造参数名。

【工具选择表 —— 严格对照，不要凭感觉选】
- 查询课表（今天/明天/某周有哪些课、某门课在哪上） → query_schedule，禁止凭记忆回答
- 新增一门课，只在一个周次上 → add_course
- 新增一门课，要覆盖多个周次 → batch_add_course
- 调整某门课，且这门课的所有周次都要变（长期调整） → update_course
- 调整某门课，只改指定的少数周次（本周、下周、第3-5周） → update_course_weeks
- 删除整门课（所有周次） → delete_course
- 只删除某门课的某几个周次 → delete_course_weeks
- 通知提醒相关 → set_notification_settings
- 考试相关 → query_exams 查、add_exam 加、update_exam 改（必须先 query_exams 拿 examId）、delete_exam 删
- 只是闲聊或问常识 → 不调用任何工具

【调课专项规则 —— 最容易出错，必须遵守】
1. 遇到"把X调到/换到/改到 周Y第Z节"这类调课请求，先确定影响范围，再选工具：
   - 用户说了具体周次，或说了"这周/下周/只调一次/临时调课" → 用 update_course_weeks（weeks 传对应周次）
   - 用户说了"以后都/每周/一直/整门课" → 用 update_course
   - 用户没说、无法判断 → 先用一句话追问"是只调本周，还是以后每次都调？"，问清楚之前不要调用任何修改类工具
2. update_course 和 delete_course 会作用于该课程的所有周次！如果用户只是想调一节课，绝对不要用它们。
3. 只有用户明确要求"两门课互换时间"时才做互换：系统没有互换工具，用两次 update_course_weeks（或两次 update_course）分别修改两门课。
4. 不允许凭猜测填 course_name。不确定就先用 query_schedule(courseName=用户说的名字) 模糊查询，再把返回的准确名称用于修改或删除。
5. 如果修改类工具返回"未找到名为「X」的课程"，不要用同样的参数重试，应当先 query_schedule 查清准确名称，再向用户核实。
6. 修改/删除成功后，回复里要讲清楚：改了哪门课、改成什么时间、影响哪些周次。

【省略信息推断规则 —— 必须】
1. 用户未指定星期几时，默认为当天。例如用户说"晚上加一节语文课"，dayOfWeek=今天的星期值
2. 用户未指定教师或教室时，先检查课表中是否已有同名课程：
   - 如果已有同名课程，自动复用其教师和教室，不需要再问用户
   - 如果没有同名课程，则询问用户教师和教室信息
3. 用户未指定周次时，默认为当前周次

【使用规则】
- 思考环节确认参数完整后，请直接调用对应工具，不要只回一段文字
- 信息不足（缺周次、缺时间、课程名不确定）时，先追问或先调用 query_schedule，此时不要调用修改类工具
- 工具调用后，系统会返回结果，然后告诉用户操作结果
- 如果用户只是聊天，不需要调用工具
- 添加跨多周的课程时，优先使用 batch_add_course
- 修改多个周次的同一门课时，优先使用 update_course_weeks
- 一次回复允许调用多个工具，最终目的是完整实现用户需求

【示例】
用户: "把高数改到周二上午第一大节"
你: 目标模糊——用户没说周次，无法判断是只改本周还是以后每次都改 → 先追问："高数是只调本周，还是以后每周都改到周二上午第一大节？"（此时不调用任何工具）
用户: "以后都改"
你: 调用 update_course(course_name="高数", new_dayOfWeek=2, new_startPeriod=1, new_endPeriod=2)

用户: "这周三的高数调到周五1-2节"
你: 调用 update_course_weeks(course_name="高数", weeks=[3], new_dayOfWeek=5, new_startPeriod=1, new_endPeriod=2)

用户: "添加一门体育课，周五下午第二大节"
你: 调用 add_course(name="体育课", dayOfWeek=5, startPeriod=7, endPeriod=8)

用户: "把高数放到第五大节"
你: 调用 update_course(course_name="高数", new_startPeriod=9, new_endPeriod=10)

用户: "删除英语课"
你: 调用 delete_course(course_name="英语课")

用户: "添加高数，周一1-2节，第1-16周"
你: 调用 batch_add_course(courses=[{name="高数", dayOfWeek=1, startPeriod=1, endPeriod=2, weekNumber=1}, ...第1-16周每项])

用户: "今天有什么课？"
你: 调用 query_schedule(dayOfWeek=今天的星期值, weekNumber=当前周)，再根据返回结果回答

用户: "明天下午有什么课？"
你: 调用 query_schedule(dayOfWeek=明天的星期值, weekNumber=当前周)，再从结果中筛出 startPeriod 在5-8之间的课程回答

用户: "晚上加一节语文课"
你: 先查课程信息中是否已有"语文"课（有则复用教师和教室），然后调用 add_course(name="语文", dayOfWeek=今天, startPeriod=9, endPeriod=10, teacher=复用值, classroom=复用值)

用户: "加一节高数，3-4节"
你: 课表中已有"高数"→复用其教师和教室，调用 add_course(name="高数", dayOfWeek=今天, startPeriod=3, endPeriod=4, teacher=复用值, classroom=复用值, weekNumber=当前周)
        """.trimIndent()
    }

    // 加密存储：专门用于保存 API Key
    private val encryptedPrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            ctx,
            ENCRYPTED_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    val settings: Flow<AISettings> = ctx.aiDataStore.data.map { prefs ->
        // 优先从加密存储读取 API Key；若为空则从旧存储迁移
        val encryptedApiKey = encryptedPrefs.getString(ENC_KEY_API_KEY, null)
        val apiKey = if (!encryptedApiKey.isNullOrEmpty()) {
            encryptedApiKey
        } else {
            val legacyKey = prefs[API_KEY] ?: ""
            if (legacyKey.isNotEmpty()) {
                // 迁移：写入加密存储，清除旧存储
                encryptedPrefs.edit().putString(ENC_KEY_API_KEY, legacyKey).apply()
                ctx.aiDataStore.edit { it.remove(API_KEY) }
            }
            legacyKey
        }
        AISettings(
            apiKey = apiKey,
            model = prefs[MODEL] ?: "",
            baseUrl = prefs[BASE_URL] ?: "",
            enabled = prefs[ENABLED] ?: false,
            // 注意：保存设置时可能写入空字符串，空值必须回退到默认提示词，否则会丢失全部前置指令
            systemPrompt = prefs[SYSTEM_PROMPT]?.takeIf { it.isNotBlank() } ?: DEFAULT_SYSTEM_PROMPT,
            presetName = prefs[PRESET_NAME] ?: ""
        )
    }
    
    suspend fun saveApiKey(apiKey: String) {
        encryptedPrefs.edit().putString(ENC_KEY_API_KEY, apiKey).apply()
    }
    
    suspend fun saveModel(model: String) {
        ctx.aiDataStore.edit { prefs ->
            prefs[MODEL] = model
        }
    }
    
    suspend fun saveBaseUrl(baseUrl: String) {
        ctx.aiDataStore.edit { prefs ->
            prefs[BASE_URL] = baseUrl
        }
    }
    
    suspend fun setEnabled(enabled: Boolean) {
        ctx.aiDataStore.edit { prefs ->
            prefs[ENABLED] = enabled
        }
    }
    
    suspend fun saveSystemPrompt(prompt: String) {
        ctx.aiDataStore.edit { prefs ->
            // 空字符串视为“使用默认提示词”，避免把默认前置指令覆盖为空
            if (prompt.isNotBlank()) prefs[SYSTEM_PROMPT] = prompt else prefs.remove(SYSTEM_PROMPT)
        }
    }
    
    suspend fun saveSettings(settings: AISettings) {
        encryptedPrefs.edit().putString(ENC_KEY_API_KEY, settings.apiKey).apply()
        ctx.aiDataStore.edit { prefs ->
            prefs[MODEL] = settings.model
            prefs[BASE_URL] = settings.baseUrl
            prefs[ENABLED] = settings.enabled
            // 空提示词不写入，交由 DEFAULT_SYSTEM_PROMPT 兜底
            if (settings.systemPrompt.isNotBlank()) {
                prefs[SYSTEM_PROMPT] = settings.systemPrompt
            } else {
                prefs.remove(SYSTEM_PROMPT)
            }
            prefs[PRESET_NAME] = settings.presetName
        }
    }
    
    suspend fun savePreset(preset: ModelPreset) {
        ctx.aiDataStore.edit { prefs ->
            prefs[BASE_URL] = preset.baseUrl
            prefs[MODEL] = preset.defaultModel
            prefs[PRESET_NAME] = preset.name
        }
    }
    
    suspend fun resetToDefault() {
        encryptedPrefs.edit().clear().apply()
        ctx.aiDataStore.edit { prefs ->
            prefs.clear()
        }
    }
}
