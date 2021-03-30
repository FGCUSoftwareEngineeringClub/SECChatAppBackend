package routes

import database.ChatDatabase
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import respondWithMessage

@OptIn(KtorExperimentalLocationsAPI::class)
fun Route.UserRoutes() {
    @Serializable
    data class RegistrationParameters(
        val username: String? = null,
        val password: String? = null,
        val displayName: String? = null,
    )

    post("/register") {
        val body = try {
            call.receiveOrNull<RegistrationParameters>()
        } catch (err: SerializationException) {
            null
        }

        if (body == null) {
            call.respondWithMessage("No parameters were provided.", HttpStatusCode.BadRequest)
            return@post
        }

        val (username, password, displayName) = body

        if (username.isNullOrBlank() || password.isNullOrBlank() || displayName.isNullOrBlank()) {
            call.respondWithMessage(
                message = "A required parameter was missing. (username, password, or display name)",
                status = HttpStatusCode.BadRequest
            )
        } else {
            val preexistingUser = ChatDatabase.users.getUser(username)

            if (preexistingUser != null) {
                call.respondWithMessage("A user with that name already exists.", HttpStatusCode.BadRequest)
            } else {
                val newUser = ChatDatabase.users.insertUser(username, displayName, password)
                call.respond(HttpStatusCode.Created, newUser)
                ChatDatabase.conversations.sendWelcomeMessage(newUser)
            }
        }
    }

    @Location("/me")
    class me

    @Location("/{username}")
    data class user(val username: String)

    @Location("/users")
    class users

    authenticate {
        get<me>() {
            val userInfo = context.principal<UserIdPrincipal>()

            val user = if (userInfo != null) ChatDatabase.users.getUser(userInfo.name) else null

            if (user == null) {
                call.respondWithMessage("User not found", HttpStatusCode.NotFound)
            } else {
                call.respond(user)
            }
        }

        get<user>() { user ->
            val username = user.username

            if (username.isNullOrBlank()) {
                call.respondWithMessage("No user was specified", HttpStatusCode.BadRequest)
            } else {
                val user = ChatDatabase.users.getUser(username)
                if (user == null) {
                    call.respondWithMessage("User not found", HttpStatusCode.NotFound)
                } else {
                    call.respond(user)
                }
            }
        }

        get<users>() {
            val users = ChatDatabase.users.getAllUsers().first()
            call.respond(users)
        }
    }
}