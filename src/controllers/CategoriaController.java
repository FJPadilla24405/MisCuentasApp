package controllers;

import java.io.IOException;
import java.io.UncheckedIOException;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import models.Categoria;
import models.Usuario;
import service.DatoInvalido;
import service.categoria.CategoriaService;
import util.Campos;

/**
 * Controlador de CategoriaDialog.fxml: ventana para crear o editar una categoría (nombre, tipo y
 * subcategoría opcional). Se abre con CategoriaController.mostrar(...). La lógica está en CategoriaService.
 *
 * @author Fco Padilla
 */
public class CategoriaController {

    @FXML private Label lblTitulo;
    @FXML private HBox cajaNombre;
    @FXML private HBox cajaSubcategoria;
    @FXML private TextField txtNombre;
    @FXML private TextField txtSubcategoria;
    @FXML private ToggleGroup grupoTipo;
    @FXML private ToggleButton tglGasto;
    @FXML private ToggleButton tglIngreso;
    @FXML private Label lblTipoNota;
    @FXML private Label lblError;

    private final CategoriaService servicio = new CategoriaService();
    private Usuario usuario;
    private Categoria existente;
    // true si se ha guardado la categoría
    private boolean guardada = false;

    /**
     * Impide dejar el tipo sin elegir y quita el borde rojo al escribir
     */
    @FXML
    private void initialize() {
        grupoTipo.selectedToggleProperty().addListener((obs, antes, ahora) -> {
            if (ahora == null) {
                grupoTipo.selectToggle(antes);
            }
        });
        Campos.configurar(cajaNombre, txtNombre);
        Campos.configurar(cajaSubcategoria, txtSubcategoria);
        Platform.runLater(txtNombre::requestFocus);
    }

    /**
     * Abre la ventana y espera a que se cierre.
     * @param propietario: ventana desde la que se abre (queda bloqueada hasta que se cierre)
     * @param usuario: usuario de la sesión
     * @param existente: categoría a editar, o null para crear una nueva
     * @return Devuelve true si se ha guardado la categoría
     */
    public static boolean mostrar(Window propietario, Usuario usuario, Categoria existente) {
        try {
            FXMLLoader loader = new FXMLLoader(CategoriaController.class.getResource("/screens/dialogs/CategoriaDialog.fxml"));
            Parent raiz = loader.load();
            CategoriaController controlador = loader.getController();
            controlador.preparar(usuario, existente);

            Stage ventana = new Stage();
            ventana.initOwner(propietario);
            ventana.initModality(Modality.WINDOW_MODAL);
            ventana.setTitle(existente == null ? "MisCuentas · Añadir categoría" : "MisCuentas · Editar categoría");
            ventana.setResizable(false);
            ventana.setScene(new Scene(raiz));
            ventana.showAndWait();
            return controlador.guardada;
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo abrir la ventana de la categoría", e);
        }
    }

    /**
     * Rellena la ventana si se edita una categoría. Si la categoría ya se usa, el tipo no se puede cambiar.
     * @param usuario: usuario de la sesión
     * @param existente: categoría a editar, o null
     */
    private void preparar(Usuario usuario, Categoria existente) {
        this.usuario = usuario;
        this.existente = existente;
        if (existente == null) {
            return;
        }
        lblTitulo.setText("Editar categoría");
        txtNombre.setText(existente.getNombre());
        txtSubcategoria.setText(existente.getSubcategoria() == null ? "" : existente.getSubcategoria());
        (("ingreso".equals(existente.getTipo())) ? tglIngreso : tglGasto).setSelected(true);
        if (servicio.usos(existente).enUso()) {
            tglGasto.setDisable(true);
            tglIngreso.setDisable(true);
            lblTipoNota.setVisible(true);
            lblTipoNota.setManaged(true);
        }
    }

    /**
     * Valida los datos y guarda la categoría
     */
    @FXML
    private void guardar() {
        ocultarError();
        Campos.marcarError(cajaNombre, false);
        Campos.marcarError(cajaSubcategoria, false);
        try {
            servicio.guardar(usuario, existente, txtNombre.getText(), grupoTipo.getSelectedToggle() == tglIngreso ? "ingreso" : "gasto",
                    txtSubcategoria.getText());
            guardada = true;
            cerrar();
        } catch (DatoInvalido e) {
            if ("nombre".equals(e.getCampo())) {
                Campos.marcarError(cajaNombre, true);
            } else if ("subcategoria".equals(e.getCampo())) {
                Campos.marcarError(cajaSubcategoria, true);
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
