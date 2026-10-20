package util;

/**
 * Reglas de contraseña para el registro. Mínimo: 8 caracteres con letras y números (3 tramos).
 * El cuarto tramo se consigue mezclando mayúsculas y minúsculas o con algún símbolo.
 *
 * @author Fco Padilla
 */
public final class Contrasena {

    public static final String AYUDA = "Mínimo 8 caracteres, con letras y números.";

    /**
     * Resultado de evaluar una contraseña.
     * @param tramos: tramos de la barra que hay que rellenar (0 a 4)
     * @param debil: true si no llega al mínimo (la barra se pinta de aviso)
     * @param mensaje: texto que se muestra bajo la barra
     */
    public record Fortaleza(int tramos, boolean debil, String mensaje) {
    }

    // No se instancia
    private Contrasena() {
    }

    /** @return true si cumple lo mínimo para poder registrarse. */
    public static boolean cumpleMinimos(String pw) {
        return pw != null && pw.length() >= 8
                && pw.chars().anyMatch(Character::isLetter)
                && pw.chars().anyMatch(Character::isDigit);
    }

    /** Calcula cuánto se rellena la barra y qué texto se muestra. */
    public static Fortaleza evaluar(String pw) {
        if (pw == null || pw.isEmpty()) {
            return new Fortaleza(0, false, AYUDA);
        }
        if (!cumpleMinimos(pw)) {
            int cumplidos = (pw.length() >= 8 ? 1 : 0)
                    + (pw.chars().anyMatch(Character::isLetter) ? 1 : 0)
                    + (pw.chars().anyMatch(Character::isDigit) ? 1 : 0);
            return new Fortaleza(Math.max(1, Math.min(cumplidos, 2)), true,
                    "Contraseña débil · mínimo 8 caracteres, con letras y números.");
        }
        boolean mayus = pw.chars().anyMatch(Character::isUpperCase);
        boolean minus = pw.chars().anyMatch(Character::isLowerCase);
        boolean simbolo = pw.chars().anyMatch(c -> !Character.isLetterOrDigit(c));
        if ((mayus && minus) || simbolo) {
            return new Fortaleza(4, false, "Contraseña muy segura");
        }
        return new Fortaleza(3, false, "Contraseña segura · 8 caracteres, letras y números");
    }
}
