package service;

/**
 * Un dato que el usuario ha escrito mal. Lleva el campo que falla y un mensaje en español listo para mostrar.
 *
 * @author Fco Padilla
 */
public class DatoInvalido extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;
    // Campo del formulario que falla (por ejemplo "nombre" o "importe")
    private final String campo;

    /**
     * @param campo: campo que falla
     * @param mensaje: texto para el usuario
     */
    public DatoInvalido(String campo, String mensaje) {
        super(mensaje);
        this.campo = campo;
    }

    /**
     * @return Devuelve el campo que falla
     */
    public String getCampo() {
        return campo;
    }
}
