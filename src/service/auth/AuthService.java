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
import java.util.Set;
import java.util.UUID;

/**
 * Lógica de registro e inicio de sesión, separada de las pantallas.
 *
 * Toda cuenta (online u offline) tiene una contraseña, y en la base de datos local se guarda su huella
 * (ContrasenaLocal). Al iniciar sesión se comprueba primero en local; después, si la cuenta es online y
 * no se ha pedido entrar sin conexión, se valida también en Firebase.
 *
 * @author Fco Padilla
 */
public class AuthService {

    /**
     * Cómo ha ido un inicio de sesión.
     */
    public enum Estado {
        // Contraseña correcta en local y en Firebase: se entra con sesión de Firebase
        ONLINE,
        // Contraseña correcta en local y no se consulta Firebase (cuenta offline o entrada sin conexión)
        OFFLINE,
        // Contraseña correcta en local pero no se ha podido verificar en Firebase (sin red u otro fallo): se pregunta
        SIN_VERIFICAR,
        // El email o la contraseña no son correctos: no se entra
        CREDENCIALES_INCORRECTAS,
        // La contraseña coincide en local pero Firebase la rechaza (se cambió en la nube): no se entra
        RECHAZADA_POR_FIREBASE,
        // Otro motivo por el que no se entra (el texto va en detalle)
        ERROR
    }

    /**
     * Resultado de iniciarSesion.
     * @param estado: cómo ha ido
     * @param usuario: usuario local (en ONLINE, OFFLINE y SIN_VERIFICAR)
     * @param sesion: sesión de Firebase (solo en ONLINE)
     * @param detalle: texto del motivo (en ERROR; en SIN_VERIFICAR, null si fue por falta de conexión)
     */
    public record ResultadoLogin(Estado estado, Usuario usuario, Sesion sesion, String detalle) {
    }

    // Códigos de Firebase que significan "esas credenciales no valen"
    private static final Set<String> CREDENCIALES_INCORRECTAS =
            Set.of("INVALID_LOGIN_CREDENTIALS", "INVALID_PASSWORD", "EMAIL_NOT_FOUND");

    private final UsuarioDAO usuarioDAO = new UsuarioDAO();
    private FirebaseAuthClient firebase;

