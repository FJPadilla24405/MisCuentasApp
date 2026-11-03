package service.presupuesto;

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
import models.Presupuesto;
import models.Usuario;
import service.categoria.CategoriaService;
import service.dao.CuentaDAO;
import service.dao.MovimientoDAO;
import service.dao.PresupuestoDAO;
import util.Dinero;
import util.Formato;

/**
 * Lógica de los presupuestos: calcular cuánto se ha gastado de cada uno en un mes, y crear, editar y
 * borrar presupuestos. Un presupuesto es un límite de gasto mensual para una categoría, que cuenta los
 * gastos de las cuentas de su alcance ("todas", "banco" o "efectivo"). Solo cuentan las cuentas activas.
 * Solo puede haber un presupuesto por categoría y mes.
 *
 * @author Fco Padilla
 */
public class PresupuestoService {

    /**
     * Un dato que no es válido.
     */
    public static class DatoInvalido extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
        private final String campo;

        /**
         * @param campo: campo que falla ("nombre", "categoria", "limite" o "alcance")
         * @param mensaje: texto para el usuario
         */
        public DatoInvalido(String campo, String mensaje) {
            super(mensaje);
            this.campo = campo;
        }

        /**
         * @return Devuelve el campo que falla
         */
        public String getCampo() {
            return campo;
        }
    }

    /**
     * Cómo va un presupuesto: dentro del límite, cerca de él (más del 80 % gastado) o superado.
     */
    public enum Estado {
        DENTRO, CERCA, SUPERADO
    }

    /**
     * Un presupuesto con lo gastado en su mes.
     * @param presupuesto: el presupuesto
     * @param categoria: su categoría
     * @param gastado: suma de los gastos de la categoría en el mes, en las cuentas de su alcance
     */
    public record FilaPresupuesto(Presupuesto presupuesto, Categoria categoria, BigDecimal gastado) {

        /**
         * @return Devuelve el límite del presupuesto
         */
        public BigDecimal limite() {
            return presupuesto.getLimite();
        }

        /**
         * @return Devuelve lo que queda por gastar (0 si se ha superado)
         */
        public BigDecimal restante() {
            return limite().subtract(gastado).max(Dinero.CERO);
        }

        /**
         * @return Devuelve el porcentaje gastado del límite con un decimal (puede pasar de 100)
         */
        public BigDecimal porcentaje() {
            if (limite().signum() <= 0) {
                return BigDecimal.ZERO;
            }
            return gastado.multiply(BigDecimal.valueOf(100)).divide(limite(), 1, RoundingMode.HALF_UP);
        }

        /**
         * @return Devuelve el estado según lo gastado: superado si pasa del límite, cerca si pasa del 80 %
         */
        public Estado estado() {
            if (gastado.compareTo(limite()) > 0) {
                return Estado.SUPERADO;
            }
            // gastado / limite > 0,8  es lo mismo que  gastado * 5 > limite * 4
            if (gastado.multiply(BigDecimal.valueOf(5)).compareTo(limite().multiply(BigDecimal.valueOf(4))) > 0) {
                return Estado.CERCA;
            }
            return Estado.DENTRO;
        }

        /**
         * @return Devuelve el nombre de la categoría (con su subcategoría si la tiene)
         */
        public String nombreCategoria() {
            String sub = categoria.getSubcategoria();
            return sub == null || sub.isBlank() ? categoria.getNombre() : categoria.getNombre() + " · " + sub;
        }
    }

    /**
     * Todo lo que necesita la pantalla de presupuestos de un mes.
     * @param filas: presupuestos del mes, en el orden en que se crearon
     * @param limiteTotal: suma de los límites
     * @param gastadoTotal: suma de lo gastado en cada presupuesto
     * @param restanteTotal: suma de lo que queda en cada presupuesto (un presupuesto superado no resta a los demás)
     * @param meses: meses que se pueden elegir (con presupuestos, más el actual y el elegido), del más reciente al más antiguo
     */
    public record ResumenPresupuestos(List<FilaPresupuesto> filas, BigDecimal limiteTotal, BigDecimal gastadoTotal,
            BigDecimal restanteTotal, List<YearMonth> meses) {

        /**
         * @return Devuelve el porcentaje del límite total que se ha gastado, o null si no hay límite
         */
        public BigDecimal porcentajeGastado() {
            return porcentaje(gastadoTotal);
        }

        /**
         * @return Devuelve el porcentaje del límite total que queda, o null si no hay límite
         */
        public BigDecimal porcentajeRestante() {
            return porcentaje(restanteTotal);
        }

        /**
         * @param parte: cantidad
         * @return Devuelve la cantidad como porcentaje del límite total, o null si no hay límite
         */
        private BigDecimal porcentaje(BigDecimal parte) {
            if (limiteTotal.signum() <= 0) {
                return null;
            }
            return parte.multiply(BigDecimal.valueOf(100)).divide(limiteTotal, 1, RoundingMode.HALF_UP);
        }
    }

    // Alcances posibles de un presupuesto
    public static final List<String> ALCANCES = List.of("todas", "banco", "efectivo");
    // Longitud máxima del nombre, la misma que el VARCHAR(50) del esquema
    private static final int MAX_NOMBRE = 50;

    private final PresupuestoDAO presupuestoDAO = new PresupuestoDAO();
    private final CuentaDAO cuentaDAO = new CuentaDAO();
    private final MovimientoDAO movimientoDAO = new MovimientoDAO();
    private final CategoriaService categoriaService = new CategoriaService();

    /**
     * Calcula los presupuestos de un mes con lo gastado en cada uno.
     * @param usuario: usuario de la sesión
     * @param mes: mes y año
     * @return Devuelve los datos listos para pintar
     */
    public ResumenPresupuestos calcular(Usuario usuario, YearMonth mes) {
        Map<Integer, Cuenta> cuentas = new HashMap<>();
        for (Cuenta c : cuentaDAO.listarPorUsuario(usuario.getId())) {
            cuentas.put(c.getId(), c);
        }
        Map<Integer, Categoria> categorias = new HashMap<>();
        for (Categoria c : categoriaService.categoriasDeGasto(usuario)) {
            categorias.put(c.getId(), c);
        }
        List<Movimiento> gastosDelMes = new ArrayList<>();
        for (Movimiento m : movimientoDAO.listarPorUsuarioYRango(usuario.getId(), mes.atDay(1).toString(), mes.atEndOfMonth().toString())) {
            if ("gasto".equals(m.getTipo()) && cuentas.containsKey(m.getCuentaId())) {
                gastosDelMes.add(m);
            }
        }

        List<FilaPresupuesto> filas = new ArrayList<>();
        BigDecimal limiteTotal = Dinero.CERO;
        BigDecimal gastadoTotal = Dinero.CERO;
        BigDecimal restanteTotal = Dinero.CERO;
        List<Presupuesto> delMes = new ArrayList<>(presupuestoDAO.listarPorUsuarioYMes(usuario.getId(), mes.toString()));
        delMes.sort(Comparator.comparingInt(Presupuesto::getId));
        for (Presupuesto p : delMes) {
            Categoria categoria = categorias.get(p.getCategoriaId());
            if (categoria == null) {
                continue;
            }
            BigDecimal gastado = Dinero.CERO;
            for (Movimiento m : gastosDelMes) {
                if (m.getCategoriaId() == p.getCategoriaId() && entraEnAlcance(cuentas.get(m.getCuentaId()), p.getAlcance())) {
                    gastado = gastado.add(m.getImporte());
                }
            }
            FilaPresupuesto fila = new FilaPresupuesto(p, categoria, gastado);
            filas.add(fila);
            limiteTotal = limiteTotal.add(p.getLimite());
            gastadoTotal = gastadoTotal.add(gastado);
            restanteTotal = restanteTotal.add(fila.restante());
        }

        TreeSet<YearMonth> meses = new TreeSet<>(Comparator.reverseOrder());
        meses.add(YearMonth.now());
        meses.add(mes);
        for (String m : presupuestoDAO.listarMesesPorUsuario(usuario.getId())) {
            try {
                meses.add(YearMonth.parse(m));
            } catch (DateTimeParseException e) {
                // Mes mal escrito en la base de datos: se ignora para la lista
            }
        }
        return new ResumenPresupuestos(filas, limiteTotal, gastadoTotal, restanteTotal, new ArrayList<>(meses));
    }

    /**
     * Crea un presupuesto o modifica uno existente. Si algo no es válido no se guarda nada.
     * @param usuario: usuario de la sesión
     * @param existente: presupuesto a modificar, o null para crear uno nuevo
     * @param nombre: nombre del presupuesto
     * @param categoriaId: id de la categoría de gasto
     * @param limite: límite de gasto del mes
     * @param mes: mes y año del presupuesto
     * @param alcance: "todas", "banco" o "efectivo"
     * @return Devuelve el presupuesto guardado
     * @throws DatoInvalido si algún dato no es válido o ya hay un presupuesto de esa categoría en ese mes
     */
    public Presupuesto guardar(Usuario usuario, Presupuesto existente, String nombre, Integer categoriaId,
            BigDecimal limite, YearMonth mes, String alcance) {
        String nombreLimpio = nombre == null ? "" : nombre.trim();
        if (nombreLimpio.isEmpty() || nombreLimpio.length() > MAX_NOMBRE) {
            throw new DatoInvalido("nombre", nombreLimpio.isEmpty() ? "Escribe un nombre para el presupuesto."
                    : "El nombre no puede superar " + MAX_NOMBRE + " caracteres.");
        }
        Categoria categoria = null;
        if (categoriaId != null) {
            for (Categoria c : categoriaService.categoriasDeGasto(usuario)) {
                if (c.getId() == categoriaId) {
                    categoria = c;
                }
            }
        }
        if (categoria == null) {
            throw new DatoInvalido("categoria", "Elige una categoría de gasto.");
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
        if (!ALCANCES.contains(alcance)) {
            throw new DatoInvalido("alcance", "Elige a qué cuentas se aplica.");
        }
        boolean repetido = presupuestoDAO.buscarPorCategoriaYMes(usuario.getId(), categoria.getId(), mes.toString())
                .filter(otro -> existente == null || otro.getId() != existente.getId()).isPresent();
        if (repetido) {
            throw new DatoInvalido("categoria", "Ya tienes un presupuesto de " + categoria.getNombre() + " en "
                    + Formato.nombreMes(mes) + " de " + mes.getYear() + ".");
        }

        Presupuesto p = existente == null ? new Presupuesto(0, usuario.getId(), categoria.getId(), limiteNormalizado, "mensual", mes.toString()) : existente;
        p.setCategoriaId(categoria.getId());
        p.setLimite(limiteNormalizado);
        p.setPeriodo("mensual");
        p.setMesReferencia(mes.toString());
        p.setNombre(nombreLimpio);
        p.setAlcance(alcance);
        if (existente == null) {
            presupuestoDAO.insertar(p);
        } else {
            presupuestoDAO.actualizar(p);
        }
        return p;
    }

    /**
     * Borra un presupuesto. No toca los movimientos ni los saldos.
     * @param presupuesto: presupuesto a borrar
     */
    public void eliminar(Presupuesto presupuesto) {
        presupuestoDAO.eliminar(presupuesto.getId());
    }

    /**
     * @param usuario: usuario de la sesión
     * @param categoriaId: id de la categoría
     * @param mes: mes y año
     * @return Devuelve true si ya hay un presupuesto de esa categoría en ese mes
     */
    public boolean tienePresupuesto(Usuario usuario, int categoriaId, YearMonth mes) {
        return presupuestoDAO.buscarPorCategoriaYMes(usuario.getId(), categoriaId, mes.toString()).isPresent();
    }

    /**
     * Nombres de las cuentas activas que entran en un alcance
     * @param usuario: usuario de la sesión
     * @param alcance: "todas", "banco" o "efectivo"
     * @return Devuelve los nombres de las cuentas
     */
    public List<String> nombresDeCuentas(Usuario usuario, String alcance) {
        List<String> nombres = new ArrayList<>();
        for (Cuenta c : cuentaDAO.listarPorUsuario(usuario.getId())) {
            if (entraEnAlcance(c, alcance)) {
                nombres.add(c.getNombre());
            }
        }
        return nombres;
    }

    /**
     * @param cuenta: cuenta del gasto
     * @param alcance: "todas", "banco" o "efectivo"
     * @return Devuelve true si los gastos de esa cuenta cuentan para un presupuesto con ese alcance
     */
    private boolean entraEnAlcance(Cuenta cuenta, String alcance) {
        if (cuenta == null) {
            return false;
        }
        boolean efectivo = "efectivo".equals(cuenta.getTipo());
        return switch (alcance == null ? "todas" : alcance) {
            case "banco" -> !efectivo;
            case "efectivo" -> efectivo;
            default -> true;
        };
    }
}
