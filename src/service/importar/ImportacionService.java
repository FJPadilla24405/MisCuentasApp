package service.importar;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import models.Categoria;
import models.Cuenta;
import models.Movimiento;
import models.Usuario;
import service.CategoriaService;
import service.DatabaseManager;
import service.dao.CuentaDAO;
import service.dao.MovimientoDAO;
import util.Dinero;

/**
 * Convierte un archivo ya leído en movimientos: averigua dónde están los títulos y qué columna es la fecha, el
 * concepto, el importe y la categoría; interpreta cada fila con el formato de fecha, de decimales y de signo
 * elegidos; relaciona las categorías del archivo con las de la aplicación; avisa de lo que ya existe; y, al final,
 * guarda en una sola transacción las filas marcadas (si algo falla, no se guarda ninguna).
 *
 * @author Claude
 */
public class ImportacionService {

    /** Formato de las fechas escritas como texto. Las fechas de un Excel son fechas de verdad y no dependen de esto. */
    public enum FormatoFecha {
        AUTO("Automático"),
        DIA_MES_ANIO("DD/MM/AAAA"),
        MES_DIA_ANIO("MM/DD/AAAA"),
        ANIO_MES_DIA("AAAA-MM-DD");

        private final String etiqueta;

        FormatoFecha(String etiqueta) {
            this.etiqueta = etiqueta;
        }

        /**
         * @return Devuelve el nombre que se ve en la aplicación
         */
        public String getEtiqueta() {
            return etiqueta;
        }
    }

    /** Separador de decimales de los importes escritos como texto. */
    public enum SeparadorDecimal {
        AUTO("Automático", null),
        COMA("Coma (1.234,56)", "coma (,)"),
        PUNTO("Punto (1,234.56)", "punto (.)");

        private final String etiqueta;
        private final String corta;

        SeparadorDecimal(String etiqueta, String corta) {
            this.etiqueta = etiqueta;
            this.corta = corta;
        }

        /**
         * @return Devuelve el nombre que se ve en la lista de opciones
         */
        public String getEtiqueta() {
            return etiqueta;
        }

        /**
         * @return Devuelve el nombre corto que se ve en la línea de resumen del formato (coma (,)), o null en Automático
         */
        public String getCorta() {
            return corta;
        }
    }

    /** Qué signo tienen los gastos en el archivo. */
    public enum SignoGasto {
        NEGATIVO("Los importes negativos son gastos", "Importe negativo = gasto"),
        POSITIVO("Los importes positivos son gastos", "Importe positivo = gasto");

        private final String etiqueta;
        private final String corta;

        SignoGasto(String etiqueta, String corta) {
            this.etiqueta = etiqueta;
            this.corta = corta;
        }

        /**
         * @return Devuelve el nombre que se ve en la lista de opciones
         */
        public String getEtiqueta() {
            return etiqueta;
        }

        /**
         * @return Devuelve el nombre corto que se ve en la línea de resumen del formato
         */
        public String getCorta() {
            return corta;
        }
    }

    /**
     * Formato elegido para leer las fechas, los importes y el signo de los gastos.
     * @param fecha: formato de las fechas escritas como texto
     * @param decimal: separador de decimales
     * @param signo: signo de los gastos
     */
    public record Formato(FormatoFecha fecha, SeparadorDecimal decimal, SignoGasto signo) {

        /**
         * @return Devuelve el formato con el que se empieza: todo automático y los gastos en negativo
         */
        public static Formato inicial() {
            return new Formato(FormatoFecha.AUTO, SeparadorDecimal.AUTO, SignoGasto.NEGATIVO);
        }
    }

    /**
     * Qué columna del archivo es cada dato. Las posiciones empiezan en 0 y -1 quiere decir sin asignar.
     * @param fecha: columna de la fecha
     * @param concepto: columna del concepto
     * @param importe: columna del importe
     * @param categoria: columna de la categoría (opcional)
     * @param subcategoria: columna de la subcategoría (opcional)
     * @param nota: columna de la nota (opcional)
     * @param saldo: columna del saldo de la cuenta después de cada movimiento (opcional)
     */
    public record Columnas(int fecha, int concepto, int importe, int categoria, int subcategoria, int nota, int saldo) {

        /**
         * @return Devuelve true si están asignadas las tres columnas obligatorias (fecha, concepto e importe)
         */
        public boolean completas() {
            return fecha >= 0 && concepto >= 0 && importe >= 0;
        }
    }

    /**
     * Lo que ha quedado tras importar.
     * @param importados: movimientos creados
     * @param categoriasNuevas: categorías creadas por no existir
     * @param ultimoMes: mes del movimiento más reciente de los importados
     * @param saldoAjustado: true si se ha ajustado el saldo de la cuenta al del archivo
     */
    public record Resultado(int importados, int categoriasNuevas, YearMonth ultimoMes, boolean saldoAjustado) {
    }

    /**
     * El saldo que figura en el archivo: el que tenía la cuenta después de su movimiento más reciente.
     * @param saldo: saldo de la cuenta
     * @param fecha: fecha de ese movimiento (el saldo es el de la cuenta al terminar ese día)
     */
    public record SaldoArchivo(BigDecimal saldo, LocalDate fecha) {
    }

    // Tope de movimientos por archivo
    public static final int MAX_FILAS = 2000;
    private static final int MAX_CONCEPTO = 100;
    private static final int MAX_NOMBRE_CATEGORIA = 50;
    private static final int MAX_NOTA = 255;
    private static final int FILAS_BUSCANDO_TITULOS = 40;

