package service.sync;
 
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
 
/**
 * Lector/escritor de JSON mínimo, sin dependencias externas.
 * Tipos usados: Map (objeto), List (array), String, Long, Double, Boolean y null.
 */
final class Json {
 
    private Json() {
    }
 
    // ---------- Escritura ----------
 
    static String escribir(Object valor) {
        StringBuilder sb = new StringBuilder();
        escribir(valor, sb);
        return sb.toString();
    }
 
    private static void escribir(Object v, StringBuilder sb) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String s) {
            escribirTexto(s, sb);
        } else if (v instanceof Boolean || v instanceof Integer || v instanceof Long) {
            sb.append(v);
        } else if (v instanceof Number n) {
            double d = n.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                throw new IllegalArgumentException("Número no válido para JSON: " + d);
            }
            sb.append(d);
        } else if (v instanceof Map<?, ?> m) {
            sb.append('{');
            boolean primero = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!primero) sb.append(',');
                primero = false;
                escribirTexto(String.valueOf(e.getKey()), sb);
                sb.append(':');
                escribir(e.getValue(), sb);
            }
            sb.append('}');
        } else if (v instanceof Iterable<?> it) {
            sb.append('[');
            boolean primero = true;
            for (Object o : it) {
                if (!primero) sb.append(',');
                primero = false;
                escribir(o, sb);
            }
            sb.append(']');
        } else {
            throw new IllegalArgumentException("Tipo no soportado en JSON: " + v.getClass());
        }
    }
 
    private static void escribirTexto(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }
 
    // ---------- Lectura ----------
 
    static Object leer(String json) {
        Lector l = new Lector(json);
        l.saltarEspacios();
        Object v = l.valor();
        l.saltarEspacios();
        if (l.pos != json.length()) {
            throw new IllegalArgumentException("JSON con texto sobrante en la posición " + l.pos);
        }
        return v;
    }
 
    private static final class Lector {
        final String s;
        int pos;
 
        Lector(String s) {
            this.s = s;
        }
 
        void saltarEspacios() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        }
 
        char actual() {
            if (pos >= s.length()) throw new IllegalArgumentException("JSON incompleto");
            return s.charAt(pos);
        }
 
        Object valor() {
            saltarEspacios();
            char c = actual();
            if (c == '{') return objeto();
            if (c == '[') return array();
            if (c == '"') return texto();
            if (s.startsWith("true", pos)) { pos += 4; return Boolean.TRUE; }
            if (s.startsWith("false", pos)) { pos += 5; return Boolean.FALSE; }
            if (s.startsWith("null", pos)) { pos += 4; return null; }
            return numero();
        }
 
        Map<String, Object> objeto() {
            Map<String, Object> m = new LinkedHashMap<>();
            pos++; // {
            saltarEspacios();
            if (actual() == '}') { pos++; return m; }
            while (true) {
                saltarEspacios();
                String clave = texto();
                saltarEspacios();
                if (actual() != ':') throw new IllegalArgumentException("Se esperaba ':' en " + pos);
                pos++;
                m.put(clave, valor());
                saltarEspacios();
                char c = actual();
                pos++;
                if (c == '}') return m;
                if (c != ',') throw new IllegalArgumentException("Se esperaba ',' o '}' en " + (pos - 1));
            }
        }
 
        List<Object> array() {
            List<Object> l = new ArrayList<>();
            pos++; // [
            saltarEspacios();
            if (actual() == ']') { pos++; return l; }
            while (true) {
                l.add(valor());
                saltarEspacios();
                char c = actual();
                pos++;
                if (c == ']') return l;
                if (c != ',') throw new IllegalArgumentException("Se esperaba ',' o ']' en " + (pos - 1));
            }
        }
 
        String texto() {
            if (actual() != '"') throw new IllegalArgumentException("Se esperaba '\"' en " + pos);
            pos++;
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = actual();
                pos++;
                if (c == '"') return sb.toString();
                if (c != '\\') { sb.append(c); continue; }
                char e = actual();
                pos++;
                switch (e) {
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                        pos += 4;
                    }
                    default -> sb.append(e); // \" \\ \/
                }
            }
        }
 
        Number numero() {
            int ini = pos;
            while (pos < s.length() && "+-0123456789.eE".indexOf(s.charAt(pos)) >= 0) pos++;
            String t = s.substring(ini, pos);
            if (t.isEmpty()) throw new IllegalArgumentException("Valor JSON no válido en " + ini);
            if (t.indexOf('.') >= 0 || t.indexOf('e') >= 0 || t.indexOf('E') >= 0) return Double.parseDouble(t);
            return Long.parseLong(t);
        }
    }
}
