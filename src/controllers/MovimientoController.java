package controllers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;
import models.Categoria;
import models.Cuenta;
import models.Movimiento;
import models.Usuario;
import service.DatoInvalido;
import service.movimiento.Frecuencia;
import service.movimiento.MovimientoService;
import util.Campos;
import util.Formato;
import util.Importes;

/**
 * Controlador de MovimientoDialog.fxml: ventana para añadir un movimiento a mano (gasto o ingreso, importe,
 * concepto, cuenta, fecha, categoría, nota opcional y, si es recurrente, cada cuánto se repite: a diario, cada semana,
 * cada mes o cada año). Se abre con MovimientoController.mostrar(...) para añadir uno nuevo o con
 * MovimientoController.editar(...) para cambiar uno que ya existe (la misma ventana, con sus datos puestos).
 * La lógica está en MovimientoService.
 *
 * @author Fco Padilla
 */
public class MovimientoController {

    // Cómo se escribe y se lee la fecha en el campo
    private static final DateTimeFormatter FECHA_ESCRITA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_LEIDA = DateTimeFormatter.ofPattern("d/M/yyyy");
    // Signos que se ven delante del importe (el segundo es el signo menos de verdad, no un guion)
    private static final String SIGNO_INGRESO = "+";
    private static final String SIGNO_GASTO = "\u2212";

    @FXML private ToggleGroup grupoTipo;
    @FXML private ToggleButton tglGasto;
    @FXML private ToggleButton tglIngreso;
    @FXML private HBox cajaImporte;
    @FXML private HBox cajaConcepto;
    @FXML private HBox cajaNota;
    @FXML private Label lblSigno;
    @FXML private Label lblMoneda;
    @FXML private TextField txtImporte;
    @FXML private TextField txtConcepto;
    @FXML private TextField txtNota;
    @FXML private ComboBox<Cuenta> cmbCuenta;
    @FXML private ComboBox<Categoria> cmbCategoria;
    @FXML private DatePicker dpFecha;
    @FXML private CheckBox chkRecurrente;
    @FXML private ToggleGroup grupoFrecuencia;
    @FXML private ToggleButton tglDiaria;
    @FXML private ToggleButton tglSemanal;
    @FXML private ToggleButton tglMensual;
    @FXML private ToggleButton tglAnual;
    @FXML private VBox cajaFrecuencia;
    @FXML private Label lblFrecuencia;
    @FXML private Label lblBorrador;
    @FXML private VBox cajaRecurrente;
    @FXML private Button btnGuardar;
    @FXML private Label lblError;

    private final MovimientoService servicio = new MovimientoService();
    private Usuario usuario;
    // Movimiento que se está editando; null si se está añadiendo uno nuevo
    private Movimiento editando;
    // Movimiento guardado; vacío si se cancela
    private Optional<Movimiento> resultado = Optional.empty();

