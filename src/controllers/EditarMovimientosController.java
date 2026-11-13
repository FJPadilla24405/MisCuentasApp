package controllers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import models.Cuenta;
import models.Movimiento;
import models.Recurrente;
import models.Usuario;
import service.movimiento.Frecuencia;
import service.movimiento.MovimientoService;
import service.resumen.ResumenService;
import service.resumen.ResumenService.FilaMovimiento;
import util.Formato;

/**
 * Controlador de EditarMovimientosDialog.fxml: la lista de movimientos del mes que se está viendo en Resumen, para
 * editarlos (con la misma ventana de añadir movimiento), eliminarlos (con una confirmación en la propia fila) y, en los
 * recurrentes, desactivar o reactivar la repetición automática. Se abre con EditarMovimientosController.mostrar(...).
 *
 * @author Fco Padilla
 */
public class EditarMovimientosController {

    private static final String ICONO_INGRESO = "M17 7 L7 17 M17 17 H7 V7";
    private static final String ICONO_GASTO = "M7 17 L17 7 M7 7 H17 V17";
    private static final double ESCALA_ICONO = 0.67;

    @FXML private Label lblSubtitulo;
    @FXML private TextField txtBuscar;
    @FXML private ScrollPane scroll;
    @FXML private VBox lista;
    @FXML private Label lblVacio;
    @FXML private Label lblError;

    private final ResumenService resumen = new ResumenService();
    private final MovimientoService servicio = new MovimientoService();
    private Usuario usuario;
    private Cuenta cuenta;
    private YearMonth mes;
    // true en cuanto se cambia algo: quien abre la ventana tiene que refrescar lo que muestra
    private boolean cambiado = false;
    // Id del movimiento cuya eliminación se está confirmando (null si ninguno)
    private Integer confirmando;

    /**
     * Hace que la lista se actualice al escribir en el buscador
     */
    @FXML
    private void initialize() {
        txtBuscar.textProperty().addListener((obs, antes, ahora) -> pintar());
    }

    /**
     * Abre la ventana y espera a que se cierre.
     * @param propietario: ventana desde la que se abre (queda bloqueada hasta que se cierre)
     * @param usuario: usuario de la sesión
     * @param cuenta: cuenta que se está viendo, o null para todas
     * @param mes: mes que se está viendo
     * @return Devuelve true si se ha cambiado algo (un movimiento editado, eliminado o una recurrencia activada o desactivada)
     */
    public static boolean mostrar(Window propietario, Usuario usuario, Cuenta cuenta, YearMonth mes) {
        try {
            FXMLLoader loader = new FXMLLoader(EditarMovimientosController.class.getResource("/screens/dialogs/EditarMovimientosDialog.fxml"));
            Parent raiz = loader.load();
            EditarMovimientosController controlador = loader.getController();
            controlador.preparar(usuario, cuenta, mes);

            Stage ventana = new Stage();
            ventana.initOwner(propietario);
            ventana.initModality(Modality.WINDOW_MODAL);
            ventana.setTitle("MisCuentas · Editar movimientos");
            ventana.setScene(new Scene(raiz));
            ventana.setMinWidth(760);
            ventana.setMinHeight(480);
            ventana.showAndWait();
            return controlador.cambiado;
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo abrir la ventana de editar movimientos", e);
        }
    }

    /**
     * Pone el título y pinta la lista
     * @param usuario: usuario de la sesión
     * @param cuenta: cuenta que se está viendo, o null para todas
     * @param mes: mes que se está viendo
     */
    private void preparar(Usuario usuario, Cuenta cuenta, YearMonth mes) {
        this.usuario = usuario;
        this.cuenta = cuenta;
        this.mes = mes;
        lblSubtitulo.setText((cuenta == null ? "Todas las cuentas" : cuenta.getNombre()) + " · " + Formato.mesConAnio(mes));
        pintar();
    }

    // ===== Lista =====

