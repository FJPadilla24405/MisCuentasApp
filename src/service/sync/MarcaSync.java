package service.sync;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

/**
 * Recuerda, para cada usuario de Firebase, cómo estaban los datos locales la última vez que se subieron o se
 * descargaron: una huella de los datos y la fecha de esa sincronización. Así se sabe si desde entonces se ha
 * cambiado algo en este equipo. Se guarda en un archivo junto a la base de datos (~/.miscuentas/sync-UID.properties).
 *
 * @author Fco Padilla
 */
public final class MarcaSync {

    /**
     * Estado de los datos locales en la última sincronización.
     * @param huella: huella de los datos locales en ese momento
     * @param ultimaSync: fecha que se escribió en Firebase en esa sincronización (texto ISO)
     */
    public record Marca(String huella, String ultimaSync) {
    }

    // No se instancia
    private MarcaSync() {
    }

    /**
     * @param uid: uid de Firebase del usuario
     * @return Devuelve la ruta del archivo de ese usuario (el uid se limpia para que sea un nombre de archivo válido)
     */
    private static Path ruta(String uid) {
        return Path.of(System.getProperty("user.home"), ".miscuentas", "sync-" + uid.replaceAll("[^A-Za-z0-9_-]", "_") + ".properties");
    }

    /**
     * @param uid: uid de Firebase del usuario
     * @return Devuelve la marca guardada, o vacío si nunca se ha sincronizado en este equipo (o no se puede leer)
     */
    public static Optional<Marca> leer(String uid) {
        Path archivo = ruta(uid);
        if (!Files.exists(archivo)) {
            return Optional.empty();
        }
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(archivo)) {
            p.load(in);
        } catch (IOException e) {
            return Optional.empty();
        }
        String huella = p.getProperty("huella");
        return huella == null || huella.isBlank() ? Optional.empty() : Optional.of(new Marca(huella, p.getProperty("ultimaSync", "")));
    }

    /**
     * Guarda la marca de una sincronización. Si no se puede escribir, no pasa nada grave: la aplicación
     * considerará que hay cambios sin subir.
     * @param uid: uid de Firebase del usuario
     * @param huella: huella de los datos locales
     * @param ultimaSync: fecha de la sincronización
     */
    public static void guardar(String uid, String huella, String ultimaSync) {
        Path archivo = ruta(uid);
        Properties p = new Properties();
        p.setProperty("huella", huella);
        p.setProperty("ultimaSync", ultimaSync == null ? "" : ultimaSync);
        try {
            Files.createDirectories(archivo.getParent());
            try (OutputStream out = Files.newOutputStream(archivo)) {
                p.store(out, "Estado de los datos locales en la ultima sincronizacion con Firebase");
            }
        } catch (IOException e) {
            // Sin marca, la aplicación considerará que hay cambios sin subir (lado seguro)
        }
    }
}
