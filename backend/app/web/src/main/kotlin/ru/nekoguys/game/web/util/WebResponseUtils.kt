package ru.nekoguys.game.web.util

import com.fasterxml.jackson.annotation.JsonIgnore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.ServerSentEvent

open class WebResponse(
    @get:JsonIgnore
    val status: HttpStatus
)

fun <T : Any> Flow<T>.asServerSentEventStream(
    streamName: String,
): Flow<ServerSentEvent<T>> =
    map { ServerSentEvent.builder(it).id(streamName).build() }
        .withRequestIdInContext()

suspend fun <T : WebResponse> wrapServiceCall(block: suspend () -> T?): ResponseEntity<T> {
    return withMDCContext {
        @Suppress("DEPRECATION")
        block().toResponseEntity()
    }
}

@Deprecated(
    message = "It's probably better to use `wrapServiceCall` there",
    replaceWith = ReplaceWith(""),
    level = DeprecationLevel.WARNING,
)
fun <T : WebResponse> T?.toResponseEntity(
    ifEmpty: HttpStatus = HttpStatus.NOT_FOUND,
): ResponseEntity<T> =
    if (this != null) {
        ResponseEntity.status(status).body(this)
    } else {
        ResponseEntity.status(ifEmpty).build()
    }
