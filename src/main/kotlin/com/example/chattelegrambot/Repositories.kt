package com.example.chattelegrambot

import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.support.JpaEntityInformation
import org.springframework.data.jpa.repository.support.SimpleJpaRepository
import org.springframework.data.repository.NoRepositoryBean
import org.springframework.data.repository.findByIdOrNull
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@NoRepositoryBean
interface BaseRepository<T : BaseEntity> : JpaRepository<T, Long>, JpaSpecificationExecutor<T> {
    fun findByIdAndDeletedFalse(id: Long): T?
    fun trash(id: Long): T?
    fun trashList(ids: List<Long>): List<T?>
    fun findAllNotDeleted(): List<T>
    fun findAllNotDeleted(pageable: Pageable): List<T>
    fun findAllNotDeletedForPageable(pageable: Pageable): Page<T>
    fun saveAndRefresh(t: T): T
}

class BaseRepositoryImpl<T : BaseEntity>(
    entityInformation: JpaEntityInformation<T, Long>,
    private val entityManager: EntityManager
) : SimpleJpaRepository<T, Long>(entityInformation, entityManager), BaseRepository<T> {

    val isNotDeletedSpecification = Specification<T> { root, _, cb -> cb.equal(root.get<Boolean>("deleted"), false) }

    override fun findByIdAndDeletedFalse(id: Long) = findByIdOrNull(id)?.run { if (deleted) null else this }

    @Transactional
    override fun trash(id: Long): T? = findByIdAndDeletedFalse(id)?.run {
        deleted = true
        save(this)
    }

    override fun findAllNotDeleted(): List<T> = findAll(isNotDeletedSpecification)
    override fun findAllNotDeleted(pageable: Pageable): List<T> = findAll(isNotDeletedSpecification, pageable).content
    override fun findAllNotDeletedForPageable(pageable: Pageable): Page<T> =
        findAll(isNotDeletedSpecification, pageable)

    override fun trashList(ids: List<Long>): List<T?> = ids.map { trash(it) }

    @Transactional
    override fun saveAndRefresh(t: T): T {
        return save(t).apply { entityManager.refresh(this) }
    }
}

@Repository
interface UserRepository : BaseRepository<Users> {

    @Query(
        """
        select u from users u
         where u.deleted = false
         and u.chatId =?1
    """
    )
    fun findUsersByChatId(chatId: Long): Users?

}//

@Repository
interface OperatorRepository : BaseRepository<Operator> {
    @Query(
        """
        select o from operators o
         where o.deleted = false
         and o.chatId =?1
    """
    )
    fun findOperatorByChatId(chatId: Long): Operator?


    @Modifying
    @Query(
        """
        update operators o set o.status = ?2
        where o.chatId = ?1 and o.deleted = false
    """
    )
    fun changeStatus(chatId: Long, status: Status)

    @Query(
        value =
        """
      select * from operators o
where o.deleted = false and (o.language[1] =:language or o.language[2] =:language) and o.status = 'OPERATOR_ACTIVE'
order by o.id
limit 1
    """, nativeQuery = true
    )
    fun findAvailableOperator(@Param("language") language: String): Operator?


}

@Repository
interface WorkSessionRepository : BaseRepository<WorkSession> {

    @Query(
        """
            select ws from workSessions ws
            where ws.operator.chatId = ?1 and ws.endDate is null
    """
    )
    fun getTodayWorkSession(chatId: Long): WorkSession

    @Query(
        """
    SELECT w.operator.fullName,SUM(w.workMinute) / 60.0
    FROM workSessions w 
    GROUP BY w.operator.fullName
"""
    )
    fun findTotalWorkHoursRaw(): List<Array<Any>>


    @Query(
        """
    SELECT w.operator.fullName, SUM(w.salary*1.0) 
    FROM workSessions w 
    GROUP BY w.operator.fullName
"""
    )
    fun findTotalSalaryRaw(): List<Array<Any>>


}

@Repository
interface QueueRepository : BaseRepository<Queue> {
    @Query(
        """
        select q.users from queues q
        where q.deleted = false and q.language = ?1
        order by q.createdDate asc
        limit 1
    """
    )
    fun findFirstUserFromQueue(language: Language): Users? // togirlash kerak


    @Query(
        """
        select q from queues q
        where q.users.chatId = ?1 and q.deleted = false
    """
    )
    fun existUser(chatId: Long): Queue?

    @Modifying
    @Query(
        """
        update queues q set q.deleted = true 
        where q.users.chatId = ?1 and q.language = ?2
    """
    )
    fun deleteUserFromQueue(chatId: Long, language: Language)

    fun findByDeletedFalseOrderByCreatedDateAsc(): List<Queue>

    fun existsByUsersChatIdAndDeletedFalse(chatId: Long): Boolean
}

@Repository
interface RatingRepository : BaseRepository<Rating> {
    @Query(
        """
        select r from ratings r 
        where r.users.chatId = ?1 and r.score is null
    """
    )
    fun findRating(chatId: Long): Rating?

    @Query(
        """
    SELECT r.operator.fullName, COALESCE(AVG(r.score), 0) 
    FROM ratings r 
    GROUP BY r.operator.fullName
"""
    )
    fun findAverageRatingsRaw(): List<Array<Any>>


}

@Repository
interface MessageRepository : BaseRepository<Message> {

    @Query(
        """
        select m from messages m
        where m.senderId = ?1 and m.conversation is null and m.deleted = false
    """
    )
    fun findMessagesByUser(chatId: Long): List<Message>?

    @Query(
        """
        select m from messages m
        where m.senderId = ?1 and m.conversation is null and m.content = ?2 and  m.deleted = false
        order by m.createdDate
        limit 1
    """
    )
    fun findMessageByUser(chatId: Long, content: String): Message?

    @Query(
        """
        select m from messages m
        where m.senderId = ?1 and m.content = ?2 and  m.deleted = false
        order by m.createdDate
        limit 1
    """
    )
    fun findMessageByOperator(chatId: Long, content: String): Message?


    @Query(
        """
        select m from messages m
        where m.senderId = ?1 and m.conversation is null and m.deleted = false
        order by m.createdDate desc
        limit 1
    """
    )
    fun findFirstMessageByUser(chatId: Long): Message?

    @Modifying
    @Query(
        """
        update messages m set m.deleted = true
        where m.senderId = ?1 and m.conversation is null
    """
    )
    fun deleteMessagesByUser(chatId: Long)

    fun findByMessageIdAndDeletedFalse(messageId: Long): Message

}

@Repository
interface ConversationRepository : BaseRepository<Conversation> {

    @Query(
        """
        select c from conversations c
        where c.operator.chatId = ?1 and c.endDate is null and c.deleted = false
    """
    )
    fun findConversationByOperator(chatId: Long): Conversation?

    @Query(
        """
        select c from conversations c
        where c.users.chatId = ?1 and c.endDate is null and c.deleted = false
    """
    )
    fun findConversationByUser(chatId: Long): Conversation?

    @Query(
        """
    SELECT c.operator.fullName, COUNT(c.id) 
    FROM conversations c 
    GROUP BY c.operator.fullName
"""
    )
    fun findOperatorConversationCountsRaw(): List<Array<Any>>

}

@Repository
interface BotMessageRepository : BaseRepository<BotMessage>{
    fun findByMessageId(messageId: Long): BotMessage
}

