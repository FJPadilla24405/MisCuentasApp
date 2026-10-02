package dao;

import connections.DatabaseManager;
import models.Cuenta;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CuentaDAO {

    public Cuenta insertar(Cuenta c) {
        String sql = "INSERT INTO cuentas (usuario_id, nombre, tipo, saldo_inicial, moneda, activa) VALUES (?, ?, ?, ?, ?, ?)";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, c.getUsuarioId());
            ps.setString(2, c.getNombre());
            ps.setString(3, c.getTipo());
            ps.setDouble(4, c.getSaldoInicial());
            ps.setString(5, c.getMoneda());
            ps.setInt(6, c.isActiva() ? 1 : 0);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    c.setId(keys.getInt(1));
                }
            }
            return c;
        } catch (SQLException e) {
            throw new RuntimeException("Error insertando cuenta", e);
        }
    }

    public Optional<Cuenta> buscarPorId(int id) {
        String sql = "SELECT * FROM cuentas WHERE id = ?";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapear(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Error buscando cuenta", e);
        }
    }

    public List<Cuenta> listarPorUsuario(int usuarioId) {
        String sql = "SELECT * FROM cuentas WHERE usuario_id = ? AND activa = 1";
        List<Cuenta> lista = new ArrayList<>();
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
            throw new RuntimeException("Error listando cuentas", e);
        }
    }

    /**
     * Calcula el saldo actual de una cuenta a partir de su saldo inicial
     * más la suma de ingresos y menos la suma de gastos registrados.
     */
    public double calcularSaldo(int cuentaId) {
        String sql = "SELECT c.saldo_inicial + " +
                "COALESCE((SELECT SUM(importe) FROM movimientos WHERE cuenta_id = ? AND tipo = 'ingreso'), 0) - " +
                "COALESCE((SELECT SUM(importe) FROM movimientos WHERE cuenta_id = ? AND tipo = 'gasto'), 0) AS saldo " +
                "FROM cuentas c WHERE c.id = ?";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cuentaId);
            ps.setInt(2, cuentaId);
            ps.setInt(3, cuentaId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble("saldo") : 0.0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error calculando saldo", e);
        }
    }

    public void actualizar(Cuenta c) {
        String sql = "UPDATE cuentas SET nombre = ?, tipo = ?, saldo_inicial = ?, moneda = ?, activa = ? WHERE id = ?";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, c.getNombre());
            ps.setString(2, c.getTipo());
            ps.setDouble(3, c.getSaldoInicial());
            ps.setString(4, c.getMoneda());
            ps.setInt(5, c.isActiva() ? 1 : 0);
            ps.setInt(6, c.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error actualizando cuenta", e);
        }
    }

    /** Baja lógica: marca la cuenta como inactiva en lugar de borrarla, para no perder el histórico de movimientos. */
    public void desactivar(int id) {
        String sql = "UPDATE cuentas SET activa = 0 WHERE id = ?";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error desactivando cuenta", e);
        }
    }

    private Cuenta mapear(ResultSet rs) throws SQLException {
        return new Cuenta(
                rs.getInt("id"),
                rs.getInt("usuario_id"),
                rs.getString("nombre"),
                rs.getString("tipo"),
                rs.getDouble("saldo_inicial"),
                rs.getString("moneda"),
                rs.getInt("activa") == 1
        );
    }
}