    /**
     * Inicia sesión con email y contraseña. Tarda (calcula la huella y puede usar la red), así que hay
     * que llamarlo fuera de la hebra de JavaFX.
     * <ol>
     * <li>Se busca el email en la base de datos local y se comprueba la contraseña con su huella.</li>
     * <li>Cuenta offline, o entrada sin conexión pedida: se entra si la contraseña es correcta en local.</li>
     * <li>Cuenta online: se valida en Firebase. Si no hay conexión y la contraseña es correcta en local, se
     * devuelve SIN_VERIFICAR para que la pantalla pregunte. Si la contraseña no coincide en local pero
     * Firebase la acepta (se cambió con el correo de recuperación), se actualiza la huella local y se entra.</li>
     * </ol>
     * @param email: email de la cuenta
     * @param password: contraseña escrita
     * @param sinConexion: true para no consultar Firebase (casilla de iniciar sesión sin conexión)
     * @return Devuelve cómo ha ido el inicio de sesión
     */
    public ResultadoLogin iniciarSesion(String email, String password, boolean sinConexion) {
        Optional<Usuario> encontrado = usuarioDAO.buscarPorEmail(email.trim());
        if (encontrado.isEmpty()) {
            return resultado(Estado.CREDENCIALES_INCORRECTAS, null, null, null);
        }
        Usuario usuario = encontrado.get();
        boolean tieneHuella = usuario.getPasswordHash() != null && !usuario.getPasswordHash().isBlank();
        boolean localCorrecta = tieneHuella && ContrasenaLocal.verificar(password, usuario.getPasswordHash());

        // Cuenta offline o entrada sin conexión: solo cuenta lo que hay en local
        if (usuario.isSoloLocal() || sinConexion) {
            if (localCorrecta) {
                return resultado(Estado.OFFLINE, usuario, null, null);
            }
            if (!tieneHuella) {
                return resultado(Estado.ERROR, null, null, usuario.isSoloLocal()
                        ? "Esta cuenta offline se creó antes de que hubiera contraseñas y no se puede abrir. Crea una cuenta nueva."
                        : "Esta cuenta todavía no tiene la contraseña guardada en este equipo. Inicia sesión una vez con conexión.");
            }
            return resultado(Estado.CREDENCIALES_INCORRECTAS, null, null, null);
        }

        // Cuenta online: Firebase
        try {
            Sesion sesion = firebase().iniciarSesion(email.trim(), password);
            if (!localCorrecta) {
                // Firebase la acepta pero la huella local no coincide o no existe: la contraseña se cambió
                // fuera de este equipo (recuperación por correo) o la cuenta es anterior a las huellas
                String nueva = ContrasenaLocal.hashear(password);
                usuarioDAO.actualizarPasswordHash(usuario.getId(), nueva);
                usuario.setPasswordHash(nueva);
            }
            return resultado(Estado.ONLINE, usuario, sesion, null);
        } catch (AuthException a) {
            if (CREDENCIALES_INCORRECTAS.contains(a.getCodigo())) {
                return localCorrecta ? resultado(Estado.RECHAZADA_POR_FIREBASE, usuario, null, null)
                        : resultado(Estado.CREDENCIALES_INCORRECTAS, null, null, null);
            }
            return localCorrecta ? resultado(Estado.SIN_VERIFICAR, usuario, null, a.getMessage())
                    : resultado(Estado.ERROR, null, null, a.getMessage());
        } catch (IOException e) {
            // Sin conexión: solo se puede preguntar si la contraseña ya es correcta en local
            return localCorrecta ? resultado(Estado.SIN_VERIFICAR, usuario, null, null)
                    : resultado(Estado.CREDENCIALES_INCORRECTAS, null, null, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return localCorrecta ? resultado(Estado.SIN_VERIFICAR, usuario, null, null)
                    : resultado(Estado.CREDENCIALES_INCORRECTAS, null, null, null);
        }
    }

    /**
     * @param email: email a buscar
     * @return true si ya hay un usuario local con ese email.
     */
    public boolean existeEmailLocal(String email) {
        return usuarioDAO.buscarPorEmail(email.trim()).isPresent();
    }

    /**
     * Crea una cuenta online: la cuenta en Firebase (hace falta conexión: de ahí sale el uid) y después el
     * usuario local, con la huella de la contraseña. Deja la sesión iniciada. Bloquea la hebra, llamarlo
     * fuera de la hebra de JavaFX.
     * @param nombre: nombre de usuario
     * @param email: correo electrónico
     * @param password: contraseña
     * @return El usuario local creado
     * @throws AuthException si el email ya existe en este equipo o Firebase rechaza el registro
     * @throws IOException si no hay conexión
     * @throws InterruptedException
     */
    public Usuario registrar(String nombre, String email, String password) throws IOException, InterruptedException {
        String correo = email.trim().toLowerCase(Locale.ROOT);
        comprobarEmailLibre(correo);
        String huella = ContrasenaLocal.hashear(password);
        Sesion sesion = firebase().registrar(correo, password);
        Usuario usuario = nuevoUsuario(sesion.uid(), nombre, correo, huella);
        SesionActual.iniciar(usuario, sesion);
        return usuario;
    }

    /**
     * Crea una cuenta offline: solo en este equipo, sin Firebase ni conexión, pero con contraseña (se
     * guarda su huella). Deja la sesión iniciada (sin Firebase). Esta cuenta no se puede sincronizar.
     * Tarda unas décimas de segundo (calcula la huella), llamarlo fuera de la hebra de JavaFX.
     * @param nombre: nombre de usuario
     * @param email: correo electrónico
     * @param password: contraseña
     * @return El usuario local creado
     * @throws AuthException si ya hay un usuario local con ese email
     */
    public Usuario registrarSoloLocal(String nombre, String email, String password) throws AuthException {
        String correo = email.trim().toLowerCase(Locale.ROOT);
        comprobarEmailLibre(correo);
        String uidLocal = Usuario.PREFIJO_UID_LOCAL + UUID.randomUUID();
        Usuario usuario = nuevoUsuario(uidLocal, nombre, correo, ContrasenaLocal.hashear(password));
        SesionActual.iniciarSinConexion(usuario);
        return usuario;
    }

    /**
     * Pide a Firebase que mande el correo para crear una contraseña nueva. Bloquea la hebra (red).
     * Si el correo no está registrado en Firebase no se avisa: la pantalla muestra siempre el mismo
     * mensaje, para que nadie pueda usar esta opción para averiguar qué correos tienen cuenta.
     * La contraseña nueva se cambia en Firebase; la huella local se actualiza sola en el siguiente
     * inicio de sesión con conexión.
     * @param email: correo de la cuenta
     * @throws AuthException si el correo no es válido, la configuración falta o hay demasiadas peticiones
     * @throws IOException si no hay conexión
     * @throws InterruptedException
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

    /**
     * Comprueba que no haya ya un usuario local con ese email
     * @param correo: email normalizado
     * @throws AuthException si ya existe
     */
    private void comprobarEmailLibre(String correo) throws AuthException {
        if (existeEmailLocal(correo)) {
            throw new AuthException("EMAIL_EXISTS_LOCAL", "Ya existe una cuenta con ese correo en este equipo.");
        }
    }

    /**
     * Inserta un usuario nuevo en la base de datos local
     * @param uid: uid de Firebase, o el identificador local de las cuentas offline
     * @param nombre: nombre de usuario
     * @param correo: email normalizado
     * @param huella: huella de la contraseña
     * @return Devuelve el usuario insertado
     */
    private Usuario nuevoUsuario(String uid, String nombre, String correo, String huella) {
        Usuario usuario = new Usuario(0, uid, nombre.trim(), correo, "EUR", "claro");
        usuario.setPasswordHash(huella);
        return usuarioDAO.insertar(usuario);
    }

    /**
     * Atajo para crear un resultado
     * @param estado: cómo ha ido
     * @param usuario: usuario local
     * @param sesion: sesión de Firebase
     * @param detalle: texto del motivo
     * @return Devuelve el resultado
     */
    private ResultadoLogin resultado(Estado estado, Usuario usuario, Sesion sesion, String detalle) {
        return new ResultadoLogin(estado, usuario, sesion, detalle);
    }

    /**
     * La apiKey se lee de assets/firebase.properties (dentro de la aplicación, el usuario no configura nada).
     * @return Devuelve el cliente de Firebase, creándolo la primera vez
     * @throws AuthException si falta la configuración
     */
    protected FirebaseAuthClient firebase() throws AuthException {
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
