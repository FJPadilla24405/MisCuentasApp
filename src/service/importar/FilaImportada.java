package service.importar;

import java.math.BigDecimal;
import java.time.LocalDate;

import models.Categoria;

/**
 * Un movimiento del archivo, ya interpretado y listo para enseñarse en la vista previa y, si se marca, importarse.
 * Si la categoría del archivo (con su subcategoría, si la trae) no existe en la aplicación, la fila lleva el nombre de
 * la que se creará al importar.
 *
 * @author Fco Padilla
 */
public final class FilaImportada {

    /**
     * Cómo ha quedado la fila al interpretarla.
     */
    public enum Estado {
        // Se puede importar
        VALIDO,
        // Ya hay en la cuenta un movimiento igual (misma fecha, importe, tipo y concepto): se propone no importarlo
        DUPLICADO,
        // Falta o no se entiende la fecha o el importe: no se puede importar
        ERROR
    }

    private final int numeroFila;
    private LocalDate fecha;
    private String concepto;
    // Siempre positivo: que sea un gasto o un ingreso lo dice el tipo
    private BigDecimal importe;
    private String tipo;
    private Categoria categoria;
    private String nombreCategoriaNueva;
    private String subcategoriaNueva;
    private String nota;
    private Estado estado;
    private String motivo;

    /**
     * @param numeroFila: posición de la fila en el archivo, empezando en 1
     */
    FilaImportada(int numeroFila) {
        this.numeroFila = numeroFila;
    }

    /**
     * @return Devuelve la posición de la fila en el archivo, empezando en 1
     */
    public int getNumeroFila() {
        return numeroFila;
    }

    /**
     * @return Devuelve la fecha, o null si no se ha entendido
     */
    public LocalDate getFecha() {
        return fecha;
    }

    void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    /**
     * @return Devuelve el concepto
     */
    public String getConcepto() {
        return concepto;
    }

    void setConcepto(String concepto) {
        this.concepto = concepto;
    }

    /**
     * @return Devuelve el importe, siempre positivo, o null si no se ha entendido
     */
    public BigDecimal getImporte() {
        return importe;
    }

    void setImporte(BigDecimal importe) {
        this.importe = importe;
    }

    /**
     * @return Devuelve "gasto" o "ingreso" (null si no se ha entendido el importe)
     */
    public String getTipo() {
        return tipo;
    }

    void setTipo(String tipo) {
        this.tipo = tipo;
    }

    /**
     * @return Devuelve el importe con su signo (negativo en los gastos), o null si no se ha entendido
     */
    public BigDecimal getImporteConSigno() {
        if (importe == null) {
            return null;
        }
        return "gasto".equals(tipo) ? importe.negate() : importe;
    }

    /**
     * @return Devuelve la categoría de la aplicación asignada, o null si se va a crear una nueva (o la fila es un error)
     */
    public Categoria getCategoria() {
        return categoria;
    }

    /**
     * Asigna una categoría de la aplicación (y descarta la nueva que se iba a crear)
     * @param categoria: categoría del mismo tipo que la fila
     */
    public void setCategoria(Categoria categoria) {
        this.categoria = categoria;
        this.nombreCategoriaNueva = null;
        this.subcategoriaNueva = null;
    }

    /**
     * @return Devuelve el nombre de la categoría que se creará al importar, o null si se usa una que ya existe
     */
    public String getNombreCategoriaNueva() {
        return nombreCategoriaNueva;
    }

    void setCategoriaNueva(String nombre, String subcategoria) {
        this.nombreCategoriaNueva = nombre;
        this.subcategoriaNueva = subcategoria;
        this.categoria = null;
    }

    /**
     * @return Devuelve la subcategoría de la categoría que tendrá el movimiento (la existente o la nueva), o null si no tiene
     */
    public String getSubcategoria() {
        return categoria != null ? categoria.getSubcategoria() : subcategoriaNueva;
    }

    /**
     * @return Devuelve la subcategoría de la categoría que se creará al importar, o null si no tiene o se usa una que ya existe
     */
    public String getSubcategoriaNueva() {
        return subcategoriaNueva;
    }

    /**
     * @return Devuelve la nota del movimiento, o null si no tiene
     */
    public String getNota() {
        return nota;
    }

    void setNota(String nota) {
        this.nota = nota;
    }

    /**
     * @return Devuelve el nombre de la categoría que tendrá el movimiento (la existente o la nueva), o null si no tiene
     */
    public String getNombreCategoria() {
        if (categoria != null) {
            return categoria.getNombre();
        }
        return nombreCategoriaNueva;
    }

    /**
     * @return Devuelve true si la categoría se va a crear al importar
     */
    public boolean esCategoriaNueva() {
        return categoria == null && nombreCategoriaNueva != null;
    }

    /**
     * @return Devuelve cómo ha quedado la fila
     */
    public Estado getEstado() {
        return estado;
    }

    void setEstado(Estado estado, String motivo) {
        this.estado = estado;
        this.motivo = motivo;
    }

    /**
     * @return Devuelve el motivo del error o del duplicado, o null si es válida
     */
    public String getMotivo() {
        return motivo;
    }
}
