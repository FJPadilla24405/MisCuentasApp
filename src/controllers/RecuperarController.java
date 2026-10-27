package controllers;

import java.io.IOException;
import java.util.regex.Pattern;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.util.Duration;
import service.auth.AuthService;
import service.sync.FirebaseAuthClient.AuthException;
import util.Campos;

/**
 * Controlador de RecuperarPanel.fxml. Pide a Firebase que envíe al correo un enlace para crear una
 * contraseña nueva. Hace falta conexión. La contraseña nueva se crea en la página de Firebase del
 * enlace, así que la base de datos local no cambia y el login sigue funcionando igual.
 *
 * @author Fco Padilla
 */
public class RecuperarController {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    // Segundos que hay que esperar para poder pedir otro correo (evita pulsar sin parar).
    private static final int ESPERA = 30;

    @FXML private HBox cajaEmail;
    @FXML private TextField txtEmail;
    @FXML private Label lblError;
    @FXML private Label lblOk;
    @FXML private Button btnEnviar;

    private final AuthService auth = new AuthService();
    private Runnable alIrALogin = () -> { };
    private Timeline cuentaAtras;

    @FXML
    private void initialize() {
        Campos.configurar(cajaEmail, txtEmail);
    }

    /**
     * Cambia el panel del login
     * @param accion: cambio de panel
     */
    public void setAlIrALogin(Runnable accion) {
        this.alIrALogin = accion;
    }

    /**
     * Rellena el email con el que ya se había escrito en el login (si había algo).
     * @param email: email del usuario
     */
    public void setEmail(String email) {
        if (email != null && !email.isBlank()) {
            txtEmail.setText(email.trim());
        }
    }

    /**
     * Se llama al salir de este panel: quita los mensajes y para la espera.
     */
    public void reiniciar() {
        ocultar(lblError);
        ocultar(lblOk);
        Campos.marcarError(cajaEmail, false);
        if (cuentaAtras != null) {
            cuentaAtras.stop();
        }
        btnEnviar.setDisable(false);
        btnEnviar.setText("Enviar enlace");
    }

    /**
     * Permite que el campo sea interactuable
     */
    public void enfocar() {
        Platform.runLater(txtEmail::requestFocus);
    }

    /**
     * Cambia al panel de inicio de sesión
     */
    @FXML
    private void irALogin() {
        alIrALogin.run();
    }

    /**
     * Manda la petición de recuperar cuenta con el email introducido
     */
    @FXML
    private void enviar() {
        if (btnEnviar.isDisabled()) {
            return;
        }
        ocultar(lblError);
        ocultar(lblOk);
        String correo = txtEmail.getText().trim();
        if (!EMAIL.matcher(correo).matches()) {
            Campos.marcarError(cajaEmail, true);
            mostrar(lblError, "Escribe un email válido.");
            return;
        }

        btnEnviar.setDisable(true);
        btnEnviar.setText("Enviando…");
        Task<Void> tarea = new Task<>() {
            @Override
            protected Void call() throws Exception {
                auth.recuperarContrasena(correo);
                return null;
            }
        };
        tarea.setOnSucceeded(e -> {
            // Mismo mensaje exista o no la cuenta, para no revelar qué correos están registrados
            mostrar(lblOk, "Si existe una cuenta con ese email, te hemos enviado un correo con un enlace para "
                    + "crear una contraseña nueva. Revisa también la carpeta de spam.");
            iniciarEspera();
        });
        tarea.setOnFailed(e -> {
            btnEnviar.setDisable(false);
            btnEnviar.setText("Enviar enlace");
            Throwable fallo = tarea.getException();
            if (fallo instanceof AuthException a) {
                mostrar(lblError, a.getMessage());
            } else if (fallo instanceof IOException) {
                mostrar(lblError, "No se ha podido conectar con Firebase. Para recuperar la contraseña necesitas conexión a internet.");
            } else {
                mostrar(lblError, "No se pudo enviar el correo: " + fallo);
            }
        });
        Thread hebra = new Thread(tarea, "recuperar-firebase");
        hebra.setDaemon(true);
        hebra.start();
    }

    /**
     * Deja el botón bloqueado ESPERA segundos, con la cuenta atrás en el texto.
     */
    private void iniciarEspera() {
        int[] restantes = { ESPERA };
        btnEnviar.setText("Reenviar en " + restantes[0] + " s");
        cuentaAtras = new Timeline(new KeyFrame(Duration.seconds(1), ev -> {
            restantes[0]--;
            if (restantes[0] <= 0) {
                btnEnviar.setDisable(false);
                btnEnviar.setText("Reenviar enlace");
            } else {
                btnEnviar.setText("Reenviar en " + restantes[0] + " s");
            }
        }));
        cuentaAtras.setCycleCount(ESPERA);
        cuentaAtras.play();
    }

    /**
     * Pone visible un texto
     * @param etiqueta: etiqueta a poner visible
     * @param texto: texto del label
     */
    private void mostrar(Label etiqueta, String texto) {
        etiqueta.setText(texto);
        etiqueta.setVisible(true);
        etiqueta.setManaged(true);
    }

    /**
     * Oculta un texto
     * @param etiqueta: etiqueta que se oculta ocultosamente
     */
    private void ocultar(Label etiqueta) {
        etiqueta.setText("");
        etiqueta.setVisible(false);
        etiqueta.setManaged(false);
    }
}
