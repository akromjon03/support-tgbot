package com.example.chattelegrambot

import org.springframework.context.MessageSource
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.*
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageMedia
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.media.InputMediaAudio
import org.telegram.telegrambots.meta.api.objects.media.InputMediaDocument
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto
import org.telegram.telegrambots.meta.api.objects.media.InputMediaVideo
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import java.util.*


@Component
class BotHandler(
    @Lazy private val botHandlerForMessages: BotHandlerForMessages,
    private val botHandlerForReplyMarkUp: BotHandlerForReplyMarkUp,
    private val userService: UserService,
    private val messageSource: MessageSource,
    private val operatorService: OperatorService,
    private val queueRepository: QueueRepository,
    private val messageRepository: MessageRepository
) : TelegramLongPollingBot() {
    override fun getBotUsername(): String {
        return "@chat_telegram_1_0_bot"
    }

    override fun getBotToken(): String {
        return "7923535042:AAHgoQ0uf1h3zxHnidCSWBz7iszFtIbeKRA"
    }

    override fun onUpdateReceived(update: Update?) {
        if (update != null && update.hasMessage() && update.message.hasContact()) {
            val contact = update.message.contact
            val phoneNumber = contact.phoneNumber
            val chatId = update.message.chatId

            if (contact.userId != chatId) {
                sendResponse(chatId, "contact.error")
            } else {
                // Full name va xabar identifikatorlarini o‘chirish
                val messageId = getAllFullNameIdAndMessageIds(chatId)
                if (messageId != null) {
                    deleteCallBack(chatId, messageId) // Callback o‘chiriladi
                    removeMapFullNameChatIdAndMessageId(chatId)// Mapdan olib tashlanadi
                }

                // Callback o‘chirish
                deleteCallBack(chatId, update.message.messageId)

                // Foydalanuvchi bosqichini tekshirish
                when (userService.getUserStep(chatId)) {
                    Status.USER_PHONE -> {
                        val userRegisterUser: RegisterUser = getRegistrationData(chatId)
                        userRegisterUser.phoneNumber = phoneNumber
                        setRegistrationData(chatId, userRegisterUser) // Royxatga olish malumotlari saqlanadi
                        userService.addUser(userRegisterUser, chatId)
                        removeRegistrationData(chatId) // Royxatga olish malumotlarini ochirish
                        sendResponse(
                            chatId,
                            "write.question"
                        )
                        userService.setUserStep(chatId, Status.USER_WRITE_MESSAGE) // Foydalanuvchi bosqichi yangilanadi
                    }

                    else -> ""
                }
            }
        } else if (update != null && update.hasMessage()) {
            val chatId = update.message.chatId
            val message = update.message
            val mId = update.message.messageId
            val text = update.message.text
            if (text != null && text.equals("/changelanguage")
                && userService.getUserStep(chatId) == Status.USER_WRITE_MESSAGE
            ) {
                deleteCallBack(chatId, mId)
                execute(
                    botHandlerForReplyMarkUp.sendInlineMarkUp(
                        chatId,
                        Language.UZ.toString(),
                        Language.EN.toString(),
                        "Choose the language(Tilni tanlang):"
                    )
                )
            } else if (text != null && text.equals("/start")) {
                find(chatId)
            } else {
                when (userService.getUserStep(chatId)) {
                    Status.USER_FULL_NAME -> {
                        if (text != null) {
                            val userRegisterUser: RegisterUser = getRegistrationData(chatId)
                            userRegisterUser.fullName = text
                            setRegistrationData(chatId, userRegisterUser)

                            // ism yozilgan xabarni ochirish
                            deleteCallBack(chatId, update.message.messageId)

                            // ismini soragan xabarni olish va ochirish
                            val messageId = getAllFullNameIdAndMessageIds(chatId)
                            if (messageId != null) {
                                deleteCallBack(chatId, messageId)
                                removeMapFullNameChatIdAndMessageId(chatId)
                            }
                            sendContactRequest(chatId) //kontakni yuborish

                            userService.setUserStep(chatId, Status.USER_PHONE)
                        }
                    }

                    Status.USER_WRITE_MESSAGE -> {
                        sendWritedMessage(chatId, update.message, update.message.messageId)
                    }

                    Status.USER_QUEUE -> {
                        if (text != null) {
                            if (text.equals(getMessageFromResourceBundle(chatId, "back"))) {
                                userService.deleteQueue(chatId)
                                userService.deleteMessage(chatId)
                                sendResponse(
                                    chatId,
                                    "not.answer.delete", ReplyKeyboardRemove(true)
                                )
                                find(chatId)
                                userService.setUserStep(chatId, Status.USER_WRITE_MESSAGE)
                            } else {
                                addMessageForUser(chatId, update.message, update.message.messageId, null)
                            }
                        } else {
                            addMessageForUser(chatId, update.message, update.message.messageId, null)
                        }
                    }

                    Status.USER_CHATTING -> {
                        val operatorChatId = userService.findConversationByUser(chatId)?.operator?.chatId
                        if (update.message.isReply) {
                            val operatorMessageContent =
                                botHandlerForMessages.getContent(update.message.replyToMessage)
                            val operatorMessage =
                                operatorMessageContent?.let {
                                    operatorService.findMessageByOperator(
                                        operatorChatId!!,
                                        it
                                    )
                                }
                            if (operatorMessage != null) {
                                val senderMessageId =
                                    sendReplyMessage(operatorChatId!!, message, operatorMessage.messageId)
                                addMessageForUser(chatId, message, mId, senderMessageId)
                            } else {
                                val userMessage = operatorMessageContent?.let {
                                    userService.findMessageByUser(chatId, it)
                                }
                                val senderMessageId =
                                    sendReplyMessage(operatorChatId!!, message, userMessage?.senderMessageId)
                                addMessageForUser(chatId, message, mId, senderMessageId)
                            }
                        } else {
                            findConversation(chatId, message, mId)
                        }
                    }

                    Status.OPERATOR_START_WORK -> {
                        if (text != null) {
                            if (text.equals(getMessageFromResourceBundle(chatId, "start.work"))) {
                                startWork(chatId)
                                operatorService.startWorkSession(chatId)
                            }
                        }
                    }

                    Status.OPERATOR_ACTIVE -> {
                        if (text != null) {
                            if (text.equals(getMessageFromResourceBundle(chatId, "finish.work"))) {
                                finishWork(chatId)
                            }
                        }
                    }

                    Status.OPERATOR_BUSY -> {
                        if (text != null && (text.equals(getMessageFromResourceBundle(chatId, "finish.work")) )) {
                            finishWork(chatId)
                        } else if (text != null && (text.equals(getMessageFromResourceBundle(chatId, "finish.conversation")))) {
                            finishConversation(chatId)
                        } else {
                            val userChatId = operatorService.findConversationByOperator(chatId)?.users?.chatId
                            try {
                                if (update.message.isReply) {
                                    val userMessageContent =
                                        botHandlerForMessages.getContent(update.message.replyToMessage)
                                    val userMessage =
                                        userMessageContent?.let { userService.findMessageByUser(userChatId!!, it) }
                                    if (userMessage != null) {
                                        val senderMessageId =
                                            sendReplyMessage(userChatId!!, message, userMessage.messageId)
                                        addReplyMessageForOperator(
                                            chatId,
                                            message,
                                            mId,
                                            userChatId,
                                            userMessageContent,
                                            senderMessageId
                                        )
                                    } else {
                                        val operatorMessage = userMessageContent?.let {
                                            operatorService.findMessageByOperator(chatId, it)
                                        }
                                        val senderMessageId =
                                            sendReplyMessage(userChatId!!, message, operatorMessage?.senderMessageId)
                                        addMessageForOperator(chatId, message, mId, senderMessageId)
                                    }
                                } else {
                                    val senderMessageId = botHandlerForMessages.sendMessage(userChatId!!, message)
                                    addMessageForOperator(chatId, message, mId, userChatId, senderMessageId)
                                }
                            } catch (tae: TelegramApiException) {
                                tae.message?.let {
                                    if (it.contains("Forbidden: bot was blocked by the user")) {
                                        sendResponse(chatId, "user.blocked")
                                    }
                                }
                            }
                        }
                    }

                    else -> ""
                }
            }

        } else if (update != null && update.hasCallbackQuery() &&
            userService.getUserStep(update.callbackQuery.message.chatId) == Status.USER_WRITE_MESSAGE
        ) {
            val chatId = update.callbackQuery.message.chatId
            val data = update.callbackQuery.data
            val userStep = userService.getUserStep(chatId)
            //// delete calback query
            deleteCallBack(chatId, update.callbackQuery.message.messageId)

            when {
                "${Language.EN}_call_back_data" == data && userStep == Status.USER_WRITE_MESSAGE -> {
                    userService.addLanguage(chatId, Language.EN)
                    find(chatId)
                }

                "${Language.UZ}_call_back_data" == data && userStep == Status.USER_WRITE_MESSAGE -> {
                    userService.addLanguage(chatId, Language.UZ)
                    find(chatId)
                }

                else -> ""
            }
        } else if (update != null && update.hasCallbackQuery()) {
            val chatId = update.callbackQuery.message.chatId
            val data = update.callbackQuery.data
            val userStep = userService.getUserStep(chatId)
            //// delete calback query
            deleteCallBack(chatId, update.callbackQuery.message.messageId)
            when {
                "${Language.EN}_call_back_data" == data && userStep == Status.USER_LANGUAGE -> {
                    userService.setUserStep(chatId, Status.USER_FULL_NAME)
                    userService.addLanguage(chatId, Language.EN)
                    val message = execute(
                        botHandlerForMessages.sendText(
                            chatId, getMessageFromResourceBundle(chatId, "enter.name")
                        )
                    )
                    ////
                    putFullNameIdAndMessageId(chatId, message.messageId)
                }

                "${Language.UZ}_call_back_data" == data && userStep == Status.USER_LANGUAGE -> {
                    userService.setUserStep(chatId, Status.USER_FULL_NAME)
                    userService.addLanguage(chatId, Language.UZ)


                    val message = execute(
                        botHandlerForMessages.sendText(
                            chatId, getMessageFromResourceBundle(chatId, "enter.name")
                        )
                    )
                    ////
                    putFullNameIdAndMessageId(chatId, message.messageId)
                }

                "1_call_back_data" == data && userStep == Status.USER_RATING -> addRatingScore(1, chatId)
                "2_call_back_data" == data && userStep == Status.USER_RATING -> addRatingScore(2, chatId)
                "3_call_back_data" == data && userStep == Status.USER_RATING -> addRatingScore(3, chatId)
                "4_call_back_data" == data && userStep == Status.USER_RATING -> addRatingScore(4, chatId)
                "5_call_back_data" == data && userStep == Status.USER_RATING -> addRatingScore(5, chatId)
                else -> ""
            }


        } else if (update != null && update.hasEditedMessage()) {
            val editedMessage = update.editedMessage
            val editChatId = editedMessage.chatId
            val messageId = editedMessage.messageId.toLong()

            var conversation = userService.findConversationByUser(editChatId)
            var chatId = conversation?.operator?.chatId
            if (conversation == null) {
                conversation = operatorService.findConversationByOperator(editChatId)
                chatId = conversation?.users?.chatId
            }

            if (queueRepository.existsByUsersChatIdAndDeletedFalse(editChatId)) {
                if (editedMessage.hasText()) {
                    val text = editedMessage.text
                    operatorService.getMessageByMessageId(messageId).let {
                        it.content = "$text (edited)"
                        messageRepository.save(it)
                    }
                } else if (editedMessage.hasPhoto()) {
                    operatorService.getMessageByMessageId(messageId).let {
                        it.content = editedMessage.photo.last().fileId
                        it.caption = editedMessage.caption ?: ""
                        messageRepository.save(it)
                    }
                } else if (editedMessage.hasDocument()) {
                    operatorService.getMessageByMessageId(messageId).let {
                        it.content = editedMessage.document.fileId
                        it.caption = editedMessage.caption ?: ""
                        messageRepository.save(it)
                    }
                } else if (editedMessage.hasVideo()) {
                    operatorService.getMessageByMessageId(messageId).let {
                        it.content = editedMessage.video.fileId
                        it.caption = editedMessage.caption ?: ""
                        messageRepository.save(it)
                    }
                } else if (editedMessage.hasAudio()) {
                    operatorService.getMessageByMessageId(messageId).let {
                        it.content = editedMessage.audio.fileId
                        it.caption = editedMessage.caption ?: ""
                        messageRepository.save(it)
                    }
                }

            } else if (editedMessage.hasText()) {
                val editText = editedMessage.text
                val editMessage = EditMessageText()
                editMessage.chatId = chatId.toString()
                editMessage.messageId = operatorService.getBotMessageId(messageId).telegramMessageId
                editMessage.text = "$editText (edited)"
                execute(editMessage)
            } else if (editedMessage.hasPhoto()) {
                val editMessage = EditMessageMedia()
                editMessage.chatId = chatId.toString()
                editMessage.messageId = operatorService.getBotMessageId(messageId).telegramMessageId
                val newMedia = InputMediaPhoto()
                newMedia.media = editedMessage.photo.last().fileId
                newMedia.caption = editedMessage.caption ?: ""
                editMessage.media = newMedia
                execute(editMessage)
            } else if (editedMessage.hasDocument()) {
                val editMessage = EditMessageMedia()
                editMessage.chatId = chatId.toString()
                editMessage.messageId = operatorService.getBotMessageId(messageId.toLong()).telegramMessageId
                val newMedia = InputMediaDocument()
                newMedia.media = editedMessage.document.fileId
                newMedia.caption = editedMessage.caption ?: ""
                editMessage.media = newMedia
                execute(editMessage)
            } else if (editedMessage.hasVideo()) {
                val editMessage = EditMessageMedia()
                editMessage.chatId = chatId.toString()
                editMessage.messageId = operatorService.getBotMessageId(messageId.toLong()).telegramMessageId
                val newMedia = InputMediaVideo()
                newMedia.media = editedMessage.video.fileId
                newMedia.caption = editedMessage.caption ?: ""
                editMessage.media = newMedia
                execute(editMessage)
            } else if (editedMessage.hasVoice()) {
                val editMessage = EditMessageMedia()
                editMessage.chatId = chatId.toString()
                editMessage.messageId = operatorService.getBotMessageId(messageId.toLong()).telegramMessageId
                val newMedia = InputMediaDocument()
                newMedia.media = editedMessage.voice.fileId
                newMedia.caption = editedMessage.caption ?: ""
                editMessage.media = newMedia
                execute(editMessage)
            } else if (editedMessage.hasAudio()) {
                val editMessage = EditMessageMedia()
                editMessage.chatId = chatId.toString()
                editMessage.messageId = operatorService.getBotMessageId(messageId.toLong()).telegramMessageId
                val newMedia = InputMediaAudio()
                newMedia.media = editedMessage.audio.fileId
                newMedia.caption = editedMessage.caption ?: ""
                editMessage.media = newMedia
                execute(editMessage)
            } else if (editedMessage.hasAnimation()) {
                val editMessage = EditMessageMedia()
                editMessage.chatId = chatId.toString()
                editMessage.messageId = operatorService.getBotMessageId(messageId.toLong()).telegramMessageId
                val newMedia = InputMediaDocument()
                newMedia.media = editedMessage.animation.fileId
                newMedia.caption = editedMessage.caption ?: ""
                editMessage.media = newMedia
                execute(editMessage)
            }

        }
    }

    fun find(chatId: Long) {
        when {
            userService.findUser(chatId)?.phone != null -> {
                if (userService.getUserStep(chatId) == Status.USER_WRITE_MESSAGE) {
                    sendResponse(
                        chatId,
                        "write.question",
                        ReplyKeyboardRemove(true)
                    )
                }
            }

            operatorService.findOperator(chatId) != null -> {
                val operator = operatorService.findOperator(chatId)
                if (userService.getUserStep(chatId) == Status.OPERATOR_INACTIVE) {
                    operatorService.setOperatorStep(chatId, Status.OPERATOR_START_WORK)
                    sendResponse(
                        chatId,
                        "hello",
                        operator?.fullName
                    )
                    sendReplyMarkUp(
                        chatId,
                        "start.work",
                        "sent.stark.work"
                    )
                }
            }

            else -> {
                if (userService.findUser(chatId) == null) {
                    userService.addUser(chatId, Status.USER_LANGUAGE)
                } else {
                    userService.deleteUser(chatId)
                }
                execute(
                    botHandlerForReplyMarkUp.sendInlineMarkUp(
                        chatId,
                        Language.UZ.toString(),
                        Language.EN.toString(),
                        "Choose the language(Tilni tanlang):"
                    )
                )
            }
        }
    }

    fun startWork(chatId: Long) {
        operatorService.startWork(chatId)?.let { it ->
            try {
                sendResponse(
                    it.chatId,
                    "sent.successfully.to.operator",
                    operatorService.findOperator(chatId)?.fullName,
                    ReplyKeyboardRemove(true)
                )
                sendResponse(
                    chatId,
                    "start.conversation",
                    it.fullName
                )
                sendReplyMarkUp(
                    chatId,
                    "finish.work",
                    "finish.conversation",
                    "message.for.finish"
                )

                userService.findMessagesByUser(it.chatId)?.let {
                    it.forEach { message ->
                        val senderMessageId =
                            botHandlerForMessages.sendMessage(chatId, message.type, message.caption, message.content)
                        userService.addSenderMessageId(message.senderId, message.content, senderMessageId)
                    }
                }
                operatorService.setOperatorStep(chatId, Status.OPERATOR_BUSY)
                userService.setUserStep(it.chatId, Status.USER_CHATTING)
                operatorService.addConversation(chatId, it)
                userService.deleteQueue(it.chatId)

            } catch (tae: TelegramApiException) {
                tae.message?.let { item ->
                    if (item.contains("Forbidden: bot was blocked by the user")) {
                        userService.deleteQueue(it.chatId)
                        userService.deleteMessage(it.chatId)
                        userService.setUserStep(it.chatId, Status.USER_WRITE_MESSAGE)
                        sendResponse(chatId, "user.blocked.queue")
                        startWork(chatId)
                    }
                }
            }
        } ?: run {
            sendResponse(
                chatId,
                "not.user.in.queue",
            )
            sendReplyMarkUp(chatId, "finish.work", "message.for.finish.work")
        }
    }

    fun sendReplyMarkUp(
        chatId: Long,
        message: String,
        response: String,
    ) {
        val userLanguage = userService.getUserLanguage(chatId)?.get(0)?.name?.lowercase() ?: "en"
        val locale = Locale(userLanguage)

        val message1 = messageSource.getMessage(message, null, locale)
        val response1 = messageSource.getMessage(response, null, locale)
        execute(botHandlerForReplyMarkUp.sendReplyMarkUp(chatId, message1, response1))

    }

    fun sendReplyMarkUp(
        chatId: Long,
        first: String,
        second: String,
        response: String,
    ) {
        val userLanguage = userService.getUserLanguage(chatId)?.get(0)?.name?.lowercase() ?: "en"
        val locale = Locale(userLanguage)


        execute(
            botHandlerForReplyMarkUp.sendReplyMarkUp(
                chatId,
                messageSource.getMessage(first, null, locale),
                messageSource.getMessage(second, null, locale),
                messageSource.getMessage(response, null, locale)
            )
        )
    }


    fun sendResponse(chatId: Long, code: String, vararg args: Any?) {

        val userLanguage = userService.getUserLanguage(chatId)?.get(0)?.name?.lowercase() ?: "en"
        val locale = Locale(userLanguage)

        val response = if (args.isNotEmpty()) {
            messageSource.getMessage(code, args, locale)
        } else {
            messageSource.getMessage(code, null, locale)
        }
        execute(botHandlerForMessages.sendText(chatId, response))
    }


    fun getMessageFromResourceBundle(chatId: Long, code: String): String {
        val userLanguage = userService.getUserLanguage(chatId)?.get(0)?.name?.lowercase() ?: "en"
        val locale = Locale(userLanguage)

        return messageSource.getMessage(code, null, locale)
    }


    fun sendResponse(chatId: Long, code: String, replyKeyboardRemove: ReplyKeyboardRemove) {

        val response = getMessageFromResourceBundle(chatId, code)
        execute(botHandlerForMessages.sendMessage(chatId, response, replyKeyboardRemove))
    }


    fun sendWritedMessage(chatId: Long, message: Message, messageId: Int) {
        var senderMessageId: Int? = null
        operatorService.findAvailableOperator(userService.getUserLanguage(chatId)!![0])?.let {
            sendResponse(
                chatId,
                "sent.successfully.to.operator",
                it.fullName,
                ReplyKeyboardRemove(true)
            )
            sendResponse(
                it.chatId,
                "start.conversation",
                userService.findUser(chatId)!!.fullName
            )
            sendReplyMarkUp(
                it.chatId,
                "finish.work",
                "finish.conversation",
                "message.for.finish"
            )
            botHandlerForMessages.sendMessage(it.chatId, message)
            operatorService.setOperatorStep(it.chatId, Status.OPERATOR_BUSY)
            userService.setUserStep(chatId, Status.USER_CHATTING)
            userService.addConversation(chatId, it)

        } ?: run {
            userService.addQueue(chatId)
            sendResponse(
                chatId,
                "busy.operator"
            )
            userService.setUserStep(chatId, Status.USER_QUEUE)
            sendReplyMarkUp(
                chatId,
                "back",
                "back.message"
            )
        }
        addMessageForUser(chatId, message, messageId, senderMessageId)
    }

    fun addMessageForUser(chatId: Long, message: Message, messageId: Int, senderMessageId: Int?) {
        botHandlerForMessages.findTypeOfMessage(message)?.let { item ->
            botHandlerForMessages.getContent(message)?.let {
                userService.addMessage(chatId, it, item, message.caption, messageId, senderMessageId)
            }
        }
    }

    fun addMessageForOperator(chatId: Long, message: Message, messageId: Int, userChatId: Long, senderMessageId: Int?) {
        botHandlerForMessages.findTypeOfMessage(message)?.let { item ->
            botHandlerForMessages.getContent(message)?.let {
                senderMessageId?.let { id ->
                    operatorService.addMessage(chatId, it, item, message.caption, messageId, id)
                    userService.addConversationToMessage(userChatId)
                }
            }
        }
    }

    fun addMessageForOperator(chatId: Long, message: Message, messageId: Int, senderMessageId: Int?) {
        botHandlerForMessages.findTypeOfMessage(message)?.let { item ->
            botHandlerForMessages.getContent(message)?.let {
                senderMessageId?.let { id ->
                    operatorService.addMessage(chatId, it, item, message.caption, messageId, id)
                }
            }
        }
    }

    fun addReplyMessageForOperator(
        chatId: Long,
        message: Message,
        messageId: Int,
        userChatId: Long,
        userContent: String,
        senderMessageId: Int?
    ) {
        botHandlerForMessages.findTypeOfMessage(message)?.let { item ->
            botHandlerForMessages.getContent(message)?.let {
                senderMessageId?.let { id ->
                    operatorService.addMessage(chatId, it, item, message.caption, messageId, id)
                    userService.addConversationToMessage(userChatId, userContent)
                }
            }
        }
    }


    fun findConversation(chatId: Long, message: Message, messageId: Int) {
        userService.findConversationByUser(chatId)?.let {
            val senderMessageId = botHandlerForMessages.sendMessage(it.operator.chatId, message)
            addMessageForUser(chatId, message, messageId, senderMessageId)
        }
    }

    fun sendReplyMessage(chatId: Long, operatorMessage: Message, messageId: Int?): Int? {
        return messageId?.let {
            botHandlerForMessages.sendRepLyMessage(chatId, operatorMessage, it)
        }
    }

    fun finishConversation(chatId: Long) {
        addRating(operatorService.finishConversation(chatId))
        operatorService.setOperatorStep(chatId, Status.OPERATOR_ACTIVE)
        sendResponse(
            chatId,
            "conversation.finished.success"
        )
        startWork(chatId)

    }

    fun finishWork(chatId: Long) {
        operatorService.finishWork(chatId)
        addRating(operatorService.finishConversation(chatId))
        operatorService.setOperatorStep(chatId, Status.OPERATOR_INACTIVE)
        sendResponse(
            chatId,
            "work.finished.success",
            ReplyKeyboardRemove(true)
        )
        find(chatId)
    }

    fun addRating(chatId: Long?) {
        if (chatId != null) {
            try {
                userService.deleteMessage(chatId)
                when (userService.getUserLanguage(chatId)?.get(0)) {
                    Language.EN -> execute(
                        botHandlerForReplyMarkUp.sendInlineMarkUp(
                            listOf("1", "2", "3", "4", "5"),
                            chatId,
                            getMessageFromResourceBundle(chatId, "rate.conversation")

                        )
                    )

                    Language.UZ -> execute(
                        botHandlerForReplyMarkUp.sendInlineMarkUp(
                            listOf("1", "2", "3", "4", "5"),
                            chatId,
                            getMessageFromResourceBundle(chatId, "rate.conversation")
                        )
                    )

                    else -> ""
                }
                userService.setUserStep(chatId, Status.USER_RATING)
            } catch (tae: TelegramApiException) {
                tae.message?.let {
                    if (it.contains("Forbidden: bot was blocked by the user")) {
                        userService.setUserStep(chatId, Status.USER_WRITE_MESSAGE)
                        userService.addRatingScore(0, chatId)
                    }
                }
            }
        }

    }

    fun addRatingScore(score: Int, chatId: Long) {
        userService.addRatingScore(score, chatId)
        sendResponse(
            chatId,
            "write.question",
            ReplyKeyboardRemove(true)
        )
        userService.setUserStep(chatId, Status.USER_WRITE_MESSAGE)
    }

    fun deleteCallBack(chatId: Long, messageId: Int) {
        try {
            execute(
                org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage(
                    chatId.toString(),
                    messageId
                )
            )
        } catch (e: TelegramApiException) {
            e.printStackTrace()
        }
    }

    fun sendContactRequest(chatId: Long) {
        // Keyboard tugmasi yaratiladi
        val contactButton = KeyboardButton("\uD83D\uDCDE").apply {
            requestContact = true // Kontaktni so‘rashni yoqish
        }

        // Klaviatura qatorini yaratish
        val keyboardRow = KeyboardRow().apply {
            add(contactButton)
        }

        // ReplyKeyboardMarkup ni sozlash
        val replyKeyboardMarkup = ReplyKeyboardMarkup().apply {
            keyboard = listOf(keyboardRow)
            resizeKeyboard = true // Klaviatura ekranga moslashadi
            oneTimeKeyboard = true // Tugma faqat bir marta ko‘rinadi
        }

        // Xabarni yaratish
        val message = SendMessage().apply {
            this.chatId = chatId.toString()
            text = getMessageFromResourceBundle(chatId, "share.your.contact") + "☎\uFE0F"
            replyMarkup = replyKeyboardMarkup // Klaviatura qo‘shiladi
        }

        // Xabarni yuborish
        val sendMessages = execute(message)
        putFullNameIdAndMessageId(chatId, sendMessages.messageId)
    }
}

