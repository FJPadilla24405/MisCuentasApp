package util.graphics;

import javafx.scene.paint.Color;

/**
 * Colores de las gráficas, sacados de la maqueta (verdes de la aplicación y un azul grisáceo para variar).
 *
 * @author Fco Padilla
 */
public final class Paleta {

    // Barras de ingresos, línea del balance acumulado y primer trozo del donut
    public static final Color INGRESOS = Color.web("#287a57");
    // Barras de gastos
    public static final Color GASTOS = Color.web("#a9c7b3");
    // Textos de los ejes
    public static final Color TEXTO_SUAVE = Color.web("#6b7a72");
    // Texto del elemento resaltado
    public static final Color TITULO = Color.web("#1b3a2d");
    // Líneas de la cuadrícula
    public static final Color CUADRICULA = Color.web("#e6ebe8");
    // Línea del cero y anillo vacío del donut
    public static final Color BASE = Color.web("#d3dbd6");
    // Trozo del donut que agrupa las categorías pequeñas
    public static final Color OTRAS = Color.web("#c3cbc6");

    // Colores de las categorías del donut, de la más gastada a la menos
    private static final Color[] CATEGORIAS = {
            Color.web("#287a57"), Color.web("#a9c7b3"), Color.web("#8aa4c2"),
            Color.web("#cfe0d4"), Color.web("#6fa383"), Color.web("#d6dcd8")
    };

    // No se instancia
    private Paleta() {
    }

    /**
     * @param posicion: lugar de la categoría en la lista, empezando en 0
     * @return Devuelve el color que le toca (se repiten si hay más categorías que colores)
     */
    public static Color categoria(int posicion) {
        return CATEGORIAS[posicion % CATEGORIAS.length];
    }
}
