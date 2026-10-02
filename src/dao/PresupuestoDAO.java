package dao;

import connections.DatabaseManager;
import models.Presupuesto;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PresupuestoDAO {

    public Presupuesto insertar(Presupuesto p) {
        String sql = "INSERT INTO presupuestos (usuario_id, categoria_id, limite, periodo, mes_referencia) VALUES (?, ?, ?, ?, ?)";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, p.getUsuarioId());
            ps.setInt(2, p.getCategoriaId());
            ps.setDouble(3, p.getLimite());
            ps.setString(4, p.getPeriodo());
            ps.setString(5, p.getMesReferencia());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    p.setId(keys.getInt(1));
                }
            }
            return p;
        } catch (SQLException e) {
            throw new RuntimeException("Error insertando presupuesto", e);
        }
    }

    public List<Presupuesto> listarPorUsuarioYMes(int usuarioId, String mesReferencia) {
        String sql = "SELECT * FROM presupuestos WHERE usuario_id = ? AND mes_referencia = ?";
        List<Presupuesto> lista = new ArrayList<>();
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, usuarioId);
            ps.setString(2, mesReferencia);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
            }
            return lista;
        } catch (SQLException e) {
            throw new RuntimeException("Error listando presupuestos", e);
        }
    }

    public Optional<Presupuesto> buscarPorCategoriaYMes(int usuarioId, int categoriaId, String mesReferencia) {
        String sql = "SELECT * FROM presupuestos WHERE usuario_id = ? AND categoria_id = ? AND mes_referencia = ?";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, usuarioId);
            ps.setInt(2, categoriaId);
            ps.setString(3, mesReferencia);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapear(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Error buscando presupuesto", e);
        }
    }

    public void actualizar(Presupuesto p) {
        String sql = "UPDATE presupuestos SET limite = ?, periodo = ? WHERE id = ?";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, p.getLimite());
            ps.setString(2, p.getPeriodo());
            ps.setInt(3, p.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error actualizando presupuesto", e);
        }
    }

    public void eliminar(int id) {
        String sql = "DELETE FROM presupuestos WHERE id = ?";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error eliminando presupuesto", e);
        }
    }

    private Presupuesto mapear(ResultSet rs) throws SQLException {
        return new Presupuesto(
                rs.getInt("id"),
                rs.getInt("usuario_id"),
                rs.getInt("categoria_id"),
                rs.getDouble("limite"),
                rs.getString("periodo"),
                rs.getString("mes_referencia")
        );
    }
}
