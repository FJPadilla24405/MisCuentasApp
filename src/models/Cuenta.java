package models;

import java.math.BigDecimal;

/**
 * Cuenta de dinero de un usuario (tabla cuentas): un banco o el efectivo. Cada movimiento pertenece a una
 * cuenta. El saldo actual no se guarda: se calcula a partir del saldo inicial y de los movimientos (ver CuentaDAO).
 *
 * @author Fco Padilla
 */
public class Cuenta {

    // Identificador local (clave primaria autoincremental de SQLite)
    private int id;
    // Id del usuario dueño de la cuenta
    private int usuarioId;
    // Nombre de la cuenta (máximo 50 caracteres en la base de datos)
    private String nombre;
    private String tipo; // "banco" o "efectivo"
    // Saldo con el que se crea la cuenta (DECIMAL(12,2) en la base de datos)
    private BigDecimal saldoInicial;
    // Código de moneda de tres letras (la del usuario)
    private String moneda;
    // false si la cuenta se ha dado de baja (baja lógica: se conserva el histórico de movimientos)
    private boolean activa;

    /**
     * Constructor vacío
     */
    public Cuenta() {
    }

    /**
     * Crea una cuenta con todos sus datos.
     * @param id: identificador local de la cuenta
     * @param usuarioId: id del usuario dueño de la cuenta
     * @param nombre: nombre de la cuenta
     * @param tipo: "banco" o "efectivo"
     * @param saldoInicial: saldo con el que se crea la cuenta
     * @param moneda: código de moneda de tres letras
     * @param activa: false si la cuenta está dada de baja
     */
    public Cuenta(int id, int usuarioId, String nombre, String tipo, BigDecimal saldoInicial, String moneda, boolean activa) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.nombre = nombre;
        this.tipo = tipo;
        this.saldoInicial = saldoInicial;
        this.moneda = moneda;
        this.activa = activa;
    }

    /** @return Devuelve el identificador local de la cuenta */
    public int getId() {
        return id;
    }

    /** @param id: identificador local de la cuenta */
    public void setId(int id) {
        this.id = id;
    }

    /** @return Devuelve el id del usuario dueño de la cuenta */
    public int getUsuarioId() {
        return usuarioId;
    }

    /** @param usuarioId: id del usuario dueño de la cuenta */
    public void setUsuarioId(int usuarioId) {
        this.usuarioId = usuarioId;
    }

    /** @return Devuelve el nombre de la cuenta */
    public String getNombre() {
        return nombre;
    }

    /** @param nombre: nombre de la cuenta */
    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    /** @return Devuelve el tipo de cuenta ("banco" o "efectivo") */
    public String getTipo() {
        return tipo;
    }

    /** @param tipo: "banco" o "efectivo" */
    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    /** @return Devuelve el saldo con el que se creó la cuenta (no el saldo actual) */
    public BigDecimal getSaldoInicial() {
        return saldoInicial;
    }

    /** @param saldoInicial: saldo con el que se crea la cuenta */
    public void setSaldoInicial(BigDecimal saldoInicial) {
        this.saldoInicial = saldoInicial;
    }

    /** @return Devuelve el código de moneda de la cuenta (tres letras) */
    public String getMoneda() {
        return moneda;
    }

    /** @param moneda: código de moneda de tres letras */
    public void setMoneda(String moneda) {
        this.moneda = moneda;
    }

    /** @return Devuelve true si la cuenta está activa y false si se ha dado de baja */
    public boolean isActiva() {
        return activa;
    }

    /** @param activa: false para dar de baja la cuenta sin borrarla */
    public void setActiva(boolean activa) {
        this.activa = activa;
    }

    /**
     * @return Devuelve un texto corto con el id, el nombre y el tipo de la cuenta
     */
    @Override
    public String toString() {
        return "Cuenta{id=" + id + ", nombre='" + nombre + "', tipo='" + tipo + "'}";
    }
}
