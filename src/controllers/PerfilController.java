package controllers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Group;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.util.StringConverter;
import models.Cuenta;
import models.Usuario;
import service.auth.SesionActual;
import service.perfil.PerfilService;
import service.perfil.PerfilService.DatoInvalido;
import service.resumen.ResumenService.FilaCuenta;
import util.Campos;
import util.Formato;

/**
 * Controlador de Perfil.fxml: datos personales, cambio de contraseña, preferencias (moneda y tema) y
 * gestión de las cuentas (añadir, renombrar, activar y desactivar). La lógica está en PerfilService.
 * El tema oscuro y el del sistema solo se guardan: todavía no cambian el aspecto de la aplicación.
 *
 * @author Fco Padilla
 */
public class PerfilController {

    // Trazos de los iconos que se crean en código (cuadrícula de 24x24)
    private static final String ICONO_BANCO = "M3 21 H21 M5 21 V11 M9 21 V11 M15 21 V11 M19 21 V11 M12 3 L3 9 H21 Z";
    private static final String ICONO_EFECTIVO = "M4 7 H19 A1 1 0 0 1 20 8 V19 A1 1 0 0 1 19 20 H5 A2 2 0 0 1 3 18 V6 A2 2 0 0 1 5 4 H17 M16 14 L16.01 14";
    // 24 de la cuadrícula del icono escalados a 16 píxeles
    private static final double ESCALA_ICONO = 0.67;
    // Monedas que se pueden elegir
    private static final List<String> MONEDAS = List.of("EUR", "USD", "GBP", "CHF", "MXN", "ARS", "COP", "CLP", "PEN", "BRL", "JPY");

    @FXML private ScrollPane raiz;
    @FXML private Label lblAvatar;
    @FXML private Label lblTipoCuenta;
    @FXML private HBox cajaNombre;
    @FXML private HBox cajaEmail;
    @FXML private TextField txtNombre;
    @FXML private TextField txtEmail;
    @FXML private Label lblEmailNota;
    @FXML private Label lblPasswordNota;
    @FXML private ComboBox<String> cmbMoneda;
    @FXML private VBox cardClaro;
    @FXML private VBox cardOscuro;
    @FXML private VBox cardSistema;
    @FXML private Group checkClaro;
    @FXML private Group checkOscuro;
    @FXML private Group checkSistema;
    @FXML private Label lblError;
    @FXML private Label lblOk;
    @FXML private Label lblActivas;
    @FXML private VBox listaCuentas;

    private final PerfilService servicio = new PerfilService();
    private Runnable alCambiar = () -> { };
    private Usuario usuario;
    // Tema elegido en pantalla (todavía sin guardar)
    private String tema = "claro";

    /**
     * Prepara el selector de moneda, quita el borde rojo al escribir y carga los datos del usuario
     */
    @FXML
    private void initialize() {
        cmbMoneda.setConverter(new StringConverter<>() {
            @Override
            public String toString(String codigo) {
                return codigo == null ? "" : descripcionMoneda(codigo);
            }

            @Override
            public String fromString(String texto) {
                return texto;
            }
        });
        Campos.configurar(cajaNombre, txtNombre);
        Campos.configurar(cajaEmail, txtEmail);
        cargar();
    }

    /**
     * Qué hacer cuando se guarda algo que se ve fuera de esta pantalla (nombre, moneda o cuentas), para
     * que la barra lateral y la barra superior se actualicen. Lo llama quien carga esta pantalla.
     * @param accion: refresco de la barra lateral y la barra superior
     */
    public void setAlCambiar(Runnable accion) {
        this.alCambiar = accion;
    }

    /**
     * Abre la ventana para cambiar la contraseña
     */
    @FXML
    private void cambiarContrasena() {
        ocultarMensajes();
        if (CambiarContrasenaController.mostrar(raiz.getScene().getWindow(), usuario)) {
            mostrarOk("Contraseña actualizada.");
        }
    }

    /**
     * Elige el tema claro
     */
    @FXML
    private void elegirClaro() {
        elegirTema("claro");
    }

