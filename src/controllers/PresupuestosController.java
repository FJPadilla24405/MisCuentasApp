package controllers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import models.Presupuesto;
import models.Usuario;
import service.auth.SesionActual;
import service.presupuestos.PresupuestoService;
import service.presupuestos.PresupuestoService.Estado;
import service.presupuestos.PresupuestoService.FilaPresupuesto;
import service.presupuestos.PresupuestoService.Vista;
import util.Formato;
import util.IconosCategoria;

/**
 * Controlador de Presupuestos.fxml: los presupuestos de un mes con lo que se ha gastado en cada uno. Arriba
 * se ve el límite total, lo gastado y lo que queda; debajo, una tarjeta por categoría con su barra de
 * progreso. El botón "Añadir presupuesto" abre NuevoPresupuestoController. Los datos los calcula PresupuestoService.
 *
 * @author Fco Padilla
 */
public class PresupuestosController {

    // Tarjetas por fila
    private static final int COLUMNAS = 3;
    // 24 de la cuadrícula del icono escalados a 16 píxeles
    private static final double ESCALA_ICONO = 0.67;

    @FXML private ScrollPane raiz;
    @FXML private MenuButton menuMes;
    @FXML private Label lblLimiteTotal;
    @FXML private Label lblLimiteDetalle;
    @FXML private Label lblGastadoTitulo;
    @FXML private Label lblGastado;
    @FXML private Label lblGastadoDetalle;
    @FXML private Label lblRestante;
    @FXML private Label lblRestanteDetalle;
    @FXML private HBox cajaAviso;
    @FXML private Label lblAviso;
    @FXML private Label lblPeriodo;
    @FXML private GridPane gridPresupuestos;
    @FXML private Label lblVacio;
    @FXML private Label lblDatosHasta;

    private final PresupuestoService servicio = new PresupuestoService();
    private YearMonth mes = YearMonth.now();
    private Vista vista;
    private String moneda = "EUR";

    /**
     * Pinta los presupuestos del mes actual al abrir la pantalla
     */
    @FXML
    private void initialize() {
        refrescar();
    }

    /**
     * Abre la ventana para crear un presupuesto. Si se crea, se pasa al mes del nuevo presupuesto para verlo.
     */
    @FXML
    private void anadirPresupuesto() {
        Usuario usuario = SesionActual.getUsuario();
        if (usuario == null) {
            return;
        }
        Optional<Presupuesto> creado = NuevoPresupuestoController.mostrar(raiz.getScene().getWindow(), usuario, mes);
        creado.ifPresent(p -> {
            mes = YearMonth.parse(p.getMesReferencia());
            refrescar();
        });
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
        vista = servicio.calcular(usuario, mes);
        pintarMeses();
        pintarResumen();
        pintarAviso();
        pintarTarjetas();
        pintarPie();
    }

    /**
     * Rellena el selector de mes y el texto "Mensuales · Septiembre 2026"
     */
    private void pintarMeses() {
        menuMes.setText(Formato.mesConAnio(mes));
        lblPeriodo.setText("Mensuales · " + Formato.mesConAnio(mes));
        menuMes.getItems().clear();
        for (YearMonth m : vista.meses()) {
            MenuItem item = new MenuItem(Formato.mesConAnio(m));
            item.setOnAction(e -> {
                mes = m;
                refrescar();
            });
            menuMes.getItems().add(item);
        }
    }

    /**
     * Rellena las tres tarjetas de arriba: límite total, gastado y restante
     */
    private void pintarResumen() {
        int n = vista.filas().size();
        lblLimiteTotal.setText(Formato.moneda(vista.limiteTotal(), moneda));
        lblLimiteDetalle.setText(n == 0 ? "Sin presupuestos este mes" : contar(n, "presupuesto", "presupuestos") + " · " + textoAlcanceGeneral());

        lblGastadoTitulo.setText("Gastado en " + Formato.nombreMes(mes));
        lblGastado.setText(Formato.moneda(vista.gastadoTotal(), moneda));
        BigDecimal usado = vista.porcentajeUsado();
        lblGastadoDetalle.setText(usado == null ? "Aún no hay límites con los que comparar"
                : Formato.porcentaje(usado) + " del límite total utilizado");

        BigDecimal restante = vista.restanteTotal();
        lblRestante.setText(restante.signum() < 0 ? Formato.monedaConSigno(restante, moneda) : Formato.moneda(restante, moneda));
        BigDecimal disponible = vista.porcentajeDisponible();
        if (disponible == null) {
            lblRestanteDetalle.setText("Añade un presupuesto para empezar");
        } else if (restante.signum() < 0) {
            lblRestanteDetalle.setText("Has superado el límite total");
        } else {
            lblRestanteDetalle.setText(Formato.porcentaje(disponible) + " del límite total disponible");
        }
    }

