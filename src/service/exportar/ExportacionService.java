package service.exportar;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import models.Cuenta;
import models.Movimiento;
import models.Usuario;
import service.dao.CuentaDAO;
import service.exportar.Tabla.Dinero;
import service.exportar.Tabla.Modo;
import service.exportar.Tabla.Porcentaje;
import service.exportar.Tabla.Seccion;
import service.resumen.GraficasService;
import service.resumen.GraficasService.Anual;
import service.resumen.GraficasService.FilaCuentaMes;
import service.resumen.GraficasService.FilaMes;
import service.resumen.GraficasService.Mensual;
import service.resumen.GraficasService.Porcion;
import service.resumen.GraficasService.Semana;
import service.resumen.ResumenService;
import service.resumen.ResumenService.FilaMovimiento;
import util.Formato;

/**
 * Prepara lo que se exporta: calcula las cifras del periodo elegido reutilizando los servicios de Resumen y de
 * Gráficas, y las convierte en tablas (Seccion y Tabla) que el CSV, el Excel y el PDF escriben cada uno a su manera.
 *
 * @author Fco Padilla
 */
public class ExportacionService {

    public static final String SECCION_RESUMEN = "Resumen";
    public static final String SECCION_GRAFICAS = "Gráficas";
    public static final String SECCION_MOVIMIENTOS = "Movimientos";

    // Guion largo entre los dos extremos de un rango de fechas
    private static final String RAYA = "\u2013";

    private final GraficasService graficas = new GraficasService();
    private final ResumenService resumen = new ResumenService();
    private final CuentaDAO cuentaDAO = new CuentaDAO();

    /**
     * Calcula los datos del periodo y la cuenta elegidos
     * @param usuario: usuario de la sesión
     * @param opciones: lo elegido en el diálogo
     * @return Devuelve los datos listos para escribir
     */
    public DatosExportacion preparar(Usuario usuario, OpcionesExportacion opciones) {
        Cuenta cuenta = opciones.cuenta();
        String ambito = cuenta == null ? "Todas las cuentas" : cuenta.getNombre();
        List<FilaMovimiento> movimientos = resumen.movimientos(usuario, cuenta, opciones.prefijoFecha());

        // Gasto por categoría, de mayor a menor
        Map<String, BigDecimal> porCategoria = new HashMap<>();
        for (FilaMovimiento f : movimientos) {
            if (!f.esIngreso()) {
                porCategoria.merge(f.categoria(), f.movimiento().getImporte(), BigDecimal::add);
            }
        }
        List<Porcion> categorias = new ArrayList<>();
        porCategoria.forEach((nombre, importe) -> categorias.add(new Porcion(nombre, importe)));
        categorias.sort(Comparator.comparing(Porcion::importe).reversed().thenComparing(Porcion::categoria));

        if (opciones.anual()) {
            Anual datos = graficas.anual(usuario, cuenta, opciones.anio());
            BigDecimal saldoFinal = datos.saldoInicial().add(datos.balance());
            return new DatosExportacion(usuario, cuenta, ambito, usuario.getMoneda(), true, opciones.textoPeriodo(),
                    rangoAnual(datos), saldoFinal, datos.ingresos(), datos.gastos(), categorias, null, datos, movimientos);
        }
        Mensual datos = graficas.mensual(usuario, cuenta, opciones.mes());
        String rango = "1" + RAYA + opciones.mes().lengthOfMonth() + " " + Formato.nombreMes(opciones.mes()) + " " + opciones.mes().getYear();
        return new DatosExportacion(usuario, cuenta, ambito, usuario.getMoneda(), false, opciones.textoPeriodo(),
                rango, datos.saldoFinalTotal(), datos.ingresos(), datos.gastos(), categorias, datos, null, movimientos);
    }

