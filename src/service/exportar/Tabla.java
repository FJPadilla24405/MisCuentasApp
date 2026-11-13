package service.exportar;

import java.math.BigDecimal;
import java.util.List;

/**
 * Una tabla de datos lista para escribirse en cualquier formato: el CSV, el Excel y el PDF reciben las mismas
 * tablas y cada uno las dibuja a su manera. Las celdas son String, Integer, LocalDate, Dinero, Porcentaje o null
 * (celda vacía).
 *
 * @param titulo: título de la tabla
 * @param columnas: nombre de cada columna
 * @param filas: filas de la tabla, cada una con tantas celdas como columnas
 * @param filaTotal: posición de la fila que es un total (se escribe destacada), o -1 si no hay
 * @param anchos: ancho de cada columna en % para el PDF (la suma es 100)
 * @author Fco Padilla
 */
public record Tabla(String titulo, List<String> columnas, List<List<Object>> filas, int filaTotal, double[] anchos) {

    /**
     * Cómo se pinta un importe en el PDF.
     */
    public enum Modo {
        // Importe normal, sin signo
        NORMAL,
        // Balance: con signo y en verde, rojo o gris
        BALANCE,
        // Importe de un movimiento: con signo; los ingresos en verde
        MOVIMIENTO
    }

    /**
     * Un importe de dinero.
     * @param valor: cantidad (negativa en los gastos de una tabla de movimientos)
     * @param modo: cómo se escribe en el PDF
     */
    public record Dinero(BigDecimal valor, Modo modo) {
    }

    /**
     * Un porcentaje, por ejemplo 48,4 para el 48,4 %.
     * @param valor: porcentaje
     */
    public record Porcentaje(BigDecimal valor) {
    }

    /**
     * Un grupo de tablas con nombre: en el Excel es una hoja y en el CSV un bloque.
     * @param nombre: nombre del grupo (Resumen, Gráficas o Movimientos)
     * @param tablas: tablas del grupo
     */
    public record Seccion(String nombre, List<Tabla> tablas) {
    }
}
