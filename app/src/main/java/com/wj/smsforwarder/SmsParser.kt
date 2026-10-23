package com.wj.smsforwarder

/**
 * 识别"验证码类"短信，并尽量提取出验证码本身。
 */
object SmsParser {

    private val KEYWORDS = Regex(
        "(验证码|校验码|动态码|动态密码|确认码|校验代码|verification\\s*code|verify\\s*code|security\\s*code|passcode|otp)",
        RegexOption.IGNORE_CASE
    )

    // 验证码关键词后面不远处出现的 4~8 位数字
    private val CODE_AFTER_KEYWORD = Regex(
        "(?:验证码|校验码|动态码|动态密码|确认码|code|passcode|otp)[^0-9]{0,20}([0-9]{4,8})",
        RegexOption.IGNORE_CASE
    )

    // Google 风格 G-123456
    private val GOOGLE_CODE = Regex("G-([0-9]{6})")

    // 独立的 4~8 位数字（前后都不是数字）
    private val STANDALONE = Regex("(?<![0-9])[0-9]{4,8}(?![0-9])")

    fun isVerification(body: String): Boolean =
        KEYWORDS.containsMatchIn(body) || GOOGLE_CODE.containsMatchIn(body)

    fun extractCode(body: String): String? {
        GOOGLE_CODE.find(body)?.let { return it.groupValues[1] }
        CODE_AFTER_KEYWORD.find(body)?.let { return it.groupValues[1] }
        // 兜底：优先取 6 位/4 位的独立数字（绝大多数验证码是 4 或 6 位）
        return STANDALONE.findAll(body)
            .firstOrNull { it.value.length == 6 || it.value.length == 4 }?.value
            ?: STANDALONE.find(body)?.value
    }
}