    /**
     * @param datos: datos del año
     * @return Devuelve los días que cubre el año, por ejemplo "1 enero – 30 noviembre 2026"
     */
    private String rangoAnual(Anual datos) {
        List<FilaMes> meses = datos.meses();
        if (meses.isEmpty()) {
            return String.valueOf(datos.anio());
        }
        YearMonth ultimo = meses.get(meses.size() - 1).mes();
        return "1 enero " + RAYA + " " + ultimo.lengthOfMonth() + " " + Formato.nombreMes(ultimo) + " " + datos.anio();
    }

    /**
     * @param usuario: usuario de la sesión
     * @return Devuelve las cuentas activas del usuario, para elegir cuál exportar
     */
    public List<Cuenta> cuentasDisponibles(Usuario usuario) {
        return cuentaDAO.listarPorUsuario(usuario.getId());
    }

    /**
     * @param usuario: usuario de la sesión
     * @param mesElegido: mes que se ve en la pantalla (siempre aparece en la lista)
     * @return Devuelve los meses que se pueden elegir (con movimientos, más el actual y el elegido), del más reciente al más antiguo
     */
    public List<YearMonth> mesesDisponibles(Usuario usuario, YearMonth mesElegido) {
        return graficas.mensual(usuario, null, mesElegido).meses();
    }

    /**
     * Cuenta los movimientos de un periodo (para el texto "Tabla detallada de los N movimientos" del diálogo)
     * @param usuario: usuario de la sesión
     * @param cuenta: cuenta, o null para todas
     * @param prefijoFecha: yyyy-MM para un mes o yyyy para un año
     * @return Devuelve cuántos movimientos hay
     */
    public int contarMovimientos(Usuario usuario, Cuenta cuenta, String prefijoFecha) {
        return resumen.movimientos(usuario, cuenta, prefijoFecha).size();
    }

    /**
     * Construye las secciones que se han pedido, cada una con sus tablas
     * @param datos: datos del periodo
     * @param opciones: lo elegido en el diálogo
     * @param paraPdf: true si es para el PDF (la tabla de movimientos lleva menos columnas, porque la página es estrecha)
     * @return Devuelve las secciones, en el orden Resumen, Gráficas y Movimientos
     */
    public List<Seccion> secciones(DatosExportacion datos, OpcionesExportacion opciones, boolean paraPdf) {
        List<Seccion> secciones = new ArrayList<>();
        if (opciones.resumen()) {
            secciones.add(new Seccion(SECCION_RESUMEN, tablasResumen(datos)));
        }
        if (opciones.graficas()) {
            secciones.add(new Seccion(SECCION_GRAFICAS, tablasGraficas(datos, opciones.resumen())));
        }
        if (opciones.movimientos()) {
            secciones.add(new Seccion(SECCION_MOVIMIENTOS, List.of(tablaMovimientos(datos, opciones.notas(), paraPdf))));
        }
        return secciones;
    }

    // ===== Resumen =====

    /**
     * Tablas del resumen: las cifras del periodo, el gasto por categoría y la tabla cuenta por cuenta (mes) o mes a mes (año)
     * @param d: datos del periodo
     * @return Devuelve las tablas
     */
    private List<Tabla> tablasResumen(DatosExportacion d) {
        List<Tabla> tablas = new ArrayList<>();

        List<List<Object>> cifras = new ArrayList<>();
        cifras.add(fila("Saldo total", dinero(d.saldoTotal())));
        cifras.add(fila("Ingresos", dinero(d.ingresos())));
        cifras.add(fila("Gastos", dinero(d.gastos())));
        cifras.add(fila("Balance", new Dinero(d.balance(), Modo.BALANCE)));
        cifras.add(fila("Tasa de ahorro", d.tasaAhorro() == null ? null : new Porcentaje(d.tasaAhorro())));
        tablas.add(new Tabla("Resumen de " + d.periodo().toLowerCase() + " · " + d.ambito(), List.of("Concepto", "Valor"),
                cifras, -1, new double[] { 60, 40 }));

        if (!d.categorias().isEmpty()) {
            tablas.add(tablaCategorias(d));
        }

        if (d.anual()) {
            tablas.add(tablaMesAMes(d.datosAnio()));
        } else {
            tablas.add(tablaCuentas(d.mensual()));
        }
        return tablas;
    }

