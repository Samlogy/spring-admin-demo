package com.example.admin;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-process HTTP stub (JDK HttpServer, no extra dependency) playing two roles in tests:
 * <ul>
 * <li>an OIDC issuer, so the oidc profiles can run their real discovery ({@code issuer-uri}) without an IdP;</li>
 * <li>a monolith Actuator, so the Admin Server proxy has something to call.</li>
 * </ul>
 */
public final class StubServer implements AutoCloseable {

    /** Requests received on /actuator/**: "METHOD path Authorization-header". */
    public final List<String> actuatorRequests = new CopyOnWriteArrayList<>();

    private final HttpServer server;

    public StubServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        }
        catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        server.createContext("/.well-known/openid-configuration", exchange -> json(exchange, 200, """
                {"issuer":"%1$s","authorization_endpoint":"%1$s/authorize","token_endpoint":"%1$s/token",
                 "jwks_uri":"%1$s/jwks","userinfo_endpoint":"%1$s/userinfo","end_session_endpoint":"%1$s/logout",
                 "response_types_supported":["code"],"subject_types_supported":["public"],
                 "id_token_signing_alg_values_supported":["RS256"]}""".formatted(url())));
        server.createContext("/actuator", exchange -> {
            String path = exchange.getRequestURI().getPath();
            actuatorRequests.add(exchange.getRequestMethod() + " " + path + " "
                    + exchange.getRequestHeaders().getFirst("Authorization"));
            if (path.equals("/actuator") || path.equals("/actuator/")) {
                StringBuilder links = new StringBuilder("{\"_links\":{");
                for (String id : List.of("health", "info", "loggers", "env", "heapdump")) {
                    links.append("\"%s\":{\"href\":\"%s/actuator/%s\"},".formatted(id, url(), id));
                }
                links.setLength(links.length() - 1);
                json(exchange, 200, links.append("}}").toString());
            }
            else if (path.startsWith("/actuator/health")) {
                json(exchange, 200, "{\"status\":\"UP\"}");
            }
            else if (exchange.getRequestMethod().equals("POST")) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
            }
            else {
                json(exchange, 200, "{}");
            }
        });
        server.start();
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static void json(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