    private static final Pattern FECHA = Pattern.compile("^(\\d{1,4})[/\\-. ](\\d{1,2})[/\\-. ](\\d{1,4})(?:[T\\s,].*)?$");
    private static final Pattern FECHA_COMPACTA = Pattern.compile("^(\\d{4})(\\d{2})(\\d{2})$");
    private static final Pattern NUMERO_COMA = Pattern.compile(".*\\d,\\d{1,2}$");
    private static final Pattern NUMERO_PUNTO = Pattern.compile(".*\\d\\.\\d{1,2}$");

    private final CategoriaService categoriaService = new CategoriaService();
    private final MovimientoDAO movimientoDAO = new MovimientoDAO();
    private final CuentaDAO cuentaDAO = new CuentaDAO();

    // ===== Títulos y columnas =====

    /**
     * Busca la fila de títulos: la que está justo encima de la primera fila de datos (una fila de datos es la que tiene una
     * fecha y un número). Los extractos de algunos bancos llevan encima unas filas con el titular, la cuenta o la fecha de descarga.
     * @param archivo: archivo leído
     * @return Devuelve la posición de la fila de títulos (empezando en 0), o -1 si el archivo no tiene títulos
     */
    public int detectarFilaTitulos(ArchivoImportado archivo) {
        List<List<Object>> filas = archivo.filas();
        int limite = Math.min(filas.size(), FILAS_BUSCANDO_TITULOS);
        int primera = -1;
        for (int i = 0; i < limite; i++) {
            boolean siguienteTambien = i + 1 >= filas.size() || esFilaDeDatos(filas.get(i + 1));
            if (esFilaDeDatos(filas.get(i)) && siguienteTambien) {
                primera = i;
                break;
            }
        }
        if (primera <= 0) {
            return -1;
        }
        for (int i = primera - 1; i >= Math.max(0, primera - 6); i--) {
            if (celdasDeTexto(filas.get(i)) >= 2 && !esFilaDeDatos(filas.get(i))) {
                return i;
            }
        }
        return celdasDeTexto(filas.get(primera - 1)) >= 1 ? primera - 1 : -1;
    }

    /**
     * @param fila: fila del archivo
     * @return Devuelve true si parece un movimiento: al menos tres celdas con algo, una fecha y un número
     */
    private boolean esFilaDeDatos(List<Object> fila) {
        int llenas = 0;
        boolean fecha = false;
        boolean numero = false;
        for (Object c : fila) {
            if (c == null) {
                continue;
            }
            llenas++;
            if (c instanceof LocalDate || (c instanceof String s && pareceFecha(s))) {
                fecha = true;
            } else if (c instanceof BigDecimal || (c instanceof String s && pareceNumero(s))) {
                numero = true;
            }
        }
        return llenas >= 3 && fecha && numero;
    }

    private static int celdasDeTexto(List<Object> fila) {
        int n = 0;
        for (Object c : fila) {
            if (c instanceof String s && !s.isBlank()) {
                n++;
            }
        }
        return n;
    }

    private static boolean pareceFecha(String texto) {
        String t = texto.trim();
        return FECHA.matcher(t).matches() || FECHA_COMPACTA.matcher(t).matches();
    }

    private static boolean pareceNumero(String texto) {
        return importeDeTexto(texto, SeparadorDecimal.AUTO) != null;
    }

    /**
     * Nombre de cada columna para las listas de elección: el título de la columna o, si no tiene, "Columna C"
     * @param archivo: archivo leído
     * @param filaTitulos: posición de la fila de títulos, o -1 si no hay
     * @return Devuelve un nombre por cada columna del archivo
     */
    public List<String> nombresColumnas(ArchivoImportado archivo, int filaTitulos) {
        List<String> nombres = new ArrayList<>();
        List<Object> titulos = filaTitulos >= 0 ? archivo.filas().get(filaTitulos) : List.of();
        for (int c = 0; c < archivo.numColumnas(); c++) {
            Object t = c < titulos.size() ? titulos.get(c) : null;
            String texto = t == null ? "" : t.toString().trim();
            nombres.add(texto.isEmpty() ? "Columna " + letraDeColumna(c) : texto);
        }
        return nombres;
    }

    private static String letraDeColumna(int columna) {
        StringBuilder letras = new StringBuilder();
        int c = columna;
        do {
            letras.insert(0, (char) ('A' + c % 26));
            c = c / 26 - 1;
        } while (c >= 0);
        return letras.toString();
    }