    /**
     * @param d: datos del periodo
     * @return Devuelve la tabla del gasto por categoría con el porcentaje que supone cada una
     */
    private Tabla tablaCategorias(DatosExportacion d) {
        List<List<Object>> filas = new ArrayList<>();
        for (Porcion p : d.categorias()) {
            BigDecimal porcentaje = d.porcentajeDelGasto(p);
            filas.add(fila(p.categoria(), dinero(p.importe()), porcentaje == null ? null : new Porcentaje(porcentaje)));
        }
        return new Tabla("Gasto por categoría", List.of("Categoría", "Gasto", "% del gasto"), filas, -1,
                new double[] { 50, 28, 22 });
    }

    /**
     * @param d: datos del mes
     * @return Devuelve la tabla cuenta por cuenta con una fila de total
     */
    private Tabla tablaCuentas(Mensual d) {
        List<List<Object>> filas = new ArrayList<>();
        for (FilaCuentaMes f : d.cuentas()) {
            filas.add(fila(f.cuenta().getNombre(), dinero(f.saldoInicial()), dinero(f.ingresos()), dinero(f.gastos()),
                    new Dinero(f.balance(), Modo.BALANCE), dinero(f.saldoFinal())));
        }
        filas.add(fila("Total", dinero(d.saldoInicialTotal()), dinero(d.ingresos()), dinero(d.gastos()),
                new Dinero(d.balance(), Modo.BALANCE), dinero(d.saldoFinalTotal())));
        return new Tabla("El mes, cuenta por cuenta",
                List.of("Cuenta", "Saldo inicial", "Ingresos", "Gastos", "Balance", "Saldo actual"), filas,
                filas.size() - 1, new double[] { 22, 15.6, 15.6, 15.6, 15.6, 15.6 });
    }

    /**
     * @param d: datos del año
     * @return Devuelve la tabla mes a mes con una fila de total
     */
    private Tabla tablaMesAMes(Anual d) {
        List<List<Object>> filas = new ArrayList<>();
        for (FilaMes f : d.meses()) {
            filas.add(fila(Formato.nombreMesMayuscula(f.mes()), dinero(f.ingresos()), dinero(f.gastos()),
                    new Dinero(f.balance(), Modo.BALANCE), porcentaje(f.tasaAhorro())));
        }
        filas.add(fila("Total " + d.anio(), dinero(d.ingresos()), dinero(d.gastos()),
                new Dinero(d.balance(), Modo.BALANCE), porcentaje(d.tasaAhorro())));
        return new Tabla("Mes a mes", List.of("Mes", "Ingresos", "Gastos", "Balance neto", "Tasa de ahorro (%)"), filas,
                filas.size() - 1, new double[] { 28, 18, 18, 18, 18 });
    }

    // ===== Gráficas =====

    /**
     * Tablas con los datos de las gráficas: ingresos y gastos por semana (mes) o por mes (año) y, en el mes, el gasto
     * por categoría (que no se repite si ya está en el resumen) o, en el año, el balance acumulado.
     * @param d: datos del periodo
     * @param conResumen: true si el resumen también se exporta (y ya lleva el gasto por categoría)
     * @return Devuelve las tablas
     */
    private List<Tabla> tablasGraficas(DatosExportacion d, boolean conResumen) {
        List<Tabla> tablas = new ArrayList<>();
        if (d.anual()) {
            List<BigDecimal> acumulado = d.datosAnio().acumulado();
            List<List<Object>> filas = new ArrayList<>();
            for (int i = 0; i < d.datosAnio().meses().size(); i++) {
                FilaMes f = d.datosAnio().meses().get(i);
                filas.add(fila(Formato.nombreMesMayuscula(f.mes()), dinero(f.ingresos()), dinero(f.gastos()),
                        new Dinero(acumulado.get(i), Modo.BALANCE)));
            }
            tablas.add(new Tabla("Ingresos y gastos por mes", List.of("Mes", "Ingresos", "Gastos", "Balance acumulado"),
                    filas, -1, new double[] { 31, 23, 23, 23 }));
        } else {
            List<List<Object>> filas = new ArrayList<>();
            for (Semana s : d.mensual().semanas()) {
                filas.add(fila(s.etiqueta(), dinero(s.ingresos()), dinero(s.gastos())));
            }
            tablas.add(new Tabla("Ingresos y gastos por semana", List.of("Semana", "Ingresos", "Gastos"), filas, -1,
                    new double[] { 40, 30, 30 }));
            if (!conResumen && !d.categorias().isEmpty()) {
                tablas.add(tablaCategorias(d));
            }
        }
        return tablas;
    }

