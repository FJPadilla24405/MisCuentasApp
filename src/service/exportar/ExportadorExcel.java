package service.exportar;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import service.exportar.Tabla.Dinero;
import service.exportar.Tabla.Modo;
import service.exportar.Tabla.Porcentaje;
import service.exportar.Tabla.Seccion;
import service.exportar.XlsxWriter.Celda;
import service.exportar.XlsxWriter.Hoja;
import util.Formato;

/**
 * Convierte las secciones de una exportación en un libro de Excel: una hoja por sección (Resumen, Gráficas,
 * Movimientos) y, dentro de cada hoja, las tablas una debajo de otra con su título. Los importes son números de
 * verdad (se pueden sumar y ordenar) con formato de moneda.
 *
 * @author Fco Padilla
 */
public final class ExportadorExcel {

    // Límites del ancho de columna, en caracteres
    private static final double ANCHO_MINIMO = 12;
    private static final double ANCHO_MAXIMO = 46;

    // No se instancia
    private ExportadorExcel() {
    }

    /**
     * Escribe el libro en un archivo
     * @param destino: archivo .xlsx
     * @param secciones: secciones a escribir
     * @param moneda: código de moneda de tres letras
     * @throws IOException si no se puede escribir el archivo
     */
    public static void escribir(Path destino, List<Seccion> secciones, String moneda) throws IOException {
        List<Hoja> hojas = new ArrayList<>();
        for (Seccion seccion : secciones) {
            hojas.add(hoja(seccion));
        }
        try (var salida = Files.newOutputStream(destino)) {
            XlsxWriter.escribir(salida, hojas, Formato.simbolo(moneda));
        }
    }

    /**
     * Crea la hoja de una sección: cada tabla con su título, su fila de títulos y sus filas, y una fila en blanco entre tablas
     * @param seccion: sección a escribir
     * @return Devuelve la hoja
     */
    private static Hoja hoja(Seccion seccion) {
        List<List<Celda>> filas = new ArrayList<>();
        int numColumnas = 0;
        for (Tabla tabla : seccion.tablas()) {
            numColumnas = Math.max(numColumnas, tabla.columnas().size());
        }
        double[] anchos = new double[numColumnas];
        java.util.Arrays.fill(anchos, ANCHO_MINIMO);

        for (int t = 0; t < seccion.tablas().size(); t++) {
            Tabla tabla = seccion.tablas().get(t);
            if (t > 0) {
                filas.add(List.of());
            }
            filas.add(List.of(new Celda(tabla.titulo(), XlsxWriter.TITULO)));

            List<Celda> cabecera = new ArrayList<>();
            for (int c = 0; c < tabla.columnas().size(); c++) {
                cabecera.add(new Celda(tabla.columnas().get(c), c == 0 ? XlsxWriter.CABECERA : XlsxWriter.CABECERA_DERECHA));
                ajustar(anchos, c, tabla.columnas().get(c), false);
            }
            filas.add(cabecera);

            for (int f = 0; f < tabla.filas().size(); f++) {
                boolean total = f == tabla.filaTotal();
                List<Celda> fila = new ArrayList<>();
                List<Object> valores = tabla.filas().get(f);
                for (int c = 0; c < valores.size(); c++) {
                    Object v = valores.get(c);
                    fila.add(celda(v, total));
                    ajustar(anchos, c, textoAproximado(v), v instanceof Dinero);
                }
                filas.add(fila);
            }
        }
        // El título de cada tabla no ensancha la primera columna: es largo y se desborda hacia la derecha
        return new Hoja(seccion.nombre(), filas, anchos);
    }

    /**
     * @param valor: contenido de una celda de la tabla
     * @param total: true si es de la fila del total
     * @return Devuelve la celda de Excel con su estilo
     */
    private static Celda celda(Object valor, boolean total) {
        if (valor instanceof Dinero d) {
            boolean signo = d.modo() != Modo.NORMAL;
            int estilo = total ? (signo ? XlsxWriter.TOTAL_DINERO_SIGNO : XlsxWriter.TOTAL_DINERO)
                    : (signo ? XlsxWriter.DINERO_SIGNO : XlsxWriter.DINERO);
            return new Celda(d.valor(), estilo);
        }
        if (valor instanceof Porcentaje p) {
            return new Celda(p.valor(), total ? XlsxWriter.TOTAL_PORCENTAJE : XlsxWriter.PORCENTAJE);
        }
        if (valor instanceof LocalDate f) {
            return new Celda(f, XlsxWriter.FECHA);
        }
        if (valor == null) {
            return new Celda(null, total ? XlsxWriter.TOTAL_TEXTO : XlsxWriter.NORMAL);
        }
        return new Celda(valor.toString(), total ? XlsxWriter.TOTAL_TEXTO : XlsxWriter.TEXTO);
    }

    /**
     * @param valor: contenido de una celda
     * @return Devuelve un texto parecido a lo que se verá, para calcular el ancho de la columna
     */
    private static String textoAproximado(Object valor) {
        if (valor == null) {
            return "";
        }
        if (valor instanceof Dinero d) {
            return Formato.moneda(d.valor().abs(), "EUR") + " +";
        }
        if (valor instanceof Porcentaje) {
            return "000,0 %";
        }
        if (valor instanceof LocalDate) {
            return "00/00/0000";
        }
        return valor.toString();
    }

    /**
     * Ensancha una columna si el texto no cabe
     * @param anchos: ancho actual de cada columna
     * @param columna: columna del texto
     * @param texto: texto que tiene que caber
     * @param numero: true si es un importe (necesita algo más de sitio por el símbolo)
     */
    private static void ajustar(double[] anchos, int columna, String texto, boolean numero) {
        if (columna >= anchos.length) {
            return;
        }
        double necesario = Math.min(ANCHO_MAXIMO, texto.length() * 1.15 + 3 + (numero ? 1 : 0));
        if (necesario > anchos[columna]) {
            anchos[columna] = necesario;
        }
    }
}
