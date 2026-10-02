package dao;

import connections.DatabaseManager;
import models.Movimiento;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class MovimientoDAO {

    public Movimiento insertar(Movimiento m) {
        String sql = "INSERT INTO movimientos (usuario_id, cuenta_id, categoria_id, importe, tipo, fecha, descripcion, sincronizado) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, 0)";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, m.getUsuarioId());
            ps.setInt(2, m.getCuentaId());
            ps.setInt(3, m.getCategoriaId());
            ps.setDouble(4, m.getImporte());
            ps.setString(5, m.getTipo());
            ps.setString(6, m.getFecha());
            ps.setString(7, m.getDescripcion());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    m.setId(keys.getInt(1));
                }
            }
            return m;
        } catch (SQLException e) {
            throw new RuntimeException("Error insertando movimiento", e);
        }
    }

    public List<Movimiento> listarPorCuenta(int cuentaId) {
        String sql = "SELECT * FROM movimientos WHERE cuenta_id = ? ORDER BY fecha DESC";
        return ejecutarListado(sql, cuentaId);
    }

    public List<Movimiento> listarPorUsuario(int usuarioId) {
        String sql = "SELECT * FROM movimientos WHERE usuario_id = ? ORDER BY fecha DESC";
        return ejecutarListado(sql, usuarioId);
    }

    /** Movimientos de un usuario en un rango de fechas (formato ISO yyyy-MM-dd), útil para informes mensuales. */
    public List<Movimiento> listarPorUsuarioYRango(int usuarioId, String fechaInicio, String fechaFin) {
        String sql = "SELECT * FROM movimientos WHERE usuario_id = ? AND fecha BETWEEN ? AND ? ORDER BY fecha DESC";
        List<Movimiento> lista = new ArrayList<>();
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, usuarioId);
            ps.setString(2, fechaInicio);
            ps.setString(3, fechaFin);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
            }
            return lista;
        } catch (SQLException e) {
            throw new RuntimeException("Error listando movimientos por rango", e);
        }
    }

    /** Suma de gastos de un usuario en una categoría durante un mes concreto (para comparar contra el presupuesto). */
    public double sumarGastosPorCategoriaYMes(int usuarioId, int categoriaId, String mesReferencia) {
        String sql = "SELECT COALESCE(SUM(importe), 0) AS total FROM movimientos " +
                "WHERE usuario_id = ? AND categoria_id = ? AND tipo = 'gasto' AND fecha LIKE ?";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, usuarioId);
            ps.setInt(2, categoriaId);
            ps.setString(3, mesReferencia + "%"); // mesReferencia con formato "yyyy-MM"
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble("total") : 0.0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error sumando gastos por categoria", e);
        }
    }

    public void actualizar(Movimiento m) {
        String sql = "UPDATE movimientos SET cuenta_id = ?, categoria_id = ?, importe = ?, tipo = ?, fecha = ?, descripcion = ?, sincronizado = 0 WHERE id = ?";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, m.getCuentaId());
            ps.setInt(2, m.getCategoriaId());
            ps.setDouble(3, m.getImporte());
            ps.setString(4, m.getTipo());
            ps.setString(5, m.getFecha());
            ps.setString(6, m.getDescripcion());
            ps.setInt(7, m.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error actualizando movimiento", e);
        }
    }

    public void eliminar(int id) {
        String sql = "DELETE FROM movimientos WHERE id = ?";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error eliminando movimiento", e);
        }
    }

    private List<Movimiento> ejecutarListado(String sql, int parametro) {
        List<Movimiento> lista = new ArrayList<>();
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, parametro);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
            }
            return lista;
        } catch (SQLException e) {
            throw new RuntimeException("Error listando movimientos", e);
        }
    }

    private Movimiento mapear(ResultSet rs) throws SQLException {
        return new Movimiento(
                rs.getInt("id"),
                rs.getInt("usuario_id"),
                rs.getInt("cuenta_id"),
                rs.getInt("categoria_id"),
                rs.getDouble("importe"),
                rs.getString("tipo"),
                rs.getString("fecha"),
                rs.getString("descripcion"),
                rs.getInt("sincronizado") == 1
        );
    }
}
