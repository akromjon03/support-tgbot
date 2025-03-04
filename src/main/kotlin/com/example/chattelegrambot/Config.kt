package com.example.chattelegrambot

import org.springframework.context.MessageSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.support.ResourceBundleMessageSource
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession

//@Configuration
//class WebMvcConfig : WebMvcConfigurer {
//    @Bean
//    fun localeResolver() = SessionLocaleResolver().apply { setDefaultLocale(Locale("uz")) }
//
//    @Bean
//    fun errorMessageSource() = ResourceBundleMessageSource().apply {
//        setDefaultEncoding(Charsets.UTF_8.name())
//        setBasename("error")
//    }
//
//
//    @Bean
//    fun messageSource() = ResourceBundleMessageSource().apply {
//        setDefaultEncoding(Charsets.UTF_8.name())
//        setBasename("message")
//    }
//}

@Configuration
class BotConfig(
    private val botHandler: BotHandler
) {
    @Bean
    fun botSession(): DefaultBotSession {
        TelegramBotsApi(DefaultBotSession::class.java).registerBot(botHandler)
        return DefaultBotSession()
    }
}

@Configuration
class MessageSourceConfiguration {
    @Bean
    fun messageSource(): MessageSource {
        val messageSource = ResourceBundleMessageSource()
        messageSource.setBasename("messages")
        messageSource.setDefaultEncoding("UTF-8")
        return messageSource
    }
}