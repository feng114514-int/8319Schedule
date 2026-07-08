package com.example.a8319schedule

import android.app.Application
import android.content.Context


/**
 * 应用生命周期监听器
 * 用于监听应用进入前台和后台，并触发小部件更新
 */
object AppLifecycleListener {
    
    fun initialize(context: Context) {
        // 由于已经在MainActivity中处理了小部件更新，这里不需要额外操作
    }
}