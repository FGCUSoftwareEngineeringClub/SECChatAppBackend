import database.ChatDatabase
import database.ConversationRepository
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.locations.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.serialization.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import org.slf4j.event.Level
import routes.ChatRoutes
import routes.UserRoutes

val ChatJson = Json {
    encodeDefaults = true
    isLenient = true
    allowSpecialFloatingPointValues = true
    allowStructuredMapKeys = true
    prettyPrint = false
    useArrayPolymorphism = true
    ignoreUnknownKeys = true
}

suspend fun loginUser(username: String, password: String): UserIdPrincipal? {
    if (username.isBlank() || password.isBlank()) return null

    if (username in ConversationRepository.Bots) return null

    val user = ChatDatabase.users.isValidLogin(username, password)
    return if (user == null) {
        null
    } else {
        UserIdPrincipal(user.username)
    }
}

fun main() {
    embeddedServer(Netty, port = System.getenv("PORT")?.toInt() ?: 8080) {
        install(CallLogging) {
            level = Level.INFO
        }

        install(ContentNegotiation) {
            json(ChatJson)
        }

        install(Authentication) {
            basic() {
                validate { credentials ->
                    loginUser(credentials.name, credentials.password)
                }
            }
        }
        install(Locations)

        install(WebSockets)

        routing {
            get("/") {
                call.respondRedirect("https://github.com/FGCUSoftwareEngineeringClub/SECChatAppBackend")
            }
            route("/api/v1") {
                route("/user") { UserRoutes() }
                route("/chat") { ChatRoutes() }
            }
        }
    }.start(wait = true)
}