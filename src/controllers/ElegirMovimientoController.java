package controllers;

import java.io.IOException;
import java.io.UncheckedIOException;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Controlador de ElegirMovimientoDialog.fxml: primera ventana de "Añadir movimiento". Pregunta si se
 * quiere añadir a mano o importar un archivo. Importar todavía no está hecho: su opción sale desactivada.
 *
 * @author Fco Padilla
 */
public class ElegirMovimientoController {

    @FXML private Button btnManual;

    // true si se ha elegido añadir manualmente
    private boolean manual = false;

    /**
     * Abre la ventana y espera a que se cierre.
     * @param propietario: ventana desde la que se abre (queda bloqueada hasta que se cierre)
     * @return Devuelve true si se ha elegido añadir manualmente, false si se ha cancelado
     */
    public static boolean preguntar(Window propietario) {
        try {
            FXMLLoader loader = new FXMLLoader(ElegirMovimientoController.class.getResource("/screens/dialogs/ElegirMovimientoDialog.fxml"));
            Parent raiz = loader.load();
            ElegirMovimientoController controlador = loader.getController();

            Stage ventana = new Stage();
            ventana.initOwner(propietario);
            ventana.initModality(Modality.WINDOW_MODAL);
            ventana.setTitle("MisCuentas · Añadir movimiento");
            ventana.setResizable(false);
            ventana.setScene(new Scene(raiz));
            ventana.showAndWait();
            return controlador.manual;
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo abrir la ventana de añadir movimiento", e);
        }
    }

    /**
     * Elige añadir el movimiento manualmente y cierra la ventana
     */
    @FXML
    private void elegirManual() {
        manual = true;
        cerrar();
    }

    /**
     * Cierra la ventana sin elegir nada
     */
    @FXML
    private void cancelar() {
        cerrar();
    }

    /**
     * Cierra la ventana
     */
    private void cerrar() {
        ((Stage) btnManual.getScene().getWindow()).close();
    }
}
