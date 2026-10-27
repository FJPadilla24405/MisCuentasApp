package util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Currency;
import java.util.Locale;

/**
 * Formatos para mostrar datos en pantalla (importes con moneda e iniciales de un nombre).
 *
 * @author Fco Padilla
 */
public final class Formato {

    // Idioma con el que se escriben los importes: 1.750,00 €
    private static final Locale ES = Locale.forLanguageTag("es-ES");

    /**
     * Constructor base vacío
     */
    private Formato() {
    }

    /**
     * Da formato de moneda a un importe: separador de miles con punto, dos decimales con coma y el
     * símbolo de la moneda detrás (por ejemplo 5.750,00 €). Se usa un patrón fijo porque el formato
     * de moneda de Java para español no pone el punto en los miles de cuatro cifras.
     * @param valor: importe a mostrar
     * @param moneda: código de moneda de tres letras (EUR, USD...)
     * @return Devuelve el importe como texto
     */
    public static String moneda(BigDecimal valor, String moneda) {
        DecimalFormat formato = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(ES));
        formato.setRoundingMode(RoundingMode.HALF_UP);
        return formato.format(valor) + "\u00A0" + simbolo(moneda);
    }

    /**
     * Símbolo de una moneda en español (EUR es €). Si el código no es válido se devuelve tal cual.
     * @param moneda: código de moneda de tres letras
     * @return Devuelve el símbolo
     */
    public static String simbolo(String moneda) {
        try {
            return Currency.getInstance(moneda).getSymbol(ES);
        } catch (IllegalArgumentException | NullPointerException e) {
            return moneda == null ? "" : moneda;
        }
    }

    /**
     * Iniciales de un nombre para el avatar: las de las dos primeras palabras ("Lucía Martín" es LM)
     * o las dos primeras letras si solo hay una palabra.
     * @param nombre: nombre completo
     * @return Devuelve las iniciales en mayúsculas, o "?" si el nombre está vacío
     */
    public static String iniciales(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return "?";
        }
        String[] palabras = nombre.trim().split("\\s+");
        String iniciales;
        if (palabras.length >= 2) {
            iniciales = palabras[0].substring(0, 1) + palabras[1].substring(0, 1);
        } else {
            iniciales = palabras[0].substring(0, Math.min(2, palabras[0].length()));
        }
        return iniciales.toUpperCase(ES);
    }
}
