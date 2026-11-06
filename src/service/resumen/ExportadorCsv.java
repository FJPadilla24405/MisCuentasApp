package service.resumen;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

import service.resumen.GraficasService.Anual;
import service.resumen.GraficasService.FilaCuentaMes;
import service.resumen.GraficasService.FilaMes;
import service.resumen.GraficasService.Mensual;
import service.resumen.GraficasService.Porcion;
import service.resumen.ResumenService.FilaMovimiento;
import util.Formato;

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
     * Genera el CSV del resumen mensual de Gráficas y resúmenes: la tabla cuenta por cuenta con su total y, debajo,
     * el gasto por categoría. Los importes van sin símbolo de moneda y con coma decimal.
     * @param datos: datos del mes
     * @return Devuelve el contenido del archivo, con saltos de línea \r\n
     */
    public static String generarResumenMensual(Mensual datos) {
        StringBuilder csv = new StringBuilder("Cuenta;Saldo inicial;Ingresos;Gastos;Balance;Saldo actual").append("\r\n");
        for (FilaCuentaMes f : datos.cuentas()) {
            csv.append(campo(f.cuenta().getNombre())).append(';')
                    .append(numero(f.saldoInicial())).append(';')
                    .append(numero(f.ingresos())).append(';')
                    .append(numero(f.gastos())).append(';')
                    .append(numero(f.balance())).append(';')
                    .append(numero(f.saldoFinal())).append("\r\n");
        }
        csv.append("Total;")
                .append(numero(datos.saldoInicialTotal())).append(';')
                .append(numero(datos.ingresos())).append(';')
                .append(numero(datos.gastos())).append(';')
                .append(numero(datos.balance())).append(';')
                .append(numero(datos.saldoFinalTotal())).append("\r\n");

        csv.append("\r\n").append("Categoría;Gasto;% del gasto").append("\r\n");
        for (Porcion p : datos.categorias()) {
            csv.append(campo(p.categoria())).append(';').append(numero(p.importe())).append(';');
            if (datos.gastos().signum() > 0) {
                csv.append(numero(p.importe().multiply(java.math.BigDecimal.valueOf(100))
                        .divide(datos.gastos(), 1, java.math.RoundingMode.HALF_UP)));
            }
            csv.append("\r\n");
        }
        return csv.toString();
    }

    /**
     * Genera el CSV del resumen anual de Gráficas y resúmenes: una fila por mes y la del total del año.
     * @param datos: datos del año
     * @return Devuelve el contenido del archivo, con saltos de línea \r\n
     */
    public static String generarResumenAnual(Anual datos) {
        StringBuilder csv = new StringBuilder("Mes;Ingresos;Gastos;Balance neto;Tasa de ahorro (%)").append("\r\n");
        for (FilaMes f : datos.meses()) {
            csv.append(Formato.nombreMesMayuscula(f.mes())).append(';')
                    .append(numero(f.ingresos())).append(';')
                    .append(numero(f.gastos())).append(';')
                    .append(numero(f.balance())).append(';')
                    .append(f.tasaAhorro() == null ? "" : numero(f.tasaAhorro())).append("\r\n");
        }
        csv.append("Total ").append(datos.anio()).append(';')
                .append(numero(datos.ingresos())).append(';')
                .append(numero(datos.gastos())).append(';')
                .append(numero(datos.balance())).append(';')
                .append(datos.tasaAhorro() == null ? "" : numero(datos.tasaAhorro())).append("\r\n");
        return csv.toString();
    }

    /**
     * @param valor: importe o porcentaje
     * @return Devuelve el número escrito con coma decimal y sin separador de miles
     */
    private static String numero(BigDecimal valor) {
        return valor.toPlainString().replace('.', ',');
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
