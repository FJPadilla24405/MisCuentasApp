package models;

/**
 * Regla de repetición de un movimiento recurrente (tabla recurrentes): cada cuánto y qué día se genera.
 * Aquí no están el importe, la cuenta ni la categoría: los movimientos que nacen de la regla la enlazan
 * mediante Movimiento.recurrenteId.
 *
 * @author Fco Padilla
 */
public class Recurrente {

    // Identificador local (clave primaria autoincremental de SQLite)
    private int id;
    private String frecuencia; // "mensual", "semanal", ...
    // Día en el que se ejecuta la regla (su significado depende de la frecuencia)
    private int diaEjecucion;
    // false si la regla está desactivada y ya no genera movimientos
    private boolean activo;
    private String ultimaEjecucion; // fecha ISO, puede ser null si nunca se ha ejecutado

    /**
     * Constructor vacío
     */
    public Recurrente() {
    }

    /**
     * Crea una regla recurrente con todos sus datos.
     * @param id: identificador local de la regla
     * @param frecuencia: cada cuánto se repite ("mensual", "semanal"...)
     * @param diaEjecucion: día en el que se ejecuta
     * @param activo: false si la regla está desactivada
     * @param ultimaEjecucion: fecha ISO de la última ejecución (null si nunca se ha ejecutado)
     */
    public Recurrente(int id, String frecuencia, int diaEjecucion, boolean activo, String ultimaEjecucion) {
        this.id = id;
        this.frecuencia = frecuencia;
        this.diaEjecucion = diaEjecucion;
        this.activo = activo;
        this.ultimaEjecucion = ultimaEjecucion;
    }

    /**
     * @return Devuelve el identificador local de la regla
     */
    public int getId() {
        return id;
    }

    /**
     * @param id: identificador local de la regla
     */
    public void setId(int id) {
        this.id = id;
    }

    /**
     * @return Devuelve la frecuencia de la regla ("mensual", "semanal"...)
     */
    public String getFrecuencia() {
        return frecuencia;
    }

    /**
     * @param frecuencia: cada cuánto se repite ("mensual", "semanal"...)
     */
    public void setFrecuencia(String frecuencia) {
        this.frecuencia = frecuencia;
    }

    /**
     * @return Devuelve el día en el que se ejecuta la regla
     */
    public int getDiaEjecucion() {
        return diaEjecucion;
    }

    /**
     * @param diaEjecucion: día en el que se ejecuta la regla
     */
    public void setDiaEjecucion(int diaEjecucion) {
        this.diaEjecucion = diaEjecucion;
    }

    /**
     * @return Devuelve true si la regla está activa
     */
    public boolean isActivo() {
        return activo;
    }

    /**
     * @param activo: false para desactivar la regla
     */
    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    /**
     * @return Devuelve la fecha ISO de la última ejecución (null si nunca se ha ejecutado)
     */
    public String getUltimaEjecucion() {
        return ultimaEjecucion;
    }

    /**
     * @param ultimaEjecucion: fecha ISO de la última ejecución
     */
    public void setUltimaEjecucion(String ultimaEjecucion) {
        this.ultimaEjecucion = ultimaEjecucion;
    }

    /**
     * @return Devuelve un texto corto con el id, la frecuencia y si está activa
     */
    @Override
    public String toString() {
        return "Recurrente{id=" + id + ", frecuencia='" + frecuencia + "', activo=" + activo + "}";
    }
}
