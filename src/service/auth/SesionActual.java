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

    /** Entrada con Firebase validado. */
    public static void iniciar(Usuario u, FirebaseAuthClient.Sesion sesion) {
        usuario = u;
        firebase = sesion;
    }

    /** Entrada sin conexión: solo se ha comprobado la base de datos local. */
    public static void iniciarSinConexion(Usuario u) {
        usuario = u;
        firebase = null;
    }

    public static Usuario getUsuario() {
        return usuario;
    }

    public static FirebaseAuthClient.Sesion getFirebase() {
        return firebase;
    }

    public static boolean isConectado() {
        return firebase != null;
    }

    /** @return true si quien ha entrado tiene una cuenta solo local (sin Firebase). */
    public static boolean isSoloLocal() {
        return usuario != null && usuario.isSoloLocal();
    }

    public static void cerrar() {
        usuario = null;
        firebase = null;
    }
}
