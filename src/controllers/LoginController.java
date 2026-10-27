package controllers;

import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import models.Usuario;
import service.auth.AuthService;
import service.auth.AuthService.ResultadoLogin;
import service.auth.SesionActual;
import util.Campos;

/**
 * Controlador de LoginPanel.fxml. Pide email y contraseña.
 * Primero se comprueba la contraseña con la huella guardada en este equipo y, si la cuenta es online y
 * no se ha marcado "Iniciar sesión sin conexión", se valida también en Firebase. Con una contraseña
 * incorrecta no se entra. Si no hay conexión con Firebase, un popup deja elegir entre reintentar o
 * continuar sin conexión.
 * La lógica está en AuthService.iniciarSesion; este controlador solo recoge los datos y muestra el resultado.
 *
 * @author Fco Padilla
 */
public class LoginController {

    @FXML private VBox raiz;
    @FXML private HBox cajaEmail;
    @FXML private HBox cajaPassword;
    @FXML private TextField txtEmail;
    @FXML private PasswordField txtPasswordOculto;
    @FXML private TextField txtPasswordVisible;
    @FXML private SVGPath iconoOjo;
    @FXML private CheckBox chkSinConexion;
    @FXML private Label lblError;
    @FXML private Button btnEntrar;

    private final AuthService auth = new AuthService();
    private Runnable alIrARegistro = () -> { };
    private Consumer<String> alIrARecuperar = email -> { };
    private Consumer<Usuario> alAcceder = u -> { };

    /**
     * Prepara los campos: une la contraseña oculta con la visible y quita el error al escribir
     */
    @FXML
    private void initialize() {
        Campos.enlazarContrasena(txtPasswordOculto, txtPasswordVisible);
        Campos.configurar(cajaEmail, txtEmail);
        Campos.configurar(cajaPassword, txtPasswordOculto, txtPasswordVisible);
    }

    /**
     * Cambia al panel de registro
     * @param accion: cambio de panel
     */
    public void setAlIrARegistro(Runnable accion) {
        this.alIrARegistro = accion;
    }

    /**
     * Recibe el email que ya se había escrito, para que el panel de recuperar lo muestre relleno.
     * @param accion: cambio de panel
     */
    public void setAlIrARecuperar(Consumer<String> accion) {
        this.alIrARecuperar = accion;
    }

    /**
     * Accede al resto de la aplicación
     * @param accion: cambio de pantalla
     */
    public void setAlAcceder(Consumer<Usuario> accion) {
        this.alAcceder = accion;
    }

    /**
     * Se llama al volver a este panel: quita errores y la contraseña escrita.
     */
    public void reiniciar() {
        ocultarError();
        txtPasswordOculto.clear();
        Campos.marcarError(cajaEmail, false);
        Campos.marcarError(cajaPassword, false);
    }

    /**
     * Permite que el campo sea interactuable
     */
    public void enfocar() {
        Platform.runLater(txtEmail::requestFocus);
    }

    /**
     * Va al panel de registro
     */
    @FXML
    private void irARegistro() {
        alIrARegistro.run();
    }

    /**
     * Va al panel de recuperar contraseña
     */
    @FXML
    private void irARecuperar() {
        alIrARecuperar.accept(txtEmail.getText().trim());
    }

    /**
     * Muestra o no la contraseña que se está escribiendo
     */
    @FXML
    private void alternarPassword() {
        Campos.alternarContrasena(txtPasswordOculto, txtPasswordVisible, iconoOjo);
    }

    /**
     * Recoge el email y la contraseña y comprueba el inicio de sesión
     */
    @FXML
    private void iniciarSesion() {
        ocultarError();
        String correo = txtEmail.getText().trim();
        String clave = txtPasswordOculto.getText();

        boolean completo = true;
        if (correo.isEmpty()) {
            Campos.marcarError(cajaEmail, true);
            completo = false;
        }
        if (clave.isEmpty()) {
            Campos.marcarError(cajaPassword, true);
            completo = false;
        }
        if (!completo) {
            mostrarError("Rellena el email y la contraseña.");
            return;
        }
        comprobar(correo, clave, chkSinConexion.isSelected());
    }

