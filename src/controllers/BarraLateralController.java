package controllers;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import models.Cuenta;
import models.Usuario;
import service.auth.SesionActual;
import service.dao.CuentaDAO;
import util.Formato;
import util.Seccion;

/**
 * Controlador de BarraLateral.fxml. Los apartados fijos están en el FXML; la lista "Tus cuentas" es
 * lo único editable: se lee de la base de datos y se vuelve a pintar cuando se añade una cuenta.
 * No sabe qué contenido se muestra: avisa de lo que se elige y MainController se encarga del resto.
 *
 * @author Fco Padilla
 */
public class BarraLateralController {

    // Trazos de los iconos de cada tipo de cuenta (cuadrícula de 24x24)
    private static final String ICONO_BANCO = "M3 21 H21 M5 21 V11 M9 21 V11 M15 21 V11 M19 21 V11 M12 3 L3 9 H21 Z";
    private static final String ICONO_EFECTIVO = "M4 7 H19 A1 1 0 0 1 20 8 V19 A1 1 0 0 1 19 20 H5 A2 2 0 0 1 3 18 V6 A2 2 0 0 1 5 4 H17 M16 14 L16.01 14";
    // 24 de la cuadrícula del icono escalados a 16 píxeles
    private static final double ESCALA_ICONO = 0.67;
    // Padding horizontal de .nav-item (12 a cada lado), para que la fila de la cuenta ocupe todo el botón
    private static final double PADDING_ITEM = 24;

    @FXML private VBox listaCuentas;
    @FXML private Button btnVistaGeneral;
    @FXML private Button btnGraficas;
    @FXML private Button btnPresupuestos;
    @FXML private Button btnPerfil;
    @FXML private Label lblIniciales;
    @FXML private Label lblNombre;

    private final CuentaDAO cuentaDAO = new CuentaDAO();
    private Consumer<Seccion> alElegirSeccion = s -> { };
    private Consumer<Cuenta> alElegirCuenta = c -> { };
    private Runnable alCerrarSesion = () -> { };
    // Botón marcado ahora mismo (apartado o cuenta) y, si es una cuenta, su id para recuperarlo al repintar la lista
    private Button activo;
    private Integer cuentaActivaId;
    // Fila de cada cuenta de la lista, por id de cuenta, para poder marcarla desde fuera
    private final Map<Integer, Button> filasCuentas = new HashMap<>();

    /**
     * Rellena el nombre del usuario y pinta sus cuentas
     */
    @FXML
    private void initialize() {
        Usuario usuario = SesionActual.getUsuario();
        if (usuario != null) {
            lblNombre.setText(usuario.getNombre());
            lblIniciales.setText(Formato.iniciales(usuario.getNombre()));
        }
        refrescarCuentas();
    }

    /**
     * Qué hacer cuando se elige un apartado fijo
     * @param accion: recibe el apartado elegido
     */
    public void setAlElegirSeccion(Consumer<Seccion> accion) {
        this.alElegirSeccion = accion;
    }

    /**
     * Qué hacer cuando se elige una cuenta de la lista
     * @param accion: recibe la cuenta elegida
     */
    public void setAlElegirCuenta(Consumer<Cuenta> accion) {
        this.alElegirCuenta = accion;
    }

    /**
     * Qué hacer cuando se pulsa cerrar sesión
     * @param accion: vuelta a la pantalla de acceso
     */
    public void setAlCerrarSesion(Runnable accion) {
        this.alCerrarSesion = accion;
    }

    /**
     * Marca un apartado en la barra y avisa de que se ha elegido
     * @param seccion: apartado a elegir
     */
    public void seleccionarSeccion(Seccion seccion) {
        cuentaActivaId = null;
        activar(botonDe(seccion));
        alElegirSeccion.accept(seccion);
    }

    /**
     * Marca una cuenta en la barra y avisa de que se ha elegido, igual que si se hubiera pulsado su fila.
     * Lo usan los paneles que enseñan cuentas (por ejemplo las tarjetas de la vista general).
     * @param cuenta: cuenta a elegir
     */
    public void seleccionarCuenta(Cuenta cuenta) {
        cuentaActivaId = cuenta.getId();
        activar(filasCuentas.get(cuenta.getId()));
        alElegirCuenta.accept(cuenta);
    }

