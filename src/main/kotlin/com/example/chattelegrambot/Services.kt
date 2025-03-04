package com.example.chattelegrambot

import jakarta.transaction.Transactional
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*
import kotlin.time.Duration as Duration


interface UserService {
    fun findUser(userChatId: Long): Users? //
    fun addUser(user: RegisterUser, chatId: Long) //
    fun findMessagesByUser(userChatId: Long): List<Message>? //
    fun addQueue(chatId: Long) //
    fun addRatingScore(score: Int, chatId: Long)
    fun findConversationByUser(userChatId: Long): Conversation?
    fun deleteMessage(chatId: Long)
    fun findMessageByUser(chatId: Long, message: String): Message?
    fun addConversationToMessage(chatId: Long)
    fun addConversationToMessage(chatId: Long, content: String)
    fun deleteQueue(chatId: Long) ///
    fun addMessage(
        chatId: Long,
        content: String,
        type: String,
        caption: String?,
        messageId: Int,
        senderMessageId: Int?
    ) ///

    fun addConversation(chatId: Long, operator: Operator) ///
    fun addRating(user: Users, operator: Operator, conversation: Conversation) ///
    fun getUserStep(chatId: Long): Status?
    fun setUserStep(chatId: Long, status: Status)
    fun addUser(chatId: Long, status: Status)
    fun addLanguage(chatId: Long, langType: Language)
    fun getUserLanguage(chatId: Long): List<Language>?
    fun deleteUser(chatId: Long)
    fun addSenderMessageId(chatId: Long, content: String, senderMessageId: Int?)
}

interface OperatorService {
    fun addConversation(chatId: Long, user: Users)
    fun addMessage(chatId: Long, content: String, type: String, caption: String?, messageId: Int, senderMessageId: Int)
    fun addOperator(userId: Long, language: List<Language>): Long?
    fun changeStatus(chatId: Long, status: Status)
    fun findOperator(operatorChatId: Long): Operator?
    fun findMessageByOperator(chatId: Long, message: String): Message?
    fun findAvailableOperator(langType: Language): Operator?
    fun startWork(chatId: Long): Users?
    fun startWorkSession(chatId: Long)
    fun finishWork(chatId: Long)
    fun finishConversation(chatId: Long): Long?
    fun findConversationByOperator(chatId: Long): Conversation?
    fun getOperatorStep(chatId: Long): Status?
    fun getOperatorLanguage(chatId: Long): List<Language>?
    fun setOperatorStep(chatId: Long, status: Status)
    fun getBotMessageId(messageId: Long): BotMessage
    fun getMessageByMessageId(messageId: Long): Message
}

@Service
class UserServiceImpl(
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository,
    private val queueRepository: QueueRepository,
    private val conversationRepository: ConversationRepository,
    private val ratingRepository: RatingRepository,
    private val operatorService: OperatorService
) : UserService {
    override fun findUser(userChatId: Long): Users? {
        return userRepository.findUsersByChatId(userChatId)
    }

    override fun addUser(registerUser: RegisterUser, chatId: Long) {
        val user = userRepository.findUsersByChatId(chatId)
        user?.fullName = registerUser.fullName
        user?.phone = registerUser.phoneNumber
        userRepository.save(user!!)
    }

    override fun addUser(chatId: Long, status: Status) {
        userRepository.save(
            Users(status, chatId, null, null, null)
        )
    }

    override fun findMessagesByUser(userChatId: Long): List<Message>? {
        return messageRepository.findMessagesByUser(userChatId)
    }

    override fun addQueue(chatId: Long) {
        userRepository.findUsersByChatId(chatId)?.let {
            queueRepository.existUser(chatId) ?: queueRepository.save(Queue(it, it.langType!!))
        }
    }

    override fun addRatingScore(score: Int, chatId: Long) {
        ratingRepository.findRating(chatId)?.let {
            it.score = score
            ratingRepository.save(it)
        }
    }


    override fun findConversationByUser(userChatId: Long): Conversation? {
        return conversationRepository.findConversationByUser(userChatId)
    }

    @Transactional
    override fun deleteMessage(chatId: Long) {
        messageRepository.deleteMessagesByUser(chatId)
    }

    override fun findMessageByUser(chatId: Long, message: String): Message? {
        return messageRepository.findMessageByUser(chatId, message)
    }

    override fun addConversationToMessage(chatId: Long) {
        conversationRepository.findConversationByUser(chatId)?.let { item ->
            messageRepository.findFirstMessageByUser(chatId)?.let {
                it.conversation = item
                messageRepository.save(it)
            }
        }
    }

    override fun addConversationToMessage(chatId: Long, content: String) {
        conversationRepository.findConversationByUser(chatId)?.let { item ->
            messageRepository.findMessageByUser(chatId, content)?.let {
                it.conversation = item
                messageRepository.save(it)
            }
        }
    }

    @Transactional
    override fun deleteQueue(chatId: Long) {
        userRepository.findUsersByChatId(chatId)?.let {
            queueRepository.deleteUserFromQueue(it.chatId, it.langType!!)
        }
    }

    override fun addMessage(
        chatId: Long,
        content: String,
        type: String,
        caption: String?,
        messageId: Int,
        senderMessageId: Int?
    ) {
        messageRepository.save(
            Message(
                null,
                chatId,
                SenderType.USER,
                content,
                type,
                caption,
                messageId,
                senderMessageId
            )
        )
    }

    override fun addConversation(chatId: Long, operator: Operator) {
        userRepository.findUsersByChatId(chatId)?.let {
            conversationRepository.save(Conversation(it, operator, it.langType!!))
        }
    }

    override fun addRating(user: Users, operator: Operator, conversation: Conversation) {
        ratingRepository.save(Rating(conversation, user, operator))
    }

    override fun getUserStep(chatId: Long): Status? {
        return userRepository.findUsersByChatId(chatId)?.status ?: operatorService.getOperatorStep(chatId)
    }

    override fun setUserStep(chatId: Long, status: Status) {
        val user = userRepository.findUsersByChatId(chatId)
        user?.status = status
        userRepository.save(user!!)

    }

    override fun addLanguage(chatId: Long, langType: Language) {
        userRepository.findUsersByChatId(chatId)?.let {
            it.langType = langType
            userRepository.save(it)
        }
    }

    override fun getUserLanguage(chatId: Long): List<Language>? {
        userRepository.findUsersByChatId(chatId)?.let {
            return listOf(it.langType!!)
        } ?: run {
            return operatorService.getOperatorLanguage(chatId)
        }
    }

    override fun deleteUser(chatId: Long) {
        userRepository.findUsersByChatId(chatId)?.let {
            userRepository.deleteById(it.id!!)
        }
    }

    override fun addSenderMessageId(chatId: Long, content: String, senderMessageId: Int?) {
        messageRepository.findMessageByUser(chatId, content)?.let {
            it.senderMessageId = senderMessageId
            messageRepository.save(it)
        }
    }

}


