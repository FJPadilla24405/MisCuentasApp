package service.exportar;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Escribe un libro de Excel (.xlsx) sin necesidad de ninguna librería: un .xlsx es un zip con unos cuantos XML.
 * Solo sabe hacer lo que necesita la exportación: varias hojas, textos, números, fechas, importes con su símbolo,
 * porcentajes, una fila de títulos con color y una fila de total destacada.
 *
 * @author Fco Padilla
 */
public final class XlsxWriter {

    // Estilos de celda (posición en cellXfs de styles.xml)
    public static final int NORMAL = 0;
    public static final int TITULO = 1;
    public static final int CABECERA = 2;
    public static final int CABECERA_DERECHA = 3;
    public static final int TEXTO = 4;
    public static final int DINERO = 5;
    public static final int DINERO_SIGNO = 6;
    public static final int PORCENTAJE = 7;
    public static final int FECHA = 8;
    public static final int TOTAL_TEXTO = 9;
    public static final int TOTAL_DINERO = 10;
    public static final int TOTAL_DINERO_SIGNO = 11;
    public static final int TOTAL_PORCENTAJE = 12;

    // Día cero de Excel: las fechas se guardan como días desde aquí
    private static final LocalDate EPOCA = LocalDate.of(1899, 12, 30);

    /**
     * Una celda: su valor (String, BigDecimal, Integer, LocalDate o null) y su estilo.
     * @param valor: contenido
     * @param estilo: una de las constantes de estilo de esta clase
     */
    public record Celda(Object valor, int estilo) {
    }

    /**
     * Una hoja del libro.
     * @param nombre: nombre de la pestaña
     * @param filas: filas de celdas (las celdas null quedan vacías)
     * @param anchos: ancho de cada columna en caracteres
     */
    public record Hoja(String nombre, List<List<Celda>> filas, double[] anchos) {
    }

    // No se instancia
    private XlsxWriter() {
    }

