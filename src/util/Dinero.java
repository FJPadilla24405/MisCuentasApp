package util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Punto único de conversión de dinero entre Java (BigDecimal) y SQLite (columnas DECIMAL(12,2)).
 *
 * @author Fco Padilla
 */
public final class Dinero {

    /**
     * Decimales de un importe.
     */
    static final int ESCALA = 2;

    /**
     * Cifras totales permitidas por DECIMAL(12,2): 10 enteras + 2 decimales.
     */
    static final int MAX_CIFRAS = 12;

    /**
     * 0.00, para devolver cuando no hay filas o el valor es NULL.
     */
    public static final BigDecimal CERO = BigDecimal.ZERO.setScale(ESCALA);

    // Constructor base vacío
    private Dinero() {
    }

    /**
     * Lee una columna monetaria como BigDecimal con 2 decimales. Un NULL se lee como 0.00.
     * El redondeo también limpia los restos de coma flotante de resultados de SUM().
     * @param rs: resultado de la consulta
     * @param columna: columna donde está el campo
     * @return Devuelve el valor del decimal en bigdecimal
     * @throws SQLException
     */
    public static BigDecimal leer(ResultSet rs, String columna) throws SQLException {
        BigDecimal valor = rs.getBigDecimal(columna);
        return valor == null ? CERO : valor.setScale(ESCALA, RoundingMode.HALF_UP);
    }

    /**
     * Guarda un importe en el parámetro indicado, ya redondeado a 2 decimales y comprobado.
     * @param ps: sentencia sql preparada
     * @param indice:
     * @param valor: valor del importe
     * @throws SQLException
     */
    public static void escribir(PreparedStatement ps, int indice, BigDecimal valor) throws SQLException {
        ps.setBigDecimal(indice, normalizar(valor));
    }

    /**
     * Redondea a 2 decimales (HALF_UP) y comprueba que cabe en DECIMAL(12,2).
     * @param valor: valor para normalizar
     * @return Devuelve el valor redondeado
     * @throws IllegalArgumentException si el importe es null o tiene demasiadas cifras
     */
    public static BigDecimal normalizar(BigDecimal valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El importe no puede ser null");
        }
        BigDecimal redondeado = valor.setScale(ESCALA, RoundingMode.HALF_UP);
        if (redondeado.precision() > MAX_CIFRAS) {
            throw new IllegalArgumentException("Importe fuera de rango para DECIMAL(12,2): " + valor.toPlainString());
        }
        return redondeado;
    }
}