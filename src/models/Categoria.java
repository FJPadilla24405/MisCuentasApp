package models;

/**
 * Categoría de ingresos o gastos de un usuario (tabla categorias). Los movimientos y los presupuestos
 * se asignan a una categoría, que puede tener una subcategoría.
 *
 * @author Fco Padilla
 */
public class Categoria {

    // Identificador local (clave primaria autoincremental de SQLite)
    private int id;
    // Id del usuario dueño de la categoría
    private int usuarioId;
    // Nombre de la categoría (máximo 50 caracteres en la base de datos)
    private String nombre;
    private String tipo; // "ingreso" o "gasto"
    // Subcategoría opcional (null si no tiene; columna subcat de la base de datos)
    private String subcat;
    // true si es una categoría predefinida de la aplicación y no una creada por el usuario
    private boolean predefinida;

    /**
     * Constructor vacío
     */
    public Categoria() {
    }

    /**
     * Crea una categoría con todos sus datos.
     * @param id: identificador local de la categoría
     * @param usuarioId: id del usuario dueño de la categoría
     * @param nombre: nombre de la categoría
     * @param tipo: "ingreso" o "gasto"
     * @param subcat: subcategoría (puede ser null)
     * @param predefinida: true si es una categoría predefinida
     */
    public Categoria(int id, int usuarioId, String nombre, String tipo, String subcat, boolean predefinida) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.nombre = nombre;
        this.tipo = tipo;
        this.subcat = subcat;
        this.predefinida = predefinida;
    }

    /**
     * @return Devuelve el identificador local de la categoría
     */
    public int getId() {
        return id;
    }

    /**
     * @param id: identificador local de la categoría
     */
    public void setId(int id) {
        this.id = id;
    }

    /**
     * @return Devuelve el id del usuario dueño de la categoría
     */
    public int getUsuarioId() {
        return usuarioId;
    }

    /**@param usuarioId: id del usuario dueño de la categoría
     * 
     */
    public void setUsuarioId(int usuarioId) {
        this.usuarioId = usuarioId;
    }

    /**
     * @return Devuelve el nombre de la categoría
     */
    public String getNombre() {
        return nombre;
    }

    /**
     * @param nombre: nombre de la categoría
     */
    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    /**
     * @return Devuelve el tipo de la categoría ("ingreso" o "gasto")
     */
    public String getTipo() {
        return tipo;
    }

    /**
     * @param tipo: "ingreso" o "gasto"
     */
    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    /**
     * @return Devuelve la subcategoría (null si no tiene)
     */
    public String getSubcategoria() {
        return subcat;
    }

    /**
     * @param subcat: subcategoría a guardar
     */
    public void setSubcategoria(String subcat) {
        this.subcat = subcat;
    }

    /**
     * @return Devuelve true si es una categoría predefinida de la aplicación
     */
    public boolean isPredefinida() {
        return predefinida;
    }

    /**
     * @param predefinida: true si es una categoría predefinida
     */
    public void setPredefinida(boolean predefinida) {
        this.predefinida = predefinida;
    }

    /**
     * @return Devuelve un texto corto con el id, el nombre y el tipo de la categoría
     */
    @Override
    public String toString() {
        return "Categoria{id=" + id + ", nombre='" + nombre + "', tipo='" + tipo + "'}";
    }
}
