package service.exportar;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import models.Cuenta;
import models.Usuario;
import service.resumen.GraficasService;
import service.resumen.GraficasService.Anual;
import service.resumen.GraficasService.Mensual;
import service.resumen.GraficasService.Porcion;
import service.resumen.ResumenService.FilaMovimiento;

/**
 * Todo lo que hace falta para escribir un informe de un periodo: las cifras, el gasto por categoría, los datos de
 * las gráficas y los movimientos. Lo calcula ExportacionService.
 *
 * @param usuario: usuario de la sesión
 * @param cuenta: cuenta exportada, o null para todas
 * @param ambito: nombre de lo exportado ("Todas las cuentas" o el nombre de la cuenta)
 * @param moneda: código de moneda de tres letras
 * @param anual: true si el periodo es un año
 * @param periodo: periodo escrito ("Septiembre 2026" o "2026")
 * @param rango: días que cubre el periodo ("1–30 septiembre 2026")
 * @param saldoTotal: saldo al terminar el periodo
 * @param ingresos: ingresos del periodo
 * @param gastos: gastos del periodo
 * @param categorias: gasto por categoría, de mayor a menor
 * @param mensual: datos de las gráficas del mes (null si el periodo es un año)
 * @param datosAnio: datos de las gráficas del año (null si el periodo es un mes)
 * @param movimientos: movimientos del periodo, del más reciente al más antiguo
 * @author Fco Padilla
 */
public record DatosExportacion(Usuario usuario, Cuenta cuenta, String ambito, String moneda, boolean anual,
        String periodo, String rango, BigDecimal saldoTotal, BigDecimal ingresos, BigDecimal gastos,
        List<Porcion> categorias, Mensual mensual, Anual datosAnio, List<FilaMovimiento> movimientos) {

    /**
     * @return Devuelve ingresos menos gastos del periodo
     */
    public BigDecimal balance() {
        return ingresos.subtract(gastos);
    }

    /**
     * @return Devuelve el porcentaje de los ingresos que se ha ahorrado (puede ser negativo), o null si no hay ingresos
     */
    public BigDecimal tasaAhorro() {
        if (ingresos.signum() <= 0) {
            return null;
        }
        return balance().multiply(BigDecimal.valueOf(100)).divide(ingresos, 1, RoundingMode.HALF_UP);
    }

    /**
     * @return Devuelve cuántos movimientos tiene el periodo
     */
    public int numMovimientos() {
        return movimientos.size();
    }

    /**
     * @param categoria: gasto de una categoría
     * @return Devuelve el porcentaje que supone del gasto total, o null si no hay gastos
     */
    public BigDecimal porcentajeDelGasto(Porcion categoria) {
        if (gastos.signum() <= 0) {
            return null;
        }
        return categoria.importe().multiply(BigDecimal.valueOf(100)).divide(gastos, 1, RoundingMode.HALF_UP);
    }

    /**
     * Deja como mucho un número de trozos para el anillo y el reparto del gasto: si hay más, los últimos se suman en "Otras"
     * @param max: máximo de trozos
     * @return Devuelve las categorías agrupadas
     */
    public List<Porcion> categoriasAgrupadas(int max) {
        return GraficasService.agrupar(categorias, max);
    }
}
