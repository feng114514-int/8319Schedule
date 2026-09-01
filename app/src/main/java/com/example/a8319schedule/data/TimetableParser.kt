package com.example.a8319schedule.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.UUID

object TimetableParser {

    private val courseColors = CourseColors.PALETTE

    // 预编译正则，避免在循环中重复编译
    private val REGEX_COURSE_CODE = Regex(".*[A-Z]\\d{3,}.*")
    private val REGEX_CHINESE_NAME_2_4 = Regex("^[\\u4e00-\\u9fa5]{2,4}$")
    private val REGEX_TEACHER_IN_TITLE = Regex("(教师|老师)[：:]?\\s*([\\u4e00-\\u9fa5]+)")
    private val REGEX_ROOM_NUMBER = Regex(".*[A-Za-z]?\\d{2,}.*")
    private val REGEX_CHINESE_NAME_2_10 = Regex("^[\\u4e00-\\u9fa5]{2,10}$")
    private val REGEX_ROOM_IN_TITLE = Regex("(教室|地点)[：:]?\\s*(.+)")
    private val REGEX_WEEK_RANGE = Regex("(\\d+)\\s*[-~]\\s*(\\d+)\\s*周")
    private val REGEX_WEEK_RANGE_PREFIXED = Regex("第\\s*(\\d+)\\s*[-~]\\s*(\\d+)\\s*周")
    private val REGEX_SINGLE_WEEK = Regex("(?:第)?(\\d+)\\s*周")
    
    data class ParseResult(
        val courses: List<Course>,
        val success: Boolean,
        val message: String,
        val semesterInfo: String = ""
    )
    
    fun parse(html: String): ParseResult {
        return try {
            val doc = Jsoup.parse(html)
            val kbTable = doc.getElementById("kbtable")
            
            if (kbTable == null) {
                return ParseResult(
                    courses = emptyList(),
                    success = false,
                    message = "未找到课表表格，请确保页面包含完整的课表信息"
                )
            }

            // 防御：教学周历页与课表页的表格 ID 同为 kbtable，但周历无课程内容 div；
            // 避免误将周历 HTML 当课表文件导入后解析出空课表
            if (kbTable.selectFirst("div.kbcontent, div.kbcontent1") == null) {
                return ParseResult(
                    courses = emptyList(),
                    success = false,
                    message = "未识别为课表页面（所选文件可能是教学周历），请选择\"学期理论课表\"页面导出的 HTML"
                )
            }
            
            val semesterInfo = extractSemesterInfo(doc)
            val courses = parseCoursesFromTable(kbTable)
            
            if (courses.isEmpty()) {
                ParseResult(
                    courses = emptyList(),
                    success = false,
                    message = "未能解析出课程信息，请检查页面格式是否正确"
                )
            } else {
                ParseResult(
                    courses = courses,
                    success = true,
                    message = "成功导入 ${courses.size} 门课程",
                    semesterInfo = semesterInfo
                )
            }
        } catch (e: Exception) {
            ParseResult(
                courses = emptyList(),
                success = false,
                message = "解析失败: ${e.message}"
            )
        }
    }
    
    private fun extractSemesterInfo(doc: Document): String {
        return try {
            val title = doc.title()
            if (title.contains("学年") || title.contains("学期")) {
                title
            } else {
                val yearInput = doc.select("input[name=xnm]").first()
                val semesterInput = doc.select("input[name=xqm]").first()
                if (yearInput != null && semesterInput != null) {
                    val year = yearInput.attr("value")
                    val semester = when (semesterInput.attr("value")) {
                        "1" -> "第一学期"
                        "2" -> "第二学期"
                        "3" -> "第三学期"
                        else -> "未知学期"
                    }
                    "$year 学年 $semester"
                } else {
                    ""
                }
            }
        } catch (e: Exception) {
            ""
        }
    }
    