    /**
     * Propone qué columna es cada dato, mirando primero el título de la columna (fecha, concepto, descripción, importe,
     * categoría...) y, si no basta, lo que hay dentro: la columna con más fechas, la que tiene más números y la de
     * textos más largos.
     * @param archivo: archivo leído
     * @param filaTitulos: posición de la fila de títulos, o -1 si no hay
     * @return Devuelve las columnas propuestas (-1 en las que no se han encontrado)
     */
    public Columnas sugerirColumnas(ArchivoImportado archivo, int filaTitulos) {
        int n = archivo.numColumnas();
        List<Object> titulos = filaTitulos >= 0 ? archivo.filas().get(filaTitulos) : List.of();
        String[] claves = new String[n];
        for (int c = 0; c < n; c++) {
            claves[c] = c < titulos.size() && titulos.get(c) != null ? normalizar(titulos.get(c).toString()) : "";
        }
        boolean[] usada = new boolean[n];
        int categoria = mejor(claves, usada, ImportacionService::puntosCategoria);
        int subcategoria = mejor(claves, usada, ImportacionService::puntosSubcategoria);
        int fecha = mejor(claves, usada, ImportacionService::puntosFecha);
        int importe = mejor(claves, usada, ImportacionService::puntosImporte);
        int concepto = mejor(claves, usada, ImportacionService::puntosConcepto);
        // La nota se busca la última: "observaciones" puede ser el concepto si no hay otra columna que lo sea
        int nota = mejor(claves, usada, ImportacionService::puntosNota);
        int saldo = mejor(claves, usada, ImportacionService::puntosSaldo);

        // Lo que no se encuentra por el título, se busca por el contenido
        int desde = filaTitulos + 1;
        if (fecha < 0) {
            fecha = porContenido(archivo, desde, usada, c -> proporcion(archivo, desde, c, ImportacionService::esFecha));
        }
        if (importe < 0) {
            importe = porContenido(archivo, desde, usada, c -> claves[c].contains("saldo") ? 0 : proporcion(archivo, desde, c, ImportacionService::esNumero));
        }
        if (concepto < 0) {
            concepto = porContenido(archivo, desde, usada, c -> longitudMediaDeTexto(archivo, desde, c));
        }
        return new Columnas(fecha, concepto, importe, categoria, subcategoria, nota, saldo);
    }

    // Cada una puntúa un título de columna ya normalizado: más puntos, mejor candidato (0 = no sirve)
    private static int puntosFecha(String t) {
        if (t.contains("operacion") && (t.contains("fecha") || t.startsWith("f "))) {
            return 100;
        }
        if (t.contains("contable")) {
            return 80;
        }
        if (t.equals("fecha") || t.startsWith("fecha ")) {
            return 70;
        }
        if (t.contains("date")) {
            return 60;
        }
        if (t.contains("fecha")) {
            return 55;
        }
        return t.startsWith("f ") && t.contains("valor") ? 50 : 0;
    }

    private static int puntosConcepto(String t) {
        if (t.contains("concepto")) {
            return 100;
        }
        if (t.contains("descripcion") || t.contains("description")) {
            return 90;
        }
        if (t.contains("detalle")) {
            return 80;
        }
        if (t.contains("movimiento")) {
            return 70;
        }
        if (t.contains("beneficiario") || t.contains("memo") || t.contains("payee")) {
            return 55;
        }
        if (t.contains("observaciones") || t.equals("texto")) {
            return 45;
        }
        return t.contains("referencia") ? 40 : 0;
    }

    private static int puntosSaldo(String t) {
        if (t.contains("saldo") || t.contains("balance")) {
            return t.contains("disponible") || t.contains("contable") || t.contains("final") || t.startsWith("saldo") ? 100 : 80;
        }
        return 0;
    }

    private static int puntosSubcategoria(String t) {
        return t.startsWith("sub") && (t.contains("categoria") || t.contains("category")) ? 100 : 0;
    }

    private static int puntosNota(String t) {
        if (t.contains("comentario") || t.equals("nota") || t.equals("notas") || t.contains("notes")) {
            return 100;
        }
        if (t.contains("observaciones") || t.contains("observacion")) {
            return 80;
        }
        return t.contains("mensaje") || t.contains("aclaracion") ? 50 : 0;
    }

    private static int puntosImporte(String t) {
        if (t.contains("saldo") || t.contains("balance") || t.contains("fecha")) {
            return 0;
        }
        if (t.contains("importe")) {
            return 100;
        }
        if (t.contains("amount") || t.contains("monto")) {
            return 90;
        }
        if (t.contains("cantidad")) {
            return 80;
        }
        return t.equals("valor") || t.startsWith("valor ") ? 40 : 0;
    }

    private static int puntosCategoria(String t) {
        if (t.startsWith("sub")) {
            return 0;
        }
        return t.startsWith("categoria") || t.startsWith("category") || t.equals("rubro") ? 100 : 0;
    }

    private interface Puntuador {
        int puntos(String titulo);
    }

    /**
     * Elige la columna con más puntos que no esté ya usada, y la marca como usada
     */
    private static int mejor(String[] claves, boolean[] usada, Puntuador puntuador) {
        int mejor = -1;
        int mejorPuntos = 0;
        for (int c = 0; c < claves.length; c++) {
            int puntos = usada[c] ? 0 : puntuador.puntos(claves[c]);
            if (puntos > mejorPuntos) {
                mejorPuntos = puntos;
                mejor = c;
            }
        }
        if (mejor >= 0) {
            usada[mejor] = true;
        }
        return mejor;
    }

    private interface Medida {
        double valor(int columna);
    }

    private static int porContenido(ArchivoImportado archivo, int desde, boolean[] usada, Medida medida) {
        int mejor = -1;
        double mejorValor = 0;
        for (int c = 0; c < usada.length; c++) {
            if (usada[c]) {
                continue;
            }
            double v = medida.valor(c);
            if (v > mejorValor) {
                mejorValor = v;
                mejor = c;
            }
        }
        // Una columna que casi no tiene fechas o números no sirve
        if (mejor >= 0 && mejorValor >= 0.3) {
            usada[mejor] = true;
            return mejor;
        }
        return -1;
    }

    private interface Prueba {
        boolean cumple(Object celda);
    }

    private static double proporcion(ArchivoImportado archivo, int desde, int columna, Prueba prueba) {
        int total = 0;
        int cumplen = 0;
        for (int f = Math.max(0, desde); f < archivo.filas().size(); f++) {
            List<Object> fila = archivo.filas().get(f);
            Object celda = columna < fila.size() ? fila.get(columna) : null;
            if (celda != null) {
                total++;
                if (prueba.cumple(celda)) {
                    cumplen++;
                }
            }
        }
        return total == 0 ? 0 : (double) cumplen / total;
    }

