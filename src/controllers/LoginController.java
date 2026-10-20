package controllers;

import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import models.Usuario;
import service.auth.AuthService;
import service.auth.SesionActual;
import service.sync.FirebaseAuthClient.AuthException;
import service.sync.FirebaseAuthClient.Sesion;
import util.Campos;

/**
 * Controlador de LoginPanel.fxml.
 * Paso 1: usuario y email deben coincidir con la base de datos local (si no, no se entra).
 * Paso 2: email y contraseña se validan en Firebase; si fallan o no hay conexión, un popup deja
 * elegir entre reintentar o continuar sin conexión.
 *
 * @author Fco Padilla
 */
public class LoginController {

    /** Códigos de Firebase que significan "esas credenciales no valen". */
    private static final Set<String> CREDENCIALES_INCORRECTAS =
            Set.of("INVALID_LOGIN_CREDENTIALS", "INVALID_PASSWORD", "EMAIL_NOT_FOUND");

    @FXML private VBox raiz;
    @FXML private HBox cajaUsuario;
    @FXML private HBox cajaEmail;
    @FXML private HBox cajaPassword;
    @FXML private TextField txtUsuario;
    @FXML private TextField txtEmail;
    @FXML private PasswordField txtPasswordOculto;
    @FXML private TextField txtPasswordVisible;
    @FXML private SVGPath iconoOjo;
    @FXML private Label lblError;
    @FXML private Button btnEntrar;

    private final AuthService auth = new AuthService();
    private Runnable alIrARegistro = () -> { };
    private Consumer<String> alIrARecuperar = email -> { };
    private Consumer<Usuario> alAcceder = u -> { };

    @FXML
    private void initialize() {
        Campos.enlazarContrasena(txtPasswordOculto, txtPasswordVisible);
        Campos.configurar(cajaUsuario, txtUsuario);
        Campos.configurar(cajaEmail, txtEmail);
        Campos.configurar(cajaPassword, txtPasswordOculto, txtPasswordVisible);
    }

    // ---------------------------------------------------------------- lo que le dice AuthController

    public void setAlIrARegistro(Runnable accion) {
        this.alIrARegistro = accion;
    }

    /** Recibe el email que ya se había escrito, para que el panel de recuperar lo muestre relleno. */
    public void setAlIrARecuperar(Consumer<String> accion) {
        this.alIrARecuperar = accion;
    }

    public void setAlAcceder(Consumer<Usuario> accion) {
        this.alAcceder = accion;
    }

    /** Se llama al volver a este panel: quita errores y la contraseña escrita. */
    public void reiniciar() {
        ocultarError();
        txtPasswordOculto.clear();
        Campos.marcarError(cajaUsuario, false);
        Campos.marcarError(cajaEmail, false);
        Campos.marcarError(cajaPassword, false);
    }

    public void enfocar() {
        Platform.runLater(txtUsuario::requestFocus);
    }

    // ---------------------------------------------------------------- acciones del FXML

    @FXML
    private void irARegistro() {
        alIrARegistro.run();
    }

    @FXML
    private void irARecuperar() {
        alIrARecuperar.accept(txtEmail.getText().trim());
    }

    @FXML
    private void alternarPassword() {
        Campos.alternarContrasena(txtPasswordOculto, txtPasswordVisible, iconoOjo);
    }

    @FXML
    private void iniciarSesion() {
        ocultarError();
        String nombre = txtUsuario.getText().trim();
        String correo = txtEmail.getText().trim();
        String clave = txtPasswordOculto.getText();

        boolean completo = true;
        if (nombre.isEmpty()) {
            Campos.marcarError(cajaUsuario, true);
            completo = false;
        }
        if (correo.isEmpty()) {
            Campos.marcarError(cajaEmail, true);
            completo = false;
        }
        if (clave.isEmpty()) {
            Campos.marcarError(cajaPassword, true);
            completo = false;
        }
        if (!completo) {
            mostrarError("Rellena el usuario, el email y la contraseña.");
            return;
        }

        // Paso 1: base de datos local (consulta rápida a SQLite)
        Optional<Usuario> local = auth.buscarUsuarioLocal(nombre, correo);
        if (local.isEmpty()) {
            Campos.marcarError(cajaUsuario, true);
            Campos.marcarError(cajaEmail, true);
            mostrarError("El usuario y el email no coinciden con ninguna cuenta de este equipo.");
            return;
        }

        // Paso 2: Firebase (red, en segundo plano)
        comprobarFirebase(local.get(), correo, clave);
    }