    // ===== Movimientos =====

    /**
     * Tabla de movimientos. Los gastos llevan el importe en negativo. En el PDF no lleva la columna Tipo (el signo del
     * importe ya lo dice) y la nota va antes del importe; en el CSV y el Excel las columnas son las de siempre y la nota va al final.
     * @param d: datos del periodo
     * @param notas: true para añadir la columna de notas
     * @param paraPdf: true si es para el PDF
     * @return Devuelve la tabla
     */
    private Tabla tablaMovimientos(DatosExportacion d, boolean notas, boolean paraPdf) {
        List<String> columnas = new ArrayList<>();
        columnas.add("Fecha");
        if (!paraPdf) {
            columnas.add("Tipo");
        }
        columnas.addAll(List.of("Concepto", "Categoría", "Cuenta"));
        if (notas && paraPdf) {
            columnas.add("Nota");
        }
        columnas.add("Importe");
        if (notas && !paraPdf) {
            columnas.add("Nota");
        }

        List<List<Object>> filas = new ArrayList<>();
        for (FilaMovimiento f : d.movimientos()) {
            Movimiento m = f.movimiento();
            BigDecimal importe = f.esIngreso() ? m.getImporte() : m.getImporte().negate();
            List<Object> celdas = new ArrayList<>();
            celdas.add(fecha(m.getFecha()));
            if (!paraPdf) {
                celdas.add(f.esIngreso() ? "Ingreso" : "Gasto");
            }
            celdas.add(f.concepto());
            celdas.add(f.categoria());
            celdas.add(f.cuenta());
            if (notas && paraPdf) {
                celdas.add(m.getNota());
            }
            celdas.add(new Dinero(importe, Modo.MOVIMIENTO));
            if (notas && !paraPdf) {
                celdas.add(m.getNota());
            }
            filas.add(celdas);
        }

        double[] anchos;
        if (paraPdf) {
            anchos = notas ? new double[] { 9, 24, 14, 15, 22, 16 } : new double[] { 11, 33, 19, 20, 17 };
        } else {
            anchos = new double[columnas.size()];
            java.util.Arrays.fill(anchos, 100.0 / columnas.size());
        }
        return new Tabla("Movimientos de " + d.periodo().toLowerCase(), columnas, filas, -1, anchos);
    }

    // ===== Piezas =====

    /**
     * @param valor: cantidad
     * @return Devuelve el importe normal (sin signo en el PDF)
     */
    private static Dinero dinero(BigDecimal valor) {
        return new Dinero(valor, Modo.NORMAL);
    }

    /**
     * @param valor: porcentaje o null
     * @return Devuelve el porcentaje, o null (celda vacía) si no hay
     */
    private static Porcentaje porcentaje(BigDecimal valor) {
        return valor == null ? null : new Porcentaje(valor);
    }

    /**
     * @param celdas: contenido de cada celda
     * @return Devuelve la fila (una lista admite celdas null, que List.of no)
     */
    private static List<Object> fila(Object... celdas) {
        return new ArrayList<>(java.util.Arrays.asList(celdas));
    }

    /**
     * @param iso: fecha de la base de datos (yyyy-MM-dd)
     * @return Devuelve la fecha, o null si no es válida
     */
    private static LocalDate fecha(String iso) {
        try {
            return LocalDate.parse(iso);
        } catch (DateTimeParseException | NullPointerException e) {
            return null;
        }
    }
}
