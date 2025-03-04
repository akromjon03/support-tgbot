package com.example.chattelegrambot

import java.math.BigDecimal


private val userRegistrations = mutableMapOf<Long, RegisterUser>()
private val mapFullNameChatIdAndMessageId = mutableMapOf<Long, Int>()
val HOURLY_RATE = BigDecimal("100000.00")


@Synchronized
fun getAllFullNameIdAndMessageIds(chatId: Long) = mapFullNameChatIdAndMessageId[chatId]


@Synchronized
fun putFullNameIdAndMessageId(chatId: Long, messageId: Int) {
    mapFullNameChatIdAndMessageId[chatId] = messageId
}

@Synchronized
fun removeMapFullNameChatIdAndMessageId(chatId: Long) = mapFullNameChatIdAndMessageId.remove(chatId)


@Synchronized
fun getRegistrationData(chatId: Long) = userRegistrations.getOrPut(chatId) { RegisterUser() }

@Synchronized
fun setRegistrationData(chatId: Long, registrationData: RegisterUser) {
    userRegistrations[chatId] = registrationData
}

@Synchronized
fun removeRegistrationData(chatId: Long) {
    userRegistrations.remove(chatId)
}