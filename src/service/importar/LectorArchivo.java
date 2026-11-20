package service.importar;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Lee un archivo de movimientos (CSV, Excel .xls o Excel .xlsx) y lo deja como una tabla de celdas. El tipo se
 * averigua mirando el contenido, no la extensión: un CSV guardado como .xls o un .xlsx sin extensión se leen igual.
 * Si el archivo no se puede importar, la excepción lleva el motivo, escrito para el usuario.
 *
 * @author Fco Padilla
 */
public final class LectorArchivo {

    // Tamaño máximo del archivo: 15 MB
    public static final long MAX_BYTES = 15L * 1024 * 1024;

    // No se instancia
    private LectorArchivo() {
    }

    /**
     * Lee y comprueba un archivo
     * @param archivo: ruta del archivo
     * @return Devuelve el contenido del archivo, ya leído
     * @throws ArchivoNoValido si el archivo no existe, está vacío, es demasiado grande, no es de un tipo que se pueda
     *         importar o no tiene filas con datos
     */
    public static ArchivoImportado leer(Path archivo) throws ArchivoNoValido {
        String nombre = archivo.getFileName() == null ? archivo.toString() : archivo.getFileName().toString();
        long bytes;
        try {
            if (!Files.isRegularFile(archivo)) {
                throw new ArchivoNoValido("No se encuentra el archivo «" + nombre + "».");
            }
            bytes = Files.size(archivo);
        } catch (IOException e) {
            throw new ArchivoNoValido("No se puede abrir el archivo «" + nombre + "».", e);
        }
        if (bytes == 0) {
            throw new ArchivoNoValido("El archivo «" + nombre + "» está vacío.");
        }
        if (bytes > MAX_BYTES) {
            throw new ArchivoNoValido("El archivo «" + nombre + "» es demasiado grande (el máximo es 15 MB).");
        }

        byte[] datos;
        try {
            datos = Files.readAllBytes(archivo);
        } catch (IOException e) {
            throw new ArchivoNoValido("No se puede leer el archivo «" + nombre + "».", e);
        }

        TipoArchivo tipo;
        String hoja = null;
        List<List<Object>> filas;
        if (empiezaPor(datos, "%PDF")) {
            throw new ArchivoNoValido("Los PDF no se pueden importar. Descarga el extracto de tu banco en CSV o Excel.");
        } else if (LectorXls.esOle2(datos)) {
            LectorXls.Hoja h = LectorXls.leer(datos);
            tipo = TipoArchivo.EXCEL_XLS;
            hoja = h.nombre();
            filas = h.filas();
        } else if (empiezaPor(datos, "PK")) {
            if (!LectorXlsx.esLibroXlsx(archivo)) {
                throw new ArchivoNoValido("El archivo «" + nombre + "» es un archivo comprimido, no una hoja de Excel.");
            }
            LectorXls.Hoja h = LectorXlsx.leer(archivo);
            tipo = TipoArchivo.EXCEL_XLSX;
            hoja = h.nombre();
            filas = h.filas();
        } else {
            tipo = TipoArchivo.CSV;
            filas = LectorCsv.leer(datos);
        }

        if (filas.size() < 2) {
            throw new ArchivoNoValido("El archivo «" + nombre + "» no tiene movimientos: hacen falta, al menos, una fila de títulos y una de datos.");
        }
        return new ArchivoImportado(nombre, bytes, tipo, hoja, filas);
    }

    private static boolean empiezaPor(byte[] datos, String texto) {
        if (datos.length < texto.length()) {
            return false;
        }
        for (int i = 0; i < texto.length(); i++) {
            if (datos[i] != (byte) texto.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}
