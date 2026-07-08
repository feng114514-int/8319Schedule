package com.example.a8319schedule.data

/**
 * 课程颜色调色板 —— 统一定义，避免多处重复
 * 16 色版本，与 Android Material Design 调色板对齐
 */
object CourseColors {

    /** 16 色调色板（Long 类型，兼容 Course.color 字段） */
    val PALETTE: List<Long> = listOf(
        0xFF4CAF50L, // 绿色
        0xFF2196F3L, // 蓝色
        0xFFFF9800L, // 橙色
        0xFF9C27B0L, // 紫色
        0xFFF44336L, // 红色
        0xFF00BCD4L, // 青色
        0xFFE91E63L, // 粉色
        0xFF3F51B5L, // 靛蓝
        0xFF795548L, // 棕色
        0xFF607D8BL, // 蓝灰色
        0xFF8BC34AL, // 浅绿
        0xFF03A9F4L, // 浅蓝
        0xFFFFC107L, // 琥珀
        0xFF9E9E9EL, // 灰色
        0xFFFF5722L, // 深橙
        0xFF009688L  // 蓝绿色
    )

    /** 获取调色板中指定索引的颜色（自动取模循环） */
    fun getColor(index: Int): Long = PALETTE[index % PALETTE.size]
}
