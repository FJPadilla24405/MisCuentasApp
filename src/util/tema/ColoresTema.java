package util.tema;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Colores de los temas. Aquí se define qué colores puede elegir el usuario en el tema personalizado, cuáles son
 * los del tema claro (los mismos que application.css) y los del tema oscuro, y cómo se calculan, a partir de
 * los colores elegidos, las variables de CSS que usa la aplicación (-mc-fondo, -mc-verde...) y los colores de las
 * gráficas. No usa JavaFX: los colores van como texto "#rrggbb".
 *
 * @author Fco Padilla
 */
public final class ColoresTema {

    /**
     * Cada color que se puede elegir en el tema personalizado.
     */
    public enum Elemento {
        FONDO("fondo", "Fondo de la aplicación", "Fondos", "#f5f7f5"),
        SUPERFICIE("superficie", "Tarjetas, barras y ventanas", "Fondos", "#ffffff"),
        CABECERA("cabecera", "Cabecera y tarjeta del saldo", "Fondos", "#153f30"),
        PRINCIPAL("principal", "Botones y enlaces", "Acentos", "#287a57"),
        SELECCION("seleccion", "Elemento seleccionado", "Acentos", "#e3efe8"),
        TITULO("titulo", "Títulos e importes", "Textos y líneas", "#1b3a2d"),
        TEXTO("texto", "Texto general", "Textos y líneas", "#2b3a33"),
        SUAVE("suave", "Texto secundario", "Textos y líneas", "#6b7a72"),
        BORDE("borde", "Bordes y líneas", "Textos y líneas", "#dbe3df"),
        INGRESOS("ingresos", "Tarjeta de ingresos", "Resumen", "#86ff7d"),
        GASTOS("gastos", "Tarjeta de gastos", "Resumen", "#ffbe7d"),
        BALANCE_POSITIVO("balancePositivo", "Balance positivo", "Resumen", "#7dffcb"),
        BALANCE_NEGATIVO("balanceNegativo", "Balance negativo", "Resumen", "#ff937d"),
        AVISO("aviso", "Avisos", "Avisos", "#d98a1b"),
        ERROR("error", "Errores y alertas", "Avisos", "#c0392b");

        private final String clave;
        private final String etiqueta;
        private final String grupo;
        private final String claro;

        Elemento(String clave, String etiqueta, String grupo, String claro) {
            this.clave = clave;
            this.etiqueta = etiqueta;
            this.grupo = grupo;
            this.claro = claro;
        }

        /** @return Devuelve la clave con la que se guarda el color */
        public String getClave() {
            return clave;
        }

        /** @return Devuelve el nombre que ve el usuario */
        public String getEtiqueta() {
            return etiqueta;
        }

        /** @return Devuelve el grupo en el que se enseña */
        public String getGrupo() {
            return grupo;
        }

        /** @return Devuelve el color que tiene en el tema claro */
        public String getClaro() {
            return claro;
        }
    }

    /**
     * Colores de las gráficas, que se dibujan en un lienzo y por tanto no pueden usar variables de CSS.
     * @param ingresos: barras de ingresos, línea del balance y primer trozo del donut
     * @param gastos: barras de gastos
     * @param textoSuave: textos de los ejes
     * @param titulo: texto del elemento resaltado
     * @param cuadricula: líneas de la cuadrícula
     * @param base: línea del cero y anillo vacío del donut
     * @param otras: trozo del donut que agrupa las categorías pequeñas
     * @param superficie: relleno de los puntos de la línea
     * @param categorias: colores de las categorías del donut, de la más gastada a la menos
     */
    public record Grafica(String ingresos, String gastos, String textoSuave, String titulo, String cuadricula,
            String base, String otras, String superficie, List<String> categorias) {
    }

    // Las gráficas del tema claro, tal como eran antes de que hubiera temas
    public static final Grafica GRAFICA_CLARA = new Grafica("#287a57", "#a9c7b3", "#6b7a72", "#1b3a2d", "#e6ebe8",
            "#d3dbd6", "#c3cbc6", "#ffffff",
            List.of("#287a57", "#a9c7b3", "#8aa4c2", "#cfe0d4", "#6fa383", "#d6dcd8"));

    private static final Pattern HEX = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final String BLANCO = "#ffffff";
    private static final String NEGRO = "#000000";
    // Texto que se pone sobre un color claro (casi negro, con un toque verde como el resto de la aplicación)
    private static final String TEXTO_OSCURO = "#0f1a14";
    // Luminancia a partir de la cual un fondo se considera claro (donde el blanco y el negro contrastan igual)
    private static final double LUMINANCIA_MEDIA = 0.179;

    // No se instancia
    private ColoresTema() {
    }

    // ===== Colores de partida =====

    /**
     * @return Devuelve los colores del tema claro (los de application.css)
     */
    public static Map<String, String> baseClara() {
        Map<String, String> m = new LinkedHashMap<>();
        for (Elemento e : Elemento.values()) {
            m.put(e.getClave(), e.getClaro());
        }
        return m;
    }

