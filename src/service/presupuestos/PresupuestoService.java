package service.presupuestos;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

import models.Categoria;
import models.Cuenta;
import models.Movimiento;
import models.Presupuesto;
import models.Usuario;
import service.dao.CategoriaDAO;
import service.dao.CuentaDAO;
import service.dao.MovimientoDAO;
import service.dao.PresupuestoDAO;
import util.Dinero;

/**
 * Calcula los datos de la pantalla de presupuestos (cuánto se ha gastado de cada límite en un mes) y crea
 * presupuestos nuevos. Un presupuesto es el límite de gasto de una categoría en un mes; su alcance dice qué
 * cuentas cuentan: todas, solo las de tipo banco o solo las de tipo efectivo.
 *
 * @author Fco Padilla
 */
public class PresupuestoService {

    // A partir de este porcentaje del límite gastado, el presupuesto está "cerca del límite"
    private static final BigDecimal UMBRAL_CERCA = BigDecimal.valueOf(85);
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);
    // Longitud máxima del nombre, la misma que el VARCHAR(50) del esquema
    public static final int MAX_NOMBRE = 50;
    // Categorías de gasto que se crean la primera vez si el usuario todavía no tiene ninguna
    private static final String[] CATEGORIAS_BASE = { "Vivienda", "Alimentación", "Compras", "Servicios", "Transporte", "Ocio" };

    /**
     * Cómo va un presupuesto respecto a su límite.
     */
    public enum Estado {
        /** Se ha gastado menos del 85 % del límite */
        DENTRO,
        /** Se ha gastado el 85 % o más, pero no se ha superado */
        CERCA,
        /** Se ha gastado más que el límite */
        SUPERADO
    }

    /**
     * Un presupuesto con lo gastado en su categoría y mes.
     * @param presupuesto: el presupuesto
     * @param categoria: nombre de su categoría
     * @param gastado: suma de los gastos de la categoría en el mes, en las cuentas que entran en su alcance
     */
    public record FilaPresupuesto(Presupuesto presupuesto, String categoria, BigDecimal gastado) {

        /**
         * @return Devuelve el límite del presupuesto
         */
        public BigDecimal limite() {
            return presupuesto.getLimite();
        }

        /**
         * @return Devuelve lo que queda hasta el límite (negativo si se ha superado)
         */
        public BigDecimal restante() {
            return limite().subtract(gastado);
        }

        /**
         * @return Devuelve el porcentaje del límite gastado con un decimal (puede pasar de 100)
         */
        public BigDecimal porcentaje() {
            if (limite().signum() <= 0) {
                return gastado.signum() > 0 ? CIEN : BigDecimal.ZERO.setScale(1);
            }
            return gastado.multiply(CIEN).divide(limite(), 1, RoundingMode.HALF_UP);
        }

        /**
         * @return Devuelve cómo va el presupuesto: dentro del límite, cerca de él o superado
         */
        public Estado estado() {
            if (limite().signum() <= 0) {
                return gastado.signum() > 0 ? Estado.SUPERADO : Estado.DENTRO;
            }
            if (gastado.compareTo(limite()) > 0) {
                return Estado.SUPERADO;
            }
            if (gastado.multiply(CIEN).compareTo(limite().multiply(UMBRAL_CERCA)) >= 0) {
                return Estado.CERCA;
            }
            return Estado.DENTRO;
        }
    }

    /**
     * Todo lo que necesita la pantalla.
     * @param filas: presupuestos del mes, en el orden en que se crearon
     * @param limiteTotal: suma de los límites
     * @param gastadoTotal: suma de lo gastado en cada presupuesto
     * @param meses: meses que se pueden elegir (con presupuestos, más el actual y el siguiente), del más reciente al más antiguo
     */
    public record Vista(List<FilaPresupuesto> filas, BigDecimal limiteTotal, BigDecimal gastadoTotal, List<YearMonth> meses) {

        /**
         * @return Devuelve el límite total menos lo gastado (negativo si se ha superado)
         */
        public BigDecimal restanteTotal() {
            return limiteTotal.subtract(gastadoTotal);
        }

        /**
         * @return Devuelve el porcentaje del límite total gastado, o null si no hay límites
         */
        public BigDecimal porcentajeUsado() {
            if (limiteTotal.signum() <= 0) {
                return null;
            }
            return gastadoTotal.multiply(CIEN).divide(limiteTotal, 1, RoundingMode.HALF_UP);
        }

        /**
         * @return Devuelve el porcentaje del límite total que queda libre (0 si se ha superado), o null si no hay límites
         */
        public BigDecimal porcentajeDisponible() {
            if (limiteTotal.signum() <= 0) {
                return null;
            }
            BigDecimal libre = restanteTotal().max(BigDecimal.ZERO);
            return libre.multiply(CIEN).divide(limiteTotal, 1, RoundingMode.HALF_UP);
        }
    }

    /**
     * Error de un dato que ha escrito el usuario; el mensaje se puede enseñar tal cual.
     */
    public static class DatoInvalido extends RuntimeException {
        private static final long serialVersionUID = 1L;

        /** Campo del formulario con el error: "nombre", "categoria" o "limite" */
        private final String campo;

        /**
         * @param campo: campo del formulario con el error ("nombre", "categoria" o "limite")
         * @param mensaje: qué está mal, en una frase para el usuario
         */
        public DatoInvalido(String campo, String mensaje) {
            super(mensaje);
            this.campo = campo;
        }

        /**
         * @return Devuelve el campo del formulario con el error
         */
        public String getCampo() {
            return campo;
        }
    }

    private final PresupuestoDAO presupuestoDAO = new PresupuestoDAO();
    private final CategoriaDAO categoriaDAO = new CategoriaDAO();
    private final CuentaDAO cuentaDAO = new CuentaDAO();
    private final MovimientoDAO movimientoDAO = new MovimientoDAO();

    /**
     * Calcula los presupuestos de un mes con lo que se ha gastado en cada uno. Los gastos se cuentan como en
     * la pantalla de resumen: movimientos de tipo gasto del mes, de cuentas activas.
     * @param usuario: dueño de los presupuestos
     * @param mes: mes y año
     * @return Devuelve los datos listos para pintar
     */
    public Vista calcular(Usuario usuario, YearMonth mes) {
        Map<Integer, String> categorias = new HashMap<>();
        for (Categoria c : categoriaDAO.listarPorUsuario(usuario.getId())) {
            categorias.put(c.getId(), c.getNombre());
        }
        Map<Integer, Cuenta> cuentas = new HashMap<>();
        for (Cuenta c : cuentaDAO.listarPorUsuario(usuario.getId())) {
            cuentas.put(c.getId(), c);
        }

        String prefijoMes = mes.toString();
        List<Movimiento> gastosDelMes = new ArrayList<>();
        for (Movimiento m : movimientoDAO.listarPorUsuario(usuario.getId())) {
            if ("gasto".equals(m.getTipo()) && m.getFecha() != null && m.getFecha().startsWith(prefijoMes)
                    && cuentas.containsKey(m.getCuentaId())) {
                gastosDelMes.add(m);
            }
        }

        List<Presupuesto> presupuestos = new ArrayList<>(presupuestoDAO.listarPorUsuarioYMes(usuario.getId(), prefijoMes));
        presupuestos.sort(Comparator.comparingInt(Presupuesto::getId));

        List<FilaPresupuesto> filas = new ArrayList<>();
        BigDecimal limiteTotal = Dinero.CERO;
        BigDecimal gastadoTotal = Dinero.CERO;
        for (Presupuesto p : presupuestos) {
            BigDecimal gastado = Dinero.CERO;
            for (Movimiento m : gastosDelMes) {
                if (m.getCategoriaId() == p.getCategoriaId() && entraEnAlcance(cuentas.get(m.getCuentaId()), p.getAlcance())) {
                    gastado = gastado.add(m.getImporte());
                }
            }
            filas.add(new FilaPresupuesto(p, categorias.getOrDefault(p.getCategoriaId(), "Sin categoría"), gastado));
            limiteTotal = limiteTotal.add(p.getLimite());
            gastadoTotal = gastadoTotal.add(gastado);
        }
        return new Vista(filas, limiteTotal, gastadoTotal, mesesElegibles(usuario, mes));
    }

    /**
     * Categorías de gasto del usuario, ordenadas por nombre. Si todavía no tiene ninguna (la aplicación aún no
     * tiene pantalla para crearlas), se crean las categorías base: Vivienda, Alimentación, Compras, Servicios,
     * Transporte y Ocio.
     * @param usuario: dueño de las categorías
     * @return Devuelve las categorías de gasto
     */
    public List<Categoria> categoriasDeGasto(Usuario usuario) {
        List<Categoria> lista = categoriaDAO.listarPorUsuarioYTipo(usuario.getId(), "gasto");
        if (lista.isEmpty()) {
            for (String nombre : CATEGORIAS_BASE) {
                categoriaDAO.insertar(new Categoria(0, usuario.getId(), nombre, "gasto", null, true));
            }
            lista = categoriaDAO.listarPorUsuarioYTipo(usuario.getId(), "gasto");
        }
        List<Categoria> ordenada = new ArrayList<>(lista);
        ordenada.sort(Comparator.comparing(c -> c.getNombre().toLowerCase(Locale.ROOT)));
        return ordenada;
    }

    /**
     * Cuentas activas que entran en un alcance.
     * @param usuario: dueño de las cuentas
     * @param alcance: "todas", "banco" o "efectivo"
     * @return Devuelve las cuentas del alcance
     */
    public List<Cuenta> cuentasDelAlcance(Usuario usuario, String alcance) {
        List<Cuenta> lista = new ArrayList<>();
        for (Cuenta c : cuentaDAO.listarPorUsuario(usuario.getId())) {
            if (entraEnAlcance(c, alcance)) {
                lista.add(c);
            }
        }
        return lista;
    }

    /**
     * Crea un presupuesto mensual. Solo puede haber uno por categoría y mes.
     * @param usuario: dueño del presupuesto
     * @param nombre: nombre del presupuesto
     * @param categoria: categoría a la que se aplica el límite
     * @param limite: cantidad máxima de gasto (mayor que cero)
     * @param mes: mes al que se aplica
     * @param alcance: "todas", "banco" o "efectivo"
     * @return Devuelve el presupuesto creado
     * @throws DatoInvalido si algún dato no es válido o ya existe un presupuesto de esa categoría en ese mes
     */
    public Presupuesto crear(Usuario usuario, String nombre, Categoria categoria, BigDecimal limite, YearMonth mes, String alcance) {
        String nombreLimpio = nombre == null ? "" : nombre.trim();
        if (nombreLimpio.isEmpty()) {
            throw new DatoInvalido("nombre", "Escribe un nombre para el presupuesto.");
        }
        if (nombreLimpio.length() > MAX_NOMBRE) {
            throw new DatoInvalido("nombre", "El nombre no puede superar " + MAX_NOMBRE + " caracteres.");
        }
        if (categoria == null) {
            throw new DatoInvalido("categoria", "Elige una categoría.");
        }
        if (limite == null || limite.signum() <= 0) {
            throw new DatoInvalido("limite", "El importe límite tiene que ser mayor que cero.");
        }
        BigDecimal limiteNormalizado;
        try {
            limiteNormalizado = Dinero.normalizar(limite);
        } catch (IllegalArgumentException e) {
            throw new DatoInvalido("limite", "El importe límite es demasiado grande.");
        }
        if (limiteNormalizado.signum() <= 0) {
            throw new DatoInvalido("limite", "El importe límite tiene que ser mayor que cero.");
        }
        if (presupuestoDAO.buscarPorCategoriaYMes(usuario.getId(), categoria.getId(), mes.toString()).isPresent()) {
            throw new DatoInvalido("categoria", "Ya tienes un presupuesto de " + categoria.getNombre() + " para ese mes.");
        }
        String alcanceFinal = Presupuesto.ALCANCE_BANCO.equals(alcance) || Presupuesto.ALCANCE_EFECTIVO.equals(alcance)
                ? alcance : Presupuesto.ALCANCE_TODAS;
        return presupuestoDAO.insertar(new Presupuesto(0, usuario.getId(), categoria.getId(), nombreLimpio,
                limiteNormalizado, "mensual", mes.toString(), alcanceFinal));
    }

    /**
     * @param cuenta: cuenta de un gasto
     * @param alcance: "todas", "banco" o "efectivo"
     * @return Devuelve true si los gastos de esa cuenta cuentan en ese alcance
     */
    private boolean entraEnAlcance(Cuenta cuenta, String alcance) {
        if (cuenta == null) {
            return false;
        }
        if (Presupuesto.ALCANCE_EFECTIVO.equals(alcance)) {
            return "efectivo".equals(cuenta.getTipo());
        }
        if (Presupuesto.ALCANCE_BANCO.equals(alcance)) {
            return "banco".equals(cuenta.getTipo());
        }
        return true;
    }

    /**
     * Meses que se pueden elegir en el selector: los que ya tienen presupuestos, el actual, el siguiente
     * (para planificar) y el que se está viendo.
     * @param usuario: dueño de los presupuestos
     * @param elegido: mes que se está viendo
     * @return Devuelve los meses, del más reciente al más antiguo
     */
    private List<YearMonth> mesesElegibles(Usuario usuario, YearMonth elegido) {
        TreeSet<YearMonth> meses = new TreeSet<>(Comparator.reverseOrder());
        meses.add(YearMonth.now());
        meses.add(YearMonth.now().plusMonths(1));
        meses.add(elegido);
        for (String texto : presupuestoDAO.listarMesesPorUsuario(usuario.getId())) {
            try {
                meses.add(YearMonth.parse(texto));
            } catch (DateTimeParseException | NullPointerException e) {
                // Mes mal escrito en la base de datos: se ignora para la lista
            }
        }
        return new ArrayList<>(meses);
    }
}