    // ---------------------------------------------------------------- Firebase

    private void comprobarFirebase(Usuario local, String correo, String clave) {
        ocupado(true);
        Task<Sesion> tarea = new Task<>() {
            @Override
            protected Sesion call() throws Exception {
                return auth.iniciarSesionFirebase(correo, clave);
            }
        };
        tarea.setOnSucceeded(e -> {
            ocupado(false);
            SesionActual.iniciar(local, tarea.getValue());
            alAcceder.accept(local);
        });
        tarea.setOnFailed(e -> {
            ocupado(false);
            tratarFalloFirebase(local, correo, clave, tarea.getException());
        });
        Thread hebra = new Thread(tarea, "login-firebase");
        hebra.setDaemon(true);
        hebra.start();
    }

    /** Pregunta si seguir sin conexión o reintentar, según el motivo del fallo. */
    private void tratarFalloFirebase(Usuario local, String correo, String clave, Throwable fallo) {
        boolean contrasenaIncorrecta = fallo instanceof AuthException a
                && CREDENCIALES_INCORRECTAS.contains(a.getCodigo());

        String titulo;
        String mensaje;
        if (contrasenaIncorrecta) {
            titulo = "Contraseña incorrecta";
            mensaje = "Firebase no acepta esa contraseña para este correo.\n\n"
                    + "Puedes volver a escribirla o continuar sin conexión (tus datos no se sincronizarán).";
        } else if (fallo instanceof AuthException a) {
            titulo = "No se pudo verificar la cuenta";
            mensaje = a.getMessage() + "\n\n"
                    + "Puedes reintentarlo o continuar sin conexión (tus datos no se sincronizarán).";
        } else {
            titulo = "Sin conexión con Firebase";
            mensaje = "No se ha podido contactar con Firebase.\n\n"
                    + "Puedes reintentarlo o continuar sin conexión (tus datos no se sincronizarán).";
        }

        if (preguntarContinuarSinConexion(titulo, mensaje)) {
            SesionActual.iniciarSinConexion(local);
            alAcceder.accept(local);
        } else if (contrasenaIncorrecta) {
            txtPasswordOculto.clear();
            txtPasswordOculto.requestFocus();
        } else {
            comprobarFirebase(local, correo, clave);
        }
    }

    /** @return true si el usuario elige "Continuar sin conexión"; cerrar el popup equivale a reintentar. */
    private boolean preguntarContinuarSinConexion(String titulo, String mensaje) {
        ButtonType reintentar = new ButtonType("Reintentar", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType continuar = new ButtonType("Continuar sin conexión", ButtonBar.ButtonData.OK_DONE);

        Alert popup = new Alert(Alert.AlertType.WARNING, mensaje, reintentar, continuar);
        popup.setTitle("MisCuentas");
        popup.setHeaderText(titulo);
        popup.setGraphic(null);
        popup.initOwner(raiz.getScene().getWindow());
        popup.getDialogPane().getStylesheets()
                .add(getClass().getResource("/screens/styles/application.css").toExternalForm());
        popup.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        popup.getDialogPane().lookupButton(continuar).getStyleClass().add("boton-primario");
        popup.getDialogPane().lookupButton(reintentar).getStyleClass().add("boton-secundario");

        return popup.showAndWait().filter(b -> b == continuar).isPresent();
    }

    // ---------------------------------------------------------------- ayudas

    private void ocupado(boolean si) {
        btnEntrar.setDisable(si);
        btnEntrar.setText(si ? "Comprobando…" : "Iniciar sesión");
    }

    private void mostrarError(String texto) {
        lblError.setText(texto);
        lblError.setVisible(true);
        lblError.setManaged(true);
    }

    private void ocultarError() {
        lblError.setText("");
        lblError.setVisible(false);
        lblError.setManaged(false);
    }
}
