package service.sync;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Clase encargada de leer y escribir datos en formato JSON sin utilizar
 * dependencias externas.
 * 
 * Los tipos de datos utilizados son Map para objetos, List para arrays,
 * String para textos, Long y Double para números, Boolean para valores
 * lógicos y null para valores nulos.
 * 
 * @author Fco Padilla
 */
final class Json {

    /**
     * Constructor privado para impedir que se creen instancias de esta clase,
     * ya que sus métodos son estáticos.
     */
    private Json() {
    }

    /**
     * Convierte un objeto Java en una cadena de texto con formato JSON.
     *
     * @param valor objeto que se quiere convertir a JSON
     * @return cadena de texto que representa el objeto en formato JSON
     */
    static String escribir(Object valor) {
        StringBuilder sb = new StringBuilder();
        escribir(valor, sb);
        return sb.toString();
    }

    /**
     * Escribe un valor Java en formato JSON y añade el resultado al
     * StringBuilder recibido.
     * 
     * Comprueba el tipo de dato para determinar cómo debe representarse.
     *
     * @param v valor que se quiere convertir
     * @param sb StringBuilder donde se construye el JSON
     */
    private static void escribir(Object v, StringBuilder sb) {
        if (v == null) {
            // Representa los valores nulos.
            sb.append("null");
        } else if (v instanceof String s) {
            // Escribe el texto entre comillas y escapa los caracteres necesarios.
            escribirTexto(s, sb);
        } else if (v instanceof Boolean || v instanceof Integer || v instanceof Long) {
            // Escribe directamente los valores lógicos y los números enteros.
            sb.append(v);
        } else if (v instanceof Number n) {
            // Comprueba que el número sea válido para JSON antes de escribirlo.
            double d = n.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                throw new IllegalArgumentException("Número no válido para JSON: " + d);
            }
            sb.append(d);
        } else if (v instanceof Map<?, ?> m) {
            // Escribe un objeto JSON recorriendo sus claves y valores.
            sb.append('{');
            boolean primero = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                // Añade una coma entre los elementos del objeto.
                if (!primero) sb.append(',');
                primero = false;

                // Escribe la clave y separa la clave del valor con dos puntos.
                escribirTexto(String.valueOf(e.getKey()), sb);
                sb.append(':');
                escribir(e.getValue(), sb);
            }
            sb.append('}');
        } else if (v instanceof Iterable<?> it) {
            // Escribe una colección como un array JSON.
            sb.append('[');
            boolean primero = true;
            for (Object o : it) {
                // Añade una coma entre los elementos del array.
                if (!primero) sb.append(',');
                primero = false;
                escribir(o, sb);
            }
            sb.append(']');
        } else {
            // Rechaza los tipos de datos que no se pueden convertir.
            throw new IllegalArgumentException("Tipo no soportado en JSON: " + v.getClass());
        }
    }

    /**
     * Escribe una cadena de texto en formato JSON, escapando las comillas,
     * las barras invertidas y los caracteres de control que lo necesitan.
     *
     * @param s texto que se quiere escribir
     * @param sb StringBuilder donde se añade el texto convertido
     */
    private static void escribirTexto(String s, StringBuilder sb) {
        // Abre la cadena JSON con comillas dobles.
        sb.append('"');

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            // Convierte los caracteres especiales en sus secuencias de escape.
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    // Escapa los caracteres de control que no se pueden escribir directamente.
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }

        // Cierra la cadena JSON con comillas dobles.
        sb.append('"');
    }

    /**
     * Convierte una cadena JSON en el objeto Java correspondiente.
     * 
     * Comprueba que no queden caracteres adicionales después de leer el valor.
     *
     * @param json cadena de texto que contiene el JSON
     * @return objeto Java obtenido al interpretar el JSON
     * @throws IllegalArgumentException si el JSON contiene datos no válidos
     */
    static Object leer(String json) {
        Lector l = new Lector(json);

        // Ignora los espacios anteriores al valor JSON.
        l.saltarEspacios();

        // Lee el valor principal del JSON.
        Object v = l.valor();

        // Ignora los espacios que pueda haber después del valor.
        l.saltarEspacios();

        // Comprueba que no haya texto sobrante.
        if (l.pos != json.length()) {
            throw new IllegalArgumentException("JSON con texto sobrante en la posición " + l.pos);
        }

        return v;
    }

    /**
     * Clase interna encargada de analizar una cadena JSON y convertir
     * sus elementos en objetos Java.
     */
    private static final class Lector {

        // Cadena JSON que se está leyendo.
        final String s;

        // Posición actual dentro de la cadena.
        int pos;

        /**
         * Crea un lector para la cadena JSON recibida.
         *
         * @param s cadena JSON que se quiere analizar
         */
        Lector(String s) {
            this.s = s;
        }

        /**
         * Avanza la posición mientras encuentre espacios en blanco.
         */
        void saltarEspacios() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        }

        /**
         * Devuelve el carácter situado en la posición actual.
         *
         * @return carácter actual de la cadena
         * @throws IllegalArgumentException si se alcanza el final de la cadena
         */
        char actual() {
            if (pos >= s.length()) throw new IllegalArgumentException("JSON incompleto");
            return s.charAt(pos);
        }

        /**
         * Lee el siguiente valor JSON y determina su tipo según el primer carácter.
         *
         * @return valor convertido a su tipo Java correspondiente
         */
        Object valor() {
            saltarEspacios();
            char c = actual();

            // Identifica el tipo de valor según el carácter inicial.
            if (c == '{') return objeto();
            if (c == '[') return array();
            if (c == '"') return texto();

            // Interpreta los valores lógicos y los valores nulos.
            if (s.startsWith("true", pos)) { pos += 4; return Boolean.TRUE; }
            if (s.startsWith("false", pos)) { pos += 5; return Boolean.FALSE; }
            if (s.startsWith("null", pos)) { pos += 4; return null; }

            // Si no es ninguno de los anteriores, intenta leer un número.
            return numero();
        }

        /**
         * Lee un objeto JSON y almacena sus claves y valores en un mapa.
         * Utiliza LinkedHashMap para conservar el orden de inserción de las claves.
         *
         * @return mapa con las claves y los valores del objeto JSON
         */
        Map<String, Object> objeto() {
            Map<String, Object> m = new LinkedHashMap<>();

            pos++; // Consume la llave de apertura: {
            saltarEspacios();

            // Comprueba si el objeto está vacío.
            if (actual() == '}') { pos++; return m; }

            while (true) {
                saltarEspacios();

                // Lee la clave del elemento.
                String clave = texto();
                saltarEspacios();

                // Comprueba que la clave esté seguida de dos puntos.
                if (actual() != ':') throw new IllegalArgumentException("Se esperaba ':' en " + pos);
                pos++;

                // Lee el valor y lo asocia con su clave.
                m.put(clave, valor());

                saltarEspacios();
                char c = actual();
                pos++;

                // Finaliza el objeto o continúa leyendo sus elementos.
                if (c == '}') return m;
                if (c != ',') throw new IllegalArgumentException("Se esperaba ',' o '}' en " + (pos - 1));
            }
        }

        /**
         * Lee un array JSON y almacena sus elementos en una lista.
         *
         * @return lista con los valores del array JSON
         */
        List<Object> array() {
            List<Object> l = new ArrayList<>();

            pos++; // Consume el corchete de apertura: [
            saltarEspacios();

            // Comprueba si el array está vacío.
            if (actual() == ']') { pos++; return l; }

            while (true) {
                // Lee el siguiente elemento y lo añade a la lista.
                l.add(valor());

                saltarEspacios();
                char c = actual();
                pos++;

                // Finaliza el array o comprueba que haya una coma.
                if (c == ']') return l;
                if (c != ',') throw new IllegalArgumentException("Se esperaba ',' o ']' en " + (pos - 1));
            }
        }

        /**
         * Lee una cadena JSON y convierte sus secuencias de escape
         * en los caracteres correspondientes.
         *
         * @return texto obtenido de la cadena JSON
         */
        String texto() {
            // Comprueba que la cadena comience con comillas dobles.
            if (actual() != '"') throw new IllegalArgumentException("Se esperaba '\"' en " + pos);
            pos++;

            StringBuilder sb = new StringBuilder();

            while (true) {
                char c = actual();
                pos++;

                // Finaliza la lectura cuando encuentra las comillas de cierre.
                if (c == '"') return sb.toString();

                // Añade directamente los caracteres que no necesitan escape.
                if (c != '\\') { sb.append(c); continue; }

                // Lee el carácter que sigue a la barra invertida.
                char e = actual();
                pos++;

                // Convierte cada secuencia de escape en su carácter correspondiente.
                switch (e) {
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        // Convierte cuatro dígitos hexadecimales en un carácter Unicode.
                        sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                        pos += 4;
                    }
                    // Añade el carácter escapado en los demás casos.
                    default -> sb.append(e);
                }
            }
        }

        /**
         * Lee un número JSON y lo convierte en Long o Double según su formato.
         * Los números con decimales o notación científica se convierten en Double.
         *
         * @return número obtenido de la cadena JSON
         * @throws IllegalArgumentException si no se encuentra un número válido
         */
        Number numero() {
            // Guarda la posición inicial del número.
            int ini = pos;

            // Avanza mientras encuentre caracteres propios de un número.
            while (pos < s.length() && "+-0123456789.eE".indexOf(s.charAt(pos)) >= 0) pos++;

            String t = s.substring(ini, pos);

            // Comprueba que se haya encontrado algún carácter numérico.
            if (t.isEmpty()) throw new IllegalArgumentException("Valor JSON no válido en " + ini);

            // Elige el tipo numérico según la presencia de decimales o exponente.
            if (t.indexOf('.') >= 0 || t.indexOf('e') >= 0 || t.indexOf('E') >= 0) return Double.parseDouble(t);

            return Long.parseLong(t);
        }
    }
}