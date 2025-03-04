package com.example.chattelegrambot

import java.math.BigDecimal


data class BaseMessage(val code: Int, val message: String?)


data class RegisterUser(
    var fullName: String? = null,
    var phoneNumber: String? = null,
)

data class OperatorWorkHoursDto(
    val operatorName: String,
    val totalWorkHours: Double
)

data class OperatorSalaryDto(
    val operatorName: String,
    val totalSalary: Double?
)

data class OperatorRatingDto(
    val operatorName: String,
    val averageRating: Double
)

data class OperatorConversationDto(
    val operatorName: String,
    val conversationCount: Long
)


data class RegisterOperator(
    val id: Long,
    val langType: List<Language>,
)

data class AdminCount(
    val adminCount:Long
)