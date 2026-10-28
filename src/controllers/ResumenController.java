package controllers;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import javafx.fxml.FXML;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Group;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.stage.FileChooser;
import models.Cuenta;
import models.Usuario;
import service.auth.SesionActual;
import service.resumen.ExportadorCsv;
import service.resumen.ResumenService;
import service.resumen.ResumenService.FilaCuenta;
import service.resumen.ResumenService.FilaMovimiento;
import service.resumen.ResumenService.Resumen;
import util.Formato;

/**
 * Controlador de Resumen.fxml. Es una sola pantalla para la vista general (todas las cuentas) y para
 * cada cuenta: se le dice qué mostrar con mostrarTodas() o mostrarCuenta(cuenta) y el resto (tarjetas,
 * movimientos, filtros, mes y exportación) funciona igual. Los datos los calcula ResumenService.
 *
 * @author Fco Padilla
 */
public class ResumenController {

    // Trazos de los iconos que se crean en código (cuadrícula de 24x24)
    private static final String ICONO_BANCO = "M3 21 H21 M5 21 V11 M9 21 V11 M15 21 V11 M19 21 V11 M12 3 L3 9 H21 Z";
    private static final String ICONO_EFECTIVO = "M4 7 H19 A1 1 0 0 1 20 8 V19 A1 1 0 0 1 19 20 H5 A2 2 0 0 1 3 18 V6 A2 2 0 0 1 5 4 H17 M16 14 L16.01 14";
    private static final String ICONO_INGRESO = "M17 7 L7 17 M17 17 H7 V7";
    private static final String ICONO_GASTO = "M7 17 L17 7 M7 7 H17 V17";
    private static final String ICONO_SIGUIENTE = "M9 6 L15 12 L9 18";
    // 24 de la cuadrícula del icono escalados a 16 píxeles
    private static final double ESCALA_ICONO = 0.67;
    // Padding horizontal de .tarjeta-cuenta (16 a cada lado), para que el contenido ocupe todo el botón
    private static final double PADDING_TARJETA = 32;
    // Máximo de filas que se pintan a la vez en la tabla (el buscador permite llegar a las demás)
    private static final int MAX_FILAS = 50;
    // Ancho de cada columna de la tabla en %: fecha, concepto, categoría, cuenta, importe
    private static final double[] ANCHOS = { 10, 38, 18, 18, 16 };
    // Para que Excel reconozca que el CSV está en UTF-8
    private static final String BOM = "\uFEFF";

    @FXML private ScrollPane raiz;
    @FXML private Label lblEtiqueta;
    @FXML private Label lblTitulo;
    @FXML private Label lblSubtitulo;
    @FXML private MenuButton menuMes;
    @FXML private Label lblSaldoTitulo;
    @FXML private Label lblSaldoTotal;
    @FXML private Label lblSaldoDetalle;
    @FXML private Label lblIngresos;
    @FXML private Label lblIngresosDetalle;
    @FXML private Label lblGastos;
    @FXML private Label lblGastosDetalle;
    @FXML private Label lblBalance;
    @FXML private Label lblBalanceDetalle;
    @FXML private GridPane gridCuentas;
    @FXML private Label lblNumMovimientos;
    @FXML private TextField txtBuscar;
    @FXML private MenuButton menuTipo;
    @FXML private VBox listaMovimientos;
    @FXML private Label lblVacio;
    @FXML private Label lblMas;

    private final ResumenService servicio = new ResumenService();
    private Consumer<Cuenta> alElegirCuenta = c -> { };
    // Cuenta que se muestra, o null para la vista general
    private Cuenta cuenta;
    private YearMonth mes = YearMonth.now();
    private Resumen datos;
    // Filtro de tipo de la tabla: null (todos), "ingreso" o "gasto"
    private String tipoFiltro;
    private String moneda = "EUR";

