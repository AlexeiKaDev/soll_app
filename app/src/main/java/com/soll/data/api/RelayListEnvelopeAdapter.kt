package com.soll.data.api

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * The production relay (sales.monolith-ost.com) wraps collection responses in
 * `{"data": [...]}` for some list endpoints (e.g. GET sources, GET
 * sources/{id}/items) while returning a bare JSON array for others. Accept
 * either shape so the client stays correct regardless of which wrapping the
 * deployed relay version is currently using.
 */
private class RelayListEnvelopeAdapter<T>(
    private val elementAdapter: JsonAdapter<T>,
) : JsonAdapter<List<T>>() {
    override fun fromJson(reader: JsonReader): List<T>? {
        return when (reader.peek()) {
            JsonReader.Token.NULL -> reader.nextNull()
            JsonReader.Token.BEGIN_ARRAY -> readArray(reader)
            JsonReader.Token.BEGIN_OBJECT -> {
                var result: List<T> = emptyList()
                reader.beginObject()
                while (reader.hasNext()) {
                    if (reader.nextName() == "data" && reader.peek() == JsonReader.Token.BEGIN_ARRAY) {
                        result = readArray(reader)
                    } else {
                        reader.skipValue()
                    }
                }
                reader.endObject()
                result
            }
            else -> {
                reader.skipValue()
                emptyList()
            }
        }
    }

    private fun readArray(reader: JsonReader): List<T> {
        val items = mutableListOf<T>()
        reader.beginArray()
        while (reader.hasNext()) {
            elementAdapter.fromJson(reader)?.let(items::add)
        }
        reader.endArray()
        return items
    }

    override fun toJson(writer: JsonWriter, value: List<T>?) {
        writer.beginArray()
        value?.forEach { elementAdapter.toJson(writer, it) }
        writer.endArray()
    }
}

object RelayListEnvelopeAdapterFactory : JsonAdapter.Factory {
    private val envelopedElementTypes = setOf(
        MonitoredSourceResponse::class.java,
        SourceItemResponse::class.java,
    )

    override fun create(type: Type, annotations: MutableSet<out Annotation>, moshi: Moshi): JsonAdapter<*>? {
        if (annotations.isNotEmpty()) return null
        if (Types.getRawType(type) != List::class.java) return null
        val elementType = (type as? ParameterizedType)?.actualTypeArguments?.getOrNull(0) ?: return null
        if (elementType !in envelopedElementTypes) return null
        val elementAdapter = moshi.nextAdapter<Any>(this, elementType, emptySet())
        return RelayListEnvelopeAdapter(elementAdapter).nullSafe()
    }
}

/**
 * The relay's normalizeSourceItem() defaults a missing/non-array link_preview
 * to PHP's `[]`, which json_encode emits as a JSON *array* (`[]`), not an
 * object -- PHP has no distinct empty-map literal. Every map-typed field
 * (Map<String, Any?>, used for link_preview, metadata, payload, etc.
 * throughout this API) must tolerate that shape instead of Moshi's default
 * Map adapter throwing "Expected BEGIN_OBJECT but was BEGIN_ARRAY", which
 * silently failed to parse an item's entire containing list in practice
 * (e.g. every item for a source whose items all lack a real link preview).
 */
private class TolerantMapAdapter(
    private val delegate: JsonAdapter<Map<String, Any?>>,
) : JsonAdapter<Map<String, Any?>>() {
    override fun fromJson(reader: JsonReader): Map<String, Any?>? {
        if (reader.peek() == JsonReader.Token.BEGIN_ARRAY) {
            reader.skipValue()
            return emptyMap()
        }
        return delegate.fromJson(reader)
    }

    override fun toJson(writer: JsonWriter, value: Map<String, Any?>?) {
        delegate.toJson(writer, value)
    }
}

object TolerantMapAdapterFactory : JsonAdapter.Factory {
    override fun create(type: Type, annotations: MutableSet<out Annotation>, moshi: Moshi): JsonAdapter<*>? {
        if (annotations.isNotEmpty()) return null
        if (Types.getRawType(type) != Map::class.java) return null
        val args = (type as? ParameterizedType)?.actualTypeArguments ?: return null
        if (args.size != 2 || args[0] != String::class.java) return null
        @Suppress("UNCHECKED_CAST")
        val delegate = moshi.nextAdapter<Map<String, Any?>>(this, type, emptySet())
        return TolerantMapAdapter(delegate).nullSafe()
    }
}
