package database

import com.github.softwareengineeringclub.database.Database
import com.squareup.sqldelight.Query
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import model.Conversation
import model.Message
import model.User
import java.net.URL

class ConversationRepository(private val database: Database, private val users: UserRepository) {
    private val profaneRegex: Regex

    init {

        println("Loading profanity filter...")
        val url = URL("https://raw.githubusercontent.com/RobertJGabriel/Google-profanity-words/master/list.txt")
        // Grab each word in the list
        val profaneWords = url.readText().split("\n")

        // Convert each word into a regex string like so that ["dog", "cat", "mouse"] becomes "(dog)|(cat)|(mouse)"
        val profaneStr = profaneWords.filter { it.isNotBlank() }.joinToString("|") { word -> "($word)" }
        profaneRegex = Regex(profaneStr)
    }


    fun getConversations() = database.conversationQueries
        .getAllConversations()
        .asFlow()
        .mapToList(Dispatchers.IO)

    suspend fun getConversation(id: Int) = withContext(Dispatchers.IO) {
        database.conversationQueries.getConversation(id) { id, name, hasDefaultName, ownerId ->
            val owner = users.getUserQuery(ownerId).executeAsOne()
            Conversation(id, name, owner, getLastConversationMessageQuery(id).executeAsOneOrNull())
        }.executeAsOneOrNull()
    }

    suspend fun getConversationMembers(id: Int): List<User> = withContext(Dispatchers.IO) {
        database.conversationQueries.getConversationMembers(id) { username, displayName, _ ->
            User(username, displayName)
        }.executeAsList()
    }

    suspend fun addUserToConversation(conversationId: Int, username: String) {
        withContext(Dispatchers.IO) {
            val members = database.conversationQueries.getConversationMembers(conversationId).executeAsList()

            if (members.any { it.username == username }) {
                // nothing to do
            } else {
                database.conversationQueries.addUserToConversation(conversationId, username)
                updateConversationName(conversationId)
            }

        }
    }

    suspend fun removeUserFromConversation(conversationId: Int, username: String) {
        withContext(Dispatchers.IO) {
            database.conversationQueries.removeUserFromConversation(conversationId, username)
        }
    }

    suspend fun updateConversationName(conversationId: Int) = withContext(Dispatchers.IO) {
        val conversation = database.conversationQueries.getConversation(conversationId).executeAsOne()

        if (conversation.hasDefaultName == 0) return@withContext

        val newName = generateGroupName(getConversationMembers(conversationId))
        database.conversationQueries.updateConversationName(newName, conversationId)
    }

    suspend fun setConversationName(conversationId: Int, name: String) = withContext(Dispatchers.IO) {
        database.conversationQueries.updateConversationName(name, conversationId)
        database.conversationQueries.setConversationNameType(0, conversationId)
    }

    suspend fun createConversation(ownerUsername: String, name: String, isDefaultName: Boolean) = withContext(Dispatchers.IO) {
        val createdId = database.transactionWithResult<Int> {
            database.conversationQueries.createConversation(
                unnaughtifyText(name),
                ownerUsername,
                if (isDefaultName) 1 else 0
            )

            ChatDatabase.driver.executeQuery(
                null,
                "SELECT currval(pg_get_serial_sequence('Conversation','id'));",
                0
            ).use { cursor ->
                if (cursor.next()) {
                    cursor.getLong(0)!!.toInt()
                } else {
                    -1
                }
            }
        }

        addUserToConversation(createdId, ownerUsername)
        insertMessage(createdId, ownerUsername, "@$ownerUsername started a chat.")
        getConversation(createdId)!!
    }

    fun getConversationMessagesQuery(conversationId: Int, beforeTime: Long = System.currentTimeMillis(), limit: Long = 25): Query<Message> {
        return database.conversationMessageQueries.getConversationMessages(conversationId, beforeTime, limit) { conversationId, id, authorId, text, dateSent ->
            Message(
                conversationId = conversationId,
                id = id,
                author = users.getUserQuery(authorId).executeAsOne(),
                text = text,
                time = dateSent,
            )
        }
    }

    fun getConversationMessagesFlow(conversationId: Int, beforeTime: Long = System.currentTimeMillis(), limit: Long = 15) =
        getConversationMessagesQuery(conversationId, beforeTime, limit).asFlow().mapToList(Dispatchers.IO)

    suspend fun getMessage(messageId: Int): Message? = withContext(Dispatchers.IO) {
        getMessageQuery(messageId).executeAsOneOrNull()
    }

    fun getMessageQuery(messageId: Int): Query<Message> {
        return database.conversationMessageQueries.getMessage(messageId) {
                conversationId, id, authorId, text, dateSent ->

            Message(
                conversationId = conversationId,
                id = id,
                author = users.getUserQuery(authorId).executeAsOne(),
                text = text,
                time = dateSent
            )
        }
    }

    suspend fun insertMessage(conversationId: Int, username: String, text: String) = withContext(Dispatchers.IO) {
        database.conversationMessageQueries.insertMessage(
            conversationId,
            username,
            unnaughtifyText(text),
            System.currentTimeMillis(),
        )
    }

    suspend fun removeMessage(messageId: Int) = withContext(Dispatchers.IO) {
        database.conversationMessageQueries.deleteMessage(messageId)
    }

    fun getLastConversationMessageQuery(conversationId: Int) = database.conversationMessageQueries
        .getMostRecentConversationMessage(conversationId) { _, id, authorId, text, dateSent ->
            Message(
                conversationId = conversationId,
                id = id,
                author = users.getUserQuery(authorId).executeAsOne(),
                text = text,
                time = dateSent
            )
        }

    fun getLastConversationMessage(conversationId: Int) = getLastConversationMessageQuery(conversationId)
        .asFlow()
        .mapToList(Dispatchers.IO)

    fun getUserConversations(username: String) =
        database.conversationQueries.getParticipatedConversations(username) { id, name, hasDefaultName, ownerId, lastMessageId ->
            Conversation(
                id = id,
                name = name,
                owner = users.getUserQuery(ownerId).executeAsOne(),
                lastMessage = lastMessageId?.let { getMessageQuery(it).executeAsOneOrNull() }
            )
        }.asFlow().mapToList(Dispatchers.IO)

    fun generateGroupName(members: List<User>): String {
        return members.joinToString(", ") { it.displayName }
    }

    private fun unnaughtifyText(message: String): String {
        // Replaces a profane substring with asterisks.
        return message.replace(profaneRegex) {
            "*".repeat(it.value.length)
        }
    }


    companion object {
        val Bots = listOf("BiggestSECFan")
    }

    private fun ensureBotUserExists() {
        val bot = database.userQueries.getUser("BiggestSECFan").executeAsOneOrNull()
        if (bot == null) {
            database.userQueries.insertUser("BiggestSECFan", "[Bot] Biggest SEC Fan", "")
        }
    }

    suspend fun sendWelcomeMessage(newUser: User) = withContext(Dispatchers.IO) {
        ensureBotUserExists()
        val convo = createConversation("BiggestSECFan", "Welcome to SEC Chat!", false)
        addUserToConversation(convo.id, newUser.username)
        insertMessage(convo.id, "BiggestSECFan", "Welcome to the club ${newUser.displayName}!")
    }
}
