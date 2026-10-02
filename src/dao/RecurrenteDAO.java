package dao;

import connections.DatabaseManager;
import models.Recurrente;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class RecurrenteDAO {

    public Recurrente insertar(Recurrente r) {
        String sql = "INSERT INTO recurrentes (usuario_id, cuenta_id, categoria_id, importe, tipo, frecuencia, dia_ejecucion, activo, ultima_ejecucion) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, r.getUsuarioId());
            ps.setInt(2, r.getCuentaId());
            ps.setInt(3, r.getCategoriaId());
            ps.setDouble(4, r.getImporte());
            ps.setString(5, r.getTipo());
            ps.setString(6, r.getFrecuencia());
            ps.setInt(7, r.getDiaEjecucion());
            ps.setInt(8, r.isActivo() ? 1 : 0);
            ps.setString(9, r.getUltimaEjecucion());
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

    public List<Recurrente> listarActivosPorUsuario(int usuarioId) {
        String sql = "SELECT * FROM recurrentes WHERE usuario_id = ? AND activo = 1";
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
     * Recurrentes activos cuya "ultima_ejecucion" no coincide con el mes actual,
     * es decir, los que tocaría generar como movimiento hoy. La comprobación de
     * "¿toca ya?" según el día del mes se hace en la capa de servicio/controlador,
     * que es quien conoce la fecha actual y decide si llamar a MovimientoDAO.
     */
    public List<Recurrente> listarPendientesDelMes(int usuarioId, String mesReferencia) {
        String sql = "SELECT * FROM recurrentes WHERE usuario_id = ? AND activo = 1 " +
                "AND (ultima_ejecucion IS NULL OR ultima_ejecucion NOT LIKE ?)";
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

    public void actualizar(Recurrente r) {
        String sql = "UPDATE recurrentes SET cuenta_id = ?, categoria_id = ?, importe = ?, tipo = ?, frecuencia = ?, dia_ejecucion = ?, activo = ? WHERE id = ?";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, r.getCuentaId());
            ps.setInt(2, r.getCategoriaId());
            ps.setDouble(3, r.getImporte());
            ps.setString(4, r.getTipo());
            ps.setString(5, r.getFrecuencia());
            ps.setInt(6, r.getDiaEjecucion());
            ps.setInt(7, r.isActivo() ? 1 : 0);
            ps.setInt(8, r.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error actualizando recurrente", e);
        }
    }

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

    private Recurrente mapear(ResultSet rs) throws SQLException {
        return new Recurrente(
                rs.getInt("id"),
                rs.getInt("usuario_id"),
                rs.getInt("cuenta_id"),
                rs.getInt("categoria_id"),
                rs.getDouble("importe"),
                rs.getString("tipo"),
                rs.getString("frecuencia"),
                rs.getInt("dia_ejecucion"),
                rs.getInt("activo") == 1,
                rs.getString("ultima_ejecucion")
        );
    }
}
