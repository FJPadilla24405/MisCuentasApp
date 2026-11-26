package service.sync;

import java.sql.Connection;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.sqlite.SQLiteConnection;

import models.Usuario;
import service.auth.SesionActual;

/**
 * Apunta cuándo se cambiaron por última vez los datos de este equipo. La base de datos no guarda la fecha de cada cambio,
 * así que se pide a SQLite que avise cada vez que se inserta, cambia o borra una fila de las tablas que se sincronizan, y
 * se guarda la hora del último aviso del usuario que tiene la sesión abierta (en el archivo de MarcaSync). Como una
 * importación o una descarga son miles de avisos seguidos, la hora se anota en memoria y se pasa al archivo una vez, un
 * segundo después del último cambio (y al cerrar la aplicación). Cuando se sube o se descarga, esa hora se olvida: desde
 * ese momento los datos locales son iguales a los de Firebase.
 *
 * @author Fco Padilla
 */
public final class CambiosLocales {

    // Tablas que se suben a Firebase: un cambio en cualquiera de ellas es un cambio de los datos
    private static final Set<String> TABLAS = Set.of("usuarios", "cuentas", "categorias", "movimientos", "presupuestos", "recurrentes");
    private static final long ESPERA_MS = 1000;

    private static final Map<String, Instant> PENDIENTES = new HashMap<>();
    private static final ScheduledExecutorService RELOJ = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread hilo = new Thread(r, "cambios-locales");
        hilo.setDaemon(true);
        return hilo;
    });
    private static ScheduledFuture<?> volcadoProgramado;
    private static boolean ganchoDeCierre = false;

    // No se instancia
    private CambiosLocales() {
    }

    /**
     * Empieza a vigilar los cambios de una conexión a la base de datos
     * @param conexion: conexión de SQLite (si es de otro tipo, no hace nada)
     */
    public static synchronized void vigilar(Connection conexion) {
        if (conexion instanceof SQLiteConnection sqlite) {
            sqlite.addUpdateListener((tipo, bd, tabla, fila) -> alCambiar(tabla));
            if (!ganchoDeCierre) {
                // Al cerrar la aplicación se pasa al archivo lo que quede pendiente
                Runtime.getRuntime().addShutdownHook(new Thread(CambiosLocales::volcar, "cambios-locales-cierre"));
                ganchoDeCierre = true;
            }
        }
    }

    /**
     * Se llama cada vez que SQLite cambia una fila. Tiene que ser rápido y no tocar la base de datos.
     */
    private static void alCambiar(String tabla) {
        if (!TABLAS.contains(tabla)) {
            return;
        }
        Usuario usuario = SesionActual.getUsuario();
        if (usuario == null || usuario.isSoloLocal() || usuario.getFirebaseUid() == null) {
            return;
        }
        synchronized (CambiosLocales.class) {
            PENDIENTES.put(usuario.getFirebaseUid(), Instant.now());
            if (volcadoProgramado != null) {
                volcadoProgramado.cancel(false);
            }
            volcadoProgramado = RELOJ.schedule(CambiosLocales::volcar, ESPERA_MS, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Pasa al archivo de cada usuario la hora de su último cambio
     */
    static void volcar() {
        Map<String, Instant> copia;
        synchronized (CambiosLocales.class) {
            copia = new HashMap<>(PENDIENTES);
            PENDIENTES.clear();
        }
        copia.forEach(MarcaSync::registrarCambioLocal);
    }

    /**
     * Olvida los cambios que todavía no se han pasado al archivo de un usuario (porque se acaba de subir o descargar)
     * @param uid: uid de Firebase del usuario
     */
    static synchronized void olvidar(String uid) {
        PENDIENTES.remove(uid);
    }
}