    private static boolean esFecha(Object celda) {
        return celda instanceof LocalDate || (celda instanceof String s && pareceFecha(s));
    }

    private static boolean esNumero(Object celda) {
        return celda instanceof BigDecimal || (celda instanceof String s && pareceNumero(s));
    }

    private static double longitudMediaDeTexto(ArchivoImportado archivo, int desde, int columna) {
        int total = 0;
        long letras = 0;
        for (int f = Math.max(0, desde); f < archivo.filas().size(); f++) {
            List<Object> fila = archivo.filas().get(f);
            if (columna < fila.size() && fila.get(columna) instanceof String s && !esFecha(s) && !esNumero(s)) {
                total++;
                letras += s.length();
            }
        }
        // Se normaliza para que 20 letras de media ya cuenten como una columna de conceptos
        return total == 0 ? 0 : Math.min(1.0, (double) letras / total / 20);
    }

    // ===== Formato =====

    /**
     * Averigua cómo están escritas las fechas de texto del archivo: si empiezan por el año, y si no, si alguna tiene un
     * primer número mayor que 12 (día primero) o un segundo número mayor que 12 (mes primero). Si no hay manera de saberlo,
     * se supone día/mes/año.
     * @param archivo: archivo leído
     * @param desde: primera fila de datos
     * @param columnaFecha: columna de las fechas (-1 si no hay)
     * @return Devuelve el formato detectado (nunca AUTO)
     */
    public FormatoFecha detectarFormatoFecha(ArchivoImportado archivo, int desde, int columnaFecha) {
        if (columnaFecha < 0) {
            return FormatoFecha.DIA_MES_ANIO;
        }
        int diaPrimero = 0;
        int mesPrimero = 0;
        int anioPrimero = 0;
        for (int f = Math.max(0, desde); f < archivo.filas().size(); f++) {
            List<Object> fila = archivo.filas().get(f);
            if (columnaFecha >= fila.size() || !(fila.get(columnaFecha) instanceof String s)) {
                continue;
            }
            Matcher m = FECHA.matcher(s.trim());
            if (!m.matches()) {
                continue;
            }
            if (m.group(1).length() == 4) {
                anioPrimero++;
            } else {
                int a = Integer.parseInt(m.group(1));
                int b = Integer.parseInt(m.group(2));
                if (a > 12) {
                    diaPrimero++;
                } else if (b > 12) {
                    mesPrimero++;
                }
            }
        }
        if (anioPrimero > 0 && anioPrimero >= diaPrimero + mesPrimero) {
            return FormatoFecha.ANIO_MES_DIA;
        }
        return mesPrimero > diaPrimero ? FormatoFecha.MES_DIA_ANIO : FormatoFecha.DIA_MES_ANIO;
    }

    /**
     * Averigua el separador de decimales de los importes escritos como texto: si terminan en ",50" o ",5", coma; si
     * terminan en ".50", punto. Si no se sabe, coma (lo habitual en España).
     * @param archivo: archivo leído
     * @param desde: primera fila de datos
     * @param columnaImporte: columna de los importes (-1 si no hay)
     * @return Devuelve el separador detectado (nunca AUTO)
     */
    public SeparadorDecimal detectarDecimal(ArchivoImportado archivo, int desde, int columnaImporte) {
        if (columnaImporte < 0) {
            return SeparadorDecimal.COMA;
        }
        int coma = 0;
        int punto = 0;
        for (int f = Math.max(0, desde); f < archivo.filas().size(); f++) {
            List<Object> fila = archivo.filas().get(f);
            if (columnaImporte < fila.size() && fila.get(columnaImporte) instanceof String s) {
                String t = s.trim();
                if (NUMERO_COMA.matcher(t).matches()) {
                    coma++;
                } else if (NUMERO_PUNTO.matcher(t).matches()) {
                    punto++;
                }
            }
        }
        return punto > coma ? SeparadorDecimal.PUNTO : SeparadorDecimal.COMA;
    }

    // ===== Interpretar =====

