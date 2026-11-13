package service.resumen;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import service.exportar.Tabla;
import service.exportar.Tabla.Dinero;
import service.exportar.Tabla.Porcentaje;
import service.exportar.Tabla.Seccion;

/**
 * Convierte las secciones de una exportación en un CSV que se abre bien con Excel en español: separador punto y
 * coma, decimales con coma y fechas dd/MM/aaaa. Los importes van sin símbolo de moneda y los gastos de la tabla
 * de movimientos, en negativo.
 *
 * @author Fco Padilla
 */
public final class ExportadorCsv {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String SALTO = "\r\n";

    // Constructor base vacío
    private ExportadorCsv() {
    }

    /**
     * Genera el texto del CSV. Cada tabla lleva su fila de títulos; si hay más de una sección, cada una empieza con
     * su nombre y, si una sección tiene varias tablas, cada tabla con el suyo. Entre tablas hay una línea en blanco.
     * @param secciones: secciones a escribir
     * @return Devuelve el contenido del archivo, con saltos de línea \r\n
     */
    public static String generar(List<Seccion> secciones) {
        StringBuilder csv = new StringBuilder();
        boolean primera = true;
        for (Seccion seccion : secciones) {
            for (int i = 0; i < seccion.tablas().size(); i++) {
                Tabla tabla = seccion.tablas().get(i);
                if (!primera) {
                    csv.append(SALTO);
                }
                primera = false;
                if (secciones.size() > 1 && i == 0) {
                    csv.append(campo(seccion.nombre().toUpperCase())).append(SALTO);
                }
                if (seccion.tablas().size() > 1) {
                    csv.append(campo(tabla.titulo())).append(SALTO);
                }
                escribirTabla(csv, tabla);
            }
        }
        return csv.toString();
    }

    /**
     * Escribe la fila de títulos y las filas de una tabla
     * @param csv: texto al que se añade
     * @param tabla: tabla a escribir
     */
    private static void escribirTabla(StringBuilder csv, Tabla tabla) {
        csv.append(String.join(";", tabla.columnas().stream().map(ExportadorCsv::campo).toList())).append(SALTO);
        for (List<Object> fila : tabla.filas()) {
            for (int i = 0; i < fila.size(); i++) {
                if (i > 0) {
                    csv.append(';');
                }
                csv.append(celda(fila.get(i)));
            }
            csv.append(SALTO);
        }
    }

    /**
     * @param valor: contenido de una celda de la tabla
     * @return Devuelve la celda escrita para el CSV
     */
    private static String celda(Object valor) {
        if (valor == null) {
            return "";
        }
        if (valor instanceof Dinero d) {
            return numero(d.valor());
        }
        if (valor instanceof Porcentaje p) {
            return numero(p.valor());
        }
        if (valor instanceof LocalDate f) {
            return f.format(FECHA);
        }
        return campo(valor.toString());
    }

    /**
     * @param valor: importe o porcentaje
     * @return Devuelve el número escrito con coma decimal y sin separador de miles
     */
    private static String numero(BigDecimal valor) {
        return valor.toPlainString().replace('.', ',');
    }

    /**
     * Pone un texto entre comillas si lleva punto y coma, comillas o saltos de línea, y evita que Excel
     * lo interprete como una fórmula si empieza por =, +, - o @.
     * @param texto: valor de la celda
     * @return Devuelve el valor listo para el CSV
     */
    private static String campo(String texto) {
        String t = texto == null ? "" : texto;
        if (!t.isEmpty() && "=+-@".indexOf(t.charAt(0)) >= 0) {
            t = "'" + t;
        }
        if (t.contains(";") || t.contains("\"") || t.contains("\n") || t.contains("\r")) {
            t = "\"" + t.replace("\"", "\"\"") + "\"";
        }
        return t;
    }
}
