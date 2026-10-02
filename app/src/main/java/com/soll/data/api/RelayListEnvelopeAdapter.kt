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
