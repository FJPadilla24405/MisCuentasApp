package util;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Elige el icono de una categoría según su nombre (las categorías no guardan icono). Los trazos están
 * en una cuadrícula de 24x24.
 *
 * @author Fco Padilla
 */
public final class IconosCategoria {

    private static final String CASA = "M3 11 L12 3 L21 11 M5 10 V21 H19 V10 M10 21 V14 H14 V21";
    private static final String CESTA = "M3 9 H21 L19 20 H5 Z M8 9 L12 3 L16 9";
    private static final String BOLSA = "M5 7 H19 L20 20 H4 Z M9 7 V6 A3 3 0 0 1 15 6 V7";
    private static final String RAYO = "M13 3 L5 14 H12 L11 21 L19 10 H12 Z";
    private static final String COCHE = "M5 17 V10 L7 5 H17 L19 10 V17 M3 17 H21 M7 17 V19 M17 17 V19 M5 11 H19";
    private static final String ENTRADA = "M4 7 H20 V17 H4 Z M8 7 V17 M16 7 V17 M4 12 H20";
    private static final String CORAZON = "M12 20 C5 15 3 11 3 8 A4.5 4.5 0 0 1 12 6 A4.5 4.5 0 0 1 21 8 C21 11 19 15 12 20 Z";
    private static final String LIBRO = "M4 5 H11 A2 2 0 0 1 12 7 V20 A2 2 0 0 0 10 18 H4 Z M20 5 H13 A2 2 0 0 0 12 7 V20 A2 2 0 0 1 14 18 H20 Z";
    private static final String ETIQUETA = "M3 12 L12 3 H21 V12 L12 21 Z M16 8 L16.01 8";

    // Constructor base vacío
    private IconosCategoria() {
    }

    /**
     * @param nombre: nombre de la categoría
     * @return Devuelve el trazo SVG del icono que le corresponde (una etiqueta si no se reconoce)
     */
    public static String para(String nombre) {
        String n = Normalizer.normalize(nombre == null ? "" : nombre, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        if (contiene(n, "vivienda", "hogar", "alquiler", "hipoteca", "casa")) {
            return CASA;
        }
        if (contiene(n, "aliment", "comida", "supermercado", "restaurante")) {
            return CESTA;
        }
        if (contiene(n, "compra", "ropa", "tienda")) {
            return BOLSA;
        }
        if (contiene(n, "servicio", "suministro", "luz", "agua", "internet", "factura")) {
            return RAYO;
        }
        if (contiene(n, "transporte", "coche", "gasolina", "bus", "taxi")) {
            return COCHE;
        }
        if (contiene(n, "ocio", "entretenimiento", "cine", "juego", "viaje")) {
            return ENTRADA;
        }
        if (contiene(n, "salud", "medic", "farmacia")) {
            return CORAZON;
        }
        if (contiene(n, "educacion", "estudio", "curso", "libro")) {
            return LIBRO;
        }
        return ETIQUETA;
    }

    /**
     * @param texto: texto en minúscula y sin tildes
     * @param palabras: palabras a buscar
     * @return Devuelve true si el texto contiene alguna de las palabras
     */
    private static boolean contiene(String texto, String... palabras) {
        for (String p : palabras) {
            if (texto.contains(p)) {
                return true;
            }
        }
        return false;
    }
}
