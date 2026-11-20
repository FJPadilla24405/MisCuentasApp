package service.importar;

/**
 * El archivo elegido no se puede importar (está vacío, no es un CSV ni un Excel, está protegido...). El mensaje se le
 * enseña tal cual al usuario, así que está escrito para él.
 *
 * @author Fco Padilla
 */
public class ArchivoNoValido extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * @param mensaje: qué le pasa al archivo, escrito para el usuario
     */
    public ArchivoNoValido(String mensaje) {
        super(mensaje);
    }

    /**
     * @param mensaje: qué le pasa al archivo, escrito para el usuario
     * @param causa: error técnico que lo provocó
     */
    public ArchivoNoValido(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
