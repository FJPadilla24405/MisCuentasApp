package graficos;

import java.math.BigDecimal;
import java.util.List;

import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import util.Formato;

/**
 * Gráfica de anillo: un trozo por categoría y, en el centro, el total con su etiqueta. Si no hay datos
 * se pinta el anillo vacío.
 *
 * @author Fco Padilla
 */
public class GraficoDonut extends LienzoGrafico {

    // Grosor del anillo respecto al radio exterior
    private static final double GROSOR = 0.24;
    // Separación entre trozos, en grados
    private static final double HUECO = 1.5;
    private static final Font FUENTE_ETIQUETA = fuente(9, false);

    private List<String> nombres = List.of();
    private List<BigDecimal> valores = List.of();
    private List<Color> colores = List.of();
    private String centro = "";
    private String etiquetaCentro = "";
    private String moneda = "EUR";

    /**
     * Crea la gráfica vacía
     */
    public GraficoDonut() {
        setMinSize(130, 130);
        setPrefSize(150, 150);
    }

    /**
     * Pone los datos y vuelve a dibujar
     * @param nombres: nombre de cada trozo
     * @param valores: importe de cada trozo
     * @param colores: color de cada trozo
     * @param centro: texto grande del centro (el total)
     * @param etiquetaCentro: texto pequeño bajo el total
     * @param moneda: código de moneda de tres letras
     */
    public void setDatos(List<String> nombres, List<BigDecimal> valores, List<Color> colores, String centro,
            String etiquetaCentro, String moneda) {
        this.nombres = nombres;
        this.valores = valores;
        this.colores = colores;
        this.centro = centro;
        this.etiquetaCentro = etiquetaCentro;
        this.moneda = moneda;
        redibujar();
    }

    /**
     * @return Devuelve la suma de todos los trozos
     */
    private double total() {
        double total = 0;
        for (BigDecimal v : valores) {
            total += v.doubleValue();
        }
        return total;
    }

    @Override
    protected void dibujar(GraphicsContext gc, double ancho, double alto) {
        double cx = ancho / 2;
        double cy = alto / 2;
        double exterior = Math.min(ancho, alto) / 2 - 2;
        double grosor = exterior * GROSOR;
        double radio = exterior - grosor / 2;
        double total = total();

        gc.setLineWidth(grosor);
        gc.setLineCap(StrokeLineCap.BUTT);
        if (total <= 0) {
            gc.setStroke(Paleta.CUADRICULA);
            gc.strokeOval(cx - radio, cy - radio, radio * 2, radio * 2);
        } else {
            double inicio = 90;
            double hueco = valores.size() > 1 ? HUECO : 0;
            for (int i = 0; i < valores.size(); i++) {
                double barrido = 360 * valores.get(i).doubleValue() / total;
                if (barrido - hueco > 0.2) {
                    gc.setStroke(colores.get(i));
                    // En JavaFX los ángulos crecen al revés que un reloj: el barrido negativo avanza como las agujas
                    gc.strokeArc(cx - radio, cy - radio, radio * 2, radio * 2, inicio - hueco / 2, -(barrido - hueco), ArcType.OPEN);
                }
                inicio -= barrido;
            }
        }
        gc.setLineWidth(1);

        // Total en el centro, con una letra que se achica hasta que cabe dentro del anillo
        double disponible = (radio - grosor / 2) * 2 * 0.85;
        double tamano = 20;
        Font grande = fuente(tamano, true);
        while (tamano > 10 && anchoTexto(centro, grande) > disponible) {
            tamano -= 1;
            grande = fuente(tamano, true);
        }
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        gc.setFill(Paleta.TITULO);
        gc.setFont(grande);
        gc.fillText(centro, cx, etiquetaCentro.isEmpty() ? cy : cy - 6);
        if (!etiquetaCentro.isEmpty()) {
            gc.setFill(Paleta.TEXTO_SUAVE);
            gc.setFont(FUENTE_ETIQUETA);
            gc.fillText(etiquetaCentro, cx, cy + 12);
        }
    }

    @Override
    protected String textoEn(double x, double y) {
        double total = total();
        if (total <= 0) {
            return null;
        }
        double cx = getWidth() / 2;
        double cy = getHeight() / 2;
        double exterior = Math.min(getWidth(), getHeight()) / 2 - 2;
        double distancia = Math.hypot(x - cx, y - cy);
        if (distancia > exterior || distancia < exterior * (1 - GROSOR)) {
            return null;
        }
        // Ángulo medido desde arriba y en el sentido de las agujas, de 0 a 360
        double angulo = Math.toDegrees(Math.atan2(x - cx, -(y - cy)));
        if (angulo < 0) {
            angulo += 360;
        }
        double acumulado = 0;
        for (int i = 0; i < valores.size(); i++) {
            acumulado += 360 * valores.get(i).doubleValue() / total;
            if (angulo <= acumulado) {
                BigDecimal porcentaje = BigDecimal.valueOf(valores.get(i).doubleValue() * 100 / total);
                return nombres.get(i) + "\n" + Formato.moneda(valores.get(i), moneda) + " · " + Formato.porcentajeCorto(porcentaje);
            }
        }
        return null;
    }
}
