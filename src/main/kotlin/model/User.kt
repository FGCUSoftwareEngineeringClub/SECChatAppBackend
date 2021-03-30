package model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val username: String,
    val displayName: String,
)