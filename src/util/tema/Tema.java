package util.tema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javafx.collections.ListChangeListener;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Window;
import util.graphics.Paleta;

/**
 * Tema visual de la aplicación (claro, oscuro o personalizado). Los colores de application.css son variables
 * (-mc-fondo, -mc-verde...) definidas para el tema claro; los demás temas las redefinen poniendo un estilo en la
 * raíz de cada ventana, de modo que se aplican a toda la aplicación (ventanas, diálogos y avisos incluidos) sin
 * tocar los FXML. Las gráficas, que se dibujan en un lienzo, leen sus colores de Paleta.
 *
 * Con el tema claro no se pone ningún estilo: se ve exactamente lo que dice application.css.
 *
 * @author Fco Padilla
 */
public final class Tema {

    public static final String CLARO = "claro";
    public static final String OSCURO = "oscuro";
    public static final String PERSONAL = "personal";
    // Temas que se pueden elegir
    public static final List<String> TEMAS = List.of(CLARO, OSCURO, PERSONAL);

    // Claves de las propiedades que se guardan en los nodos y escenas
    private static final String CLAVE_ESTILO_BASE = "miscuentas.estiloBase";
    private static final String CLAVE_ENLAZADA = "miscuentas.temaEnlazado";

    // Estilo que se pone a la raíz de cada ventana (vacío con el tema claro)
    private static String estilo = "";
    private static String actual = CLARO;
    private static boolean iniciado;

    // No se instancia
    private Tema() {
    }

    /**
     * @param tema: tema guardado (puede ser null, o uno que ya no existe, como el antiguo "sistema")
     * @return Devuelve un tema válido: el mismo si existe y "claro" si no
     */
    public static String normalizar(String tema) {
        return tema != null && TEMAS.contains(tema.trim()) ? tema.trim() : CLARO;
    }

    /**
     * @param tema: tema guardado
     * @return Devuelve el nombre del tema para enseñarlo ("claro", "oscuro" o "personalizado")
     */
    public static String nombre(String tema) {
        return PERSONAL.equals(normalizar(tema)) ? "personalizado" : normalizar(tema);
    }

    /**
     * @return Devuelve el tema que se está enseñando
     */
    public static String getActual() {
        return actual;
    }

    /**
     * Hace que cualquier ventana que se abra, y las que ya estén abiertas, lleven el tema. Hay que llamarlo una
     * vez al arrancar, antes de mostrar la primera ventana.
     */
    public static void iniciar() {
        if (iniciado) {
            return;
        }
        iniciado = true;
        Window.getWindows().addListener((ListChangeListener<Window>) cambio -> {
            while (cambio.next()) {
                if (cambio.wasAdded()) {
                    for (Window ventana : cambio.getAddedSubList()) {
                        enlazar(ventana);
                    }
                }
            }
        });
        repintar();
    }

    /**
     * Cambia el tema de toda la aplicación
     * @param tema: "claro", "oscuro" o "personal" (cualquier otro valor se trata como "claro")
     * @param colores: colores del tema personalizado (solo se usan con "personal"; los que falten son los del claro)
     */
    public static void usar(String tema, Map<String, String> colores) {
        actual = normalizar(tema);
        switch (actual) {
            case OSCURO -> {
                Map<String, String> base = ColoresTema.baseOscura();
                estilo = ColoresTema.estilo(ColoresTema.variables(base));
                Paleta.usar(ColoresTema.grafica(base));
            }
            case PERSONAL -> {
                estilo = ColoresTema.estilo(ColoresTema.variables(colores));
                Paleta.usar(ColoresTema.grafica(colores));
            }
            default -> {
                estilo = "";
                Paleta.usar(ColoresTema.GRAFICA_CLARA);
            }
        }
        repintar();
    }

    /**
     * Vuelve al tema claro (por ejemplo, al cerrar la sesión)
     */
    public static void usarClaro() {
        usar(CLARO, null);
    }

    /**
     * Pone el estilo a todas las ventanas abiertas
     */
    private static void repintar() {
        for (Window ventana : new ArrayList<>(Window.getWindows())) {
            enlazar(ventana);
        }
    }

    /**
     * Pone el estilo a la raíz de la ventana y se queda atento por si cambia de raíz (la ventana principal
     * cambia de pantalla cambiando la raíz de su escena)
     * @param ventana: ventana a pintar
     */
    private static void enlazar(Window ventana) {
        Scene escena = ventana.getScene();
        if (escena == null) {
            return;
        }
        pintar(escena);
        if (!escena.getProperties().containsKey(CLAVE_ENLAZADA)) {
            escena.getProperties().put(CLAVE_ENLAZADA, Boolean.TRUE);
            escena.rootProperty().addListener((o, antes, ahora) -> pintar(escena));
        }
    }

    /**
     * Pone el estilo del tema a la raíz de una escena, conservando el estilo que la raíz tuviera de antes
     * @param escena: escena a pintar
     */
    private static void pintar(Scene escena) {
        Parent raiz = escena.getRoot();
        if (raiz == null) {
            return;
        }
        Object guardado = raiz.getProperties().get(CLAVE_ESTILO_BASE);
        if (guardado == null) {
            guardado = raiz.getStyle() == null ? "" : raiz.getStyle();
            raiz.getProperties().put(CLAVE_ESTILO_BASE, guardado);
        }
        String base = (String) guardado;
        String nuevo = estilo.isEmpty() ? base : (base.isEmpty() ? estilo : base + " " + estilo);
        if (!nuevo.equals(raiz.getStyle())) {
            raiz.setStyle(nuevo);
        }
    }
}
