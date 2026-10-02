package dao;

import connections.DatabaseManager;
import models.Categoria;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Clase del DAO para la tabla Categorias
 * 
 * @author Fco. Padilla
 */
public class CategoriaDAO {

	/**
	 * Método que inserta una categoría en la base de datos con un prepared statement.
	 * @param c: Categoría a insertar.
	 * @return Devuelve la categoría introducida en la base de datos.
	 */
    public Categoria insertar(Categoria c) {
        String sql = "INSERT INTO categorias (usuario_id, nombre, tipo, icono, predefinida) VALUES (?, ?, ?, ?, ?)";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, c.getUsuarioId());
            ps.setString(2, c.getNombre());
            ps.setString(3, c.getTipo());
            ps.setString(4, c.getIcono());
            ps.setInt(5, c.isPredefinida() ? 1 : 0);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    c.setId(keys.getInt(1));
                }
            }
            return c;
        } catch (SQLException e) {
            throw new RuntimeException("Error insertando categoria", e);
        }
    }

    /**
     * Método que saca las Categorías de un usuario concreto.
     * @param usuarioId: Id del usuario.
     * @return Devuelve una lista de categorías.
     */
    public List<Categoria> listarPorUsuario(int usuarioId) {
        String sql = "SELECT * FROM categorias WHERE usuario_id = ?";
        List<Categoria> lista = new ArrayList<>();
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
            throw new RuntimeException("Error listando categorias", e);
        }
    }

    /**
     * Método que saca las categorías de un usuario concreto y tipo específico.
     * @param usuarioId: Id del usuario.
     * @param tipo: Tipo de categoría (ingreso o gasto)
     * @return Devuelve una lista de categorías.
     */
    public List<Categoria> listarPorUsuarioYTipo(int usuarioId, String tipo) {
        String sql = "SELECT * FROM categorias WHERE usuario_id = ? AND tipo = ?";
        List<Categoria> lista = new ArrayList<>();
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, usuarioId);
            ps.setString(2, tipo);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
            }
            return lista;
        } catch (SQLException e) {
            throw new RuntimeException("Error listando categorias", e);
        }
    }

    /**
     * Actualiza una categoría con datos nuevos.
     * @param c: Categoría para actualizar.
     */
    public void actualizar(Categoria c) {
        String sql = "UPDATE categorias SET nombre = ?, tipo = ?, icono = ? WHERE id = ?";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, c.getNombre());
            ps.setString(2, c.getTipo());
            ps.setString(3, c.getIcono());
            ps.setInt(4, c.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error actualizando categoria", e);
        }
    }

    /**
     * Elimina una categoría por su id.
     * @param id: Id de la categoría para eliminar
     */
    public void eliminar(int id) {
        String sql = "DELETE FROM categorias WHERE id = ?";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error eliminando categoria", e);
        }
    }

    private Categoria mapear(ResultSet rs) throws SQLException {
        return new Categoria(
                rs.getInt("id"),
                rs.getInt("usuario_id"),
                rs.getString("nombre"),
                rs.getString("tipo"),
                rs.getString("icono"),
                rs.getInt("predefinida") == 1
        );
    }
}
