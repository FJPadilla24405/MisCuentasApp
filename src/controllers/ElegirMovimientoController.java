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
 * quiere añadir a mano o importar un archivo.
 *
 * @author Fco Padilla
 */
public class ElegirMovimientoController {

    /**
     * Lo que se ha elegido en la ventana.
     */
    public enum Opcion {
        // Se ha cerrado la ventana sin elegir
        NINGUNA,
        // Escribir el movimiento a mano
        MANUAL,
        // Cargar varios movimientos desde un archivo
        IMPORTAR
    }

    @FXML private Button btnManual;

    private Opcion elegida = Opcion.NINGUNA;

    /**
     * Abre la ventana y espera a que se cierre.
     * @param propietario: ventana desde la que se abre (queda bloqueada hasta que se cierre)
     * @return Devuelve la opción elegida (NINGUNA si se ha cancelado)
     */
    public static Opcion preguntar(Window propietario) {
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
            return controlador.elegida;
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo abrir la ventana de añadir movimiento", e);
        }
    }

    /**
     * Elige añadir el movimiento manualmente y cierra la ventana
     */
    @FXML
    private void elegirManual() {
        elegida = Opcion.MANUAL;
        cerrar();
    }

    /**
     * Elige importar un archivo y cierra la ventana
     */
    @FXML
    private void elegirImportar() {
        elegida = Opcion.IMPORTAR;
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
