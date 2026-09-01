package com.example.a8319schedule

import com.example.a8319schedule.data.WeekCalendarParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 教学周历解析器单元测试。
 *
 * 重点验证周历的日期显示规则：
 * - 工作日格子通常仅显示"日"（如"31"），不带月份，不能直接使用；
 * - 周末格子显示完整"月日"（如"9月5日"）；
 * - 跨月周（周一 8/31、周六 9/5）必须按列位置回推周一，不能把周末的月份套到周一。
 */
class WeekCalendarParserTest {

    private fun table(vararg rows: String): String =
        "<html><body><table id=\"kbtable\">${rows.joinToString("")}</table></body></html>"

    private fun weekRow(weekNo: String, vararg cells: String): String =
        "<tr><td>$weekNo</td>${cells.joinToString("") { "<td>$it</td>" }}</tr>"

    private val headerRow =
        "<tr><td>周次</td><td>星期一</td><td>星期二</td><td>星期三</td><td>星期四</td>" +
            "<td>星期五</td><td>星期六</td><td>星期日</td></tr>"

    private fun assertNearToday(date: LocalDate) {
        assertTrue(
            "识别结果 $date 应与今天相差不超过 400 天",
            kotlin.math.abs(ChronoUnit.DAYS.between(date, LocalDate.now())) < 400
        )
    }

    @Test
    fun `跨月周 - 工作日仅显示日 周末显示月日 应回推到8月31日`() {
        // 2026 秋季学期第 1 周：周一 8/31（仅显示"31"），周六 9/5、周日 9/6 显示完整月日
        val html = table(
            headerRow,
            weekRow("1", "31", "1", "2", "3", "4", "9月5日", "9月6日"),
            weekRow("2", "7", "8", "9", "10", "11", "12", "9月13日")
        )
        val result = WeekCalendarParser.parse(html)
        assertNotNull(result.firstDay)
        // 关键断言：不能把周末的 9 月当成周一的月份
        assertEquals(8, result.firstDay!!.monthValue)
        assertEquals(31, result.firstDay!!.dayOfMonth)
        assertNearToday(result.firstDay!!)
    }

    @Test
    fun `title 属性带完整日期时应优先采用`() {
        val htmlWithTitle = table(
            headerRow,
            "<tr><td>1</td>" +
                "<td title='2026年08月31'>31</td><td title='2026年09月01'>1</td>" +
                "<td title='2026年09月02'>2</td><td title='2026年09月03'>3</td>" +
                "<td title='2026年09月04'>4</td><td title='2026年09月05'>9月5日</td>" +
                "<td title='2026年09月06'>9月6日</td></tr>"
        )
        val result = WeekCalendarParser.parse(htmlWithTitle)
        assertEquals(LocalDate.of(2026, 8, 31), result.firstDay)
    }

    @Test
    fun `周一格 title 直接采用 不受其他格子脏数据干扰`() {
        // 周一格 title 即开学日期本身；周六/周日格即使是脏数据（与 title 矛盾）也不影响结果
        val html = table(
            headerRow,
            "<tr><td>1</td>" +
                "<td title='2026年08月31'>31</td><td>1</td><td>2</td><td>3</td><td>4</td>" +
                "<td>9月15日</td><td>9月16日</td></tr>"
        )
        assertEquals(LocalDate.of(2026, 8, 31), WeekCalendarParser.parse(html).firstDay)
    }

    @Test
    fun `仅周日一个格子有月日也能识别`() {
        val html = table(
            weekRow("1", "31", "1", "2", "3", "4", "5", "9月6日")
        )
        val result = WeekCalendarParser.parse(html)
        assertNotNull(result.firstDay)
        assertEquals(8, result.firstDay!!.monthValue)
        assertEquals(31, result.firstDay!!.dayOfMonth)
    }

    @Test
    fun `全部格子仅显示日时不应识别`() {
        // 无任何月份线索，必须放弃而不是猜测
        val html = table(weekRow("1", "31", "1", "2", "3", "4", "5", "6"))
        assertNull(WeekCalendarParser.parse(html).firstDay)
    }

    @Test
    fun `周历从第2周开始显示时前移一周`() {
        // 第 2 周周六 9/12 → 该周周一 9/7 → 开学 = 9/7 - 7 天 = 8/31
        val html = table(
            weekRow("2", "7", "8", "9", "10", "11", "9月12日", "9月13日")
        )
        val result = WeekCalendarParser.parse(html)
        assertNotNull(result.firstDay)
        assertEquals(8, result.firstDay!!.monthValue)
        assertEquals(31, result.firstDay!!.dayOfMonth)
    }

    @Test
    fun `春季学期跨月 - 周日跨入下月`() {
        // 周一 2/23，周日 3/1（2 月仅 28 天），周日格子是唯一带月份的线索
        val html = table(
            weekRow("1", "23", "24", "25", "26", "27", "28", "3月1日")
        )
        val result = WeekCalendarParser.parse(html)
        assertNotNull(result.firstDay)
        assertEquals(2, result.firstDay!!.monthValue)
        assertEquals(23, result.firstDay!!.dayOfMonth)
    }

    @Test
    fun `课表 HTML 不被误识别为周历`() {
        // 课表页 kbtable：第一列是节次（"1-2"），格子是课程长文本（含"1-16周"等数字片段）
        val html = table(
            "<tr><td>1-2</td>" +
                "<td><div class=\"kbcontent\">高等数学<br/>1-16周<br/>张三<br/>A101</div></td>" +
                "<td><div class=\"kbcontent\"></div></td><td><div class=\"kbcontent\"></div></td>" +
                "<td><div class=\"kbcontent\"></div></td><td><div class=\"kbcontent\"></div></td>" +
                "<td><div class=\"kbcontent\"></div></td><td><div class=\"kbcontent\"></div></td></tr>"
        )
        assertNull(WeekCalendarParser.parse(html).firstDay)
    }

    @Test
    fun `空内容与无表格返回 null`() {
        assertNull(WeekCalendarParser.parse(null).firstDay)
        assertNull(WeekCalendarParser.parse("").firstDay)
        assertNull(WeekCalendarParser.parse("<html><body>无表格</body></html>").firstDay)
    }

    @Test
    fun `数字分隔符格式也支持`() {
        // 部分系统显示 "09-05" / "09-06" 格式
        val html = table(
            weekRow("1", "31", "1", "2", "3", "4", "09-05", "09-06")
        )
        val result = WeekCalendarParser.parse(html)
        assertNotNull(result.firstDay)
        assertEquals(8, result.firstDay!!.monthValue)
        assertEquals(31, result.firstDay!!.dayOfMonth)
    }
}