    /**
     * Interpreta todas las filas de datos con las columnas y el formato elegidos y las prepara para la vista previa:
     * cada una queda válida, duplicada (ya existe en la cuenta) o con error, con su categoría asignada. Las filas sin
     * fecha ni importe (pies de página, totales) se saltan.
     * @param usuario: usuario de la sesión
     * @param cuenta: cuenta a la que se importa
     * @param archivo: archivo leído
     * @param filaTitulos: posición de la fila de títulos, o -1 si no hay
     * @param columnas: qué columna es cada dato
     * @param formato: formato de fechas, decimales y signo
     * @return Devuelve una fila por cada movimiento del archivo, en el orden del archivo
     * @throws ArchivoNoValido si faltan columnas o el archivo tiene más movimientos de los que se pueden importar a la vez
     */
    public List<FilaImportada> interpretar(Usuario usuario, Cuenta cuenta, ArchivoImportado archivo, int filaTitulos,
            Columnas columnas, Formato formato) throws ArchivoNoValido {
        if (!columnas.completas()) {
            throw new ArchivoNoValido("Elige las columnas de la fecha, el concepto y el importe.");
        }
        int desde = filaTitulos + 1;
        FormatoFecha fechas = formato.fecha() == FormatoFecha.AUTO ? detectarFormatoFecha(archivo, desde, columnas.fecha()) : formato.fecha();
        SeparadorDecimal decimales = formato.decimal() == SeparadorDecimal.AUTO ? detectarDecimal(archivo, desde, columnas.importe()) : formato.decimal();

        Map<String, List<Categoria>> categoriasPorTipo = new HashMap<>();
        categoriasPorTipo.put("gasto", categoriaService.categoriasDeTipo(usuario, "gasto"));
        categoriasPorTipo.put("ingreso", categoriaService.categoriasDeTipo(usuario, "ingreso"));

        // Los movimientos que ya hay en la cuenta, contados por clave, para avisar de los duplicados
        Map<String, Integer> existentes = new HashMap<>();
        for (Movimiento m : movimientoDAO.listarPorCuenta(cuenta.getId())) {
            existentes.merge(clave(m.getFecha(), m.getImporte(), m.getTipo(), m.getDescripcion()), 1, Integer::sum);
        }

        List<FilaImportada> resultado = new ArrayList<>();
        for (int f = Math.max(0, desde); f < archivo.filas().size(); f++) {
            List<Object> celdas = archivo.filas().get(f);
            Object celdaFecha = celda(celdas, columnas.fecha());
            Object celdaImporte = celda(celdas, columnas.importe());
            LocalDate fecha = fechaDe(celdaFecha, fechas);
            BigDecimal importe = importeDe(celdaImporte, decimales);
            if (fecha == null && importe == null) {
                // Una fila sin fecha ni importe es un total, un pie de página o ruido: no es un movimiento
                continue;
            }
            if (resultado.size() >= MAX_FILAS) {
                throw new ArchivoNoValido("El archivo tiene más de " + MAX_FILAS + " movimientos. Divídelo en varios archivos para importarlo.");
            }
            FilaImportada fila = new FilaImportada(f + 1);
            fila.setFecha(fecha);
            fila.setConcepto(conceptoDe(celda(celdas, columnas.concepto()), celda(celdas, columnas.categoria())));
            fila.setNota(notaDe(celda(celdas, columnas.nota())));
            fila.setSaldo(importeDe(celda(celdas, columnas.saldo()), decimales));

            if (fecha == null) {
                fila.setEstado(FilaImportada.Estado.ERROR, "Fecha no válida");
            } else if (fecha.getYear() < 1990 || fecha.getYear() > 2100) {
                fila.setEstado(FilaImportada.Estado.ERROR, "Fecha fuera de rango");
            } else if (importe == null) {
                fila.setEstado(FilaImportada.Estado.ERROR, "Importe no válido");
            } else if (importe.signum() == 0) {
                fila.setEstado(FilaImportada.Estado.ERROR, "Importe cero");
            } else {
                boolean negativo = importe.signum() < 0;
                boolean gasto = formato.signo() == SignoGasto.NEGATIVO ? negativo : !negativo;
                fila.setTipo(gasto ? "gasto" : "ingreso");
                try {
                    fila.setImporte(Dinero.normalizar(importe.abs()));
                } catch (IllegalArgumentException e) {
                    fila.setEstado(FilaImportada.Estado.ERROR, "Importe demasiado grande");
                }
                if (fila.getEstado() == null) {
                    asignarCategoria(fila, celda(celdas, columnas.categoria()), celda(celdas, columnas.subcategoria()), categoriasPorTipo);
                    String clave = clave(fecha.toString(), fila.getImporte(), fila.getTipo(), fila.getConcepto());
                    int quedan = existentes.getOrDefault(clave, 0);
                    if (quedan > 0) {
                        existentes.put(clave, quedan - 1);
                        fila.setEstado(FilaImportada.Estado.DUPLICADO, "Ya existe un movimiento igual");
                    } else {
                        fila.setEstado(FilaImportada.Estado.VALIDO, null);
                    }
                }
            }
            resultado.add(fila);
        }
        return resultado;
    }

    private static Object celda(List<Object> fila, int columna) {
        return columna >= 0 && columna < fila.size() ? fila.get(columna) : null;
    }

    /**
     * @param concepto: celda del concepto
     * @param categoria: celda de la categoría (se usa si el concepto está vacío)
     * @return Devuelve el concepto limpio y recortado a la longitud máxima; "Sin concepto" si no hay nada
     */
    private static String conceptoDe(Object concepto, Object categoria) {
        String texto = concepto == null ? "" : concepto.toString().replaceAll("\\s+", " ").trim();
        if (texto.isEmpty() && categoria != null) {
            texto = categoria.toString().trim();
        }
        if (texto.isEmpty()) {
            texto = "Sin concepto";
        }
        return texto.length() > MAX_CONCEPTO ? texto.substring(0, MAX_CONCEPTO - 1).trim() + "…" : texto;
    }

    /**
     * @param nota: celda de la nota
     * @return Devuelve la nota limpia y recortada a la longitud máxima, o null si no hay
     */
    private static String notaDe(Object nota) {
        String texto = nota == null ? "" : nota.toString().replaceAll("\\s+", " ").trim();
        if (texto.isEmpty()) {
            return null;
        }
        return texto.length() > MAX_NOTA ? texto.substring(0, MAX_NOTA - 1).trim() + "…" : texto;
    }

