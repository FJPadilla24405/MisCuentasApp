package service.resumen;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import models.Categoria;
import models.Cuenta;
import models.Movimiento;
import models.Usuario;
import service.dao.CategoriaDAO;
import service.dao.CuentaDAO;
import service.dao.MovimientoDAO;
import util.Dinero;

/**
 * Calcula los datos de la pantalla de resumen (vista general y vista de una cuenta). Es la misma
 * lógica para las dos: la vista general suma todas las cuentas y la de una cuenta solo esa.
 *
 * @author Fco Padilla
 */
public class ResumenService {

    /**
     * Una cuenta con su saldo actual.
     * @param cuenta: la cuenta
     * @param saldo: saldo inicial más ingresos menos gastos
     */
    public record FilaCuenta(Cuenta cuenta, BigDecimal saldo) {
    }

    /**
     * Un movimiento con los nombres que hacen falta para mostrarlo.
     * @param movimiento: el movimiento
     * @param categoria: nombre de su categoría
     * @param cuenta: nombre de su cuenta
     */
    public record FilaMovimiento(Movimiento movimiento, String categoria, String cuenta) {

        /**
         * @return Devuelve lo que se muestra como concepto: la descripción o, si no tiene, la categoría
         */
        public String concepto() {
            String d = movimiento.getDescripcion();
            return d == null || d.isBlank() ? categoria : d.trim();
        }

        /**
         * @return Devuelve true si el movimiento es un ingreso
         */
        public boolean esIngreso() {
            return "ingreso".equals(movimiento.getTipo());
        }
    }

    /**
     * Todo lo que necesita la pantalla.
     * @param cuentas: cuentas del ámbito con su saldo
     * @param saldoTotal: suma de los saldos
     * @param numBancos: cuentas de tipo banco
     * @param numEfectivo: cuentas de tipo efectivo
     * @param ingresos: suma de los ingresos del mes
     * @param numIngresos: cuántos ingresos hay en el mes
     * @param gastos: suma de los gastos del mes
     * @param numGastos: cuántos gastos hay en el mes
     * @param movimientos: movimientos del mes, del más reciente al más antiguo
     * @param meses: meses que se pueden elegir (con movimientos, más el actual y el elegido), del más reciente al más antiguo
     */
    public record Resumen(List<FilaCuenta> cuentas, BigDecimal saldoTotal, int numBancos, int numEfectivo,
            BigDecimal ingresos, int numIngresos, BigDecimal gastos, int numGastos,
            List<FilaMovimiento> movimientos, List<YearMonth> meses) {

        /**
         * @return Devuelve ingresos menos gastos del mes
         */
        public BigDecimal balance() {
            return ingresos.subtract(gastos);
        }

        /**
         * @return Devuelve el porcentaje de los ingresos que se ha conservado (balance entre ingresos por 100),
         *         o null si no hay ingresos o el balance es negativo
         */
        public BigDecimal porcentajeConservado() {
            if (ingresos.signum() <= 0 || balance().signum() < 0) {
                return null;
            }
            return balance().multiply(BigDecimal.valueOf(100)).divide(ingresos, 1, RoundingMode.HALF_UP);
        }
    }

    private final CuentaDAO cuentaDAO = new CuentaDAO();
    private final MovimientoDAO movimientoDAO = new MovimientoDAO();
    private final CategoriaDAO categoriaDAO = new CategoriaDAO();

    /**
     * Calcula el resumen de un mes. Los saldos son los de ahora; los ingresos, gastos y movimientos, los del mes.
     * @param usuario: dueño de las cuentas
     * @param cuenta: la cuenta a resumir, o null para sumar todas las cuentas activas
     * @param mes: mes y año
     * @return Devuelve los datos listos para pintar
     */
    public Resumen calcular(Usuario usuario, Cuenta cuenta, YearMonth mes) {
        // Cuentas del ámbito (las desactivadas no cuentan)
        Map<Integer, Cuenta> ambito = new HashMap<>();
        List<FilaCuenta> filas = new ArrayList<>();
        BigDecimal saldoTotal = Dinero.CERO;
        int bancos = 0;
        int efectivo = 0;
        for (Cuenta c : cuentaDAO.listarPorUsuario(usuario.getId())) {
            if (cuenta != null && c.getId() != cuenta.getId()) {
                continue;
            }
            BigDecimal saldo = cuentaDAO.calcularSaldo(c.getId());
            ambito.put(c.getId(), c);
            filas.add(new FilaCuenta(c, saldo));
            saldoTotal = saldoTotal.add(saldo);
            if ("efectivo".equals(c.getTipo())) {
                efectivo++;
            } else {
                bancos++;
            }
        }

        // Movimientos del ámbito
        Map<Integer, String> categorias = new HashMap<>();
        for (Categoria cat : categoriaDAO.listarPorUsuario(usuario.getId())) {
            categorias.put(cat.getId(), cat.getNombre());
        }
        TreeSet<YearMonth> meses = new TreeSet<>(Comparator.reverseOrder());
        meses.add(YearMonth.now());
        meses.add(mes);
        BigDecimal ingresos = Dinero.CERO;
        BigDecimal gastos = Dinero.CERO;
        int numIngresos = 0;
        int numGastos = 0;
        List<FilaMovimiento> delMes = new ArrayList<>();
        String prefijoMes = mes.toString();
        for (Movimiento m : movimientoDAO.listarPorUsuario(usuario.getId())) {
            Cuenta c = ambito.get(m.getCuentaId());
            if (c == null) {
                continue;
            }
            anotarMes(meses, m.getFecha());
            if (m.getFecha() == null || !m.getFecha().startsWith(prefijoMes)) {
                continue;
            }
            if ("ingreso".equals(m.getTipo())) {
                ingresos = ingresos.add(m.getImporte());
                numIngresos++;
            } else {
                gastos = gastos.add(m.getImporte());
                numGastos++;
            }
            delMes.add(new FilaMovimiento(m, categorias.getOrDefault(m.getCategoriaId(), "Sin categoría"), c.getNombre()));
        }
        delMes.sort(Comparator.comparing((FilaMovimiento f) -> f.movimiento().getFecha()).reversed()
                .thenComparing(Comparator.comparingInt((FilaMovimiento f) -> f.movimiento().getId()).reversed()));

        return new Resumen(filas, saldoTotal, bancos, efectivo, ingresos, numIngresos, gastos, numGastos,
                delMes, new ArrayList<>(meses));
    }

    /**
     * Añade a la lista de meses el mes de una fecha (yyyy-MM-dd)
     * @param meses: meses ya conocidos
     * @param fecha: fecha de un movimiento
     */
    private void anotarMes(TreeSet<YearMonth> meses, String fecha) {
        if (fecha == null || fecha.length() < 7) {
            return;
        }
        try {
            meses.add(YearMonth.parse(fecha.substring(0, 7)));
        } catch (DateTimeParseException e) {
            // Fecha mal escrita en la base de datos: se ignora para la lista de meses
        }
    }
}
