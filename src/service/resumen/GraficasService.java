package service.resumen;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

import models.Categoria;
import models.Cuenta;
import models.Movimiento;
import models.Usuario;
import service.dao.CategoriaDAO;
import service.dao.CuentaDAO;
import service.dao.MovimientoDAO;
import util.Dinero;
import util.Formato;

/**
 * Calcula los datos del apartado Gráficas y resúmenes. Tiene dos vistas: la mensual (semanas, gasto por
 * categoría y tabla cuenta por cuenta) y la anual (mes a mes y balance acumulado). En las dos se puede
 * resumir todas las cuentas activas o solo una.
 *
 * @author Fco Padilla
 */
public class GraficasService {

    // Número de tramos en los que se reparte un mes (días 1-7, 8-14, 15-21 y 22 hasta el final)
    public static final int SEMANAS = 4;
    // Días de cada tramo menos el último, que llega hasta el final del mes
    private static final int DIAS_SEMANA = 7;
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    /**
     * Un tramo del mes con lo ingresado y lo gastado en él.
     * @param etiqueta: texto del tramo, por ejemplo "1–7 sept"
     * @param ingresos: suma de los ingresos del tramo
     * @param gastos: suma de los gastos del tramo
     */
    public record Semana(String etiqueta, BigDecimal ingresos, BigDecimal gastos) {
    }

    /**
     * Lo gastado en una categoría.
     * @param categoria: nombre de la categoría
     * @param importe: suma de sus gastos
     */
    public record Porcion(String categoria, BigDecimal importe) {
    }

    /**
     * Una cuenta con lo que se ha movido en el mes.
     * @param cuenta: la cuenta
     * @param saldoInicial: saldo al empezar el mes
     * @param ingresos: ingresos del mes
     * @param gastos: gastos del mes
     */
    public record FilaCuentaMes(Cuenta cuenta, BigDecimal saldoInicial, BigDecimal ingresos, BigDecimal gastos) {

        /**
         * @return Devuelve ingresos menos gastos del mes
         */
        public BigDecimal balance() {
            return ingresos.subtract(gastos);
        }

        /**
         * @return Devuelve el saldo al terminar el mes (saldo inicial más balance)
         */
        public BigDecimal saldoFinal() {
            return saldoInicial.add(balance());
        }
    }

    /**
     * Todo lo que necesita la vista mensual.
     * @param mes: mes resumido
     * @param ingresos: suma de los ingresos del mes
     * @param gastos: suma de los gastos del mes
     * @param cuentasConIngresos: nombres de las cuentas que han recibido algún ingreso en el mes
     * @param gastosMesAnterior: suma de los gastos del mes anterior
     * @param movimientosMesAnterior: cuántos movimientos hubo el mes anterior (0 si no hay datos con los que comparar)
     * @param numMovimientos: cuántos movimientos hay en el mes
     * @param semanas: ingresos y gastos por tramo del mes (siempre SEMANAS tramos)
     * @param categorias: gasto por categoría, de mayor a menor
     * @param cuentas: una fila por cuenta
     * @param meses: meses que se pueden elegir (con movimientos, más el actual y el elegido), del más reciente al más antiguo
     */
    public record Mensual(YearMonth mes, BigDecimal ingresos, BigDecimal gastos, List<String> cuentasConIngresos,
            BigDecimal gastosMesAnterior, int movimientosMesAnterior, int numMovimientos, List<Semana> semanas,
            List<Porcion> categorias, List<FilaCuentaMes> cuentas, List<YearMonth> meses) {

        /**
         * @return Devuelve ingresos menos gastos del mes
         */
        public BigDecimal balance() {
            return ingresos.subtract(gastos);
        }

        /**
         * @return Devuelve el porcentaje de los ingresos que se ha ahorrado (puede ser negativo), o null si no hay ingresos
         */
        public BigDecimal tasaAhorro() {
            return GraficasService.tasa(balance(), ingresos);
        }

        /**
         * @return Devuelve la suma de los saldos iniciales de las cuentas
         */
        public BigDecimal saldoInicialTotal() {
            return cuentas.stream().map(FilaCuentaMes::saldoInicial).reduce(Dinero.CERO, BigDecimal::add);
        }

        /**
         * @return Devuelve la suma de los saldos al terminar el mes
         */
        public BigDecimal saldoFinalTotal() {
            return cuentas.stream().map(FilaCuentaMes::saldoFinal).reduce(Dinero.CERO, BigDecimal::add);
        }
    }

