package com.perpus.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Properties;

public final class ApiClient {
    private static final String DEFAULT_API_URL =
            "https://riclbviiftvnoxvlmbrb.supabase.co/functions/v1/perpus-api";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Properties CONFIG = loadConfig();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static volatile String accessToken;

    private ApiClient() {}

    public static JsonNode get(String path) {
        return data("GET", path, null);
    }

    public static JsonNode post(String path, Object body) {
        return data("POST", path, body);
    }

    public static JsonNode put(String path, Object body) {
        return data("PUT", path, body);
    }

    public static JsonNode delete(String path) {
        return data("DELETE", path, null);
    }

    public static JsonNode data(String method, String path, Object body) {
        String baseUrl = configuredValue("PERPUS_API_URL", "api.url", DEFAULT_API_URL);
        baseUrl = baseUrl.trim().replaceAll("/+$", "");
        if (!baseUrl.startsWith("https://")) {
            throw new IllegalStateException("PERPUS_API_URL harus menggunakan HTTPS");
        }

        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/" + path.replaceAll("^/+", "")))
                .timeout(Duration.ofSeconds(25))
                .header("Accept", "application/json");
        String anonKey = configuredValue("SUPABASE_ANON_KEY", "api.anon-key", "");
        if (anonKey == null || anonKey.isBlank()) {
            throw new IllegalStateException("Kunci publishable Supabase belum dikonfigurasi. Isi api.anon-key dengan publishable/anon key, bukan service_role key.");
        }
        request.header("apikey", anonKey.trim());
        String token = accessToken;
        if (token != null && !token.isBlank()) {
            request.header("Authorization", "Bearer " + token);
        }

        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            try {
                request.header("Content-Type", "application/json; charset=utf-8");
                request.method(method, HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
            } catch (IOException ex) {
                throw new IllegalStateException("Gagal menyiapkan permintaan ke server", ex);
            }
        }

        try {
            HttpResponse<String> response = HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode result = JSON.readTree(response.body());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String message = result.path("error").path("message").asText("Server tidak dapat memproses permintaan");
                throw new ApiException(response.statusCode(), message);
            }
            return result.path("data");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Permintaan ke server terhenti", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("Tidak dapat terhubung ke server. Periksa koneksi internet Anda.", ex);
        }
    }

    public static JsonNode login(String username, String password) {
        accessToken = null;
        JsonNode response = data("POST", "auth/login", java.util.Map.of(
                "username", username,
                "password", password));
        String token = response.path("token").asText();
        if (token.isBlank()) throw new IllegalStateException("Server tidak mengembalikan token login");
        accessToken = token;
        return response.path("user");
    }

    public static void register(String nama, String username, String password, String alamat, String noTelepon) {
        post("auth/register", java.util.Map.of(
                "nama", nama,
                "username", username,
                "password", password,
                "alamat", alamat == null ? "" : alamat,
                "noTelepon", noTelepon == null ? "" : noTelepon));
    }

    public static void checkConnection() {
        get("health");
    }

    public static String encodeQuery(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public static String encodePathSegment(String value) {
        return encodeQuery(value).replace("+", "%20");
    }

    public static void clearAuth() {
        accessToken = null;
    }

    private static Properties loadConfig() {
        Properties properties = new Properties();
        try (var stream = ApiClient.class.getResourceAsStream("/app.properties")) {
            if (stream == null) throw new IllegalStateException("Konfigurasi aplikasi /app.properties tidak ditemukan");
            properties.load(stream);
            return properties;
        } catch (IOException ex) {
            throw new IllegalStateException("Gagal membaca konfigurasi aplikasi", ex);
        }
    }

    private static String configuredValue(String environmentName, String propertyName, String fallback) {
        String value = System.getenv(environmentName);
        if (value != null && !value.isBlank()) return value;
        return CONFIG.getProperty(propertyName, fallback);
    }

    public static final class ApiException extends RuntimeException {
        private final int statusCode;

        public ApiException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }
}