    /**
     * Hace que la tabla se actualice al escribir en el buscador
     */
    @FXML
    private void initialize() {
        txtBuscar.textProperty().addListener((obs, antes, ahora) -> pintarMovimientos());
    }

    /**
     * Qué hacer cuando se pulsa la tarjeta de una cuenta
     * @param accion: recibe la cuenta elegida
     */
    public void setAlElegirCuenta(Consumer<Cuenta> accion) {
        this.alElegirCuenta = accion;
    }

    /**
     * Muestra la vista general: la suma de todas las cuentas
     */
    public void mostrarTodas() {
        this.cuenta = null;
        refrescar();
    }

    /**
     * Muestra el resumen de una sola cuenta, con el mismo formato que la vista general
     * @param cuenta: cuenta a mostrar
     */
    public void mostrarCuenta(Cuenta cuenta) {
        this.cuenta = cuenta;
        refrescar();
    }

    /**
     * Muestra todos los tipos de movimiento
     */
    @FXML
    private void verTodos() {
        filtrarTipo(null, "Todos los tipos");
    }

    /**
     * Muestra solo los ingresos
     */
    @FXML
    private void verIngresos() {
        filtrarTipo("ingreso", "Ingresos");
    }

    /**
     * Muestra solo los gastos
     */
    @FXML
    private void verGastos() {
        filtrarTipo("gasto", "Gastos");
    }