    /**
     * Muestra el aviso con las categorías superadas o cerca de su límite; si no hay ninguna, lo oculta
     */
    private void pintarAviso() {
        List<String> superados = new ArrayList<>();
        List<String> cerca = new ArrayList<>();
        for (FilaPresupuesto f : vista.filas()) {
            if (f.estado() == Estado.SUPERADO) {
                superados.add(f.categoria());
            } else if (f.estado() == Estado.CERCA) {
                cerca.add(f.categoria());
            }
        }
        boolean hayAviso = !superados.isEmpty() || !cerca.isEmpty();
        cajaAviso.setVisible(hayAviso);
        cajaAviso.setManaged(hayAviso);
        if (!hayAviso) {
            return;
        }
        StringBuilder texto = new StringBuilder();
        if (!superados.isEmpty()) {
            texto.append(Formato.enumerar(superados)).append(superados.size() == 1 ? " ha superado su límite. " : " han superado su límite. ");
        }
        if (!cerca.isEmpty()) {
            texto.append(Formato.enumerar(cerca)).append(cerca.size() == 1 ? " está cerca de su límite. " : " están cerca de su límite. ");
        }
        if (superados.isEmpty()) {
            texto.append("Ningún presupuesto se ha superado este mes.");
        }
        lblAviso.setText(texto.toString().trim());
    }

    /**
     * Pinta una tarjeta por presupuesto, tres por fila, o un mensaje si el mes no tiene presupuestos
     */
    private void pintarTarjetas() {
        gridPresupuestos.getChildren().clear();
        boolean vacia = vista.filas().isEmpty();
        gridPresupuestos.setVisible(!vacia);
        gridPresupuestos.setManaged(!vacia);
        lblVacio.setVisible(vacia);
        lblVacio.setManaged(vacia);
        if (vacia) {
            lblVacio.setText("Todavía no tienes presupuestos para " + Formato.nombreMes(mes) + " de " + mes.getYear()
                    + ". Pulsa \"Añadir presupuesto\" para crear el primero.");
            return;
        }
        int i = 0;
        for (FilaPresupuesto fila : vista.filas()) {
            gridPresupuestos.add(crearTarjeta(fila), i % COLUMNAS, i / COLUMNAS);
            i++;
        }
    }

    /**
     * Pone hasta qué día llegan los datos: hoy si es el mes actual, el último día si ya pasó
     */
    private void pintarPie() {
        LocalDate hoy = LocalDate.now();
        if (mes.isAfter(YearMonth.from(hoy))) {
            lblDatosHasta.setText("Este mes todavía no ha empezado");
            return;
        }
        LocalDate hasta = mes.equals(YearMonth.from(hoy)) ? hoy : mes.atEndOfMonth();
        lblDatosHasta.setText("Datos hasta el " + Formato.fechaCorta(hasta.toString()) + " " + hasta.getYear());
    }

    /**
     * Crea la tarjeta de un presupuesto: icono y nombre de la categoría, estado, límite, gastado, restante,
     * barra de progreso y alcance de cuentas
     * @param fila: presupuesto con lo gastado
     * @return Devuelve la tarjeta
     */
    private VBox crearTarjeta(FilaPresupuesto fila) {
        String estado = fila.estado().name().toLowerCase(Locale.ROOT);

        Label nombre = new Label(fila.categoria());
        nombre.getStyleClass().add("presupuesto-nombre");
        nombre.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(nombre, Priority.ALWAYS);
        Label pastilla = new Label(textoEstado(fila.estado()));
        pastilla.getStyleClass().addAll("estado-pastilla", "estado-" + estado);
        pastilla.setMinWidth(Region.USE_PREF_SIZE);
        HBox cabecera = new HBox(10, cajaIcono(IconosCategoria.trazo(fila.categoria())), nombre, pastilla);
        cabecera.setAlignment(Pos.CENTER_LEFT);

        Label etiquetaLimite = new Label("Límite mensual");
        etiquetaLimite.getStyleClass().add("presupuesto-etiqueta");
        Label limite = new Label(Formato.moneda(fila.limite(), moneda));
        limite.getStyleClass().add("presupuesto-limite");
        Region hueco = new Region();
        HBox.setHgrow(hueco, Priority.ALWAYS);
        HBox filaLimite = new HBox(etiquetaLimite, hueco, limite);
        filaLimite.setAlignment(Pos.CENTER_LEFT);

        Label gastado = new Label(Formato.moneda(fila.gastado(), moneda));
        gastado.getStyleClass().add("presupuesto-importe");
        Label restante = new Label(fila.restante().signum() < 0 ? Formato.monedaConSigno(fila.restante(), moneda)
                : Formato.moneda(fila.restante(), moneda));
        restante.getStyleClass().addAll("presupuesto-importe", "restante-" + estado);
        HBox cifras = new HBox(36, columna("Gastado", gastado), columna("Restante", restante));

        Label usado = new Label(Formato.porcentaje(fila.porcentaje()) + " utilizado");
        usado.getStyleClass().add("presupuesto-etiqueta");
        Label alcance = new Label(textoAlcance(fila.presupuesto().getAlcance()));
        alcance.getStyleClass().add("presupuesto-etiqueta");
        Region hueco2 = new Region();
        HBox.setHgrow(hueco2, Priority.ALWAYS);
        HBox filaPie = new HBox(usado, hueco2, alcance);
        filaPie.setAlignment(Pos.CENTER_LEFT);

        VBox tarjeta = new VBox(12, cabecera, filaLimite, cifras, crearBarra(fila, estado), filaPie);
        tarjeta.getStyleClass().add("presupuesto-tarjeta");
        tarjeta.setMaxWidth(Double.MAX_VALUE);
        tarjeta.setMaxHeight(Double.MAX_VALUE);
        String nombrePresupuesto = fila.presupuesto().getNombre();
        if (nombrePresupuesto != null && !nombrePresupuesto.isBlank()) {
            Tooltip.install(tarjeta, new Tooltip(nombrePresupuesto));
        }
        return tarjeta;
    }

