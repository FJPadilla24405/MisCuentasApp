package controllers;

import java.io.IOException;
import java.io.UncheckedIOException;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import util.TextoLegal;

/**
 * Controlador de LegalDialog.fxml: ventana con las condiciones de uso y la política de privacidad.
 * Los textos están en assets/legal/ y los datos del titular en assets/legal/legal.properties.
 * Se abre con LegalController.mostrar(ventana, false) (condiciones) o (ventana, true) (privacidad).
 *
 * @author Fco Padilla
 */
public class LegalController {

    @FXML private TabPane tabs;
    @FXML private Tab tabCondiciones;
    @FXML private Tab tabPrivacidad;
    @FXML private VBox contenidoCondiciones;
    @FXML private VBox contenidoPrivacidad;

    @FXML
    private void initialize() {
        contenidoCondiciones.getChildren().setAll(TextoLegal.cargar("/assets/legal/condiciones.txt"));
        contenidoPrivacidad.getChildren().setAll(TextoLegal.cargar("/assets/legal/privacidad.txt"));
    }

    @FXML
    private void cerrar() {
        ((Stage) tabs.getScene().getWindow()).close();
    }

    /**
     * Abre la ventana legal sobre otra ventana.
     * @param propietario: ventana desde la que se abre (queda bloqueada hasta que se cierre)
     * @param privacidad: true para abrir en la política de privacidad, false para las condiciones de uso
     */
    public static void mostrar(Window propietario, boolean privacidad) {
        try {
            FXMLLoader loader = new FXMLLoader(LegalController.class.getResource("/screens/dialogs/LegalDialog.fxml"));
            Parent raiz = loader.load();
            LegalController c = loader.getController();
            c.tabs.getSelectionModel().select(privacidad ? c.tabPrivacidad : c.tabCondiciones);

            Stage ventana = new Stage();
            ventana.initOwner(propietario);
            ventana.initModality(Modality.WINDOW_MODAL);
            ventana.setTitle("MisCuentas · Información legal");
            ventana.setMinWidth(480);
            ventana.setMinHeight(400);
            ventana.setScene(new Scene(raiz));
            ventana.show();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo abrir la ventana legal", e);
        }
    }
}