    /**
     * Un mes dentro de la vista anual.
     * @param mes: mes y año
     * @param ingresos: suma de los ingresos del mes
     * @param gastos: suma de los gastos del mes
     * @param movimientos: cuántos movimientos tiene el mes
     */
    public record FilaMes(YearMonth mes, BigDecimal ingresos, BigDecimal gastos, int movimientos) {

        /**
         * @return Devuelve ingresos menos gastos del mes
         */
        public BigDecimal balance() {
            return ingresos.subtract(gastos);
        }

        /**
         * @return Devuelve el porcentaje de los ingresos que se ha ahorrado (puede ser negativo), o null si no hay ingresos
         */
        public BigDecimal tasaAhorro() {
            return GraficasService.tasa(balance(), ingresos);
        }
    }

    /**
     * Todo lo que necesita la vista anual.
     * @param anio: año resumido
     * @param ingresos: suma de los ingresos del año
     * @param gastos: suma de los gastos del año
     * @param saldoInicial: saldo de las cuentas el 1 de enero
     * @param meses: de enero hasta el último mes que se cuenta (el actual, o diciembre en años pasados)
     * @param mesesDisponibles: meses que se pueden elegir (con movimientos, más el actual y enero del año elegido), del más reciente al más antiguo
     */
    public record Anual(int anio, BigDecimal ingresos, BigDecimal gastos, BigDecimal saldoInicial,
            List<FilaMes> meses, List<YearMonth> mesesDisponibles) {

        /**
         * @return Devuelve ingresos menos gastos del año
         */
        public BigDecimal balance() {
            return ingresos.subtract(gastos);
        }

        /**
         * @return Devuelve el porcentaje de los ingresos que se ha ahorrado (puede ser negativo), o null si no hay ingresos
         */
        public BigDecimal tasaAhorro() {
            return GraficasService.tasa(balance(), ingresos);
        }

        /**
         * @return Devuelve cuántos meses entran en el resumen
         */
        public int numMeses() {
            return meses.size();
        }

        /**
         * @return Devuelve lo que se gasta de media al mes (cero si no hay meses)
         */
        public BigDecimal mediaGastos() {
            if (meses.isEmpty()) {
                return Dinero.CERO;
            }
            return gastos.divide(BigDecimal.valueOf(meses.size()), 2, RoundingMode.HALF_UP);
        }

        /**
         * @return Devuelve el mes con más ahorro (balance positivo más alto; el más reciente si hay empate), o vacío si ninguno ahorra
         */
        public Optional<FilaMes> mejorMes() {
            FilaMes mejor = null;
            for (FilaMes f : meses) {
                if (f.movimientos() > 0 && f.balance().signum() > 0
                        && (mejor == null || f.balance().compareTo(mejor.balance()) >= 0)) {
                    mejor = f;
                }
            }
            return Optional.ofNullable(mejor);
        }

        /**
         * @return Devuelve el balance acumulado al terminar cada mes (el mismo número de valores que meses)
         */
        public List<BigDecimal> acumulado() {
            List<BigDecimal> valores = new ArrayList<>();
            BigDecimal suma = Dinero.CERO;
            for (FilaMes f : meses) {
                suma = suma.add(f.balance());
                valores.add(suma);
            }
            return valores;
        }
    }

    // Lo que se va sumando de cada cuenta mientras se recorren los movimientos
    private static final class Acumulado {
        private BigDecimal previo;
        private BigDecimal ingresos = Dinero.CERO;
        private BigDecimal gastos = Dinero.CERO;

        private Acumulado(BigDecimal saldoInicial) {
            this.previo = saldoInicial == null ? Dinero.CERO : saldoInicial;
        }
    }

