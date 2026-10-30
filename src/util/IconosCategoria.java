package util;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Icono de cada categoría de gasto (trazos SVG de una cuadrícula de 24x24). Se elige por el nombre de la
 * categoría; las que no tienen uno propio usan una etiqueta.
 *
 * @author Fco Padilla
 */
public final class IconosCategoria {

    private static final String VIVIENDA = "M3 11 L12 3 L21 11 M5 9.5 V20 H19 V9.5 M10 20 V14 H14 V20";
    private static final String ALIMENTACION = "M3 9 H21 L19 20 H5 Z M8 9 L11 4 M16 9 L13 4 M9 13 V16 M12 13 V16 M15 13 V16";
    private static final String COMPRAS = "M5 8 H19 L20 20 H4 Z M9 8 V6 A3 3 0 0 1 15 6 V8";
    private static final String SERVICIOS = "M13 2 L4 14 H12 L11 22 L20 10 H12 Z";
    private static final String TRANSPORTE = "M5 13 L7 7 H17 L19 13 M3 13 H21 V18 H3 Z M7 18 V20 M17 18 V20 M7 15.5 L7.01 15.5 M17 15.5 L17.01 15.5";
    private static final String OCIO = "M4 6 H20 V18 H4 Z M8 6 V18 M16 6 V18 M4 10 H8 M4 14 H8 M16 10 H20 M16 14 H20";
    private static final String SALUD = "M12 20 C12 20 4 15 4 9.5 A4 4 0 0 1 12 8 A4 4 0 0 1 20 9.5 C20 15 12 20 12 20 Z";
    private static final String EDUCACION = "M4 5 H11 A1 1 0 0 1 12 6 V19 A3 3 0 0 0 9 17 H4 Z M20 5 H13 A1 1 0 0 0 12 6 V19 A3 3 0 0 1 15 17 H20 Z";
    private static final String OTRA = "M3 12 V4 H11 L21 14 L14 21 Z M7.5 8 L7.51 8";

    // No se instancia
    private IconosCategoria() {
    }

    /**
     * @param nombreCategoria: nombre de la categoría
     * @return Devuelve el trazo SVG de su icono
     */
    public static String trazo(String nombreCategoria) {
        String n = Normalizer.normalize(nombreCategoria == null ? "" : nombreCategoria, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        if (n.contains("viviend") || n.contains("hogar") || n.contains("alquiler")) {
            return VIVIENDA;
        }
        if (n.contains("aliment") || n.contains("supermercado") || n.contains("comida")) {
            return ALIMENTACION;
        }
        if (n.contains("compra") || n.contains("ropa")) {
            return COMPRAS;
        }
        if (n.contains("servicio") || n.contains("suministro") || n.contains("factura")) {
            return SERVICIOS;
        }
        if (n.contains("transporte") || n.contains("coche") || n.contains("gasolina")) {
            return TRANSPORTE;
        }
        if (n.contains("ocio") || n.contains("entretenimiento") || n.contains("cine")) {
            return OCIO;
        }
        if (n.contains("salud") || n.contains("farmacia") || n.contains("medic")) {
            return SALUD;
        }
        if (n.contains("educacion") || n.contains("estudio") || n.contains("formacion")) {
            return EDUCACION;
        }
        return OTRA;
    }
}
