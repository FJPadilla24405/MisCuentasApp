package models;

public class Recurrente {

    private int id;
    private String frecuencia; // "mensual", "semanal", ...
    private int diaEjecucion;
    private boolean activo;
    private String ultimaEjecucion; // fecha ISO, puede ser null si nunca se ha ejecutado

    public Recurrente() {
    }

    public Recurrente(int id, String frecuencia, int diaEjecucion, boolean activo, String ultimaEjecucion) {
        this.id = id;
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
        return "Recurrente{id=" + id + ", frecuencia='" + frecuencia + "', activo=" + activo + "}";
    }
}
