package com.example.a8319schedule.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * AI 聊天上下文管理器
 * 存储最近 N 轮对话历史，重启后恢复上下文
 */
class AIChatMemoryManager(private val context: Context) {

    companion object {
        private const val TAG = "AIChatMemoryManager"
        private const val CONTEXT_FILE_NAME = "ai_chat_context.json"
        private const val MAX_CONTEXT_ROUNDS = 20  // 最多保留 20 轮对话
    }

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val contextFile: File
        get() = File(context.filesDir, CONTEXT_FILE_NAME)

    /**
     * 读取保存的对话上下文
     */
    suspend fun loadContext(): List<ChatMessage> = withContext(Dispatchers.IO) {
        try {
            if (contextFile.exists()) {
                val text = contextFile.readText(Charsets.UTF_8)
                json.decodeFromString<List<ChatMessage>>(text)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "读取上下文失败: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * 保存对话上下文
     */
    suspend fun saveContext(messages: List<ChatMessage>) = withContext(Dispatchers.IO) {
        try {
            // 只保留最近的 MAX_CONTEXT_ROUNDS 条消息
            val trimmed = if (messages.size > MAX_CONTEXT_ROUNDS) {
                messages.takeLast(MAX_CONTEXT_ROUNDS)
            } else {
                messages
            }
            if (trimmed.isEmpty()) {
                deleteContext()
                return@withContext
            }
            contextFile.writeText(json.encodeToString(trimmed), Charsets.UTF_8)
            Log.d(TAG, "上下文已保存，消息数: ${trimmed.size}")
        } catch (e: Exception) {
            Log.e(TAG, "保存上下文失败: ${e.message}", e)
        }
    }

    /**
     * 删除上下文文件
     */
    suspend fun deleteContext(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (contextFile.exists()) {
                contextFile.delete()
            } else {
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "删除上下文失败: ${e.message}", e)
            false
        }
    }

    /**
     * 检查是否有保存的上下文
     */
    fun hasContext(): Boolean = contextFile.exists() && contextFile.length() > 0
}
