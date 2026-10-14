package models;

import java.math.BigDecimal;

public class Presupuesto {

    private int id;
    private int usuarioId;
    private int categoriaId;
    private BigDecimal limite;
    private String periodo; // "mensual"
    private String mesReferencia; // formato "yyyy-MM"

    public Presupuesto() {
    }

    public Presupuesto(int id, int usuarioId, int categoriaId, BigDecimal limite, String periodo, String mesReferencia) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.categoriaId = categoriaId;
        this.limite = limite;
        this.periodo = periodo;
        this.mesReferencia = mesReferencia;
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

    public int getCategoriaId() {
        return categoriaId;
    }

    public void setCategoriaId(int categoriaId) {
        this.categoriaId = categoriaId;
    }

    public BigDecimal getLimite() {
        return limite;
    }

    public void setLimite(BigDecimal limite) {
        this.limite = limite;
    }

    public String getPeriodo() {
        return periodo;
    }

    public void setPeriodo(String periodo) {
        this.periodo = periodo;
    }

    public String getMesReferencia() {
        return mesReferencia;
    }

    public void setMesReferencia(String mesReferencia) {
        this.mesReferencia = mesReferencia;
    }

    @Override
    public String toString() {
        return "Presupuesto{id=" + id + ", limite=" + limite + ", mesReferencia='" + mesReferencia + "'}";
    }
}
