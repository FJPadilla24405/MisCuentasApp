package service.sync;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Cliente mínimo de la API REST de Firebase Realtime Database.
 * Cada petición va a  {baseUrl}/{ruta}.json?auth={idToken}
 *
 * @param baseUrl URL de la base de datos SIN barra final y SIN nodo, p. ej.
 * @param idToken token de Firebase Auth del usuario (null solo si las reglas son públicas, p. ej. en pruebas)
 */
public class FirebaseClient {

    private final String baseUrl;
    private final String idToken;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public FirebaseClient(String baseUrl, String idToken) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.idToken = idToken;
    }

    /** Lee un nodo. Devuelve null si no existe. */
    public Object leer(String ruta) throws IOException, InterruptedException {
        HttpRequest req = peticion(ruta).GET().build();
        return Json.leer(enviar(req));
    }

    /** Escribe un nodo, reemplazando todo lo que hubiera debajo. */
    public void escribir(String ruta, Object valor) throws IOException, InterruptedException {
        HttpRequest req = peticion(ruta)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(Json.escribir(valor), StandardCharsets.UTF_8))
                .build();
        enviar(req);
    }

    private HttpRequest.Builder peticion(String ruta) {
        String url = baseUrl + "/" + ruta + ".json";
        if (idToken != null && !idToken.isBlank()) {
            url += "?auth=" + URLEncoder.encode(idToken, StandardCharsets.UTF_8);
        }
        return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30));
    }

    private String enviar(HttpRequest req) throws IOException, InterruptedException {
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) {
            throw new IOException("Firebase respondió " + resp.statusCode() + ": " + resp.body());
        }
        return resp.body();
    }
}
