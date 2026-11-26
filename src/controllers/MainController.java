package controllers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.layout.StackPane;
import javafx.stage.Window;
import javafx.util.Duration;
import models.Cuenta;
import models.Usuario;
import service.auth.SesionActual;
import service.movimiento.RecurrenteService;
import service.perfil.TemaService;
import util.Seccion;

/**
 * Controlador de MainScreen.fxml. La barra lateral (BarraLateralController) es siempre la misma; este
 * controlador solo cambia lo que hay en el hueco de la derecha según lo que se elija en ella.
 *
 * Para añadir el contenido de un apartado: crear su FXML en screens/sections/ con el nombre que indica
 * Seccion (por ejemplo VistaGeneral.fxml). Mientras no exista, se muestra un panel provisional.
 *
 * @author Fco Padilla
 */
public class MainController {

    @FXML private StackPane contenido;
    // Controlador de la barra lateral incluida con fx:include fx:id="barraLateral"
    @FXML private BarraLateralController barraLateralController;

    // Cada cuánto se mira si algún movimiento recurrente ya toca (por si la aplicación se queda abierta de un día para otro)
    private static final Duration REVISION_RECURRENTES = Duration.minutes(15);

    private final RecurrenteService recurrentes = new RecurrenteService();
    private Timeline revisionRecurrentes;
    private Runnable alCerrarSesion = () -> { };

    /**
     * Conecta la barra lateral con el hueco de contenido y abre la vista general
     */
    @FXML
    private void initialize() {
        barraLateralController.setAlElegirSeccion(this::mostrarSeccion);
        barraLateralController.setAlElegirCuenta(this::mostrarResumen);
        barraLateralController.setAlCerrarSesion(() -> alCerrarSesion.run());
        // Tras descargar datos de Firebase se vuelve a abrir lo que se estaba viendo y se rehacen nombre, iniciales y cuentas
        barraLateralController.setAlDescargar(() -> {
            // La descarga puede haber cambiado el tema elegido
            TemaService.aplicar(SesionActual.getUsuario());
            barraLateralController.recargarActivo();
        });
        // Los movimientos recurrentes que ya tocan se añaden antes de pintar nada, y después se revisan cada cierto tiempo
        if (generarRecurrentes() > 0) {
            barraLateralController.refrescarCuentas();
        }
        barraLateralController.seleccionarSeccion(Seccion.VISTA_GENERAL);
        revisionRecurrentes = new Timeline(new KeyFrame(REVISION_RECURRENTES, e -> revisarRecurrentes()));
        revisionRecurrentes.setCycleCount(Animation.INDEFINITE);
        revisionRecurrentes.play();
    }

