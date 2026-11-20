package service.importar;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Lee un libro de Excel moderno (.xlsx) sin ninguna librería: es un zip con XML dentro. Se lee la primera hoja visible
 * que tenga datos, con sus textos compartidos y los estilos de número para saber qué celdas son fechas.
 *
 * @author Fco Padilla
 */
final class LectorXlsx {

    private static final int MAX_FILAS = 200_000;
    private static final int MAX_COLUMNAS = 256;
    // Tope de lo que se descomprime de cada parte del zip, por si es una bomba de compresión
    private static final long MAX_BYTES_PARTE = 300L * 1024 * 1024;

    private final ZipFile zip;
    private final List<String> textos = new ArrayList<>();
    // Para cada estilo de celda (cellXfs), si su formato de número es de fecha
    private final List<Boolean> estiloEsFecha = new ArrayList<>();
    private boolean sistema1904 = false;

    private LectorXlsx(ZipFile zip) {
        this.zip = zip;
    }

    /**
     * @param archivo: archivo a comprobar
     * @return Devuelve true si es un zip que lleva un libro de Excel (xl/workbook.xml)
     */
    static boolean esLibroXlsx(Path archivo) {
        try (ZipFile zip = new ZipFile(archivo.toFile())) {
            return zip.getEntry("xl/workbook.xml") != null;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /**
     * Lee la primera hoja visible con datos
     * @param archivo: archivo .xlsx
     * @return Devuelve el nombre de la hoja y sus filas (las celdas son String, BigDecimal, LocalDate o null)
     * @throws ArchivoNoValido si no es un libro de Excel que se pueda leer
     */
    static LectorXls.Hoja leer(Path archivo) throws ArchivoNoValido {
        try (ZipFile zip = new ZipFile(archivo.toFile())) {
            return new LectorXlsx(zip).leerLibro();
        } catch (ZipException e) {
            throw new ArchivoNoValido("El archivo de Excel está dañado y no se puede abrir.", e);
        } catch (IOException | XMLStreamException | RuntimeException e) {
            throw new ArchivoNoValido("No se ha podido leer el archivo de Excel: parece dañado.", e);
        }
    }

    // ===== Libro =====

    private LectorXls.Hoja leerLibro() throws IOException, XMLStreamException, ArchivoNoValido {
        // Hojas: nombre, identificador de relación y si están ocultas
        record Pestana(String nombre, String relacion, boolean oculta) {
        }
        List<Pestana> pestanas = new ArrayList<>();
        XMLStreamReader r = abrirXml("xl/workbook.xml");
        try {
            while (r.hasNext()) {
                if (r.next() == XMLStreamConstants.START_ELEMENT) {
                    if (r.getLocalName().equals("workbookPr")) {
                        String d = r.getAttributeValue(null, "date1904");
                        sistema1904 = "1".equals(d) || "true".equalsIgnoreCase(d);
                    } else if (r.getLocalName().equals("sheet")) {
                        String estado = r.getAttributeValue(null, "state");
                        pestanas.add(new Pestana(r.getAttributeValue(null, "name"), atributoRelacion(r), estado != null && !estado.equals("visible")));
                    }
                }
            }
        } finally {
            r.close();
        }
        Map<String, String> destinos = leerRelaciones();
        leerTextosCompartidos();
        leerEstilos();

        for (Pestana p : pestanas) {
            String destino = destinos.get(p.relacion());
            if (p.oculta() || destino == null) {
                continue;
            }
            String ruta = destino.startsWith("/") ? destino.substring(1) : "xl/" + destino;
            if (zip.getEntry(ruta) == null) {
                continue;
            }
            List<List<Object>> filas = leerHoja(ruta);
            if (!filas.isEmpty()) {
                return new LectorXls.Hoja(p.nombre(), filas);
            }
        }
        throw new ArchivoNoValido("El archivo de Excel no tiene ningún dato.");
    }

    /** El atributo r:id de un elemento, sea cual sea el prefijo con el que el archivo declare el espacio de nombres */
    private static String atributoRelacion(XMLStreamReader r) {
        for (int i = 0; i < r.getAttributeCount(); i++) {
            if (r.getAttributeLocalName(i).equals("id")) {
                return r.getAttributeValue(i);
            }
        }
        return null;
    }

    private Map<String, String> leerRelaciones() throws IOException, XMLStreamException {
        Map<String, String> destinos = new HashMap<>();
        if (zip.getEntry("xl/_rels/workbook.xml.rels") == null) {
            return destinos;
        }
        XMLStreamReader r = abrirXml("xl/_rels/workbook.xml.rels");
        try {
            while (r.hasNext()) {
                if (r.next() == XMLStreamConstants.START_ELEMENT && r.getLocalName().equals("Relationship")) {
                    destinos.put(r.getAttributeValue(null, "Id"), r.getAttributeValue(null, "Target"));
                }
            }
        } finally {
            r.close();
        }
        return destinos;
    }

    // ===== Textos compartidos y estilos =====

    private void leerTextosCompartidos() throws IOException, XMLStreamException {
        if (zip.getEntry("xl/sharedStrings.xml") == null) {
            return;
        }
        XMLStreamReader r = abrirXml("xl/sharedStrings.xml");
        try {
            StringBuilder actual = null;
            boolean dentroDeTexto = false;
            // Dentro de <si> puede haber <t> (texto simple), <r><t> (texto con formato) y <rPh><t> (lectura fonética, que se ignora)
            int profundidadFonetica = 0;
            while (r.hasNext()) {
                int tipo = r.next();
                if (tipo == XMLStreamConstants.START_ELEMENT) {
                    switch (r.getLocalName()) {
                        case "si" -> actual = new StringBuilder();
                        case "rPh" -> profundidadFonetica++;
                        case "t" -> dentroDeTexto = profundidadFonetica == 0;
                        default -> { }
                    }
                } else if (tipo == XMLStreamConstants.CHARACTERS && dentroDeTexto && actual != null) {
                    actual.append(r.getText());
                } else if (tipo == XMLStreamConstants.END_ELEMENT) {
                    switch (r.getLocalName()) {
                        case "t" -> dentroDeTexto = false;
                        case "rPh" -> profundidadFonetica--;
                        case "si" -> {
                            textos.add(actual == null ? "" : actual.toString());
                            actual = null;
                        }
                        default -> { }
                    }
                }
            }
        } finally {
            r.close();
        }
    }

    private void leerEstilos() throws IOException, XMLStreamException {
        if (zip.getEntry("xl/styles.xml") == null) {
            return;
        }
        Map<Integer, String> formatosPropios = new HashMap<>();
        List<Integer> formatoDeXf = new ArrayList<>();
        XMLStreamReader r = abrirXml("xl/styles.xml");
        try {
            boolean enCellXfs = false;
            while (r.hasNext()) {
                int tipo = r.next();
                if (tipo == XMLStreamConstants.START_ELEMENT) {
                    switch (r.getLocalName()) {
                        case "numFmt" -> formatosPropios.put(Integer.parseInt(r.getAttributeValue(null, "numFmtId")), r.getAttributeValue(null, "formatCode"));
                        case "cellXfs" -> enCellXfs = true;
                        case "xf" -> {
                            if (enCellXfs) {
                                String id = r.getAttributeValue(null, "numFmtId");
                                formatoDeXf.add(id == null ? 0 : Integer.parseInt(id));
                            }
                        }
                        default -> { }
                    }
                } else if (tipo == XMLStreamConstants.END_ELEMENT && r.getLocalName().equals("cellXfs")) {
                    enCellXfs = false;
                }
            }
        } finally {
            r.close();
        }
        for (int formato : formatoDeXf) {
            estiloEsFecha.add(LectorXls.FormatoFecha.esFecha(formato, formatosPropios.get(formato)));
        }
    }

    // ===== Hoja =====

    private List<List<Object>> leerHoja(String ruta) throws IOException, XMLStreamException {
        Map<Integer, Map<Integer, Object>> celdas = new HashMap<>();
        int maxFila = -1;
        int maxColumna = -1;
        XMLStreamReader r = abrirXml(ruta);
        try {
            int fila = -1;
            while (r.hasNext()) {
                if (r.next() != XMLStreamConstants.START_ELEMENT) {
                    continue;
                }
                if (r.getLocalName().equals("row")) {
                    String n = r.getAttributeValue(null, "r");
                    fila = n != null ? Integer.parseInt(n) - 1 : fila + 1;
                } else if (r.getLocalName().equals("c")) {
                    String referencia = r.getAttributeValue(null, "r");
                    String tipo = r.getAttributeValue(null, "t");
                    String estilo = r.getAttributeValue(null, "s");
                    int columna = referencia == null ? -1 : columnaDe(referencia);
                    int numFila = referencia == null ? fila : filaDe(referencia);
                    Object valor = leerCelda(r, tipo, estilo == null ? 0 : Integer.parseInt(estilo));
                    if (valor != null && columna >= 0 && numFila >= 0 && numFila < MAX_FILAS && columna < MAX_COLUMNAS) {
                        celdas.computeIfAbsent(numFila, f -> new HashMap<>()).put(columna, valor);
                        maxFila = Math.max(maxFila, numFila);
                        maxColumna = Math.max(maxColumna, columna);
                    }
                }
            }
        } finally {
            r.close();
        }
        return aFilas(celdas, maxFila, maxColumna);
    }

    /**
     * Lee el contenido de una celda (el lector está en su etiqueta de apertura) y deja el lector en su cierre
     */
    private Object leerCelda(XMLStreamReader r, String tipo, int estilo) throws XMLStreamException {
        String valor = null;
        StringBuilder enLinea = null;
        int profundidad = 1;
        String abierto = null;
        while (r.hasNext() && profundidad > 0) {
            int e = r.next();
            if (e == XMLStreamConstants.START_ELEMENT) {
                profundidad++;
                abierto = r.getLocalName();
                if (abierto.equals("is")) {
                    enLinea = new StringBuilder();
                }
            } else if (e == XMLStreamConstants.END_ELEMENT) {
                profundidad--;
                abierto = null;
            } else if (e == XMLStreamConstants.CHARACTERS) {
                if ("v".equals(abierto)) {
                    valor = (valor == null ? "" : valor) + r.getText();
                } else if ("t".equals(abierto) && enLinea != null) {
                    enLinea.append(r.getText());
                }
            }
        }
        if ("inlineStr".equals(tipo)) {
            return enLinea == null ? null : enLinea.toString().trim();
        }
        if (valor == null) {
            return null;
        }
        switch (tipo == null ? "n" : tipo) {
            case "s" -> {
                int i = Integer.parseInt(valor.trim());
                return i >= 0 && i < textos.size() ? textos.get(i).trim() : null;
            }
            case "str" -> {
                return valor.trim();
            }
            case "b" -> {
                return "1".equals(valor.trim()) ? "VERDADERO" : "FALSO";
            }
            case "e" -> {
                return null;
            }
            case "d" -> {
                try {
                    return LocalDate.parse(valor.trim().substring(0, Math.min(10, valor.trim().length())));
                } catch (RuntimeException ex) {
                    return null;
                }
            }
            default -> {
                double numero;
                try {
                    numero = Double.parseDouble(valor.trim());
                } catch (NumberFormatException ex) {
                    return valor.trim();
                }
                boolean fecha = estilo >= 0 && estilo < estiloEsFecha.size() && estiloEsFecha.get(estilo);
                if (fecha && numero >= 1) {
                    return LectorXls.FormatoFecha.aFecha(numero, sistema1904);
                }
                // Excel guarda 17 cifras (81.43000000000001) pero solo enseña 15: se deja como lo vería el usuario
                BigDecimal limpio = new BigDecimal(valor.trim(), new MathContext(15)).stripTrailingZeros();
                return limpio.scale() < 0 ? limpio.setScale(0) : limpio;
            }
        }
    }

    private static List<List<Object>> aFilas(Map<Integer, Map<Integer, Object>> celdas, int maxFila, int maxColumna) {
        List<List<Object>> filas = new ArrayList<>();
        for (int f = 0; f <= maxFila; f++) {
            Map<Integer, Object> fila = celdas.get(f);
            if (fila == null || fila.isEmpty()) {
                continue;
            }
            int ultima = -1;
            Set<Integer> usadas = new HashSet<>();
            for (Map.Entry<Integer, Object> e : fila.entrySet()) {
                if (!(e.getValue() instanceof String s) || !s.isBlank()) {
                    ultima = Math.max(ultima, e.getKey());
                    usadas.add(e.getKey());
                }
            }
            if (ultima < 0) {
                continue;
            }
            List<Object> celdasFila = new ArrayList<>(ultima + 1);
            for (int c = 0; c <= Math.min(ultima, maxColumna); c++) {
                celdasFila.add(usadas.contains(c) ? fila.get(c) : null);
            }
            filas.add(celdasFila);
        }
        return filas;
    }

    // ===== Referencias y XML =====

    /** De "C7" a la columna 2 (empezando en 0) */
    private static int columnaDe(String referencia) {
        int c = 0;
        int i = 0;
        while (i < referencia.length() && Character.isLetter(referencia.charAt(i))) {
            c = c * 26 + (Character.toUpperCase(referencia.charAt(i)) - 'A' + 1);
            i++;
        }
        return c - 1;
    }

    /** De "C7" a la fila 6 (empezando en 0) */
    private static int filaDe(String referencia) {
        int i = 0;
        while (i < referencia.length() && Character.isLetter(referencia.charAt(i))) {
            i++;
        }
        try {
            return Integer.parseInt(referencia.substring(i)) - 1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Abre una parte del zip como XML, con las entidades externas y los DTD desactivados (un XML malicioso no puede
     * leer archivos del equipo) y con un tope de tamaño.
     */
    private XMLStreamReader abrirXml(String ruta) throws IOException, XMLStreamException {
        ZipEntry entrada = zip.getEntry(ruta);
        if (entrada == null) {
            throw new IOException("Falta " + ruta);
        }
        InputStream flujo = new LimitedStream(zip.getInputStream(entrada), MAX_BYTES_PARTE);
        XMLInputFactory fabrica = XMLInputFactory.newFactory();
        fabrica.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        fabrica.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        return fabrica.createXMLStreamReader(flujo);
    }

    /** Flujo que falla si se leen más bytes de los permitidos */
    private static final class LimitedStream extends InputStream {
        private final InputStream origen;
        private long restante;

        LimitedStream(InputStream origen, long limite) {
            this.origen = origen;
            this.restante = limite;
        }

        @Override
        public int read() throws IOException {
            int b = origen.read();
            if (b >= 0 && --restante < 0) {
                throw new IOException("El archivo descomprimido es demasiado grande");
            }
            return b;
        }

        @Override
        public int read(byte[] destino, int desde, int cuantos) throws IOException {
            int n = origen.read(destino, desde, cuantos);
            if (n > 0) {
                restante -= n;
                if (restante < 0) {
                    throw new IOException("El archivo descomprimido es demasiado grande");
                }
            }
            return n;
        }

        @Override
        public void close() throws IOException {
            origen.close();
        }
    }
}
