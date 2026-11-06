package service.categoria;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import models.Categoria;
import models.Usuario;
import service.DatoInvalido;
import service.dao.CategoriaDAO;
import service.dao.PresupuestoDAO;

/**
 * Categorías del usuario: las predefinidas (se crean al registrar al usuario, con crearPredefinidas) y crear,
 * editar y eliminar categorías propias. Para cuentas anteriores a ese cambio, o a las que les falte un tipo
 * entero de categorías, los métodos que ofrecen categorías las completan al usarlas.
 *
 * @author Fco Padilla
 */
public class CategoriaService {

    // Categorías de gasto que se crean al principio
    private static final List<String> GASTOS = List.of("Vivienda", "Alimentación", "Compras", "Servicios", "Transporte", "Ocio", "Salud");
    // Categorías de ingreso que se crean al principio
    private static final List<String> INGRESOS = List.of("Nómina", "Otros ingresos");

    // Longitud máxima de nombre y subcategoría, la misma que los VARCHAR(50) del esquema
    private static final int MAX = 50;

    /**
     * Cuántas cosas usan una categoría.
     * @param movimientos: movimientos con esa categoría
     * @param presupuestos: presupuestos de esa categoría
     */
    public record Usos(int movimientos, int presupuestos) {

        /**
         * @return Devuelve true si algo usa la categoría
         */
        public boolean enUso() {
            return movimientos > 0 || presupuestos > 0;
        }
    }

    private final CategoriaDAO categoriaDAO = new CategoriaDAO();
    private final PresupuestoDAO presupuestoDAO = new PresupuestoDAO();

    /**
     * Crea las categorías predefinidas (las de gasto y las de ingreso) de un usuario. Se llama justo al
     * registrarlo, para que las tenga desde el principio.
     * @param usuario: usuario recién creado
     */
    public void crearPredefinidas(Usuario usuario) {
        for (String nombre : GASTOS) {
            categoriaDAO.insertar(new Categoria(0, usuario.getId(), nombre, "gasto", null, true));
        }
        for (String nombre : INGRESOS) {
            categoriaDAO.insertar(new Categoria(0, usuario.getId(), nombre, "ingreso", null, true));
        }
    }

    /**
     * Red de seguridad para usuarios que no tienen ninguna categoría (cuentas creadas antes de que las
     * predefinidas se crearan al registrarse): se las crea. Si ya tiene alguna, no hace nada, para no
     * volver a crear las que haya borrado.
     * @param usuario: usuario de la sesión
     */
    public void asegurarPredefinidas(Usuario usuario) {
        if (categoriaDAO.listarPorUsuario(usuario.getId()).isEmpty()) {
            crearPredefinidas(usuario);
        }
    }

    /**
     * Crea las categorías de partida de un tipo si el usuario no tiene ninguna de ese tipo. Hace falta al
     * elegir categoría en un movimiento o un presupuesto: sin ninguna de ese tipo no se podría crear (por
     * ejemplo, un usuario que solo tiene categorías de gasto no podría añadir ningún ingreso).
     * @param usuario: usuario de la sesión
     * @param tipo: "gasto" o "ingreso"
     */
    private void asegurarPredefinidasDeTipo(Usuario usuario, String tipo) {
        if (!categoriaDAO.listarPorUsuarioYTipo(usuario.getId(), tipo).isEmpty()) {
            return;
        }
        for (String nombre : "ingreso".equals(tipo) ? INGRESOS : GASTOS) {
            categoriaDAO.insertar(new Categoria(0, usuario.getId(), nombre, tipo, null, true));
        }
    }

    /**
     * Categorías de gasto del usuario (las que admiten presupuesto), creando las de partida si no tiene ninguna
     * @param usuario: usuario de la sesión
     * @return Devuelve las categorías de gasto
     */
    public List<Categoria> categoriasDeGasto(Usuario usuario) {
        asegurarPredefinidas(usuario);
        asegurarPredefinidasDeTipo(usuario, "gasto");
        return categoriaDAO.listarPorUsuarioYTipo(usuario.getId(), "gasto");
    }

    /**
     * Todas las categorías del usuario, primero las de gasto y luego las de ingreso, cada grupo por nombre
     * @param usuario: usuario de la sesión
     * @return Devuelve las categorías
     */
    public List<Categoria> listar(Usuario usuario) {
        asegurarPredefinidas(usuario);
        return categoriaDAO.listarPorUsuario(usuario.getId()).stream()
                .sorted(Comparator.comparing((Categoria c) -> !"gasto".equals(c.getTipo()))
                        .thenComparing(c -> c.getNombre().toLowerCase(Locale.ROOT))
                        .thenComparing(c -> c.getSubcategoria() == null ? "" : c.getSubcategoria().toLowerCase(Locale.ROOT)))
                .toList();
    }

