package model

import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val conversationId: Int,
    val id: Int,
    val author: User,
    val text: String,
    val time: Long,
)