    /**
     * Relaciona la categoría del archivo con las de la aplicación para que quede como pone la tabla: por el nombre y,
     * si el archivo trae subcategoría, también por la subcategoría (sin mirar mayúsculas ni tildes y solo entre las del
     * mismo tipo). Si no existe una igual, se creará al importar con ese nombre y esa subcategoría. Si el archivo no trae
     * subcategoría, vale cualquier categoría con ese nombre (mejor la que no tiene subcategoría). Si no trae categoría
     * pero sí subcategoría, la subcategoría hace de categoría. Si no trae ninguna (o la categoría es del otro tipo), se
     * usa "Otros ingresos" u "Otros gastos".
     */
    private void asignarCategoria(FilaImportada fila, Object celdaCategoria, Object celdaSubcategoria, Map<String, List<Categoria>> porTipo) {
        List<Categoria> delTipo = porTipo.get(fila.getTipo());
        String nombre = celdaCategoria == null ? "" : celdaCategoria.toString().trim();
        String sub = celdaSubcategoria == null ? "" : celdaSubcategoria.toString().trim();
        if (nombre.isEmpty() && !sub.isEmpty()) {
            nombre = sub;
            sub = "";
        }
        if (!nombre.isEmpty()) {
            String buscada = normalizar(nombre);
            String buscadaSub = normalizar(sub);
            Categoria alternativa = null;
            for (Categoria c : delTipo) {
                if (!normalizar(c.getNombre()).equals(buscada)) {
                    continue;
                }
                String subDeC = c.getSubcategoria() == null ? "" : normalizar(c.getSubcategoria());
                if (subDeC.equals(buscadaSub)) {
                    fila.setCategoria(c);
                    return;
                }
                if (alternativa == null) {
                    alternativa = c;
                }
            }
            if (buscadaSub.isEmpty() && alternativa != null) {
                // El archivo no dice subcategoría: vale cualquiera con ese nombre
                fila.setCategoria(alternativa);
                return;
            }
            String otroTipo = "gasto".equals(fila.getTipo()) ? "ingreso" : "gasto";
            boolean esDelOtroTipo = alternativa == null && porTipo.get(otroTipo).stream().anyMatch(c -> normalizar(c.getNombre()).equals(buscada));
            if (!esDelOtroTipo) {
                fila.setCategoriaNueva(recortarNombre(nombre), sub.isEmpty() ? null : recortarNombre(sub));
                return;
            }
        }
        String porDefecto = "gasto".equals(fila.getTipo()) ? "Otros gastos" : "Otros ingresos";
        for (Categoria c : delTipo) {
            if (normalizar(c.getNombre()).equals(normalizar(porDefecto)) && (c.getSubcategoria() == null || c.getSubcategoria().isBlank())) {
                fila.setCategoria(c);
                return;
            }
        }
        fila.setCategoriaNueva(porDefecto, null);
    }

    private static String recortarNombre(String texto) {
        return texto.length() > MAX_NOMBRE_CATEGORIA ? texto.substring(0, MAX_NOMBRE_CATEGORIA).trim() : texto;
    }

    /**
     * @param tipo: "gasto" o "ingreso"
     * @param usuario: usuario de la sesión
     * @return Devuelve las categorías de la aplicación de ese tipo, para elegir otra en la vista previa
     */
    public List<Categoria> categoriasDeTipo(Usuario usuario, String tipo) {
        return categoriaService.categoriasDeTipo(usuario, tipo);
    }

    private static String clave(String fecha, BigDecimal importe, String tipo, String concepto) {
        return fecha + "|" + importe.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() + "|" + tipo + "|" + normalizar(concepto == null ? "" : concepto);
    }

    // ===== Fechas e importes =====

