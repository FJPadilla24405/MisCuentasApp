package service.categoria;

import java.util.List;

import models.Categoria;
import models.Usuario;
import service.dao.CategoriaDAO;

/**
 * Categorías de partida. Un usuario nuevo no tiene ninguna, y hacen falta para crear presupuestos y
 * movimientos, así que la primera vez que se necesitan se crean estas.
 *
 * @author Fco Padilla
 */
public class CategoriaService {

    // Categorías de gasto que se crean al principio
    private static final List<String> GASTOS = List.of("Vivienda", "Alimentación", "Compras", "Servicios", "Transporte", "Ocio", "Salud");
    // Categorías de ingreso que se crean al principio
    private static final List<String> INGRESOS = List.of("Nómina", "Otros ingresos");

    private final CategoriaDAO categoriaDAO = new CategoriaDAO();

    /**
     * Crea las categorías de partida si el usuario no tiene ninguna. Si ya tiene alguna (sea cual sea) no
     * hace nada, para no volver a crear las que haya borrado.
     * @param usuario: usuario de la sesión
     */
    public void asegurarPredefinidas(Usuario usuario) {
        if (!categoriaDAO.listarPorUsuario(usuario.getId()).isEmpty()) {
            return;
        }
        for (String nombre : GASTOS) {
            categoriaDAO.insertar(new Categoria(0, usuario.getId(), nombre, "gasto", null, true));
        }
        for (String nombre : INGRESOS) {
            categoriaDAO.insertar(new Categoria(0, usuario.getId(), nombre, "ingreso", null, true));
        }
    }

    /**
     * Categorías de gasto del usuario (las que admiten presupuesto), creando las de partida si no tiene ninguna
     * @param usuario: usuario de la sesión
     * @return Devuelve las categorías de gasto
     */
    public List<Categoria> categoriasDeGasto(Usuario usuario) {
        asegurarPredefinidas(usuario);
        return categoriaDAO.listarPorUsuarioYTipo(usuario.getId(), "gasto");
    }
}
