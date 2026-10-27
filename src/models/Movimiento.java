package models;

import java.math.BigDecimal;

/**
 * Movimiento de dinero (tabla movimientos): un ingreso o un gasto anotado en una cuenta y una categoría
 * en una fecha. Puede ser puntual o venir de una regla recurrente (recurrenteId).
 *
 * @author Fco Padilla
 */
public class Movimiento {

    // Identificador local (clave primaria autoincremental de SQLite)
    private int id;
    // Id del usuario dueño del movimiento
    private int usuarioId;
    // Id de la cuenta en la que se anota el movimiento
    private int cuentaId;
    // Id de la categoría del movimiento
    private int categoriaId;
    // Cantidad del movimiento (DECIMAL(12,2)); si suma o resta al saldo lo indica el tipo
    private BigDecimal importe;
    private String tipo; // "ingreso" o "gasto"
    private String fecha; // formato ISO: yyyy-MM-dd
    // Texto libre opcional (máximo 100 caracteres en la base de datos)
    private String descripcion;
    // Marca local: true si el movimiento ya está subido a Firebase (no se sube a la nube)
    private boolean sincronizado;
    // Id de la regla recurrente de la que nace; 0 si es un movimiento puntual (en la base de datos se guarda NULL)
    private int recurrenteId;

    /**
     * Constructor vacío
     */
    public Movimiento() {
    }

    /**
     * Crea un movimiento puntual (sin regla recurrente: recurrenteId queda en 0).
     * @param id: identificador local del movimiento
     * @param usuarioId: id del usuario dueño del movimiento
     * @param cuentaId: id de la cuenta
     * @param categoriaId: id de la categoría
     * @param importe: cantidad del movimiento
     * @param tipo: "ingreso" o "gasto"
     * @param fecha: fecha en formato ISO (yyyy-MM-dd)
     * @param descripcion: texto libre (puede ser null)
     * @param sincronizado: true si ya está subido a Firebase
     */
    public Movimiento(int id, int usuarioId, int cuentaId, int categoriaId, BigDecimal importe,
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
    
    /**
     * Crea un movimiento indicando también la regla recurrente de la que nace.
     * @param id: identificador local del movimiento
     * @param usuarioId: id del usuario dueño del movimiento
     * @param cuentaId: id de la cuenta
     * @param categoriaId: id de la categoría
     * @param importe: cantidad del movimiento
     * @param tipo: "ingreso" o "gasto"
     * @param fecha: fecha en formato ISO (yyyy-MM-dd)
     * @param descripcion: texto libre (puede ser null)
     * @param sincronizado: true si ya está subido a Firebase
     * @param recurrenteId: id de la regla recurrente (0 si es puntual)
     */
    public Movimiento(int id, int usuarioId, int cuentaId, int categoriaId, BigDecimal importe,
            String tipo, String fecha, String descripcion, boolean sincronizado, int recurrenteId) {
    	this.id = id;
    	this.usuarioId = usuarioId;
    	this.cuentaId = cuentaId;
    	this.categoriaId = categoriaId;
    	this.importe = importe;
		this.tipo = tipo;
		this.fecha = fecha;
		this.descripcion = descripcion;
		this.sincronizado = sincronizado;
		this.recurrenteId = recurrenteId;
    }

    /** @return Devuelve el identificador local del movimiento */
    public int getId() {
        return id;
    }

    /** @param id: identificador local del movimiento */
    public void setId(int id) {
        this.id = id;
    }

    /** @return Devuelve el id del usuario dueño del movimiento */
    public int getUsuarioId() {
        return usuarioId;
    }

    /** @param usuarioId: id del usuario dueño del movimiento */
    public void setUsuarioId(int usuarioId) {
        this.usuarioId = usuarioId;
    }

    /** @return Devuelve el id de la cuenta del movimiento */
    public int getCuentaId() {
        return cuentaId;
    }

    /** @param cuentaId: id de la cuenta del movimiento */
    public void setCuentaId(int cuentaId) {
        this.cuentaId = cuentaId;
    }

    /** @return Devuelve el id de la categoría del movimiento */
    public int getCategoriaId() {
        return categoriaId;
    }

    /** @param categoriaId: id de la categoría del movimiento */
    public void setCategoriaId(int categoriaId) {
        this.categoriaId = categoriaId;
    }

    /** @return Devuelve la cantidad del movimiento */
    public BigDecimal getImporte() {
        return importe;
    }

    /** @param importe: cantidad del movimiento */
    public void setImporte(BigDecimal importe) {
        this.importe = importe;
    }

    /** @return Devuelve el tipo del movimiento ("ingreso" o "gasto") */
    public String getTipo() {
        return tipo;
    }

    /** @param tipo: "ingreso" o "gasto" */
    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    /** @return Devuelve la fecha en formato ISO (yyyy-MM-dd) */
    public String getFecha() {
        return fecha;
    }

    /** @param fecha: fecha en formato ISO (yyyy-MM-dd) */
    public void setFecha(String fecha) {
        this.fecha = fecha;
    }

    /** @return Devuelve la descripción del movimiento (puede ser null) */
    public String getDescripcion() {
        return descripcion;
    }

    /** @param descripcion: texto libre del movimiento */
    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    /** @return Devuelve true si el movimiento ya está subido a Firebase */
    public boolean isSincronizado() {
        return sincronizado;
    }

    /** @param sincronizado: true si el movimiento ya está subido a Firebase */
    public void setSincronizado(boolean sincronizado) {
        this.sincronizado = sincronizado;
    }

    /** @return Devuelve el id de la regla recurrente de la que nace (0 si es puntual) */
    public int getRecurrenteId() {
		return recurrenteId;
	}

    /** @param recurrenteId: id de la regla recurrente (0 si es puntual) */
	public void setRecurrenteId(int recurrenteId) {
		this.recurrenteId = recurrenteId;
	}

    /**
     * @return Devuelve un texto corto con el id, el importe, el tipo y la fecha del movimiento
     */
	@Override
    public String toString() {
        return "Movimiento{id=" + id + ", importe=" + importe + ", tipo='" + tipo + "', fecha='" + fecha + "'}";
    }
}
