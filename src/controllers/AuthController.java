package controllers;

import java.util.function.Consumer;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import models.Usuario;

/**
 * Controlador de AuthScreen.fxml. No tiene lógica de login, registro ni recuperación: solo intercambia
 * los tres paneles (cada uno con su propio controlador) y les pasa qué hacer cuando el usuario ya ha entrado.
 *
 * @author Fco Padilla
 */
public class AuthController {

    // Los paneles incluidos con fx:include: el nodo raíz y su controlador
    @FXML private VBox login;
    @FXML private VBox registro;
    @FXML private VBox recuperar;
    @FXML private LoginController loginController;
    @FXML private RegistroController registroController;
    @FXML private RecuperarController recuperarController;

    /** Panel que se está viendo ahora. */
    private Node actual;
    private boolean animando = false;

    @FXML
    private void initialize() {
        ocultar(registro);
        ocultar(recuperar);
        actual = login;

        loginController.setAlIrARegistro(() -> cambiar(registro, 24));
        loginController.setAlIrARecuperar(email -> {
            recuperarController.setEmail(email);
            cambiar(recuperar, 24);
        });
        registroController.setAlIrALogin(() -> cambiar(login, -24));
        recuperarController.setAlIrALogin(() -> cambiar(login, -24));

        loginController.enfocar();
    }

    /**
     * Qué hacer cuando el login o el registro han ido bien (la sesión ya está en SesionActual).
     * Lo llama quien carga esta pantalla, normalmente Main.
     */
    public void setAlAcceder(Consumer<Usuario> alAcceder) {
        loginController.setAlAcceder(alAcceder);
        registroController.setAlAcceder(alAcceder);
    }

    /** Desvanece el panel actual y hace entrar el indicado deslizándose desde el lado dado. */
    private void cambiar(Node entrante, double desdeX) {
        if (animando || entrante == actual) {
            return;
        }
        animando = true;
        Node saliente = actual;
        actual = entrante;

        FadeTransition salida = new FadeTransition(Duration.millis(140), saliente);
        salida.setToValue(0);
        salida.setOnFinished(e -> {
            ocultar(saliente);
            saliente.setOpacity(1);
            reiniciar(saliente);

            entrante.setOpacity(0);
            entrante.setTranslateX(desdeX);
            entrante.setVisible(true);
            entrante.setManaged(true);

            FadeTransition aparece = new FadeTransition(Duration.millis(200), entrante);
            aparece.setToValue(1);
            TranslateTransition desliza = new TranslateTransition(Duration.millis(200), entrante);
            desliza.setToX(0);
            ParallelTransition entrada = new ParallelTransition(aparece, desliza);
            entrada.setOnFinished(x -> {
                animando = false;
                enfocar(entrante);
            });
            entrada.play();
        });
        salida.play();
    }

    private void ocultar(Node n) {
        n.setVisible(false);
        n.setManaged(false);
    }

    private void reiniciar(Node panel) {
        if (panel == login) {
            loginController.reiniciar();
        } else if (panel == registro) {
            registroController.reiniciar();
        } else {
            recuperarController.reiniciar();
        }
    }

    private void enfocar(Node panel) {
        if (panel == login) {
            loginController.enfocar();
        } else if (panel == registro) {
            registroController.enfocar();
        } else {
            recuperarController.enfocar();
        }
    }
}