    /**
     * Deja como mucho un número de trozos: si hay más categorías, las últimas se suman en una llamada "Otras"
     * @param categorias: gasto por categoría, de mayor a menor
     * @param max: máximo de trozos (el último es "Otras" si hay que agrupar)
     * @return Devuelve los trozos del anillo
     */
    public static List<Porcion> agrupar(List<Porcion> categorias, int max) {
        if (categorias.size() <= max) {
            return categorias;
        }
        List<Porcion> trozos = new ArrayList<>(categorias.subList(0, max - 1));
        BigDecimal resto = Dinero.CERO;
        for (Porcion p : categorias.subList(max - 1, categorias.size())) {
            resto = resto.add(p.importe());
        }
        trozos.add(new Porcion("Otras", resto));
        return trozos;
    }

    private final CuentaDAO cuentaDAO = new CuentaDAO();
    private final MovimientoDAO movimientoDAO = new MovimientoDAO();
    private final CategoriaDAO categoriaDAO = new CategoriaDAO();

    /**
     * Calcula la vista mensual. Solo cuentan las cuentas activas; los movimientos de una cuenta dada de baja no se suman.
     * @param usuario: dueño de las cuentas
     * @param cuenta: la cuenta a resumir, o null para sumar todas las cuentas activas
     * @param mes: mes y año
     * @return Devuelve los datos listos para pintar
     */
    public Mensual mensual(Usuario usuario, Cuenta cuenta, YearMonth mes) {
        Map<Integer, Acumulado> porCuenta = new LinkedHashMap<>();
        List<Cuenta> ambito = cuentasDelAmbito(usuario, cuenta);
        for (Cuenta c : ambito) {
            porCuenta.put(c.getId(), new Acumulado(c.getSaldoInicial()));
        }
        Map<Integer, String> nombresCategoria = nombresDeCategorias(usuario);

        TreeSet<YearMonth> meses = new TreeSet<>(Comparator.reverseOrder());
        meses.add(YearMonth.now());
        meses.add(mes);

        String prefijoMes = mes.toString();
        String prefijoAnterior = mes.minusMonths(1).toString();
        BigDecimal[] semanaIngresos = ceros(SEMANAS);
        BigDecimal[] semanaGastos = ceros(SEMANAS);
        Map<String, BigDecimal> porCategoria = new HashMap<>();
        BigDecimal gastosAnterior = Dinero.CERO;
        int movimientosAnterior = 0;
        int numMovimientos = 0;

        for (Movimiento m : movimientoDAO.listarPorUsuario(usuario.getId())) {
            Acumulado a = porCuenta.get(m.getCuentaId());
            String fecha = m.getFecha();
            if (a == null || fecha == null || fecha.length() < 10) {
                continue;
            }
            anotarMes(meses, fecha);
            boolean ingreso = "ingreso".equals(m.getTipo());
            BigDecimal importe = m.getImporte();
            String ym = fecha.substring(0, 7);
            int orden = ym.compareTo(prefijoMes);

            if (orden < 0) {
                // Todo lo anterior al mes forma el saldo con el que empieza
                a.previo = a.previo.add(ingreso ? importe : importe.negate());
            }
            if (ym.equals(prefijoAnterior)) {
                movimientosAnterior++;
                if (!ingreso) {
                    gastosAnterior = gastosAnterior.add(importe);
                }
            }
            if (orden != 0) {
                continue;
            }

            numMovimientos++;
            int semana = semanaDe(fecha);
            if (ingreso) {
                a.ingresos = a.ingresos.add(importe);
                if (semana >= 0) {
                    semanaIngresos[semana] = semanaIngresos[semana].add(importe);
                }
            } else {
                a.gastos = a.gastos.add(importe);
                if (semana >= 0) {
                    semanaGastos[semana] = semanaGastos[semana].add(importe);
                }
                porCategoria.merge(nombresCategoria.getOrDefault(m.getCategoriaId(), "Sin categoría"), importe, BigDecimal::add);
            }
        }

        BigDecimal ingresos = Dinero.CERO;
        BigDecimal gastos = Dinero.CERO;
        List<String> conIngresos = new ArrayList<>();
        List<FilaCuentaMes> filas = new ArrayList<>();
        for (Cuenta c : ambito) {
            Acumulado a = porCuenta.get(c.getId());
            filas.add(new FilaCuentaMes(c, a.previo, a.ingresos, a.gastos));
            ingresos = ingresos.add(a.ingresos);
            gastos = gastos.add(a.gastos);
            if (a.ingresos.signum() > 0) {
                conIngresos.add(c.getNombre());
            }
        }

        List<Semana> semanas = new ArrayList<>();
        for (int i = 0; i < SEMANAS; i++) {
            semanas.add(new Semana(etiquetaSemana(mes, i), semanaIngresos[i], semanaGastos[i]));
        }

        List<Porcion> categorias = new ArrayList<>();
        porCategoria.forEach((nombre, importe) -> categorias.add(new Porcion(nombre, importe)));
        categorias.sort(Comparator.comparing(Porcion::importe).reversed().thenComparing(Porcion::categoria));

        return new Mensual(mes, ingresos, gastos, conIngresos, gastosAnterior, movimientosAnterior, numMovimientos,
                semanas, categorias, filas, new ArrayList<>(meses));
    }

