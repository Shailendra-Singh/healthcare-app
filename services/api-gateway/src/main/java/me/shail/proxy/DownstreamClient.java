package me.shail.proxy;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import me.shail.config.GatewayConfig;

/** Sends a request to one of the downstream services, over the internal API networks. */
@ApplicationScoped
public class DownstreamClient {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @Inject
    GatewayConfig config;

    /**
     * @param path  the path on the service, e.g. {@code /api/v1/tasks}
     * @param query the raw query string, or null
     * @param body  null for no body
     * @throws DownstreamUnavailableException when the service cannot be reached
     */
    public DownstreamResponse send(String service, String method, String path, String query, String contentType,
            byte[] body) {
        GatewayConfig.Service target = config.services().get(service);
        if (target == null) {
            throw new IllegalArgumentException("Unknown service: " + service);
        }
        URI uri = URI.create(target.url().toString().replaceAll("/+$", "") + path + (query == null || query.isEmpty() ? "" : "?" + query));
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(60))
                .header("Accept", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
        if (contentType != null && body != null) {
            request.header("Content-Type", contentType);
        }
        try {
            HttpResponse<byte[]> response = http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
            return new DownstreamResponse(response.statusCode(),
                    response.headers().firstValue("Content-Type").orElse(null), response.body());
        } catch (IOException e) {
            throw new DownstreamUnavailableException(service, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DownstreamUnavailableException(service, e);
        }
    }
}
