package com.zhixing.ui

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * 排期日期校验器。纯逻辑，零 Android 依赖。
 *
 * yyyy-MM-dd 格式字典序等同于时间序，直接字符串比较即可。
 * 规则：排期日期不允许早于 today。
 */
fun isValidScheduleDate(date: String, today: String): Boolean {
    return date >= today
}

/**
 * 将"yyyy-MM-dd"视为 UTC 当地日期，返回该日 00:00:00.000 的 epoch 毫秒。
 *
 * Material3 DatePicker 的 selectDateMillis 用的是 UTC 午夜毫秒，
 * 这里对齐同一语义，保证 round-trip 自洽。
 */
fun localMillisAtMidnight(dateStr: String): Long {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    val parts = dateStr.split("-")
    cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt(), 0, 0, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

/**
 * 将 epoch 毫秒按 UTC 格式化为"yyyy-MM-dd"。
 * [localMillisAtMidnight] 的逆运算。
 */
fun millisToDateStrUtc(millis: Long): String {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    cal.timeInMillis = millis
    return "%04d-%02d-%02d".format(
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.DAY_OF_MONTH),
    )
}
