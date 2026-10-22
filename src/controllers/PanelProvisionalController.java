package controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

/**
 * Controlador de PanelProvisional.fxml: lo que se ve en un apartado que todavía no tiene su propio FXML.
 *
 * @author Fco Padilla
 */
public class PanelProvisionalController {

    @FXML private Label lblTitulo;
    @FXML private Label lblDetalle;

    /**
     * Pone el texto del panel
     * @param titulo: nombre del apartado
     * @param detalle: frase que se muestra debajo del título
     */
    public void setContenido(String titulo, String detalle) {
        lblTitulo.setText(titulo);
        lblDetalle.setText(detalle);
    }
}
