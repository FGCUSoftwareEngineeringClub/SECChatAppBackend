package model

import kotlinx.serialization.Serializable

@Serializable
data class Conversation(
    val id: Int,
    val name: String,
    val owner: User,
    val lastMessage: Message?
)