    private static LocalDate fechaDe(Object celda, FormatoFecha formato) {
        if (celda instanceof LocalDate d) {
            return d;
        }
        if (celda instanceof BigDecimal n) {
            // Un número entero entre 1982 y 2064 en una columna de fechas es una fecha de Excel guardada sin formato
            int serie = n.intValue();
            return n.stripTrailingZeros().scale() <= 0 && serie >= 30000 && serie <= 60000 ? LocalDate.of(1899, 12, 30).plusDays(serie) : null;
        }
        if (!(celda instanceof String s)) {
            return null;
        }
        String t = s.trim();
        try {
            Matcher compacta = FECHA_COMPACTA.matcher(t);
            if (compacta.matches()) {
                return LocalDate.of(Integer.parseInt(compacta.group(1)), Integer.parseInt(compacta.group(2)), Integer.parseInt(compacta.group(3)));
            }
            Matcher m = FECHA.matcher(t);
            if (!m.matches()) {
                return null;
            }
            int a = Integer.parseInt(m.group(1));
            int b = Integer.parseInt(m.group(2));
            int c = Integer.parseInt(m.group(3));
            int dia;
            int mes;
            int anio;
            if (m.group(1).length() == 4 || formato == FormatoFecha.ANIO_MES_DIA) {
                anio = a;
                mes = b;
                dia = c;
            } else if (formato == FormatoFecha.MES_DIA_ANIO) {
                mes = a;
                dia = b;
                anio = c;
            } else {
                dia = a;
                mes = b;
                anio = c;
            }
            // Un año de dos cifras es de este siglo
            return LocalDate.of(anio < 100 ? 2000 + anio : anio, mes, dia);
        } catch (DateTimeException | NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal importeDe(Object celda, SeparadorDecimal decimal) {
        if (celda instanceof BigDecimal n) {
            return n;
        }
        return celda instanceof String s ? importeDeTexto(s, decimal) : null;
    }

    /**
     * Lee un importe escrito como texto: admite símbolo de moneda, espacios, signo delante o detrás, paréntesis como
     * negativo y separador de miles.
     * @param texto: importe escrito
     * @param decimal: separador de decimales; en AUTO se deduce de cada valor
     * @return Devuelve el importe, o null si no es un número
     */
    static BigDecimal importeDeTexto(String texto, SeparadorDecimal decimal) {
        String t = texto.trim().replace('\u2212', '-').replace("\u00a0", "").replace(" ", "");
        t = t.replaceAll("(?i)(eur|usd|gbp|€|\\$|£)", "");
        if (t.isEmpty()) {
            return null;
        }
        boolean negativo = false;
        if (t.startsWith("(") && t.endsWith(")")) {
            negativo = true;
            t = t.substring(1, t.length() - 1);
        }
        if (t.endsWith("-")) {
            negativo = !negativo;
            t = t.substring(0, t.length() - 1);
        } else if (t.startsWith("-")) {
            negativo = !negativo;
            t = t.substring(1);
        } else if (t.startsWith("+")) {
            t = t.substring(1);
        }
        if (!t.matches("[0-9.,]+") || !t.matches(".*[0-9].*")) {
            return null;
        }
        String limpio;
        SeparadorDecimal usado = decimal;
        if (usado == SeparadorDecimal.AUTO) {
            int ultimaComa = t.lastIndexOf(',');
            int ultimoPunto = t.lastIndexOf('.');
            if (ultimaComa >= 0 && ultimoPunto >= 0) {
                usado = ultimaComa > ultimoPunto ? SeparadorDecimal.COMA : SeparadorDecimal.PUNTO;
            } else if (ultimaComa >= 0) {
                // 1,234,567 son miles; 12,5 y 1,25 son decimales
                usado = t.indexOf(',') != ultimaComa ? SeparadorDecimal.PUNTO : SeparadorDecimal.COMA;
            } else {
                // 1.234.567 son miles; 160.68 y 12.5 son decimales; 1.250 se entiende como miles
                usado = t.indexOf('.') != ultimoPunto || (ultimoPunto >= 0 && t.length() - ultimoPunto - 1 == 3 && ultimoPunto > 0
                        && t.matches("\\d{1,3}\\.\\d{3}")) ? SeparadorDecimal.COMA : SeparadorDecimal.PUNTO;
            }
        }
        limpio = usado == SeparadorDecimal.COMA ? t.replace(".", "").replace(',', '.') : t.replace(",", "");
        try {
            BigDecimal valor = new BigDecimal(limpio);
            return negativo ? valor.negate() : valor;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * @param texto: cualquier texto
     * @return Devuelve el texto en minúsculas, sin tildes y con un solo espacio entre palabras (y sin signos), para comparar
     */
    static String normalizar(String texto) {
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    // ===== Saldo =====

    /**
     * Busca el saldo del archivo: el de la fila con la fecha más reciente (cualquier fila con fecha y saldo, esté marcada o no,
     * porque el saldo es el de la cuenta en el banco). Si hay varias del mismo día, se queda con la última según el orden en
     * que el banco las escribe: la primera si el archivo va de lo más reciente a lo más antiguo, y la última si va al revés.
     * @param filas: filas interpretadas
     * @return Devuelve el saldo y su fecha, o null si ninguna fila trae saldo
     */
    public SaldoArchivo saldoDelArchivo(List<FilaImportada> filas) {
        LocalDate primera = null;
        LocalDate ultima = null;
        for (FilaImportada f : filas) {
            if (f.getFecha() != null) {
                if (primera == null) {
                    primera = f.getFecha();
                }
                ultima = f.getFecha();
            }
        }
        // Va de lo más reciente a lo más antiguo si la primera fecha no es anterior a la última
        boolean descendente = primera != null && !primera.isBefore(ultima);
        FilaImportada elegida = null;
        for (FilaImportada f : filas) {
            if (f.getFecha() == null || f.getSaldo() == null) {
                continue;
            }
            if (elegida == null || f.getFecha().isAfter(elegida.getFecha())
                    || (f.getFecha().isEqual(elegida.getFecha()) && !descendente)) {
                elegida = f;
            }
        }
        return elegida == null ? null : new SaldoArchivo(elegida.getSaldo(), elegida.getFecha());
    }

    /**
     * Calcula lo que ya suma la cuenta hasta una fecha sin contar lo que se va a importar: su saldo inicial más sus ingresos
     * menos sus gastos de ese día y de los anteriores.
     * @param cuenta: cuenta a la que se importa
     * @param hasta: fecha (incluida)
     * @return Devuelve el saldo de la cuenta a esa fecha con los movimientos que ya tiene
     */
    public BigDecimal saldoBase(Cuenta cuenta, LocalDate hasta) {
        BigDecimal saldo = saldoInicialActual(cuenta);
        for (Movimiento m : movimientoDAO.listarPorCuenta(cuenta.getId())) {
            if (m.getFecha() != null && m.getFecha().compareTo(hasta.toString()) <= 0) {
                saldo = saldo.add("ingreso".equals(m.getTipo()) ? m.getImporte() : m.getImporte().negate());
            }
        }
        return saldo;
    }

    /**
     * Suma lo que aportan unas filas (ingresos menos gastos) hasta una fecha
     * @param filas: filas a sumar (las que se van a importar)
     * @param hasta: fecha (incluida)
     * @return Devuelve la suma con signo
     */
    public static BigDecimal sumaHasta(List<FilaImportada> filas, LocalDate hasta) {
        BigDecimal suma = Dinero.CERO;
        for (FilaImportada f : filas) {
            if (f.getFecha() != null && !f.getFecha().isAfter(hasta) && f.getImporteConSigno() != null) {
                suma = suma.add(f.getImporteConSigno());
            }
        }
        return suma;
    }

    private BigDecimal saldoInicialActual(Cuenta cuenta) {
        return cuentaDAO.buscarPorId(cuenta.getId()).map(Cuenta::getSaldoInicial).orElse(cuenta.getSaldoInicial());
    }

    // ===== Importar =====

    /**
     * Guarda los movimientos marcados en la cuenta, en una sola transacción: si falla uno, no se guarda ninguno. Las
     * categorías que no existían se crean una sola vez cada una. Las filas con error no se importan nunca.
     * @param usuario: usuario de la sesión
     * @param cuenta: cuenta a la que se importa
     * @param filas: filas marcadas en la vista previa
     * @return Devuelve cuántos movimientos y categorías se han creado y el mes del movimiento más reciente
     * @throws RuntimeException si falla la base de datos (no se ha guardado nada)
     */
    public Resultado importar(Usuario usuario, Cuenta cuenta, List<FilaImportada> filas) {
        return importar(usuario, cuenta, filas, null);
    }

    /**
     * Igual que importar sin más, y además, si se pasa el saldo del archivo, ajusta el saldo de la cuenta al terminar. El
     * ajuste cambia el saldo inicial de la cuenta para que, a la fecha del saldo del archivo, la cuenta tenga justo ese saldo
     * (con los movimientos que ya tenía y los importados). Los movimientos posteriores a esa fecha siguen sumándose encima.
     * Todo va en la misma transacción.
     * @param usuario: usuario de la sesión
     * @param cuenta: cuenta a la que se importa
     * @param filas: filas marcadas en la vista previa
     * @param ajustarAlSaldo: saldo del archivo al que se ajusta la cuenta, o null para no tocar el saldo
     * @return Devuelve cuántos movimientos y categorías se han creado, el mes del más reciente y si se ha ajustado el saldo
     * @throws RuntimeException si falla la base de datos o el saldo no es válido (no se ha guardado nada)
     */
    public Resultado importar(Usuario usuario, Cuenta cuenta, List<FilaImportada> filas, SaldoArchivo ajustarAlSaldo) {
        Connection conn = DatabaseManager.getConnection();
        boolean autoCommit;
        try {
            autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo iniciar la importación", e);
        }
        try {
            Map<String, Categoria> creadas = new HashMap<>();
            int importados = 0;
            LocalDate ultima = null;
            for (FilaImportada f : filas) {
                if (f.getEstado() == FilaImportada.Estado.ERROR || f.getFecha() == null || f.getImporte() == null) {
                    continue;
                }
                Categoria categoria = f.getCategoria();
                if (categoria == null) {
                    String nombre = f.getNombreCategoriaNueva();
                    String subcategoria = f.getSubcategoriaNueva();
                    categoria = creadas.computeIfAbsent(f.getTipo() + "|" + normalizar(nombre) + "|" + normalizar(subcategoria == null ? "" : subcategoria),
                            k -> crearCategoria(usuario, nombre, f.getTipo(), subcategoria));
                }
                Movimiento nuevo = new Movimiento(0, usuario.getId(), cuenta.getId(), categoria.getId(), f.getImporte(),
                        f.getTipo(), f.getFecha().toString(), f.getConcepto(), false, 0);
                nuevo.setNota(f.getNota());
                movimientoDAO.insertar(nuevo);
                importados++;
                if (ultima == null || f.getFecha().isAfter(ultima)) {
                    ultima = f.getFecha();
                }
            }
            boolean ajustado = false;
            if (ajustarAlSaldo != null) {
                ajustarSaldoInicial(cuenta, ajustarAlSaldo);
                ajustado = true;
            }
            conn.commit();
            return new Resultado(importados, creadas.size(), ultima == null ? YearMonth.now() : YearMonth.from(ultima), ajustado);
        } catch (SQLException | RuntimeException e) {
            try {
                conn.rollback();
            } catch (SQLException ignorada) {
                // Si tampoco se puede deshacer, el error original es el que importa
            }
            throw e instanceof RuntimeException r ? r : new RuntimeException("No se pudo guardar la importación", e);
        } finally {
            try {
                conn.setAutoCommit(autoCommit);
            } catch (SQLException ignorada) {
                // La conexión se vuelve a configurar al abrir la siguiente operación
            }
        }
    }

    /**
     * Cambia el saldo inicial de la cuenta para que, a la fecha del saldo del archivo, la cuenta tenga ese saldo: el saldo
     * inicial pasa a ser el saldo del archivo menos lo que suman los movimientos de la cuenta hasta esa fecha (los que
     * tenía y los recién importados, que ya están en la base de datos dentro de la misma transacción).
     */
    private void ajustarSaldoInicial(Cuenta cuenta, SaldoArchivo saldoArchivo) {
        Cuenta actual = cuentaDAO.buscarPorId(cuenta.getId()).orElseThrow(() -> new IllegalStateException("La cuenta ya no existe"));
        BigDecimal movimientos = Dinero.CERO;
        for (Movimiento m : movimientoDAO.listarPorCuenta(cuenta.getId())) {
            if (m.getFecha() != null && m.getFecha().compareTo(saldoArchivo.fecha().toString()) <= 0) {
                movimientos = movimientos.add("ingreso".equals(m.getTipo()) ? m.getImporte() : m.getImporte().negate());
            }
        }
        try {
            actual.setSaldoInicial(Dinero.normalizar(saldoArchivo.saldo().subtract(movimientos)));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("El saldo del archivo no es válido: es demasiado grande.", e);
        }
        cuentaDAO.actualizar(actual);
    }

    private Categoria crearCategoria(Usuario usuario, String nombre, String tipo, String subcategoria) {
        return categoriaService.guardar(usuario, null, nombre, tipo, subcategoria);
    }
}
