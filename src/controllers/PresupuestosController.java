package controllers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import models.Usuario;
import service.auth.SesionActual;
import service.presupuesto.PresupuestoService;
import service.presupuesto.PresupuestoService.Estado;
import service.presupuesto.PresupuestoService.FilaPresupuesto;
import service.presupuesto.PresupuestoService.ResumenPresupuestos;
import util.Formato;
import util.IconosCategoria;

/**
 * Controlador de Presupuestos.fxml: tarjetas de resumen del mes, aviso de presupuestos cerca del límite
 * o superados y una tarjeta por presupuesto (lo gastado, lo que queda y una barra de progreso).
 * Al pulsar una tarjeta se edita ese presupuesto y "Añadir presupuesto" abre la misma ventana vacía.
 * Los datos los calcula PresupuestoService.
 *
 * @author Fco Padilla
 */
public class PresupuestosController {

    // Trazo del icono de las tarjetas (cuadrícula de 24x24) y su escala a 16 píxeles
    private static final double ESCALA_ICONO = 0.67;

    @FXML private ScrollPane raiz;
    @FXML private MenuButton menuMes;
    @FXML private Label lblLimite;
    @FXML private Label lblLimiteDetalle;
    @FXML private Label lblGastadoTitulo;
    @FXML private Label lblGastado;
    @FXML private Label lblGastadoDetalle;
    @FXML private Label lblRestante;
    @FXML private Label lblRestanteDetalle;
    @FXML private HBox avisoBox;
    @FXML private Label lblAviso;
    @FXML private Label lblPeriodo;
    @FXML private GridPane gridPresupuestos;
    @FXML private Label lblVacio;
    @FXML private Label lblDatos;

    private final PresupuestoService servicio = new PresupuestoService();
    private YearMonth mes = YearMonth.now();
    private Usuario usuario;
    private String moneda = "EUR";

    /**
     * Pinta los presupuestos del mes actual
     */
    @FXML
    private void initialize() {
        refrescar();
    }

    /**
     * Abre la ventana para crear un presupuesto. Si se guarda, se muestra el mes de ese presupuesto.
     */
    @FXML
    private void anadirPresupuesto() {
        abrirDialogo(null);
    }

    /**
     * Abre la ventana de un presupuesto (nuevo si es null, o para editar el que se pasa)
     * @param fila: presupuesto a editar, o null para uno nuevo
     */
    private void abrirDialogo(FilaPresupuesto fila) {
        Optional<YearMonth> resultado = PresupuestoController.mostrar(raiz.getScene().getWindow(), usuario, mes,
                fila == null ? null : fila.presupuesto());
        if (resultado.isPresent()) {
            mes = resultado.get();
            refrescar();
        }
    }

    /**
     * Vuelve a calcular los presupuestos del mes y pinta toda la pantalla
     */
    private void refrescar() {
        usuario = SesionActual.getUsuario();
        if (usuario == null) {
            return;
        }
        moneda = usuario.getMoneda();
        ResumenPresupuestos datos = servicio.calcular(usuario, mes);
        pintarMeses(datos.meses());
        pintarTarjetas(datos);
        pintarAviso(datos);
        pintarPresupuestos(datos);
        lblPeriodo.setText("Mensuales · " + Formato.mesConAnio(mes));
        lblDatos.setText(textoDatos());
    }

