package controllers;

import java.io.IOException;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.shape.SVGPath;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import models.Usuario;
import service.auth.AuthService;
import service.sync.FirebaseAuthClient.AuthException;
import util.Campos;
import util.Contrasena;

/**
 * Controlador de CambiarContrasenaDialog.fxml: pide la contraseña actual y la nueva (dos veces).
 * En una cuenta online la contraseña se cambia en Firebase y en este equipo (hace falta conexión); en una
 * cuenta offline solo en este equipo. La lógica está en AuthService.cambiarContrasena.
 *
 * @author Fco Padilla
 */
public class CambiarContrasenaController {

    @FXML private Label lblNota;
    @FXML private HBox cajaActual;
    @FXML private HBox cajaNueva;
    @FXML private HBox cajaRepetir;
    @FXML private PasswordField txtActual;
    @FXML private PasswordField txtNuevaOculta;
    @FXML private TextField txtNuevaVisible;
    @FXML private PasswordField txtRepetir;
    @FXML private SVGPath iconoOjo;
    @FXML private Region tramo1;
    @FXML private Region tramo2;
    @FXML private Region tramo3;
    @FXML private Region tramo4;
    @FXML private Label lblMedidor;
    @FXML private Label lblError;
    @FXML private Button btnCambiar;

    private final AuthService auth = new AuthService();
    private Usuario usuario;
    private boolean cambiada = false;
    private Region[] tramos;

    /**
     * Prepara los campos: une la contraseña nueva oculta con la visible, quita el error al escribir y
     * actualiza el medidor
     */
    @FXML
    private void initialize() {
        tramos = new Region[] { tramo1, tramo2, tramo3, tramo4 };
        Campos.enlazarContrasena(txtNuevaOculta, txtNuevaVisible);
        Campos.configurar(cajaActual, txtActual);
        Campos.configurar(cajaNueva, txtNuevaOculta, txtNuevaVisible);
        Campos.configurar(cajaRepetir, txtRepetir);
        txtNuevaOculta.textProperty().addListener((obs, antes, ahora) -> actualizarMedidor(ahora));
        Platform.runLater(txtActual::requestFocus);
    }

    /**
     * Abre la ventana y espera a que se cierre.
     * @param propietario: ventana desde la que se abre (queda bloqueada hasta que se cierre)
     * @param usuario: usuario que cambia su contraseña
     * @return Devuelve true si la contraseña se ha cambiado
     */
    public static boolean mostrar(Window propietario, Usuario usuario) {
        try {
            FXMLLoader loader = new FXMLLoader(CambiarContrasenaController.class.getResource("/screens/dialogs/CambiarContrasenaDialog.fxml"));
            Parent raiz = loader.load();
            CambiarContrasenaController controlador = loader.getController();
            controlador.usuario = usuario;
            if (!usuario.isSoloLocal()) {
                controlador.lblNota.setText("Escribe tu contraseña actual y la nueva. También se cambiará en tu cuenta online, "
                        + "así que hace falta conexión.");
            }

            Stage ventana = new Stage();
            ventana.initOwner(propietario);
            ventana.initModality(Modality.WINDOW_MODAL);
            ventana.setTitle("MisCuentas · Cambiar contraseña");
            ventana.setResizable(false);
            ventana.setScene(new Scene(raiz));
            ventana.showAndWait();
            return controlador.cambiada;
        } catch (IOException e) {
            throw new java.io.UncheckedIOException("No se pudo abrir la ventana de cambiar contraseña", e);
        }
    }

    /**
     * Muestra u oculta la contraseña nueva
     */
    @FXML
    private void alternarNueva() {
        Campos.alternarContrasena(txtNuevaOculta, txtNuevaVisible, iconoOjo);
    }

    /**
     * Cierra la ventana sin cambiar nada
     */
    @FXML
    private void cancelar() {
        cerrar();
    }

    /**
     * Valida los campos y cambia la contraseña en segundo plano (calcular la huella y hablar con
     * Firebase tardan)
     */
    @FXML
    private void cambiar() {
        if (btnCambiar.isDisabled()) {
            return;
        }
        ocultarError();
        String actual = txtActual.getText();
        String nueva = txtNuevaOculta.getText();
        if (actual.isEmpty()) {
            Campos.marcarError(cajaActual, true);
            mostrarError("Escribe tu contraseña actual.");
            return;
        }
        if (!Contrasena.cumpleMinimos(nueva)) {
            Campos.marcarError(cajaNueva, true);
            mostrarError("La contraseña nueva debe tener al menos 8 caracteres, con letras y números.");
            return;
        }
        if (nueva.equals(actual)) {
            Campos.marcarError(cajaNueva, true);
            mostrarError("La contraseña nueva tiene que ser distinta de la actual.");
            return;
        }
        if (!nueva.equals(txtRepetir.getText())) {
            Campos.marcarError(cajaRepetir, true);
            mostrarError("Las dos contraseñas nuevas no coinciden.");
            return;
        }

        ocupado(true);
        Task<Void> tarea = new Task<>() {
            @Override
            protected Void call() throws Exception {
                auth.cambiarContrasena(usuario, actual, nueva);
                return null;
            }
        };
        tarea.setOnSucceeded(e -> {
            cambiada = true;
            cerrar();
        });
        tarea.setOnFailed(e -> {
            ocupado(false);
            Throwable fallo = tarea.getException();
            if (fallo instanceof AuthException a) {
                if ("PASSWORD_ACTUAL_INCORRECTA".equals(a.getCodigo())) {
                    Campos.marcarError(cajaActual, true);
                }
                mostrarError(a.getMessage());
            } else if (fallo instanceof IOException) {
                mostrarError("No se ha podido conectar con Firebase. Para cambiar la contraseña de una cuenta online necesitas conexión a internet.");
            } else {
                mostrarError("No se pudo cambiar la contraseña: " + fallo);
            }
        });
        Thread hilo = new Thread(tarea, "cambiar-contrasena");
        hilo.setDaemon(true);
        hilo.start();
    }

    /**
     * Rellena la barra y el texto del medidor según la contraseña nueva
     * @param pw: contraseña nueva escrita
     */
    private void actualizarMedidor(String pw) {
        Contrasena.Fortaleza f = Contrasena.evaluar(pw);
        for (int i = 0; i < tramos.length; i++) {
            tramos[i].getStyleClass().removeAll("lleno", "debil");
            if (i < f.tramos()) {
                tramos[i].getStyleClass().add(f.debil() ? "debil" : "lleno");
            }
        }
        lblMedidor.getStyleClass().remove("debil");
        if (f.debil()) {
            lblMedidor.getStyleClass().add("debil");
        }
        lblMedidor.setText(f.mensaje());
    }

    /**
     * Deshabilita el botón mientras se cambia la contraseña
     * @param si: si se está cambiando o no
     */
    private void ocupado(boolean si) {
        btnCambiar.setDisable(si);
        btnCambiar.setText(si ? "Cambiando…" : "Cambiar contraseña");
    }

    /**
     * Cierra la ventana
     */
    private void cerrar() {
        ((Stage) txtActual.getScene().getWindow()).close();
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
