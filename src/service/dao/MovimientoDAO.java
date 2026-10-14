package service.dao;

import models.Movimiento;
import service.connections.DatabaseManager;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Clase del DAO para la tabla Movimientos
 * 
 * @author Fco. Padilla
 */
public class MovimientoDAO {

	/**
	 * Método que inserta un movimiento en la tabla
	 * @param m: movimiento a insertar
	 * @return Devuelve el movimiento insertado
	 */
    public Movimiento insertar(Movimiento m) {
        String sql = "INSERT INTO movimientos (usuario_id, cuenta_id, categoria_id, importe, tipo, fecha, descripcion, sincronizado, recurrente_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?)";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, m.getUsuarioId());
            ps.setInt(2, m.getCuentaId());
            ps.setInt(3, m.getCategoriaId());
            Dinero.escribir(ps, 4, m.getImporte());
            ps.setString(5, m.getTipo());
            ps.setString(6, m.getFecha());
            ps.setString(7, m.getDescripcion());
            if (m.getRecurrenteId() > 0) {
                ps.setInt(8, m.getRecurrenteId());
            } else {
                ps.setNull(8, java.sql.Types.INTEGER);
            }
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

    /**
     * Método que cre una lista de los movimientos de una cuenta especifica
     * @param cuentaId: id de la cuenta
     * @return Lista con movimientos de una cuenta
     */
    public List<Movimiento> listarPorCuenta(int cuentaId) {
        String sql = "SELECT * FROM movimientos WHERE cuenta_id = ? ORDER BY fecha DESC";
        return ejecutarListado(sql, cuentaId);
    }

    /**
     * Método que lista los movimientos por un usuario concreto
     * @param usuarioId: id del usuario
     * @return Lista con los movimientos de un usuario
     */
    public List<Movimiento> listarPorUsuario(int usuarioId) {
        String sql = "SELECT * FROM movimientos WHERE usuario_id = ? ORDER BY fecha DESC";
        return ejecutarListado(sql, usuarioId);
    }

    /**
     * Movimientos de un usuario en un rango de fechas (formato ISO yyyy-MM-dd).
     * @param usuarioId: id del usuario
     * @param fechaInicio: primera fecha del rango de fechas
     * @param fechaFin: segunda fecha del rango
     * @return Devuelve una lista de movimientos en el rango de fechas especificado
     */
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

    /**
     * Método que suma los gastos de un usuario por su categoria y mes
     * @param usuarioId: id del usuario
     * @param categoriaId: id de la categoria
     * @param mesReferencia: mes del gasto
     * @return Devuelve la suma de los gastos
     */
    public BigDecimal sumarGastosPorCategoriaYMes(int usuarioId, int categoriaId, String mesReferencia) {
        String sql = "SELECT COALESCE(SUM(importe), 0) AS total FROM movimientos " +
                "WHERE usuario_id = ? AND categoria_id = ? AND tipo = 'gasto' AND fecha LIKE ?";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, usuarioId);
            ps.setInt(2, categoriaId);
            ps.setString(3, mesReferencia + "%"); // mesReferencia con formato "yyyy-MM"
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Dinero.leer(rs, "total") : Dinero.CERO;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error sumando gastos por categoria", e);
        }
    }

    /**
     * Método que actualiza un movimiento
     * @param m: movimiento a actualizar
     */
    public void actualizar(Movimiento m) {
        String sql = "UPDATE movimientos SET cuenta_id = ?, categoria_id = ?, importe = ?, tipo = ?, fecha = ?, descripcion = ?, sincronizado = 0 WHERE id = ?";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, m.getCuentaId());
            ps.setInt(2, m.getCategoriaId());
            Dinero.escribir(ps, 3, m.getImporte());
            ps.setString(4, m.getTipo());
            ps.setString(5, m.getFecha());
            ps.setString(6, m.getDescripcion());
            ps.setInt(7, m.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error actualizando movimiento", e);
        }
    }

    /**
     * Método que elimina un movimiento
     * @param id: id del movimiento a eliminar
     */
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

    /**
     * Lista movimientos por parametro
     * @param sql: Sentencia sql
     * @param parametro: Parametro que añadir
     * @return Devuelve la lista de movimientos
     */
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

    /**
     * Crea un objeto movimiento con el resultado de una consulta
     * @param rs: Resultado de la consulta
     * @return Devuelve el movimiento creado
     * @throws SQLException
     */
    private Movimiento mapear(ResultSet rs) throws SQLException {
        return new Movimiento(
                rs.getInt("id"),
                rs.getInt("usuario_id"),
                rs.getInt("cuenta_id"),
                rs.getInt("categoria_id"),
                Dinero.leer(rs, "importe"),
                rs.getString("tipo"),
                rs.getString("fecha"),
                rs.getString("descripcion"),
                rs.getInt("sincronizado") == 1,
                rs.getInt("recurrente_id")
        );
    }
}
