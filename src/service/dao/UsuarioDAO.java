package service.dao;

import models.Usuario;
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
 * Clase que representa el DAO de la tabla Usuarios
 * 
 * @author Fco Padilla
 */
public class UsuarioDAO {

	/**
	 * Método que inserta un usuario
	 * @param u: usuario a insertar
	 * @return Devuelve el usuario insertado
	 */
    public Usuario insertar(Usuario u) {
        String sql = "INSERT INTO usuarios (firebase_uid, nombre, email, moneda, tema) VALUES (?, ?, ?, ?, ?)";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, u.getFirebaseUid());
            ps.setString(2, u.getNombre());
            ps.setString(3, u.getEmail());
            ps.setString(4, u.getMoneda());
            ps.setString(5, u.getTema());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    u.setId(keys.getInt(1));
                }
            }
            return u;
        } catch (SQLException e) {
            throw new RuntimeException("Error insertando usuario", e);
        }
    }

    /**
     * Busca un usuario por su firebase uid
     * @param firebaseUid: el id del usuario en firebase
     * @return Usuario con el uid de firebase especificado si existe
     */
    public Optional<Usuario> buscarPorFirebaseUid(String firebaseUid) {
        String sql = "SELECT * FROM usuarios WHERE firebase_uid = ?";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, firebaseUid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapear(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Error buscando usuario", e);
        }
    }

    /**
     * Busca un usuario por su email (sin distinguir mayúsculas)
     * @param email: correo del usuario
     * @return Usuario con ese email si existe
     */
    public Optional<Usuario> buscarPorEmail(String email) {
        String sql = "SELECT * FROM usuarios WHERE LOWER(email) = LOWER(?)";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapear(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Error buscando usuario por email", e);
        }
    }

    /**
     * Busca un usuario por su id
     * @param id: id del usuario
     * @return Usuario con el id especificado si existe
     */
    public Optional<Usuario> buscarPorId(int id) {
        String sql = "SELECT * FROM usuarios WHERE id = ?";
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
            throw new RuntimeException("Error buscando usuario", e);
        }
    }

    /**
     * Lista todos los usuarios de la base de datos
     * @return Devuelve una lista de todos los usuarios registrados
     */
    public List<Usuario> listarTodos() {
        String sql = "SELECT * FROM usuarios";
        List<Usuario> lista = new ArrayList<>();
        Connection conn = DatabaseManager.getConnection();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                lista.add(mapear(rs));
            }
            return lista;
        } catch (SQLException e) {
            throw new RuntimeException("Error listando usuarios", e);
        }
    }

    /**
     * Actualiza los datos de un usuario en la base de datos
     * @param u: usuario con datos nuevos
     */
    public void actualizar(Usuario u) {
        String sql = "UPDATE usuarios SET nombre = ?, email = ?, moneda = ?, tema = ? WHERE id = ?";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, u.getNombre());
            ps.setString(2, u.getEmail());
            ps.setString(3, u.getMoneda());
            ps.setString(4, u.getTema());
            ps.setInt(5, u.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error actualizando usuario", e);
        }
    }

    /**
     * Elimina un usuario de la base de datos
     * @param id: id del usuario
     */
    public void eliminar(int id) {
        String sql = "DELETE FROM usuarios WHERE id = ?";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error eliminando usuario", e);
        }
    }

    /**
     * Método que crea un usuario con los resultados de la consulta
     * @param rs: Resultado de la consulta
     * @return Devuelve un usuario
     * @throws SQLException
     */
    private Usuario mapear(ResultSet rs) throws SQLException {
        return new Usuario(
                rs.getInt("id"),
                rs.getString("firebase_uid"),
                rs.getString("nombre"),
                rs.getString("email"),
                rs.getString("moneda"),
                rs.getString("tema")
        );
    }
}
