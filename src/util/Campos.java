package util;

import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.shape.SVGPath;

/**
 * Pequeñas ayudas para los formularios de los FXML: resaltado de la caja al enfocar, borde de error
 * y mostrar/ocultar contraseña. Los estilos están en screens/styles/application.css.
 *
 * @author Fco Padilla
 */
public final class Campos {

    // Trazos de los iconos que cambian en tiempo de ejecución (el resto están escritos en los FXML)
    public static final String OJO = "M2 12 C2 12 5.5 5 12 5 C18.5 5 22 12 22 12 C22 12 18.5 19 12 19 C5.5 19 2 12 2 12 Z "
            + "M12 9 A3 3 0 1 0 12 15 A3 3 0 1 0 12 9 Z";
    public static final String OJO_TACHADO = OJO + " M3 3 L21 21";

    // No se instancia
    private Campos() {
    }

    /**
     * Hace que la caja se resalte mientras alguno de sus campos tiene el foco y que el borde rojo
     * de error desaparezca en cuanto se escribe.
     * @param caja: HBox con la clase "campo-caja"
     * @param campos: los campos de texto que hay dentro de esa caja
     */
    public static void configurar(HBox caja, TextField... campos) {
        for (TextField t : campos) {
            t.focusedProperty().addListener((obs, antes, ahora) -> {
                caja.getStyleClass().remove("enfocado");
                if (ahora) {
                    caja.getStyleClass().add("enfocado");
                }
            });
            t.textProperty().addListener((obs, antes, ahora) -> marcarError(caja, false));
        }
    }

    /** Pone o quita el borde rojo de una caja. */
    public static void marcarError(HBox caja, boolean error) {
        caja.getStyleClass().remove("error");
        if (error) {
            caja.getStyleClass().add("error");
        }
    }

    /** Un campo de contraseña y otro de texto comparten lo escrito; solo cambia cuál de los dos se ve. */
    public static void enlazarContrasena(PasswordField oculto, TextField visible) {
        visible.textProperty().bindBidirectional(oculto.textProperty());
    }

    /** Muestra u oculta la contraseña y cambia el icono del ojo. */
    public static void alternarContrasena(PasswordField oculto, TextField visible, SVGPath iconoOjo) {
        boolean mostrar = !visible.isVisible();
        visible.setVisible(mostrar);
        visible.setManaged(mostrar);
        oculto.setVisible(!mostrar);
        oculto.setManaged(!mostrar);
        iconoOjo.setContent(mostrar ? OJO_TACHADO : OJO);
        TextField activo = mostrar ? visible : oculto;
        activo.requestFocus();
        activo.positionCaret(activo.getText().length());
    }
}
