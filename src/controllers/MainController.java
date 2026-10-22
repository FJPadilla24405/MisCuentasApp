package controllers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.layout.StackPane;
import models.Cuenta;
import util.Seccion;

/**
 * Controlador de MainScreen.fxml. La barra lateral (BarraLateralController) es siempre la misma; este
 * controlador solo cambia lo que hay en el hueco de la derecha según lo que se elija en ella.
 *
 * Para añadir el contenido de un apartado: crear su FXML en screens/sections/ con el nombre que indica
 * Seccion (por ejemplo VistaGeneral.fxml). Mientras no exista, se muestra un panel provisional.
 *
 * @author Fco Padilla
 */
public class MainController {

    @FXML private StackPane contenido;
    // Controlador de la barra lateral incluida con fx:include fx:id="barraLateral"
    @FXML private BarraLateralController barraLateralController;

    private Runnable alCerrarSesion = () -> { };

    /**
     * Conecta la barra lateral con el hueco de contenido y abre la vista general
     */
    @FXML
    private void initialize() {
        barraLateralController.setAlElegirSeccion(this::mostrarSeccion);
        barraLateralController.setAlElegirCuenta(this::mostrarCuenta);
        barraLateralController.setAlCerrarSesion(() -> alCerrarSesion.run());
        barraLateralController.seleccionarSeccion(Seccion.VISTA_GENERAL);
    }

    /**
     * Qué hacer cuando el usuario pulsa cerrar sesión. Lo llama quien carga esta pantalla, normalmente Main.
     * @param accion: vuelta a la pantalla de acceso
     */
    public void setAlCerrarSesion(Runnable accion) {
        this.alCerrarSesion = accion;
    }

    /**
     * Vuelve a leer las cuentas de la base de datos y las pinta en la barra lateral. Hay que llamarlo
     * cuando un panel cambie algo que se ve ahí (un movimiento nuevo cambia el saldo de una cuenta).
     */
    public void refrescarCuentas() {
        barraLateralController.refrescarCuentas();
    }

    /**
     * Pone un nodo en el hueco de contenido, quitando el que hubiera.
     * @param nodo: panel a mostrar
     */
    public void mostrarContenido(Node nodo) {
        contenido.getChildren().setAll(nodo);
    }

    /**
     * Muestra un apartado: carga su FXML de screens/sections/ o, si todavía no existe, el panel provisional.
     * @param seccion: apartado elegido en la barra lateral
     */
    private void mostrarSeccion(Seccion seccion) {
        URL fxml = getClass().getResource(seccion.getRutaFxml());
        if (fxml == null) {
            mostrarProvisional(seccion.getTitulo(), "Este apartado todavía no está hecho.");
            return;
        }
        try {
            Parent panel = FXMLLoader.load(fxml);
            mostrarContenido(panel);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo cargar " + seccion.getRutaFxml(), e);
        }
    }

    /**
     * Muestra una cuenta. De momento es un panel provisional con su nombre: cuando exista
     * screens/sections/Cuenta.fxml se cargará aquí y se le pasará la cuenta.
     * @param cuenta: cuenta elegida en la barra lateral
     */
    private void mostrarCuenta(Cuenta cuenta) {
        mostrarProvisional(cuenta.getNombre(), "Aquí irán los movimientos de esta cuenta.");
    }

    /**
     * Carga el panel provisional con un título y una frase
     * @param titulo: título del panel
     * @param detalle: frase bajo el título
     */
    private void mostrarProvisional(String titulo, String detalle) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/screens/sections/PanelProvisional.fxml"));
            Parent panel = loader.load();
            PanelProvisionalController controlador = loader.getController();
            controlador.setContenido(titulo, detalle);
            mostrarContenido(panel);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo cargar el panel provisional", e);
        }
    }
}
