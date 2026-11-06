package service.connections;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
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
                migrar();
            } catch (IOException | SQLException e) {
                throw new RuntimeException("No se pudo inicializar la base de datos", e);
            }
        }
        return connection;
    }

    /**
     * Lee assets/schema.sql desde el classpath y ejecuta cada línea.
     * Usa CREATE TABLE IF NOT EXISTS para que no pete cada vez que llamemos al método.
     * @throws SQLException
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
     * Pone al día una base de datos creada con una versión anterior del esquema. CREATE TABLE IF NOT EXISTS
     * no añade columnas a una tabla que ya existe, así que aquí se añaden las que falten.
     * Por ahora: usuarios.password_hash (huella de la contraseña local) presupuestos.nombre, presupuestos.alcance y movimientos.nota.
     * @throws SQLException
     */
    private static void migrar() throws SQLException {
        anadirColumnaSiFalta("usuarios", "password_hash", "VARCHAR(255)");
        anadirColumnaSiFalta("presupuestos", "nombre", "VARCHAR(50)");
        anadirColumnaSiFalta("presupuestos", "alcance", "VARCHAR(10) DEFAULT 'todas'");
        anadirColumnaSiFalta("movimientos", "nota", "VARCHAR(255)");
    }

    /**
     * Añade una columna a una tabla si todavía no la tiene
     * @param tabla: nombre de la tabla
     * @param columna: nombre de la columna
     * @param definicion: tipo y valor por defecto de la columna
     * @throws SQLException
     */
    private static void anadirColumnaSiFalta(String tabla, String columna, String definicion) throws SQLException {
        boolean existe = false;
        try (Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery("PRAGMA table_info(" + tabla + ")")) {
            while (rs.next()) {
                if (columna.equalsIgnoreCase(rs.getString("name"))) {
                    existe = true;
                }
            }
        }
        if (!existe) {
            try (Statement st = connection.createStatement()) {
                st.execute("ALTER TABLE " + tabla + " ADD COLUMN " + columna + " " + definicion);
            }
        }
    }

    /**
     * Intenta leer un archivo dentro del proyecto comprobando que exista. Usado para leer el .sql
     * @param ruta: Ruta del archivo que queremos procesar.
     * @return Devuelve un string con el contenido completo del archivo leído.
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

    /**
     * Cierra la conexión. Llamar al salir de la aplicación.
     */
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
