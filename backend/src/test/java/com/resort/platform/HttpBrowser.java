package com.resort.platform;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Navegador mínimo sobre HTTP real: cookies e CSRF como o frontend faz. */
public class HttpBrowser {

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private final String baseUrl;
    private final Map<String, String> cookies = new LinkedHashMap<>();

    public HttpBrowser(int port) {
        this.baseUrl = "http://localhost:" + port;
    }

    public HttpResponse<String> get(String path) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET(), false);
    }

    public HttpResponse<String> post(String path, String json) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
        return send(builder, true);
    }

    public HttpResponse<String> patch(String path, String json) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .method("PATCH", json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
        return send(builder, true);
    }

    public HttpResponse<String> upload(String path, String filename, byte[] content) throws Exception {
        String boundary = "----teste" + System.nanoTime();
        byte[] head = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + filename
                        + "\"\r\nContent-Type: text/csv\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] body = new byte[head.length + content.length + tail.length];
        System.arraycopy(head, 0, body, 0, head.length);
        System.arraycopy(content, 0, body, head.length, content.length);
        System.arraycopy(tail, 0, body, head.length + content.length, tail.length);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        return send(builder, true);
    }

    public String cookie(String name) {
        return cookies.get(name);
    }

    public void setCookie(String name, String value) {
        cookies.put(name, value);
    }

    public static List<String> setCookieHeaders(HttpResponse<?> response, String name) {
        return response.headers().allValues("Set-Cookie").stream()
                .filter(header -> header.startsWith(name + "="))
                .toList();
    }

    private HttpResponse<String> send(HttpRequest.Builder builder, boolean unsafe) throws Exception {
        if (unsafe && !cookies.containsKey("XSRF-TOKEN")) {
            get("/api/auth/me");
        }
        if (!cookies.isEmpty()) {
            builder.header("Cookie", cookies.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining("; ")));
        }
        if (unsafe && cookies.containsKey("XSRF-TOKEN")) {
            builder.header("X-XSRF-TOKEN", cookies.get("XSRF-TOKEN"));
        }
        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        for (String header : response.headers().allValues("Set-Cookie")) {
            String nameValue = header.split(";", 2)[0];
            int separator = nameValue.indexOf('=');
            String name = nameValue.substring(0, separator);
            String value = nameValue.substring(separator + 1);
            if (value.isEmpty() || header.toLowerCase().contains("max-age=0")) {
                cookies.remove(name);
            } else {
                cookies.put(name, value);
            }
        }
        return response;
    }
}