@Controller
class BotHandlerForMessages(
    private val botHandler: BotHandler
) {

    fun sendMessage(chatId: Long, message: Message): Int? {
        return when {
            message.hasPhoto() -> botHandler.execute(
                sendPhoto(
                    chatId,
                    message.photo[message.photo.size - 1].fileId,
                    message.caption
                )
            ).messageId

            message.hasVideo() -> botHandler.execute(sendVideo(chatId, message.video.fileId, message.caption)).messageId
            message.hasText() -> botHandler.execute(sendText(chatId, message.text)).messageId
            message.hasAnimation() -> botHandler.execute(
                sendAnimation(
                    chatId,
                    message.animation.fileId,
                    message.caption
                )
            ).messageId

            message.hasAudio() -> botHandler.execute(sendAudio(chatId, message.audio.fileId, message.caption)).messageId
            message.hasVideoNote() -> botHandler.execute(sendVideoNote(chatId, message.videoNote.fileId)).messageId
            message.hasDocument() -> botHandler.execute(
                sendDocument(
                    chatId,
                    message.document.fileId,
                    message.caption
                )
            ).messageId

            message.hasSticker() -> botHandler.execute(sendSticker(chatId, message.sticker.fileId)).messageId
            message.hasVoice() -> botHandler.execute(sendVoice(chatId, message.voice.fileId, message.caption)).messageId
            message.hasDice() -> botHandler.execute(sendDice(chatId, message.dice.emoji)).messageId
            message.hasLocation() -> botHandler.execute(
                sendLocation(
                    chatId,
                    message.location.latitude,
                    message.location.longitude
                )
            ).messageId

            else -> null
        }
    }


    fun sendMessage(chatId: Long, messageType: String, caption: String?, messageContent: String): Int? {
        return when (messageType) {
            "PHOTO" -> botHandler.execute(sendPhoto(chatId, messageContent, caption)).messageId
            "VIDEO" -> botHandler.execute(sendVideo(chatId, messageContent, caption)).messageId
            "TEXT" -> botHandler.execute(sendText(chatId, messageContent)).messageId
            "ANIMATION" -> botHandler.execute(sendAnimation(chatId, messageContent, caption)).messageId
            "AUDIO" -> botHandler.execute(sendAudio(chatId, messageContent, caption)).messageId
            "VIDEONOTE" -> botHandler.execute(sendVideoNote(chatId, messageContent)).messageId
            "DOCUMENT" -> botHandler.execute(sendDocument(chatId, messageContent, caption)).messageId
            "STICKER" -> botHandler.execute(sendSticker(chatId, messageContent)).messageId
            "VOICE" -> botHandler.execute(sendVoice(chatId, messageContent, caption)).messageId
            "DICE" -> botHandler.execute(sendDice(chatId, messageContent)).messageId
            "LOCATION" -> {
                val locations = messageContent.split(",")
                botHandler.execute(sendLocation(chatId, locations[0].toDouble(), locations[1].toDouble())).messageId
            }

            else -> {
                return null
            }
        }
    }

    fun sendRepLyMessage(chatId: Long, message: Message, messageId: Int): Int? {
        if (message.hasPhoto()) {
            val sendPhoto = sendPhoto(chatId, message.photo[message.photo.size - 1].fileId, message.caption)
            sendPhoto.replyToMessageId = messageId
            return botHandler.execute(sendPhoto).messageId
        } else if (message.hasVideo()) {
            val sendVideo = sendVideo(chatId, message.video.fileId, message.caption)
            sendVideo.replyToMessageId = messageId
            return botHandler.execute(sendVideo).messageId
        } else if (message.hasText()) {
            val sendText = sendText(chatId, message.text)
            sendText.replyToMessageId = messageId
            return botHandler.execute(sendText).messageId
        } else if (message.hasAnimation()) {
            val sendAnimation = sendAnimation(chatId, message.animation.fileId, message.caption)
            sendAnimation.replyToMessageId = messageId
            return botHandler.execute(sendAnimation).messageId
        } else if (message.hasAudio()) {
            val sendAudio = sendAudio(chatId, message.audio.fileId, message.caption)
            sendAudio.replyToMessageId = messageId
            return botHandler.execute(sendAudio).messageId
        } else if (message.hasVideoNote()) {
            val sendVideoNote = sendVideoNote(chatId, message.videoNote.fileId)
            sendVideoNote.replyToMessageId = messageId
            return botHandler.execute(sendVideoNote).messageId
        } else if (message.hasDocument()) {
            val sendDocument = sendDocument(chatId, message.document.fileId, message.caption)
            sendDocument.replyToMessageId = messageId
            return botHandler.execute(sendDocument).messageId
        } else if (message.hasSticker()) {
            val sendSticker = sendSticker(chatId, message.sticker.fileId)
            sendSticker.replyToMessageId = messageId
            return botHandler.execute(sendSticker).messageId
        } else if (message.hasVoice()) {
            val sendVoice = sendVoice(chatId, message.voice.fileId, message.caption)
            sendVoice.replyToMessageId = messageId
            return botHandler.execute(sendVoice).messageId
        } else if (message.hasDice()) {
            val sendDice = sendDice(chatId, message.dice.emoji)
            sendDice.replyToMessageId = messageId
            return botHandler.execute(sendDice).messageId
        } else if (message.hasLocation()) {
            val sendLocation = sendLocation(chatId, message.location.latitude, message.location.longitude)
            sendLocation.replyToMessageId = messageId
            return botHandler.execute(sendLocation).messageId
        } else {
            return null
        }
    }

    fun findTypeOfMessage(message: Message): String? {
        return when {
            message.hasPhoto() -> "PHOTO"
            message.hasVideo() -> "VIDEO"
            message.hasText() -> "TEXT"
            message.hasAnimation() -> "ANIMATION"
            message.hasAudio() -> "AUDIO"
            message.hasVideoNote() -> "VIDEONOTE"
            message.hasDocument() -> "DOCUMENT"
            message.hasSticker() -> "STICKER"
            message.hasVoice() -> "VOICE"
            message.hasDice() -> "DICE"
            message.hasLocation() -> "LOCATION"
            else -> null
        }
    }

    fun getContent(message: Message): String? {
        return when {
            message.hasPhoto() -> message.photo.last().fileId
            message.hasVideo() -> message.video.fileId
            message.hasText() -> message.text
            message.hasAnimation() -> message.animation.fileId
            message.hasAudio() -> message.audio.fileId
            message.hasVideoNote() -> message.videoNote.fileId
            message.hasDocument() -> message.document.fileId
            message.hasSticker() -> message.sticker.fileId
            message.hasVoice() -> message.voice.fileId
            message.hasDice() -> message.dice.emoji
            message.hasLocation() -> "${message.location.latitude},${message.location.longitude}"
            else -> null
        }
    }

    fun sendMessage(chatId: Long, message: String, replyKeyboardRemove: ReplyKeyboardRemove): SendMessage {
        val sendMessage = SendMessage()
        sendMessage.chatId = chatId.toString()
        sendMessage.text = message
        sendMessage.replyMarkup = replyKeyboardRemove
        return sendMessage
    }

    fun sendText(chatId: Long, message: String): SendMessage {
        return SendMessage().apply {
            this.text = message
            this.chatId = chatId.toString()
        }
    }

    fun sendPhoto(chatId: Long, fileId: String, caption: String?): SendPhoto {
        return SendPhoto().apply {
            this.chatId = chatId.toString()
            this.photo = InputFile(fileId)
            this.caption = caption
        }
    }

    fun sendAudio(chatId: Long, fileId: String, caption: String?): SendAudio {
        return SendAudio().apply {
            this.chatId = chatId.toString()
            this.audio = InputFile(fileId)
            this.caption = caption
        }
    }

    fun sendVideo(chatId: Long, fileId: String, caption: String?): SendVideo {
        return SendVideo().apply {
            this.chatId = chatId.toString()
            this.video = InputFile(fileId)
            this.caption = caption
        }
    }

    fun sendAnimation(chatId: Long, fileId: String, caption: String?): SendAnimation {
        return SendAnimation().apply {
            this.chatId = chatId.toString()
            this.animation = InputFile(fileId)
            this.caption = caption
        }
    }

    fun sendVoice(chatId: Long, fileId: String, caption: String?): SendVoice {
        return SendVoice().apply {
            this.chatId = chatId.toString()
            this.voice = InputFile(fileId)
            this.caption = caption
        }
    }

    fun sendVideoNote(chatId: Long, fileId: String): SendVideoNote {
        return SendVideoNote().apply {
            this.chatId = chatId.toString()
            this.videoNote = InputFile(fileId)

        }
    }

    fun sendSticker(chatId: Long, fileId: String): SendSticker {
        return SendSticker().apply {
            this.chatId = chatId.toString()
            this.sticker = InputFile(fileId)
        }
    }

    fun sendDocument(chatId: Long, fileId: String, caption: String?): SendDocument {
        return SendDocument().apply {
            this.chatId = chatId.toString()
            this.document = InputFile(fileId)
            this.caption = caption
        }
    }

    fun sendDice(chatId: Long, fileId: String): SendDice {
        return SendDice().apply {
            this.chatId = chatId.toString()
            this.emoji = fileId
        }
    }

//    fun sendLocation(chatId: Long, fileId: String, caption: String?): SendLocation {
//        return
//    }

    fun sendLocation(chatId: Long, latitude: Double, longitude: Double): SendLocation {
        return SendLocation().apply {
            this.chatId = chatId.toString()
            this.latitude = latitude
            this.longitude = longitude
        }
    }
}

