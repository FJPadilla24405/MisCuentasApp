package service.importar;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Lee un archivo de texto con valores separados (CSV). Averigua solo cómo está escrito: la codificación (UTF-8, UTF-16
 * o Windows-1252, que es la de los CSV que se guardan en Excel en español), el separador (punto y coma, coma,
 * tabulador o barra vertical) y las comillas. Todas las celdas se devuelven como texto.
 *
 * @author Fco Padilla + Claude
 */
final class LectorCsv {

    private static final char[] SEPARADORES = { ';', ',', '\t', '|' };
    // Cuántas líneas se miran para adivinar el separador
    private static final int LINEAS_MUESTRA = 30;

    // No se instancia
    private LectorCsv() {
    }

    /**
     * Convierte el contenido de un archivo de texto en filas de celdas
     * @param datos: contenido del archivo
     * @return Devuelve las filas, sin las filas totalmente vacías
     * @throws ArchivoNoValido si no es un archivo de texto
     */
    static List<List<Object>> leer(byte[] datos) throws ArchivoNoValido {
        String texto = decodificar(datos);
        char separador = adivinarSeparador(texto);
        List<List<Object>> filas = new ArrayList<>();
        for (List<String> fila : trocear(texto, separador)) {
            int ultima = fila.size() - 1;
            while (ultima >= 0 && fila.get(ultima).isEmpty()) {
                ultima--;
            }
            if (ultima < 0) {
                continue;
            }
            List<Object> celdas = new ArrayList<>(ultima + 1);
            for (int i = 0; i <= ultima; i++) {
                String celda = fila.get(i);
                celdas.add(celda.isEmpty() ? null : celda);
            }
            filas.add(celdas);
        }
        return filas;
    }

    /**
     * Pasa los bytes a texto: respeta la marca del principio (BOM) si la hay y, si no, prueba UTF-8 y, si no es UTF-8
     * válido, Windows-1252.
     */
    private static String decodificar(byte[] datos) throws ArchivoNoValido {
        for (byte b : datos) {
            // Un byte cero en un archivo de texto en 8 bits significa que es un archivo binario (una imagen, un zip...)
            if (b == 0 && !empiezaPorBomUtf16(datos)) {
                throw new ArchivoNoValido("No parece un archivo de texto ni una hoja de Excel. Prueba con un CSV, un .xls o un .xlsx.");
            }
        }
        if (datos.length >= 3 && (datos[0] & 0xFF) == 0xEF && (datos[1] & 0xFF) == 0xBB && (datos[2] & 0xFF) == 0xBF) {
            return new String(datos, 3, datos.length - 3, StandardCharsets.UTF_8);
        }
        if (empiezaPorBomUtf16(datos)) {
            boolean grande = (datos[0] & 0xFF) == 0xFE;
            return new String(datos, 2, datos.length - 2, grande ? StandardCharsets.UTF_16BE : StandardCharsets.UTF_16LE);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(datos)).toString();
        } catch (CharacterCodingException e) {
            return new String(datos, Charset.forName("windows-1252"));
        }
    }

    private static boolean empiezaPorBomUtf16(byte[] datos) {
        return datos.length >= 2 && (((datos[0] & 0xFF) == 0xFF && (datos[1] & 0xFF) == 0xFE)
                || ((datos[0] & 0xFF) == 0xFE && (datos[1] & 0xFF) == 0xFF));
    }

    /**
     * Elige el separador que más se repite, y igual, en las primeras líneas (fuera de las comillas)
     */
    private static char adivinarSeparador(String texto) {
        List<int[]> cuentas = new ArrayList<>();
        int[] actual = new int[SEPARADORES.length];
        boolean comillas = false;
        int lineas = 0;
        for (int i = 0; i < texto.length() && lineas < LINEAS_MUESTRA; i++) {
            char c = texto.charAt(i);
            if (c == '"') {
                comillas = !comillas;
            } else if (!comillas && (c == '\n' || c == '\r')) {
                if (c == '\r' && i + 1 < texto.length() && texto.charAt(i + 1) == '\n') {
                    i++;
                }
                cuentas.add(actual);
                actual = new int[SEPARADORES.length];
                lineas++;
            } else if (!comillas) {
                for (int s = 0; s < SEPARADORES.length; s++) {
                    if (c == SEPARADORES[s]) {
                        actual[s]++;
                    }
                }
            }
        }
        cuentas.add(actual);

        char mejor = ';';
        int mejorPuntos = 0;
        for (int s = 0; s < SEPARADORES.length; s++) {
            // Un buen separador aparece en muchas líneas y casi siempre el mismo número de veces
            java.util.Map<Integer, Integer> repeticiones = new java.util.HashMap<>();
            int conSeparador = 0;
            for (int[] linea : cuentas) {
                if (linea[s] > 0) {
                    conSeparador++;
                    repeticiones.merge(linea[s], 1, Integer::sum);
                }
            }
            int masFrecuente = repeticiones.values().stream().mapToInt(Integer::intValue).max().orElse(0);
            int puntos = masFrecuente * 1000 + conSeparador;
            if (puntos > mejorPuntos) {
                mejorPuntos = puntos;
                mejor = SEPARADORES[s];
            }
        }
        return mejor;
    }

    /**
     * Separa el texto en filas y celdas. Las comillas dobles protegen separadores y saltos de línea, y dos comillas
     * seguidas dentro de unas comillas son una comilla.
     */
    private static List<List<String>> trocear(String texto, char separador) {
        List<List<String>> filas = new ArrayList<>();
        List<String> fila = new ArrayList<>();
        StringBuilder celda = new StringBuilder();
        boolean comillas = false;
        boolean habiaComillas = false;
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (comillas) {
                if (c == '"') {
                    if (i + 1 < texto.length() && texto.charAt(i + 1) == '"') {
                        celda.append('"');
                        i++;
                    } else {
                        comillas = false;
                    }
                } else {
                    celda.append(c);
                }
            } else if (c == '"' && celda.toString().isBlank()) {
                comillas = true;
                habiaComillas = true;
                celda.setLength(0);
            } else if (c == separador) {
                fila.add(cerrarCelda(celda, habiaComillas));
                habiaComillas = false;
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < texto.length() && texto.charAt(i + 1) == '\n') {
                    i++;
                }
                fila.add(cerrarCelda(celda, habiaComillas));
                habiaComillas = false;
                filas.add(fila);
                fila = new ArrayList<>();
            } else {
                celda.append(c);
            }
        }
        if (celda.length() > 0 || !fila.isEmpty() || habiaComillas) {
            fila.add(cerrarCelda(celda, habiaComillas));
            filas.add(fila);
        }
        return filas;
    }

    private static String cerrarCelda(StringBuilder celda, boolean entreComillas) {
        // Se recortan los espacios también en las celdas entre comillas: en un extracto no significan nada
        String texto = celda.toString();
        celda.setLength(0);
        return texto.trim();
    }
}