    /**
     * Crea la barra de progreso: una pista gris con un relleno cuyo ancho es el porcentaje gastado (como máximo todo)
     * @param fila: presupuesto con lo gastado
     * @param estado: dentro, cerca o superado, para el color
     * @return Devuelve la barra
     */
    private StackPane crearBarra(FilaPresupuesto fila, String estado) {
        double proporcion = Math.max(0, Math.min(1, fila.porcentaje().doubleValue() / 100.0));
        Region relleno = new Region();
        relleno.getStyleClass().addAll("barra-relleno", "barra-" + estado);
        StackPane pista = new StackPane(relleno);
        pista.getStyleClass().add("barra-pista");
        StackPane.setAlignment(relleno, Pos.CENTER_LEFT);
        relleno.maxWidthProperty().bind(Bindings.multiply(pista.widthProperty(), proporcion));
        relleno.setVisible(proporcion > 0);
        return pista;
    }

    /**
     * Una columna de cifras: una etiqueta pequeña encima del importe
     * @param titulo: etiqueta
     * @param importe: importe ya formateado
     * @return Devuelve la columna
     */
    private VBox columna(String titulo, Label importe) {
        Label etiqueta = new Label(titulo);
        etiqueta.getStyleClass().add("presupuesto-etiqueta");
        VBox caja = new VBox(2, etiqueta, importe);
        caja.setAlignment(Pos.CENTER_LEFT);
        return caja;
    }

    /**
     * Icono dentro de un cuadrado redondeado de color suave, como en las tarjetas de cuenta
     * @param trazo: trazo SVG del icono
     * @return Devuelve el cuadrado con el icono
     */
    private StackPane cajaIcono(String trazo) {
        SVGPath icono = new SVGPath();
        icono.setContent(trazo);
        icono.getStyleClass().add("icono");
        icono.setScaleX(ESCALA_ICONO);
        icono.setScaleY(ESCALA_ICONO);
        StackPane caja = new StackPane(new Group(icono));
        caja.getStyleClass().add("icono-caja");
        return caja;
    }

    /**
     * @param estado: estado de un presupuesto
     * @return Devuelve el texto de su pastilla
     */
    private String textoEstado(Estado estado) {
        return switch (estado) {
            case DENTRO -> "Dentro del límite";
            case CERCA -> "Cerca del límite";
            case SUPERADO -> "Límite superado";
        };
    }

    /**
     * @param alcance: "todas", "banco" o "efectivo"
     * @return Devuelve el texto que se ve en la tarjeta
     */
    private String textoAlcance(String alcance) {
        if (Presupuesto.ALCANCE_BANCO.equals(alcance)) {
            return "Solo bancos";
        }
        if (Presupuesto.ALCANCE_EFECTIVO.equals(alcance)) {
            return "Solo efectivo";
        }
        return "Todas las cuentas";
    }

    /**
     * @return Devuelve el alcance común a todos los presupuestos del mes, o "Varios alcances" si no es el mismo
     */
    private String textoAlcanceGeneral() {
        String primero = null;
        for (FilaPresupuesto f : vista.filas()) {
            String actual = f.presupuesto().getAlcance();
            if (primero == null) {
                primero = actual;
            } else if (!primero.equals(actual)) {
                return "Varios alcances";
            }
        }
        return textoAlcance(primero);
    }

    /**
     * Escribe una cantidad con su palabra en singular o plural
     * @param n: cantidad
     * @param singular: palabra si es 1
     * @param plural: palabra si no es 1
     * @return Devuelve el texto, por ejemplo "1 presupuesto" o "6 presupuestos"
     */
    private String contar(int n, String singular, String plural) {
        return n + " " + (n == 1 ? singular : plural);
    }
}