    /**
     * Calcula la vista anual: lo ingresado y gastado cada mes desde enero. En el año actual llega hasta el mes
     * actual (o hasta el último mes con movimientos, si hay alguno posterior); en un año pasado, hasta diciembre.
     * @param usuario: dueño de las cuentas
     * @param cuenta: la cuenta a resumir, o null para sumar todas las cuentas activas
     * @param anio: año a resumir
     * @return Devuelve los datos listos para pintar
     */
    public Anual anual(Usuario usuario, Cuenta cuenta, int anio) {
        List<Cuenta> ambito = cuentasDelAmbito(usuario, cuenta);
        Map<Integer, Cuenta> porId = new HashMap<>();
        BigDecimal saldoInicial = Dinero.CERO;
        for (Cuenta c : ambito) {
            porId.put(c.getId(), c);
            saldoInicial = saldoInicial.add(c.getSaldoInicial() == null ? Dinero.CERO : c.getSaldoInicial());
        }

        TreeSet<YearMonth> disponibles = new TreeSet<>(Comparator.reverseOrder());
        disponibles.add(YearMonth.now());
        disponibles.add(YearMonth.of(anio, 1));

        String inicioAnio = String.format("%04d-01", anio);
        String prefijoAnio = String.format("%04d-", anio);
        BigDecimal[] ingresosMes = ceros(12);
        BigDecimal[] gastosMes = ceros(12);
        int[] movimientosMes = new int[12];
        int ultimoConDatos = 0;

        for (Movimiento m : movimientoDAO.listarPorUsuario(usuario.getId())) {
            String fecha = m.getFecha();
            if (!porId.containsKey(m.getCuentaId()) || fecha == null || fecha.length() < 10) {
                continue;
            }
            anotarMes(disponibles, fecha);
            boolean ingreso = "ingreso".equals(m.getTipo());
            BigDecimal importe = m.getImporte();
            String ym = fecha.substring(0, 7);
            if (ym.compareTo(inicioAnio) < 0) {
                saldoInicial = saldoInicial.add(ingreso ? importe : importe.negate());
            } else if (ym.startsWith(prefijoAnio)) {
                int indice = mesDe(ym);
                if (indice < 0) {
                    continue;
                }
                movimientosMes[indice]++;
                ultimoConDatos = Math.max(ultimoConDatos, indice + 1);
                if (ingreso) {
                    ingresosMes[indice] = ingresosMes[indice].add(importe);
                } else {
                    gastosMes[indice] = gastosMes[indice].add(importe);
                }
            }
        }

        YearMonth hoy = YearMonth.now();
        int ultimoMes;
        if (anio == hoy.getYear()) {
            ultimoMes = Math.max(hoy.getMonthValue(), ultimoConDatos);
        } else {
            ultimoMes = 12;
        }

        BigDecimal ingresos = Dinero.CERO;
        BigDecimal gastos = Dinero.CERO;
        List<FilaMes> meses = new ArrayList<>();
        for (int i = 0; i < ultimoMes; i++) {
            meses.add(new FilaMes(YearMonth.of(anio, i + 1), ingresosMes[i], gastosMes[i], movimientosMes[i]));
            ingresos = ingresos.add(ingresosMes[i]);
            gastos = gastos.add(gastosMes[i]);
        }
        return new Anual(anio, ingresos, gastos, saldoInicial, meses, new ArrayList<>(disponibles));
    }

