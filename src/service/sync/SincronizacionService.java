package service.sync;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import models.Usuario;
import service.auth.SesionActual;
import service.sync.FirebaseAuthClient.AuthException;
import service.sync.FirebaseAuthClient.Sesion;

/**
 * Lo que necesita la interfaz para subir y descargar datos: se encarga de renovar el token de la sesión de
 * Firebase antes de cada operación (caduca a la hora) y de leer la configuración de assets/firebase.properties.
 * Subir y descargar usan la red: hay que llamarlos fuera de la hebra de JavaFX.
 *
 * @author Fco Padilla
 */
public class SincronizacionService {

    private FirebaseAuthClient auth;
    private String dbUrl;

    /**
     * Dice si en este equipo hay cambios que no se han subido a Firebase. No usa la red.
     * @param usuario: usuario de la sesión (con cuenta en Firebase)
     * @return Devuelve true si hay cambios locales sin subir
     */
    public boolean hayCambiosSinSubir(Usuario usuario) {
        return new SyncService(null).hayCambiosSinSubir(usuario);
    }

    /**
     * Sube los datos locales a Firebase, que reemplazan a los que hubiera allí.
     * @param usuario: usuario de la sesión
     * @throws IOException si no hay conexión o Firebase rechaza la petición
     * @throws InterruptedException
     */
    public void subir(Usuario usuario) throws IOException, InterruptedException {
        preparar(usuario).subir(usuario);
    }

    /**
     * Descarga los datos de Firebase, que reemplazan a los locales.
     * @param usuario: usuario de la sesión
     * @return Devuelve false si en Firebase no hay datos de este usuario (no se cambia nada en local)
     * @throws IOException si no hay conexión o Firebase rechaza la petición
     * @throws InterruptedException
     */
    public boolean descargar(Usuario usuario) throws IOException, InterruptedException {
        return preparar(usuario).descargar(usuario);
    }

    /**
     * Renueva el token de la sesión y crea el cliente de la base de datos con él. Si Firebase rechaza la sesión
     * (caducada o revocada), la aplicación pasa a modo sin conexión para que se pueda volver a conectar.
     * @param usuario: usuario de la sesión
     * @return Devuelve el servicio de sincronización listo para usar
     * @throws IOException si no hay conexión o la sesión ya no vale (AuthException)
     * @throws InterruptedException
     */
    private SyncService preparar(Usuario usuario) throws IOException, InterruptedException {
        cargarConfiguracion();
        Sesion actual = SesionActual.getFirebase();
        if (actual == null) {
            throw new AuthException("SIN_SESION", "No hay conexión con Firebase. Pulsa Conectar con Firebase.");
        }
        Sesion nueva;
        try {
            nueva = auth.renovar(actual);
        } catch (AuthException e) {
            SesionActual.iniciarSinConexion(usuario);
            throw new AuthException(e.getCodigo(), "La sesión con Firebase ha caducado. Vuelve a conectarte.");
        }
        SesionActual.iniciar(usuario, nueva);
        return new SyncService(new FirebaseClient(dbUrl, nueva.idToken()));
    }

    /**
     * Lee apiKey y dbUrl de assets/firebase.properties la primera vez
     * @throws AuthException si falta la configuración
     */
    private void cargarConfiguracion() throws AuthException {
        if (auth != null) {
            return;
        }
        Properties p = new Properties();
        try (InputStream in = SincronizacionService.class.getResourceAsStream("/assets/firebase.properties")) {
            if (in != null) {
                p.load(in);
            }
        } catch (IOException e) {
            throw new AuthException("CONFIG", "No se pudo leer la configuración de Firebase.");
        }
        String apiKey = p.getProperty("apiKey", "").trim();
        String url = p.getProperty("dbUrl", "").trim();
        if (apiKey.isEmpty() || url.isEmpty()) {
            throw new AuthException("CONFIG", "Falta apiKey o dbUrl en assets/firebase.properties.");
        }
        auth = new FirebaseAuthClient(apiKey);
        dbUrl = url;
    }
}
