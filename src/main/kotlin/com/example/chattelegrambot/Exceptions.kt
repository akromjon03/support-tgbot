package com.example.chattelegrambot

import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.context.support.ResourceBundleMessageSource

sealed class DemoExceptionHandler : RuntimeException() {
    abstract fun errorCode(): ErrorCodes
    open fun getAllArguments(): Array<Any?>? = null

    fun getErrorMessage(resourceBundle: ResourceBundleMessageSource): BaseMessage {
        val message = try {
            resourceBundle.getMessage(  // USER_NOT_FOUND
                errorCode().name, getAllArguments(), LocaleContextHolder.getLocale()
            )
        } catch (e: Exception) {
            e.message
        }
        return BaseMessage(errorCode().code, message)
    }
}
sealed class BaseMessageSource {

    abstract fun messageCode(): String

    open fun getMessageArguments(): Array<Any?>? = null

    fun getMessage(messageSource: ResourceBundleMessageSource): String {
        return try {
            messageSource.getMessage(messageCode(), getMessageArguments(), LocaleContextHolder.getLocale())
        } catch (e: Exception) {
            e.message ?: "Xabar topilmadi"
        }
    }
}

