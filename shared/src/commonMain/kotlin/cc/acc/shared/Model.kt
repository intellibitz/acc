package cc.acc.shared

import kotlinx.serialization.Serializable

@Serializable
data class AIModel(
    val provider: String,
    val name: String,
    val repo: String,
    val filePattern: String,
    val tier: String,
    val quant: String,
    val superpower: String,
    val source: String,
    val isPrivate: Boolean = false
)
