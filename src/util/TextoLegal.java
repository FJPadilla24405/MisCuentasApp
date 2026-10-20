package util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * Convierte los textos legales (assets/legal/*.txt) en nodos de JavaFX para mostrarlos en pantalla.
 * Formato de los .txt: "# " título, "## " apartado, "- " viñeta, cualquier otra línea es un párrafo.
 * Los marcadores {{titular}}, {{correo}} y {{fecha}} se sustituyen por lo que ponga assets/legal/legal.properties.
 *
 * @author Fco Padilla
 */
public final class TextoLegal {

    private static final String DATOS = "/assets/legal/legal.properties";

    // No se instancia
    private TextoLegal() {
    }

    /**
     * Lee un texto legal del classpath.
     * @param recurso: ruta del recurso, por ejemplo "/assets/legal/condiciones.txt"
     * @return Los nodos listos para meter en un VBox
     */
    public static List<Node> cargar(String recurso) {
        List<Node> nodos = new ArrayList<>();
        Properties datos = leerDatos();
        try (InputStream in = TextoLegal.class.getResourceAsStream(recurso)) {
            if (in == null) {
                nodos.add(etiqueta("No se pudo cargar el documento (" + recurso + ").", "legal-parrafo"));
                return nodos;
            }
            BufferedReader lector = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String linea;
            while ((linea = lector.readLine()) != null) {
                linea = sustituir(linea.strip(), datos);
                if (linea.isEmpty()) {
                    continue;
                }
                if (linea.startsWith("## ")) {
                    nodos.add(etiqueta(linea.substring(3), "legal-h2"));
                } else if (linea.startsWith("# ")) {
                    nodos.add(etiqueta(linea.substring(2), "legal-titulo"));
                } else if (linea.startsWith("- ")) {
                    nodos.add(viñeta(linea.substring(2)));
                } else {
                    nodos.add(etiqueta(linea, "legal-parrafo"));
                }
            }
        } catch (IOException e) {
            nodos.add(etiqueta("No se pudo leer el documento: " + e.getMessage(), "legal-parrafo"));
        }
        return nodos;
    }

    private static Properties leerDatos() {
        Properties p = new Properties();
        try (InputStream in = TextoLegal.class.getResourceAsStream(DATOS)) {
            if (in != null) {
                p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            // Sin datos: los marcadores se quedan tal cual y se ve que falta rellenarlos
        }
        return p;
    }

    private static String sustituir(String texto, Properties datos) {
        for (String clave : new String[] { "titular", "correo", "fecha" }) {
            String valor = datos.getProperty(clave);
            if (valor != null) {
                texto = texto.replace("{{" + clave + "}}", valor.strip());
            }
        }
        return texto;
    }

    private static Label etiqueta(String texto, String clase) {
        Label l = new Label(texto);
        l.getStyleClass().add(clase);
        l.setWrapText(true);
        l.setMaxWidth(Double.MAX_VALUE);
        // Sin esto, en algunos sistemas el texto largo se corta con "..." en vez de crecer en alto
        l.setMinHeight(Region.USE_PREF_SIZE);
        return l;
    }

    private static HBox viñeta(String texto) {
        Label punto = new Label("•");
        punto.getStyleClass().add("legal-parrafo");
        Label contenido = etiqueta(texto, "legal-parrafo");
        HBox.setHgrow(contenido, Priority.ALWAYS);
        HBox fila = new HBox(8, punto, contenido);
        fila.setAlignment(Pos.TOP_LEFT);
        fila.getStyleClass().add("legal-vineta");
        return fila;
    }
}
