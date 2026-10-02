package models;

public class Movimiento {

    private int id;
    private int usuarioId;
    private int cuentaId;
    private int categoriaId;
    private double importe;
    private String tipo; // "ingreso" o "gasto"
    private String fecha; // formato ISO: yyyy-MM-dd
    private String descripcion;
    private boolean sincronizado;

    public Movimiento() {
    }

    public Movimiento(int id, int usuarioId, int cuentaId, int categoriaId, double importe,
                       String tipo, String fecha, String descripcion, boolean sincronizado) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.cuentaId = cuentaId;
        this.categoriaId = categoriaId;
        this.importe = importe;
        this.tipo = tipo;
        this.fecha = fecha;
        this.descripcion = descripcion;
        this.sincronizado = sincronizado;
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

    public int getCuentaId() {
        return cuentaId;
    }

    public void setCuentaId(int cuentaId) {
        this.cuentaId = cuentaId;
    }

    public int getCategoriaId() {
        return categoriaId;
    }

    public void setCategoriaId(int categoriaId) {
        this.categoriaId = categoriaId;
    }

    public double getImporte() {
        return importe;
    }

    public void setImporte(double importe) {
        this.importe = importe;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getFecha() {
        return fecha;
    }

    public void setFecha(String fecha) {
        this.fecha = fecha;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public boolean isSincronizado() {
        return sincronizado;
    }

    public void setSincronizado(boolean sincronizado) {
        this.sincronizado = sincronizado;
    }

    @Override
    public String toString() {
        return "Movimiento{id=" + id + ", importe=" + importe + ", tipo='" + tipo + "', fecha='" + fecha + "'}";
    }
}