    /**
     * Vuelve a leer los movimientos del mes y pinta una fila por cada uno (los que coinciden con el buscador, que mira
     * el concepto, la categoría y la cuenta). Conserva la posición del scroll.
     */
    private void pintar() {
        if (usuario == null) {
            return;
        }
        double posicion = scroll.getVvalue();
        lista.getChildren().clear();
        String busqueda = txtBuscar.getText() == null ? "" : txtBuscar.getText().trim().toLowerCase(Locale.ROOT);
        List<FilaMovimiento> todos = resumen.movimientos(usuario, cuenta, mes.toString());
        List<FilaMovimiento> filas = todos.stream()
                .filter(f -> busqueda.isEmpty() || f.concepto().toLowerCase(Locale.ROOT).contains(busqueda)
                        || f.categoria().toLowerCase(Locale.ROOT).contains(busqueda)
                        || f.cuenta().toLowerCase(Locale.ROOT).contains(busqueda))
                .toList();

        boolean vacia = filas.isEmpty();
        lblVacio.setVisible(vacia);
        lblVacio.setManaged(vacia);
        if (vacia) {
            lblVacio.setText(todos.isEmpty() ? "No hay movimientos en " + Formato.nombreMes(mes) + "." : "Ningún movimiento coincide con lo que buscas.");
        }
        // Se mira una sola vez la regla de cada recurrencia, aunque tenga varios movimientos en el mes
        Map<Integer, Optional<Recurrente>> reglas = new HashMap<>();
        for (FilaMovimiento fila : filas) {
            lista.getChildren().add(crearFila(fila, reglas));
        }
        // El scroll se recoloca cuando la lista ya tiene su tamaño nuevo
        Platform.runLater(() -> scroll.setVvalue(posicion));
    }

    /**
     * Crea la fila de un movimiento: icono, concepto (con una etiqueta si es recurrente), fecha, categoría y cuenta,
     * importe y los botones de lo que se puede hacer con él
     * @param fila: movimiento con los nombres de su categoría y su cuenta
     * @param reglas: reglas recurrentes ya leídas, por id
     * @return Devuelve la fila
     */
    private HBox crearFila(FilaMovimiento fila, Map<Integer, Optional<Recurrente>> reglas) {
        Movimiento m = fila.movimiento();
        Optional<Recurrente> regla = m.getRecurrenteId() > 0
                ? reglas.computeIfAbsent(m.getRecurrenteId(), id -> servicio.reglaDe(m)) : Optional.empty();

        Label concepto = etiqueta(fila.concepto(), "tabla-concepto");
        HBox titulo = new HBox(8, concepto);
        titulo.setAlignment(Pos.CENTER_LEFT);
        regla.ifPresent(r -> titulo.getChildren().add(pastillaRecurrente(r)));
        Label detalle = etiqueta(Formato.fechaCorta(m.getFecha()) + " · " + fila.categoria() + " · " + fila.cuenta(), "texto-suave-pequeno");
        VBox textos = new VBox(3, titulo, detalle);
        textos.setMinWidth(0);
        HBox.setHgrow(textos, Priority.ALWAYS);

        BigDecimal importe = fila.esIngreso() ? m.getImporte() : m.getImporte().negate();
        Label valor = etiqueta(Formato.monedaConSigno(importe, usuario.getMoneda()), fila.esIngreso() ? "importe-ingreso" : "importe-gasto");
        valor.setMinWidth(Region.USE_PREF_SIZE);

        HBox acciones = new HBox(8);
        acciones.setAlignment(Pos.CENTER_RIGHT);
        acciones.setMinWidth(Region.USE_PREF_SIZE);
        if (confirmando != null && confirmando == m.getId()) {
            Button botonSi = boton("Sí, eliminar", "boton-peligro");
            botonSi.setOnAction(e -> eliminar(m));
            Button botonNo = boton("No", "boton-secundario");
            botonNo.setOnAction(e -> {
                confirmando = null;
                pintar();
            });
            acciones.getChildren().addAll(etiqueta("¿Eliminar este movimiento?", "texto-suave-pequeno"), botonSi, botonNo);
        } else {
            Button botonEditar = boton("Editar", "boton-secundario");
            botonEditar.setOnAction(e -> editar(m));
            acciones.getChildren().add(botonEditar);
            regla.ifPresent(r -> {
                Button alternar = boton(r.isActivo() ? "Desactivar" : "Reactivar", "boton-secundario");
                alternar.setOnAction(e -> alternarRecurrencia(m, r.isActivo()));
                acciones.getChildren().add(alternar);
            });
            Button botonEliminar = boton("Eliminar", "boton-peligro");
            botonEliminar.setOnAction(e -> {
                confirmando = m.getId();
                pintar();
            });
            acciones.getChildren().add(botonEliminar);
        }

        HBox contenedor = new HBox(12, cajaIcono(fila.esIngreso() ? ICONO_INGRESO : ICONO_GASTO), textos, valor, acciones);
        contenedor.setAlignment(Pos.CENTER_LEFT);
        contenedor.getStyleClass().add("editar-fila");
        return contenedor;
    }