    /**
     * Guarda en un archivo CSV los movimientos del mes elegido (de la cuenta, o de todas en la vista
     * general). No tiene en cuenta el buscador ni el filtro de tipo.
     */
    @FXML
    private void exportar() {
        if (datos == null) {
            return;
        }
        FileChooser selector = new FileChooser();
        selector.setTitle("Exportar movimientos");
        String base = cuenta == null ? "movimientos" : "movimientos-" + cuenta.getNombre().replaceAll("[^\\p{L}\\p{N}-]+", "_");
        selector.setInitialFileName(base + "-" + mes + ".csv");
        selector.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV separado por punto y coma", "*.csv"));
        File destino = selector.showSaveDialog(raiz.getScene().getWindow());
        if (destino == null) {
            return;
        }
        try {
            Files.write(destino.toPath(), (BOM + ExportadorCsv.generar(datos.movimientos())).getBytes(StandardCharsets.UTF_8));
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
     * Vuelve a calcular los datos del mes y pinta toda la pantalla
     */
    private void refrescar() {
        Usuario usuario = SesionActual.getUsuario();
        if (usuario == null) {
            return;
        }
        moneda = usuario.getMoneda();
        datos = servicio.calcular(usuario, cuenta, mes);
        pintarCabecera(usuario);
        pintarMeses();
        pintarTarjetas();
        pintarCuentas();
        pintarMovimientos();
    }

    /**
     * Pone el título según se muestre la vista general o una cuenta
     * @param usuario: usuario que ha entrado
     */
    private void pintarCabecera(Usuario usuario) {
        String nombrePila = usuario.getNombre().trim().split("\\s+")[0];
        String nombreMes = Formato.nombreMes(mes);
        if (cuenta == null) {
            lblEtiqueta.setText("VISTA GENERAL");
            lblTitulo.setText("Todo tu dinero, en un solo lugar.");
            lblSubtitulo.setText("Hola, " + nombrePila + ". Este es el pulso de tus finanzas en " + nombreMes + ".");
        } else {
            lblEtiqueta.setText("efectivo".equals(cuenta.getTipo()) ? "EFECTIVO" : "CUENTA BANCARIA");
            lblTitulo.setText(cuenta.getNombre());
            lblSubtitulo.setText("Hola, " + nombrePila + ". Así se ha movido esta cuenta en " + nombreMes + ".");
        }
    }

    /**
     * Rellena el selector de mes con los meses que tienen movimientos (más el actual)
     */
    private void pintarMeses() {
        menuMes.setText(Formato.mesConAnio(mes));
        menuMes.getItems().clear();
        for (YearMonth m : datos.meses()) {
            MenuItem item = new MenuItem(Formato.mesConAnio(m));
            item.setOnAction(e -> {
                mes = m;
                refrescar();
            });
            menuMes.getItems().add(item);
        }
    }

    /**
     * Rellena las cuatro tarjetas de arriba: saldo, ingresos, gastos y balance
     */
    private void pintarTarjetas() {
        lblSaldoTitulo.setText(cuenta == null ? "Saldo total disponible" : "Saldo disponible");
        lblSaldoTotal.setText(Formato.moneda(datos.saldoTotal(), moneda));
        lblSaldoDetalle.setText(detalleSaldo());

        lblIngresos.setText(Formato.moneda(datos.ingresos(), moneda));
        lblIngresosDetalle.setText(datos.numIngresos() == 0 ? "Sin ingresos registrados"
                : contar(datos.numIngresos(), "ingreso registrado", "ingresos registrados"));

        lblGastos.setText(Formato.moneda(datos.gastos(), moneda));
        lblGastosDetalle.setText(datos.numGastos() == 0 ? "Sin gastos registrados"
                : contar(datos.numGastos(), "gasto registrado", "gastos registrados"));

        lblBalance.setText(Formato.monedaConSigno(datos.balance(), moneda));
        BigDecimal conservado = datos.porcentajeConservado();
        if (conservado != null) {
            lblBalanceDetalle.setText("Has conservado el " + Formato.porcentaje(conservado) + " de tus ingresos");
        } else if (datos.balance().signum() < 0) {
            lblBalanceDetalle.setText("Has gastado más de lo que has ingresado");
        } else {
            lblBalanceDetalle.setText("Aún no hay ingresos este mes");
        }
    }

    /**
     * @return Devuelve la frase bajo el saldo: en qué cuentas está repartido
     */
    private String detalleSaldo() {
        if (cuenta != null) {
            return "efectivo".equals(cuenta.getTipo()) ? "En tu cartera" : "En tu cuenta bancaria";
        }
        int bancos = datos.numBancos();
        int efectivo = datos.numEfectivo();
        if (bancos == 0 && efectivo == 0) {
            return "Aún no tienes cuentas";
        }
        String partes = bancos == 0 ? "" : contar(bancos, "banco", "bancos");
        if (efectivo > 0) {
            partes = bancos == 0 ? "efectivo" : partes + " y efectivo";
        }
        return "En " + partes;
    }

    /**
     * Pinta una tarjeta por cuenta (solo en la vista general; en la vista de una cuenta se oculta la fila)
     */
    private void pintarCuentas() {
        gridCuentas.getChildren().clear();
        boolean visible = cuenta == null;
        gridCuentas.setVisible(visible);
        gridCuentas.setManaged(visible);
        if (!visible) {
            return;
        }
        if (datos.cuentas().isEmpty()) {
            Label aviso = new Label("Aún no tienes cuentas. Añade la primera con \"Añadir cuenta\" en la barra lateral.");
            aviso.getStyleClass().add("texto-suave");
            aviso.setWrapText(true);
            gridCuentas.add(aviso, 0, 0, 3, 1);
            return;
        }
        int i = 0;
        for (FilaCuenta fila : datos.cuentas()) {
            gridCuentas.add(crearTarjetaCuenta(fila), i % 3, i / 3);
            i++;
        }
    }

    /**
     * Crea la tarjeta de una cuenta: icono, nombre, tipo y saldo. Al pulsarla se abre la cuenta.
     * @param fila: cuenta con su saldo
     * @return Devuelve el botón de la tarjeta
     */
    private Button crearTarjetaCuenta(FilaCuenta fila) {
        Cuenta c = fila.cuenta();
        boolean efectivo = "efectivo".equals(c.getTipo());

        Label nombre = new Label(c.getNombre());
        nombre.getStyleClass().add("tarjeta-cuenta-nombre");
        Label tipo = new Label(efectivo ? "Tu cartera" : "Cuenta bancaria");
        tipo.getStyleClass().add("tarjeta-cuenta-tipo");
        VBox textos = new VBox(2, nombre, tipo);
        textos.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(textos, Priority.ALWAYS);

        Label saldo = new Label(Formato.moneda(fila.saldo(), c.getMoneda()));
        saldo.getStyleClass().add("tarjeta-cuenta-saldo");
        saldo.setMinWidth(Region.USE_PREF_SIZE);

        HBox contenido = new HBox(12, cajaIcono(efectivo ? ICONO_EFECTIVO : ICONO_BANCO), textos, saldo, icono(ICONO_SIGUIENTE));
        contenido.setAlignment(Pos.CENTER_LEFT);

        Button tarjeta = new Button();
        tarjeta.setGraphic(contenido);
        tarjeta.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        tarjeta.getStyleClass().add("tarjeta-cuenta");
        tarjeta.setMaxWidth(Double.MAX_VALUE);
        contenido.prefWidthProperty().bind(tarjeta.widthProperty().subtract(PADDING_TARJETA));
        tarjeta.setOnAction(e -> alElegirCuenta.accept(c));
        return tarjeta;
    }

    /**
     * Cambia el filtro de tipo de la tabla
     * @param tipo: null (todos), "ingreso" o "gasto"
     * @param texto: lo que pone el botón del filtro
     */
    private void filtrarTipo(String tipo, String texto) {
        tipoFiltro = tipo;
        menuTipo.setText(texto);
        pintarMovimientos();
    }

    /**
     * Pinta la tabla de movimientos con el filtro de tipo y el texto del buscador (que mira el concepto,
     * la categoría y la cuenta). Solo se pintan los primeros MAX_FILAS.
     */
    private void pintarMovimientos() {
        if (datos == null) {
            return;
        }
        listaMovimientos.getChildren().clear();
        String busqueda = txtBuscar.getText() == null ? "" : txtBuscar.getText().trim().toLowerCase(Locale.ROOT);
        List<FilaMovimiento> filas = datos.movimientos().stream()
                .filter(f -> tipoFiltro == null || tipoFiltro.equals(f.movimiento().getTipo()))
                .filter(f -> busqueda.isEmpty() || f.concepto().toLowerCase(Locale.ROOT).contains(busqueda)
                        || f.categoria().toLowerCase(Locale.ROOT).contains(busqueda)
                        || f.cuenta().toLowerCase(Locale.ROOT).contains(busqueda))
                .toList();

        lblNumMovimientos.setText(contar(filas.size(), "movimiento", "movimientos"));
        boolean vacia = filas.isEmpty();
        lblVacio.setVisible(vacia);
        lblVacio.setManaged(vacia);
        if (vacia) {
            lblVacio.setText(datos.movimientos().isEmpty() ? "No hay movimientos en " + Formato.nombreMes(mes) + "."
                    : "Ningún movimiento coincide con lo que buscas.");
        } else {
            listaMovimientos.getChildren().add(crearCabeceraTabla());
            for (FilaMovimiento fila : filas.subList(0, Math.min(MAX_FILAS, filas.size()))) {
                listaMovimientos.getChildren().add(crearFilaMovimiento(fila));
            }
        }
        boolean hayMas = filas.size() > MAX_FILAS;
        lblMas.setVisible(hayMas);
        lblMas.setManaged(hayMas);
        lblMas.setText("Se muestran los " + MAX_FILAS + " movimientos más recientes de " + filas.size()
                + ". Usa el buscador o el filtro para encontrar otros.");
    }

    /**
     * @return Devuelve la fila de títulos de la tabla (FECHA, CONCEPTO...)
     */
    private GridPane crearCabeceraTabla() {
        GridPane fila = nuevaFilaTabla("tabla-cabecera");
        String[] titulos = { "FECHA", "CONCEPTO", "CATEGORÍA", "CUENTA", "IMPORTE" };
        for (int i = 0; i < titulos.length; i++) {
            Label titulo = new Label(titulos[i]);
            titulo.getStyleClass().add("tabla-titulo");
            if (i == titulos.length - 1) {
                GridPane.setHalignment(titulo, HPos.RIGHT);
            }
            fila.add(titulo, i, 0);
        }
        return fila;
    }

    /**
     * Crea la fila de un movimiento: fecha, concepto con su icono, categoría, cuenta e importe con signo
     * @param fila: movimiento con los nombres de su categoría y su cuenta
     * @return Devuelve la fila
     */
    private GridPane crearFilaMovimiento(FilaMovimiento fila) {
        GridPane grid = nuevaFilaTabla("tabla-fila");

        Label fecha = celda(Formato.fechaCorta(fila.movimiento().getFecha()), "tabla-fecha");
        Label concepto = celda(fila.concepto(), "tabla-concepto");
        HBox conceptoConIcono = new HBox(10, cajaIcono(fila.esIngreso() ? ICONO_INGRESO : ICONO_GASTO), concepto);
        conceptoConIcono.setAlignment(Pos.CENTER_LEFT);

        BigDecimal importe = fila.esIngreso() ? fila.movimiento().getImporte() : fila.movimiento().getImporte().negate();
        Label valor = celda(Formato.monedaConSigno(importe, moneda), fila.esIngreso() ? "importe-ingreso" : "importe-gasto");
        GridPane.setHalignment(valor, HPos.RIGHT);

        grid.add(fecha, 0, 0);
        grid.add(conceptoConIcono, 1, 0);
        grid.add(celda(fila.categoria(), "tabla-texto"), 2, 0);
        grid.add(celda(fila.cuenta(), "tabla-texto"), 3, 0);
        grid.add(valor, 4, 0);
        return grid;
    }

    /**
     * Crea una fila vacía de la tabla con los anchos de columna de ANCHOS (los mismos para la cabecera y las filas)
     * @param clase: clase CSS de la fila
     * @return Devuelve la fila
     */
    private GridPane nuevaFilaTabla(String clase) {
        GridPane fila = new GridPane();
        fila.getStyleClass().add(clase);
        for (double ancho : ANCHOS) {
            ColumnConstraints columna = new ColumnConstraints();
            columna.setPercentWidth(ancho);
            columna.setHgrow(Priority.ALWAYS);
            fila.getColumnConstraints().add(columna);
        }
        // Todo centrado en vertical, para que el icono y el texto de una fila queden alineados
        RowConstraints centrada = new RowConstraints();
        centrada.setValignment(VPos.CENTER);
        fila.getRowConstraints().add(centrada);
        return fila;
    }

    /**
     * Crea una celda de texto que se corta con puntos suspensivos si no cabe
     * @param texto: contenido
     * @param clase: clase CSS
     * @return Devuelve la etiqueta
     */
    private Label celda(String texto, String clase) {
        Label etiqueta = new Label(texto);
        etiqueta.getStyleClass().add(clase);
        etiqueta.setMaxWidth(Double.MAX_VALUE);
        return etiqueta;
    }

    /**
     * Icono dentro de un cuadrado redondeado de color suave, como en las tarjetas y las filas
     * @param trazo: trazo SVG del icono
     * @return Devuelve el cuadrado con el icono
     */
    private StackPane cajaIcono(String trazo) {
        StackPane caja = new StackPane(icono(trazo));
        caja.getStyleClass().add("icono-caja");
        return caja;
    }

    /**
     * Crea un icono de 16 píxeles
     * @param contenido: trazo SVG
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
     * @return Devuelve el texto, por ejemplo "1 gasto" o "10 gastos"
     */
    private String contar(int n, String singular, String plural) {
        return n + " " + (n == 1 ? singular : plural);
    }
}