    /**
     * Lanza la comprobación en segundo plano (calcular la huella y consultar Firebase tardan) y
     * trata el resultado cuando termina.
     * @param correo: email escrito
     * @param clave: contraseña escrita
     * @param sinConexion: true si se ha marcado iniciar sesión sin conexión
     */
    private void comprobar(String correo, String clave, boolean sinConexion) {
        ocupado(true);
        Task<ResultadoLogin> tarea = new Task<>() {
            @Override
            protected ResultadoLogin call() throws Exception {
                return auth.iniciarSesion(correo, clave, sinConexion);
            }
        };
        tarea.setOnSucceeded(e -> {
            ocupado(false);
            tratarResultado(tarea.getValue(), correo, clave);
        });
        tarea.setOnFailed(e -> {
            ocupado(false);
            mostrarError("No se pudo iniciar sesión: " + tarea.getException());
        });
        Thread hilo = new Thread(tarea, "login");
        hilo.setDaemon(true);
        hilo.start();
    }

    /**
     * Hace lo que corresponda según cómo haya ido el inicio de sesión
     * @param resultado: resultado de AuthService.iniciarSesion
     * @param correo: email escrito
     * @param clave: contraseña escrita
     */
    private void tratarResultado(ResultadoLogin resultado, String correo, String clave) {
        switch (resultado.estado()) {
            case ONLINE -> {
                SesionActual.iniciar(resultado.usuario(), resultado.sesion());
                alAcceder.accept(resultado.usuario());
            }
            case OFFLINE -> {
                SesionActual.iniciarSinConexion(resultado.usuario());
                alAcceder.accept(resultado.usuario());
            }
            case SIN_VERIFICAR -> preguntarSinConexion(resultado, correo, clave);
            case CREDENCIALES_INCORRECTAS -> {
                Campos.marcarError(cajaEmail, true);
                Campos.marcarError(cajaPassword, true);
                mostrarError("Email o contraseña incorrectos.");
            }
            case RECHAZADA_POR_FIREBASE -> {
                Campos.marcarError(cajaPassword, true);
                mostrarError("Firebase no acepta esa contraseña: puede que la hayas cambiado. Escribe la nueva o, "
                        + "si quieres usar la guardada en este equipo, marca \"Iniciar sesión sin conexión\".");
            }
            case ERROR -> mostrarError(resultado.detalle());
        }
    }

    /**
     * La contraseña es correcta en este equipo pero no se ha podido verificar en Firebase: pregunta si
     * seguir sin conexión o reintentar.
     * @param resultado: resultado con el usuario y el motivo (null si simplemente no hay conexión)
     * @param correo: email escrito
     * @param clave: contraseña escrita
     */
    private void preguntarSinConexion(ResultadoLogin resultado, String correo, String clave) {
        String titulo;
        String mensaje;
        if (resultado.detalle() == null) {
            titulo = "Sin conexión con Firebase";
            mensaje = "No se ha podido contactar con Firebase.\n\n"
                    + "Puedes reintentarlo o continuar sin conexión (tus datos no se sincronizarán).";
        } else {
            titulo = "No se pudo verificar la cuenta";
            mensaje = resultado.detalle() + "\n\n"
                    + "Puedes reintentarlo o continuar sin conexión (tus datos no se sincronizarán).";
        }

        if (preguntarContinuarSinConexion(titulo, mensaje)) {
            SesionActual.iniciarSinConexion(resultado.usuario());
            alAcceder.accept(resultado.usuario());
        } else {
            comprobar(correo, clave, false);
        }
    }

    /**
     * Popup con las opciones Reintentar y Continuar sin conexión
     * @param titulo: título del dialog
     * @param mensaje: mensaje del dialog
     * @return Devuelve true para continuar sin conexión o false para reintentar; cerrar el popup equivale a reintentar
     */
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

    /**
     * Deshabilita el botón si se está iniciando sesión
     * @param si: si se está comprobando el inicio o no
     */
    private void ocupado(boolean si) {
        btnEntrar.setDisable(si);
        btnEntrar.setText(si ? "Comprobando…" : "Iniciar sesión");
    }

    /**
     * Muestra el error que ha sucedido
     * @param texto: texto del error
     */
    private void mostrarError(String texto) {
        lblError.setText(texto);
        lblError.setVisible(true);
        lblError.setManaged(true);
    }

    /**
     * Oculta el error
     */
    private void ocultarError() {
        lblError.setText("");
        lblError.setVisible(false);
        lblError.setManaged(false);
    }
}