@Service
class OperatorServiceImpl(
    private val operatorRepository: OperatorRepository,
    private val workSessionRepository: WorkSessionRepository,
    private val queueRepository: QueueRepository,
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    @Lazy private val userService: UserService,
    private val userRepository: UserRepository,
    private val botMessageRepository: BotMessageRepository
) : OperatorService {
    override fun addConversation(chatId: Long, user: Users) {
        operatorRepository.findOperatorByChatId(chatId)?.let {
            conversationRepository.save(Conversation(user, it, user.langType!!))
        }
    }

    override fun addMessage(
        chatId: Long,
        content: String,
        type: String,
        caption: String?,
        messageId: Int,
        senderMessageId: Int
    ) {
        conversationRepository.findConversationByOperator(chatId)?.let { item ->
            messageRepository.save(
                Message(
                    item,
                    chatId,
                    SenderType.OPERATOR,
                    content,
                    type,
                    caption,
                    messageId,
                    senderMessageId
                )
            )
        }
    }

    override fun addOperator(userId: Long, language: List<Language>): Long? {
        var userChatId: Long? = null
        userRepository.findByIdAndDeletedFalse(userId)?.let {
            operatorRepository.save(Operator(it.chatId, it.fullName!!, it.phone!!, language))
            userChatId = it.chatId
            userRepository.trash(userId)
        }
        return userChatId
    }

    @Transactional
    override fun changeStatus(chatId: Long, status: Status) {
        operatorRepository.changeStatus(chatId, status)
    }

    override fun findOperator(operatorChatId: Long): Operator? {
        return operatorRepository.findOperatorByChatId(operatorChatId)
    }

    override fun findMessageByOperator(chatId: Long, message: String): Message? {
        return messageRepository.findMessageByOperator(chatId, message)
    }

    override fun findAvailableOperator(langType: Language): Operator? {
        return operatorRepository.findAvailableOperator(langType.toString())
    }

    override fun startWork(chatId: Long): Users? {
        val langList = mutableListOf<Language>()
        operatorRepository.findOperatorByChatId(chatId)?.let {
            it.status = Status.OPERATOR_ACTIVE
            operatorRepository.save(it)
            for (language in it.language) {
                langList.add(language)
            }
        }
        for (queue in queueRepository.findByDeletedFalseOrderByCreatedDateAsc()) {
            for (lang in langList) {
                if (lang == queue.language) {
                    return queue.users
                }
            }
        }
        return null
    }

    override fun startWorkSession(chatId: Long) {
        operatorRepository.findOperatorByChatId(chatId)?.let {
            workSessionRepository.save(WorkSession(it, null, null, null, null))
        }
    }


    override fun finishWork(chatId: Long) {
        operatorRepository.findOperatorByChatId(chatId)?.let { operator ->
            // Operatorni faolsiz holatga o'tkazish
            operator.status = Status.OPERATOR_INACTIVE
            operatorRepository.save(operator)

            // Ish boshlanish va tugash vaqtlari
            val workSession = workSessionRepository.getTodayWorkSession(chatId)
            val startDate = workSession.createdDate!!.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
            val endDate = LocalDateTime.now()

            // Sekundlar orasidagi farqni hisoblash
            val duration = java.time.Duration.between(startDate, endDate)
            val totalSeconds = duration.seconds
            val totalHours = totalSeconds / 3600 //0
            val remainingSeconds = totalSeconds % 3600//2700
            val totalMinutes = remainingSeconds / 60

            // Ish haqi hisoblash
            val hourlyRate = HOURLY_RATE // Soatlik stavka
            val salary = (totalHours.toBigDecimal() * hourlyRate).toDouble() +
                    ((remainingSeconds.toBigDecimal().toDouble()/BigDecimal(3600).toDouble()) * hourlyRate.toDouble())

            // Sessionni yangilash
            workSession.endDate = java.util.Date.from(endDate.atZone(ZoneId.systemDefault()).toInstant())
            workSession.workHour = totalHours.toInt()
            workSession.workMinute = totalMinutes.toInt()
            workSession.salary = salary

            workSessionRepository.save(workSession)
        }
    }


    @Transactional
    override fun finishConversation(chatId: Long): Long? {
        var userChatId: Long? = null
        operatorRepository.findOperatorByChatId(chatId)?.let { item ->
            conversationRepository.findConversationByOperator(item.chatId)?.let {
                userChatId = it.users.chatId
                userService.addRating(it.users, it.operator, it)
                it.endDate = Date()
                conversationRepository.save(it)
            }
        }
        return userChatId
    }

    override fun findConversationByOperator(chatId: Long): Conversation? {
        return conversationRepository.findConversationByOperator(chatId)
    }

    override fun getOperatorStep(chatId: Long): Status? {
        return operatorRepository.findOperatorByChatId(chatId)?.status ?: return null
    }

    override fun getOperatorLanguage(chatId: Long): List<Language>? {
        return operatorRepository.findOperatorByChatId(chatId)?.language ?: return null
    }

    override fun setOperatorStep(chatId: Long, status: Status) {
        val operator = operatorRepository.findOperatorByChatId(chatId)
        operator?.status = status
        operatorRepository.save(operator!!)
    }

    override fun getBotMessageId(messageId: Long): BotMessage {
        return botMessageRepository.findByMessageId(messageId)
    }

    override fun getMessageByMessageId(messageId: Long): Message {
        return messageRepository.findByMessageIdAndDeletedFalse(messageId)
    }
}