@Controller
class BotHandlerForReplyMarkUp {
    fun sendInlineMarkUp(chatId: Long, firstMessage: String, secondMessage: String, messageText: String): SendMessage {
        val sendMessage = SendMessage()
        val sendInlineKeyboardMarkUp = InlineKeyboardMarkup()
        val inlineKeyboardMarkupButton1 = InlineKeyboardButton()
        val inlineKeyboardMarkupButton2 = InlineKeyboardButton()
        inlineKeyboardMarkupButton1.text = "\uD83C\uDDFA\uD83C\uDDFF $firstMessage"
        inlineKeyboardMarkupButton1.callbackData = "${firstMessage}_call_back_data"
        inlineKeyboardMarkupButton2.text = "\uD83C\uDDEC\uD83C\uDDE7 $secondMessage"
        inlineKeyboardMarkupButton2.callbackData = "${secondMessage}_call_back_data"
        val listOfButtons = mutableListOf(inlineKeyboardMarkupButton1, inlineKeyboardMarkupButton2)
        val listOfListsOfButtons = mutableListOf(listOfButtons)
        sendInlineKeyboardMarkUp.keyboard = listOfListsOfButtons
        sendMessage.replyMarkup = sendInlineKeyboardMarkUp
        sendMessage.chatId = chatId.toString()
        sendMessage.text = messageText
        return sendMessage
    }

