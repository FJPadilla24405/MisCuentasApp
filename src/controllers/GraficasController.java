package controllers;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import graficos.GraficoBarras;
import graficos.GraficoDonut;
import graficos.GraficoLinea;
import graficos.Paleta;
import javafx.fxml.FXML;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.stage.FileChooser;
import models.Cuenta;
import models.Usuario;
import service.auth.SesionActual;
import service.dao.CuentaDAO;
import service.resumen.ExportadorCsv;
import service.resumen.GraficasService;
import service.resumen.GraficasService.Anual;
import service.resumen.GraficasService.FilaCuentaMes;
import service.resumen.GraficasService.FilaMes;
import service.resumen.GraficasService.Mensual;
import service.resumen.GraficasService.Porcion;
import util.Formato;
import util.IconosCategoria;

/**
 * Controlador de Graficas.fxml (apartado Gráficas y resúmenes). Es una sola pantalla con dos vistas, la
 * mensual y la anual, que se cambian con el interruptor Mes / Año; en las dos se puede ver todas las
 * cuentas o solo una. Los datos los calcula GraficasService y las gráficas están en el paquete graficos.
 *
 * @author Fco Padilla
 */
public class GraficasController {

    // Trazos de los iconos que se crean en código (cuadrícula de 24x24)
    private static final double ESCALA_ICONO = 0.67;
    // Máximo de trozos del anillo: si hay más categorías, las últimas se agrupan en "Otras"
    private static final int MAX_CATEGORIAS = 6;
    // Ancho de cada columna de las tablas en %
    private static final double[] ANCHOS_MES = { 22, 15.6, 15.6, 15.6, 15.6, 15.6 };
    private static final double[] ANCHOS_ANIO = { 28, 18, 18, 18, 18 };
    private static final String[] TITULOS_MES = { "CUENTA", "SALDO INICIAL", "INGRESOS", "GASTOS", "BALANCE", "SALDO ACTUAL" };
    private static final String[] TITULOS_ANIO = { "MES", "INGRESOS", "GASTOS", "BALANCE NETO", "TASA DE AHORRO" };
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);
    // Para que Excel reconozca que el CSV está en UTF-8
    private static final String BOM = "\uFEFF";
    // Signo menos de verdad (el guion de teclado queda corto al lado de las cifras)
    private static final String MENOS = "\u2212";

    @FXML private ScrollPane raiz;
    @FXML private Label lblEtiqueta;
    @FXML private Label lblTitulo;
    @FXML private Label lblSubtitulo;
    @FXML private MenuButton menuCuenta;
    @FXML private ToggleButton tglMes;
    @FXML private ToggleButton tglAnio;
    @FXML private Button btnAnterior;
    @FXML private Button btnSiguiente;
    @FXML private MenuButton menuPeriodo;
    @FXML private Label lblIngresosTitulo;
    @FXML private Label lblIngresos;
    @FXML private Label lblIngresosDetalle;
    @FXML private Label lblGastosTitulo;
    @FXML private Label lblGastos;
    @FXML private Label lblGastosDetalle;
    @FXML private Label lblBalanceTitulo;
    @FXML private Label lblBalance;
    @FXML private Label lblBalanceDetalle;
    @FXML private Label lblTasa;
    @FXML private Label lblTasaDetalle;
    @FXML private GridPane gridGraficos;
    @FXML private Label lblBarrasSubtitulo;
    @FXML private StackPane huecoBarras;
    @FXML private VBox tarjetaCategorias;
    @FXML private Label lblCategoriasMes;
    @FXML private StackPane huecoDonut;
    @FXML private VBox listaCategorias;
    @FXML private HBox notaCategoria;
    @FXML private StackPane notaIcono;
    @FXML private Label lblNotaCategoria;
    @FXML private VBox tarjetaAcumulado;
    @FXML private Label lblAcumuladoValor;
    @FXML private Label lblAcumuladoDetalle;
    @FXML private StackPane huecoLinea;
    @FXML private Label lblConservado;
    @FXML private Label lblTablaTitulo;
    @FXML private Label lblTablaPastilla;
    @FXML private HBox cajaMejorMes;
    @FXML private Label lblMejorMes;
    @FXML private VBox listaTabla;
    @FXML private Label lblVacio;

    private final GraficasService servicio = new GraficasService();
    private final CuentaDAO cuentaDAO = new CuentaDAO();
    private Consumer<String> alCambiarMiga = texto -> { };
    private Consumer<String> alCambiarInfo = texto -> { };

    // Agrupa los botones Mes y Año para que siempre haya uno elegido (se crea aquí y no en el FXML)
    private final ToggleGroup grupoVista = new ToggleGroup();

    private GraficoBarras barras;
    private GraficoDonut donut;
    private GraficoLinea linea;

    // Cuenta que se resume, o null para todas
    private Cuenta cuenta;
    // true en la vista anual y false en la mensual
    private boolean anual;
    private YearMonth mes = YearMonth.now();
    private int anio = mes.getYear();
    private String moneda = "EUR";
    private List<Cuenta> cuentas = List.of();
    // Últimos datos calculados de cada vista (los usa la exportación)
    private Mensual datosMes;
    private Anual datosAnio;

    /**
     * Crea las gráficas y conecta el interruptor Mes / Año
     */
    @FXML
    private void initialize() {
        barras = new GraficoBarras();
        huecoBarras.getChildren().add(barras);
        donut = new GraficoDonut();
        huecoDonut.getChildren().add(donut);
        linea = new GraficoLinea();
        huecoLinea.getChildren().add(linea);

        tglMes.setToggleGroup(grupoVista);
        tglAnio.setToggleGroup(grupoVista);
        grupoVista.selectToggle(tglMes);
        grupoVista.selectedToggleProperty().addListener((obs, antes, ahora) -> {
            if (ahora == null) {
                // Pulsar el botón que ya está elegido lo desmarcaría: se vuelve a marcar
                if (antes != null) {
                    antes.setSelected(true);
                }
                return;
            }
            boolean aAnual = ahora == tglAnio;
            if (aAnual != anual) {
                cambiarVista(aAnual);
            }
        });
    }

    /**
     * Qué hacer cuando cambia la vista, para escribir en la barra superior de dónde estamos
     * @param accion: recibe el texto de las migas de pan (por ejemplo "Gráficas / Resumen mensual")
     */
    public void setAlCambiarMiga(Consumer<String> accion) {
        this.alCambiarMiga = accion;
    }

    /**
     * Qué hacer para enseñar, en la barra superior, la moneda y el tema (solo en la vista anual)
     * @param accion: recibe el texto (por ejemplo "EUR · Tema claro") o un texto vacío para esconderlo
     */
    public void setAlCambiarInfo(Consumer<String> accion) {
        this.alCambiarInfo = accion;
    }

    /**
     * Pinta la pantalla por primera vez, con el mes actual. Se llama después de conectar las acciones
     * de arriba, para que la barra superior se actualice.
     */
    public void iniciar() {
        refrescar();
    }

    /**
     * Pasa de la vista mensual a la anual o al revés, conservando el periodo: del mes se pasa a su año
     * y del año al mes actual (o a diciembre si es un año pasado).
     * @param aAnual: true para ir a la vista anual
     */
    private void cambiarVista(boolean aAnual) {
        anual = aAnual;
        if (aAnual) {
            anio = mes.getYear();
        } else if (mes.getYear() != anio) {
            YearMonth hoy = YearMonth.now();
            mes = YearMonth.of(anio, anio == hoy.getYear() ? hoy.getMonthValue() : 12);
        }
        refrescar();
    }

    /**
     * Va al mes anterior, o al año anterior en la vista anual
     */
    @FXML
    private void periodoAnterior() {
        if (anual) {
            anio--;
        } else {
            mes = mes.minusMonths(1);
        }
        refrescar();
    }

    /**
     * Va al mes siguiente, o al año siguiente en la vista anual
     */
    @FXML
    private void periodoSiguiente() {
        if (anual) {
            anio++;
        } else {
            mes = mes.plusMonths(1);
        }
        refrescar();
    }

    /**
     * Guarda en un archivo CSV el resumen que se está viendo: la tabla de la vista (cuenta por cuenta o mes a mes)
     * y, en la mensual, también el gasto por categoría.
     */
    @FXML
    private void exportar() {
        if (anual ? datosAnio == null : datosMes == null) {
            return;
        }
        FileChooser selector = new FileChooser();
        selector.setTitle("Exportar resumen");
        String sufijo = cuenta == null ? "" : "-" + cuenta.getNombre().replaceAll("[^\\p{L}\\p{N}-]+", "_");
        selector.setInitialFileName((anual ? "resumen-anual-" + anio : "resumen-mensual-" + mes) + sufijo + ".csv");
        selector.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV separado por punto y coma", "*.csv"));
        File destino = selector.showSaveDialog(raiz.getScene().getWindow());
        if (destino == null) {
            return;
        }
        String csv = anual ? ExportadorCsv.generarResumenAnual(datosAnio) : ExportadorCsv.generarResumenMensual(datosMes);
        try {
            Files.write(destino.toPath(), (BOM + csv).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            Alert error = new Alert(Alert.AlertType.ERROR, "No se pudo guardar el archivo: " + e.getMessage());
            error.setTitle("MisCuentas");
            error.setHeaderText("No se pudo exportar");
            error.initOwner(raiz.getScene().getWindow());
            error.getDialogPane().getStylesheets().add(getClass().getResource("/screens/styles/application.css").toExternalForm());
            error.showAndWait();
        }
    }

    /**
     * Elige la cuenta que se resume
     * @param elegida: cuenta, o null para todas
     */
    private void elegirCuenta(Cuenta elegida) {
        cuenta = elegida;
        refrescar();
    }

    /**
     * Vuelve a calcular los datos de la vista actual y pinta toda la pantalla
     */
    private void refrescar() {
        Usuario usuario = SesionActual.getUsuario();
        if (usuario == null) {
            return;
        }
        moneda = usuario.getMoneda();
        cuentas = cuentaDAO.listarPorUsuario(usuario.getId());
        // Si la cuenta elegida ya no está (se dio de baja), se vuelve a todas
        if (cuenta != null && cuentas.stream().noneMatch(c -> c.getId() == cuenta.getId())) {
            cuenta = null;
        }
        pintarMenuCuentas();
        if (anual) {
            datosAnio = servicio.anual(usuario, cuenta, anio);
            pintarAnual(usuario, datosAnio);
        } else {
            datosMes = servicio.mensual(usuario, cuenta, mes);
            pintarMensual(datosMes);
        }
    }

    /**
     * Rellena el selector de cuenta: todas más cada cuenta activa
     */
    private void pintarMenuCuentas() {
        menuCuenta.setText(cuenta == null ? "Todas las cuentas" : cuenta.getNombre());
        menuCuenta.getItems().clear();
        MenuItem todas = new MenuItem("Todas las cuentas");
        todas.setOnAction(e -> elegirCuenta(null));
        menuCuenta.getItems().add(todas);
        for (Cuenta c : cuentas) {
            MenuItem item = new MenuItem(c.getNombre());
            item.setOnAction(e -> elegirCuenta(c));
            menuCuenta.getItems().add(item);
        }
    }

    // ===== Vista mensual =====

    /**
     * Pinta la vista mensual
     * @param d: datos del mes
     */
    private void pintarMensual(Mensual d) {
        alCambiarMiga.accept("Gráficas / Resumen mensual");
        alCambiarInfo.accept("");

        // Cabecera: en la vista mensual solo lleva el título
        mostrar(lblEtiqueta, false);
        mostrar(lblSubtitulo, false);
        lblTitulo.setText("Entenderlo es el primer paso.");
        pintarPeriodoMensual(d.meses());

        // Tarjetas
        lblIngresosTitulo.setText("Ingresos");
        lblIngresos.setText(Formato.moneda(d.ingresos(), moneda));
        lblIngresosDetalle.setText(d.cuentasConIngresos().isEmpty() ? "Sin ingresos este mes" : Formato.lista(d.cuentasConIngresos()));
        lblGastosTitulo.setText("Gastos");
        lblGastos.setText(Formato.moneda(d.gastos(), moneda));
        lblGastosDetalle.setText(detalleGastos(d));
        lblBalanceTitulo.setText("Balance neto");
        lblBalance.setText(Formato.monedaConSigno(d.balance(), moneda));
        lblBalanceDetalle.setText("Ingresos menos gastos");
        pintarTasa(d.tasaAhorro(), "De tus ingresos del mes", "Aún no hay ingresos este mes");

        // Gráficas
        ajustarColumnas(57, 43);
        lblBarrasSubtitulo.setText("Por semana · " + Formato.mesConAnio(d.mes()));
        List<String> etiquetas = new ArrayList<>();
        List<BigDecimal> ingresos = new ArrayList<>();
        List<BigDecimal> gastos = new ArrayList<>();
        for (GraficasService.Semana s : d.semanas()) {
            etiquetas.add(s.etiqueta());
            ingresos.add(s.ingresos());
            gastos.add(s.gastos());
        }
        barras.setDatos(etiquetas, ingresos, gastos, -1, moneda);
        mostrar(tarjetaCategorias, true);
        mostrar(tarjetaAcumulado, false);
        pintarCategorias(d);

        // Tabla cuenta por cuenta
        lblTablaTitulo.setText("El mes, cuenta por cuenta");
        lblTablaPastilla.setText(contar(d.numMovimientos(), "movimiento", "movimientos"));
        mostrar(lblTablaPastilla, true);
        mostrar(cajaMejorMes, false);
        pintarTablaMensual(d);
    }

    /**
     * Rellena el selector de mes con los meses que tienen movimientos (más el actual) y activa las flechas
     * @param meses: meses elegibles, del más reciente al más antiguo
     */
    private void pintarPeriodoMensual(List<YearMonth> meses) {
        menuPeriodo.setText(Formato.mesConAnio(mes));
        menuPeriodo.getItems().clear();
        for (YearMonth m : meses) {
            MenuItem item = new MenuItem(Formato.mesConAnio(m));
            item.setOnAction(e -> {
                mes = m;
                refrescar();
            });
            menuPeriodo.getItems().add(item);
        }
        btnSiguiente.setDisable(mes.equals(meses.get(0)));
        btnAnterior.setDisable(mes.equals(meses.get(meses.size() - 1)));
    }

    /**
     * @param d: datos del mes
     * @return Devuelve la frase bajo los gastos: cuánto más o menos que el mes anterior
     */
    private String detalleGastos(Mensual d) {
        String anterior = Formato.nombreMes(d.mes().minusMonths(1));
        if (d.movimientosMesAnterior() == 0) {
            return "Sin datos de " + anterior;
        }
        BigDecimal diferencia = d.gastos().subtract(d.gastosMesAnterior());
        if (diferencia.signum() == 0) {
            return "Igual que en " + anterior;
        }
        return Formato.monedaCorta(diferencia.abs(), moneda) + (diferencia.signum() < 0 ? " menos" : " más") + " que en " + anterior;
    }

    /**
     * Rellena la tarjeta de la tasa de ahorro
     * @param tasa: porcentaje ahorrado (puede ser negativo), o null si no hay ingresos
     * @param detalle: frase cuando hay tasa
     * @param sinIngresos: frase cuando no hay ingresos
     */
    private void pintarTasa(BigDecimal tasa, String detalle, String sinIngresos) {
        if (tasa == null) {
            lblTasa.setText("\u2014");
            lblTasaDetalle.setText(sinIngresos);
            return;
        }
        lblTasa.setText(textoTasa(tasa));
        lblTasaDetalle.setText(detalle);
    }

    /**
     * @param tasa: porcentaje (puede ser negativo)
     * @return Devuelve el porcentaje como texto, con el signo menos de verdad si es negativo
     */
    private String textoTasa(BigDecimal tasa) {
        return tasa.signum() < 0 ? MENOS + Formato.porcentaje(tasa.abs()) : Formato.porcentaje(tasa);
    }

    /**
     * Pinta el anillo con el gasto por categoría, su leyenda y la frase de la categoría que más pesa
     * @param d: datos del mes
     */
    private void pintarCategorias(Mensual d) {
        lblCategoriasMes.setText(Formato.nombreMesMayuscula(d.mes()));
        listaCategorias.getChildren().clear();

        List<Porcion> trozos = agrupar(d.categorias());
        boolean agrupado = d.categorias().size() > MAX_CATEGORIAS;
        List<String> nombres = new ArrayList<>();
        List<BigDecimal> valores = new ArrayList<>();
        List<Color> colores = new ArrayList<>();
        for (int i = 0; i < trozos.size(); i++) {
            Porcion t = trozos.get(i);
            Color color = agrupado && i == trozos.size() - 1 ? Paleta.OTRAS : Paleta.categoria(i);
            nombres.add(t.categoria());
            valores.add(t.importe());
            colores.add(color);
            listaCategorias.getChildren().add(filaLeyenda(color, t));
        }
        donut.setDatos(nombres, valores, colores, Formato.monedaCorta(d.gastos(), moneda), "GASTO TOTAL", moneda);

        boolean hayGastos = d.gastos().signum() > 0 && !d.categorias().isEmpty();
        mostrar(notaCategoria, hayGastos);
        if (hayGastos) {
            Porcion mayor = d.categorias().get(0);
            BigDecimal porcentaje = mayor.importe().multiply(CIEN).divide(d.gastos(), 1, RoundingMode.HALF_UP);
            lblNotaCategoria.setText(mayor.categoria() + " representa el " + Formato.porcentajeCorto(porcentaje) + " de tus gastos.");
            notaIcono.getChildren().setAll(icono(IconosCategoria.para(mayor.categoria())));
        } else {
            Label vacio = new Label("Aún no hay gastos en " + Formato.nombreMes(d.mes()) + ".");
            vacio.getStyleClass().add("texto-suave");
            vacio.setWrapText(true);
            listaCategorias.getChildren().add(vacio);
        }
    }

    /**
     * Deja como mucho MAX_CATEGORIAS trozos: si hay más, las últimas categorías se suman en una llamada "Otras"
     * @param categorias: gasto por categoría, de mayor a menor
     * @return Devuelve los trozos del anillo
     */
    private List<Porcion> agrupar(List<Porcion> categorias) {
        if (categorias.size() <= MAX_CATEGORIAS) {
            return categorias;
        }
        List<Porcion> trozos = new ArrayList<>(categorias.subList(0, MAX_CATEGORIAS - 1));
        BigDecimal resto = BigDecimal.ZERO;
        for (Porcion p : categorias.subList(MAX_CATEGORIAS - 1, categorias.size())) {
            resto = resto.add(p.importe());
        }
        trozos.add(new Porcion("Otras", resto));
        return trozos;
    }

    /**
     * Crea una fila de la leyenda del anillo: punto de color, nombre e importe
     * @param color: color del trozo
     * @param trozo: categoría con su gasto
     * @return Devuelve la fila
     */
    private HBox filaLeyenda(Color color, Porcion trozo) {
        Label nombre = new Label(trozo.categoria());
        nombre.getStyleClass().add("leyenda-nombre");
        nombre.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(nombre, Priority.ALWAYS);
        Label importe = new Label(Formato.monedaCorta(trozo.importe(), moneda));
        importe.getStyleClass().add("leyenda-importe");
        importe.setMinWidth(Region.USE_PREF_SIZE);
        HBox fila = new HBox(8, new Circle(5, color), nombre, importe);
        fila.setAlignment(Pos.CENTER_LEFT);
        return fila;
    }

    /**
     * Pinta la tabla cuenta por cuenta: saldo inicial, ingresos, gastos, balance y saldo al terminar el mes, con una fila de total
     * @param d: datos del mes
     */
    private void pintarTablaMensual(Mensual d) {
        listaTabla.getChildren().clear();
        boolean vacia = d.cuentas().isEmpty();
        lblVacio.setText("Aún no tienes cuentas. Añade la primera con \"Añadir cuenta\" en la barra lateral.");
        mostrar(lblVacio, vacia);
        if (vacia) {
            return;
        }
        listaTabla.getChildren().add(cabeceraTabla(ANCHOS_MES, TITULOS_MES));
        for (FilaCuentaMes f : d.cuentas()) {
            listaTabla.getChildren().add(filaTablaMensual(f.cuenta().getNombre(), f.saldoInicial(), f.ingresos(),
                    f.gastos(), f.balance(), f.saldoFinal(), false));
        }
        listaTabla.getChildren().add(filaTablaMensual("Total", d.saldoInicialTotal(), d.ingresos(), d.gastos(),
                d.balance(), d.saldoFinalTotal(), true));
    }

    /**
     * Crea una fila de la tabla mensual
     * @param nombre: cuenta, o "Total"
     * @param saldoInicial: saldo al empezar el mes
     * @param ingresos: ingresos del mes
     * @param gastos: gastos del mes
     * @param balance: ingresos menos gastos
     * @param saldoFinal: saldo al terminar el mes
     * @param total: true si es la fila del total
     * @return Devuelve la fila
     */
    private GridPane filaTablaMensual(String nombre, BigDecimal saldoInicial, BigDecimal ingresos, BigDecimal gastos,
            BigDecimal balance, BigDecimal saldoFinal, boolean total) {
        GridPane fila = nuevaFilaTabla(ANCHOS_MES, "tabla-fila");
        if (total) {
            fila.getStyleClass().add("tabla-total");
        }
        String numero = total ? "tabla-numero-fuerte" : "tabla-numero";
        fila.add(celda(nombre, "tabla-cuenta", false), 0, 0);
        fila.add(celda(Formato.moneda(saldoInicial, moneda), numero, true), 1, 0);
        fila.add(celda(Formato.moneda(ingresos, moneda), numero, true), 2, 0);
        fila.add(celda(Formato.moneda(gastos, moneda), numero, true), 3, 0);
        fila.add(celda(Formato.monedaConSigno(balance, moneda), claseBalance(balance), true), 4, 0);
        fila.add(celda(Formato.moneda(saldoFinal, moneda), "tabla-numero-fuerte", true), 5, 0);
        return fila;
    }

    // ===== Vista anual =====

    /**
     * Pinta la vista anual
     * @param usuario: usuario que ha entrado
     * @param d: datos del año
     */
    private void pintarAnual(Usuario usuario, Anual d) {
        alCambiarMiga.accept("Gráficas / Resumen anual");
        String tema = usuario.getTema() == null || usuario.getTema().isBlank() ? "claro" : usuario.getTema();
        alCambiarInfo.accept(moneda + " · Tema " + tema);

        // Cabecera
        mostrar(lblEtiqueta, true);
        mostrar(lblSubtitulo, true);
        lblTitulo.setText("Tu año, con una mirada más amplia.");
        lblSubtitulo.setText("Cada mes cuenta. Mira el recorrido que has construido con tu dinero.");
        pintarPeriodoAnual(d);

        // Tarjetas
        String rango = rango(d);
        lblIngresosTitulo.setText("Ingresos acumulados");
        lblIngresos.setText(Formato.moneda(d.ingresos(), moneda));
        lblIngresosDetalle.setText(rango + " · " + contar(d.numMeses(), "mes", "meses"));
        lblGastosTitulo.setText("Gastos acumulados");
        lblGastos.setText(Formato.moneda(d.gastos(), moneda));
        lblGastosDetalle.setText("Media mensual: " + Formato.moneda(d.mediaGastos(), moneda));
        lblBalanceTitulo.setText("Balance acumulado");
        lblBalance.setText(Formato.monedaConSigno(d.balance(), moneda));
        lblBalanceDetalle.setText("Saldo inicial de " + d.anio() + ": " + Formato.moneda(d.saldoInicial(), moneda));
        pintarTasa(d.tasaAhorro(), "Sobre tus ingresos del año", "Aún no hay ingresos este año");

        // Gráficas
        ajustarColumnas(64, 36);
        lblBarrasSubtitulo.setText("Por mes · " + rango + " de " + d.anio());
        List<String> etiquetas = new ArrayList<>();
        List<BigDecimal> ingresos = new ArrayList<>();
        List<BigDecimal> gastos = new ArrayList<>();
        for (FilaMes f : d.meses()) {
            etiquetas.add(Formato.mesCortoMayuscula(f.mes().getMonthValue()));
            ingresos.add(f.ingresos());
            gastos.add(f.gastos());
        }
        YearMonth hoy = YearMonth.now();
        barras.setDatos(etiquetas, ingresos, gastos, d.anio() == hoy.getYear() ? hoy.getMonthValue() - 1 : -1, moneda);
        mostrar(tarjetaCategorias, false);
        mostrar(tarjetaAcumulado, true);
        pintarAcumulado(d, etiquetas);

        // Tabla mes a mes
        lblTablaTitulo.setText("Mes a mes");
        mostrar(lblTablaPastilla, false);
        d.mejorMes().ifPresentOrElse(mejor -> {
            lblMejorMes.setText(Formato.nombreMesMayuscula(mejor.mes()) + ": tu mayor ahorro");
            mostrar(cajaMejorMes, true);
        }, () -> mostrar(cajaMejorMes, false));
        pintarTablaAnual(d);
    }

    /**
     * Rellena el selector de año con los años que tienen movimientos (más el actual) y activa las flechas
     * @param d: datos del año
     */
    private void pintarPeriodoAnual(Anual d) {
        List<Integer> anios = d.mesesDisponibles().stream().map(YearMonth::getYear).distinct().toList();
        menuPeriodo.setText(String.valueOf(anio));
        menuPeriodo.getItems().clear();
        for (int a : anios) {
            MenuItem item = new MenuItem(String.valueOf(a));
            item.setOnAction(e -> {
                anio = a;
                refrescar();
            });
            menuPeriodo.getItems().add(item);
        }
        btnSiguiente.setDisable(anio >= anios.get(0));
        btnAnterior.setDisable(anio <= anios.get(anios.size() - 1));
    }

    /**
     * @param d: datos del año
     * @return Devuelve los meses que cubre el resumen: "Enero a septiembre", o solo "Enero" si es uno
     */
    private String rango(Anual d) {
        List<FilaMes> meses = d.meses();
        String primero = Formato.nombreMesMayuscula(meses.get(0).mes());
        if (meses.size() == 1) {
            return primero;
        }
        return primero + " a " + Formato.nombreMes(meses.get(meses.size() - 1).mes());
    }

    /**
     * Pinta la tarjeta del balance acumulado: el importe, la línea mes a mes y el porcentaje conservado
     * @param d: datos del año
     * @param etiquetas: nombre corto de cada mes
     */
    private void pintarAcumulado(Anual d, List<String> etiquetas) {
        lblAcumuladoValor.setText(d.balance().signum() < 0 ? Formato.monedaConSigno(d.balance(), moneda) : Formato.moneda(d.balance(), moneda));
        lblAcumuladoDetalle.setText("Balance acumulado · Saldo inicial anual: " + Formato.monedaCorta(d.saldoInicial(), moneda));
        linea.setDatos(etiquetas, d.acumulado(), moneda);

        BigDecimal tasa = d.tasaAhorro();
        mostrar(lblConservado, tasa != null);
        if (tasa != null) {
            lblConservado.setText(tasa.signum() >= 0 ? Formato.porcentaje(tasa) + " de tus ingresos conservados"
                    : "Has gastado más de lo que has ingresado");
        }
    }

    /**
     * Pinta la tabla mes a mes con una fila de total del año
     * @param d: datos del año
     */
    private void pintarTablaAnual(Anual d) {
        listaTabla.getChildren().clear();
        mostrar(lblVacio, false);
        listaTabla.getChildren().add(cabeceraTabla(ANCHOS_ANIO, TITULOS_ANIO));
        for (FilaMes f : d.meses()) {
            listaTabla.getChildren().add(filaTablaAnual(Formato.nombreMesMayuscula(f.mes()), f.ingresos(), f.gastos(),
                    f.balance(), f.tasaAhorro(), false));
        }
        listaTabla.getChildren().add(filaTablaAnual("Total " + d.anio(), d.ingresos(), d.gastos(), d.balance(),
                d.tasaAhorro(), true));
    }

    /**
     * Crea una fila de la tabla anual
     * @param nombre: mes, o "Total"
     * @param ingresos: ingresos
     * @param gastos: gastos
     * @param balance: ingresos menos gastos
     * @param tasa: porcentaje ahorrado, o null si no hubo ingresos
     * @param total: true si es la fila del total
     * @return Devuelve la fila
     */
    private GridPane filaTablaAnual(String nombre, BigDecimal ingresos, BigDecimal gastos, BigDecimal balance,
            BigDecimal tasa, boolean total) {
        GridPane fila = nuevaFilaTabla(ANCHOS_ANIO, "tabla-fila");
        if (total) {
            fila.getStyleClass().add("tabla-total");
        }
        String numero = total ? "tabla-numero-fuerte" : "tabla-numero";
        fila.add(celda(nombre, "tabla-cuenta", false), 0, 0);
        fila.add(celda(Formato.moneda(ingresos, moneda), numero, true), 1, 0);
        fila.add(celda(Formato.moneda(gastos, moneda), numero, true), 2, 0);
        fila.add(celda(Formato.monedaConSigno(balance, moneda), claseBalance(balance), true), 3, 0);
        fila.add(celda(tasa == null ? "\u2014" : textoTasa(tasa), total ? "tabla-numero-fuerte" : "tabla-texto", true), 4, 0);
        return fila;
    }

    // ===== Piezas comunes =====

    /**
     * Cambia el reparto de ancho entre la gráfica de barras y la tarjeta de su derecha
     * @param izquierda: % de ancho de la gráfica de barras
     * @param derecha: % de ancho de la otra tarjeta
     */
    private void ajustarColumnas(double izquierda, double derecha) {
        gridGraficos.getColumnConstraints().get(0).setPercentWidth(izquierda);
        gridGraficos.getColumnConstraints().get(1).setPercentWidth(derecha);
    }

    /**
     * Muestra u oculta un nodo sin que deje hueco cuando está oculto
     * @param nodo: nodo a mostrar u ocultar
     * @param visible: true para mostrarlo
     */
    private static void mostrar(Node nodo, boolean visible) {
        nodo.setVisible(visible);
        nodo.setManaged(visible);
    }

    /**
     * @param balance: importe del balance
     * @return Devuelve la clase CSS según sea positivo (verde), negativo (rojo) o cero (gris)
     */
    private static String claseBalance(BigDecimal balance) {
        if (balance.signum() > 0) {
            return "balance-positivo";
        }
        return balance.signum() < 0 ? "balance-negativo" : "balance-cero";
    }

    /**
     * @param anchos: ancho de cada columna en %
     * @param titulos: texto de cada columna; la primera va a la izquierda y las demás a la derecha
     * @return Devuelve la fila de títulos de la tabla
     */
    private GridPane cabeceraTabla(double[] anchos, String[] titulos) {
        GridPane fila = nuevaFilaTabla(anchos, "tabla-cabecera");
        for (int i = 0; i < titulos.length; i++) {
            fila.add(celda(titulos[i], "tabla-titulo", i > 0), i, 0);
        }
        return fila;
    }

    /**
     * Crea una fila vacía de tabla con los anchos de columna indicados (los mismos para la cabecera y las filas)
     * @param anchos: ancho de cada columna en %
     * @param clase: clase CSS de la fila
     * @return Devuelve la fila
     */
    private GridPane nuevaFilaTabla(double[] anchos, String clase) {
        GridPane fila = new GridPane();
        fila.getStyleClass().add(clase);
        for (double ancho : anchos) {
            ColumnConstraints columna = new ColumnConstraints();
            columna.setPercentWidth(ancho);
            columna.setHgrow(Priority.ALWAYS);
            fila.getColumnConstraints().add(columna);
        }
        RowConstraints centrada = new RowConstraints();
        centrada.setValignment(VPos.CENTER);
        fila.getRowConstraints().add(centrada);
        return fila;
    }

    /**
     * Crea una celda de texto que se corta con puntos suspensivos si no cabe
     * @param texto: contenido
     * @param clase: clase CSS
     * @param derecha: true para alinear el texto a la derecha
     * @return Devuelve la etiqueta
     */
    private Label celda(String texto, String clase, boolean derecha) {
        Label etiqueta = new Label(texto);
        etiqueta.getStyleClass().add(clase);
        etiqueta.setMaxWidth(Double.MAX_VALUE);
        etiqueta.setAlignment(derecha ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        GridPane.setHalignment(etiqueta, derecha ? HPos.RIGHT : HPos.LEFT);
        return etiqueta;
    }

    /**
     * Crea un icono de 16 píxeles
     * @param contenido: trazo SVG en una cuadrícula de 24x24
     * @return Devuelve el icono
     */
    private Group icono(String contenido) {
        SVGPath trazo = new SVGPath();
        trazo.setContent(contenido);
        trazo.getStyleClass().add("icono");
        trazo.setScaleX(ESCALA_ICONO);
        trazo.setScaleY(ESCALA_ICONO);
        return new Group(trazo);
    }

    /**
     * Escribe una cantidad con su palabra en singular o plural
     * @param n: cantidad
     * @param singular: palabra si es 1
     * @param plural: palabra si no es 1
     * @return Devuelve el texto, por ejemplo "1 mes" o "9 meses"
     */
    private String contar(int n, String singular, String plural) {
        return n + " " + (n == 1 ? singular : plural);
    }
}
