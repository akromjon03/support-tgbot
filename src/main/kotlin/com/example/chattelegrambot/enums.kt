package com.example.chattelegrambot

enum class ErrorCodes(val code: Int) {
    USER_NOT_FOUND(100),
}

enum class Language {
    UZ, EN
}


enum class SenderType {
    USER, OPERATOR
}

enum class Status {
    USER_LANGUAGE,
    USER_PHONE,
    USER_FULL_NAME,
    USER_WRITE_MESSAGE,
    USER_CHATTING,
    USER_QUEUE,
    USER_RATING,
    USER_BLOCKED,
    OPERATOR_START_WORK,
    OPERATOR_INACTIVE,
    OPERATOR_ACTIVE,
    OPERATOR_BUSY,
}