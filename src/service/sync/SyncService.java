package service.sync;

import models.Usuario;
import service.connections.DatabaseManager;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sincroniza la base de datos SQLite local con Firebase Realtime Database.
 *
 * @author Fco Padilla
 */
public class SyncService {

    private static final String RAIZ = "Users";

    private final FirebaseClient firebase;

    public SyncService(FirebaseClient firebase) {
        this.firebase = firebase;
    }

    /**
     * SUBIR: la copia local del usuario REEMPLAZA a la de Firebase (gana lo local).
     * Lo que exista en la nube y ya no exista en local se borra.
     */
    public void subir(Usuario usuario) throws IOException, InterruptedException {
        exigirUid(usuario);
        Connection conn = DatabaseManager.getConnection();
        comprobarEsquema(conn);

        Map<String, Object> raiz = new LinkedHashMap<>();
        try {
            // Perfil, directamente bajo Users/{uid}
            for (Map<String, Object> fila : consultar(conn,
                    "SELECT nombre, email, moneda, tema FROM usuarios WHERE id = ?", usuario.getId())) {
                raiz.putAll(aCamel(fila));
            }

            Map<String, Object> cuentas = new LinkedHashMap<>();
            for (Map<String, Object> f : consultar(conn, "SELECT * FROM cuentas WHERE usuario_id = ?", usuario.getId())) {
                cuentas.put(String.valueOf(f.get("id")), registro(f, "id", "usuario_id"));
            }
            colocar(raiz, "cuentas", cuentas);

            Map<String, Object> categorias = new LinkedHashMap<>();
            for (Map<String, Object> f : consultar(conn, "SELECT * FROM categorias WHERE usuario_id = ?", usuario.getId())) {
                categorias.put(String.valueOf(f.get("id")), registro(f, "id", "usuario_id"));
            }
            colocar(raiz, "categorias", categorias);

            Map<String, Object> movimientos = new LinkedHashMap<>();
            for (Map<String, Object> f : consultar(conn, "SELECT * FROM movimientos WHERE usuario_id = ?", usuario.getId())) {
                // sincronizado es solo una marca local: no se sube
                Map<String, Object> mov = registro(f, "id", "usuario_id", "sincronizado");
                Object recurrenteId = f.get("recurrente_id");
                if (recurrenteId != null) {
                    List<Map<String, Object>> reglas = consultar(conn, "SELECT * FROM recurrentes WHERE id = ?", recurrenteId);
                    if (!reglas.isEmpty()) {
                        Map<String, Object> recurrentes = new LinkedHashMap<>();
                        recurrentes.put(String.valueOf(recurrenteId), registro(reglas.get(0), "id"));
                        mov.put("recurrentes", recurrentes);
                    }
                }
                movimientos.put(String.valueOf(f.get("id")), mov);
            }
            colocar(raiz, "movimientos", movimientos);

            Map<String, Object> presupuestos = new LinkedHashMap<>();
            for (Map<String, Object> f : consultar(conn, "SELECT * FROM presupuestos WHERE usuario_id = ?", usuario.getId())) {
                String clave = f.get("categoria_id") + "_" + f.get("mes_referencia");
                if (presupuestos.containsKey(clave)) {
                    throw new IllegalStateException("Hay dos presupuestos para la misma categoría y mes (" + clave + ")");
                }
                presupuestos.put(clave, registro(f, "id", "usuario_id"));
            }
            colocar(raiz, "presupuestos", presupuestos);

            raiz.put("ultimaSync", Instant.now().toString());
        } catch (SQLException e) {
            throw new RuntimeException("Error leyendo la base de datos local", e);
        }

        // Una sola petición: o se sube todo o no se sube nada
        firebase.escribir(RAIZ + "/" + usuario.getFirebaseUid(), raiz);

        try (PreparedStatement ps = conn.prepareStatement("UPDATE movimientos SET sincronizado = 1 WHERE usuario_id = ?")) {
            ps.setInt(1, usuario.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error marcando movimientos como sincronizados", e);
        }
    }

    /**
     * DESCARGAR: los datos de Firebase REEMPLAZAN a los locales del usuario (gana la nube).
     * Todo se hace en una transacción: si algo falla, la base local queda como estaba.
     *
     * @return false si en Firebase no hay datos de este usuario (no se toca nada en local)
     */
    @SuppressWarnings("unchecked")
    public boolean descargar(Usuario usuario) throws IOException, InterruptedException {
        exigirUid(usuario);
        Object remoto = firebase.leer(RAIZ + "/" + usuario.getFirebaseUid());
        if (!(remoto instanceof Map)) {
            return false;
        }
        Map<String, Object> raiz = (Map<String, Object>) remoto;

        Connection conn = DatabaseManager.getConnection();
        comprobarEsquema(conn);
        int uid = usuario.getId();
        try {
            conn.setAutoCommit(false);

            // --- 1) Borrar lo local del usuario (hijos antes que padres)
            List<Object> reglasDelUsuario = new ArrayList<>();
            for (Map<String, Object> f : consultar(conn,
                    "SELECT DISTINCT recurrente_id FROM movimientos WHERE usuario_id = ? AND recurrente_id IS NOT NULL", uid)) {
                reglasDelUsuario.add(f.get("recurrente_id"));
            }
            ejecutar(conn, "DELETE FROM movimientos WHERE usuario_id = ?", uid);
            for (Object reglaId : reglasDelUsuario) {
                // Solo se borra la regla si ya no la usa ningún movimiento (de ningún usuario)
                ejecutar(conn, "DELETE FROM recurrentes WHERE id = ? "
                        + "AND NOT EXISTS (SELECT 1 FROM movimientos WHERE recurrente_id = recurrentes.id)", reglaId);
            }
            ejecutar(conn, "DELETE FROM presupuestos WHERE usuario_id = ?", uid);
            ejecutar(conn, "DELETE FROM categorias WHERE usuario_id = ?", uid);
            ejecutar(conn, "DELETE FROM cuentas WHERE usuario_id = ?", uid);

            // --- 2) Perfil
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE usuarios SET nombre = COALESCE(?, nombre), email = COALESCE(?, email), "
                            + "moneda = COALESCE(?, moneda), tema = COALESCE(?, tema) WHERE id = ?")) {
                ps.setObject(1, raiz.get("nombre"));
                ps.setObject(2, raiz.get("email"));
                ps.setObject(3, raiz.get("moneda"));
                ps.setObject(4, raiz.get("tema"));
                ps.setInt(5, uid);
                ps.executeUpdate();
            }

            // --- 3) Insertar lo remoto (padres antes que hijos)
            for (Map.Entry<String, Map<String, Object>> e : entradas(raiz.get("cuentas"))) {
                insertar(conn, "cuentas", uid, e.getValue(), idDe(e.getKey()));
            }
            for (Map.Entry<String, Map<String, Object>> e : entradas(raiz.get("categorias"))) {
                insertar(conn, "categorias", uid, e.getValue(), idDe(e.getKey()));
            }
            for (Map.Entry<String, Map<String, Object>> e : entradas(raiz.get("presupuestos"))) {
                insertar(conn, "presupuestos", uid, e.getValue(), null); // el id local lo asigna SQLite
            }

            Set<Integer> reglasInsertadas = new HashSet<>();
            for (Map.Entry<String, Map<String, Object>> e : entradas(raiz.get("movimientos"))) {
                Map<String, Object> mov = new LinkedHashMap<>(e.getValue());
                Integer reglaId = null;
                for (Map.Entry<String, Map<String, Object>> r : entradas(mov.get("recurrentes"))) {
                    reglaId = idDe(r.getKey());
                    if (reglasInsertadas.add(reglaId)) {
                        // Una regla local huérfana (ningún movimiento la usa, p. ej. porque se borró su
                        // movimiento) con el mismo id se sustituye. Si la usa otro movimiento, no se toca
                        // y el INSERT falla avisando del choque de ids.
                        ejecutar(conn, "DELETE FROM recurrentes WHERE id = ? "
                                + "AND NOT EXISTS (SELECT 1 FROM movimientos WHERE recurrente_id = recurrentes.id)", reglaId);
                        insertar(conn, "recurrentes", null, r.getValue(), reglaId);
                    }
                }
                if (reglaId != null) {
                    mov.put("recurrenteId", reglaId);
                }
                insertar(conn, "movimientos", uid, mov, idDe(e.getKey()));
            }

            conn.commit();
            return true;
        } catch (SQLException | RuntimeException e) {
            try {
                conn.rollback();
            } catch (SQLException ignorada) {
                // ya hay un error más importante que contar
            }
            throw new RuntimeException("Error aplicando los datos descargados (si es un UNIQUE, puede que los ids "
                    + "choquen con los de otro usuario de este mismo equipo)", e);
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException ignorada) {
                // nada que hacer
            }
        }
    }

    /** Inserta una fila remota: solo columnas que existan en local, con usuario_id e id fijados por nosotros. */
    private void insertar(Connection conn, String tabla, Integer usuarioId, Map<String, Object> remoto, Integer id)
            throws SQLException {
        Set<String> locales = columnas(conn, tabla);
        Map<String, Object> valores = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : remoto.entrySet()) {
            String columna = aSnake(e.getKey());
            // Solo columnas locales: evita errores y que una clave rara de Firebase se cuele en el SQL
            if (locales.contains(columna) && !columna.equals("id") && !columna.equals("usuario_id")) {
                valores.put(columna, e.getValue());
            }
        }
        if (id != null) {
            valores.put("id", id);
        }
        if (usuarioId != null) {
            valores.put("usuario_id", usuarioId); // el id local del usuario puede ser distinto en otro equipo
        }
        if (locales.contains("sincronizado")) {
            valores.put("sincronizado", 1);
        }

        StringBuilder cols = new StringBuilder();
        StringBuilder marcas = new StringBuilder();
        for (String c : valores.keySet()) {
            if (cols.length() > 0) {
                cols.append(", ");
                marcas.append(", ");
            }
            cols.append(c);
            marcas.append('?');
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO " + tabla + " (" + cols + ") VALUES (" + marcas + ")")) {
            int i = 1;
            for (Object v : valores.values()) {
                ps.setObject(i++, v);
            }
            ps.executeUpdate();
        }
    }

    /**
     * Firebase devuelve un nodo como lista (en vez de objeto) cuando sus claves son 0,1,2... casi
     * consecutivas, así que aceptamos las dos formas. La clave (o el índice) es el id de la fila.
     */
    @SuppressWarnings("unchecked")
    private List<Map.Entry<String, Map<String, Object>>> entradas(Object seccion) {
        List<Map.Entry<String, Map<String, Object>>> filas = new ArrayList<>();
        if (seccion instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (e.getValue() instanceof Map<?, ?> fila) {
                    filas.add(Map.entry(String.valueOf(e.getKey()), (Map<String, Object>) fila));
                }
            }
        } else if (seccion instanceof List<?> l) {
            for (int i = 0; i < l.size(); i++) {
                if (l.get(i) instanceof Map<?, ?> fila) {
                    filas.add(Map.entry(String.valueOf(i), (Map<String, Object>) fila));
                }
            }
        }
        return filas;
    }

    private Integer idDe(String clave) {
        try {
            return Integer.valueOf(clave);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Clave no numérica en Firebase: '" + clave + "'");
        }
    }

    /** Quita las columnas indicadas y los NULL, y pasa los nombres a camelCase. */
    private Map<String, Object> registro(Map<String, Object> fila, String... excluir) {
        Map<String, Object> copia = new LinkedHashMap<>(fila);
        for (String c : excluir) {
            copia.remove(c);
        }
        return aCamel(copia);
    }

    private Map<String, Object> aCamel(Map<String, Object> fila) {
        Map<String, Object> r = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : fila.entrySet()) {
            if (e.getValue() != null) { // Firebase no guarda nulos; un campo ausente se lee como NULL
                r.put(aCamel(e.getKey()), e.getValue());
            }
        }
        return r;
    }

    /** cuenta_id -> cuentaId */
    static String aCamel(String snake) {
        StringBuilder sb = new StringBuilder();
        boolean mayus = false;
        for (char c : snake.toCharArray()) {
            if (c == '_') {
                mayus = true;
            } else {
                sb.append(mayus ? Character.toUpperCase(c) : c);
                mayus = false;
            }
        }
        return sb.toString();
    }

    /** cuentaId -> cuenta_id */
    static String aSnake(String camel) {
        StringBuilder sb = new StringBuilder();
        for (char c : camel.toCharArray()) {
            if (Character.isUpperCase(c)) {
                sb.append('_').append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Los nodos vacíos no se escriben (Firebase los eliminaría igualmente). */
    private void colocar(Map<String, Object> raiz, String clave, Map<String, Object> seccion) {
        if (!seccion.isEmpty()) {
            raiz.put(clave, seccion);
        }
    }

    private List<Map<String, Object>> consultar(Connection conn, String sql, Object... params) throws SQLException {
        List<Map<String, Object>> filas = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                while (rs.next()) {
                    Map<String, Object> fila = new LinkedHashMap<>();
                    for (int i = 1; i <= md.getColumnCount(); i++) {
                        fila.put(md.getColumnName(i), rs.getObject(i));
                    }
                    filas.add(fila);
                }
            }
        }
        return filas;
    }

    private void ejecutar(Connection conn, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            ps.executeUpdate();
        }
    }

    private Set<String> columnas(Connection conn, String tabla) throws SQLException {
        Set<String> nombres = new HashSet<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("PRAGMA table_info(" + tabla + ")")) {
            while (rs.next()) {
                nombres.add(rs.getString("name"));
            }
        }
        return nombres;
    }

    /** Las tablas con dueño directo necesitan usuario_id, y movimientos necesita enlazar con recurrentes. */
    private void comprobarEsquema(Connection conn) {
        try {
            for (String tabla : List.of("cuentas", "categorias", "presupuestos", "movimientos")) {
                if (!columnas(conn, tabla).contains("usuario_id")) {
                    throw new IllegalStateException("La tabla '" + tabla + "' no tiene columna usuario_id; "
                            + "revisa assets/schema.sql (y borra miscuentas.db si venía de un esquema antiguo)");
                }
            }
            if (!columnas(conn, "movimientos").contains("recurrente_id")) {
                throw new IllegalStateException("La tabla 'movimientos' no tiene columna recurrente_id");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error comprobando el esquema local", e);
        }
    }

    private void exigirUid(Usuario usuario) {
        if (usuario == null || usuario.getFirebaseUid() == null || usuario.getFirebaseUid().isBlank()) {
            throw new IllegalArgumentException("El usuario no tiene firebase_uid");
        }
    }
}