interface OperatorStatisticsService {
    fun getTotalOperators(): Long
    fun findTotalWorkHours(): List<OperatorWorkHoursDto>

    fun findTotalSalary(): List<OperatorSalaryDto>

    fun findAverageRatings(): List<OperatorRatingDto>

    fun findOperatorConversationCounts(): List<OperatorConversationDto>

}

@Service
class OperatorStatisticsServiceImpl(
    private val operatorRepository: OperatorRepository,
    private val workSessionRepository: WorkSessionRepository,
    private val conversationRepository: ConversationRepository,
    private val ratingRepository: RatingRepository
) : OperatorStatisticsService {
    override fun getTotalOperators(): Long {
        return operatorRepository.count()
    }

    override fun findTotalWorkHours(): List<OperatorWorkHoursDto> {
        return workSessionRepository.findTotalWorkHoursRaw().map { row ->
            val operatorName = row[0] as String
            val totalWorkHours = (row[1] as Number).toDouble()
            OperatorWorkHoursDto(operatorName, totalWorkHours)
        }
    }

    override fun findTotalSalary(): List<OperatorSalaryDto> {
        return workSessionRepository.findTotalSalaryRaw().map { row ->
            val operatorName = row[0] as String
            val totalSalary = (row[1] as Double?) ?: 0.0
            OperatorSalaryDto(operatorName, totalSalary)
        }
    }



    override fun findAverageRatings(): List<OperatorRatingDto> {
        return ratingRepository.findAverageRatingsRaw().map { row ->
            val operatorName = row[0] as String
            val averageRating = (row[1] as Number).toDouble()
            OperatorRatingDto(operatorName, averageRating)
        }
    }


    override fun findOperatorConversationCounts(): List<OperatorConversationDto> {
        return conversationRepository.findOperatorConversationCountsRaw().map { row ->
            val operatorName = row[0] as String
            val conversationCount = (row[1] as Number).toLong()
            OperatorConversationDto(operatorName, conversationCount)
        }
    }

}

