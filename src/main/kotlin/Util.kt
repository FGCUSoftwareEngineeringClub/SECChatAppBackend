import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import kotlinx.serialization.Serializable

suspend fun ApplicationCall.respondNull(status: HttpStatusCode = HttpStatusCode.NotFound) = respondText(
    text = "null",
    contentType = ContentType.Application.Json,
    status = status,
)

@Serializable
data class Error(
    val message: String,
    val code: Int
)

suspend fun ApplicationCall.respondWithMessage(message: String, status: HttpStatusCode) {
    val error = Error(message, code = status.value)
    respond(status, error)
}