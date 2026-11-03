package util;

import java.math.BigDecimal;

/**
 * Lee importes escritos por el usuario en un campo de texto.
 *
 * @author Fco Padilla
 */
public final class Importe {

    // Constructor base vacío
    private Importe() {
    }

    /**
     * Convierte el texto de un campo en un número. Acepta 1250.5, 1250,5, 1.250,50 y 350 €; un solo punto
     * seguido de tres cifras (1.250) se entiende como separador de miles.
     * @param texto: lo escrito en el campo
     * @return Devuelve el importe
     * @throws NumberFormatException si está vacío o no es un número
     */
    public static BigDecimal leer(String texto) {
        String t = texto == null ? "" : texto.trim().replace(" ", "").replace("\u00A0", "").replace("€", "");
        if (t.isEmpty()) {
            throw new NumberFormatException("importe vacío");
        }
        if (t.contains(",")) {
            t = t.replace(".", "").replace(",", ".");
        } else if (t.matches("-?\\d{1,3}(\\.\\d{3})+")) {
            t = t.replace(".", "");
        }
        return new BigDecimal(t);
    }
}