    /**
     * Porcentaje del balance sobre los ingresos con un decimal (puede ser negativo)
     * @param balance: ingresos menos gastos
     * @param ingresos: ingresos
     * @return Devuelve el porcentaje, o null si no hay ingresos
     */
    private static BigDecimal tasa(BigDecimal balance, BigDecimal ingresos) {
        if (ingresos.signum() <= 0) {
            return null;
        }
        return balance.multiply(CIEN).divide(ingresos, 1, RoundingMode.HALF_UP);
    }

    /** 
     * @param usuario: dueño de las cuentas
     * @param cuenta: la cuenta elegida, o null para todas
     * @return Devuelve las cuentas activas que entran en el resumen
     */
    private List<Cuenta> cuentasDelAmbito(Usuario usuario, Cuenta cuenta) {
        List<Cuenta> ambito = new ArrayList<>();
        for (Cuenta c : cuentaDAO.listarPorUsuario(usuario.getId())) {
            if (cuenta == null || c.getId() == cuenta.getId()) {
                ambito.add(c);
            }
        }
        return ambito;
    }

    /**
     * @param usuario: dueño de las categorías
     * @return Devuelve el nombre de cada categoría por su id (las subcategorías cuentan con la categoría principal)
     */
    private Map<Integer, String> nombresDeCategorias(Usuario usuario) {
        Map<Integer, String> nombres = new HashMap<>();
        for (Categoria c : categoriaDAO.listarPorUsuario(usuario.getId())) {
            nombres.put(c.getId(), c.getNombre());
        }
        return nombres;
    }

    /**
     * @param n: cantidad de valores
     * @return Devuelve un array con n ceros con dos decimales
     */
    private static BigDecimal[] ceros(int n) {
        BigDecimal[] valores = new BigDecimal[n];
        java.util.Arrays.fill(valores, Dinero.CERO);
        return valores;
    }

    /**
     * Tramo del mes al que pertenece una fecha
     * @param fecha: fecha yyyy-MM-dd
     * @return Devuelve de 0 a SEMANAS - 1, o -1 si el día no se puede leer
     */
    private static int semanaDe(String fecha) {
        try {
            int dia = Integer.parseInt(fecha.substring(8, 10));
            return Math.min((dia - 1) / DIAS_SEMANA, SEMANAS - 1);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * @param ym: texto yyyy-MM
     * @return Devuelve la posición del mes (0 a 11), o -1 si no se puede leer
     */
    private static int mesDe(String ym) {
        try {
            int mes = Integer.parseInt(ym.substring(5, 7));
            return mes >= 1 && mes <= 12 ? mes - 1 : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Texto de un tramo del mes: "1–7 sept", "8–14 sept", "15–21 sept" y "22–30 sept"
     * @param mes: mes y año
     * @param indice: tramo (0 a SEMANAS - 1)
     * @return Devuelve la etiqueta
     */
    private static String etiquetaSemana(YearMonth mes, int indice) {
        int primero = indice * DIAS_SEMANA + 1;
        int ultimo = indice == SEMANAS - 1 ? mes.lengthOfMonth() : (indice + 1) * DIAS_SEMANA;
        return primero + "\u2013" + ultimo + " " + Formato.mesCorto(mes);
    }

    /**
     * Añade a la lista de meses el mes de una fecha (yyyy-MM-dd)
     * @param meses: meses ya conocidos
     * @param fecha: fecha de un movimiento
     */
    private static void anotarMes(TreeSet<YearMonth> meses, String fecha) {
        try {
            meses.add(YearMonth.parse(fecha.substring(0, 7)));
        } catch (DateTimeParseException e) {
            // Fecha mal escrita en la base de datos: se ignora para la lista de meses
        }
    }
}
