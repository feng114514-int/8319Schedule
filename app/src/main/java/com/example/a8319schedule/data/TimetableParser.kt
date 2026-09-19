package com.example.a8319schedule.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

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

    // 单元格内多段课程的分隔线：----------------------
    private val REGEX_BLOCK_SEPARATOR = Regex("-{5,}")
    // 节次：[01-02节]
    private val REGEX_SECTION = Regex("\\[\\s*(\\d+)\\s*-\\s*(\\d+)\\s*节\\s*]")
    // 周次表达式主体："1,3,5,7,9-16(周)"、"2-16(周)"、"第1-16周"、"1~8周"
    private val REGEX_WEEK_TOKEN =
        Regex("([0-9]{1,2}(?:\\s*[,，、]\\s*[0-9]{1,2})*(?:\\s*[-~－—]\\s*[0-9]{1,2})?)\\s*[（(]?\\s*周")
    // 纯周次表达式（不含"周"字），如 "1,3,5,7,9-16"
    private val REGEX_PURE_WEEK_EXPR = Regex("[0-9]{1,2}(?:\\s*[,，、]\\s*[0-9]{1,2})*(?:\\s*[-~－—]\\s*[0-9]{1,2})?")

    /**
     * 解析教务系统的周次表达式，返回去重升序的周次列表（保留单双周）。
     * 例："1,3,5,7,9-16(周)" -> [1,3,5,7,9,10,11,12,13,14,15,16]
     *    "2,4,6,8(周)"      -> [2,4,6,8]
     * 解析不出时返回空列表，由调用方决定兜底策略。
     */
    fun parseWeekExpression(raw: String): List<Int> {
        if (raw.isBlank()) return emptyList()
        val body = REGEX_WEEK_TOKEN.find(raw)?.groupValues?.get(1)
            ?: raw.trim().takeIf { REGEX_PURE_WEEK_EXPR.matchEntire(it) != null }
            ?: return emptyList()

        val weeks = LinkedHashSet<Int>()
        for (part in body.split(',', '，', '、')) {
            val p = part.trim()
            if (p.isEmpty()) continue
            val seg = p.split('-', '~', '－', '—', limit = 2)
            val start = seg[0].trim().toIntOrNull() ?: continue
            val end = if (seg.size == 2) (seg[1].trim().toIntOrNull() ?: start) else start
            if (start !in 1..30 || end !in 1..30) continue
            if (start <= end) {
                for (w in start..end) weeks.add(w)
            } else {
                weeks.add(start)
            }
        }
        return weeks.sorted()
    }
    
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
            // 第 0 列是节次/时间列（th 或 td），其后依次是周一~周日
            val cells = row.select("th, td")
            if (cells.isEmpty()) continue

            for (cellIndex in 1 until cells.size) {
                val cell = cells[cellIndex]
                val dayOfWeek = cellIndex
                if (dayOfWeek > 7) break

                // 同一单元格内可能同时存在简略版(kbcontent1)与详细版(kbcontent)两个 div，
                // 内容重复，只取其一；优先详细版（含教师、节次）
                val div = cell.select("div.kbcontent").firstOrNull { !it.hasClass("kbcontent1") }
                    ?: cell.selectFirst("div.kbcontent1")
                    ?: continue

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
        
        return courses
    }
    
    private fun parseCourseFromDiv(div: Element, dayOfWeek: Int, periodIndex: Int): List<Course> {
        val blocks = splitCourseBlocks(div)
        val courses = mutableListOf<Course>()
        blocks.forEachIndexed { blockIndex, block ->
            courses.addAll(buildCourses(block, dayOfWeek, periodIndex, blockIndex))
        }
        return courses
    }

    /**
     * 一个单元格内可能存在多段课程（不同周次/教室），以 ---------------------- 分隔。
     * 必须拆分后逐段解析，否则单双周会被合并成连续周次。
     */
    private fun splitCourseBlocks(div: Element): List<Element> {
        val html = div.html()
        if (!REGEX_BLOCK_SEPARATOR.containsMatchIn(html)) return listOf(div)

        val blocks = REGEX_BLOCK_SEPARATOR.split(html)
            .map { Jsoup.parseBodyFragment(it).body() }
            .filter { it.text().replace('\u00A0', ' ').isNotBlank() }
        return blocks.ifEmpty { listOf(div) }
    }

    private fun buildCourses(
        block: Element,
        dayOfWeek: Int,
        periodIndex: Int,
        blockIndex: Int
    ): List<Course> {
        val courseName = extractCourseName(block) ?: return emptyList()

        val teacher = extractTeacher(block)
        val classroom = extractClassroom(block)
        val weeks = extractWeeks(block.text())

        // 节次：优先使用 "[01-02节]" 这类精确节次，否则按大节换算
        // 大节1→小节1-2, 大节2→小节3-4, 大节3→小节5-6, 大节4→小节7-8, 大节5→小节9-10
        val section = REGEX_SECTION.find(block.text())
        val startPeriod = section?.groupValues?.get(1)?.toIntOrNull() ?: ((periodIndex - 1) * 2 + 1)
        val endPeriod = section?.groupValues?.get(2)?.toIntOrNull() ?: (periodIndex * 2)

        // 课程组ID：加入块索引，避免同一单元格内多段课程互相覆盖
        val courseGroupId = "${courseName}_${teacher}_${classroom}_${dayOfWeek}_${periodIndex}_$blockIndex"

        // 为每个周次创建独立的课程记录（周次不连续时只在不连续的周生成，如单双周）
        return weeks.map { week ->
            Course(
                name = courseName,
                teacher = teacher,
                classroom = classroom,
                dayOfWeek = dayOfWeek,
                weekNumber = week,
                startPeriod = startPeriod,
                endPeriod = endPeriod,
                color = 0xFF4CAF50,
                courseGroupId = courseGroupId,
                courseInstanceId = "${courseGroupId}_$week"
            )
        }
    }
    
    private fun extractCourseName(div: Element): String? {
        // 课程名通常是单元格内紧跟 <br> 之前的纯文本节点
        val ownText = div.ownText().replace('\u00A0', ' ').trim()
        if (ownText.isNotBlank() && !ownText.startsWith("周") &&
            ownText.trim { it == '-' }.isNotBlank()
        ) {
            return ownText
        }

        // 兜底：课程名被包在 font/a 里时，取第一个不像周次/教师/教室的文本
        for (font in div.select("font")) {
            val title = font.attr("title")
            if (title == "老师" || title == "教师" || title == "教室" || title == "地点") continue
            val text = font.text().replace('\u00A0', ' ').trim()
            if (text.isBlank()) continue
            if (text.startsWith("周") || text.contains("(周)") || text.contains("节]")) continue
            if (text.startsWith("(") && text.endsWith(")")) continue
            if (text.matches(REGEX_COURSE_CODE)) continue
            return text
        }

        val link = div.selectFirst("a")?.text()?.trim()
        if (!link.isNullOrEmpty()) return link

        return null
    }
    
    private fun extractTeacher(div: Element): String {
        // 教务系统在 font 上标注了 title="老师"/"教师"，优先按标注取，避免靠字数猜测
        for (key in listOf("老师", "教师")) {
            val value = div.selectFirst("font[title='$key']")?.text()
                ?.replace('\u00A0', ' ')?.trim()
            if (!value.isNullOrEmpty()) return value
        }

        for (font in div.select("font")) {
            val title = font.attr("title")
            if (title == "教室" || title == "地点") continue
            val text = font.text().trim()
            if (text.startsWith("(") && text.endsWith(")")) continue
            if (text.contains("(周)") || text.contains("节]")) continue
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
        // 教务系统在 font 上标注了 title="教室"/"地点"，优先按标注取
        for (key in listOf("教室", "地点")) {
            val value = div.selectFirst("font[title='$key']")?.text()
                ?.replace('\u00A0', ' ')?.trim()
            if (!value.isNullOrEmpty()) return value
        }

        for (font in div.select("font")) {
            val title = font.attr("title")
            if (title == "老师" || title == "教师") continue
            val text = font.text().trim()
            if (text.startsWith("(") && text.endsWith(")")) continue
            if (text.contains("(周)") || text.contains("节]")) continue
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
    
    /**
     * 提取周次列表，保留单双周等不连续周次。
     * 例："大学英语(三) 1,3,5,7,9-16(周) 2306" -> [1,3,5,7,9,10,11,12,13,14,15,16]
     */
    private fun extractWeeks(text: String): List<Int> {
        val weeks = parseWeekExpression(text)
        if (weeks.isNotEmpty()) return weeks

        val rangeMatch = REGEX_WEEK_RANGE.find(text) ?: REGEX_WEEK_RANGE_PREFIXED.find(text)
        if (rangeMatch != null) {
            val start = rangeMatch.groupValues[1].toIntOrNull()
            val end = rangeMatch.groupValues[2].toIntOrNull()
            if (start != null && end != null && start <= end) {
                return (start..end).toList()
            }
        }

        val single = REGEX_SINGLE_WEEK.find(text)?.groupValues?.get(1)?.toIntOrNull()
        if (single != null) return listOf(single)

        // 默认1-16周
        return (1..16).toList()
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

                // 周次表达式优先（保留单双周），否则回退到开始周/结束周区间
                val weekExpr = data["周次"] ?: ""
                val weekList = if (weekExpr.isNotBlank()) {
                    parseWeekExpression(weekExpr).ifEmpty { (startWeek..endWeek).toList() }
                } else {
                    (startWeek..endWeek).toList()
                }
                
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
                    
                    // 为每个周次创建独立的课程记录（周次不连续时只在不连续的周生成）
                    for (week in weekList) {
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
                            courseInstanceId = "${courseGroupId}_$week"
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