    /**
     * Prepara los selectores, el campo de fecha y los cambios al alternar entre gasto e ingreso
     */
    @FXML
    private void initialize() {
        cmbCuenta.setConverter(new StringConverter<>() {
            @Override
            public String toString(Cuenta c) {
                return c == null ? "" : c.getNombre() + " - " + ("efectivo".equals(c.getTipo()) ? "Cartera" : "Cuenta bancaria");
            }

            @Override
            public Cuenta fromString(String texto) {
                return null;
            }
        });
        cmbCategoria.setConverter(new StringConverter<>() {
            @Override
            public String toString(Categoria c) {
                if (c == null) {
                    return "";
                }
                String sub = c.getSubcategoria();
                return sub == null || sub.isBlank() ? c.getNombre() : c.getNombre() + " · " + sub;
            }

            @Override
            public Categoria fromString(String texto) {
                return null;
            }
        });
        dpFecha.setConverter(new StringConverter<>() {
            @Override
            public String toString(LocalDate fecha) {
                return fecha == null ? "" : FECHA_ESCRITA.format(fecha);
            }

            @Override
            public LocalDate fromString(String texto) {
                try {
                    return LocalDate.parse(texto.trim(), FECHA_LEIDA);
                } catch (DateTimeParseException | NullPointerException e) {
                    return null;
                }
            }
        });

        Campos.configurar(cajaImporte, txtImporte);
        Campos.configurar(cajaConcepto, txtConcepto);
        Campos.configurar(cajaNota, txtNota);
        cmbCuenta.valueProperty().addListener((obs, antes, ahora) -> cmbCuenta.getStyleClass().remove("error"));
        cmbCategoria.valueProperty().addListener((obs, antes, ahora) -> cmbCategoria.getStyleClass().remove("error"));
        dpFecha.valueProperty().addListener((obs, antes, ahora) -> dpFecha.getStyleClass().remove("error"));
        grupoTipo.selectedToggleProperty().addListener((obs, antes, ahora) -> {
            if (ahora == null) {
                grupoTipo.selectToggle(antes);
            } else {
                cambiarTipo();
            }
        });
        // Recurrencia: las cuatro frecuencias llevan guardada su Frecuencia, y el aviso se rehace al cambiar la frecuencia o la fecha
        tglDiaria.setUserData(Frecuencia.DIARIA);
        tglSemanal.setUserData(Frecuencia.SEMANAL);
        tglMensual.setUserData(Frecuencia.MENSUAL);
        tglAnual.setUserData(Frecuencia.ANUAL);
        grupoFrecuencia.selectedToggleProperty().addListener((obs, antes, ahora) -> {
            if (ahora == null) {
                grupoFrecuencia.selectToggle(antes);
            } else {
                actualizarFrecuencia();
            }
        });
        dpFecha.valueProperty().addListener((obs, antes, ahora) -> actualizarFrecuencia());
        chkRecurrente.selectedProperty().addListener((obs, antes, ahora) -> {
            cajaFrecuencia.setVisible(ahora);
            cajaFrecuencia.setManaged(ahora);
            actualizarFrecuencia();
            // La ventana no se puede estirar a mano, así que se ajusta sola a lo que ahora lleva dentro
            Platform.runLater(() -> {
                if (chkRecurrente.getScene() != null && chkRecurrente.getScene().getWindow() != null) {
                    chkRecurrente.getScene().getWindow().sizeToScene();
                }
            });
        });
        Platform.runLater(txtImporte::requestFocus);
    }

    /**
     * @return Devuelve la frecuencia elegida si el movimiento es recurrente, o null si es puntual
     */
    private Frecuencia frecuencia() {
        if (!chkRecurrente.isSelected() || grupoFrecuencia.getSelectedToggle() == null) {
            return null;
        }
        return (Frecuencia) grupoFrecuencia.getSelectedToggle().getUserData();
    }

    /**
     * Explica bajo las frecuencias cuándo se repetirá el movimiento y cuándo será la próxima vez
     */
    private void actualizarFrecuencia() {
        Frecuencia frecuencia = frecuencia();
        if (frecuencia == null) {
            lblFrecuencia.setText("");
            return;
        }
        LocalDate fecha = dpFecha.getValue();
        String texto = "Se añadirá un movimiento igual automáticamente " + frecuencia.descripcion(fecha) + ".";
        if (fecha != null) {
            texto += " El siguiente será el " + FECHA_ESCRITA.format(frecuencia.fechaDe(fecha, 1)) + ".";
        }
        lblFrecuencia.setText(texto);
    }

    /**
     * Abre la ventana y espera a que se cierre.
     * @param propietario: ventana desde la que se abre (queda bloqueada hasta que se cierre)
     * @param usuario: usuario de la sesión
     * @param cuentaInicial: cuenta que aparece elegida (la de la pantalla desde la que se abre)
     * @return Devuelve el movimiento guardado, o vacío si se canceló
     */
    public static Optional<Movimiento> mostrar(Window propietario, Usuario usuario, Cuenta cuentaInicial) {
        return abrir(propietario, usuario, cuentaInicial, null);
    }

