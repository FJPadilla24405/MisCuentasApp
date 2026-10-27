package service.auth;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Protege la contraseña que se guarda en la base de datos local. No se guarda la contraseña ni una
 * versión que se pueda descifrar: se guarda una huella (hash) con sal aleatoria calculada con PBKDF2
 * (HMAC-SHA256) y muchas iteraciones, para que probar contraseñas contra una copia de la base de datos
 * sea muy lento. Para comprobar una contraseña se calcula su huella con la misma sal y se compara.
 *
 * Formato guardado en usuarios.password_hash: pbkdf2-sha256$iteraciones$sal$huella (sal y huella en Base64).
 * Las iteraciones van dentro del texto, así que se pueden subir en el futuro sin invalidar las huellas antiguas.
 *
 * @author Fco Padilla
 */
public final class ContrasenaLocal {

    // Algoritmo y prefijo que identifica el formato
    private static final String ALGORITMO = "PBKDF2WithHmacSHA256";
    private static final String PREFIJO = "pbkdf2-sha256";
    // Iteraciones para las huellas nuevas (recomendación de OWASP para PBKDF2-HMAC-SHA256)
    private static final int ITERACIONES = 600_000;
    // Bytes de sal aleatoria y bits de la huella
    private static final int BYTES_SAL = 16;
    private static final int BITS_HUELLA = 256;

    private static final SecureRandom ALEATORIO = new SecureRandom();

    /**
     * Constructor base vacío
     */
    private ContrasenaLocal() {
    }

    /**
     * Calcula la huella de una contraseña con una sal nueva. Tarda unas décimas de segundo a propósito,
     * así que no hay que llamarlo desde la hebra de JavaFX.
     * @param password: contraseña en claro
     * @return Devuelve el texto que se guarda en la base de datos
     */
    public static String hashear(String password) {
        byte[] sal = new byte[BYTES_SAL];
        ALEATORIO.nextBytes(sal);
        byte[] huella = calcular(password, sal, ITERACIONES, BITS_HUELLA);
        return PREFIJO + "$" + ITERACIONES + "$"
                + Base64.getEncoder().encodeToString(sal) + "$"
                + Base64.getEncoder().encodeToString(huella);
    }

    /**
     * Comprueba si una contraseña corresponde a una huella guardada. Tarda unas décimas de segundo.
     * @param password: contraseña escrita por el usuario
     * @param guardado: texto guardado en la base de datos (puede ser null si la cuenta no tiene)
     * @return Devuelve true si coincide; false si no coincide o si el texto guardado no es válido
     */
    public static boolean verificar(String password, String guardado) {
        if (password == null || guardado == null) {
            return false;
        }
        String[] partes = guardado.split("\\$");
        if (partes.length != 4 || !PREFIJO.equals(partes[0])) {
            return false;
        }
        try {
            int iteraciones = Integer.parseInt(partes[1]);
            byte[] sal = Base64.getDecoder().decode(partes[2]);
            byte[] esperada = Base64.getDecoder().decode(partes[3]);
            byte[] obtenida = calcular(password, sal, iteraciones, esperada.length * 8);
            // Comparación en tiempo constante, para que el tiempo no delate cuántos bytes coinciden
            return MessageDigest.isEqual(esperada, obtenida);
        } catch (IllegalArgumentException e) {
            // Número o Base64 mal formados: el texto guardado no es válido
            return false;
        }
    }

    /**
     * Calcula PBKDF2 con los parámetros dados
     * @param password: contraseña en claro
     * @param sal: sal aleatoria
     * @param iteraciones: número de iteraciones
     * @param bits: longitud de la huella en bits
     * @return Devuelve los bytes de la huella
     */
    private static byte[] calcular(String password, byte[] sal, int iteraciones, int bits) {
        PBEKeySpec especificacion = new PBEKeySpec(password.toCharArray(), sal, iteraciones, bits);
        try {
            return SecretKeyFactory.getInstance(ALGORITMO).generateSecret(especificacion).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("No se pudo calcular la huella de la contraseña", e);
        } finally {
            especificacion.clearPassword();
        }
    }
}
