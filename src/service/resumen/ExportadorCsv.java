package service.resumen;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

import service.resumen.ResumenService.FilaMovimiento;

/**
 * Convierte una lista de movimientos en un CSV que se abre bien con Excel en español: separador punto y
 * coma, decimales con coma y fechas dd/MM/aaaa. El importe lleva signo (los gastos en negativo).
 *
 * @author Fco Padilla
 */
public final class ExportadorCsv {

    // Cabecera del archivo
    private static final String CABECERA = "Fecha;Tipo;Concepto;Categoría;Cuenta;Importe";
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Constructor base vacío
    private ExportadorCsv() {
    }

    /**
     * Genera el texto del CSV
     * @param movimientos: movimientos a exportar
     * @return Devuelve el contenido del archivo, con saltos de línea \r\n
     */
    public static String generar(List<FilaMovimiento> movimientos) {
        StringBuilder csv = new StringBuilder(CABECERA).append("\r\n");
        for (FilaMovimiento f : movimientos) {
            BigDecimal importe = f.movimiento().getImporte();
            if (!f.esIngreso()) {
                importe = importe.negate();
            }
            csv.append(campo(fecha(f.movimiento().getFecha()))).append(';')
                    .append(f.esIngreso() ? "Ingreso" : "Gasto").append(';')
                    .append(campo(f.concepto())).append(';')
                    .append(campo(f.categoria())).append(';')
                    .append(campo(f.cuenta())).append(';')
                    .append(importe.toPlainString().replace('.', ','))
                    .append("\r\n");
        }
        return csv.toString();
    }

    /**
     * Pasa una fecha yyyy-MM-dd a dd/MM/aaaa
     * @param iso: fecha de la base de datos
     * @return Devuelve la fecha, o el texto tal cual si no es válida
     */
    private static String fecha(String iso) {
        try {
            return LocalDate.parse(iso).format(FECHA);
        } catch (DateTimeParseException | NullPointerException e) {
            return iso == null ? "" : iso;
        }
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