    private fun parseCoursesFromTable(table: Element): List<Course> {
        val courses = mutableListOf<Course>()
        val colorMap = mutableMapOf<String, Long>()
        var colorIndex = 0
        
        val rows = table.select("tr")
        
        for (rowIndex in 1 until rows.size) {
            val row = rows[rowIndex]
            val cells = row.select("td")
            
            for (cellIndex in 1 until cells.size) {
                val cell = cells[cellIndex]
                val dayOfWeek = cellIndex
                
                val divs = cell.select("div.kbcontent, div.kbcontent1")
                
                for (div in divs) {
                    val weekCourses = parseCourseFromDiv(div, dayOfWeek, rowIndex)
                    for (course in weekCourses) {
                        val color = colorMap.getOrPut(course.name) {
                            val c = courseColors[colorIndex % courseColors.size]
                            colorIndex++
                            c
                        }
                        courses.add(course.copy(color = color))
                    }
                }
            }
        }
        
        return courses
    }
    
    private fun parseCourseFromDiv(div: Element, dayOfWeek: Int, periodIndex: Int): List<Course> {
        val courseName = extractCourseName(div)
        if (courseName.isNullOrEmpty()) return emptyList()
        
        val teacher = extractTeacher(div)
        val classroom = extractClassroom(div)
        val weekRange = extractWeekRange(div)
        
        // 将大节编号(1-5)转换为小节编号(1-10)，与AI/手动添加的课程数据格式保持一致
        // 大节1→小节1-2, 大节2→小节3-4, 大节3→小节5-6, 大节4→小节7-8, 大节5→小节9-10
        val startPeriod = (periodIndex - 1) * 2 + 1
        val endPeriod = periodIndex * 2
        
        // 创建课程组ID，基于课程名称、教师、教室、星期几和节次
        val courseGroupId = "${courseName}_${teacher}_${classroom}_${dayOfWeek}_${periodIndex}"
        
        // 为每个周次创建独立的课程记录
        val courses = mutableListOf<Course>()
        for (week in weekRange.first..weekRange.second) {
            val courseInstanceId = UUID.randomUUID().toString()
            courses.add(Course(
                name = courseName,
                teacher = teacher,
                classroom = classroom,
                dayOfWeek = dayOfWeek,
                weekNumber = week,
                startPeriod = startPeriod,
                endPeriod = endPeriod,
                color = 0xFF4CAF50,
                courseGroupId = courseGroupId,
                courseInstanceId = courseInstanceId
            ))
        }
        
        return courses
    }
    
    private fun extractCourseName(div: Element): String? {
        val firstFont = div.select("font").first()
        if (firstFont != null) {
            val text = firstFont.text().trim()
            if (!text.startsWith("周") && !text.contains("节") &&
                !text.matches(REGEX_COURSE_CODE)) {
                return text
            }
        }
        
        val ownText = div.ownText().trim()
        if (ownText.isNotEmpty() && !ownText.startsWith("周")) {
            return ownText
        }
        
        val link = div.select("a").first()
        if (link != null) {
            return link.text().trim()
        }
        
        return null
    }
    
    private fun extractTeacher(div: Element): String {
        val fonts = div.select("font")
        for (font in fonts) {
            val text = font.text().trim()
            // 老师通常是2-4个汉字
            if (text.matches(REGEX_CHINESE_NAME_2_4)) {
                return text
            }
            // 也可能是"老师名"这种格式
            if (text.contains("老师") || text.contains("教师")) {
                return text.replace("老师", "").replace("教师", "").trim()
            }
        }
        
        val title = div.attr("title")
        if (title.contains("教师") || title.contains("老师")) {
            val match = REGEX_TEACHER_IN_TITLE.find(title)
            if (match != null) {
                return match.groupValues[2]
            }
        }
        
        return ""
    }
    
    private fun extractClassroom(div: Element): String {
        val fonts = div.select("font")
        for (font in fonts) {
            val text = font.text().trim()
            // 教室通常包含数字和字母，如 "A101", "教学楼B203"
            if (text.matches(REGEX_ROOM_NUMBER) ||
                text.contains("楼") || text.contains("教室") || text.contains("实验室")) {
                // 确保不是课程名
                if (!text.matches(REGEX_CHINESE_NAME_2_10)) {
                    return text
                }
            }
        }
        
        val title = div.attr("title")
        if (title.contains("教室") || title.contains("地点")) {
            val match = REGEX_ROOM_IN_TITLE.find(title)
            if (match != null) {
                return match.groupValues[2].trim()
            }
        }
        
        return ""
    }
    