    /**
     * Abre la ventana con los datos de un movimiento que ya existe y espera a que se cierre. La recurrencia no se
     * cambia desde aquí.
     * @param propietario: ventana desde la que se abre (queda bloqueada hasta que se cierre)
     * @param usuario: usuario de la sesión
     * @param movimiento: movimiento a editar
     * @return Devuelve el movimiento con sus cambios, o vacío si se canceló
     */
    public static Optional<Movimiento> editar(Window propietario, Usuario usuario, Movimiento movimiento) {
        return abrir(propietario, usuario, null, movimiento);
    }

    /**
     * Carga y muestra la ventana, para añadir (movimiento null) o para editar
     * @param propietario: ventana desde la que se abre
     * @param usuario: usuario de la sesión
     * @param cuentaInicial: cuenta que aparece elegida al añadir (puede ser null)
     * @param movimiento: movimiento a editar, o null para añadir uno nuevo
     * @return Devuelve el movimiento guardado, o vacío si se canceló
     */
    private static Optional<Movimiento> abrir(Window propietario, Usuario usuario, Cuenta cuentaInicial, Movimiento movimiento) {
        try {
            FXMLLoader loader = new FXMLLoader(MovimientoController.class.getResource("/screens/dialogs/MovimientoDialog.fxml"));
            Parent raiz = loader.load();
            MovimientoController controlador = loader.getController();
            if (movimiento == null) {
                controlador.preparar(usuario, cuentaInicial);
            } else {
                controlador.prepararEdicion(usuario, movimiento);
            }

            Stage ventana = new Stage();
            ventana.initOwner(propietario);
            ventana.initModality(Modality.WINDOW_MODAL);
            ventana.setTitle(movimiento == null ? "MisCuentas · Añadir movimiento" : "MisCuentas · Editar movimiento");
            ventana.setResizable(false);
            ventana.setScene(new Scene(raiz));
            ventana.showAndWait();
            return controlador.resultado;
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo abrir la ventana del movimiento", e);
        }
    }

    /**
     * Rellena la ventana con los datos del movimiento que se edita. No se ofrece cambiar la recurrencia.
     * @param usuario: usuario de la sesión
     * @param movimiento: movimiento a editar
     */
    private void prepararEdicion(Usuario usuario, Movimiento movimiento) {
        this.usuario = usuario;
        this.editando = movimiento;
        lblMoneda.setText(usuario.getMoneda() + " " + Formato.simbolo(usuario.getMoneda()));
        cmbCuenta.getItems().setAll(servicio.cuentasDisponibles(usuario));
        cmbCuenta.getItems().stream().filter(c -> c.getId() == movimiento.getCuentaId()).findFirst().ifPresent(cmbCuenta::setValue);

        // Primero el tipo (que decide las categorías) y después la categoría
        grupoTipo.selectToggle("ingreso".equals(movimiento.getTipo()) ? tglIngreso : tglGasto);
        cambiarTipo();
        cmbCategoria.getItems().stream().filter(c -> c.getId() == movimiento.getCategoriaId()).findFirst().ifPresent(cmbCategoria::setValue);

        txtImporte.setText(movimiento.getImporte().toPlainString().replace('.', ','));
        txtConcepto.setText(movimiento.getDescripcion() == null ? "" : movimiento.getDescripcion());
        txtNota.setText(movimiento.getNota() == null ? "" : movimiento.getNota());
        try {
            dpFecha.setValue(LocalDate.parse(movimiento.getFecha()));
        } catch (DateTimeParseException e) {
            dpFecha.setValue(null);
        }

        cajaRecurrente.setVisible(false);
        cajaRecurrente.setManaged(false);
        lblBorrador.setText(servicio.reglaDe(movimiento).isPresent() ? "Editando · recurrente" : "Editando movimiento");
        btnGuardar.setText("Guardar cambios");
    }