    /**
     * @return Devuelve los colores del tema oscuro. También sirven de punto de partida para el personalizado.
     */
    public static Map<String, String> baseOscura() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("fondo", "#101714");
        m.put("superficie", "#18221d");
        m.put("cabecera", "#1d4d3b");
        m.put("principal", "#4cbb89");
        m.put("seleccion", "#1f3f31");
        m.put("titulo", "#e8f3ed");
        m.put("texto", "#cdddd4");
        m.put("suave", "#8fa59a");
        m.put("borde", "#2c3b34");
        m.put("ingresos", "#1d4a35");
        m.put("gastos", "#54391c");
        m.put("balancePositivo", "#1b4a44");
        m.put("balanceNegativo", "#5a2a22");
        m.put("aviso", "#e0a23a");
        m.put("error", "#e5675a");
        return m;
    }

    /**
     * Deja un mapa de colores completo y válido: lo que falta o no es un color "#rrggbb" se sustituye por el
     * del tema claro, y se ignoran las claves desconocidas.
     * @param elegidos: colores elegidos (puede ser null o estar incompleto)
     * @return Devuelve un mapa nuevo con todos los colores, en minúsculas
     */
    public static Map<String, String> completar(Map<String, String> elegidos) {
        Map<String, String> m = new LinkedHashMap<>();
        for (Elemento e : Elemento.values()) {
            String valor = elegidos == null ? null : elegidos.get(e.getClave());
            m.put(e.getClave(), valor != null && HEX.matcher(valor.trim()).matches()
                    ? valor.trim().toLowerCase(Locale.ROOT) : e.getClaro());
        }
        return m;
    }

    // ===== Variables de CSS =====

    /**
     * Calcula todas las variables de CSS a partir de los colores elegidos. Lo que el usuario no elige
     * (el color al pasar el ratón, los bordes suaves, el texto sobre los botones...) se deduce de lo que sí
     * elige, de forma que sirva igual para un tema claro que para uno oscuro.
     * Si el fondo de las tarjetas es oscuro, también se cambian los colores base de los controles de JavaFX
     * (menús, listas desplegables, calendarios) para que no salgan claros.
     * @param elegidos: colores elegidos (se completan los que falten)
     * @return Devuelve las variables de CSS (nombre con el guion inicial, por ejemplo "-mc-fondo")
     */
    public static Map<String, String> variables(Map<String, String> elegidos) {
        Map<String, String> c = completar(elegidos);
        String fondo = c.get("fondo");
        String superficie = c.get("superficie");
        String cabecera = c.get("cabecera");
        String principal = c.get("principal");
        String titulo = c.get("titulo");
        String texto = c.get("texto");
        String suave = c.get("suave");
        String borde = c.get("borde");
        String error = c.get("error");
        boolean oscuro = esOscuro(superficie);

        Map<String, String> v = new LinkedHashMap<>();
        v.put("-mc-fondo", fondo);
        v.put("-mc-superficie", superficie);
        v.put("-mc-cabecera", cabecera);
        v.put("-mc-sobre-cabecera", textoSobre(cabecera));
        v.put("-mc-sobre-cabecera-suave", mezclar(textoSobre(cabecera), cabecera, 0.2));
        v.put("-mc-verde", principal);
        v.put("-mc-verde-hover", oscuro ? mezclar(principal, BLANCO, 0.15) : mezclar(principal, NEGRO, 0.2));
        v.put("-mc-sobre-verde", textoSobre(principal));
        v.put("-mc-activo", c.get("seleccion"));
        v.put("-mc-acento-texto", titulo);
        v.put("-mc-titulo", titulo);
        v.put("-mc-texto", texto);
        v.put("-mc-suave", suave);
        v.put("-mc-tenue", mezclar(suave, superficie, 0.35));
        v.put("-mc-icono", mezclar(suave, superficie, 0.15));
        v.put("-mc-borde", borde);
        v.put("-mc-tramo", borde);
        v.put("-mc-borde-suave", mezclar(borde, superficie, 0.5));
        v.put("-mc-borde-fuerte", mezclar(borde, texto, 0.3));
        v.put("-mc-hover", mezclar(superficie, texto, 0.06));
        v.put("-mc-pista", mezclar(superficie, texto, 0.10));
        v.put("-resumen-ingresos", c.get("ingresos"));
        v.put("-resumen-gastos", c.get("gastos"));
        v.put("-resumen-balanceok", c.get("balancePositivo"));
        v.put("-resumen-balancemal", c.get("balanceNegativo"));
        v.put("-mc-aviso", c.get("aviso"));
        v.put("-mc-gasto", c.get("aviso"));
        v.put("-mc-error", error);
        v.put("-mc-negativo", error);
        v.put("-mc-error-fondo", mezclar(error, superficie, oscuro ? 0.80 : 0.88));
        v.put("-mc-error-borde", mezclar(error, superficie, oscuro ? 0.60 : 0.70));
        if (oscuro) {
            // Colores base de JavaFX: los controles sin estilo propio se derivan de -fx-base
            v.put("-fx-base", mezclar(superficie, texto, 0.14));
            v.put("-fx-background", fondo);
            v.put("-fx-control-inner-background", superficie);
            v.put("-fx-accent", principal);
        }
        return v;
    }

    /**
     * Pasa las variables a texto para ponerlo como estilo de un nodo ("-mc-fondo: #f5f7f5; -mc-verde: ...")
     * @param variables: variables de CSS
     * @return Devuelve el texto del estilo (vacío si no hay variables)
     */
    public static String estilo(Map<String, String> variables) {
        StringBuilder sb = new StringBuilder();
        if (variables != null) {
            for (Map.Entry<String, String> e : variables.entrySet()) {
                sb.append(e.getKey()).append(": ").append(e.getValue()).append("; ");
            }
        }
        return sb.toString().trim();
    }

    // ===== Gráficas =====

    /**
     * Calcula los colores de las gráficas a partir de los colores elegidos
     * @param elegidos: colores elegidos (se completan los que falten)
     * @return Devuelve los colores de las gráficas
     */
    public static Grafica grafica(Map<String, String> elegidos) {
        Map<String, String> c = completar(elegidos);
        String superficie = c.get("superficie");
        String principal = c.get("principal");
        String suave = c.get("suave");
        String borde = c.get("borde");
        boolean oscuro = esOscuro(superficie);

        // Los gastos van en un tono de lo principal que se distinga de los ingresos: más suave sobre fondo claro
        // y más luminoso sobre fondo oscuro
        String gastos = oscuro ? mezclar(principal, BLANCO, 0.5) : mezclar(principal, superficie, 0.6);
        List<String> categorias = List.of(
                principal,
                gastos,
                oscuro ? "#7c9cc4" : "#8aa4c2",
                oscuro ? mezclar(principal, BLANCO, 0.75) : mezclar(principal, superficie, 0.8),
                mezclar(principal, suave, 0.4),
                mezclar(suave, superficie, 0.5));
        return new Grafica(principal, gastos, suave, c.get("titulo"), mezclar(borde, superficie, 0.5),
                mezclar(borde, c.get("texto"), 0.1), mezclar(suave, superficie, 0.6), superficie, categorias);
    }

    // ===== Operaciones con colores =====

    /**
     * @param hex: color "#rrggbb"
     * @return Devuelve true si es un color oscuro (el texto blanco se lee mejor que el negro encima)
     */
    public static boolean esOscuro(String hex) {
        return luminancia(hex) < LUMINANCIA_MEDIA;
    }

    /**
     * Elige el color de texto, blanco o casi negro, que mejor se lee sobre un fondo
     * @param fondo: color de fondo "#rrggbb"
     * @return Devuelve el color del texto
     */
    public static String textoSobre(String fondo) {
        return contraste(BLANCO, fondo) >= contraste(TEXTO_OSCURO, fondo) ? BLANCO : TEXTO_OSCURO;
    }

    /**
     * Mezcla dos colores
     * @param a: primer color "#rrggbb"
     * @param b: segundo color "#rrggbb"
     * @param t: cuánto hay del segundo, de 0 (solo el primero) a 1 (solo el segundo)
     * @return Devuelve el color resultante
     */
    public static String mezclar(String a, String b, double t) {
        int[] x = rgb(a);
        int[] y = rgb(b);
        return hex(x[0] + (y[0] - x[0]) * t, x[1] + (y[1] - x[1]) * t, x[2] + (y[2] - x[2]) * t);
    }

    /**
     * @param a: primer color
     * @param b: segundo color
     * @return Devuelve la relación de contraste (de 1 a 21) entre dos colores, según la fórmula WCAG
     */
    static double contraste(String a, String b) {
        double la = luminancia(a);
        double lb = luminancia(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    /**
     * @param hex: color "#rrggbb"
     * @return Devuelve la luminancia relativa, de 0 (negro) a 1 (blanco)
     */
    static double luminancia(String hex) {
        int[] c = rgb(hex);
        double[] l = new double[3];
        for (int i = 0; i < 3; i++) {
            double s = c[i] / 255.0;
            l[i] = s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * l[0] + 0.7152 * l[1] + 0.0722 * l[2];
    }

    private static int[] rgb(String hex) {
        int v = Integer.parseInt(hex.substring(1), 16);
        return new int[] { (v >> 16) & 0xFF, (v >> 8) & 0xFF, v & 0xFF };
    }

    private static String hex(double r, double g, double b) {
        return String.format(Locale.ROOT, "#%02x%02x%02x", limitar(r), limitar(g), limitar(b));
    }

    private static int limitar(double canal) {
        return (int) Math.max(0, Math.min(255, Math.round(canal)));
    }
}
