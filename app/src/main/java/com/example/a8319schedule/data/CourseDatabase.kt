package com.example.a8319schedule.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 课程数据库
 */
@Database(
    entities = [Course::class, ScheduleInfo::class, Exam::class, TrainingPlan::class, TrainingPlanAll::class, Score::class],
    version = 16,
    exportSchema = false
)
abstract class CourseDatabase : RoomDatabase() {
    
    abstract fun courseDao(): CourseDao
    abstract fun scheduleInfoDao(): ScheduleInfoDao
    abstract fun examDao(): ExamDao
    abstract fun trainingPlanDao(): TrainingPlanDao
    abstract fun trainingPlanAllDao(): TrainingPlanAllDao
    abstract fun scoreDao(): ScoreDao
    
    companion object {
        @Volatile
        private var instance: CourseDatabase? = null
        
        // 从版本1到版本2的迁移，添加isSpecialWeek和originalCourseId字段
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 添加新字段
                database.execSQL("ALTER TABLE courses ADD COLUMN isSpecialWeek INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE courses ADD COLUMN originalCourseId INTEGER")
            }
        }
        
        // 从版本2到版本3的迁移，移除isSpecialWeek和originalCourseId字段
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 创建临时表，不包含要移除的字段
                database.execSQL("""
                    CREATE TABLE courses_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        teacher TEXT NOT NULL,
                        classroom TEXT NOT NULL,
                        dayOfWeek INTEGER NOT NULL,
                        startWeek INTEGER NOT NULL,
                        endWeek INTEGER NOT NULL,
                        startPeriod INTEGER NOT NULL,
                        endPeriod INTEGER NOT NULL,
                        color INTEGER NOT NULL
                    )
                """.trimIndent())
                
                // 复制数据到新表
                database.execSQL("""
                    INSERT INTO courses_new (id, name, teacher, classroom, dayOfWeek, startWeek, endWeek, startPeriod, endPeriod, color)
                    SELECT id, name, teacher, classroom, dayOfWeek, startWeek, endWeek, startPeriod, endPeriod, color FROM courses
                """.trimIndent())
                
                // 删除旧表
                database.execSQL("DROP TABLE courses")
                
                // 重命名新表
                database.execSQL("ALTER TABLE courses_new RENAME TO courses")
            }
        }
        
        // 从版本3到版本4的迁移，添加courseGroupId字段
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 添加新字段，默认值为空字符串
                database.execSQL("ALTER TABLE courses ADD COLUMN courseGroupId TEXT NOT NULL DEFAULT ''")
                
                // 为现有课程生成courseGroupId
                database.execSQL("""
                    UPDATE courses 
                    SET courseGroupId = name || '_' || COALESCE(teacher, '') || '_' || COALESCE(classroom, '') || '_' || dayOfWeek || '_' || startPeriod
                    WHERE courseGroupId = ''
                """.trimIndent())
            }
        }
        
        // 从版本4到版本5的迁移，添加courseInstanceId字段
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 添加新字段，默认值为空字符串
                database.execSQL("ALTER TABLE courses ADD COLUMN courseInstanceId TEXT NOT NULL DEFAULT ''")
                
                // 为现有课程生成唯一的courseInstanceId，使用随机UUID
                database.execSQL("""
                    UPDATE courses 
                    SET courseInstanceId = lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))),2) || '-' || substr('89ab',abs(random()) % 4 + 1, 1) || substr(lower(hex(randomblob(2))),2) || '-' || lower(hex(randomblob(6)))
                    WHERE courseInstanceId = ''
                """.trimIndent())
            }
        }
        
        // 从版本5到版本6的迁移，添加isOverride字段
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 添加新字段，默认值为0（false）
                database.execSQL("ALTER TABLE courses ADD COLUMN isOverride INTEGER NOT NULL DEFAULT 0")
            }
        }
        
        // 从版本6到版本7的迁移，重构数据模型：将startWeek/endWeek改为weekNumber
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 创建临时表，使用新的结构
                database.execSQL("""
                    CREATE TABLE courses_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        teacher TEXT NOT NULL,
                        classroom TEXT NOT NULL,
                        dayOfWeek INTEGER NOT NULL,
                        weekNumber INTEGER NOT NULL,
                        startPeriod INTEGER NOT NULL,
                        endPeriod INTEGER NOT NULL,
                        color INTEGER NOT NULL,
                        courseGroupId TEXT NOT NULL,
                        courseInstanceId TEXT NOT NULL
                    )
                """.trimIndent())
                
                // 将数据从旧表迁移到新表，为每个周次创建独立的记录
                database.execSQL("""
                    INSERT INTO courses_new 
                    SELECT 
                        id,
                        name,
                        teacher,
                        classroom,
                        dayOfWeek,
                        startWeek as weekNumber,  -- 使用startWeek作为weekNumber
                        startPeriod,
                        endPeriod,
                        color,
                        courseGroupId,
                        courseInstanceId
                    FROM courses
                    WHERE startWeek = endWeek  -- 只迁移单周课程，多周课程需要拆分
                """.trimIndent())
                
                // 删除旧表
                database.execSQL("DROP TABLE courses")
                
                // 重命名新表
                database.execSQL("ALTER TABLE courses_new RENAME TO courses")
            }
        }
        
        // 从版本7到版本8的迁移，添加多课表支持
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 1. 创建课表信息表
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS schedules (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        description TEXT NOT NULL DEFAULT '',
                        isActive INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                
                // 2. 为课程表添加scheduleId字段
                database.execSQL("ALTER TABLE courses ADD COLUMN scheduleId INTEGER NOT NULL DEFAULT 1")
                
                // 3. 创建默认课表
                val currentTime = System.currentTimeMillis()
                database.execSQL(
                    "INSERT INTO schedules (name, description, isActive, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?)",
                    arrayOf<Any>("默认课表", "应用默认课表", 1, currentTime, currentTime)
                )
            }
        }
        
        // 从版本8到版本9的迁移，添加startDate字段到schedules表
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 添加startDate字段，默认值为0
                database.execSQL("ALTER TABLE schedules ADD COLUMN startDate INTEGER NOT NULL DEFAULT 0")
            }
        }
        
        // 从版本9到版本10的迁移，将旧TimetableParser导入的大节编号(1-5)转为小节编号(1-10)
        // 旧数据特征：startPeriod == endPeriod 且值在1-5范围内（一个大节只存一条记录）
        // 新格式：startPeriod和endPeriod使用小节编号，如第1大节→startPeriod=1, endPeriod=2
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 将大节编号转为小节编号：
                // 大节1→startPeriod=1,endPeriod=2; 大节2→3,4; 大节3→5,6; 大节4→7,8; 大节5→9,10
                database.execSQL("""
                    UPDATE courses SET
                        startPeriod = (startPeriod - 1) * 2 + 1,
                        endPeriod = endPeriod * 2
                    WHERE startPeriod = endPeriod AND startPeriod BETWEEN 1 AND 5
                """.trimIndent())
            }
        }
        
        // 从版本10到版本11的迁移，合并QuizDatabase的questions表
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS questions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        subject TEXT NOT NULL,
                        questionText TEXT NOT NULL,
                        optionA TEXT NOT NULL DEFAULT '',
                        optionB TEXT NOT NULL DEFAULT '',
                        optionC TEXT NOT NULL DEFAULT '',
                        optionD TEXT NOT NULL DEFAULT '',
                        correctAnswer TEXT NOT NULL,
                        questionType TEXT NOT NULL DEFAULT 'choice'
                    )
                """.trimIndent())
            }
        }

        // 从版本11到版本12的迁移，新增考试安排表
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS exams (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        scheduleId INTEGER NOT NULL,
                        courseName TEXT NOT NULL,
                        courseCode TEXT NOT NULL,
                        sessionName TEXT NOT NULL,
                        campus TEXT NOT NULL,
                        teacher TEXT NOT NULL,
                        invigilator TEXT NOT NULL,
                        examTimeRaw TEXT NOT NULL,
                        examStartTimestamp INTEGER NOT NULL,
                        examEndTimestamp INTEGER NOT NULL,
                        examRoom TEXT NOT NULL,
                        seatNumber TEXT NOT NULL,
                        admissionTicket TEXT NOT NULL,
                        remark TEXT NOT NULL,
                        xnxqid TEXT NOT NULL
                    )
                """.trimIndent())
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_exams_scheduleId_courseCode_examTimeRaw ON exams(scheduleId, courseCode, examTimeRaw)"
                )
            }
        }

        // 从版本12到版本13的迁移，新增培养方案（执行计划）表
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS training_plans (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        scheduleId INTEGER NOT NULL,
                        seq INTEGER NOT NULL,
                        semester TEXT NOT NULL,
                        courseCode TEXT NOT NULL,
                        courseName TEXT NOT NULL,
                        department TEXT NOT NULL,
                        credit TEXT NOT NULL,
                        totalHours TEXT NOT NULL,
                        assessmentType TEXT NOT NULL,
                        courseNature TEXT NOT NULL,
                        courseAttribute TEXT NOT NULL,
                        isExam TEXT NOT NULL
                    )
                """.trimIndent())
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_training_plans_scheduleId_courseCode ON training_plans(scheduleId, courseCode)"
                )
            }
        }

        // 从版本13到版本14的迁移，新增培养方案（课程设置总表）表
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS training_plans_all (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        scheduleId INTEGER NOT NULL,
                        courseSystem TEXT NOT NULL,
                        courseGroup TEXT NOT NULL,
                        courseCode TEXT NOT NULL,
                        courseName TEXT NOT NULL,
                        completionStatus TEXT NOT NULL,
                        courseNature TEXT NOT NULL,
                        courseAttribute TEXT NOT NULL,
                        credit TEXT NOT NULL,
                        lectureHours TEXT NOT NULL,
                        practiceHours TEXT NOT NULL,
                        experimentHours TEXT NOT NULL,
                        onlineHours TEXT NOT NULL,
                        totalHours TEXT NOT NULL,
                        openSemester TEXT NOT NULL
                    )
                """.trimIndent())
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_training_plans_all_scheduleId_courseCode ON training_plans_all(scheduleId, courseCode)"
                )
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP TABLE IF EXISTS questions")
            }
        }

        // 从版本15到版本16的迁移，新增成绩查询表
        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS scores (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        scheduleId INTEGER NOT NULL,
                        seq INTEGER NOT NULL,
                        semester TEXT NOT NULL,
                        courseCode TEXT NOT NULL,
                        courseName TEXT NOT NULL,
                        score TEXT NOT NULL,
                        scoreFlag TEXT NOT NULL,
                        credit TEXT NOT NULL,
                        totalHours TEXT NOT NULL,
                        gradePoint TEXT NOT NULL,
                        makeupSemester TEXT NOT NULL,
                        assessmentType TEXT NOT NULL,
                        examNature TEXT NOT NULL,
                        courseAttribute TEXT NOT NULL,
                        courseNature TEXT NOT NULL,
                        courseCategory TEXT NOT NULL
                    )
                """.trimIndent())
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_scores_scheduleId_courseCode_semester ON scores(scheduleId, courseCode, semester)"
                )
            }
        }

        fun getDatabase(ctx: Context): CourseDatabase {
            return instance ?: synchronized(this) {
                val inst = Room.databaseBuilder(
                    ctx.applicationContext,
                    CourseDatabase::class.java,
                    "course_db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16)
                .enableMultiInstanceInvalidation()
                .build()
                instance = inst
                inst
            }
        }
    }
}
