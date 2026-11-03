package controllers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;
import models.Categoria;
import models.Presupuesto;
import models.Usuario;
import service.categoria.CategoriaService;
import service.presupuesto.PresupuestoService;
import service.presupuesto.PresupuestoService.DatoInvalido;
import util.Campos;
import util.Formato;
import util.Importe;

/**
 * Controlador de PresupuestoDialog.fxml: ventana para crear o editar un presupuesto (nombre, categoría,
 * importe límite, mes, año y alcance de cuentas). Al editar aparece además "Eliminar".
 * Se abre con PresupuestoController.mostrar(...). La lógica está en PresupuestoService.
 *
 * @author Fco Padilla
 */
public class PresupuestoController {

    @FXML private Label lblEstado;
    @FXML private HBox cajaNombre;
    @FXML private HBox cajaImporte;
    @FXML private TextField txtNombre;
    @FXML private TextField txtImporte;
    @FXML private Label lblMoneda;
    @FXML private ComboBox<Categoria> cmbCategoria;
    @FXML private ComboBox<Integer> cmbMes;
    @FXML private ComboBox<Integer> cmbAnio;
    @FXML private Label lblMesNota;
    @FXML private ToggleGroup grupoAlcance;
    @FXML private RadioButton rbTodas;
    @FXML private RadioButton rbBanco;
    @FXML private RadioButton rbEfectivo;
    @FXML private Label lblAlcanceNota;
    @FXML private Label lblError;
    @FXML private Button btnEliminar;

    private final PresupuestoService servicio = new PresupuestoService();
    private final CategoriaService categoriaService = new CategoriaService();
    private Usuario usuario;
    private Presupuesto existente;
    // Mes del presupuesto guardado o eliminado; vacío si se cancela
    private Optional<YearMonth> resultado = Optional.empty();
    // true cuando el usuario ya ha escrito el nombre a mano: deja de rellenarse solo al cambiar de categoría
    private boolean nombreManual = false;
    // true mientras el programa escribe el nombre, para no confundirlo con el usuario
    private boolean rellenandoNombre = false;

    /**
     * Prepara los selectores y los avisos que cambian al elegir mes, año y alcance
     */
    @FXML
    private void initialize() {
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
        cmbMes.setConverter(new StringConverter<>() {
            @Override
            public String toString(Integer mes) {
                if (mes == null) {
                    return "";
                }
                String nombre = Formato.nombreMes(YearMonth.of(2000, mes));
                return nombre.substring(0, 1).toUpperCase(Locale.forLanguageTag("es-ES")) + nombre.substring(1);
            }

            @Override
            public Integer fromString(String texto) {
                return null;
            }
        });
        for (int m = 1; m <= 12; m++) {
            cmbMes.getItems().add(m);
        }

        Campos.configurar(cajaNombre, txtNombre);
        Campos.configurar(cajaImporte, txtImporte);
        txtNombre.textProperty().addListener((obs, antes, ahora) -> {
            if (!rellenandoNombre) {
                nombreManual = true;
            }
        });
        cmbCategoria.valueProperty().addListener((obs, antes, ahora) -> {
            cmbCategoria.getStyleClass().remove("error");
            rellenarNombreSugerido();
        });
        cmbMes.valueProperty().addListener((obs, antes, ahora) -> actualizarNotaMes());
        cmbAnio.valueProperty().addListener((obs, antes, ahora) -> actualizarNotaMes());
        grupoAlcance.selectedToggleProperty().addListener((obs, antes, ahora) -> {
            if (ahora == null) {
                grupoAlcance.selectToggle(antes);
            } else {
                actualizarNotaAlcance();
            }
        });
        Platform.runLater(txtNombre::requestFocus);
    }

