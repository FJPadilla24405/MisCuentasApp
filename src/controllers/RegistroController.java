package controllers;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.shape.SVGPath;
import models.Usuario;
import service.auth.AuthService;
import service.sync.FirebaseAuthClient.AuthException;
import util.Campos;
import util.Contrasena;

/**
 * Controlador de RegistroPanel.fxml. Pide nombre de usuario, email y contraseña, y la casilla de cuenta
 * offline. Una cuenta online se crea en Firebase (hace falta conexión, porque de ahí sale el uid) y una
 * cuenta offline solo en este equipo. En los dos casos se guarda en la base de datos local la huella de
 * la contraseña (nunca la contraseña).
 *
 * @author Fco Padilla
 */
public class RegistroController {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    // Longitud máxima de nombre y email, la misma que los VARCHAR(50) del esquema.
    private static final int MAX = 50;

    @FXML private HBox cajaUsuario;
    @FXML private HBox cajaEmail;
    @FXML private HBox cajaPassword;
    @FXML private TextField txtUsuario;
    @FXML private TextField txtEmail;
    @FXML private PasswordField txtPasswordOculto;
    @FXML private TextField txtPasswordVisible;
    @FXML private SVGPath iconoOjo;
    @FXML private Region tramo1;
    @FXML private Region tramo2;
    @FXML private Region tramo3;
    @FXML private Region tramo4;
    @FXML private Label lblMedidor;
    @FXML private CheckBox chkTerminos;
    @FXML private CheckBox chkSoloLocal;
    @FXML private Label lblSoloLocal;
    @FXML private Label lblError;
    @FXML private Button btnCrear;

    private final AuthService auth = new AuthService();
    private Runnable alIrALogin = () -> { };
    private Consumer<Usuario> alAcceder = u -> { };
    private Region[] tramos;

    @FXML
    private void initialize() {
        tramos = new Region[] { tramo1, tramo2, tramo3, tramo4 };
        Campos.enlazarContrasena(txtPasswordOculto, txtPasswordVisible);
        Campos.configurar(cajaUsuario, txtUsuario);
        Campos.configurar(cajaEmail, txtEmail);
        Campos.configurar(cajaPassword, txtPasswordOculto, txtPasswordVisible);
        txtPasswordOculto.textProperty().addListener((obs, antes, ahora) -> actualizarMedidor(ahora));
        chkTerminos.selectedProperty().addListener((obs, antes, ahora) -> ocultarError());
        chkSoloLocal.selectedProperty().addListener((obs, antes, ahora) -> cambiarModo(ahora));
    }

    /**
     * Cambia al panel del login
     * @param accion: cambio de panel
     */
    public void setAlIrALogin(Runnable accion) {
        this.alIrALogin = accion;
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
        Campos.marcarError(cajaUsuario, false);
        Campos.marcarError(cajaEmail, false);
        Campos.marcarError(cajaPassword, false);
    }

    /**
     * Hace que el panel sea interactuable
     */
    public void enfocar() {
        Platform.runLater(txtUsuario::requestFocus);
    }

    /**
     * Cambia al panel de login
     */
    @FXML
    private void irALogin() {
        alIrALogin.run();
    }

    /**
     * Muestra el dialog con condiciones
     */
    @FXML
    private void verCondiciones() {
        LegalController.mostrar(chkTerminos.getScene().getWindow(), false);
    }

    /**
     * Muestra el dialog con privacidad
     */
    @FXML
    private void verPrivacidad() {
        LegalController.mostrar(chkTerminos.getScene().getWindow(), true);
    }

    /**
     * Muestra o deja de mostrar la contraseña que se esté introduciendo
     */
    @FXML
    private void alternarPassword() {
        Campos.alternarContrasena(txtPasswordOculto, txtPasswordVisible, iconoOjo);
    }

    /**
     * Petición a firebase para registrar un nuevo usuario en auth
     */
    @FXML
    private void registrar() {
        ocultarError();
        String nombre = txtUsuario.getText().trim();
        String correo = txtEmail.getText().trim();
        String clave = txtPasswordOculto.getText();

        if (nombre.isEmpty() || nombre.length() > MAX) {
            Campos.marcarError(cajaUsuario, true);
            mostrarError(nombre.isEmpty() ? "Escribe un nombre de usuario."
                    : "El nombre de usuario no puede superar " + MAX + " caracteres.");
            return;
        }
        if (!EMAIL.matcher(correo).matches() || correo.length() > MAX) {
            Campos.marcarError(cajaEmail, true);
            mostrarError("Escribe un email válido (máximo " + MAX + " caracteres).");
            return;
        }
        boolean soloLocal = chkSoloLocal.isSelected();
        if (!Contrasena.cumpleMinimos(clave)) {
            Campos.marcarError(cajaPassword, true);
            mostrarError("La contraseña debe tener al menos 8 caracteres, con letras y números.");
            return;
        }
        if (!chkTerminos.isSelected()) {
            mostrarError("Para crear la cuenta tienes que aceptar las condiciones y la política de privacidad.");
            return;
        }
        if (auth.existeEmailLocal(correo)) {
            Campos.marcarError(cajaEmail, true);
            mostrarError("Ya existe una cuenta con ese email en este equipo.");
            return;
        }

        // Calcular la huella de la contraseña y hablar con Firebase tardan: se hace en segundo plano
        ocupado(true);
        Task<Usuario> tarea = new Task<>() {
            @Override
            protected Usuario call() throws Exception {
                return soloLocal ? auth.registrarSoloLocal(nombre, correo, clave)
                        : auth.registrar(nombre, correo, clave);
            }
        };
        tarea.setOnSucceeded(e -> {
            ocupado(false);
            alAcceder.accept(tarea.getValue());
        });
        tarea.setOnFailed(e -> {
            ocupado(false);
            Throwable fallo = tarea.getException();
            if (fallo instanceof AuthException a) {
                if ("EMAIL_EXISTS".equals(a.getCodigo()) || "EMAIL_EXISTS_LOCAL".equals(a.getCodigo())) {
                    Campos.marcarError(cajaEmail, true);
                }
                mostrarError(a.getMessage());
            } else if (fallo instanceof IOException) {
                mostrarError("No se ha podido conectar con Firebase. Para crear una cuenta online necesitas conexión a internet; también puedes crear una cuenta offline.");
            } else {
                mostrarError("No se pudo crear la cuenta: " + fallo);
            }
        });
        Thread hebra = new Thread(tarea, "registro");
        hebra.setDaemon(true);
        hebra.start();
    }

    /**
     * Medidor de contraseña segura
     * @param pw: contraseña que se introduce
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
     * Con "cuenta offline" aparece el aviso de que no se sincroniza y cambia el texto del botón.
     * La contraseña se pide siempre.
     * @param soloLocal: si la cuenta es offline (solo en este equipo) o no
     */
    private void cambiarModo(boolean soloLocal) {
        ocultarError();
        lblSoloLocal.setVisible(soloLocal);
        lblSoloLocal.setManaged(soloLocal);
        btnCrear.setText(textoBoton());
    }

    /**
     * @return Devuelve el texto del botón según sea una cuenta offline u online
     */
    private String textoBoton() {
        return chkSoloLocal.isSelected() ? "Crear cuenta offline" : "Crear mi cuenta";
    }

    /**
     * Deshabilita el botón si se está registrando
     * @param si: true deshabilita false habilita
     */
    private void ocupado(boolean si) {
        btnCrear.setDisable(si);
        btnCrear.setText(si ? "Creando tu cuenta…" : textoBoton());
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
