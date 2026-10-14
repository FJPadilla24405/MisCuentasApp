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
 * 
 * @author Fco Padilla
 */
public class FirebaseClient {

    private final String baseUrl;
    private final String idToken;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Constructor de la clase
     * @param baseUrl: url de la base de datos firebase
     * @param idToken: token de firebase auth
     */
    public FirebaseClient(String baseUrl, String idToken) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.idToken = idToken;
    }

    /**
     * Lee un nodo. Devuelve null si no existe.
     * @param ruta: ruta a la que hacer la peticion
     * @return Devuelve un json leido
     * @throws IOException
     * @throws InterruptedException
     */
    public Object leer(String ruta) throws IOException, InterruptedException {
        HttpRequest req = peticion(ruta).GET().build();
        return Json.leer(enviar(req));
    }

    /**
     * Escribe un nodo, reemplazando todo lo que hubiera debajo.
     * @param ruta: ruta al a que hacer la peticion
     * @param valor: valor que escribir en el json
     * @throws IOException
     * @throws InterruptedException
     */
    public void escribir(String ruta, Object valor) throws IOException, InterruptedException {
        HttpRequest req = peticion(ruta)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(Json.escribir(valor), StandardCharsets.UTF_8))
                .build();
        enviar(req);
    }

    /**
     * Hace una peticion a una ruta especificada
     * @param ruta: ruta a la que hacer la peticion
     * @return Devuelve un httrequest
     */
    private HttpRequest.Builder peticion(String ruta) {
        String url = baseUrl + "/" + ruta + ".json";
        if (idToken != null && !idToken.isBlank()) {
            url += "?auth=" + URLEncoder.encode(idToken, StandardCharsets.UTF_8);
        }
        return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30));
    }

    /**
     * Envia un mensaje a un httpquest
     * @param req:
     * @return Devuelve  
     * @throws IOException
     * @throws InterruptedException
     */
    private String enviar(HttpRequest req) throws IOException, InterruptedException {
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) {
            throw new IOException("Firebase respondió " + resp.statusCode() + ": " + resp.body());
        }
        return resp.body();
    }
}
