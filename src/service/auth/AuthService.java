package service.auth;

import models.Usuario;
import service.dao.UsuarioDAO;
import service.sync.FirebaseAuthClient;
import service.sync.FirebaseAuthClient.AuthException;
import service.sync.FirebaseAuthClient.Sesion;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

/**
 * Lógica de registro e inicio de sesión, separada de las pantallas.
 * El acceso a la aplicación depende solo de la base de datos local; Firebase se comprueba aparte
 * (iniciarSesionFirebase) y su fallo no impide entrar sin conexión.
 *
 * @author Fco Padilla
 */
public class AuthService {

    private final UsuarioDAO usuarioDAO = new UsuarioDAO();
    private FirebaseAuthClient firebase;

    /**
     * Paso 1 del login: ¿existe en la base de datos local un usuario con ese nombre y ese email?
     * No distingue mayúsculas de minúsculas ni espacios sobrantes.
     * @param nombre: nombre de usuario
     * @param email: correo electrónico
     * @return El usuario local si ambos coinciden
     */
    public Optional<Usuario> buscarUsuarioLocal(String nombre, String email) {
        String n = nombre == null ? "" : nombre.trim();
        return usuarioDAO.buscarPorEmail(email == null ? "" : email.trim())
                .filter(u -> u.getNombre() != null && u.getNombre().trim().equalsIgnoreCase(n));
    }

    /**
     * Paso 2 del login: ¿son válidos email y contraseña en Firebase? Bloquea la hebra (red), así que
     * hay que llamarlo fuera de la hebra de JavaFX.
     * @throws AuthException si Firebase rechaza las credenciales o la configuración falta
     * @throws IOException si no hay conexión
     */
    public Sesion iniciarSesionFirebase(String email, String password) throws IOException, InterruptedException {
        return firebase().iniciarSesion(email, password);
    }

    /** @return true si ya hay un usuario local con ese email. */
    public boolean existeEmailLocal(String email) {
        return usuarioDAO.buscarPorEmail(email.trim()).isPresent();
    }

    /**
     * Crea la cuenta en Firebase (hace falta conexión: de ahí sale el uid) y después el usuario local.
     * Deja la sesión iniciada. Bloquea la hebra, llamarlo fuera de la hebra de JavaFX.
     * @return El usuario local creado
     */
    public Usuario registrar(String nombre, String email, String password) throws IOException, InterruptedException {
        String correo = email.trim().toLowerCase(Locale.ROOT);
        if (existeEmailLocal(correo)) {
            throw new AuthException("EMAIL_EXISTS_LOCAL", "Ya existe una cuenta con ese correo en este equipo.");
        }
        Sesion sesion = firebase().registrar(correo, password);
        Usuario usuario = usuarioDAO.insertar(new Usuario(0, sesion.uid(), nombre.trim(), correo, "EUR", "claro"));
        SesionActual.iniciar(usuario, sesion);
        return usuario;
    }

    /**
     * Pide a Firebase que mande el correo para crear una contraseña nueva. Bloquea la hebra (red).
     * Si el correo no está registrado en Firebase no se avisa: la pantalla muestra siempre el mismo
     * mensaje, para que nadie pueda usar esta opción para averiguar qué correos tienen cuenta.
     * @param email: correo de la cuenta
     * @throws AuthException si el correo no es válido, la configuración falta o hay demasiadas peticiones
     * @throws IOException si no hay conexión
     */
    public void recuperarContrasena(String email) throws IOException, InterruptedException {
        try {
            firebase().enviarCorreoRecuperacion(email);
        } catch (AuthException e) {
            if (!"EMAIL_NOT_FOUND".equals(e.getCodigo())) {
                throw e;
            }
        }
    }

    /** La apiKey se lee de assets/firebase.properties (dentro de la aplicación, el usuario no configura nada). */
    private FirebaseAuthClient firebase() throws AuthException {
        if (firebase == null) {
            Properties p = new Properties();
            try (InputStream in = AuthService.class.getResourceAsStream("/assets/firebase.properties")) {
                if (in != null) {
                    p.load(in);
                }
            } catch (IOException e) {
                throw new AuthException("CONFIG", "No se pudo leer la configuración de Firebase.");
            }
            String apiKey = p.getProperty("apiKey", "").trim();
            if (apiKey.isEmpty()) {
                throw new AuthException("CONFIG", "Falta la apiKey de Firebase en assets/firebase.properties.");
            }
            firebase = new FirebaseAuthClient(apiKey);
        }
        return firebase;
    }
}
