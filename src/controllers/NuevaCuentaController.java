package controllers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.Optional;

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
import models.Cuenta;
import models.Usuario;
import service.dao.CuentaDAO;
import util.Campos;
import util.Dinero;
import util.Formato;

/**
 * Controlador de NuevaCuentaDialog.fxml: ventana para crear una cuenta (nombre, tipo y saldo inicial).
 * La moneda es la del usuario. Se abre con NuevaCuentaController.mostrar(...).
 *
 * @author Fco Padilla
 */
public class NuevaCuentaController {

    // Longitud máxima del nombre, la misma que el VARCHAR(50) del esquema
    private static final int MAX_NOMBRE = 50;

    @FXML private HBox cajaNombre;
    @FXML private HBox cajaSaldo;
    @FXML private TextField txtNombre;
    @FXML private TextField txtSaldo;
    @FXML private Label lblMoneda;
    @FXML private Label lblError;
    @FXML private ToggleGroup grupoTipo;
    @FXML private ToggleButton tglBanco;
    @FXML private ToggleButton tglEfectivo;

    private final CuentaDAO cuentaDAO = new CuentaDAO();
    private Usuario usuario;
    private Cuenta creada;

    /**
     * Prepara los campos: impide dejar el tipo sin elegir y quita el error al escribir
     */
    @FXML
    private void initialize() {
        grupoTipo.selectedToggleProperty().addListener((obs, antes, ahora) -> {
            if (ahora == null) {
                grupoTipo.selectToggle(antes);
            }
        });
        Campos.configurar(cajaNombre, txtNombre);
        Campos.configurar(cajaSaldo, txtSaldo);
        Platform.runLater(txtNombre::requestFocus);
    }

    /**
     * Abre la ventana y espera a que se cierre.
     * @param propietario: ventana desde la que se abre (queda bloqueada hasta que se cierre)
     * @param usuario: dueño de la cuenta que se va a crear
     * @return Devuelve la cuenta creada, o vacío si se canceló
     */
    public static Optional<Cuenta> mostrar(Window propietario, Usuario usuario) {
        try {
            FXMLLoader loader = new FXMLLoader(NuevaCuentaController.class.getResource("/screens/dialogs/NuevaCuentaDialog.fxml"));
            Parent raiz = loader.load();
            NuevaCuentaController controlador = loader.getController();
            controlador.usuario = usuario;
            controlador.lblMoneda.setText(Formato.simbolo(usuario.getMoneda()));

            Stage ventana = new Stage();
            ventana.initOwner(propietario);
            ventana.initModality(Modality.WINDOW_MODAL);
            ventana.setTitle("MisCuentas · Añadir cuenta");
            ventana.setResizable(false);
            ventana.setScene(new Scene(raiz));
            ventana.showAndWait();
            return Optional.ofNullable(controlador.creada);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo abrir la ventana de nueva cuenta", e);
        }
    }

    /**
     * Valida los datos y crea la cuenta
     */
    @FXML
    private void crear() {
        ocultarError();
        String nombre = txtNombre.getText().trim();
        if (nombre.isEmpty() || nombre.length() > MAX_NOMBRE) {
            Campos.marcarError(cajaNombre, true);
            mostrarError(nombre.isEmpty() ? "Escribe un nombre para la cuenta."
                    : "El nombre no puede superar " + MAX_NOMBRE + " caracteres.");
            return;
        }
        boolean repetida = cuentaDAO.listarPorUsuario(usuario.getId()).stream()
                .anyMatch(c -> c.getNombre().trim().equalsIgnoreCase(nombre));
        if (repetida) {
            Campos.marcarError(cajaNombre, true);
            mostrarError("Ya tienes una cuenta con ese nombre.");
            return;
        }

        BigDecimal saldo;
        try {
            saldo = Dinero.normalizar(leerImporte(txtSaldo.getText()));
        } catch (NumberFormatException e) {
            Campos.marcarError(cajaSaldo, true);
            mostrarError("El saldo inicial no es un número válido. Ejemplo: 1.250,50");
            return;
        } catch (IllegalArgumentException e) {
            Campos.marcarError(cajaSaldo, true);
            mostrarError("El saldo inicial es demasiado grande.");
            return;
        }

        String tipo = grupoTipo.getSelectedToggle() == tglEfectivo ? "efectivo" : "banco";
        creada = cuentaDAO.insertar(new Cuenta(0, usuario.getId(), nombre, tipo, saldo, usuario.getMoneda(), true));
        cerrar();
    }

    /**
     * Cierra la ventana sin crear nada
     */
    @FXML
    private void cancelar() {
        cerrar();
    }

    /**
     * Convierte el texto del saldo en número. Acepta 1250.5, 1250,5 y 1.250,50; un solo punto seguido
     * de tres cifras (1.250) se entiende como separador de miles. Un campo vacío es 0.
     * @param texto: lo escrito en el campo
     * @return Devuelve el importe
     * @throws NumberFormatException si no es un número
     */
    private BigDecimal leerImporte(String texto) {
        String t = texto.trim().replace(" ", "").replace("\u00A0", "").replace("€", "");
        if (t.isEmpty()) {
            return Dinero.CERO;
        }
        if (t.contains(",")) {
            t = t.replace(".", "").replace(",", ".");
        } else if (t.matches("-?\\d{1,3}(\\.\\d{3})+")) {
            t = t.replace(".", "");
        }
        return new BigDecimal(t);
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