    /**
     * Rellena las cuentas, la fecha de hoy y las categorías de gasto
     * @param usuario: usuario de la sesión
     * @param cuentaInicial: cuenta que aparece elegida (puede ser null)
     */
    private void preparar(Usuario usuario, Cuenta cuentaInicial) {
        this.usuario = usuario;
        lblMoneda.setText(usuario.getMoneda() + " " + Formato.simbolo(usuario.getMoneda()));
        cmbCuenta.getItems().setAll(servicio.cuentasDisponibles(usuario));
        if (cuentaInicial != null) {
            cmbCuenta.getItems().stream().filter(c -> c.getId() == cuentaInicial.getId()).findFirst().ifPresent(cmbCuenta::setValue);
        }
        dpFecha.setValue(LocalDate.now());
        cambiarTipo();
    }

    /**
     * Cambia el signo del importe y las categorías que se pueden elegir según sea gasto o ingreso
     */
    private void cambiarTipo() {
        lblSigno.setText(tipo().equals("gasto") ? SIGNO_GASTO : SIGNO_INGRESO);
        cmbCategoria.getItems().setAll(servicio.categoriasDisponibles(usuario, tipo()));
        cmbCategoria.setValue(null);
    }

    /**
     * @return Devuelve "gasto" o "ingreso" según lo elegido arriba
     */
    private String tipo() {
        return grupoTipo.getSelectedToggle() == tglIngreso ? "ingreso" : "gasto";
    }

    /**
     * Valida los datos y guarda el movimiento
     */
    @FXML
    private void guardar() {
        ocultarError();
        Campos.marcarError(cajaImporte, false);
        Campos.marcarError(cajaConcepto, false);
        Campos.marcarError(cajaNota, false);
        cmbCuenta.getStyleClass().remove("error");
        cmbCategoria.getStyleClass().remove("error");
        dpFecha.getStyleClass().remove("error");

        // Si se escribió la fecha a mano y no se ha confirmado, se lee lo que hay en el campo
        dpFecha.setValue(dpFecha.getConverter().fromString(dpFecha.getEditor().getText()));

        BigDecimal importe;
        try {
            importe = Importes.leer(txtImporte.getText());
        } catch (NumberFormatException e) {
            Campos.marcarError(cajaImporte, true);
            mostrarError("El importe no es un número válido. Ejemplo: 32,50");
            return;
        }
        Cuenta cuenta = cmbCuenta.getValue();
        Categoria categoria = cmbCategoria.getValue();
        try {
            Integer cuentaId = cuenta == null ? null : cuenta.getId();
            Integer categoriaId = categoria == null ? null : categoria.getId();
            resultado = Optional.of(editando == null
                    ? servicio.guardar(usuario, cuentaId, categoriaId, tipo(), importe, dpFecha.getValue(),
                            txtConcepto.getText(), txtNota.getText(), frecuencia())
                    : servicio.actualizar(usuario, editando.getId(), cuentaId, categoriaId, tipo(), importe,
                            dpFecha.getValue(), txtConcepto.getText(), txtNota.getText()));
            cerrar();
        } catch (DatoInvalido e) {
            switch (e.getCampo()) {
                case "importe" -> Campos.marcarError(cajaImporte, true);
                case "concepto" -> Campos.marcarError(cajaConcepto, true);
                case "nota" -> Campos.marcarError(cajaNota, true);
                case "cuenta" -> cmbCuenta.getStyleClass().add("error");
                case "categoria" -> cmbCategoria.getStyleClass().add("error");
                case "fecha" -> dpFecha.getStyleClass().add("error");
                default -> { }
            }
            mostrarError(e.getMessage());
        }
    }

    /**
     * Cierra la ventana sin guardar nada
     */
    @FXML
    private void cancelar() {
        cerrar();
    }

    /**
     * Cierra la ventana
     */
    private void cerrar() {
        ((Stage) txtImporte.getScene().getWindow()).close();
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