    /**
     * Escribe el libro
     * @param salida: dónde se escribe el archivo (se cierra al terminar)
     * @param hojas: hojas del libro, al menos una
     * @param simboloMoneda: símbolo que acompaña a los importes (€, $...)
     * @throws IOException si falla la escritura
     */
    public static void escribir(OutputStream salida, List<Hoja> hojas, String simboloMoneda) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(salida, StandardCharsets.UTF_8)) {
            anadir(zip, "[Content_Types].xml", tiposDeContenido(hojas.size()));
            anadir(zip, "_rels/.rels", relacionesRaiz());
            anadir(zip, "xl/workbook.xml", libro(hojas));
            anadir(zip, "xl/_rels/workbook.xml.rels", relacionesLibro(hojas.size()));
            anadir(zip, "xl/styles.xml", estilos(simboloMoneda));
            for (int i = 0; i < hojas.size(); i++) {
                anadir(zip, "xl/worksheets/sheet" + (i + 1) + ".xml", hoja(hojas.get(i)));
            }
        }
    }

    private static void anadir(ZipOutputStream zip, String nombre, String contenido) throws IOException {
        zip.putNextEntry(new ZipEntry(nombre));
        zip.write(contenido.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String tiposDeContenido(int numHojas) {
        StringBuilder x = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
                .append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
                .append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
                .append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
                .append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
                .append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
        for (int i = 1; i <= numHojas; i++) {
            x.append("<Override PartName=\"/xl/worksheets/sheet").append(i)
                    .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        }
        return x.append("</Types>").toString();
    }

    private static String relacionesRaiz() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                + "</Relationships>";
    }

    private static String libro(List<Hoja> hojas) {
        StringBuilder x = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
                .append("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" ")
                .append("xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>");
        for (int i = 0; i < hojas.size(); i++) {
            x.append("<sheet name=\"").append(escapar(nombreHoja(hojas.get(i).nombre()))).append("\" sheetId=\"")
                    .append(i + 1).append("\" r:id=\"rId").append(i + 1).append("\"/>");
        }
        return x.append("</sheets></workbook>").toString();
    }

    private static String relacionesLibro(int numHojas) {
        StringBuilder x = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
                .append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        for (int i = 1; i <= numHojas; i++) {
            x.append("<Relationship Id=\"rId").append(i)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet")
                    .append(i).append(".xml\"/>");
        }
        x.append("<Relationship Id=\"rId").append(numHojas + 1)
                .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>");
        return x.append("</Relationships>").toString();
    }

    /**
     * Estilos del libro: letras, rellenos, formatos de número y las combinaciones que usan las celdas.
     * @param simbolo: símbolo de la moneda
     * @return Devuelve styles.xml
     */
    private static String estilos(String simbolo) {
        String s = escapar(simbolo == null ? "" : simbolo).replace("\"", "&quot;");
        String dinero = "#,##0.00\\ &quot;" + s + "&quot;";
        String signo = "+#,##0.00\\ &quot;" + s + "&quot;;\\-#,##0.00\\ &quot;" + s + "&quot;;#,##0.00\\ &quot;" + s + "&quot;";
        String porcentaje = "0.0\\ &quot;%&quot;";
        StringBuilder x = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
                .append("<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
                .append("<numFmts count=\"4\">")
                .append("<numFmt numFmtId=\"164\" formatCode=\"").append(dinero).append("\"/>")
                .append("<numFmt numFmtId=\"165\" formatCode=\"").append(signo).append("\"/>")
                .append("<numFmt numFmtId=\"166\" formatCode=\"").append(porcentaje).append("\"/>")
                .append("<numFmt numFmtId=\"167\" formatCode=\"dd/mm/yyyy\"/>")
                .append("</numFmts>")
                // 0 normal, 1 negrita, 2 negrita blanca, 3 negrita verde oscuro más grande
                .append("<fonts count=\"4\">")
                .append("<font><sz val=\"11\"/><name val=\"Calibri\"/></font>")
                .append("<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font>")
                .append("<font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/></font>")
                .append("<font><b/><sz val=\"13\"/><color rgb=\"FF1B3A2D\"/><name val=\"Calibri\"/></font>")
                .append("</fonts>")
                // 0 y 1 son obligatorios; 2 verde de los títulos; 3 verde claro de los totales
                .append("<fills count=\"4\">")
                .append("<fill><patternFill patternType=\"none\"/></fill>")
                .append("<fill><patternFill patternType=\"gray125\"/></fill>")
                .append("<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF287A57\"/><bgColor indexed=\"64\"/></patternFill></fill>")
                .append("<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFE8F1EC\"/><bgColor indexed=\"64\"/></patternFill></fill>")
                .append("</fills>")
                .append("<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>")
                .append("<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>")
                .append("<cellXfs count=\"13\">")
                .append(xf(0, 0, 0, null))      // 0 normal
                .append(xf(0, 3, 0, null))      // 1 título de tabla
                .append(xf(0, 2, 2, "left"))    // 2 cabecera
                .append(xf(0, 2, 2, "right"))   // 3 cabecera a la derecha
                .append(xf(0, 0, 0, null))      // 4 texto
                .append(xf(164, 0, 0, null))    // 5 dinero
                .append(xf(165, 0, 0, null))    // 6 dinero con signo
                .append(xf(166, 0, 0, null))    // 7 porcentaje
                .append(xf(167, 0, 0, "left"))  // 8 fecha
                .append(xf(0, 1, 3, null))      // 9 total texto
                .append(xf(164, 1, 3, null))    // 10 total dinero
                .append(xf(165, 1, 3, null))    // 11 total dinero con signo
                .append(xf(166, 1, 3, null))    // 12 total porcentaje
                .append("</cellXfs>")
                .append("<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>")
                .append("</styleSheet>");
        return x.toString();
    }

    private static String xf(int formato, int fuente, int relleno, String alineacion) {
        StringBuilder x = new StringBuilder("<xf numFmtId=\"").append(formato).append("\" fontId=\"").append(fuente)
                .append("\" fillId=\"").append(relleno).append("\" borderId=\"0\" xfId=\"0\"");
        if (formato != 0) {
            x.append(" applyNumberFormat=\"1\"");
        }
        if (fuente != 0) {
            x.append(" applyFont=\"1\"");
        }
        if (relleno != 0) {
            x.append(" applyFill=\"1\"");
        }
        if (alineacion != null) {
            x.append(" applyAlignment=\"1\"><alignment horizontal=\"").append(alineacion).append("\"/></xf>");
        } else {
            x.append("/>");
        }
        return x.toString();
    }

    private static String hoja(Hoja hoja) {
        StringBuilder x = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
                .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        if (hoja.anchos() != null && hoja.anchos().length > 0) {
            x.append("<cols>");
            for (int i = 0; i < hoja.anchos().length; i++) {
                x.append("<col min=\"").append(i + 1).append("\" max=\"").append(i + 1).append("\" width=\"")
                        .append(hoja.anchos()[i]).append("\" customWidth=\"1\"/>");
            }
            x.append("</cols>");
        }
        x.append("<sheetData>");
        int numFila = 1;
        for (List<Celda> fila : hoja.filas()) {
            x.append("<row r=\"").append(numFila).append("\">");
            for (int c = 0; c < fila.size(); c++) {
                Celda celda = fila.get(c);
                if (celda == null) {
                    continue;
                }
                escribirCelda(x, referencia(c, numFila), celda);
            }
            x.append("</row>");
            numFila++;
        }
        return x.append("</sheetData></worksheet>").toString();
    }

    private static void escribirCelda(StringBuilder x, String ref, Celda celda) {
        Object v = celda.valor();
        int s = celda.estilo();
        if (v == null) {
            // Celda vacía que conserva el color (por ejemplo, la de un total sin dato)
            x.append("<c r=\"").append(ref).append("\" s=\"").append(s).append("\"/>");
        } else if (v instanceof BigDecimal n) {
            x.append("<c r=\"").append(ref).append("\" s=\"").append(s).append("\"><v>").append(n.toPlainString()).append("</v></c>");
        } else if (v instanceof Integer n) {
            x.append("<c r=\"").append(ref).append("\" s=\"").append(s).append("\"><v>").append(n).append("</v></c>");
        } else if (v instanceof LocalDate f) {
            x.append("<c r=\"").append(ref).append("\" s=\"").append(s).append("\"><v>")
                    .append(ChronoUnit.DAYS.between(EPOCA, f)).append("</v></c>");
        } else {
            x.append("<c r=\"").append(ref).append("\" s=\"").append(s).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                    .append(escapar(v.toString())).append("</t></is></c>");
        }
    }

    /**
     * @param columna: columna empezando en 0
     * @param fila: fila empezando en 1
     * @return Devuelve la referencia de la celda (A1, B7, AA3...)
     */
    private static String referencia(int columna, int fila) {
        StringBuilder letras = new StringBuilder();
        int c = columna;
        do {
            letras.insert(0, (char) ('A' + c % 26));
            c = c / 26 - 1;
        } while (c >= 0);
        return letras.toString() + fila;
    }

    /**
     * Quita lo que Excel no admite en el nombre de una pestaña (: \ / ? * [ ]) y lo recorta a 31 caracteres
     * @param nombre: nombre deseado
     * @return Devuelve un nombre válido
     */
    private static String nombreHoja(String nombre) {
        String limpio = nombre.replaceAll("[:\\\\/?*\\[\\]]", " ").trim();
        if (limpio.isEmpty()) {
            limpio = "Hoja";
        }
        return limpio.length() > 31 ? limpio.substring(0, 31) : limpio;
    }

    /**
     * Escapa el texto para ponerlo en un XML y quita los caracteres de control que XML no permite
     * @param texto: texto original
     * @return Devuelve el texto seguro
     */
    private static String escapar(String texto) {
        List<Integer> validos = new ArrayList<>();
        texto.codePoints().forEach(cp -> {
            boolean ok = cp == 0x9 || cp == 0xA || cp == 0xD || (cp >= 0x20 && cp <= 0xD7FF)
                    || (cp >= 0xE000 && cp <= 0xFFFD) || (cp >= 0x10000 && cp <= 0x10FFFF);
            if (ok) {
                validos.add(cp);
            }
        });
        StringBuilder x = new StringBuilder();
        for (int cp : validos) {
            switch (cp) {
                case '&' -> x.append("&amp;");
                case '<' -> x.append("&lt;");
                case '>' -> x.append("&gt;");
                case '"' -> x.append("&quot;");
                default -> x.appendCodePoint(cp);
            }
        }
        return x.toString();
    }
}