    /**
     * @param regla: regla recurrente del movimiento
     * @return Devuelve la etiqueta que dice cada cuánto se repite, o que está en pausa
     */
    private Label pastillaRecurrente(Recurrente regla) {
        Frecuencia frecuencia = Frecuencia.deCodigo(regla.getFrecuencia());
        String nombre = frecuencia == null ? regla.getFrecuencia() : frecuencia.getEtiqueta().toLowerCase(Locale.ROOT);
        Label pastilla = new Label(regla.isActivo() ? "Recurrente · " + nombre : "Recurrente en pausa · " + nombre);
        pastilla.getStyleClass().add(regla.isActivo() ? "pastilla" : "pastilla-alerta");
        pastilla.setMinWidth(Region.USE_PREF_SIZE);
        return pastilla;
    }

    // ===== Acciones =====

    /**
     * Abre la ventana de movimiento con los datos de este y, si se guardan cambios, vuelve a pintar la lista
     * @param movimiento: movimiento a editar
     */
    private void editar(Movimiento movimiento) {
        ocultarError();
        Optional<Movimiento> guardado = MovimientoController.editar(lista.getScene().getWindow(), usuario, movimiento);
        if (guardado.isPresent()) {
            cambiado = true;
            pintar();
        }
    }

    /**
     * Elimina un movimiento después de la confirmación de la fila
     * @param movimiento: movimiento a eliminar
     */
    private void eliminar(Movimiento movimiento) {
        ocultarError();
        confirmando = null;
        try {
            servicio.eliminar(usuario, movimiento.getId());
            cambiado = true;
        } catch (RuntimeException e) {
            mostrarError("No se pudo eliminar el movimiento: " + e.getMessage());
        }
        pintar();
    }

    /**
     * Desactiva la repetición automática de un movimiento recurrente, o la vuelve a activar
     * @param movimiento: movimiento de la recurrencia
     * @param activa: true si ahora está activa (y por tanto se desactiva)
     */
    private void alternarRecurrencia(Movimiento movimiento, boolean activa) {
        ocultarError();
        try {
            if (activa) {
                servicio.desactivarRecurrencia(usuario, movimiento.getId());
            } else {
                servicio.reactivarRecurrencia(usuario, movimiento.getId());
            }
            cambiado = true;
        } catch (RuntimeException e) {
            mostrarError("No se pudo cambiar la recurrencia: " + e.getMessage());
        }
        pintar();
    }

    /**
     * Cierra la ventana
     */
    @FXML
    private void cerrar() {
        ((Stage) lista.getScene().getWindow()).close();
    }

    // ===== Piezas =====

    /**
     * @param texto: texto del botón
     * @param clase: boton-secundario o boton-peligro
     * @return Devuelve un botón pequeño para una fila
     */
    private static Button boton(String texto, String clase) {
        Button boton = new Button(texto);
        boton.setMnemonicParsing(false);
        boton.getStyleClass().addAll(clase, "boton-mini");
        return boton;
    }

    /**
     * @param texto: contenido
     * @param clase: clase CSS
     * @return Devuelve una etiqueta con esa clase
     */
    private static Label etiqueta(String texto, String clase) {
        Label etiqueta = new Label(texto);
        etiqueta.getStyleClass().add(clase);
        return etiqueta;
    }

    /**
     * @param trazo: trazo SVG del icono
     * @return Devuelve el icono dentro de su cuadrado de color
     */
    private static StackPane cajaIcono(String trazo) {
        SVGPath forma = new SVGPath();
        forma.setContent(trazo);
        forma.getStyleClass().add("icono");
        forma.setScaleX(ESCALA_ICONO);
        forma.setScaleY(ESCALA_ICONO);
        StackPane caja = new StackPane(new Group(forma));
        caja.getStyleClass().add("icono-caja");
        return caja;
    }

    /**
     * Muestra el texto del error
     * @param texto: error
     */
    private void mostrarError(String texto) {
        lblError.setText(texto);
        lblError.setVisible(true);
        lblError.setManaged(true);
    }

    /**
     * Oculta el texto del error
     */
    private void ocultarError() {
        lblError.setText("");
        lblError.setVisible(false);
        lblError.setManaged(false);
    }
}
