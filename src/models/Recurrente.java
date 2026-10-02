package models;

public class Recurrente {

    private int id;
    private int usuarioId;
    private int cuentaId;
    private int categoriaId;
    private double importe;
    private String tipo; // "ingreso" o "gasto"
    private String frecuencia; // "mensual", "semanal", ...
    private int diaEjecucion;
    private boolean activo;
    private String ultimaEjecucion; // fecha ISO, puede ser null si nunca se ha ejecutado

    public Recurrente() {
    }

    public Recurrente(int id, int usuarioId, int cuentaId, int categoriaId, double importe, String tipo,
                       String frecuencia, int diaEjecucion, boolean activo, String ultimaEjecucion) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.cuentaId = cuentaId;
        this.categoriaId = categoriaId;
        this.importe = importe;
        this.tipo = tipo;
        this.frecuencia = frecuencia;
        this.diaEjecucion = diaEjecucion;
        this.activo = activo;
        this.ultimaEjecucion = ultimaEjecucion;
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

    public String getFrecuencia() {
        return frecuencia;
    }

    public void setFrecuencia(String frecuencia) {
        this.frecuencia = frecuencia;
    }

    public int getDiaEjecucion() {
        return diaEjecucion;
    }

    public void setDiaEjecucion(int diaEjecucion) {
        this.diaEjecucion = diaEjecucion;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public String getUltimaEjecucion() {
        return ultimaEjecucion;
    }

    public void setUltimaEjecucion(String ultimaEjecucion) {
        this.ultimaEjecucion = ultimaEjecucion;
    }

    @Override
    public String toString() {
        return "Recurrente{id=" + id + ", importe=" + importe + ", frecuencia='" + frecuencia + "', activo=" + activo + "}";
    }
}