    /**
     * Elige el tema oscuro
     */
    @FXML
    private void elegirOscuro() {
        elegirTema("oscuro");
    }

    /**
     * Elige el tema del sistema
     */
    @FXML
    private void elegirSistema() {
        elegirTema("sistema");
    }

    /**
     * Descarta lo escrito y vuelve a poner los datos guardados
     */
    @FXML
    private void cancelar() {
        cargar();
    }

    /**
     * Guarda los datos personales y las preferencias
     */
    @FXML
    private void guardar() {
        ocultarMensajes();
        Campos.marcarError(cajaNombre, false);
        Campos.marcarError(cajaEmail, false);
        try {
            servicio.guardar(usuario, txtNombre.getText(), txtEmail.getText(), cmbMoneda.getValue(), tema);
        } catch (DatoInvalido e) {
            if ("nombre".equals(e.getCampo())) {
                Campos.marcarError(cajaNombre, true);
            } else if ("email".equals(e.getCampo())) {
                Campos.marcarError(cajaEmail, true);
            }
            mostrarError(e.getMessage());
            return;
        }
        alCambiar.run();
        cargar();
        mostrarOk("Cambios guardados.");
    }

    /**
     * Abre la ventana para crear una cuenta; si se crea, aparece en la lista y en la barra lateral
     */
    @FXML
    private void anadirCuenta() {
        ocultarMensajes();
        Optional<Cuenta> creada = NuevaCuentaController.mostrar(raiz.getScene().getWindow(), usuario);
        if (creada.isPresent()) {
            pintarCuentas();
            alCambiar.run();
        }
    }

    /**
     * Rellena toda la pantalla con los datos guardados del usuario (también sirve para cancelar los cambios)
     */
    private void cargar() {
        usuario = SesionActual.getUsuario();
        if (usuario == null) {
            return;
        }
        ocultarMensajes();
        lblAvatar.setText(Formato.iniciales(usuario.getNombre()));
        txtNombre.setText(usuario.getNombre());
        txtEmail.setText(usuario.getEmail());
        Campos.marcarError(cajaNombre, false);
        Campos.marcarError(cajaEmail, false);

        // El email solo se edita en cuentas offline: el de una cuenta online es el de Firebase
        boolean soloLocal = usuario.isSoloLocal();
        txtEmail.setEditable(soloLocal);
        cajaEmail.getStyleClass().remove("solo-lectura");
        if (!soloLocal) {
            cajaEmail.getStyleClass().add("solo-lectura");
        }
        lblEmailNota.setVisible(!soloLocal);
        lblEmailNota.setManaged(!soloLocal);
        lblTipoCuenta.setText(soloLocal ? "Cuenta offline" : "Cuenta online");
        lblPasswordNota.setText(soloLocal ? "Solo se guarda en este equipo." : "Se cambia en este equipo y en la nube.");

        List<String> monedas = new ArrayList<>(MONEDAS);
        if (!monedas.contains(usuario.getMoneda())) {
            monedas.add(usuario.getMoneda());
        }
        cmbMoneda.getItems().setAll(monedas);
        cmbMoneda.setValue(usuario.getMoneda());

        elegirTema(usuario.getTema() == null ? "claro" : usuario.getTema());
        pintarCuentas();
    }

    /**
     * Marca una de las tres tarjetas de tema (todavía sin guardar)
     * @param nuevo: "claro", "oscuro" o "sistema"
     */
    private void elegirTema(String nuevo) {
        tema = PerfilService.TEMAS.contains(nuevo) ? nuevo : "claro";
        marcarTema(cardClaro, checkClaro, "claro".equals(tema));
        marcarTema(cardOscuro, checkOscuro, "oscuro".equals(tema));
        marcarTema(cardSistema, checkSistema, "sistema".equals(tema));
    }

