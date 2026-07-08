package com.example.a8319schedule.data

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * AI课程助手服务 - Agent模式
 * 使用Function Calling让AI能够直接调用工具修改课表
 */
class AICourseService(
    private val apiKey: String,
    private val model: String = "gpt-3.5-turbo",
    private val baseUrl: String = "https://api.openai.com/v1"
) {

    companion object {
        private const val TAG = "AICourseService"

        // 共享 OkHttpClient 实例，避免重复创建线程池和连接池
        private val sharedClient = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        private val sharedStreamClient = sharedClient.newBuilder()
            .readTimeout(300, TimeUnit.SECONDS)
            .build()
        
        // 定义工具函数
        val FUNCTIONS = listOf(
            JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "add_course")
                    put("description", "添加一门新课程到课表。如果用户未指定星期几，默认为当天；如果未指定教师/教室，先检查已有同名课程并复用其信息，无同名课程则需询问用户")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("required", JSONArray().put("name").put("dayOfWeek").put("startPeriod").put("endPeriod"))
                        put("properties", JSONObject().apply {
                            put("name", JSONObject().apply {
                                put("type", "string")
                                put("description", "课程名称")
                            })
                            put("teacher", JSONObject().apply {
                                put("type", "string")
                                put("description", "教师姓名。如课表中已有同名课程，复用其教师；否则需询问用户")
                            })
                            put("classroom", JSONObject().apply {
                                put("type", "string")
                                put("description", "教室。如课表中已有同名课程，复用其教室；否则需询问用户")
                            })
                            put("dayOfWeek", JSONObject().apply {
                                put("type", "integer")
                                put("description", "星期几: 1=周一, 2=周二, 3=周三, 4=周四, 5=周五, 6=周六, 7=周日")
                            })
                            put("weekNumber", JSONObject().apply {
                                put("type", "integer")
                                put("description", "周次(1-20)，默认为1")
                            })
                            put("startPeriod", JSONObject().apply {
                                put("type", "integer")
                                put("description", "开始小节次(1-10)。1-2节=上午第1大节, 3-4节=上午第2大节, 5-6节=下午第1大节, 7-8节=下午第2大节, 9-10节=晚上第5大节。注意：用户说'第X大节'时需转换：第1大节→1, 第2大节→3, 第3大节→5, 第4大节→7, 第5大节→9")
                            })
                            put("endPeriod", JSONObject().apply {
                                put("type", "integer")
                                put("description", "结束小节次(1-10)。1-2节=上午第1大节, 3-4节=上午第2大节, 5-6节=下午第1大节, 7-8节=下午第2大节, 9-10节=晚上第5大节。注意：用户说'第X大节'时需转换：第1大节→2, 第2大节→4, 第3大节→6, 第4大节→8, 第5大节→10")
                            })
                        })
                    })
                })
            },
            JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "batch_add_course")
                    put("description", "批量添加多门课程到课表，一次调用可添加多个课程记录（如同一课程在多个周次上课）。当需要添加跨多周的课程时，优先使用此工具而非多次调用add_course。用户未指定星期几时默认当天；未指定教师/教室时，已有同名课程则复用")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("required", JSONArray().put("courses"))
                        put("properties", JSONObject().apply {
                            put("courses", JSONObject().apply {
                                put("type", "array")
                                put("description", "要添加的课程列表，每个元素包含一门课程的一次上课信息")
                                put("items", JSONObject().apply {
                                    put("type", "object")
                                    put("required", JSONArray().put("name").put("dayOfWeek").put("startPeriod").put("endPeriod"))
                                    put("properties", JSONObject().apply {
                                        put("name", JSONObject().apply {
                                            put("type", "string")
                                            put("description", "课程名称")
                                        })
                                        put("teacher", JSONObject().apply {
                                            put("type", "string")
                                            put("description", "教师姓名")
                                        })
                                        put("classroom", JSONObject().apply {
                                            put("type", "string")
                                            put("description", "教室")
                                        })
                                        put("dayOfWeek", JSONObject().apply {
                                            put("type", "integer")
                                            put("description", "星期几: 1=周一, 2=周二, 3=周三, 4=周四, 5=周五, 6=周六, 7=周日")
                                        })
                                        put("weekNumber", JSONObject().apply {
                                            put("type", "integer")
                                            put("description", "周次(1-20)，默认为1")
                                        })
                                        put("startPeriod", JSONObject().apply {
                                            put("type", "integer")
                                            put("description", "开始小节次(1-10)。大节映射：第1大节→1, 第2大节→3, 第3大节→5, 第4大节→7, 第5大节→9")
                                        })
                                        put("endPeriod", JSONObject().apply {
                                            put("type", "integer")
                                            put("description", "结束小节次(1-10)。大节映射：第1大节→2, 第2大节→4, 第3大节→6, 第4大节→8, 第5大节→10")
                                        })
                                    })
                                })
                            })
                        })
                    })
                })
            },
            JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "update_course")
                    put("description", "修改课表中现有课程的信息")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("required", JSONArray().put("course_name"))
                        put("properties", JSONObject().apply {
                            put("course_name", JSONObject().apply {
                                put("type", "string")
                                put("description", "要修改的课程名称（必须精确匹配）")
                            })
                            put("new_name", JSONObject().apply {
                                put("type", "string")
                                put("description", "新的课程名称")
                            })
                            put("new_teacher", JSONObject().apply {
                                put("type", "string")
                                put("description", "新的教师姓名")
                            })
                            put("new_classroom", JSONObject().apply {
                                put("type", "string")
                                put("description", "新的教室")
                            })
                            put("new_dayOfWeek", JSONObject().apply {
                                put("type", "integer")
                                put("description", "新的星期几: 1=周一, 2=周二, 3=周三, 4=周四, 5=周五, 6=周六, 7=周日")
                            })
                            put("new_weekNumber", JSONObject().apply {
                                put("type", "integer")
                                put("description", "新的周次(1-20)")
                            })
                            put("new_startPeriod", JSONObject().apply {
                                put("type", "integer")
                                put("description", "新的开始小节次(1-10)。大节映射：第1大节→1, 第2大节→3, 第3大节→5, 第4大节→7, 第5大节→9")
                            })
                            put("new_endPeriod", JSONObject().apply {
                                put("type", "integer")
                                put("description", "新的结束小节次(1-10)。大节映射：第1大节→2, 第2大节→4, 第3大节→6, 第4大节→8, 第5大节→10")
                            })
                        })
                    })
                })
            },
            JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "delete_course")
                    put("description", "删除课表中的课程")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("required", JSONArray().put("course_name"))
                        put("properties", JSONObject().apply {
                            put("course_name", JSONObject().apply {
                                put("type", "string")
                                put("description", "要删除的课程名称（必须精确匹配）")
                            })
                        })
                    })
                })
            },
            JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "update_course_weeks")
                    put("description", "批量修改某个课程在特定周次的信息，可同时修改多个属性")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("required", JSONArray().put("course_name").put("weeks"))
                        put("properties", JSONObject().apply {
                            put("course_name", JSONObject().apply {
                                put("type", "string")
                                put("description", "要修改的课程名称（必须精确匹配）")
                            })
                            put("weeks", JSONObject().apply {
                                put("type", "array")
                                put("description", "要修改的周次列表，如[3,5,7]表示第3、5、7周")
                                put("items", JSONObject().apply {
                                    put("type", "integer")
                                })
                            })
                            put("new_name", JSONObject().apply {
                                put("type", "string")
                                put("description", "新的课程名称（可选）")
                            })
                            put("new_teacher", JSONObject().apply {
                                put("type", "string")
                                put("description", "新的教师姓名（可选）")
                            })
                            put("new_classroom", JSONObject().apply {
                                put("type", "string")
                                put("description", "新的教室（可选）")
                            })
                            put("new_dayOfWeek", JSONObject().apply {
                                put("type", "integer")
                                put("description", "新的星期几: 1=周一, 2=周二, 3=周三, 4=周四, 5=周五, 6=周六, 7=周日（可选）")
                            })
                            put("new_startPeriod", JSONObject().apply {
                                put("type", "integer")
                                put("description", "新的开始小节次(1-10)（可选）。大节映射：第1大节→1, 第2大节→3, 第3大节→5, 第4大节→7, 第5大节→9")
                            })
                            put("new_endPeriod", JSONObject().apply {
                                put("type", "integer")
                                put("description", "新的结束小节次(1-10)（可选）。大节映射：第1大节→2, 第2大节→4, 第3大节→6, 第4大节→8, 第5大节→10")
                            })
                        })
                    })
                })
            },
            JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "delete_course_weeks")
                    put("description", "批量删除某个课程在特定周次的课程记录")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("required", JSONArray().put("course_name").put("weeks"))
                        put("properties", JSONObject().apply {
                            put("course_name", JSONObject().apply {
                                put("type", "string")
                                put("description", "要删除的课程名称（必须精确匹配）")
                            })
                            put("weeks", JSONObject().apply {
                                put("type", "array")
                                put("description", "要删除的周次列表，如[3,5,7]表示第3、5、7周")
                                put("items", JSONObject().apply {
                                    put("type", "integer")
                                })
                            })
                        })
                    })
                })
            },
            JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "query_schedule")
                    put("description", "查询课表中指定条件的课程。当用户问'今天/明天/某天有什么课'、'这周/某周有哪些课'、'某门课在什么时候'等查询类问题时，必须调用此工具获取课程数据，不要凭记忆回答。可按星期几、周次、课程名等条件筛选，不传条件则返回所有课程概要")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("dayOfWeek", JSONObject().apply {
                                put("type", "integer")
                                put("description", "查询星期几: 1=周一, 2=周二, 3=周三, 4=周四, 5=周五, 6=周六, 7=周日。不传则不按星期筛选")
                            })
                            put("weekNumber", JSONObject().apply {
                                put("type", "integer")
                                put("description", "查询第几周(1-20)。不传则不按周次筛选")
                            })
                            put("courseName", JSONObject().apply {
                                put("type", "string")
                                put("description", "按课程名模糊搜索。不传则不按名称筛选")
                            })
                        })
                    })
                })
            },
            JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "set_notification_settings")
                    put("description", "修改通知设置，包括上课提醒和每日课表摘要。可以开启/关闭提醒、设置提前提醒分钟数、设置每日摘要推送时间。用户只需表达意图，未提及的设置项保持不变")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("courseReminderEnabled", JSONObject().apply {
                                put("type", "boolean")
                                put("description", "是否开启上课提醒")
                            })
                            put("reminderMinutesBefore", JSONObject().apply {
                                put("type", "integer")
                                put("description", "提前多少分钟提醒，可选值：5, 10, 15, 20, 30, 45, 60")
                            })
                            put("dailySummaryEnabled", JSONObject().apply {
                                put("type", "boolean")
                                put("description", "是否开启每日课表摘要推送")
                            })
                            put("dailySummaryHour", JSONObject().apply {
                                put("type", "integer")
                                put("description", "每日摘要推送时间-小时（0-23）")
                            })
                            put("dailySummaryMinute", JSONObject().apply {
                                put("type", "integer")
                                put("description", "每日摘要推送时间-分钟（0-59）")
                            })
                        })
                    })
                })
            },
            JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "query_exams")
                    put("description", "查询当前课表的考试安排。可按课程名模糊筛选，不传条件则返回所有考试。返回结果中每条考试含 examId，仅用于后续 update_exam/delete_exam 定位，不要展示给用户")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("courseName", JSONObject().apply {
                                put("type", "string")
                                put("description", "按课程名模糊搜索。不传则返回全部考试")
                            })
                        })
                    })
                })
            },
            JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "add_exam")
                    put("description", "添加一条考试记录。考试时间用 examTimeRaw 传入，格式必须为 \"yyyy-MM-dd HH:mm~HH:mm\"（如 \"2026-06-29 10:25~12:05\"）。考场、座位号、教师等可选字段未提供时留空")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("required", JSONArray().put("courseName").put("examTimeRaw"))
                        put("properties", JSONObject().apply {
                            put("courseName", JSONObject().apply {
                                put("type", "string")
                                put("description", "课程名称")
                            })
                            put("examTimeRaw", JSONObject().apply {
                                put("type", "string")
                                put("description", "考试时间，格式 \"yyyy-MM-dd HH:mm~HH:mm\"，如 \"2026-06-29 10:25~12:05\"")
                            })
                            put("examRoom", JSONObject().apply {
                                put("type", "string")
                                put("description", "考场（可选）")
                            })
                            put("seatNumber", JSONObject().apply {
                                put("type", "string")
                                put("description", "座位号（可选）")
                            })
                            put("teacher", JSONObject().apply {
                                put("type", "string")
                                put("description", "授课教师（可选）")
                            })
                            put("courseCode", JSONObject().apply {
                                put("type", "string")
                                put("description", "课程编号（可选）")
                            })
                            put("campus", JSONObject().apply {
                                put("type", "string")
                                put("description", "校区（可选）")
                            })
                            put("sessionName", JSONObject().apply {
                                put("type", "string")
                                put("description", "考试场次（可选）")
                            })
                            put("remark", JSONObject().apply {
                                put("type", "string")
                                put("description", "备注（可选）")
                            })
                        })
                    })
                })
            },
            JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "update_exam")
                    put("description", "修改一条已有考试记录。必须先用 query_exams 获取 examId，再通过 examId 定位修改。未传入的字段保持原值不变")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("required", JSONArray().put("examId"))
                        put("properties", JSONObject().apply {
                            put("examId", JSONObject().apply {
                                put("type", "integer")
                                put("description", "要修改的考试记录 id（由 query_exams 返回）")
                            })
                            put("new_courseName", JSONObject().apply {
                                put("type", "string")
                                put("description", "新的课程名称（可选）")
                            })
                            put("new_examTimeRaw", JSONObject().apply {
                                put("type", "string")
                                put("description", "新的考试时间，格式 \"yyyy-MM-dd HH:mm~HH:mm\"（可选）")
                            })
                            put("new_examRoom", JSONObject().apply {
                                put("type", "string")
                                put("description", "新的考场（可选）")
                            })
                            put("new_seatNumber", JSONObject().apply {
                                put("type", "string")
                                put("description", "新的座位号（可选）")
                            })
                            put("new_teacher", JSONObject().apply {
                                put("type", "string")
                                put("description", "新的授课教师（可选）")
                            })
                            put("new_courseCode", JSONObject().apply {
                                put("type", "string")
                                put("description", "新的课程编号（可选）")
                            })
                            put("new_campus", JSONObject().apply {
                                put("type", "string")
                                put("description", "新的校区（可选）")
                            })
                            put("new_sessionName", JSONObject().apply {
                                put("type", "string")
                                put("description", "新的考试场次（可选）")
                            })
                            put("new_remark", JSONObject().apply {
                                put("type", "string")
                                put("description", "新的备注（可选）")
                            })
                        })
                    })
                })
            },
            JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "delete_exam")
                    put("description", "删除考试记录。优先按 examId 精确删除单条；若未提供 examId 而提供 courseName，则删除当前课表下所有同名考试。两个参数至少传一个")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("examId", JSONObject().apply {
                                put("type", "integer")
                                put("description", "要删除的考试记录 id（由 query_exams 返回，优先使用）")
                            })
                            put("courseName", JSONObject().apply {
                                put("type", "string")
                                put("description", "课程名称，用于删除所有同名考试（当未提供 examId 时使用）")
                            })
                        })
                    })
                })
            }
        )
    }
    
    private val client = sharedClient
    private val streamClient = sharedStreamClient

    /**
     * 构建API URL，智能处理baseUrl，避免路径重复
     */
    private fun buildApiUrl(endpoint: String = "chat/completions"): String {
        val cleanBaseUrl = baseUrl.trimEnd('/')
        return if (cleanBaseUrl.endsWith("/$endpoint")) {
            cleanBaseUrl
        } else {
            "$cleanBaseUrl/$endpoint"
        }
    }
    
    /**
     * 发送聊天消息并获取回复（支持Function Calling）
     * @return Pair(first=AI文本回复, second=函数调用结果列表)
     */
    suspend fun chat(
        messages: List<ChatMessage>,
        systemPrompt: String,
        functionResults: List<FunctionCallResult> = emptyList()
    ): Result<AgentResponse> = withContext(Dispatchers.IO) {
        try {
            val url = buildApiUrl()
            
            // 构建消息数组
            val messagesArray = JSONArray()
            
            // 添加系统提示词
            messagesArray.put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
            
            // 给 user/assistant 历史消息加上发送时间前缀，让 AI 能区分不同时刻的对话
            val timeFmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss EEE", java.util.Locale.CHINA)
            // 添加对话历史
            messages.forEach { msg ->
                // 仅给 user / assistant 消息加时间前缀，tool 消息不加
                val stampedContent = if (msg.role == "user" || msg.role == "assistant") {
                    val timeStr = timeFmt.format(java.util.Date(msg.timestamp))
                    if (msg.content.isEmpty()) "[$timeStr]" else "[$timeStr] ${msg.content}"
                } else {
                    msg.content
                }
                val msgObj = JSONObject().apply {
                    put("role", msg.role)
                    // assistant消息如果content为空且没有toolCalls，仍然需要设置content（某些API要求非null）
                    if (msg.role == "assistant" && msg.toolCalls.isNotEmpty()) {
                        put("content", stampedContent.ifEmpty { null })
                        put("tool_calls", JSONArray().apply {
                            msg.toolCalls.forEach { tc ->
                                put(JSONObject().apply {
                                    put("id", tc.toolCallId)
                                    put("type", "function")
                                    put("function", JSONObject().apply {
                                        put("name", tc.functionName)
                                        put("arguments", tc.arguments)
                                    })
                                })
                            }
                        })
                    } else if (msg.role == "tool") {
                        put("content", msg.content)
                        put("tool_call_id", msg.toolCallId)
                    } else {
                        put("content", stampedContent)
                    }
                }
                messagesArray.put(msgObj)
            }
            
            // 添加函数调用结果（兼容旧调用方式：对话历史中未包含tool消息时使用）
            functionResults.forEach { result ->
                // 避免重复：如果对话历史中已经有这个tool_call_id的tool消息，跳过
                val alreadyInHistory = messages.any { it.role == "tool" && it.toolCallId == result.toolCallId }
                if (!alreadyInHistory) {
                    messagesArray.put(JSONObject().apply {
                        put("role", "tool")
                        put("tool_call_id", result.toolCallId)
                        put("content", result.resultContent)
                    })
                }
            }
            
            // 构建请求体（带tools）
            val jsonBody = JSONObject().apply {
                put("model", model)
                put("messages", messagesArray)
                put("tools", JSONArray(FUNCTIONS.toString()))
                put("tool_choice", "auto")
                put("temperature", 0.7)
                put("max_tokens", 2000)
            }
            
            Log.d(TAG, "发送请求到: $url")
            Log.d(TAG, "请求体: $jsonBody")
            
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            Log.d(TAG, "响应状态: ${response.code}")
            Log.d(TAG, "响应体: $responseBody")
            
            if (!response.isSuccessful) {
                val errorMsg = when (response.code) {
                    401 -> "API密钥无效"
                    403 -> "API访问被拒绝，请检查API密钥权限"
                    429 -> "请求过于频繁，请稍后再试"
                    500 -> "服务器内部错误"
                    else -> "请求失败: ${response.code}"
                }
                return@withContext Result.failure(Exception(errorMsg))
            }
            
            if (responseBody.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("空响应"))
            }
            
            val jsonResponse = JSONObject(responseBody)
            val choices = jsonResponse.optJSONArray("choices")
            if (choices == null || choices.length() == 0) {
                return@withContext Result.failure(Exception("无有效响应"))
            }
            
            val message = choices.getJSONObject(0).optJSONObject("message")
            
            // 检查是否有函数调用
            val toolCalls = message?.optJSONArray("tool_calls")
            val functionCallResults = mutableListOf<FunctionCallResult>()
            
            if (toolCalls != null && toolCalls.length() > 0) {
                for (i in 0 until toolCalls.length()) {
                    val toolCall = toolCalls.getJSONObject(i)
                    val toolCallId = toolCall.optString("id")
                    val functionName = toolCall.optJSONObject("function")?.optString("name", "") ?: ""
                    val arguments = toolCall.optJSONObject("function")?.optString("arguments", "{}") ?: "{}"
                    
                    functionCallResults.add(FunctionCallResult(
                        toolCallId = toolCallId,
                        functionName = functionName,
                        arguments = arguments
                    ))
                    
                    Log.d(TAG, "AI调用函数: $functionName, 参数: $arguments")
                }
            }
            
            val textContent = message?.optString("content", "") ?: ""
            
            Result.success(AgentResponse(
                text = textContent,
                functionCalls = functionCallResults
            ))
        } catch (e: Exception) {
            Log.e(TAG, "请求异常: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 流式聊天，逐token返回
     * @return Flow<StreamChunk> 每次返回一个增量chunk
     */
    fun chatStream(
        messages: List<ChatMessage>,
        systemPrompt: String,
        functionResults: List<FunctionCallResult> = emptyList()
    ): Flow<StreamChunk> = callbackFlow {
        val url = buildApiUrl()
        
        // 构建消息数组
        val messagesArray = JSONArray()
        messagesArray.put(JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        })
        // 给 user/assistant 历史消息加上发送时间前缀，让 AI 能区分不同时刻的对话
        val timeFmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss EEE", java.util.Locale.CHINA)
        messages.forEach { msg ->
            val stampedContent = if (msg.role == "user" || msg.role == "assistant") {
                val timeStr = timeFmt.format(java.util.Date(msg.timestamp))
                if (msg.content.isEmpty()) "[$timeStr]" else "[$timeStr] ${msg.content}"
            } else {
                msg.content
            }
            val msgObj = JSONObject().apply {
                put("role", msg.role)
                if (msg.role == "assistant" && msg.toolCalls.isNotEmpty()) {
                    put("content", stampedContent.ifEmpty { null })
                    put("tool_calls", JSONArray().apply {
                        msg.toolCalls.forEach { tc ->
                            put(JSONObject().apply {
                                put("id", tc.toolCallId)
                                put("type", "function")
                                put("function", JSONObject().apply {
                                    put("name", tc.functionName)
                                    put("arguments", tc.arguments)
                                })
                            })
                        }
                    })
                } else if (msg.role == "tool") {
                    put("content", msg.content)
                    put("tool_call_id", msg.toolCallId)
                } else {
                    put("content", stampedContent)
                }
            }
            messagesArray.put(msgObj)
        }
        functionResults.forEach { result ->
            val alreadyInHistory = messages.any { it.role == "tool" && it.toolCallId == result.toolCallId }
            if (!alreadyInHistory) {
                messagesArray.put(JSONObject().apply {
                    put("role", "tool")
                    put("tool_call_id", result.toolCallId)
                    put("content", result.resultContent)
                })
            }
        }
        
        val jsonBody = JSONObject().apply {
            put("model", model)
            put("messages", messagesArray)
            put("tools", JSONArray(FUNCTIONS.toString()))
            put("tool_choice", "auto")
            put("temperature", 0.7)
            put("max_tokens", 2000)
            put("stream", true)
        }
        
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()
        
        // 收集完整的tool_calls（流式时tool_calls是分片到达的）
        val toolCallsMap = mutableMapOf<Int, ToolCallBuilder>()
        
        val eventSource = EventSources.createFactory(streamClient)
            .newEventSource(request, object : EventSourceListener() {
                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    if (data == "[DONE]") {
                        // 流结束，发送完整的tool_calls信息
                        val finalToolCalls = toolCallsMap.values.sortedBy { it.index }.map { builder ->
                            FunctionCallResult(
                                toolCallId = builder.id,
                                functionName = builder.functionName,
                                arguments = builder.arguments.toString()
                            )
                        }
                        trySend(StreamChunk.Done(finalToolCalls))
                        close()
                        return
                    }
                    
                    try {
                        val json = JSONObject(data)
                        val choices = json.optJSONArray("choices")
                        if (choices == null || choices.length() == 0) return
                        
                        val delta = choices.getJSONObject(0).optJSONObject("delta")
                        val finishReason = choices.getJSONObject(0).optString("finish_reason", "")
                        
                        // 处理文本内容
                        val content = delta?.let { d ->
                            if (d.isNull("content")) "" else d.optString("content", "")
                        } ?: ""
                        if (content.isNotEmpty()) {
                            trySend(StreamChunk.Text(content))
                        }
                        
                        // 处理tool_calls（流式分片）
                        val toolCalls = delta?.optJSONArray("tool_calls")
                        if (toolCalls != null) {
                            for (i in 0 until toolCalls.length()) {
                                val tc = toolCalls.getJSONObject(i)
                                val tcIndex = tc.optInt("index", 0)
                                val builder = toolCallsMap.getOrPut(tcIndex) { ToolCallBuilder(index = tcIndex) }
                                
                                if (!tc.isNull("id")) {
                                    builder.id = tc.optString("id", "")
                                }
                                
                                val func = tc.optJSONObject("function")
                                if (func != null) {
                                    if (!func.isNull("name")) {
                                        builder.functionName = func.optString("name", "")
                                    }
                                    if (!func.isNull("arguments")) {
                                        builder.arguments.append(func.optString("arguments", ""))
                                    }
                                }
                            }
                        }
                        
                        if (finishReason == "tool_calls" || finishReason == "stop") {
                            // 由[DONE]统一处理结束
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "解析SSE事件失败: ${e.message}")
                    }
                }
                
                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    val errorMsg = when (response?.code) {
                        401 -> "API密钥无效"
                        403 -> "API访问被拒绝"
                        429 -> "请求过于频繁，请稍后再试"
                        else -> t?.message ?: "流式请求失败"
                    }
                    trySend(StreamChunk.Error(errorMsg))
                    close()
                }
                
                override fun onClosed(eventSource: EventSource) {
                    close()
                }
            })
        
        awaitClose {
            eventSource.cancel()
        }
    }
    
    /**
     * 辅助类：流式拼接tool_call
     */
    private class ToolCallBuilder(
        val index: Int,
        var id: String = "",
        var functionName: String = "",
        val arguments: StringBuilder = StringBuilder()
    )
    
    /**
     * 将图片文件转换为base64编码
     */
    fun encodeImageToBase64(imageBytes: ByteArray): String {
        return Base64.encodeToString(imageBytes, Base64.NO_WRAP)
    }

    /**
     * 使用视觉模型识别课表图片，返回结构化课程JSON
     * @param base64Image base64编码的图片数据（不含前缀）
     * @param imageMimeType 图片MIME类型，如 "image/jpeg" 或 "image/png"
     * @return Result<String> 成功时返回AI响应的JSON字符串
     */
    suspend fun parseScheduleImage(
        base64Image: String,
        imageMimeType: String = "image/jpeg"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = buildApiUrl()

            val systemPrompt = """
                你是一个课表识别专家。用户会发送一张课表图片，你需要识别图片中的课程信息，并以JSON格式返回。

                请严格按照以下JSON格式返回，不要返回其他内容：
                {
                  "success": true,
                  "courses": [
                    {
                      "name": "课程名称",
                      "teacher": "教师姓名",
                      "classroom": "教室",
                      "dayOfWeek": 1,
                      "startPeriod": 1,
                      "endPeriod": 2,
                      "weekRange": "1-16"
                    }
                  ]
                }

                字段说明：
                - name: 课程名称（必填）
                - teacher: 教师姓名（可选，无法识别则为空字符串）
                - classroom: 教室（可选，无法识别则为空字符串）
                - dayOfWeek: 星期几，1=周一，2=周二，...，7=周日（必填）
                - startPeriod: 开始小节次(1-10)，1=第1节，2=第2节，3=第3节，4=第4节，5=第5节，6=第6节，7=第7节，8=第8节，9=第9节，10=第10节（必填）。大节映射：第1大节(1-2节)→startPeriod=1，第2大节(3-4节)→startPeriod=3，第3大节(5-6节)→startPeriod=5，第4大节(7-8节)→startPeriod=7，第5大节(9-10节)→startPeriod=9
                - endPeriod: 结束小节次(1-10)（必填）。大节映射：第1大节(1-2节)→endPeriod=2，第2大节(3-4节)→endPeriod=4，第3大节(5-6节)→endPeriod=6，第4大节(7-8节)→endPeriod=8，第5大节(9-10节)→endPeriod=10
                - weekRange: 周次范围，如 "1-16"、"1-10,12-16"、"1,3,5"（必填）

                注意：
                1. 如果课程跨多周，请在weekRange中完整表示
                2. 如果图片中不清晰或无法识别某个字段，请留空字符串或默认值
                3. 只返回JSON，不要有其他解释文字
                4. 如果图片中没有课表或无法识别，返回 {"success": false, "message": "无法识别课表"}
            """.trimIndent()

            val requestJson = JSONObject().apply {
                put("model", model)
                put("max_tokens", 4096)
                put("temperature", 0.2)
                put("messages", JSONArray().apply {
                    // system message
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    // user message with image
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", JSONArray().apply {
                            // text prompt
                            put(JSONObject().apply {
                                put("type", "text")
                                put("text", "请识别这张课表图片，提取所有课程信息并以JSON格式返回。")
                            })
                            // image
                            put(JSONObject().apply {
                                put("type", "image_url")
                                put("image_url", JSONObject().apply {
                                    put("url", "data:$imageMimeType;base64,$base64Image")
                                    put("detail", "high")
                                })
                            })
                        })
                    })
                })
            }

            Log.d(TAG, "发送图片识别请求到: $url, model: $model")
            
            // 打印请求体（截断base64避免日志过长）
            val debugRequest = requestJson.toString()
            Log.d(TAG, "请求体(前500字符): ${debugRequest.take(500)}")
            Log.d(TAG, "base64长度: ${base64Image.length}")

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            Log.d(TAG, "图片识别响应状态: ${response.code}")
            Log.d(TAG, "图片识别响应体: $responseBody")

            if (!response.isSuccessful) {
                val errorMsg = when (response.code) {
                    401 -> "API密钥无效，请检查设置"
                    403 -> "API访问被拒绝，请检查API密钥权限"
                    429 -> "请求过于频繁，请稍后再试"
                    413 -> "图片太大，请压缩后重试"
                    else -> "请求失败(${response.code}): ${responseBody?.take(200)}"
                }
                return@withContext Result.failure(Exception(errorMsg))
            }

            if (responseBody.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("空响应"))
            }

            val jsonResponse = JSONObject(responseBody)
            val choices = jsonResponse.optJSONArray("choices")
            if (choices == null || choices.length() == 0) {
                return@withContext Result.failure(Exception("无有效响应"))
            }

            val content = choices.getJSONObject(0)
                .optJSONObject("message")
                ?.optString("content", "")

            if (content.isNullOrBlank()) {
                return@withContext Result.failure(Exception("AI返回内容为空"))
            }

            Log.d(TAG, "AI图片识别原始返回: $content")
            Result.success(content)

        } catch (e: Exception) {
            Log.e(TAG, "图片识别异常: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 从对话中提取需要记忆的关键信息
     * @param conversation 本轮对话内容（用户输入 + AI回复）
     * @param existingMemory 已有记忆内容
     * @return 提取出的新记忆片段，如果无新信息则返回空字符串
     */
    suspend fun extractMemory(
        conversation: String,
        existingMemory: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = buildApiUrl()

            val systemPrompt = """
你是一个信息提取助手。请分析用户与AI助手的对话，提取出关于用户的**长期偏好、习惯、身份背景**等关键信息。

**提取规则：**
1. 只提取**长期有效**的信息（如用户专业、年级、偏好、习惯），不要提取临时性信息（如"今天有什么课"）
2. 如果对话中没有新的长期信息，直接返回空字符串，不要编造
3. 不要重复已有记忆的内容
4. 用简洁的条目式记录，每条一行

**已有记忆：**
${if (existingMemory.isNotBlank()) existingMemory else "（暂无）"}

**输出格式要求：**
- 只返回需要追加的记忆内容，每条一行
- 如果没有新信息，只返回空字符串
- 不要输出任何解释、前缀或后缀
            """.trimIndent()

            val requestJson = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", "请从以下对话中提取需要记忆的信息：\n\n$conversation")
                    })
                })
                put("temperature", 0.3)
                put("max_tokens", 500)
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("提取记忆失败(${response.code})"))
            }

            val jsonResponse = JSONObject(responseBody ?: "{}")
            val choices = jsonResponse.optJSONArray("choices")
            if (choices == null || choices.length() == 0) {
                return@withContext Result.success("")
            }

            val content = choices.getJSONObject(0)
                .optJSONObject("message")
                ?.optString("content", "")
                ?.trim() ?: ""

            // 过滤掉常见的无意义回复
            val filtered = when {
                content.isBlank() -> ""
                content.contains("无新信息") -> ""
                content.contains("没有需要") -> ""
                content.contains("暂无") -> ""
                else -> content
            }

            Log.d(TAG, "提取记忆结果: $filtered")
            Result.success(filtered)

        } catch (e: Exception) {
            Log.e(TAG, "提取记忆异常: ${e.message}", e)
            Result.failure(e)
        }
    }
}

/**
 * Agent响应，包含文本和函数调用
 */
data class AgentResponse(
    val text: String,
    val functionCalls: List<FunctionCallResult>
)

/**
 * 函数调用结果
 */
@kotlinx.serialization.Serializable
data class FunctionCallResult(
    val toolCallId: String,
    val functionName: String,
    val arguments: String,
    val resultContent: String = ""
)

/**
 * 流式输出chunk
 */
sealed class StreamChunk {
    /** 文本增量 */
    data class Text(val content: String) : StreamChunk()
    /** 流结束，附带完整的tool_calls */
    data class Done(val toolCalls: List<FunctionCallResult>) : StreamChunk()
    /** 错误 */
    data class Error(val message: String) : StreamChunk()
}
