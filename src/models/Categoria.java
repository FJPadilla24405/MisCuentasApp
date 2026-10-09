package models;

public class Categoria {

    private int id;
    private int usuarioId;
    private String nombre;
    private String tipo; // "ingreso" o "gasto"
    private String subcat;
    private boolean predefinida;

    public Categoria() {
    }

    public Categoria(int id, int usuarioId, String nombre, String tipo, String subcat, boolean predefinida) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.nombre = nombre;
        this.tipo = tipo;
        this.subcat = subcat;
        this.predefinida = predefinida;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(int usuarioId) {
        this.usuarioId = usuarioId;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getSubcategoria() {
        return subcat;
    }

    public void setSubcategoria(String icono) {
        this.subcat= icono;
    }

    public boolean isPredefinida() {
        return predefinida;
    }

    public void setPredefinida(boolean predefinida) {
        this.predefinida = predefinida;
    }

    @Override
    public String toString() {
        return "Categoria{id=" + id + ", nombre='" + nombre + "', tipo='" + tipo + "'}";
    }
}
