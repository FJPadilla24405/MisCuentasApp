package service.movimiento;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Cada cuánto se repite un movimiento recurrente: a diario, cada semana, cada mes o cada año. Un movimiento
 * recurrente es igual para ingresos y para gastos.
 *
 * Las fechas de las repeticiones se calculan siempre contando desde la fecha del primer movimiento y no desde la
 * repetición anterior: así un movimiento del día 31 cae el 28 en febrero, pero vuelve al 31 en marzo.
 *
 * @author Fco Padilla
 */
public enum Frecuencia {

    DIARIA("diaria", "Diaria"),
    SEMANAL("semanal", "Semanal"),
    MENSUAL("mensual", "Mensual"),
    ANUAL("anual", "Anual");

    private static final Locale ES = Locale.forLanguageTag("es-ES");

    private final String codigo;
    private final String etiqueta;

    Frecuencia(String codigo, String etiqueta) {
        this.codigo = codigo;
        this.etiqueta = etiqueta;
    }

    /**
     * @return Devuelve el texto que se guarda en la base de datos (diaria, semanal, mensual o anual)
     */
    public String getCodigo() {
        return codigo;
    }

    /**
     * @return Devuelve el nombre que se ve en la aplicación (Diaria, Semanal...)
     */
    public String getEtiqueta() {
        return etiqueta;
    }

    /**
     * @param codigo: texto guardado en la base de datos
     * @return Devuelve la frecuencia que le corresponde, o null si no es ninguna conocida
     */
    public static Frecuencia deCodigo(String codigo) {
        for (Frecuencia f : values()) {
            if (f.codigo.equalsIgnoreCase(codigo)) {
                return f;
            }
        }
        return null;
    }

    /**
     * Fecha de una repetición, con el día del mes que tenga el primer movimiento
     * @param inicio: fecha del primer movimiento (la repetición 0)
     * @param n: número de repetición (1 es la primera que se genera sola)
     * @return Devuelve la fecha de esa repetición
     */
    public LocalDate fechaDe(LocalDate inicio, long n) {
        return fechaDe(inicio, inicio.getDayOfMonth(), n);
    }

    /**
     * Fecha de una repetición. En la mensual y la anual el día del mes es el de la regla (no el del primer movimiento
     * que quede), para que borrar el original no mueva las fechas: un día 31 sigue siendo día 31 los meses que lo tienen.
     * @param inicio: fecha del primer movimiento (la repetición 0)
     * @param diaDelMes: día del mes de la regla (columna dia_ejecucion); si no es válido se usa el de inicio
     * @param n: número de repetición (1 es la primera que se genera sola)
     * @return Devuelve la fecha de esa repetición
     */
    public LocalDate fechaDe(LocalDate inicio, int diaDelMes, long n) {
        int dia = diaDelMes >= 1 && diaDelMes <= 31 ? diaDelMes : inicio.getDayOfMonth();
        return switch (this) {
            case DIARIA -> inicio.plusDays(n);
            case SEMANAL -> inicio.plusWeeks(n);
            case MENSUAL -> enMes(YearMonth.from(inicio).plusMonths(n), dia);
            case ANUAL -> enMes(YearMonth.of(inicio.getYear(), inicio.getMonth()).plusYears(n), dia);
        };
    }

    /**
     * @param mes: mes y año
     * @param dia: día del mes deseado
     * @return Devuelve ese día del mes o, si el mes es más corto, su último día
     */
    private static LocalDate enMes(YearMonth mes, int dia) {
        return mes.atDay(Math.min(dia, mes.lengthOfMonth()));
    }

    /**
     * Valor que se guarda en la columna dia_ejecucion de la regla: nada en la diaria, el día de la semana (1 lunes
     * a 7 domingo) en la semanal y el día del mes en la mensual y la anual.
     * @param fecha: fecha del primer movimiento
     * @return Devuelve el día de ejecución
     */
    public int diaEjecucion(LocalDate fecha) {
        return switch (this) {
            case DIARIA -> 0;
            case SEMANAL -> fecha.getDayOfWeek().getValue();
            case MENSUAL, ANUAL -> fecha.getDayOfMonth();
        };
    }

    /**
     * Explica en una frase cuándo se repite, para enseñarlo en la ventana del movimiento
     * @param fecha: fecha del movimiento, o null si todavía no se ha escrito una válida
     * @return Devuelve, por ejemplo, "cada mes, el día 14" o "cada semana, los jueves"
     */
    public String descripcion(LocalDate fecha) {
        if (fecha == null) {
            return switch (this) {
                case DIARIA -> "cada día";
                case SEMANAL -> "cada semana";
                case MENSUAL -> "cada mes";
                case ANUAL -> "cada año";
            };
        }
        return switch (this) {
            case DIARIA -> "cada día";
            case SEMANAL -> "cada semana, los " + plural(fecha.getDayOfWeek());
            case MENSUAL -> fecha.getDayOfMonth() > 28
                    ? "cada mes, el día " + fecha.getDayOfMonth() + " (o el último día si el mes es más corto)"
                    : "cada mes, el día " + fecha.getDayOfMonth();
            case ANUAL -> "cada año, el " + fecha.getDayOfMonth() + " de " + fecha.getMonth().getDisplayName(TextStyle.FULL, ES);
        };
    }

    /**
     * @param dia: día de la semana
     * @return Devuelve el nombre del día en plural: lunes, martes... sábados, domingos
     */
    private static String plural(DayOfWeek dia) {
        String nombre = dia.getDisplayName(TextStyle.FULL, ES);
        return nombre.endsWith("s") ? nombre : nombre + "s";
    }
}
