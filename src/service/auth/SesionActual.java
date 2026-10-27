package service.auth;

import models.Usuario;
import service.sync.FirebaseAuthClient;

/**
 * Guarda quién ha entrado en la aplicación y, si se ha podido, su sesión de Firebase.
 * Si se entró sin conexión, getFirebase() devuelve null y isConectado() es false:
 * la sincronización debe comprobarlo antes de intentar subir o descargar datos.
 *
 * @author Fco Padilla
 */
public final class SesionActual {

    private static Usuario usuario;
    private static FirebaseAuthClient.Sesion firebase;

    // No se instancia
    private SesionActual() {
    }

    /**
     * Entrada con Firebase validado
     * @param u: usuario que inicia sesión
     * @param sesion: sesión del authclient
     */
    public static void iniciar(Usuario u, FirebaseAuthClient.Sesion sesion) {
        usuario = u;
        firebase = sesion;
    }

    /**
     * Entrada sin conexión: solo se ha comprobado la base de datos local.
     * @param u: usuario que inicia sin conexión
     */
    public static void iniciarSinConexion(Usuario u) {
        usuario = u;
        firebase = null;
    }

    /**
     * Devuelve el usuario de la sesión actual
     * @return Devuelve el usuario
     */
    public static Usuario getUsuario() {
        return usuario;
    }

    /**
     * Devuelve la sesión actual de firebase
     * @return Devuelve la sesión de firebase
     */
    public static FirebaseAuthClient.Sesion getFirebase() {
        return firebase;
    }

    /**
     * Método que devuelve si el usuario logeado tiene conexión a firebase o no
     * @return Devuelve true si está conectado, false si no lo está
     */
    public static boolean isConectado() {
        return firebase != null;
    }

    /**
     * Devuelve si el usuario es solo local
     * @return Devuelve true si quien ha entrado tiene una cuenta solo local (sin Firebase).
     */
    public static boolean isSoloLocal() {
        return usuario != null && usuario.isSoloLocal();
    }

    /**
     * Cierra la sesión actual
     */
    public static void cerrar() {
        usuario = null;
        firebase = null;
    }
}
