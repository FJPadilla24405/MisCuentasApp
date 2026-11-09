package util.graphics;

import java.math.BigDecimal;
import java.util.List;

import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import util.Formato;

/**
 * Gráfica de una línea (el balance acumulado mes a mes). No lleva importes en los ejes: solo la línea del
 * cero, la primera y la última etiqueta y un punto en el último valor. Al pasar el ratón enseña cada mes.
 *
 * @author Fco Padilla
 */
public class GraficoLinea extends LienzoGrafico {

    private static final Font FUENTE_EJE = fuente(11, false);
    // Márgenes de la zona de dibujo: dejan sitio al punto final y a las etiquetas
    private static final double MARGEN_LATERAL = 10;
    private static final double MARGEN_SUPERIOR = 12;
    private static final double ALTO_ETIQUETAS = 28;

    private List<String> etiquetas = List.of();
    private List<BigDecimal> valores = List.of();
    private String moneda = "EUR";

    /**
     * Crea la gráfica vacía
     */
    public GraficoLinea() {
        setMinHeight(110);
        setPrefHeight(150);
    }

    /**
     * Pone los datos y vuelve a dibujar
     * @param etiquetas: etiqueta de cada punto (el mes)
     * @param valores: valor de cada punto
     * @param moneda: código de moneda de tres letras
     */
    public void setDatos(List<String> etiquetas, List<BigDecimal> valores, String moneda) {
        this.etiquetas = etiquetas;
        this.valores = valores;
        this.moneda = moneda;
        redibujar();
    }

    /**
     * @return Devuelve el valor más bajo, o 0 si es mayor
     */
    private double minimo() {
        double minimo = 0;
        for (BigDecimal v : valores) {
            minimo = Math.min(minimo, v.doubleValue());
        }
        return minimo;
    }

    /**
     * @return Devuelve el valor más alto, o 0 si es menor
     */
    private double maximo() {
        double maximo = 0;
        for (BigDecimal v : valores) {
            maximo = Math.max(maximo, v.doubleValue());
        }
        return maximo;
    }

    /**
     * @param i: posición del punto
     * @param ancho: ancho del panel
     * @return Devuelve su posición horizontal
     */
    private double posicionX(int i, double ancho) {
        int n = valores.size();
        double izquierda = MARGEN_LATERAL;
        double derecha = ancho - MARGEN_LATERAL;
        return n == 1 ? (izquierda + derecha) / 2 : izquierda + (derecha - izquierda) * i / (n - 1);
    }

    /**
     * @param valor: valor a representar
     * @param alto: alto del panel
     * @return Devuelve su posición vertical
     */
    private double posicionY(double valor, double alto) {
        double minimo = minimo();
        double maximo = maximo();
        double rango = maximo - minimo;
        double arriba = MARGEN_SUPERIOR;
        double abajo = Math.max(alto - ALTO_ETIQUETAS, arriba + 10);
        if (rango <= 0) {
            return abajo;
        }
        return abajo - (abajo - arriba) * (valor - minimo) / rango;
    }

    @Override
    protected void dibujar(GraphicsContext gc, double ancho, double alto) {
        int n = valores.size();
        if (n == 0) {
            return;
        }
        // Línea del cero
        double yCero = Math.round(posicionY(0, alto)) + 0.5;
        gc.setStroke(Paleta.BASE);
        gc.setLineWidth(1);
        gc.strokeLine(MARGEN_LATERAL, yCero, ancho - MARGEN_LATERAL, yCero);

        // Línea del balance
        gc.setStroke(Paleta.INGRESOS);
        gc.setLineWidth(2.5);
        gc.setLineCap(StrokeLineCap.ROUND);
        gc.setLineJoin(StrokeLineJoin.ROUND);
        gc.beginPath();
        for (int i = 0; i < n; i++) {
            double x = posicionX(i, ancho);
            double y = posicionY(valores.get(i).doubleValue(), alto);
            if (i == 0) {
                gc.moveTo(x, y);
            } else {
                gc.lineTo(x, y);
            }
        }
        gc.stroke();

        // Punto en el último valor
        double xFinal = posicionX(n - 1, ancho);
        double yFinal = posicionY(valores.get(n - 1).doubleValue(), alto);
        gc.setFill(javafx.scene.paint.Color.WHITE);
        gc.fillOval(xFinal - 4.5, yFinal - 4.5, 9, 9);
        gc.setStroke(Paleta.INGRESOS);
        gc.setLineWidth(2.5);
        gc.strokeOval(xFinal - 4.5, yFinal - 4.5, 9, 9);

        // Primera y última etiqueta
        double yEtiqueta = Math.max(alto - ALTO_ETIQUETAS, MARGEN_SUPERIOR + 10) + 12;
        gc.setFont(FUENTE_EJE);
        gc.setFill(Paleta.TEXTO_SUAVE);
        gc.setTextBaseline(VPos.TOP);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.fillText(etiquetas.get(0), 2, yEtiqueta);
        if (n > 1) {
            gc.setTextAlign(TextAlignment.RIGHT);
            gc.fillText(etiquetas.get(n - 1), ancho - 2, yEtiqueta);
        }
    }

    @Override
    protected String textoEn(double x, double y) {
        int n = valores.size();
        if (n == 0) {
            return null;
        }
        // Punto más cercano en horizontal
        int mejor = 0;
        double distancia = Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            double d = Math.abs(posicionX(i, getWidth()) - x);
            if (d < distancia) {
                distancia = d;
                mejor = i;
            }
        }
        return etiquetas.get(mejor) + ": " + Formato.moneda(valores.get(mejor), moneda);
    }
}
