package graficos;

import java.math.BigDecimal;
import java.util.List;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.geometry.VPos;
import util.Formato;

/**
 * Gráfica de barras por parejas: para cada etiqueta (semana o mes) una barra de ingresos y otra de gastos.
 * Tiene tres líneas de cuadrícula (0, la mitad y el máximo) con su importe a la izquierda.
 *
 * @author Fco Padilla
 */
public class GraficoBarras extends LienzoGrafico {

    private static final Font FUENTE_EJE = fuente(11, false);
    private static final Font FUENTE_EJE_NEGRITA = fuente(11, true);
    // Espacio entre el importe del eje y el borde izquierdo de la cuadrícula
    private static final double SEPARACION_EJE = 12;
    // Alto reservado bajo la cuadrícula para las etiquetas
    private static final double ALTO_ETIQUETAS = 26;
    // Altura mínima de una barra con importe, para que no desaparezca
    private static final double ALTO_MINIMO = 2;

    // Zona donde se dibujan las barras
    private record Plano(double izquierda, double derecha, double arriba, double abajo, double techo) {
    }

    private List<String> etiquetas = List.of();
    private List<BigDecimal> ingresos = List.of();
    private List<BigDecimal> gastos = List.of();
    private int resaltado = -1;
    private String moneda = "EUR";

    /**
     * Crea la gráfica vacía
     */
    public GraficoBarras() {
        setMinHeight(200);
        setPrefHeight(240);
    }

    /**
     * Pone los datos y vuelve a dibujar
     * @param etiquetas: texto de cada pareja de barras
     * @param ingresos: importe de ingresos de cada pareja
     * @param gastos: importe de gastos de cada pareja
     * @param resaltado: posición de la etiqueta que se escribe en negrita (el mes actual), o -1 si ninguna
     * @param moneda: código de moneda de tres letras
     */
    public void setDatos(List<String> etiquetas, List<BigDecimal> ingresos, List<BigDecimal> gastos, int resaltado, String moneda) {
        this.etiquetas = etiquetas;
        this.ingresos = ingresos;
        this.gastos = gastos;
        this.resaltado = resaltado;
        this.moneda = moneda;
        redibujar();
    }

    /**
     * @return Devuelve el importe más alto de todas las barras
     */
    private double maximo() {
        double maximo = 0;
        for (BigDecimal v : ingresos) {
            maximo = Math.max(maximo, v.doubleValue());
        }
        for (BigDecimal v : gastos) {
            maximo = Math.max(maximo, v.doubleValue());
        }
        return maximo;
    }

    /**
     * Calcula dónde van la cuadrícula y las barras
     * @param ancho: ancho del panel
     * @param alto: alto del panel
     * @return Devuelve la zona de dibujo
     */
    private Plano plano(double ancho, double alto) {
        double techo = maximoLimpio(maximo());
        double anchoEje = Math.max(anchoTexto(Formato.monedaCorta(BigDecimal.valueOf(techo), moneda), FUENTE_EJE),
                anchoTexto(Formato.monedaCorta(BigDecimal.ZERO, moneda), FUENTE_EJE));
        return new Plano(anchoEje + SEPARACION_EJE, ancho - 4, 8, Math.max(alto - ALTO_ETIQUETAS, 20), techo);
    }

    @Override
    protected void dibujar(GraphicsContext gc, double ancho, double alto) {
        Plano p = plano(ancho, alto);

        // Cuadrícula con el importe de cada línea
        gc.setFont(FUENTE_EJE);
        gc.setTextAlign(TextAlignment.RIGHT);
        gc.setTextBaseline(VPos.CENTER);
        for (int i = 0; i <= 2; i++) {
            double y = p.abajo() - (p.abajo() - p.arriba()) * i / 2;
            double linea = Math.round(y) + 0.5;
            gc.setStroke(i == 0 ? Paleta.BASE : Paleta.CUADRICULA);
            gc.setLineWidth(1);
            gc.strokeLine(p.izquierda(), linea, p.derecha(), linea);
            gc.setFill(Paleta.TEXTO_SUAVE);
            gc.fillText(Formato.monedaCorta(BigDecimal.valueOf(p.techo() * i / 2), moneda), p.izquierda() - SEPARACION_EJE + 2, y);
        }

        int n = etiquetas.size();
        if (n == 0) {
            return;
        }
        double anchoGrupo = (p.derecha() - p.izquierda()) / n;
        double anchoBarra = Math.max(5, Math.min(24, anchoGrupo * 0.24));
        double hueco = Math.min(3, anchoBarra * 0.25);

        for (int i = 0; i < n; i++) {
            double centro = p.izquierda() + anchoGrupo * (i + 0.5);
            barra(gc, centro - hueco / 2 - anchoBarra, anchoBarra, ingresos.get(i), Paleta.INGRESOS, p);
            barra(gc, centro + hueco / 2, anchoBarra, gastos.get(i), Paleta.GASTOS, p);

            gc.setTextAlign(TextAlignment.CENTER);
            gc.setTextBaseline(VPos.TOP);
            gc.setFont(i == resaltado ? FUENTE_EJE_NEGRITA : FUENTE_EJE);
            gc.setFill(i == resaltado ? Paleta.TITULO : Paleta.TEXTO_SUAVE);
            gc.fillText(etiquetas.get(i), centro, p.abajo() + 8);
        }
    }

    /**
     * Dibuja una barra con las esquinas de arriba redondeadas
     * @param gc: contexto de dibujo
     * @param x: borde izquierdo de la barra
     * @param ancho: ancho de la barra
     * @param valor: importe que representa
     * @param color: color de relleno
     * @param p: zona de dibujo
     */
    private void barra(GraphicsContext gc, double x, double ancho, BigDecimal valor, Color color, Plano p) {
        if (valor.signum() <= 0) {
            return;
        }
        double alto = (p.abajo() - p.arriba()) * valor.doubleValue() / p.techo();
        alto = Math.max(alto, ALTO_MINIMO);
        double y = p.abajo() - alto;
        double radio = Math.min(4, ancho / 2);
        gc.setFill(color);
        gc.fillRoundRect(x, y, ancho, alto, radio * 2, radio * 2);
        if (alto > radio) {
            // Cuadra las esquinas de abajo, que tienen que quedar pegadas a la línea del cero
            gc.fillRect(x, y + alto - radio, ancho, radio);
        }
    }

    @Override
    protected String textoEn(double x, double y) {
        int n = etiquetas.size();
        if (n == 0) {
            return null;
        }
        Plano p = plano(getWidth(), getHeight());
        if (x < p.izquierda() || x > p.derecha()) {
            return null;
        }
        int i = (int) ((x - p.izquierda()) / ((p.derecha() - p.izquierda()) / n));
        if (i < 0 || i >= n) {
            return null;
        }
        return etiquetas.get(i) + "\nIngresos: " + Formato.moneda(ingresos.get(i), moneda)
                + "\nGastos: " + Formato.moneda(gastos.get(i), moneda);
    }
}
