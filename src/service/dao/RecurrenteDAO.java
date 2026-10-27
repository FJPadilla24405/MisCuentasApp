package service.dao;

import models.Recurrente;
import service.connections.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Clase del DAO de la tabla Recurrentes
 * 
 * @author Fco Padilla
 */
public class RecurrenteDAO {

	/**
	 * Método que inserta una clase recurrente en la base de datos
	 * @param r: Recurrente a insertar
	 * @return Devuelve el recurrente que se ha insertado
	 */
    public Recurrente insertar(Recurrente r) {
        String sql = "INSERT INTO recurrentes (frecuencia, dia_ejecucion, activo, ultima_ejecucion) " +
                "VALUES (?, ?, ?, ?)";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, r.getFrecuencia());
            ps.setInt(2, r.getDiaEjecucion());
            ps.setInt(3, r.isActivo() ? 1 : 0);
            ps.setString(4, r.getUltimaEjecucion());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    r.setId(keys.getInt(1));
                }
            }
            return r;
        } catch (SQLException e) {
            throw new RuntimeException("Error insertando recurrente", e);
        }
    }

    /**
     * Lista los recurrentes activos de un usuario concreto
     * @param usuarioId: id del usuario
     * @return Devuelve una lista de movimientos recurrentes
     */
    public List<Recurrente> listarActivosPorUsuario(int usuarioId) {
        String sql = "SELECT * FROM recurrentes r WHERE r.activo = 1 " +
                "AND EXISTS (SELECT 1 FROM movimientos m WHERE m.recurrente_id = r.id AND m.usuario_id = ?)";;
        List<Recurrente> lista = new ArrayList<>();
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, usuarioId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
            }
            return lista;
        } catch (SQLException e) {
            throw new RuntimeException("Error listando recurrentes", e);
        }
    }

    /**
     * Método que lista los movimientos recurrentes que todavía no se han ejecutado un mes concreto
     * @param usuarioId: id del usuario
     * @param mesReferencia: mes que se ejecuta el recurrente
     * @return Devuelve una lista con movimientos recurrentes que no se han ejecutado
     */
    public List<Recurrente> listarPendientesDelMes(int usuarioId, String mesReferencia) {
        String sql = "SELECT * FROM recurrentes r WHERE r.activo = 1 " +
                "AND EXISTS (SELECT 1 FROM movimientos m WHERE m.recurrente_id = r.id AND m.usuario_id = ?) " +
                "AND (r.ultima_ejecucion IS NULL OR r.ultima_ejecucion NOT LIKE ?)";
        List<Recurrente> lista = new ArrayList<>();
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, usuarioId);
            ps.setString(2, mesReferencia + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
            }
            return lista;
        } catch (SQLException e) {
            throw new RuntimeException("Error listando recurrentes pendientes", e);
        }
    }

    /**
     * Método que marca un movimiento recurrente como completado
     * @param id: id del movimiento recurrente
     * @param fechaEjecucion: fecha en la que se ejecuta el movimiento
     */
    public void marcarEjecutado(int id, String fechaEjecucion) {
        String sql = "UPDATE recurrentes SET ultima_ejecucion = ? WHERE id = ?";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fechaEjecucion);
            ps.setInt(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error marcando recurrente como ejecutado", e);
        }
    }

    /**
     * Método que actualiza los datos de un movimiento recurrente
     * @param r: movimiento recurrente con datos actualizados
     */
    public void actualizar(Recurrente r) {
        String sql = "UPDATE recurrentes SET frecuencia = ?, dia_ejecucion = ?, activo = ? WHERE id = ?";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, r.getFrecuencia());
            ps.setInt(2, r.getDiaEjecucion());
            ps.setInt(3, r.isActivo() ? 1 : 0);
            ps.setInt(4, r.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error actualizando recurrente", e);
        }
    }

    /**
     * Método que desactiva un movimiento recurrente
     * @param id: id del movimiento
     */
    public void desactivar(int id) {
        String sql = "UPDATE recurrentes SET activo = 0 WHERE id = ?";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error desactivando recurrente", e);
        }
    }

    /**
     * Método que crea un movimiento recurrente con los resultados de una consulta
     * @param rs: resultado de la consulta
     * @return Devuelve un objeto recurrente con los datos de la consulta
     * @throws SQLException
     */
    private Recurrente mapear(ResultSet rs) throws SQLException {
        return new Recurrente(
                rs.getInt("id"),
                rs.getString("frecuencia"),
                rs.getInt("dia_ejecucion"),
                rs.getInt("activo") == 1,
                rs.getString("ultima_ejecucion")
        );
    }
}