    /**
     * Abre la ventana y espera a que se cierre.
     * @param propietario: ventana desde la que se abre (queda bloqueada hasta que se cierre)
     * @param usuario: usuario de la sesión
     * @param mesInicial: mes que se propone para un presupuesto nuevo
     * @param existente: presupuesto a editar, o null para crear uno nuevo
     * @return Devuelve el mes del presupuesto guardado o eliminado, o vacío si se canceló
     */
    public static Optional<YearMonth> mostrar(Window propietario, Usuario usuario, YearMonth mesInicial, Presupuesto existente) {
        try {
            FXMLLoader loader = new FXMLLoader(PresupuestoController.class.getResource("/screens/dialogs/PresupuestoDialog.fxml"));
            Parent raiz = loader.load();
            PresupuestoController controlador = loader.getController();
            controlador.preparar(usuario, mesInicial, existente);

            Stage ventana = new Stage();
            ventana.initOwner(propietario);
            ventana.initModality(Modality.WINDOW_MODAL);
            ventana.setTitle(existente == null ? "MisCuentas · Añadir presupuesto" : "MisCuentas · Editar presupuesto");
            ventana.setResizable(false);
            ventana.setScene(new Scene(raiz));
            ventana.showAndWait();
            return controlador.resultado;
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo abrir la ventana del presupuesto", e);
        }
    }

    /**
     * Rellena la ventana: las categorías y los años que se pueden elegir y, si se edita, los datos del presupuesto
     * @param usuario: usuario de la sesión
     * @param mesInicial: mes que se propone para un presupuesto nuevo
     * @param existente: presupuesto a editar, o null
     */
    private void preparar(Usuario usuario, YearMonth mesInicial, Presupuesto existente) {
        this.usuario = usuario;
        this.existente = existente;
        lblMoneda.setText(usuario.getMoneda() + " " + Formato.simbolo(usuario.getMoneda()));

        List<Categoria> categorias = categoriaService.categoriasDeGasto(usuario);
        cmbCategoria.getItems().setAll(categorias);

        YearMonth mes = existente == null ? mesInicial : YearMonth.parse(existente.getMesReferencia());
        List<Integer> anios = new ArrayList<>();
        for (int a = YearMonth.now().getYear() - 1; a <= YearMonth.now().getYear() + 3; a++) {
            anios.add(a);
        }
        if (!anios.contains(mes.getYear())) {
            anios.add(mes.getYear());
            anios.sort(null);
        }
        cmbAnio.getItems().setAll(anios);
        cmbMes.setValue(mes.getMonthValue());
        cmbAnio.setValue(mes.getYear());

        if (existente == null) {
            lblEstado.setText("Borrador sin guardar");
            // Se propone la primera categoría que todavía no tiene presupuesto ese mes
            Optional<Categoria> libre = categorias.stream()
                    .filter(c -> !servicio.tienePresupuesto(usuario, c.getId(), mes)).findFirst();
            libre.ifPresent(cmbCategoria::setValue);
        } else {
            lblEstado.setText("Editando presupuesto");
            btnEliminar.setVisible(true);
            btnEliminar.setManaged(true);
            categorias.stream().filter(c -> c.getId() == existente.getCategoriaId()).findFirst().ifPresent(cmbCategoria::setValue);
            txtNombre.setText(existente.getNombre() == null ? "" : existente.getNombre());
            nombreManual = existente.getNombre() != null && !existente.getNombre().isBlank();
            txtImporte.setText(existente.getLimite().toPlainString().replace('.', ','));
            switch (existente.getAlcance()) {
                case "banco" -> rbBanco.setSelected(true);
                case "efectivo" -> rbEfectivo.setSelected(true);
                default -> rbTodas.setSelected(true);
            }
            if (!nombreManual) {
                rellenarNombreSugerido();
            }
        }
        actualizarNotaMes();
        actualizarNotaAlcance();
    }

