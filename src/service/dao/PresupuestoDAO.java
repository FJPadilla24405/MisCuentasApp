package service.dao;

import models.Presupuesto;
import service.connections.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Clase del DAO para la tabla Movimientos
 * 
 * @author Fco. Padilla
 */
public class PresupuestoDAO {

	/**
	 * Método que inserta un presupuesto
	 * @param p: presupuesto a insertar
	 * @return Devuelve el presupuesto insertado
	 */
    public Presupuesto insertar(Presupuesto p) {
        String sql = "INSERT INTO presupuestos (usuario_id, categoria_id, limite, periodo, mes_referencia) VALUES (?, ?, ?, ?, ?)";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, p.getUsuarioId());
            ps.setInt(2, p.getCategoriaId());
            Dinero.escribir(ps, 3, p.getLimite());
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

    /**
     * Lista los presupuestos de un usuario concreto de un mes
     * @param usuarioId: id del usuario
     * @param mesReferencia: mes en el que esta activo el presupuesto
     * @return Devuelve la lista de presupuestos del usuario de un mismo mes
     */
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

    /**
     * Método que puede o no devolver un presupuesto de un usuario concreto en una categoria y mes especifico
     * @param usuarioId: id del usuario
     * @param categoriaId: id de la categoria
     * @param mesReferencia: mes del presupuesto
     * @return Devuelve un presupuesto si existe
     */
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

    /**
     * Actualiza los datos de un presupuesto en la base de datos
     * @param p: Presupuesto con datos nuevos
     */
    public void actualizar(Presupuesto p) {
        String sql = "UPDATE presupuestos SET limite = ?, periodo = ? WHERE id = ?";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            Dinero.escribir(ps, 1, p.getLimite());
            ps.setString(2, p.getPeriodo());
            ps.setInt(3, p.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error actualizando presupuesto", e);
        }
    }

    /**
     * Elimina un presupuesto de la base de datos
     * @param id: id del presupuesto
     */
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

    /**
     * Crea un objeto presupuesto con los resultados de una consulta
     * @param rs: resultado de la consulta
     * @return Devuelve un presupuesto
     * @throws SQLException
     */
    private Presupuesto mapear(ResultSet rs) throws SQLException {
        return new Presupuesto(
                rs.getInt("id"),
                rs.getInt("usuario_id"),
                rs.getInt("categoria_id"),
                Dinero.leer(rs, "limite"),
                rs.getString("periodo"),
                rs.getString("mes_referencia")
        );
    }
}
