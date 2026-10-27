package models;

import java.math.BigDecimal;

/**
 * Presupuesto de un usuario para una categoría (tabla presupuestos): el límite de gasto de un mes.
 * Se compara con la suma de los gastos de esa categoría en ese mes.
 *
 * @author Fco Padilla
 */
public class Presupuesto {

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

    /**
     * @return Devuelve un texto corto con el id, el límite y el mes del presupuesto
     */
    @Override
    public String toString() {
        return "Presupuesto{id=" + id + ", limite=" + limite + ", mesReferencia='" + mesReferencia + "'}";
    }
}