    /**
     * Pone como nombre "{categoría} del mes" mientras el usuario no haya escrito uno propio
     */
    private void rellenarNombreSugerido() {
        Categoria c = cmbCategoria.getValue();
        if (nombreManual || c == null) {
            return;
        }
        rellenandoNombre = true;
        txtNombre.setText(c.getNombre() + " del mes");
        rellenandoNombre = false;
    }

    /**
     * Escribe bajo el mes y el año el periodo que cubre: "Del 1 al 30 de septiembre de 2026."
     */
    private void actualizarNotaMes() {
        if (cmbMes.getValue() == null || cmbAnio.getValue() == null) {
            lblMesNota.setText("");
            return;
        }
        YearMonth mes = YearMonth.of(cmbAnio.getValue(), cmbMes.getValue());
        lblMesNota.setText("Del 1 al " + mes.lengthOfMonth() + " de " + Formato.nombreMes(mes) + " de " + mes.getYear() + ".");
    }

    /**
     * Escribe bajo el alcance qué cuentas incluye
     */
    private void actualizarNotaAlcance() {
        List<String> nombres = servicio.nombresDeCuentas(usuario, alcance());
        lblAlcanceNota.setText(nombres.isEmpty()
                ? "Todavía no tienes cuentas de este tipo: el presupuesto contará cuando las añadas."
                : "Incluye los gastos de " + Formato.lista(nombres) + " en esta categoría.");
    }

    /**
     * @return Devuelve el alcance elegido: "todas", "banco" o "efectivo"
     */
    private String alcance() {
        if (grupoAlcance.getSelectedToggle() == rbBanco) {
            return "banco";
        }
        return grupoAlcance.getSelectedToggle() == rbEfectivo ? "efectivo" : "todas";
    }

    /**
     * Valida los datos y guarda el presupuesto
     */
    @FXML
    private void guardar() {
        ocultarError();
        Campos.marcarError(cajaNombre, false);
        Campos.marcarError(cajaImporte, false);
        cmbCategoria.getStyleClass().remove("error");

        java.math.BigDecimal limite;
        try {
            limite = Importe.leer(txtImporte.getText());
        } catch (NumberFormatException e) {
            Campos.marcarError(cajaImporte, true);
            mostrarError("Escribe el importe límite como un número. Ejemplo: 350,00");
            return;
        }
        Categoria categoria = cmbCategoria.getValue();
        try {
            YearMonth mes = YearMonth.of(cmbAnio.getValue(), cmbMes.getValue());
            servicio.guardar(usuario, existente, txtNombre.getText(), categoria == null ? null : categoria.getId(), limite, mes, alcance());
            resultado = Optional.of(mes);
            cerrar();
        } catch (DatoInvalido e) {
            switch (e.getCampo()) {
                case "nombre" -> Campos.marcarError(cajaNombre, true);
                case "limite" -> Campos.marcarError(cajaImporte, true);
                case "categoria" -> cmbCategoria.getStyleClass().add("error");
                default -> { }
            }
            mostrarError(e.getMessage());
        }
    }

    /**
     * Elimina el presupuesto que se está editando, después de confirmarlo. No toca los movimientos.
     */
    @FXML
    private void eliminar() {
        ButtonType eliminar = new ButtonType("Eliminar", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelar = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION, "Tus movimientos y tus saldos no cambian: solo desaparece el límite.", eliminar, cancelar);
        confirmacion.setTitle("MisCuentas");
        confirmacion.setHeaderText("¿Eliminar este presupuesto?");
        confirmacion.setGraphic(null);
        confirmacion.initOwner(txtNombre.getScene().getWindow());
        confirmacion.getDialogPane().getStylesheets().add(getClass().getResource("/screens/styles/application.css").toExternalForm());
        if (confirmacion.showAndWait().filter(b -> b == eliminar).isPresent()) {
            servicio.eliminar(existente);
            resultado = Optional.of(YearMonth.parse(existente.getMesReferencia()));
            cerrar();
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
        ((Stage) txtNombre.getScene().getWindow()).close();
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