    /**
     * Marca o desmarca una tarjeta de tema
     * @param tarjeta: tarjeta del tema
     * @param check: círculo con el visto de esa tarjeta
     * @param seleccionada: true si es la elegida
     */
    private void marcarTema(VBox tarjeta, Group check, boolean seleccionada) {
        tarjeta.getStyleClass().remove("seleccionado");
        if (seleccionada) {
            tarjeta.getStyleClass().add("seleccionado");
        }
        check.setVisible(seleccionada);
    }

    /**
     * Rellena la lista de cuentas (también las desactivadas) y el contador de activas
     */
    private void pintarCuentas() {
        listaCuentas.getChildren().clear();
        List<FilaCuenta> filas = servicio.listarCuentas(usuario);
        long activas = filas.stream().filter(f -> f.cuenta().isActiva()).count();
        lblActivas.setText(activas + (activas == 1 ? " activa" : " activas"));
        if (filas.isEmpty()) {
            Label vacio = new Label("Aún no tienes cuentas.");
            vacio.getStyleClass().add("texto-suave");
            vacio.setStyle("-fx-padding: 14 0 14 0;");
            listaCuentas.getChildren().add(vacio);
            return;
        }
        for (FilaCuenta fila : filas) {
            listaCuentas.getChildren().add(crearFilaCuenta(fila));
        }
    }

    /**
     * Crea la fila de una cuenta: icono, nombre, tipo, saldo y menú con las acciones
     * @param fila: cuenta con su saldo
     * @return Devuelve la fila
     */
    private VBox crearFilaCuenta(FilaCuenta fila) {
        Cuenta c = fila.cuenta();
        boolean efectivo = "efectivo".equals(c.getTipo());

        Label nombre = new Label(c.getNombre());
        nombre.getStyleClass().add("tarjeta-cuenta-nombre");
        nombre.setMaxWidth(Double.MAX_VALUE);
        Label tipo = new Label(!c.isActiva() ? "Desactivada" : efectivo ? "Tu cartera" : "Cuenta bancaria");
        tipo.getStyleClass().add("tarjeta-cuenta-tipo");
        VBox textos = new VBox(2, nombre, tipo);
        textos.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(textos, Priority.ALWAYS);

        Button puntos = new Button("···");
        puntos.getStyleClass().add("boton-puntos");
        ContextMenu menu = crearMenuCuenta(c);
        puntos.setOnAction(e -> menu.show(puntos, Side.BOTTOM, 0, 0));

        HBox cabecera = new HBox(10, icono(efectivo ? ICONO_EFECTIVO : ICONO_BANCO), textos, puntos);
        cabecera.setAlignment(Pos.CENTER_LEFT);

        Label saldo = new Label(Formato.moneda(fila.saldo() == null ? BigDecimal.ZERO : fila.saldo(), c.getMoneda()));
        saldo.getStyleClass().add("perfil-saldo");

        VBox caja = new VBox(6, cabecera, saldo);
        caja.getStyleClass().add("cuenta-fila");
        if (!c.isActiva()) {
            caja.getStyleClass().add("inactiva");
        }
        return caja;
    }

    /**
     * Menú de acciones de una cuenta: renombrar y activar o desactivar
     * @param c: cuenta
     * @return Devuelve el menú
     */
    private ContextMenu crearMenuCuenta(Cuenta c) {
        MenuItem renombrar = new MenuItem("Renombrar");
        renombrar.setOnAction(e -> renombrar(c));
        MenuItem estado = new MenuItem(c.isActiva() ? "Desactivar" : "Activar");
        estado.setOnAction(e -> {
            if (c.isActiva()) {
                desactivar(c);
            } else {
                servicio.cambiarEstadoCuenta(c, true);
                cuentaCambiada();
            }
        });
        return new ContextMenu(renombrar, estado);
    }

