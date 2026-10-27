package service.sync;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registro e inicio de sesión con email y contraseña mediante la API REST de Firebase Authentication.
 *
 * @author Fco Padilla
 */
public class FirebaseAuthClient {

    /**
     * Resultado de registrarse o iniciar sesión.
     *
     * @param uid: identificador del usuario en Firebase (es el que se guarda en usuarios.firebase_uid)
     * @param idToken: token para llamar a la base de datos; caduca a la hora
     * @param refreshToken: token de larga duración para pedir un idToken nuevo sin volver a pedir la contraseña
     */
    public record Sesion(String uid, String idToken, String refreshToken) {
    }

    /**
     * Error devuelto por Firebase Authentication. El mensaje está en español y se puede mostrar tal cual
     * al usuario; getCodigo() da el código original de Firebase (EMAIL_EXISTS, WEAK_PASSWORD...) por si
     * la pantalla quiere reaccionar de forma distinta según el caso.
     * Es una IOException, así que quien ya captura IOException sigue funcionando igual.
     */
    public static class AuthException extends IOException {
    	private static final long serialVersionUID = 1L;
        private final String codigo;

        public AuthException(String codigo, String mensaje) {
            super(mensaje);
            this.codigo = codigo;
        }

        public String getCodigo() {
            return codigo;
        }
    }

 
    private static final String URL = "https://identitytoolkit.googleapis.com/v1";

    private final String apiKey;
    /**
     * cliente http para mandar y recibir información web
     */
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    
    /**
     * Constructor con apikey
     */
    public FirebaseAuthClient(String apiKey) {
    	this.apiKey = apiKey;
    };

    /**
     * Crea una cuenta nueva en Firebase Authentication y devuelve ya la sesión iniciada con ella.
     * @param email: Email de la cuenta
     * @param password: Contraseña de la cuenta
     * @return Sesión de esos datos
     * @throws IOException
     * @throws InterruptedException
     */
    public Sesion registrar(String email, String password) throws IOException, InterruptedException {
        return autenticar("accounts:signUp", email, password);
    }

    /**
     * Inicia sesión con una cuenta que ya existe.
     * @param email: email de la cuenta
     * @param password: contraseña de la cuenta
     * @return Sesión de esos datos
     * @throws IOException
     * @throws InterruptedException
     */
    public Sesion iniciarSesion(String email, String password) throws IOException, InterruptedException {
        return autenticar("accounts:signInWithPassword", email, password);
    }

    /**
     * Pide a Firebase que envíe al correo un enlace para crear una contraseña nueva.
     * El correo lo envía Firebase; la aplicación nunca llega a ver la contraseña nueva.
     * @param email: email de la cuenta
     * @throws IOException si no hay conexión o Firebase rechaza la petición (AuthException)
     * @throws InterruptedException
     */
    public void enviarCorreoRecuperacion(String email) throws IOException, InterruptedException {
        if (email == null || email.isBlank()) {
            throw new AuthException("MISSING_EMAIL", "Escribe tu correo electrónico.");
        }

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("requestType", "PASSWORD_RESET");
        cuerpo.put("email", email.trim());

        HttpRequest req = HttpRequest.newBuilder(URI.create(URL + "/accounts:sendOobCode?key=" + apiKey))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.escribir(cuerpo), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) {
            throw traducirError(resp.body());
        }
    }

    /**
     * Registro y login son la misma petición; solo cambia la operación de la URL.
     * @param operacion: operación que hacer en la url
     * @param email: email de la cuenta
     * @param password: contraseña de la cuenta
     * @return Sesión con los datos
     * @throws IOException
     * @throws InterruptedException
     */
    @SuppressWarnings("unchecked")
    private Sesion autenticar(String operacion, String email, String password) throws IOException, InterruptedException {
        if (email == null || email.isBlank()) {
            throw new AuthException("MISSING_EMAIL", "Escribe tu correo electrónico.");
        }
        if (password == null || password.isEmpty()) {
            throw new AuthException("MISSING_PASSWORD", "Escribe tu contraseña.");
        }

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("email", email.trim());
        cuerpo.put("password", password);
        cuerpo.put("returnSecureToken", Boolean.TRUE);

        HttpRequest req = HttpRequest.newBuilder(URI.create(URL + "/" + operacion + "?key=" + apiKey))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.escribir(cuerpo), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) {
            throw traducirError(resp.body());
        }
        Map<String, Object> json = (Map<String, Object>) Json.leer(resp.body());
        return new Sesion((String) json.get("localId"), (String) json.get("idToken"), (String) json.get("refreshToken"));
    }

    /**
     * Firebase responde los errores así: {"error":{"code":400,"message":"EMAIL_EXISTS", ...}}
     * (a veces con texto detrás: "WEAK_PASSWORD : Password should be at least 6 characters").
     * @param cuerpo: cuerpo del error
     * @return Devuelve el error traducido
     */
    private AuthException traducirError(String cuerpo) {
        String codigo = "UNKNOWN";
        try {
            Map<?, ?> error = (Map<?, ?>) ((Map<?, ?>) Json.leer(cuerpo)).get("error");
            String mensaje = String.valueOf(error.get("message"));
            codigo = mensaje.startsWith("API key") ? "API_KEY_INVALID" : mensaje.split("\\s+")[0];
        } catch (RuntimeException e) {
            // La respuesta no tenía el formato esperado: nos quedamos con UNKNOWN
        }

        String texto = switch (codigo) {
            case "EMAIL_EXISTS" -> "Ya existe una cuenta con ese correo electrónico.";
            case "INVALID_EMAIL" -> "El correo electrónico no es válido.";
            case "WEAK_PASSWORD" -> "La contraseña debe tener al menos 6 caracteres.";
            case "EMAIL_NOT_FOUND", "INVALID_PASSWORD", "INVALID_LOGIN_CREDENTIALS" -> "Correo o contraseña incorrectos.";
            case "USER_DISABLED" -> "Esta cuenta está deshabilitada.";
            case "TOO_MANY_ATTEMPTS_TRY_LATER" -> "Demasiados intentos. Inténtalo de nuevo más tarde.";
            case "RESET_PASSWORD_EXCEED_LIMIT" -> "Has pedido demasiados correos de recuperación. Inténtalo de nuevo más tarde.";
            case "OPERATION_NOT_ALLOWED" -> "El acceso con correo y contraseña no está activado en el proyecto de Firebase.";
            case "API_KEY_INVALID" -> "La clave de API de Firebase no es válida.";
            default -> "No se pudo completar la operación (" + codigo + ").";
        };
        return new AuthException(codigo, texto);
    }
}