    /**
     * Rellena el selector de mes
     * @param meses: meses que se pueden elegir
     */
    private void pintarMeses(List<YearMonth> meses) {
        menuMes.setText(Formato.mesConAnio(mes));
        menuMes.getItems().clear();
        for (YearMonth m : meses) {
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
     * @param datos: presupuestos del mes
     */
    private void pintarTarjetas(ResumenPresupuestos datos) {
        int n = datos.filas().size();
        lblLimite.setText(Formato.moneda(datos.limiteTotal(), moneda));
        lblLimiteDetalle.setText(n == 0 ? "Aún no hay presupuestos este mes"
                : n + (n == 1 ? " presupuesto" : " presupuestos") + " · " + textoAlcanceComun(datos));

        lblGastadoTitulo.setText("Gastado en " + Formato.nombreMes(mes));
        lblGastado.setText(Formato.moneda(datos.gastadoTotal(), moneda));
        BigDecimal gastado = datos.porcentajeGastado();
        lblGastadoDetalle.setText(gastado == null ? "Sin límite que comparar"
                : Formato.porcentajeCorto(gastado) + " del límite total utilizado");

        lblRestante.setText(Formato.moneda(datos.restanteTotal(), moneda));
        BigDecimal restante = datos.porcentajeRestante();
        lblRestanteDetalle.setText(restante == null ? "Sin límite que comparar"
                : Formato.porcentajeCorto(restante) + " del límite total disponible");
    }

    /**
     * @param datos: presupuestos del mes
     * @return Devuelve el alcance si todos los presupuestos tienen el mismo, o "Alcances distintos"
     */
    private String textoAlcanceComun(ResumenPresupuestos datos) {
        String primero = datos.filas().get(0).presupuesto().getAlcance();
        boolean iguales = datos.filas().stream().allMatch(f -> f.presupuesto().getAlcance().equals(primero));
        return iguales ? textoAlcance(primero) : "Alcances distintos";
    }

    /**
     * @param alcance: "todas", "banco" o "efectivo"
     * @return Devuelve el texto del alcance
     */
    private String textoAlcance(String alcance) {
        return switch (alcance) {
            case "banco" -> "Solo bancos";
            case "efectivo" -> "Solo efectivo";
            default -> "Todas las cuentas";
        };
    }

    /**
     * Muestra el aviso: rojo si hay presupuestos cerca del límite o superados, verde si todos van bien y
     * nada si no hay presupuestos.
     * @param datos: presupuestos del mes
     */
    private void pintarAviso(ResumenPresupuestos datos) {
        List<String> superados = new ArrayList<>();
        List<String> cercanos = new ArrayList<>();
        for (FilaPresupuesto f : datos.filas()) {
            if (f.estado() == Estado.SUPERADO) {
                superados.add(f.categoria().getNombre());
            } else if (f.estado() == Estado.CERCA) {
                cercanos.add(f.categoria().getNombre());
            }
        }
        avisoBox.getStyleClass().removeAll("aviso-ok", "aviso-alerta");
        if (datos.filas().isEmpty()) {
            avisoBox.setVisible(false);
            avisoBox.setManaged(false);
            return;
        }
        List<String> frases = new ArrayList<>();
        if (!superados.isEmpty()) {
            frases.add(Formato.lista(superados) + (superados.size() == 1 ? " ha superado su límite." : " han superado su límite."));
        }
        if (!cercanos.isEmpty()) {
            frases.add(Formato.lista(cercanos) + (cercanos.size() == 1 ? " está cerca de su límite." : " están cerca de su límite."));
        }
        if (superados.isEmpty() && !cercanos.isEmpty()) {
            String cuando = mes.equals(YearMonth.now()) ? "este mes" : "en " + Formato.nombreMes(mes);
            frases.add("Ningún presupuesto se ha superado " + cuando + ".");
        }
        boolean alerta = !frases.isEmpty();
        lblAviso.setText(alerta ? String.join(" ", frases) : "Todos tus presupuestos van dentro del límite.");
        avisoBox.getStyleClass().add(alerta ? "aviso-alerta" : "aviso-ok");
        avisoBox.setVisible(true);
        avisoBox.setManaged(true);
    }

    /**
     * Pinta una tarjeta por presupuesto, en tres columnas, o el mensaje de que no hay ninguno
     * @param datos: presupuestos del mes
     */
    private void pintarPresupuestos(ResumenPresupuestos datos) {
        gridPresupuestos.getChildren().clear();
        boolean vacio = datos.filas().isEmpty();
        lblVacio.setVisible(vacio);
        lblVacio.setManaged(vacio);
        gridPresupuestos.setVisible(!vacio);
        gridPresupuestos.setManaged(!vacio);
        if (vacio) {
            lblVacio.setText("No tienes presupuestos en " + Formato.nombreMes(mes) + ". Crea el primero con \"Añadir presupuesto\".");
            return;
        }
        int i = 0;
        for (FilaPresupuesto fila : datos.filas()) {
            gridPresupuestos.add(crearTarjeta(fila), i % 3, i / 3);
            i++;
        }
    }

    /**
     * Crea la tarjeta de un presupuesto: categoría y estado, límite, gastado, restante y barra de progreso
     * @param fila: presupuesto con lo gastado
     * @return Devuelve la tarjeta
     */
    private VBox crearTarjeta(FilaPresupuesto fila) {
        Estado estado = fila.estado();
        boolean alerta = estado != Estado.DENTRO;

        Label categoria = new Label(fila.nombreCategoria());
        categoria.getStyleClass().add("tarjeta-cuenta-nombre");
        categoria.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(categoria, Priority.ALWAYS);
        Label pastilla = new Label(switch (estado) {
            case DENTRO -> "Dentro del límite";
            case CERCA -> "Cerca del límite";
            case SUPERADO -> "Superado";
        });
        pastilla.getStyleClass().add(alerta ? "pastilla-alerta" : "pastilla");
        pastilla.setMinWidth(Region.USE_PREF_SIZE);
        HBox cabecera = new HBox(10, cajaIcono(IconosCategoria.para(fila.categoria().getNombre())), categoria, pastilla);
        cabecera.setAlignment(Pos.CENTER_LEFT);

        HBox limite = new HBox(new Label("Límite mensual"), espacio(), valor(Formato.moneda(fila.limite(), moneda), "presupuesto-limite"));
        limite.getChildren().get(0).getStyleClass().add("texto-suave-pequeno");
        limite.setAlignment(Pos.CENTER_LEFT);

        VBox gastado = columna("Gastado", Formato.moneda(fila.gastado(), moneda), "presupuesto-valor");
        VBox restante = columna("Restante", Formato.moneda(fila.restante(), moneda), alerta ? "presupuesto-valor-alerta" : "presupuesto-valor-ok");
        HBox cifras = new HBox(16, gastado, restante);
        HBox.setHgrow(gastado, Priority.ALWAYS);
        HBox.setHgrow(restante, Priority.ALWAYS);

        double fraccion = fila.limite().signum() > 0 ? fila.gastado().doubleValue() / fila.limite().doubleValue() : 0;
        ProgressBar barra = new ProgressBar(Math.min(1.0, fraccion));
        barra.getStyleClass().add("barra-presupuesto");
        if (alerta) {
            barra.getStyleClass().add("alerta");
        }
        barra.setMaxWidth(Double.MAX_VALUE);

        Label utilizado = new Label(Formato.porcentajeCorto(fila.porcentaje()) + " utilizado");
        utilizado.getStyleClass().add("texto-suave-pequeno");
        Label alcance = new Label(textoAlcance(fila.presupuesto().getAlcance()));
        alcance.getStyleClass().add("texto-suave-pequeno");
        HBox pie = new HBox(utilizado, espacio(), alcance);
        pie.setAlignment(Pos.CENTER_LEFT);

        VBox tarjeta = new VBox(12, cabecera, limite, cifras, barra, pie);
        tarjeta.getStyleClass().add("presupuesto-card");
        tarjeta.setOnMouseClicked(e -> abrirDialogo(fila));
        String nombre = fila.presupuesto().getNombre();
        Tooltip.install(tarjeta, new Tooltip((nombre == null || nombre.isBlank() ? fila.nombreCategoria() : nombre) + "\nPulsa para editarlo"));
        return tarjeta;
    }

    /**
     * Crea una columna con un título pequeño y un valor
     * @param titulo: título
     * @param valor: valor
     * @param clase: clase CSS del valor
     * @return Devuelve la columna
     */
    private VBox columna(String titulo, String valor, String clase) {
        Label t = new Label(titulo);
        t.getStyleClass().add("texto-suave-pequeno");
        return new VBox(3, t, valor(valor, clase));
    }

    /**
     * @param texto: contenido
     * @param clase: clase CSS
     * @return Devuelve una etiqueta con ese texto y esa clase
     */
    private Label valor(String texto, String clase) {
        Label l = new Label(texto);
        l.getStyleClass().add(clase);
        return l;
    }

    /**
     * @return Devuelve un hueco que empuja lo que va después hacia la derecha
     */
    private Region espacio() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    /**
     * Icono dentro de un cuadrado redondeado de color suave
     * @param trazo: trazo SVG del icono
     * @return Devuelve el cuadrado con el icono
     */
    private StackPane cajaIcono(String trazo) {
        SVGPath path = new SVGPath();
        path.setContent(trazo);
        path.getStyleClass().add("icono");
        path.setScaleX(ESCALA_ICONO);
        path.setScaleY(ESCALA_ICONO);
        StackPane caja = new StackPane(new Group(path));
        caja.getStyleClass().add("icono-caja");
        return caja;
    }

    /**
     * @return Devuelve "Datos hasta el 30 sept 2026": hoy si es el mes actual, el último día si es un mes pasado
     */
    private String textoDatos() {
        LocalDate hoy = LocalDate.now();
        LocalDate hasta;
        if (mes.equals(YearMonth.from(hoy))) {
            hasta = hoy;
        } else if (mes.isBefore(YearMonth.from(hoy))) {
            hasta = mes.atEndOfMonth();
        } else {
            return "Mes futuro: todavía sin datos";
        }
        return "Datos hasta el " + Formato.fechaCorta(hasta.toString()) + " " + hasta.getYear();
    }
}
