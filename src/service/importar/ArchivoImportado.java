package service.importar;

import java.util.List;

/**
 * El contenido de un archivo ya leído: una tabla de celdas, sin interpretar. Las celdas son String, BigDecimal (un
 * número de Excel), LocalDate (una fecha de Excel) o null (vacía). Un CSV solo tiene celdas String: lo que significan lo
 * decide ImportacionService con el formato elegido.
 *
 * @param nombre: nombre del archivo
 * @param bytes: tamaño del archivo
 * @param tipo: tipo de archivo detectado
 * @param hoja: nombre de la hoja leída (null en un CSV)
 * @param filas: filas con datos, sin filas vacías (cada fila tiene tantas celdas como columnas haya hasta su última celda con algo)
 * @author Fco Padilla
 */
public record ArchivoImportado(String nombre, long bytes, TipoArchivo tipo, String hoja, List<List<Object>> filas) {

    /**
     * @return Devuelve el número de columnas de la fila más ancha
     */
    public int numColumnas() {
        int max = 0;
        for (List<Object> fila : filas) {
            max = Math.max(max, fila.size());
        }
        return max;
    }
}
