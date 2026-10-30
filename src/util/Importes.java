package util;

import java.math.BigDecimal;

/**
 * Convierte lo que el usuario escribe en un campo de importe en un número.
 *
 * @author Fco Padilla
 */
public final class Importes {

    // No se instancia
    private Importes() {
    }

    /**
     * Convierte el texto de un importe en número. Acepta 1250.5, 1250,5 y 1.250,50; un solo punto seguido
     * de tres cifras (1.250) se entiende como separador de miles. Un campo vacío es 0.
     * @param texto: lo escrito en el campo
     * @return Devuelve el importe
     * @throws NumberFormatException si no es un número
     */
    public static BigDecimal leer(String texto) {
        String t = texto.trim().replace(" ", "").replace("\u00A0", "").replace("€", "");
        if (t.isEmpty()) {
            return Dinero.CERO;
        }
        if (t.contains(",")) {
            t = t.replace(".", "").replace(",", ".");
        } else if (t.matches("-?\\d{1,3}(\\.\\d{3})+")) {
            t = t.replace(".", "");
        }
        return new BigDecimal(t);
    }
}
