    @file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.tenmilelabs.application.dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class HomeLayoutResponse(
    val schemaVersion: String,
    val layoutChecksum: String,
    val components: List<HomeComponent>,
    val sidecar: HomeSidecar? = null,
)

@Serializable
data class HomeSidecar(
    val recipes: List<SidecarRecipeDto>,
    val tags: List<SidecarTagDto> = emptyList(),
    val labels: List<SidecarLabelDto> = emptyList(),
    val creators: List<SidecarCreatorDto> = emptyList(),
)

@Serializable
data class SidecarRecipeDto(
    val uuid: String,
    val title: String,
    val description: String,
    val imageUrl: String,
    val imageUrlThumbnail: String,
    val prepTimeMinutes: Int,
    val cookTimeMinutes: Int,
    val servings: Int,
    val creatorId: String,
    val privacy: String = "PUBLIC",
    val updatedAt: Long,
    val tagIds: List<String> = emptyList(),
    val labelIds: List<String> = emptyList(),
)

@Serializable
data class SidecarTagDto(val uuid: String, val displayName: String)

@Serializable
data class SidecarLabelDto(val uuid: String, val displayName: String)

@Serializable
data class SidecarCreatorDto(
    val uuid: String,
    val displayName: String,
    val avatarUrl: String? = null,
)

@Serializable(with = HomeComponentSerializer::class)
sealed interface HomeComponent {
    val id: String
    val type: String
}

@Serializable
data class SectionHeaderComponent(
    override val id: String,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val type: String = SECTION_HEADER_TYPE,
    val title: String,
    val subtitle: String? = null,
    val actionText: String? = null,
    val actionUrl: String? = null,
) : HomeComponent

@Serializable
data class CarouselComponent(
    override val id: String,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val type: String = CAROUSEL_TYPE,
    val items: List<HomeComponent>,
) : HomeComponent

@Serializable
data class LargeCardComponent(
    override val id: String,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val type: String = LARGE_CARD_TYPE,
    val recipeId: String? = null,
    val title: String,
    val description: String? = null,
    val imageUrl: String? = null,
    val prepTimeMinutes: Int? = null,
    val cookTimeMinutes: Int? = null,
    val servings: Int? = null,
    val labels: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
) : HomeComponent

@Serializable
data class SquaredCardComponent(
    override val id: String,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val type: String = SQUARED_CARD_TYPE,
    val recipeId: String? = null,
    val title: String,
    val imageUrl: String? = null,
    val subtitle: String? = null,
    val tag: String?,
) : HomeComponent

@Serializable
data class ListCardComponent(
    override val id: String,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val type: String = LIST_CARD_TYPE,
    val recipeId: String? = null,
    val title: String,
    val description: String? = null,
    val imageUrl: String? = null,
    val prepTimeMinutes: Int? = null,
    val cookTimeMinutes: Int? = null,
    val labels: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
) : HomeComponent

@Serializable
data class UnknownHomeComponent(
    override val id: String,
    override val type: String,
) : HomeComponent

object HomeComponentSerializer : KSerializer<HomeComponent> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("HomeComponent")

    override fun serialize(encoder: Encoder, value: HomeComponent) {
        when (value) {
            is SectionHeaderComponent ->
                encoder.encodeSerializableValue(SectionHeaderComponent.serializer(), value)

            is CarouselComponent ->
                encoder.encodeSerializableValue(CarouselComponent.serializer(), value)

            is LargeCardComponent ->
                encoder.encodeSerializableValue(LargeCardComponent.serializer(), value)

            is SquaredCardComponent ->
                encoder.encodeSerializableValue(SquaredCardComponent.serializer(), value)

            is ListCardComponent ->
                encoder.encodeSerializableValue(ListCardComponent.serializer(), value)

            is UnknownHomeComponent ->
                encoder.encodeSerializableValue(UnknownHomeComponent.serializer(), value)
        }
    }

    override fun deserialize(decoder: Decoder): HomeComponent {
        val input = decoder as? JsonDecoder
            ?: throw SerializationException("HomeComponentSerializer only supports JSON")

        val element = input.decodeJsonElement()
        val obj = element as? JsonObject ?: return UnknownHomeComponent(
            id = UNKNOWN_COMPONENT_ID,
            type = UNKNOWN_COMPONENT_TYPE,
        )

        val type = obj.optionalString("type") ?: UNKNOWN_COMPONENT_TYPE
        val fallbackId = obj.optionalString("id") ?: UNKNOWN_COMPONENT_ID

        return try {
            when (type) {
                SECTION_HEADER_TYPE ->
                    input.json.decodeFromJsonElement(SectionHeaderComponent.serializer(), element)

                CAROUSEL_TYPE ->
                    input.json.decodeFromJsonElement(CarouselComponent.serializer(), element)

                LARGE_CARD_TYPE ->
                    input.json.decodeFromJsonElement(LargeCardComponent.serializer(), element)

                SQUARED_CARD_TYPE ->
                    decodeSquaredCard(obj)

                LIST_CARD_TYPE ->
                    input.json.decodeFromJsonElement(ListCardComponent.serializer(), element)

                else -> UnknownHomeComponent(id = fallbackId, type = type)
            }
        } catch (_: SerializationException) {
            UnknownHomeComponent(id = fallbackId, type = type)
        } catch (_: IllegalArgumentException) {
            UnknownHomeComponent(id = fallbackId, type = type)
        }
    }

    private fun decodeSquaredCard(component: JsonObject): SquaredCardComponent {
        val id = component.requiredString("id", SQUARED_CARD_TYPE)
        val title = component.requiredString("title", SQUARED_CARD_TYPE)
        return SquaredCardComponent(
            id = id,
            recipeId = component.optionalString("recipeId"),
            title = title,
            imageUrl = component.optionalString("imageUrl"),
            subtitle = component.optionalString("subtitle"),
            tag = component.optionalString("tag"),
        )
    }
}

private fun JsonObject.requiredString(name: String, componentType: String): String =
    optionalString(name)
        ?: throw SerializationException("$componentType is missing required field '$name'")

private fun JsonObject.optionalString(name: String): String? =
    this[name]?.jsonPrimitive?.contentOrNull

const val SECTION_HEADER_TYPE = "section_header"
const val CAROUSEL_TYPE = "carousel"
const val LARGE_CARD_TYPE = "large_card"
const val SQUARED_CARD_TYPE = "squared_card"
const val LIST_CARD_TYPE = "list_card"

private const val UNKNOWN_COMPONENT_TYPE = "unknown"
private const val UNKNOWN_COMPONENT_ID = "unknown"
