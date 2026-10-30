package models;

import java.math.BigDecimal;

/**
 * Presupuesto de un usuario para una categoría (tabla presupuestos): el límite de gasto de un mes.
 * Se compara con la suma de los gastos de esa categoría en ese mes.
 *
 * @author Fco Padilla
 */
public class Presupuesto {

    /** Alcance: cuentan los gastos de todas las cuentas activas */
    public static final String ALCANCE_TODAS = "todas";
    /** Alcance: solo cuentan los gastos de las cuentas de tipo banco */
    public static final String ALCANCE_BANCO = "banco";
    /** Alcance: solo cuentan los gastos de las cuentas de tipo efectivo */
    public static final String ALCANCE_EFECTIVO = "efectivo";

    // Identificador local (clave primaria autoincremental de SQLite)
    private int id;
    // Id del usuario dueño del presupuesto
    private int usuarioId;
    // Id de la categoría a la que se aplica el límite
    private int categoriaId;
    // Cantidad máxima de gasto prevista para el periodo (DECIMAL(12,2))
    private BigDecimal limite;
    private String periodo; // "mensual"
    private String mesReferencia; // formato "yyyy-MM"
    // Nombre que le pone el usuario al presupuesto (máximo 50 caracteres; puede ser null en datos antiguos)
    private String nombre;
    // Qué cuentas cuentan para el límite: "todas", "banco" o "efectivo" (null en datos antiguos se entiende como "todas")
    private String alcance;

    /**
     * Constructor vacío
     */
    public Presupuesto() {
    }

    /**
     * Crea un presupuesto con todos sus datos.
     * @param id: identificador local del presupuesto
     * @param usuarioId: id del usuario dueño del presupuesto
     * @param categoriaId: id de la categoría
     * @param limite: cantidad máxima de gasto
     * @param periodo: periodo del presupuesto ("mensual")
     * @param mesReferencia: mes al que se aplica, con formato "yyyy-MM"
     */
    public Presupuesto(int id, int usuarioId, int categoriaId, BigDecimal limite, String periodo, String mesReferencia) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.categoriaId = categoriaId;
        this.limite = limite;
        this.periodo = periodo;
        this.mesReferencia = mesReferencia;
        this.alcance = ALCANCE_TODAS;
    }

    /**
     * Crea un presupuesto con todos sus datos, incluidos el nombre y el alcance de cuentas.
     * @param id: identificador local del presupuesto
     * @param usuarioId: id del usuario dueño del presupuesto
     * @param categoriaId: id de la categoría
     * @param nombre: nombre del presupuesto
     * @param limite: cantidad máxima de gasto
     * @param periodo: periodo del presupuesto ("mensual")
     * @param mesReferencia: mes al que se aplica, con formato "yyyy-MM"
     * @param alcance: cuentas que cuentan ("todas", "banco" o "efectivo")
     */
    public Presupuesto(int id, int usuarioId, int categoriaId, String nombre, BigDecimal limite, String periodo,
            String mesReferencia, String alcance) {
        this(id, usuarioId, categoriaId, limite, periodo, mesReferencia);
        this.nombre = nombre;
        this.alcance = alcance == null || alcance.isBlank() ? ALCANCE_TODAS : alcance;
    }

    /** @return Devuelve el identificador local del presupuesto */
    public int getId() {
        return id;
    }

    /** @param id: identificador local del presupuesto */
    public void setId(int id) {
        this.id = id;
    }

    /** @return Devuelve el id del usuario dueño del presupuesto */
    public int getUsuarioId() {
        return usuarioId;
    }

    /** @param usuarioId: id del usuario dueño del presupuesto */
    public void setUsuarioId(int usuarioId) {
        this.usuarioId = usuarioId;
    }

    /** @return Devuelve el id de la categoría a la que se aplica el presupuesto */
    public int getCategoriaId() {
        return categoriaId;
    }

    /** @param categoriaId: id de la categoría a la que se aplica el presupuesto */
    public void setCategoriaId(int categoriaId) {
        this.categoriaId = categoriaId;
    }

    /** @return Devuelve la cantidad máxima de gasto del presupuesto */
    public BigDecimal getLimite() {
        return limite;
    }

    /** @param limite: cantidad máxima de gasto */
    public void setLimite(BigDecimal limite) {
        this.limite = limite;
    }

    /** @return Devuelve el periodo del presupuesto ("mensual") */
    public String getPeriodo() {
        return periodo;
    }

    /** @param periodo: periodo del presupuesto ("mensual") */
    public void setPeriodo(String periodo) {
        this.periodo = periodo;
    }

    /** @return Devuelve el mes al que se aplica el presupuesto, con formato "yyyy-MM" */
    public String getMesReferencia() {
        return mesReferencia;
    }

    /** @param mesReferencia: mes al que se aplica, con formato "yyyy-MM" */
    public void setMesReferencia(String mesReferencia) {
        this.mesReferencia = mesReferencia;
    }

    /** @return Devuelve el nombre del presupuesto (puede ser null en presupuestos antiguos) */
    public String getNombre() {
        return nombre;
    }

    /** @param nombre: nombre del presupuesto */
    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    /** @return Devuelve el alcance de cuentas: "todas", "banco" o "efectivo" (nunca null) */
    public String getAlcance() {
        return alcance == null || alcance.isBlank() ? ALCANCE_TODAS : alcance;
    }

    /** @param alcance: "todas", "banco" o "efectivo" */
    public void setAlcance(String alcance) {
        this.alcance = alcance;
    }

    /**
     * @return Devuelve un texto corto con el id, el límite y el mes del presupuesto
     */
    @Override
    public String toString() {
        return "Presupuesto{id=" + id + ", limite=" + limite + ", mesReferencia='" + mesReferencia + "'}";
    }
}
