package io.jenkins.plugins.globaltelegrambuildnotifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class TelegramClientTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void postsFormBodyWithoutTokenInBodyAndParsesSuccess(JenkinsRule jenkins) throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        startServer(200, "{\"ok\":true}", body);
        GlobalTelegramBuildNotifierConfiguration config = configForServer();

        TelegramResponse response = new TelegramClient().send(
                new TelegramRequest("-100123", "42", "hello world"),
                "123:SECRET",
                config);

        assertTrue(response.isSuccessful());
        assertTrue(body.get().contains("chat_id=-100123"));
        assertTrue(body.get().contains("message_thread_id=42"));
        assertTrue(body.get().contains("text=hello+world"));
        assertTrue(!body.get().contains("123:SECRET"));
    }

    @Test
    void parsesRetryAfterFrom429(JenkinsRule jenkins) throws Exception {
        startServer(429, "{\"parameters\":{\"retry_after\":10}}", new AtomicReference<>());
        TelegramResponse response = new TelegramClient().send(
                new TelegramRequest("-100123", null, "hello"),
                "123:SECRET",
                configForServer());

        assertEquals(429, response.getStatusCode());
        assertEquals(10, response.getRetryAfterSeconds());
    }

    @Test
    void handlesNonJsonErrorResponse(JenkinsRule jenkins) throws Exception {
        startServer(500, "upstream unavailable", new AtomicReference<>());
        TelegramResponse response = new TelegramClient().send(
                new TelegramRequest("-100123", null, "hello"),
                "123:SECRET",
                configForServer());

        assertEquals(500, response.getStatusCode());
    }

    private void startServer(int status, String response, AtomicReference<String> requestBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/bot123:SECRET/sendMessage", exchange -> handle(exchange, status, response, requestBody));
        server.start();
    }

    private void handle(HttpExchange exchange, int status, String response, AtomicReference<String> requestBody)
            throws IOException {
        requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private GlobalTelegramBuildNotifierConfiguration configForServer() {
        GlobalTelegramBuildNotifierConfiguration config = new GlobalTelegramBuildNotifierConfiguration();
        config.setTelegramApiUrl("http://127.0.0.1:" + server.getAddress().getPort());
        config.setConnectTimeoutMillis(500);
        config.setReadTimeoutMillis(500);
        return config;
    }
}
