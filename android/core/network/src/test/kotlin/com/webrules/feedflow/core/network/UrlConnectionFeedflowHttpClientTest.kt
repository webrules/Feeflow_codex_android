package com.webrules.feedflow.core.network

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.Charset
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UrlConnectionFeedflowHttpClientTest {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val baseUrl: String get() = "http://127.0.0.1:${server.address.port}"

    @AfterTest fun tearDown() {
        server.stop(0)
    }

    @Test fun getSendsMatchingCookieHeaderAndDecodesCharset() = runBlocking {
        server.createContext("/gb") { exchange ->
            val cookie = exchange.requestHeaders.getFirst("Cookie").orEmpty()
            val body = "cookie=$cookie; body=中文".toByteArray(Charset.forName("GB18030"))
            exchange.responseHeaders.add("Content-Type", "text/plain; charset=GB18030")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()

        val response = UrlConnectionFeedflowHttpClient().get(
            "$baseUrl/gb",
            listOf(FeedflowCookie("sid", "abc", "127.0.0.1", "/gb", expiresAtMillis = null)),
        )
        assertTrue(response.contains("cookie=sid=abc"))
        assertTrue(response.contains("中文"))
    }

    @Test fun postWritesFormBodyAndThrowsForHttpErrors() = runBlocking {
        server.createContext("/post") { exchange ->
            val body = exchange.requestBody.bufferedReader().readText()
            val response = "method=${exchange.requestMethod}; type=${exchange.requestHeaders.getFirst("Content-Type")}; body=$body".toByteArray()
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.createContext("/fail") { exchange ->
            val response = "bad".toByteArray()
            exchange.sendResponseHeaders(503, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()

        assertEquals(
            "method=POST; type=application/x-www-form-urlencoded; charset=UTF-8; body=a=1",
            UrlConnectionFeedflowHttpClient().post("$baseUrl/post", "a=1"),
        )
        val error = assertFailsWith<HttpStatusException> {
            UrlConnectionFeedflowHttpClient().get("$baseUrl/fail")
        }
        assertEquals(503, error.statusCode)
        assertEquals("bad", error.bodyPreview)
    }
}
