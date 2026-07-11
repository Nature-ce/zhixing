package com.zhixing.data

/**
 * AI 拆解失败。
 *
 * 所有失败路径（网络非 2xx / 解析 / 后端返回错误体）统一转这个异常，
 * UI 层通过 [message] 展示给用户的真实原因。
 */
class DecompositionException(message: String) : Exception(message)
