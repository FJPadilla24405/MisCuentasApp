package service.exportar;

import java.time.YearMonth;

import models.Cuenta;
import util.Formato;

/**
 * Lo que se ha elegido en el diálogo de exportar: formato, cuenta, periodo, qué contenido entra en el archivo y
 * cómo se llama.
 *
 * @param formato: PDF, CSV o Excel
 * @param cuenta: cuenta a exportar, o null para todas
 * @param anual: true si el periodo es un año entero y false si es un mes
 * @param mes: mes a exportar (se usa si anual es false)
 * @param anio: año a exportar (se usa si anual es true)
 * @param resumen: incluir el resumen de ingresos, gastos y saldos
 * @param graficas: incluir las gráficas (en CSV y Excel, los datos con los que se dibujan)
 * @param movimientos: incluir la tabla detallada de movimientos
 * @param notas: incluir la nota de cada movimiento (solo tiene efecto con la tabla de movimientos)
 * @param nombreArchivo: nombre del archivo, con su extensión
 * @author Fco Padilla
 */
public record OpcionesExportacion(FormatoArchivo formato, Cuenta cuenta, boolean anual, YearMonth mes, int anio,
        boolean resumen, boolean graficas, boolean movimientos, boolean notas, String nombreArchivo) {

    /**
     * Opciones con las que se abre el diálogo: PDF, sin notas y con solo lo que se pide activado
     * @param cuenta: cuenta de la pantalla desde la que se abre, o null para todas
     * @param anual: true si la pantalla estaba en vista anual
     * @param mes: mes de la pantalla
     * @param anio: año de la pantalla
     * @param resumen: activar el resumen
     * @param graficas: activar las gráficas
     * @return Devuelve las opciones iniciales (el nombre lo pone el diálogo)
     */
    public static OpcionesExportacion inicial(Cuenta cuenta, boolean anual, YearMonth mes, int anio, boolean resumen,
            boolean graficas) {
        return new OpcionesExportacion(FormatoArchivo.PDF, cuenta, anual, mes, anio, resumen, graficas, false, false, "");
    }

    /**
     * @return Devuelve true si hay algo que exportar (al menos una de las tres partes activada)
     */
    public boolean hayContenido() {
        return resumen || graficas || movimientos;
    }

    /**
     * @return Devuelve el comienzo de las fechas del periodo (yyyy-MM para un mes, yyyy para un año), para filtrar movimientos
     */
    public String prefijoFecha() {
        return anual ? String.valueOf(anio) : mes.toString();
    }

    /**
     * @return Devuelve el periodo escrito como se ve en los informes: "Septiembre 2026" o "2026"
     */
    public String textoPeriodo() {
        return anual ? String.valueOf(anio) : Formato.mesConAnio(mes);
    }
}