    /**
     * Pide un nombre nuevo para la cuenta y lo guarda
     * @param c: cuenta a renombrar
     */
    private void renombrar(Cuenta c) {
        TextInputDialog dialogo = new TextInputDialog(c.getNombre());
        dialogo.setTitle("MisCuentas");
        dialogo.setHeaderText("Renombrar cuenta");
        dialogo.setContentText("Nombre:");
        prepararDialogo(dialogo);
        ((Button) dialogo.getDialogPane().lookupButton(ButtonType.OK)).setText("Guardar");
        ((Button) dialogo.getDialogPane().lookupButton(ButtonType.CANCEL)).setText("Cancelar");
        Optional<String> nuevo = dialogo.showAndWait();
        if (nuevo.isEmpty()) {
            return;
        }
        try {
            servicio.renombrarCuenta(usuario, c, nuevo.get());
            cuentaCambiada();
        } catch (DatoInvalido e) {
            Alert error = new Alert(Alert.AlertType.WARNING, e.getMessage());
            error.setTitle("MisCuentas");
            error.setHeaderText("No se pudo renombrar la cuenta");
            prepararDialogo(error);
            error.showAndWait();
        }
    }

    /**
     * Desactiva una cuenta después de confirmarlo
     * @param c: cuenta a desactivar
     */
    private void desactivar(Cuenta c) {
        ButtonType desactivar = new ButtonType("Desactivar", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelar = new ButtonType("Cancelar", javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION, "Dejará de aparecer en la barra lateral y de contar en la vista general. "
                + "Sus movimientos se conservan y puedes volver a activarla cuando quieras.", desactivar, cancelar);
        confirmacion.setTitle("MisCuentas");
        confirmacion.setHeaderText("¿Desactivar \"" + c.getNombre() + "\"?");
        confirmacion.setGraphic(null);
        prepararDialogo(confirmacion);
        if (confirmacion.showAndWait().filter(b -> b == desactivar).isPresent()) {
            servicio.cambiarEstadoCuenta(c, false);
            cuentaCambiada();
        }
    }

    /**
     * Refresca la lista y avisa para que se actualice la barra lateral
     */
    private void cuentaCambiada() {
        pintarCuentas();
        alCambiar.run();
    }

    /**
     * Pone al diálogo su ventana propietaria y la hoja de estilos de la aplicación
     * @param dialogo: diálogo a preparar
     */
    private void prepararDialogo(javafx.scene.control.Dialog<?> dialogo) {
        dialogo.initOwner(raiz.getScene().getWindow());
        dialogo.getDialogPane().getStylesheets().add(getClass().getResource("/screens/styles/application.css").toExternalForm());
    }

    /**
     * Texto de una moneda para el selector, por ejemplo "EUR — Euro (€)"
     * @param codigo: código de moneda de tres letras
     * @return Devuelve el texto
     */
    private String descripcionMoneda(String codigo) {
        try {
            String nombre = Currency.getInstance(codigo).getDisplayName(Locale.forLanguageTag("es-ES"));
            nombre = nombre.substring(0, 1).toUpperCase(Locale.forLanguageTag("es-ES")) + nombre.substring(1);
            return codigo + " — " + nombre + " (" + Formato.simbolo(codigo) + ")";
        } catch (IllegalArgumentException e) {
            return codigo;
        }
    }

    /**
     * Crea un icono de 16 píxeles
     * @param contenido: trazo SVG
     * @return Devuelve el icono
     */
    private Group icono(String contenido) {
        SVGPath trazo = new SVGPath();
        trazo.setContent(contenido);
        trazo.getStyleClass().add("icono");
        trazo.setScaleX(ESCALA_ICONO);
        trazo.setScaleY(ESCALA_ICONO);
        return new Group(trazo);
    }

    /**
     * Muestra un error bajo las preferencias
     * @param texto: texto del error
     */
    private void mostrarError(String texto) {
        lblError.setText(texto);
        lblError.setVisible(true);
        lblError.setManaged(true);
    }

    /**
     * Muestra un aviso de que todo ha ido bien
     * @param texto: texto del aviso
     */
    private void mostrarOk(String texto) {
        lblOk.setText(texto);
        lblOk.setVisible(true);
        lblOk.setManaged(true);
    }

    /**
     * Oculta el error y el aviso
     */
    private void ocultarMensajes() {
        for (Label l : new Label[] { lblError, lblOk }) {
            l.setText("");
            l.setVisible(false);
            l.setManaged(false);
        }
    }
}