    /**
     * Añade los movimientos recurrentes que ya tocan hasta hoy. Si falla, no impide usar la aplicación.
     * @return Devuelve cuántos movimientos se han añadido
     */
    private int generarRecurrentes() {
        Usuario usuario = SesionActual.getUsuario();
        if (usuario == null) {
            return 0;
        }
        try {
            return recurrentes.generarPendientes(usuario);
        } catch (RuntimeException e) {
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * Revisión periódica de los movimientos recurrentes. Si se han añadido movimientos, se vuelve a abrir lo que se
     * estaba viendo para que aparezcan y se actualizan los saldos. No hace nada mientras haya una ventana abierta
     * encima (por ejemplo, añadiendo un movimiento): se queda para la próxima revisión. Se detiene sola cuando se
     * cierra la sesión.
     */
    private void revisarRecurrentes() {
        if (SesionActual.getUsuario() == null || contenido.getScene() == null) {
            revisionRecurrentes.stop();
            return;
        }
        long ventanasAbiertas = Window.getWindows().stream().filter(Window::isShowing).count();
        if (ventanasAbiertas > 1) {
            return;
        }
        if (generarRecurrentes() > 0) {
            barraLateralController.recargarActivo();
        }
    }

    /**
     * Qué hacer cuando el usuario pulsa cerrar sesión. Lo llama quien carga esta pantalla, normalmente Main.
     * @param accion: vuelta a la pantalla de acceso
     */
    public void setAlCerrarSesion(Runnable accion) {
        this.alCerrarSesion = accion;
    }

    /**
     * Vuelve a leer las cuentas de la base de datos y las pinta en la barra lateral. Hay que llamarlo
     * cuando un panel cambie algo que se ve ahí (un movimiento nuevo cambia el saldo de una cuenta).
     */
    public void refrescarCuentas() {
        barraLateralController.refrescarCuentas();
    }

    /**
     * Pone un nodo en el hueco de contenido, quitando el que hubiera.
     * @param nodo: panel a mostrar
     */
    public void mostrarContenido(Node nodo) {
        contenido.getChildren().setAll(nodo);
    }

    /**
     * Muestra un apartado: carga su FXML de screens/sections/ o, si todavía no existe, el panel provisional.
     * @param seccion: apartado elegido en la barra lateral
     */
    private void mostrarSeccion(Seccion seccion) {
        if (seccion == Seccion.VISTA_GENERAL) {
            mostrarResumen(null);
            return;
        }
        if (seccion == Seccion.GRAFICAS) {
            mostrarGraficas();
            return;
        }
        if (seccion == Seccion.PERFIL) {
            mostrarPerfil();
            return;
        }
        URL fxml = getClass().getResource(seccion.getRutaFxml());
        if (fxml == null) {
            mostrarProvisional(seccion.getTitulo(), "Este apartado todavía no está hecho.");
            return;
        }
        try {
            Parent panel = FXMLLoader.load(fxml);
            mostrarContenido(panel);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo cargar " + seccion.getRutaFxml(), e);
        }
    }

    /**
     * Carga la pantalla de editar perfil. Cuando se guarda algo que se ve en la barra lateral o en la barra
     * superior (nombre, moneda, cuentas), se actualizan.
     */
    private void mostrarPerfil() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/screens/sections/Perfil.fxml"));
            Parent panel = loader.load();
            PerfilController perfil = loader.getController();
            perfil.setAlCambiar(this::alCambiarPerfil);
            mostrarContenido(panel);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo cargar el perfil", e);
        }
    }

    /**
     * Actualiza lo que se ve fuera de la pantalla de perfil: nombre e iniciales, y las cuentas con sus saldos
     */
    private void alCambiarPerfil() {
        barraLateralController.refrescarUsuario();
        barraLateralController.refrescarCuentas();
    }



    /**
     * Carga la pantalla de resumen (screens/sections/Resumen.fxml). Es una sola pantalla para la vista
     * general y para cada cuenta: solo cambian los datos que se le pasan.
     * @param cuenta: cuenta a resumir, o null para la vista general (todas las cuentas)
     */
    private void mostrarResumen(Cuenta cuenta) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/screens/sections/Resumen.fxml"));
            Parent panel = loader.load();
            ResumenController resumen = loader.getController();
            // Pulsar la tarjeta de una cuenta equivale a pulsarla en la barra lateral
            resumen.setAlElegirCuenta(barraLateralController::seleccionarCuenta);
            resumen.setAlCambiar(this::refrescarCuentas);
            if (cuenta == null) {
                resumen.mostrarTodas();
            } else {
                resumen.mostrarCuenta(cuenta);
            }
            mostrarContenido(panel);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo cargar el resumen", e);
        }
    }

    /**
     * Carga el apartado Gráficas y resúmenes (screens/sections/Graficas.fxml). Su vista mensual y su vista anual
     * escriben en la barra superior de dónde estamos, y la anual también la moneda y el tema.
     */
    private void mostrarGraficas() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/screens/sections/Graficas.fxml"));
            Parent panel = loader.load();
            GraficasController graficas = loader.getController();
            graficas.iniciar();
            mostrarContenido(panel);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo cargar las gráficas", e);
        }
    }

    /**
     * Carga el panel provisional con un título y una frase
     * @param titulo: título del panel
     * @param detalle: frase bajo el título
     */
    private void mostrarProvisional(String titulo, String detalle) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/screens/sections/PanelProvisional.fxml"));
            Parent panel = loader.load();
            PanelProvisionalController controlador = loader.getController();
            controlador.setContenido(titulo, detalle);
            mostrarContenido(panel);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo cargar el panel provisional", e);
        }
    }
}