    /**
     * Categorías de un tipo, creando las de partida si el usuario no tiene ninguna
     * @param usuario: usuario de la sesión
     * @param tipo: "gasto" o "ingreso"
     * @return Devuelve las categorías de ese tipo
     */
    public List<Categoria> categoriasDeTipo(Usuario usuario, String tipo) {
        asegurarPredefinidas(usuario);
        asegurarPredefinidasDeTipo(usuario, tipo);
        return categoriaDAO.listarPorUsuarioYTipo(usuario.getId(), tipo);
    }

    /**
     * @param categoria: categoría
     * @return Devuelve cuántos movimientos y presupuestos la usan
     */
    public Usos usos(Categoria categoria) {
        return new Usos(categoriaDAO.contarMovimientos(categoria.getId()), categoriaDAO.contarPresupuestos(categoria.getId()));
    }

    /**
     * Crea una categoría o modifica una existente. Si algo no es válido no se guarda nada. Una categoría que
     * ya se usa no puede cambiar de tipo (sus movimientos y presupuestos dejarían de tener sentido).
     * @param usuario: usuario de la sesión
     * @param existente: categoría a modificar, o null para crear una nueva
     * @param nombre: nombre de la categoría
     * @param tipo: "gasto" o "ingreso"
     * @param subcategoria: subcategoría opcional (puede ser null o vacía)
     * @return Devuelve la categoría guardada
     * @throws DatoInvalido si algún dato no es válido o ya existe una igual
     */
    public Categoria guardar(Usuario usuario, Categoria existente, String nombre, String tipo, String subcategoria) {
        String nombreLimpio = nombre == null ? "" : nombre.trim();
        if (nombreLimpio.isEmpty() || nombreLimpio.length() > MAX) {
            throw new DatoInvalido("nombre", nombreLimpio.isEmpty() ? "Escribe un nombre para la categoría."
                    : "El nombre no puede superar " + MAX + " caracteres.");
        }
        String sub = subcategoria == null || subcategoria.isBlank() ? null : subcategoria.trim();
        if (sub != null && sub.length() > MAX) {
            throw new DatoInvalido("subcategoria", "La subcategoría no puede superar " + MAX + " caracteres.");
        }
        if (!"gasto".equals(tipo) && !"ingreso".equals(tipo)) {
            throw new DatoInvalido("tipo", "Elige si es de gasto o de ingreso.");
        }
        if (existente != null && !tipo.equals(existente.getTipo()) && usos(existente).enUso()) {
            throw new DatoInvalido("tipo", "No se puede cambiar el tipo porque la categoría ya tiene movimientos o presupuestos.");
        }
        boolean repetida = categoriaDAO.listarPorUsuario(usuario.getId()).stream()
                .anyMatch(c -> (existente == null || c.getId() != existente.getId())
                        && c.getTipo().equals(tipo)
                        && c.getNombre().trim().equalsIgnoreCase(nombreLimpio)
                        && igual(c.getSubcategoria(), sub));
        if (repetida) {
            throw new DatoInvalido("nombre", "Ya tienes una categoría de " + tipo + " con ese nombre"
                    + (sub == null ? "." : " y esa subcategoría."));
        }

        Categoria c = existente == null ? new Categoria(0, usuario.getId(), nombreLimpio, tipo, sub, false) : existente;
        c.setNombre(nombreLimpio);
        c.setTipo(tipo);
        c.setSubcategoria(sub);
        if (existente == null) {
            categoriaDAO.insertar(c);
        } else {
            categoriaDAO.actualizar(c);
        }
        return c;
    }

    /**
     * Elimina una categoría. No se puede si tiene movimientos (se perderían sus datos); si solo tiene
     * presupuestos, se eliminan con ella.
     * @param categoria: categoría a eliminar
     * @throws DatoInvalido si tiene movimientos
     */
    public void eliminar(Categoria categoria) {
        Usos usos = usos(categoria);
        if (usos.movimientos() > 0) {
            throw new DatoInvalido("categoria", "No se puede eliminar \"" + categoria.getNombre() + "\" porque tiene "
                    + usos.movimientos() + (usos.movimientos() == 1 ? " movimiento." : " movimientos."));
        }
        presupuestoDAO.eliminarPorCategoria(categoria.getId());
        categoriaDAO.eliminar(categoria.getId());
    }

    /**
     * @param a: texto o null
     * @param b: texto o null
     * @return Devuelve true si son iguales sin mirar mayúsculas (null y vacío cuentan como iguales)
     */
    private boolean igual(String a, String b) {
        String x = a == null ? "" : a.trim();
        String y = b == null ? "" : b.trim();
        return x.equalsIgnoreCase(y);
    }
}
