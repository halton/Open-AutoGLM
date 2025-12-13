package com.openautoglm.agent.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray

/**
 * Represents a chat message compatible with OpenAI Chat Completions API format.
 *
 * @property role The role of the message sender (system, user, or assistant)
 * @property content The message content, which can be a simple string or a list of content parts
 */
@Serializable
data class ChatMessage(
    val role: ChatRole,
    @Serializable(with = MessageContentSerializer::class)
    val content: MessageContent
) {
    companion object {
        /**
         * Creates a system message with text content.
         */
        fun system(content: String): ChatMessage =
            ChatMessage(role = ChatRole.SYSTEM, content = MessageContent.Text(content))

        /**
         * Creates a user message with text content.
         */
        fun user(content: String): ChatMessage =
            ChatMessage(role = ChatRole.USER, content = MessageContent.Text(content))

        /**
         * Creates a user message with multimodal content (text and images).
         */
        fun user(parts: List<ContentPart>): ChatMessage =
            ChatMessage(role = ChatRole.USER, content = MessageContent.Parts(parts))

        /**
         * Creates an assistant message with text content.
         */
        fun assistant(content: String): ChatMessage =
            ChatMessage(role = ChatRole.ASSISTANT, content = MessageContent.Text(content))
    }
}

/**
 * Enum representing the role of a message sender in a chat conversation.
 */
@Serializable
enum class ChatRole {
    @SerialName("system")
    SYSTEM,

    @SerialName("user")
    USER,

    @SerialName("assistant")
    ASSISTANT
}

/**
 * Sealed class representing message content that can be either a simple string
 * or a list of content parts (for multimodal messages).
 */
@Serializable(with = MessageContentSerializer::class)
sealed class MessageContent {
    /**
     * Simple text content.
     */
    @Serializable
    data class Text(val text: String) : MessageContent()

    /**
     * Multimodal content consisting of multiple parts (text, images, etc.).
     */
    @Serializable
    data class Parts(val parts: List<ContentPart>) : MessageContent()

    /**
     * Returns the text content if this is a Text message, or concatenates
     * all text parts if this is a Parts message.
     */
    fun asText(): String = when (this) {
        is Text -> text
        is Parts -> parts.filterIsInstance<ContentPart.TextPart>()
            .joinToString("") { it.text }
    }
}

/**
 * Sealed class representing a single part of multimodal content.
 */
@Serializable
sealed class ContentPart {
    /**
     * Text content part.
     */
    @Serializable
    @SerialName("text")
    data class TextPart(
        val type: String = "text",
        val text: String
    ) : ContentPart()

    /**
     * Image content part with URL reference.
     */
    @Serializable
    @SerialName("image_url")
    data class ImageUrlPart(
        val type: String = "image_url",
        @SerialName("image_url")
        val imageUrl: ImageUrl
    ) : ContentPart()

    companion object {
        /**
         * Creates a text content part.
         */
        fun text(content: String): ContentPart = TextPart(text = content)

        /**
         * Creates an image URL content part.
         */
        fun imageUrl(url: String, detail: ImageDetail = ImageDetail.AUTO): ContentPart =
            ImageUrlPart(imageUrl = ImageUrl(url = url, detail = detail))

        /**
         * Creates an image content part from base64-encoded data.
         */
        fun imageBase64(base64Data: String, mediaType: String = "image/png", detail: ImageDetail = ImageDetail.AUTO): ContentPart =
            ImageUrlPart(imageUrl = ImageUrl(url = "data:$mediaType;base64,$base64Data", detail = detail))
    }
}

/**
 * Represents an image URL with optional detail level for vision models.
 */
@Serializable
data class ImageUrl(
    val url: String,
    val detail: ImageDetail = ImageDetail.AUTO
)

/**
 * Enum representing the detail level for image processing.
 */
@Serializable
enum class ImageDetail {
    @SerialName("auto")
    AUTO,

    @SerialName("low")
    LOW,

    @SerialName("high")
    HIGH
}

/**
 * Custom serializer for MessageContent that handles both string and array formats.
 *
 * When serializing:
 * - Text content is serialized as a plain string
 * - Parts content is serialized as an array of content parts
 *
 * When deserializing:
 * - A JSON string becomes Text content
 * - A JSON array becomes Parts content
 */
object MessageContentSerializer : JsonContentPolymorphicSerializer<MessageContent>(MessageContent::class) {
    override fun selectDeserializer(element: JsonElement) = when {
        element is JsonPrimitive && element.isString -> MessageContent.Text.serializer()
        element.jsonArray != null -> MessageContent.Parts.serializer()
        else -> throw IllegalArgumentException("Unknown MessageContent format: $element")
    }
}
