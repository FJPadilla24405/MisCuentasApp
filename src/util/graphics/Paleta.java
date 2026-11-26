package util.graphics;

import java.util.List;

import javafx.scene.paint.Color;
import util.tema.ColoresTema;
import util.tema.ColoresTema.Grafica;

/**
 * Colores de las gráficas. Cambian con el tema de la aplicación (ver util.Tema): las gráficas se dibujan en un
 * lienzo y no pueden usar las variables de CSS. Los informes PDF son siempre claros, así que el exportador
 * pide el tema claro mientras los dibuja (soloClaro).
 *
 * @author Fco Padilla
 */
public final class Paleta {

    /**
     * Los colores de un tema, ya como Color
     */
    private record Juego(Color ingresos, Color gastos, Color textoSuave, Color titulo, Color cuadricula, Color base,
            Color otras, Color superficie, List<Color> categorias) {

        static Juego de(Grafica g) {
            return new Juego(Color.web(g.ingresos()), Color.web(g.gastos()), Color.web(g.textoSuave()),
                    Color.web(g.titulo()), Color.web(g.cuadricula()), Color.web(g.base()), Color.web(g.otras()),
                    Color.web(g.superficie()), g.categorias().stream().map(Color::web).toList());
        }
    }

    private static final Juego CLARO = Juego.de(ColoresTema.GRAFICA_CLARA);
    private static Juego actual = CLARO;
    private static boolean soloClaro;

    // No se instancia
    private Paleta() {
    }

    /**
     * Cambia los colores de las gráficas (lo hace Tema al cambiar de tema)
     * @param grafica: colores nuevos
     */
    public static void usar(Grafica grafica) {
        actual = Juego.de(grafica);
    }

    /**
     * Fuerza, o deja de forzar, los colores del tema claro (para los informes PDF, que son siempre claros)
     * @param activo: true para usar siempre los colores claros
     */
    public static void soloClaro(boolean activo) {
        soloClaro = activo;
    }

    private static Juego juego() {
        return soloClaro ? CLARO : actual;
    }

    /** @return Barras de ingresos, línea del balance acumulado y primer trozo del donut */
    public static Color ingresos() {
        return juego().ingresos();
    }

    /** @return Barras de gastos */
    public static Color gastos() {
        return juego().gastos();
    }

    /** @return Textos de los ejes */
    public static Color textoSuave() {
        return juego().textoSuave();
    }

    /** @return Texto del elemento resaltado */
    public static Color titulo() {
        return juego().titulo();
    }

    /** @return Líneas de la cuadrícula */
    public static Color cuadricula() {
        return juego().cuadricula();
    }

    /** @return Línea del cero y anillo vacío del donut */
    public static Color base() {
        return juego().base();
    }

    /** @return Trozo del donut que agrupa las categorías pequeñas */
    public static Color otras() {
        return juego().otras();
    }

    /** @return Color de las tarjetas, para rellenar los puntos de la línea */
    public static Color superficie() {
        return juego().superficie();
    }

    /**
     * @param posicion: lugar de la categoría en la lista, empezando en 0
     * @return Devuelve el color que le toca (se repiten si hay más categorías que colores)
     */
    public static Color categoria(int posicion) {
        List<Color> categorias = juego().categorias();
        return categorias.get(posicion % categorias.size());
    }
}
