package routes

import ChatJson
import database.ChatDatabase
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.sessions.*
import io.ktor.util.pipeline.*
import io.ktor.websocket.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import loginUser
import model.Conversation
import model.User
import respondWithMessage
import java.util.*

suspend fun DefaultWebSocketServerSession.awaitAuthentication() {
//    this.outgoing.send(Frame.Text("Awaiting login..."))
    val first = this.incoming.receive() as? Frame.Text

    if (first == null) {
        this.outgoing.send(Frame.Text("Invalid authentication"))
        this.close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Invalid authentication"))
        return
    }

    val params = Base64.getDecoder().decode(first.readText()).decodeToString().split(":")

    if (params.size != 2) {
        this.outgoing.send(Frame.Text("Invalid authentication"))
        this.close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Invalid authentication"))
        return
    }

    val username = params[0]
    val password = params[1]

    val creds = loginUser(username, password)

    if (creds == null) {
        this.outgoing.send(Frame.Text("Invalid authentication"))
        this.close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Invalid authentication"))
        return
    }

    call.authentication.principal = creds
}

fun Route.ChatRoutes() {
    webSocket("/recent") {
        awaitAuthentication()
        val user = this.call.principal<UserIdPrincipal>()!!.name
        ChatDatabase.conversations.getUserConversations(user).collect { conversations ->
            println("recent update!")
            if (!outgoing.isClosedForSend) {
                outgoing.send(Frame.Text(ChatJson.encodeToString(conversations)))
            }
        }
    }

    webSocket("/thread/{threadId}/messages") {
        awaitAuthentication()
        call.withConversation { conversation ->
            ChatDatabase.conversations.getConversationMessagesFlow(conversation.id)
                .collect { messages ->
                    println("message update!")
                    if (!outgoing.isClosedForSend) {
                        outgoing.send(Frame.Text(ChatJson.encodeToString(messages)))
                    }
                }
        }
    }

    authenticate {
        get("/recent") {
            val user = context.principal<UserIdPrincipal>()!!.name
            val threads = ChatDatabase.conversations.getUserConversations(user).first()

            call.respond(threads)
        }

        route("/thread") {
            post {
                val username = call.principal<UserIdPrincipal>()!!.name
                val name = call.request.queryParameters["name"]
                val partnerUsernames = call.request.queryParameters["partners"]
                if (partnerUsernames.isNullOrBlank()) {
                    call.respondWithMessage("No partners were specified", HttpStatusCode.BadRequest)
                } else {
                    val partners = partnerUsernames.split(" ")
                        .filter { it.isNotBlank() && it != username}
                        .mapNotNull {
                            ChatDatabase.users.getUser(it)
                        }

                    if (partners.isEmpty()) {
                        call.respondWithMessage("The specified partners could not be found.", HttpStatusCode.BadRequest)
                        return@post
                    }

                    val conversationName = when {
                        name != null -> name
                        partners.size == 1 -> partners.first().displayName
                        else -> ChatDatabase.conversations.generateGroupName(partners + call.currentUser())
                    }

                    val convo = ChatDatabase.conversations.createConversation(username, conversationName, name == null)

                    partners.map {
                        async {
                            ChatDatabase.conversations.addUserToConversation(convo.id, it.username)
                        }
                    }.awaitAll()

                    call.respond(HttpStatusCode.Created, convo)
                }
            }

            route("/{threadId}") {
                get {
                    call.withConversation { conversation ->
                        call.respond(conversation)
                    }
                }

                get("/messages") {
                    call.withConversation {
                        call.respond(ChatDatabase.conversations.getConversationMessagesFlow(it.id).first())
                    }
                }

                get("/members") {
                    call.withConversation { conversation ->
                        call.respond(ChatDatabase.conversations.getConversationMembers(conversation.id))
                    }
                }

                post("/members/{username}") {
                    call.withConversation { conversation ->
                        val memberUsername = call.parameters["username"]
                        if (memberUsername == null || ChatDatabase.users.getUser(memberUsername) == null) {
                            call.respondWithMessage("User does not exist", HttpStatusCode.NotFound)
                        } else {
                            ChatDatabase.conversations.addUserToConversation(conversation.id, memberUsername)
                            call.respondWithMessage("User added to ${conversation.name}", HttpStatusCode.OK)
                        }
                    }
                }

                delete("/members/{username}") {
                    call.withConversation { convo ->
                        val memberUsername = call.parameters["username"]
                        if (memberUsername == null) {
                            call.respondWithMessage("No username was provided", HttpStatusCode.BadRequest)
                        } else {
                            ChatDatabase.conversations.removeUserFromConversation(convo.id, memberUsername)
                            call.respondWithMessage("User removed from ${convo.name}", HttpStatusCode.OK)
                        }
                    }
                }

                post("/message") {
                    val text = call.parameters["text"]
                    if (text.isNullOrBlank()) {
                        call.respondWithMessage("No message provided", HttpStatusCode.BadRequest)
                        return@post
                    }

                    call.withConversation {
                        ChatDatabase.conversations.insertMessage(it.id, call.principal<UserIdPrincipal>()!!.name, text)
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }
        }
    }
}

suspend fun ApplicationCall.currentUser(): User {
    val username = principal<UserIdPrincipal>()!!.name
    val name = request.queryParameters["name"]
    return ChatDatabase.users.getUser(username)!!
}

suspend fun ApplicationCall.withConversation(
    block: suspend (conversation: Conversation) -> Unit
) {
    val threadIdStr = parameters["threadId"]
    val threadId = threadIdStr?.toIntOrNull()

    if (threadId == null) {
        respondWithMessage("Thread not found", HttpStatusCode.NotFound)
        return
    }

    val conversation = ChatDatabase.conversations.getConversation(threadId)
    val user = ChatDatabase.users.getUser(principal<UserIdPrincipal>()!!.name)
    val hasPermission = ChatDatabase.conversations.getConversationMembers(threadId).contains(user)

    if (conversation == null || !hasPermission) {
        respondWithMessage("Thread not found", HttpStatusCode.NotFound)
        return
    }

    block(conversation)
}