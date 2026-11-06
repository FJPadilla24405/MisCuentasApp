package graficos;

import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

/**
 * Base de las gráficas: un panel que dibuja en un lienzo del mismo tamaño que él y que enseña un aviso
 * con los datos cuando el ratón pasa por encima. Cada gráfica solo dice cómo dibujarse (dibujar) y qué
 * texto enseñar en cada punto (textoEn).
 *
 * @author Fco Padilla
 */
public abstract class LienzoGrafico extends Region {

    // Familia de letra de la aplicación; si no existe en el sistema, JavaFX usa la de por defecto
    private static final String FAMILIA = "Segoe UI";

    private final Canvas lienzo = new Canvas();
    private final Tooltip aviso = new Tooltip();

    /**
     * Crea la gráfica vacía
     */
    protected LienzoGrafico() {
        // El lienzo no manda en el tamaño del panel: es el panel quien le da el suyo
        lienzo.setManaged(false);
        getChildren().add(lienzo);
        aviso.getStyleClass().add("grafico-aviso");
        setOnMouseMoved(this::alMoverRaton);
        setOnMouseExited(e -> aviso.hide());
    }

    /**
     * Dibuja la gráfica
     * @param gc: contexto de dibujo del lienzo
     * @param ancho: ancho disponible
     * @param alto: alto disponible
     */
    protected abstract void dibujar(GraphicsContext gc, double ancho, double alto);

    /**
     * Texto que se enseña cuando el ratón está en un punto
     * @param x: posición horizontal del ratón dentro de la gráfica
     * @param y: posición vertical del ratón dentro de la gráfica
     * @return Devuelve el texto, o null si en ese punto no hay nada que enseñar
     */
    protected abstract String textoEn(double x, double y);

    /**
     * Pide que se vuelva a dibujar la gráfica (por ejemplo, porque han cambiado los datos)
     */
    protected void redibujar() {
        aviso.hide();
        requestLayout();
    }

    /**
     * Ajusta el lienzo al tamaño del panel y dibuja
     */
    @Override
    protected void layoutChildren() {
        double ancho = getWidth();
        double alto = getHeight();
        lienzo.setWidth(ancho);
        lienzo.setHeight(alto);
        GraphicsContext gc = lienzo.getGraphicsContext2D();
        gc.clearRect(0, 0, ancho, alto);
        if (ancho > 0 && alto > 0) {
            // Estado de partida igual para todas las gráficas
            gc.setTextBaseline(VPos.CENTER);
            gc.setLineWidth(1);
            dibujar(gc, ancho, alto);
        }
    }

    /**
     * Enseña, o esconde, el aviso según lo que haya bajo el ratón
     * @param e: movimiento del ratón
     */
    private void alMoverRaton(MouseEvent e) {
        String texto = textoEn(e.getX(), e.getY());
        if (texto == null) {
            aviso.hide();
            return;
        }
        aviso.setText(texto);
        aviso.show(this, e.getScreenX() + 14, e.getScreenY() + 14);
    }

    /**
     * @param tamano: tamaño de la letra
     * @param negrita: true para letra en negrita
     * @return Devuelve la fuente de las gráficas
     */
    protected static Font fuente(double tamano, boolean negrita) {
        return Font.font(FAMILIA, negrita ? FontWeight.BOLD : FontWeight.NORMAL, tamano);
    }

    /**
     * @param texto: texto a medir
     * @param fuente: fuente con la que se escribe
     * @return Devuelve lo que ocupa el texto de ancho
     */
    protected static double anchoTexto(String texto, Font fuente) {
        Text medida = new Text(texto);
        medida.setFont(fuente);
        return medida.getLayoutBounds().getWidth();
    }

    /**
     * Calcula un máximo redondo para el eje vertical, de forma que la mitad también sea un número redondo
     * (3.100 da 4.000 y la mitad es 2.000; 2.800 da 3.000 y la mitad es 1.500).
     * @param maximo: valor más alto que hay que representar
     * @return Devuelve el máximo del eje (100 si no hay nada que representar)
     */
    protected static double maximoLimpio(double maximo) {
        if (maximo <= 0) {
            return 100;
        }
        if (maximo < 10) {
            // Números pequeños: pares, para que la mitad sea entera
            return Math.ceil(maximo / 2) * 2;
        }
        double potencia = Math.pow(10, Math.floor(Math.log10(maximo)));
        double[] pasos = { 1, 1.2, 1.6, 2, 2.4, 3, 4, 5, 6, 8, 10 };
        for (double paso : pasos) {
            if (paso * potencia >= maximo - 1e-9) {
                return paso * potencia;
            }
        }
        return 10 * potencia;
    }
}
