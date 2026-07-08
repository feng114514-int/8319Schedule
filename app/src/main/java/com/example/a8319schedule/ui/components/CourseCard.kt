package com.example.a8319schedule.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.a8319schedule.data.Course

/**
 * 在英文单词的字符之间插入零宽空格（ZWSP），允许单词中间换行。
 * 中文不受影响（中文字符本身就是合法断行点）。
 */
private fun String.allowMidWordBreak(): String {
    val result = StringBuilder()
    var prevIsEnglish = false
    for (char in this) {
        val isEnglish = char in 'a'..'z' || char in 'A'..'Z'
        if (prevIsEnglish && isEnglish) {
            result.append('\u200B')
        }
        result.append(char)
        prevIsEnglish = isEnglish
    }
    return result.toString()
}

@Composable
fun CourseCard(
    course: Course,
    modifier: Modifier = Modifier,
    onClick: (Course) -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(2.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(course.color))
            .clickable { onClick(course) }
            .padding(4.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            Text(
                text = course.name.allowMidWordBreak(),
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                minLines = 3,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (course.classroom.isNotEmpty() || course.teacher.isNotEmpty()) {
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    if (course.classroom.isNotEmpty()) {
                        Text(
                            text = course.classroom.allowMidWordBreak(),
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (course.teacher.isNotEmpty()) {
                        Text(
                            text = course.teacher.allowMidWordBreak(),
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