    fun sendInlineMarkUp(listOfInlines: List<String>, chatId: Long, message: String): SendMessage {
        val sendMessage = SendMessage()
        val inlineKeyboardMarkup = InlineKeyboardMarkup()
        val rows = mutableListOf<InlineKeyboardButton>()

        for (i in listOfInlines.indices) {
            val button = InlineKeyboardButton().apply {
                text = listOfInlines[i]
                callbackData = "${listOfInlines[i]}_call_back_data"
            }
            rows.add(button)
        }
        inlineKeyboardMarkup.apply {
            keyboard = listOf(rows)
        }
        return sendMessage.apply {
            this.chatId = chatId.toString()
            this.text = message
            replyMarkup = inlineKeyboardMarkup
        }
    }


    fun sendReplyMarkUp(chatId: Long, message: String, messageResponse: String): SendMessage {
        val sendMessage = SendMessage()
        val replyKeyboardMarkup = ReplyKeyboardMarkup()
        val replyMarkUpRow = KeyboardRow()
        val replyKeyboardButton = KeyboardButton()

        replyKeyboardButton.text = message
        replyMarkUpRow.add(replyKeyboardButton)
        replyKeyboardMarkup.selective = true
        replyKeyboardMarkup.resizeKeyboard = true
        replyKeyboardMarkup.keyboard = mutableListOf(replyMarkUpRow)


        sendMessage.replyMarkup = replyKeyboardMarkup
        sendMessage.chatId = chatId.toString()
        sendMessage.text = messageResponse
        return sendMessage
    }