    private fun extractWeekRange(div: Element): Pair<Int, Int> {
        val text = div.text()
        
        val rangeMatch = REGEX_WEEK_RANGE.find(text)
        if (rangeMatch != null) {
            val start = rangeMatch.groupValues[1].toIntOrNull() ?: 1
            val end = rangeMatch.groupValues[2].toIntOrNull() ?: 16
            return Pair(start, end)
        }

        val match2 = REGEX_WEEK_RANGE_PREFIXED.find(text)
        if (match2 != null) {
            val start = match2.groupValues[1].toIntOrNull() ?: 1
            val end = match2.groupValues[2].toIntOrNull() ?: 16
            return Pair(start, end)
        }

        val single = REGEX_SINGLE_WEEK.find(text)
        if (single != null) {
            val week = single.groupValues[1].toIntOrNull() ?: 1
            return Pair(week, week)
        }
        
        // 默认1-16周
        return Pair(1, 16)
    }
    
    fun parsePlainText(text: String): ParseResult {
        return try {
            if (text.startsWith("错误:")) {
                return ParseResult(
                    courses = emptyList(),
                    success = false,
                    message = text.substringAfter("错误: ")
                )
            }
            
            val courses = mutableListOf<Course>()
            val colorMap = mutableMapOf<String, Long>()
            var colorIndex = 0
            
            // 按---分割课程
            val blocks = text.split("---").filter { it.trim().isNotEmpty() }
            
            for (block in blocks) {
                val lines = block.trim().split("\n").filter { it.trim().isNotEmpty() }
                val data = mutableMapOf<String, String>()
                
                for (line in lines) {
                    val parts = line.split(":", limit = 2)
                    if (parts.size == 2) {
                        data[parts[0].trim()] = parts[1].trim()
                    }
                }
                
                val name = data["课程"] ?: ""
                val teacher = data["教师"] ?: ""
                val classroom = data["地点"] ?: ""
                val dayName = data["星期"] ?: ""
                val period = data["节次"]?.toIntOrNull() ?: 0
                val startWeek = data["开始周"]?.toIntOrNull() ?: 1
                val endWeek = data["结束周"]?.toIntOrNull() ?: 16
                val startPeriod = data["开始节"]?.toIntOrNull() ?: period
                val endPeriod = data["结束节"]?.toIntOrNull() ?: period
                
                if (name.isNotEmpty()) {
                    val dayOfWeek = when (dayName) {
                        "星期一" -> 1
                        "星期二" -> 2
                        "星期三" -> 3
                        "星期四" -> 4
                        "星期五" -> 5
                        "星期六" -> 6
                        "星期日", "星期天" -> 7
                        else -> 1
                    }
                    
                    val color = colorMap.getOrPut(name) {
                        val c = courseColors[colorIndex % courseColors.size]
                        colorIndex++
                        c
                    }
                    
                    // 创建课程组ID，基于课程名称、教师、教室、星期几和节次
                    val courseGroupId = "${name}_${teacher}_${classroom}_${dayOfWeek}_${startPeriod}"
                    
                    // 为每个周次创建独立的课程记录
                    for (week in startWeek..endWeek) {
                        // 创建唯一的课程实例ID
                        val courseInstanceId = UUID.randomUUID().toString()
                        
                        courses.add(Course(
                            name = name,
                            teacher = teacher,
                            classroom = classroom,
                            dayOfWeek = dayOfWeek,
                            weekNumber = week,  // 单周
                            startPeriod = startPeriod,
                            endPeriod = endPeriod,
                            color = color,
                            courseGroupId = courseGroupId,
                            courseInstanceId = courseInstanceId
                        ))
                    }
                }
            }
            
            if (courses.isEmpty()) {
                ParseResult(
                    courses = emptyList(),
                    success = false,
                    message = "未能解析出课程信息"
                )
            } else {
                ParseResult(
                    courses = courses,
                    success = true,
                    message = "成功导入 ${courses.size} 门课程"
                )
            }
        } catch (e: Exception) {
            ParseResult(
                courses = emptyList(),
                success = false,
                message = "解析失败: ${e.message}"
            )
        }
    }
}
