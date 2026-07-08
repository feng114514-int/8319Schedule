# ============================================
# 基础配置
# ============================================

-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ============================================
# 1. Android 组件（AndroidManifest.xml 引用，不能混淆类名）
# ============================================

-keep class com.example.a8319schedule.MainActivity { *; }
-keep class com.example.a8319schedule.ScheduleWidgetProvider { *; }
-keep class com.example.a8319schedule.ScheduleWidgetProviderSmall { *; }
-keep class com.example.a8319schedule.ScheduleWidgetProviderLarge { *; }
-keep class com.example.a8319schedule.WidgetRefreshService { *; }
-keep class com.example.a8319schedule.SimpleWidgetUpdater$WidgetUpdateReceiver { *; }
-keep class com.example.a8319schedule.CourseAlarmReceiver { *; }
-keep class com.example.a8319schedule.SimpleWidgetUpdater$WidgetRefreshWorker { *; }

# ============================================
# 2. Room 数据库（Entity 字段名 = 数据库列名，不能混淆）
# ============================================

-keep class com.example.a8319schedule.data.Course { *; }
-keep class com.example.a8319schedule.data.ScheduleInfo { *; }
-keep class com.example.a8319schedule.data.Score { *; }
-keep class com.example.a8319schedule.data.CourseDatabase { *; }

-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }

# ============================================
# 3. kotlinx.serialization（@Serializable 类的字段名 = JSON 键名）
# ============================================

-keep class com.example.a8319schedule.data.ScheduleShareCodec$ShareableSchedule { *; }
-keep class com.example.a8319schedule.data.ScheduleShareCodec$ShareableCourse { *; }
-keep class com.example.a8319schedule.data.ScheduleShareCodec$ShareableCourseV1 { *; }
-keep class com.example.a8319schedule.data.ScheduleShareCodec$ShareableScheduleV1 { *; }
-keep class com.example.a8319schedule.data.ChatMessage { *; }
-keep class com.example.a8319schedule.data.FunctionCallResult { *; }

-keep class * implements kotlinx.serialization.KSerializer { *; }
-keepclassmembers class * implements kotlinx.serialization.KSerializer {
    <init>(...);
}

# ============================================
# 4. Gson 反序列化（反射读取字段名）
# ============================================

-keep class * extends com.google.gson.reflect.TypeToken
-keep class com.google.gson.reflect.TypeToken { *; }
-keepclassmembers class * extends com.google.gson.reflect.TypeToken {
    <init>(...);
}

# ============================================
# 5. WebView JavaScript 接口
# ============================================

-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ============================================
# 6. @Keep 注解
# ============================================

-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# ============================================
# 7. 枚举类
# ============================================

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ============================================
# 8. Compose 相关
# ============================================

-dontwarn androidx.compose.**
-keep class kotlin.Metadata { *; }
-keep class kotlinx.metadata.** { *; }

# ============================================
# 9. 移除日志（Release 版本不输出 Log.v/d/i）
# ============================================

-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}

# ============================================
# 10. 第三方库 dontwarn
# ============================================

-dontwarn org.jspecify.**

# Google Error Prone 注解：仅编译期使用，运行时不需要
# 由 com.google.crypto.tink 等库引用，R8 混淆时缺失类会报错
-dontwarn com.google.errorprone.annotations.**