    /**
     * Vuelve a leer las cuentas del usuario (con su saldo actual) y rehace la lista. Si había una
     * cuenta marcada, sigue marcada.
     */
    public void refrescarCuentas() {
        listaCuentas.getChildren().clear();
        filasCuentas.clear();
        Usuario usuario = SesionActual.getUsuario();
        if (usuario == null) {
            return;
        }
        for (Cuenta cuenta : cuentaDAO.listarPorUsuario(usuario.getId())) {
            Button fila = crearFilaCuenta(cuenta);
            listaCuentas.getChildren().add(fila);
            filasCuentas.put(cuenta.getId(), fila);
            if (cuentaActivaId != null && cuentaActivaId == cuenta.getId()) {
                activar(fila);
            }
        }
    }

    /**
     * Abre el apartado Vista general
     */
    @FXML
    private void irAVistaGeneral() {
        seleccionarSeccion(Seccion.VISTA_GENERAL);
    }

    /**
     * Abre el apartado Gráficas y resúmenes
     */
    @FXML
    private void irAGraficas() {
        seleccionarSeccion(Seccion.GRAFICAS);
    }

    /**
     * Abre el apartado Presupuestos
     */
    @FXML
    private void irAPresupuestos() {
        seleccionarSeccion(Seccion.PRESUPUESTOS);
    }

    /**
     * Abre el apartado Editar perfil
     */
    @FXML
    private void irAPerfil() {
        seleccionarSeccion(Seccion.PERFIL);
    }

    /**
     * Abre la ventana para crear una cuenta; si se crea, aparece en la lista y se abre.
     */
    @FXML
    private void anadirCuenta() {
        Usuario usuario = SesionActual.getUsuario();
        if (usuario == null) {
            return;
        }
        Optional<Cuenta> creada = NuevaCuentaController.mostrar(listaCuentas.getScene().getWindow(), usuario);
        creada.ifPresent(cuenta -> {
            cuentaActivaId = cuenta.getId();
            refrescarCuentas();
            alElegirCuenta.accept(cuenta);
        });
    }

    /**
     * Avisa de que se quiere cerrar sesión
     */
    @FXML
    private void cerrarSesion() {
        alCerrarSesion.run();
    }

    /**
     * Crea la fila de una cuenta: icono según el tipo, nombre y saldo actual a la derecha.
     * @param cuenta: cuenta a pintar
     * @return Devuelve el botón de la fila
     */
    private Button crearFilaCuenta(Cuenta cuenta) {
        SVGPath trazo = new SVGPath();
        trazo.setContent("efectivo".equals(cuenta.getTipo()) ? ICONO_EFECTIVO : ICONO_BANCO);
        trazo.getStyleClass().add("icono");
        trazo.setScaleX(ESCALA_ICONO);
        trazo.setScaleY(ESCALA_ICONO);

        Label nombre = new Label(cuenta.getNombre());
        nombre.getStyleClass().add("nav-cuenta-nombre");
        nombre.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(nombre, Priority.ALWAYS);

        Label saldo = new Label(Formato.moneda(cuentaDAO.calcularSaldo(cuenta.getId()), cuenta.getMoneda()));
        saldo.getStyleClass().add("nav-saldo");
        saldo.setMinWidth(Region.USE_PREF_SIZE);

        HBox contenido = new HBox(10, new Group(trazo), nombre, saldo);
        contenido.setAlignment(Pos.CENTER_LEFT);

        Button fila = new Button();
        fila.setGraphic(contenido);
        fila.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        fila.getStyleClass().add("nav-item");
        fila.setMaxWidth(Double.MAX_VALUE);
        contenido.prefWidthProperty().bind(fila.widthProperty().subtract(PADDING_ITEM));
        fila.setOnAction(e -> {
            cuentaActivaId = cuenta.getId();
            activar(fila);
            alElegirCuenta.accept(cuenta);
        });
        return fila;
    }

    /**
     * Marca un botón como el activo y desmarca el anterior
     * @param boton: botón a marcar
     */
    private void activar(Button boton) {
        if (activo != null) {
            activo.getStyleClass().remove("activo");
        }
        activo = boton;
        if (boton != null && !boton.getStyleClass().contains("activo")) {
            boton.getStyleClass().add("activo");
        }
    }

    /**
     * Botón de la barra que corresponde a un apartado
     * @param seccion: apartado
     * @return Devuelve su botón
     */
    private Button botonDe(Seccion seccion) {
        return switch (seccion) {
            case VISTA_GENERAL -> btnVistaGeneral;
            case GRAFICAS -> btnGraficas;
            case PRESUPUESTOS -> btnPresupuestos;
            case PERFIL -> btnPerfil;
        };
    }
}