    fun sendReplyMarkUp(
        chatId: Long,
        firstMessage: String,
        secondMessage: String,
        messageResponse: String,
    ): SendMessage {
        val sendMessage = SendMessage()
        val replyKeyboardMarkup = ReplyKeyboardMarkup()
        val replyMarkUpRow = KeyboardRow()
        val replyKeyboardButton1 = KeyboardButton()
        val replyKeyboardButton2 = KeyboardButton()

        replyKeyboardButton1.text = firstMessage
        replyKeyboardButton2.text = secondMessage
        replyMarkUpRow.add(replyKeyboardButton1)
        replyMarkUpRow.add(replyKeyboardButton2)
        replyKeyboardMarkup.selective = true
        replyKeyboardMarkup.resizeKeyboard = true
        replyKeyboardMarkup.keyboard = mutableListOf(replyMarkUpRow)

        sendMessage.replyMarkup = replyKeyboardMarkup
        sendMessage.chatId = chatId.toString()
        sendMessage.text = messageResponse
        return sendMessage
    }

}


@RestController
@RequestMapping("/api/operators/statistics")
class OperatorStatisticsController(
    private val operatorStatisticsService: OperatorStatisticsService
) {

    @GetMapping("/total")  ///
    fun getTotalOperators(): AdminCount {
        val totalOperators = operatorStatisticsService.getTotalOperators()
        return AdminCount(totalOperators)
    }


    @GetMapping("/work-hours")
    fun getTotalWorkHours(): List<OperatorWorkHoursDto> {
        return operatorStatisticsService.findTotalWorkHours()
    }

    @GetMapping("/salary")
    fun getTotalSalary(): List<OperatorSalaryDto> {
        return operatorStatisticsService.findTotalSalary()
    }

    @GetMapping("/ratings")//ortacha
    fun getAverageRatings(): List<OperatorRatingDto> {
        return operatorStatisticsService.findAverageRatings()
    }

    @GetMapping("/conversations")
    fun getTotalConversations(): List<OperatorConversationDto> {
        return operatorStatisticsService.findOperatorConversationCounts()
    }
}


@RestController
@RequestMapping("/admin")
class AdminPanelController(
    private val operatorService: OperatorService,
    private val botHandler: BotHandler
) {
    @PostMapping("/create")
    fun createOperator(@RequestBody operator: RegisterOperator) {
        operatorService.addOperator(operator.id, operator.langType)?.let {
//            removeUsersStep(it)
            botHandler.sendResponse(
                it,
                "session.expired"
            )
        }
    }
}





