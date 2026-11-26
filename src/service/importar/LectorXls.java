package service.importar;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lee un libro de Excel antiguo (.xls, el formato binario de Excel 97 a 2003) sin ninguna librería. Un .xls es un
 * "archivo dentro de un archivo" (un contenedor OLE2) y dentro lleva una secuencia de registros (BIFF8): los textos
 * compartidos, y una celda por registro (texto, número, fecha, fórmula...). Solo lee los datos de las celdas de la
 * primera hoja visible que tenga algo; ignora formatos, imágenes, combinaciones de celdas y todo lo demás.
 *
 * @author Claude
 */
final class LectorXls {

    private static final byte[] FIRMA_OLE2 = { (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1 };

    // Registros de BIFF que se leen
    private static final int BOF = 0x0809;
    private static final int EOF = 0x000A;
    private static final int BOUNDSHEET = 0x0085;
    private static final int SST = 0x00FC;
    private static final int CONTINUE = 0x003C;
    private static final int LABELSST = 0x00FD;
    private static final int LABEL = 0x0204;
    private static final int RSTRING = 0x00D6;
    private static final int NUMBER = 0x0203;
    private static final int RK = 0x027E;
    private static final int MULRK = 0x00BD;
    private static final int FORMULA = 0x0006;
    private static final int STRING = 0x0207;
    private static final int BOOLERR = 0x0205;
    private static final int XF = 0x00E0;
    private static final int FORMAT = 0x041E;
    private static final int DATEMODE = 0x0022;
    private static final int FILEPASS = 0x002F;

    // Límites para que un archivo mal hecho no llene la memoria
    private static final int MAX_FILAS = 200_000;
    private static final int MAX_COLUMNAS = 256;

    private final byte[] libro;
    private final List<String> textos = new ArrayList<>();
    // Formato de número de cada XF (índice de estilo de celda) y formatos propios del archivo
    private final List<Integer> formatoDeXf = new ArrayList<>();
    private final Map<Integer, String> formatosPropios = new HashMap<>();
    private boolean sistema1904 = false;

    private LectorXls(byte[] libro) {
        this.libro = libro;
    }

    /**
     * @param datos: contenido de un archivo
     * @return Devuelve true si empieza con la firma de un contenedor OLE2 (.xls, pero también .doc, .ppt...)
     */
    static boolean esOle2(byte[] datos) {
        if (datos.length < FIRMA_OLE2.length) {
            return false;
        }
        for (int i = 0; i < FIRMA_OLE2.length; i++) {
            if (datos[i] != FIRMA_OLE2[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Lee la primera hoja con datos de un archivo .xls
     * @param datos: contenido del archivo
     * @return Devuelve el nombre de la hoja y sus filas (las celdas son String, BigDecimal, LocalDate o null)
     * @throws ArchivoNoValido si no es un libro de Excel que se pueda leer
     */
    static Hoja leer(byte[] datos) throws ArchivoNoValido {
        try {
            byte[] flujo = new Contenedor(datos).abrir("Workbook", "Book");
            if (flujo == null) {
                throw new ArchivoNoValido("Es un archivo de Office, pero no una hoja de cálculo de Excel.");
            }
            return new LectorXls(flujo).leerLibro();
        } catch (ArchivoNoValido e) {
            throw e;
        } catch (RuntimeException e) {
            // Cualquier desajuste al recorrer los registros quiere decir que el archivo está dañado
            throw new ArchivoNoValido("No se ha podido leer el archivo de Excel: parece dañado.", e);
        }
    }

    /**
     * Una hoja leída.
     * @param nombre: nombre de la pestaña
     * @param filas: filas con datos
     */
    record Hoja(String nombre, List<List<Object>> filas) {
    }

    // ===== Libro =====

    private Hoja leerLibro() throws ArchivoNoValido {
        // 1) Parte global: hojas, textos compartidos, formatos de número
        record Pestana(String nombre, int posicion, boolean oculta, int tipo) {
        }
        List<Pestana> pestanas = new ArrayList<>();
        int pos = 0;
        boolean primeraBof = true;
        while (pos + 4 <= libro.length) {
            int id = u16(libro, pos);
            int largo = u16(libro, pos + 2);
            int datos = pos + 4;
            if (datos + largo > libro.length) {
                break;
            }
            if (id == BOF) {
                if (primeraBof) {
                    int version = u16(libro, datos);
                    if (version != 0x0600) {
                        throw new ArchivoNoValido("Este Excel es de una versión demasiado antigua. Guárdalo como .xlsx o como CSV y vuelve a importarlo.");
                    }
                    primeraBof = false;
                }
            } else if (id == FILEPASS) {
                throw new ArchivoNoValido("El archivo de Excel está protegido con contraseña. Quita la protección y vuelve a importarlo.");
            } else if (id == DATEMODE) {
                sistema1904 = u16(libro, datos) == 1;
            } else if (id == FORMAT) {
                int indice = u16(libro, datos);
                formatosPropios.put(indice, cadenaConLongitud16(libro, datos + 2, largo - 2));
            } else if (id == XF) {
                formatoDeXf.add(u16(libro, datos + 2));
            } else if (id == BOUNDSHEET) {
                int posicion = (int) u32(libro, datos);
                int estado = libro[datos + 4] & 0x03;
                int tipo = libro[datos + 5] & 0xFF;
                int cch = libro[datos + 6] & 0xFF;
                boolean utf16 = (libro[datos + 7] & 0x01) != 0;
                String nombre = utf16 ? new String(libro, datos + 8, cch * 2, StandardCharsets.UTF_16LE)
                        : new String(libro, datos + 8, cch, StandardCharsets.ISO_8859_1);
                pestanas.add(new Pestana(nombre, posicion, estado != 0, tipo));
            } else if (id == SST) {
                // Los textos compartidos pueden continuar en registros CONTINUE que siguen
                int fin = datos + largo;
                List<int[]> trozos = new ArrayList<>();
                trozos.add(new int[] { datos, fin });
                int siguiente = fin;
                while (siguiente + 4 <= libro.length && u16(libro, siguiente) == CONTINUE) {
                    int largoContinuacion = u16(libro, siguiente + 2);
                    trozos.add(new int[] { siguiente + 4, siguiente + 4 + largoContinuacion });
                    siguiente += 4 + largoContinuacion;
                }
                leerTextosCompartidos(trozos);
                pos = siguiente;
                continue;
            } else if (id == EOF) {
                break;
            }
            pos = datos + largo;
        }
        if (primeraBof) {
            throw new ArchivoNoValido("No se ha podido leer el archivo de Excel: no tiene el contenido esperado.");
        }

        // 2) La primera hoja visible (de datos, no un gráfico) que tenga algo
        for (Pestana p : pestanas) {
            if (p.oculta() || p.tipo() != 0) {
                continue;
            }
            List<List<Object>> filas = leerHoja(p.posicion());
            if (!filas.isEmpty()) {
                return new Hoja(p.nombre(), filas);
            }
        }
        throw new ArchivoNoValido("El archivo de Excel no tiene ningún dato.");
    }

    // ===== Textos compartidos =====

    /**
     * Lee la tabla de textos compartidos (SST). Es la parte delicada del formato: un texto puede cortarse en medio al
     * llegar al final de un registro y seguir en el CONTINUE siguiente, que entonces empieza con un byte que dice si
     * los caracteres siguen en 8 o en 16 bits.
     */
    private void leerTextosCompartidos(List<int[]> trozos) {
        Cursor c = new Cursor(libro, trozos);
        c.saltar(4);
        long total = c.u32();
        for (long i = 0; i < total && c.quedan(); i++) {
            int cch = c.u16();
            int opciones = c.u8();
            boolean utf16 = (opciones & 0x01) != 0;
            boolean extension = (opciones & 0x04) != 0;
            boolean formato = (opciones & 0x08) != 0;
            int partes = formato ? c.u16() : 0;
            long largoExtension = extension ? c.u32() : 0;

            StringBuilder texto = new StringBuilder(cch);
            int restantes = cch;
            while (restantes > 0) {
                if (c.finDeTrozo()) {
                    // Continúa en el CONTINUE siguiente, que empieza con el byte de opciones de los caracteres
                    c.siguienteTrozo();
                    utf16 = (c.u8() & 0x01) != 0;
                }
                int disponibles = c.bytesEnTrozo() / (utf16 ? 2 : 1);
                int leer = Math.min(restantes, disponibles);
                if (leer == 0) {
                    break;
                }
                texto.append(c.caracteres(leer, utf16));
                restantes -= leer;
            }
            c.saltar(4L * partes + largoExtension);
            textos.add(texto.toString());
        }
    }

    // ===== Hoja =====

    private List<List<Object>> leerHoja(int posicionBof) {
        Map<Integer, Map<Integer, Object>> celdas = new HashMap<>();
        int maxFila = -1;
        int maxColumna = -1;
        int[] ultimaFormula = null;
        int pos = posicionBof;
        boolean dentro = false;
        while (pos + 4 <= libro.length) {
            int id = u16(libro, pos);
            int largo = u16(libro, pos + 2);
            int d = pos + 4;
            if (d + largo > libro.length) {
                break;
            }
            if (id == BOF) {
                if (dentro) {
                    // Empieza otra hoja o un subregistro (un gráfico incrustado): se salta hasta su EOF
                    pos = saltarSubdocumento(pos);
                    continue;
                }
                dentro = true;
            } else if (id == EOF) {
                break;
            } else if (dentro) {
                Object valor = null;
                int fila = -1;
                int col = -1;
                switch (id) {
                    case LABELSST -> {
                        fila = u16(libro, d);
                        col = u16(libro, d + 2);
                        long indice = u32(libro, d + 6);
                        valor = indice < textos.size() ? textos.get((int) indice) : null;
                    }
                    case LABEL, RSTRING -> {
                        fila = u16(libro, d);
                        col = u16(libro, d + 2);
                        valor = cadenaConLongitud16(libro, d + 6, largo - 6);
                    }
                    case NUMBER -> {
                        fila = u16(libro, d);
                        col = u16(libro, d + 2);
                        valor = numero(u16(libro, d + 4), Double.longBitsToDouble(u64(libro, d + 6)));
                    }
                    case RK -> {
                        fila = u16(libro, d);
                        col = u16(libro, d + 2);
                        valor = numero(u16(libro, d + 4), valorRk(u32(libro, d + 6)));
                    }
                    case MULRK -> {
                        fila = u16(libro, d);
                        int primera = u16(libro, d + 2);
                        int ultima = u16(libro, d + largo - 2);
                        for (int c = primera; c <= ultima; c++) {
                            int p = d + 4 + (c - primera) * 6;
                            if (p + 6 > d + largo - 2) {
                                break;
                            }
                            guardar(celdas, fila, c, numero(u16(libro, p), valorRk(u32(libro, p + 2))));
                            maxFila = Math.max(maxFila, fila);
                            maxColumna = Math.max(maxColumna, c);
                        }
                    }
                    case FORMULA -> {
                        fila = u16(libro, d);
                        col = u16(libro, d + 2);
                        if (u16(libro, d + 12) == 0xFFFF) {
                            // El resultado no es un número: 0 texto (viene en el registro STRING siguiente), 1 lógico, 2 error, 3 vacío
                            int tipo = libro[d + 6] & 0xFF;
                            if (tipo == 0) {
                                ultimaFormula = new int[] { fila, col };
                            } else if (tipo == 1) {
                                valor = (libro[d + 8] & 0xFF) != 0 ? "VERDADERO" : "FALSO";
                            }
                        } else {
                            valor = numero(u16(libro, d + 4), Double.longBitsToDouble(u64(libro, d + 6)));
                        }
                    }
                    case STRING -> {
                        if (ultimaFormula != null) {
                            fila = ultimaFormula[0];
                            col = ultimaFormula[1];
                            valor = cadenaConLongitud16(libro, d, largo);
                            ultimaFormula = null;
                        }
                    }
                    case BOOLERR -> {
                        fila = u16(libro, d);
                        col = u16(libro, d + 2);
                        if ((libro[d + 7] & 0xFF) == 0) {
                            valor = (libro[d + 6] & 0xFF) != 0 ? "VERDADERO" : "FALSO";
                        }
                    }
                    default -> { }
                }
                if (valor != null && fila >= 0 && col >= 0 && fila < MAX_FILAS && col < MAX_COLUMNAS) {
                    guardar(celdas, fila, col, valor);
                    maxFila = Math.max(maxFila, fila);
                    maxColumna = Math.max(maxColumna, col);
                }
            }
            pos = d + largo;
        }
        return aFilas(celdas, maxFila, maxColumna);
    }

    /**
     * Salta un BOF que aparece dentro de una hoja (un gráfico incrustado, por ejemplo) hasta su EOF
     * @param posicion: posición del BOF
     * @return Devuelve la posición del registro siguiente al EOF
     */
    private int saltarSubdocumento(int posicion) {
        int nivel = 0;
        int pos = posicion;
        while (pos + 4 <= libro.length) {
            int id = u16(libro, pos);
            int largo = u16(libro, pos + 2);
            if (id == BOF) {
                nivel++;
            } else if (id == EOF) {
                nivel--;
                if (nivel == 0) {
                    return pos + 4 + largo;
                }
            }
            pos += 4 + largo;
        }
        return libro.length;
    }

    private static void guardar(Map<Integer, Map<Integer, Object>> celdas, int fila, int columna, Object valor) {
        celdas.computeIfAbsent(fila, f -> new HashMap<>()).put(columna, valor);
    }

    /**
     * Pasa las celdas leídas a una lista de filas: sin filas vacías y cada fila hasta su última celda con algo
     */
    private static List<List<Object>> aFilas(Map<Integer, Map<Integer, Object>> celdas, int maxFila, int maxColumna) {
        List<List<Object>> filas = new ArrayList<>();
        for (int f = 0; f <= maxFila; f++) {
            Map<Integer, Object> fila = celdas.get(f);
            if (fila == null || fila.isEmpty()) {
                continue;
            }
            int ultima = -1;
            for (int c : fila.keySet()) {
                if (!(fila.get(c) instanceof String s) || !s.isBlank()) {
                    ultima = Math.max(ultima, c);
                }
            }
            if (ultima < 0) {
                continue;
            }
            List<Object> celdasFila = new ArrayList<>(ultima + 1);
            for (int c = 0; c <= Math.min(ultima, maxColumna); c++) {
                Object v = fila.get(c);
                celdasFila.add(v instanceof String s ? (s.isBlank() ? null : s.trim()) : v);
            }
            filas.add(celdasFila);
        }
        return filas;
    }

    // ===== Números y fechas =====

    /**
     * Convierte un número de celda en lo que representa: una fecha si el formato de la celda es de fecha y, si no, un número
     * @param xf: índice del estilo de la celda
     * @param valor: valor guardado
     */
    private Object numero(int xf, double valor) {
        if (Double.isNaN(valor) || Double.isInfinite(valor)) {
            return null;
        }
        int formato = xf < formatoDeXf.size() ? formatoDeXf.get(xf) : 0;
        if (FormatoFecha.esFecha(formato, formatosPropios.get(formato)) && valor >= 1) {
            return FormatoFecha.aFecha(valor, sistema1904);
        }
        return decimal(valor);
    }

    /**
     * @param valor: número leído
     * @return Devuelve el número como lo vería el usuario en Excel (15 cifras significativas, sin restos de coma flotante)
     */
    private static BigDecimal decimal(double valor) {
        BigDecimal d = new BigDecimal(valor, new java.math.MathContext(15));
        BigDecimal limpio = d.stripTrailingZeros();
        return limpio.scale() < 0 ? limpio.setScale(0) : limpio;
    }

    /**
     * Decodifica un número RK, la forma comprimida con la que Excel guarda los números sencillos: 30 bits de valor y
     * dos de aviso (el bit 0: dividir entre 100; el bit 1: es un entero y no el comienzo de un decimal)
     */
    private static double valorRk(long rk) {
        double valor;
        if ((rk & 0x02) != 0) {
            valor = (int) rk >> 2;
        } else {
            valor = Double.longBitsToDouble((rk & 0xFFFFFFFCL) << 32);
        }
        return (rk & 0x01) != 0 ? valor / 100 : valor;
    }

    // ===== Lectura de bytes =====

    private static int u16(byte[] b, int p) {
        return (b[p] & 0xFF) | ((b[p + 1] & 0xFF) << 8);
    }

    private static long u32(byte[] b, int p) {
        return (b[p] & 0xFFL) | ((b[p + 1] & 0xFFL) << 8) | ((b[p + 2] & 0xFFL) << 16) | ((b[p + 3] & 0xFFL) << 24);
    }

    private static long u64(byte[] b, int p) {
        return u32(b, p) | (u32(b, p + 4) << 32);
    }

    /**
     * Lee un texto que empieza con su longitud en 2 bytes, el byte de opciones y los caracteres (formato BIFF8 de LABEL,
     * STRING y FORMAT). Si el texto es más largo de lo que hay, se queda con lo que haya.
     */
    private static String cadenaConLongitud16(byte[] b, int p, int disponibles) {
        if (disponibles < 3) {
            return "";
        }
        int cch = u16(b, p);
        int opciones = b[p + 2] & 0xFF;
        boolean utf16 = (opciones & 0x01) != 0;
        boolean formato = (opciones & 0x08) != 0;
        boolean extension = (opciones & 0x04) != 0;
        int inicio = p + 3 + (formato ? 2 : 0) + (extension ? 4 : 0);
        int bytes = Math.min(cch * (utf16 ? 2 : 1), Math.max(0, p + disponibles - inicio));
        return new String(b, inicio, bytes, utf16 ? StandardCharsets.UTF_16LE : StandardCharsets.ISO_8859_1);
    }

    // ===== Cursor sobre los trozos del SST =====

    /**
     * Lee los datos de un registro que continúa en otros (SST + CONTINUE) como si fueran una sola secuencia, sabiendo
     * dónde acaba cada trozo.
     */
    private static final class Cursor {
        private final byte[] b;
        private final List<int[]> trozos;
        private int trozo = 0;
        private int pos;

        Cursor(byte[] b, List<int[]> trozos) {
            this.b = b;
            this.trozos = trozos;
            this.pos = trozos.get(0)[0];
        }

        boolean quedan() {
            return trozo < trozos.size() - 1 || pos < trozos.get(trozo)[1];
        }

        boolean finDeTrozo() {
            return pos >= trozos.get(trozo)[1];
        }

        int bytesEnTrozo() {
            return trozos.get(trozo)[1] - pos;
        }

        void siguienteTrozo() {
            trozo++;
            pos = trozos.get(trozo)[0];
        }

        int u8() {
            avanzarSiHaceFalta();
            return b[pos++] & 0xFF;
        }

        int u16() {
            return u8() | (u8() << 8);
        }

        long u32() {
            return (u16() & 0xFFFFL) | ((long) u16() << 16);
        }

        /** Pasa al trozo siguiente si ya no queda nada en este (las cabeceras de un texto no se parten, pero el texto siguiente puede empezar en un trozo nuevo) */
        private void avanzarSiHaceFalta() {
            while (finDeTrozo() && trozo < trozos.size() - 1) {
                siguienteTrozo();
            }
        }

        void saltar(long n) {
            long resto = n;
            while (resto > 0) {
                avanzarSiHaceFalta();
                int enEste = (int) Math.min(bytesEnTrozo(), resto);
                if (enEste <= 0) {
                    break;
                }
                pos += enEste;
                resto -= enEste;
            }
        }

        String caracteres(int cantidad, boolean utf16) {
            int bytes = cantidad * (utf16 ? 2 : 1);
            String texto = new String(b, pos, bytes, utf16 ? StandardCharsets.UTF_16LE : StandardCharsets.ISO_8859_1);
            pos += bytes;
            return texto;
        }
    }

    // ===== Formatos de fecha =====

    /**
     * Averigua si el formato de número de una celda es de fecha y convierte los números de Excel en fechas.
     */
    static final class FormatoFecha {

        private FormatoFecha() {
        }

        /**
         * @param indice: número de formato de la celda
         * @param propio: texto del formato si es uno definido en el archivo (null si es de los de serie)
         * @return Devuelve true si el formato muestra una fecha (con o sin hora)
         */
        static boolean esFecha(int indice, String propio) {
            if ((indice >= 14 && indice <= 22) || (indice >= 27 && indice <= 36) || (indice >= 45 && indice <= 47)
                    || (indice >= 50 && indice <= 58)) {
                return true;
            }
            return propio != null && esFechaElTexto(propio);
        }

        /**
         * Mira si un formato de número escrito (como yyyy-mm-dd o [$-409]d/m/yy h:mm) lleva partes de fecha. Se quitan antes
         * los textos entre comillas, las barras invertidas y los colores y condiciones entre corchetes.
         */
        static boolean esFechaElTexto(String formato) {
            String f = formato.replaceAll("\"[^\"]*\"", "").replaceAll("\\\\.", "").replaceAll("\\[[^\\]]*\\]", "")
                    .replaceAll("_.", "").replaceAll("\\*.", "").toLowerCase();
            if (f.equals("general") || f.isEmpty()) {
                return false;
            }
            return f.matches(".*[dmyhs].*") && !f.matches(".*e[+-]0.*");
        }

        /**
         * @param serie: número de días desde el origen de Excel
         * @param sistema1904: true si el libro usa el sistema de fechas de 1904 (el de los Excel antiguos de Mac)
         * @return Devuelve la fecha (la hora, si la tiene, se descarta)
         */
        static LocalDate aFecha(double serie, boolean sistema1904) {
            long dias = (long) Math.floor(serie);
            if (sistema1904) {
                return LocalDate.of(1904, 1, 1).plusDays(dias);
            }
            // Excel cree que 1900 fue bisiesto: desde el 1 de marzo de 1900 (día 61) el origen efectivo es el 30 de diciembre de 1899
            return dias < 61 ? LocalDate.of(1899, 12, 31).plusDays(dias - 1) : LocalDate.of(1899, 12, 30).plusDays(dias);
        }
    }

    // ===== Contenedor OLE2 =====

    /**
     * Abre un contenedor OLE2 (el "archivo dentro de un archivo" de Office) y saca de él un flujo por su nombre.
     */
    private static final class Contenedor {
        private final byte[] datos;
        private final int tamanoSector;
        private final int tamanoMini;
        private final int corteMini;
        private int[] fat;
        private int[] miniFat;
        private byte[] miniFlujo;
        private final List<byte[]> entradas = new ArrayList<>();

        Contenedor(byte[] datos) throws ArchivoNoValido {
            this.datos = datos;
            if (datos.length < 512 || !esOle2(datos)) {
                throw new ArchivoNoValido("El archivo no es una hoja de cálculo de Excel válida.");
            }
            int desplazamiento = u16(datos, 0x1E);
            int desplazamientoMini = u16(datos, 0x20);
            if (desplazamiento < 7 || desplazamiento > 16 || desplazamientoMini > desplazamiento) {
                throw new ArchivoNoValido("El archivo de Excel está dañado.");
            }
            tamanoSector = 1 << desplazamiento;
            tamanoMini = 1 << desplazamientoMini;
            corteMini = (int) u32(datos, 0x38);
            leerFat();
            leerDirectorio();
        }

        /** Posición en el archivo de un sector (el sector 0 va justo después de la cabecera de 512 bytes, o de un sector si es mayor) */
        private int posicion(int sector) {
            long p = (long) (sector + 1) * tamanoSector;
            if (p < 0 || p >= datos.length) {
                throw new IllegalStateException("sector fuera del archivo");
            }
            return (int) p;
        }

        private void leerFat() {
            int numeroSectoresFat = (int) u32(datos, 0x2C);
            List<Integer> sectoresFat = new ArrayList<>();
            for (int i = 0; i < 109 && sectoresFat.size() < numeroSectoresFat; i++) {
                int s = (int) u32(datos, 0x4C + 4 * i);
                if (s >= 0) {
                    sectoresFat.add(s);
                }
            }
            int sectorDifat = (int) u32(datos, 0x44);
            int numeroDifat = (int) u32(datos, 0x48);
            for (int n = 0; n < numeroDifat && sectorDifat >= 0 && sectoresFat.size() < numeroSectoresFat; n++) {
                int p = posicion(sectorDifat);
                int porSector = tamanoSector / 4 - 1;
                for (int i = 0; i < porSector && sectoresFat.size() < numeroSectoresFat; i++) {
                    int s = (int) u32(datos, p + 4 * i);
                    if (s >= 0) {
                        sectoresFat.add(s);
                    }
                }
                sectorDifat = (int) u32(datos, p + 4 * porSector);
            }
            int porSector = tamanoSector / 4;
            fat = new int[sectoresFat.size() * porSector];
            for (int i = 0; i < sectoresFat.size(); i++) {
                int p = posicion(sectoresFat.get(i));
                for (int j = 0; j < porSector; j++) {
                    fat[i * porSector + j] = (int) u32(datos, p + 4 * j);
                }
            }
        }

        /** Lee todos los sectores de una cadena de la FAT */
        private byte[] cadena(int inicio, long tamano) {
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            int sector = inicio;
            int vueltas = 0;
            while (sector >= 0 && sector < fat.length && vueltas++ <= fat.length) {
                int p = posicion(sector);
                salida.write(datos, p, Math.min(tamanoSector, datos.length - p));
                sector = fat[sector];
            }
            byte[] todo = salida.toByteArray();
            return tamano >= 0 && tamano < todo.length ? java.util.Arrays.copyOf(todo, (int) tamano) : todo;
        }

        private void leerDirectorio() {
            byte[] directorio = cadena((int) u32(datos, 0x30), -1);
            for (int p = 0; p + 128 <= directorio.length; p += 128) {
                entradas.add(java.util.Arrays.copyOfRange(directorio, p, p + 128));
            }
            // Mini FAT y mini flujo, donde se guardan los flujos pequeños (menos de 4096 bytes)
            int inicioMiniFat = (int) u32(datos, 0x3C);
            if (inicioMiniFat >= 0) {
                byte[] bytesMiniFat = cadena(inicioMiniFat, -1);
                miniFat = new int[bytesMiniFat.length / 4];
                for (int i = 0; i < miniFat.length; i++) {
                    miniFat[i] = (int) u32(bytesMiniFat, 4 * i);
                }
            } else {
                miniFat = new int[0];
            }
            if (!entradas.isEmpty()) {
                byte[] raiz = entradas.get(0);
                miniFlujo = cadena((int) u32(raiz, 116), u32(raiz, 120));
            } else {
                miniFlujo = new byte[0];
            }
        }

        /**
         * @param nombres: nombres posibles del flujo (Workbook en Excel 97 o más nuevo, Book en el 5)
         * @return Devuelve el contenido del flujo, o null si no existe
         */
        byte[] abrir(String... nombres) {
            for (byte[] e : entradas) {
                int tipo = e[66] & 0xFF;
                int largoNombre = Math.max(0, u16(e, 64) - 2);
                if (tipo != 2 || largoNombre > 64) {
                    continue;
                }
                String nombre = new String(e, 0, largoNombre, StandardCharsets.UTF_16LE);
                for (String buscado : nombres) {
                    if (nombre.equals(buscado)) {
                        int inicio = (int) u32(e, 116);
                        long tamano = u32(e, 120);
                        return tamano < corteMini ? flujoMini(inicio, tamano) : cadena(inicio, tamano);
                    }
                }
            }
            return null;
        }

        private byte[] flujoMini(int inicio, long tamano) {
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            int sector = inicio;
            int vueltas = 0;
            while (sector >= 0 && sector < miniFat.length && vueltas++ <= miniFat.length) {
                int p = sector * tamanoMini;
                if (p + tamanoMini > miniFlujo.length) {
                    break;
                }
                salida.write(miniFlujo, p, tamanoMini);
                sector = miniFat[sector];
            }
            byte[] todo = salida.toByteArray();
            return tamano < todo.length ? java.util.Arrays.copyOf(todo, (int) tamano) : todo;
        }
    }
}
