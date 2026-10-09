package service.connections;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Gestiona la conexión a la base de datos SQLite local.
 * La base de datos se guarda en el directorio del usuario, dentro de una
 * carpeta ".miscuentas", para que persista entre ejecuciones del programa.
 * 
 * @author Fco. Padilla
 */
public class DatabaseManager {

    private static final String BD_CARPETA = System.getProperty("user.home") + "/.miscuentas";
    private static final String BD_RUTA = BD_CARPETA + "/miscuentas.db";
    private static final String URL = "jdbc:sqlite:" + BD_RUTA;

    private static Connection connection;

    // No se instancia
    private DatabaseManager() {
    }

    /**
     * Devuelve la conexión activa a la base de datos, creándola si es la
     * primera vez que se llama (y aplicando el esquema si la base de datos es nueva).
     */
    public static synchronized Connection getConnection() {
        if (connection == null) {
            try {
                Files.createDirectories(Path.of(BD_CARPETA));
                connection = DriverManager.getConnection(URL);
                // Activamos las claves foráneas que están desactivadas por defecto en SQLite
                try (Statement st = connection.createStatement()) {
                    st.execute("PRAGMA foreign_keys = ON;");
                }
                aplicarEsquema();
            } catch (IOException | SQLException e) {
                throw new RuntimeException("No se pudo inicializar la base de datos", e);
            }
        }
        return connection;
    }

    /**
     * Lee assets/schema.sql desde el classpath y ejecuta cada línea.
     * Usa CREATE TABLE IF NOT EXISTS para que no pete cada vez que llamemos al método.
     */
    private static void aplicarEsquema() throws SQLException {
        String sql = leerRecurso("/assets/schema.sql");
        try (Statement st = connection.createStatement()) {
            for (String sentencia : sql.split(";")) {
                String limpio = sentencia.trim();
                if (!limpio.isEmpty()) {
                    st.execute(limpio);
                }
            }
        }
    }

    /**
     * Intenta leer un archivo dentro del proyecto comprobando que exista. Usado para leer el .sql
     * @param ruta: Ruta del archivo que queremos procesar.
     * @return Devuelve un string con el contenido completo del archivo leido.
     */
    private static String leerRecurso(String ruta) {
        try (InputStream is = DatabaseManager.class.getResourceAsStream(ruta)) {
            if (is == null) {
                throw new RuntimeException("No se encontró el recurso: " + ruta
                        + " (revisa que 'src' esté marcado como Source Folder en Eclipse)");
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Error leyendo " + ruta, e);
        }
    }

    /** Cierra la conexión. Llamar al salir de la aplicación. */
    public static synchronized void cerrar() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            } finally {
                connection = null;
            }
        }
    }
}
