package controllers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Group;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.util.StringConverter;
import models.Categoria;
import models.Cuenta;
import models.Usuario;
import service.auth.SesionActual;
import service.CategoriaService;
import service.CategoriaService.Usos;
import service.perfil.PerfilService;
import service.perfil.PerfilService.DatoInvalido;
import service.perfil.TemaService;
import service.resumen.ResumenService.FilaCuenta;
import util.Campos;
import util.tema.ColoresTema;
import util.tema.ColoresTema.Elemento;
import util.Formato;
import util.IconosCategoria;
import util.tema.Tema;

/**
 * Controlador de Perfil.fxml: datos personales, cambio de contraseña, preferencias (moneda y tema) y
 * gestión de las cuentas (añadir, renombrar, activar y desactivar). La lógica está en PerfilService.
 * El tema (claro, oscuro o personalizado) se ve al momento en toda la aplicación como vista previa y se queda al guardar;
 * si se sale de la pantalla sin guardar, vuelve el tema guardado. Los colores del tema personalizado se guardan en un
 * archivo de este equipo (TemaService), no en la base de datos.
 *
 * @author Fco Padilla
 */
public class PerfilController {

    // Trazos de los iconos que se crean en código (cuadrícula de 24x24)
    private static final String ICONO_BANCO = "M3 21 H21 M5 21 V11 M9 21 V11 M15 21 V11 M19 21 V11 M12 3 L3 9 H21 Z";
    private static final String ICONO_EFECTIVO = "M4 7 H19 A1 1 0 0 1 20 8 V19 A1 1 0 0 1 19 20 H5 A2 2 0 0 1 3 18 V6 A2 2 0 0 1 5 4 H17 M16 14 L16.01 14";
    // 24 de la cuadrícula del icono escalados a 16 píxeles
    private static final double ESCALA_ICONO = 0.67;
    // Alto máximo de las listas de cuentas y de categorías: a partir de ahí tienen scroll
    private static final double ALTO_MAXIMO_LISTA = 200;
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
    @FXML private VBox cardPersonal;
    @FXML private Group checkClaro;
    @FXML private Group checkOscuro;
    @FXML private Group checkPersonal;
    @FXML private HBox vistaPersonal;
    @FXML private Region vpLateral;
    @FXML private Region vpBarra;
    @FXML private Region vpBloque;
    @FXML private VBox panelPersonal;
    @FXML private VBox listaColores;
    @FXML private Label lblError;
    @FXML private Label lblOk;
    @FXML private Label lblActivas;
    @FXML private ScrollPane scrollCuentas;
    @FXML private VBox listaCuentas;
    @FXML private Label lblNumCategorias;
    @FXML private ScrollPane scrollCategorias;
    @FXML private VBox listaCategorias;

    private final PerfilService servicio = new PerfilService();
    private final CategoriaService categoriaService = new CategoriaService();
    private final TemaService temaService = new TemaService();
    // Un selector de color por cada color del tema personalizado
    private final Map<Elemento, ColorPicker> selectores = new EnumMap<>(Elemento.class);
    // true mientras se ponen los colores guardados, para que no cuente como un cambio del usuario
    private boolean cargandoColores;
    private Runnable alCambiar = () -> { };
    private Usuario usuario;
    // Tema elegido en pantalla (todavía sin guardar)
    private String tema = Tema.CLARO;

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
        limitarAlto(scrollCuentas, listaCuentas);
        limitarAlto(scrollCategorias, listaCategorias);
        crearSelectoresColor();
        // Si se sale de esta pantalla sin guardar, la vista previa del tema se quita y vuelve el tema guardado
        raiz.sceneProperty().addListener((o, antes, ahora) -> {
            if (ahora == null) {
                TemaService.aplicar(SesionActual.getUsuario());
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
        elegirTema(Tema.CLARO);
        vistaPrevia();
    }

    /**
     * Elige el tema oscuro
     */
    @FXML
    private void elegirOscuro() {
        elegirTema(Tema.OSCURO);
        vistaPrevia();
    }

    /**
     * Elige el tema personalizado y enseña el editor de colores
     */
    @FXML
    private void elegirPersonal() {
        elegirTema(Tema.PERSONAL);
        vistaPrevia();
    }

    /**
     * Pone en el editor los colores del tema claro como punto de partida
     */
    @FXML
    private void basarEnClaro() {
        ponerColores(ColoresTema.baseClara());
    }

    /**
     * Pone en el editor los colores del tema oscuro como punto de partida
     */
    @FXML
    private void basarEnOscuro() {
        ponerColores(ColoresTema.baseOscura());
    }

    /**
     * Descarta lo escrito y vuelve a poner los datos guardados
     */
    @FXML
    private void cancelar() {
        cargar();
        // Quita la vista previa: vuelve el tema guardado
        TemaService.aplicar(usuario);
    }

    /**
     * Guarda los datos personales y las preferencias
     */
    @FXML
    private void guardar() {
        ocultarMensajes();
        Campos.marcarError(cajaNombre, false);
        Campos.marcarError(cajaEmail, false);
        Map<String, String> colores = coloresElegidos();
        try {
            servicio.guardar(usuario, txtNombre.getText(), txtEmail.getText(), cmbMoneda.getValue(), tema);
            // Los colores van a un archivo de este equipo, no a la base de datos
            temaService.guardarColores(usuario, colores);
        } catch (DatoInvalido e) {
            if ("nombre".equals(e.getCampo())) {
                Campos.marcarError(cajaNombre, true);
            } else if ("email".equals(e.getCampo())) {
                Campos.marcarError(cajaEmail, true);
            }
            mostrarError(e.getMessage());
            return;
        }
        Tema.usar(tema, colores);
        alCambiar.run();
        cargar();
        mostrarOk("Cambios guardados.");
    }

    /**
     * Abre la ventana para crear una categoría
     */
    @FXML
    private void anadirCategoria() {
        ocultarMensajes();
        if (CategoriaController.mostrar(raiz.getScene().getWindow(), usuario, null)) {
            pintarCategorias();
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

        elegirTema(usuario.getTema());
        ponerColoresGuardados();
        pintarCuentas();
        pintarCategorias();
    }

    /**
     * Marca una de las tres tarjetas de tema (todavía sin guardar) y enseña el editor de colores solo con el personalizado
     * @param nuevo: "claro", "oscuro" o "personal" (cualquier otro valor, como el antiguo "sistema", se trata como "claro")
     */
    private void elegirTema(String nuevo) {
        tema = Tema.normalizar(nuevo);
        marcarTema(cardClaro, checkClaro, Tema.CLARO.equals(tema));
        marcarTema(cardOscuro, checkOscuro, Tema.OSCURO.equals(tema));
        marcarTema(cardPersonal, checkPersonal, Tema.PERSONAL.equals(tema));
        panelPersonal.setVisible(Tema.PERSONAL.equals(tema));
        panelPersonal.setManaged(Tema.PERSONAL.equals(tema));
    }

    /**
     * Enseña en toda la aplicación el tema elegido en pantalla, sin guardarlo (hasta que se guarde, es solo una vista previa)
     */
    private void vistaPrevia() {
        Tema.usar(tema, coloresElegidos());
    }

    /**
     * Crea el editor de colores: un selector por cada color del tema personalizado, agrupados y en dos columnas
     */
    private void crearSelectoresColor() {
        String grupoActual = null;
        GridPane rejilla = null;
        int enGrupo = 0;
        for (Elemento e : Elemento.values()) {
            if (!e.getGrupo().equals(grupoActual)) {
                grupoActual = e.getGrupo();
                Label titulo = new Label(grupoActual.toUpperCase(Locale.forLanguageTag("es-ES")));
                titulo.getStyleClass().add("color-grupo");
                rejilla = new GridPane();
                rejilla.setHgap(10);
                rejilla.setVgap(6);
                rejilla.getColumnConstraints().addAll(columna(38), columna(12), columna(38), columna(12));
                listaColores.getChildren().addAll(titulo, rejilla);
                enGrupo = 0;
            }
            Label etiqueta = new Label(e.getEtiqueta());
            etiqueta.getStyleClass().add("color-etiqueta");
            etiqueta.setWrapText(true);
            ColorPicker selector = new ColorPicker(Color.web(e.getClaro()));
            selector.getStyleClass().add("selector-color");
            selector.valueProperty().addListener((o, antes, ahora) -> {
                if (!cargandoColores) {
                    actualizarVistaPersonal();
                    if (Tema.PERSONAL.equals(tema)) {
                        vistaPrevia();
                    }
                }
            });
            selectores.put(e, selector);
            rejilla.add(etiqueta, (enGrupo % 2) * 2, enGrupo / 2);
            rejilla.add(selector, (enGrupo % 2) * 2 + 1, enGrupo / 2);
            GridPane.setValignment(etiqueta, javafx.geometry.VPos.CENTER);
            enGrupo++;
        }
    }

    /**
     * @param porcentaje: ancho de la columna
     * @return Devuelve una columna de la rejilla con ese ancho
     */
    private ColumnConstraints columna(double porcentaje) {
        ColumnConstraints c = new ColumnConstraints();
        c.setPercentWidth(porcentaje);
        return c;
    }

    /**
     * Pone en los selectores los colores guardados del usuario (los del tema claro si no ha guardado ninguno)
     */
    private void ponerColoresGuardados() {
        ponerSelectores(temaService.cargarColores(usuario));
    }

    /**
     * Pone unos colores en los selectores y los enseña como vista previa si el tema elegido es el personalizado
     * @param colores: colores a poner
     */
    private void ponerColores(Map<String, String> colores) {
        ponerSelectores(colores);
        if (Tema.PERSONAL.equals(tema)) {
            vistaPrevia();
        }
    }

    /**
     * Cambia el valor de los selectores sin que cuente como un cambio del usuario
     * @param colores: colores a poner (los que falten son los del tema claro)
     */
    private void ponerSelectores(Map<String, String> colores) {
        Map<String, String> completos = ColoresTema.completar(colores);
        cargandoColores = true;
        try {
            for (Map.Entry<Elemento, ColorPicker> par : selectores.entrySet()) {
                par.getValue().setValue(Color.web(completos.get(par.getKey().getClave())));
            }
        } finally {
            cargandoColores = false;
        }
        actualizarVistaPersonal();
    }

    /**
     * @return Devuelve los colores que hay ahora en los selectores, como texto "#rrggbb"
     */
    private Map<String, String> coloresElegidos() {
        Map<String, String> colores = new LinkedHashMap<>();
        for (Map.Entry<Elemento, ColorPicker> par : selectores.entrySet()) {
            Color c = par.getValue().getValue();
            colores.put(par.getKey().getClave(), String.format(Locale.ROOT, "#%02x%02x%02x",
                    Math.round(c.getRed() * 255), Math.round(c.getGreen() * 255), Math.round(c.getBlue() * 255)));
        }
        return colores;
    }

    /**
     * Pinta la miniatura de la tarjeta "Personalizado" con los colores elegidos
     */
    private void actualizarVistaPersonal() {
        Map<String, String> c = coloresElegidos();
        vistaPersonal.setStyle("-fx-background-color: " + c.get("fondo") + ";");
        vpLateral.setStyle("-fx-background-color: " + c.get("superficie") + ";");
        vpBloque.setStyle("-fx-background-color: " + c.get("superficie") + ";");
        vpBarra.setStyle("-fx-background-color: " + c.get("principal") + ";");
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
     * Hace que una lista tenga el alto de su contenido hasta un máximo (ALTO_MAXIMO_LISTA); a partir de ahí
     * la lista se queda de ese alto y aparece el scroll en vez de estirar la tarjeta hacia abajo.
     * @param scroll: ScrollPane que contiene la lista
     * @param contenido: lista que hay dentro
     */
    private void limitarAlto(ScrollPane scroll, Region contenido) {
        scroll.prefHeightProperty().bind(Bindings.min(ALTO_MAXIMO_LISTA, contenido.heightProperty()));
    }

    /**
     * Rellena la lista de categorías (primero las de gasto, luego las de ingreso) y su contador
     */
    private void pintarCategorias() {
        listaCategorias.getChildren().clear();
        List<Categoria> categorias = categoriaService.listar(usuario);
        lblNumCategorias.setText(categorias.size() + (categorias.size() == 1 ? " categoría" : " categorías"));
        for (Categoria c : categorias) {
            listaCategorias.getChildren().add(crearFilaCategoria(c));
        }
    }

    /**
     * Crea la fila de una categoría: icono, nombre, tipo y menú con las acciones
     * @param c: categoría
     * @return Devuelve la fila
     */
    private HBox crearFilaCategoria(Categoria c) {
        String sub = c.getSubcategoria();
        Label nombre = new Label(sub == null || sub.isBlank() ? c.getNombre() : c.getNombre() + " · " + sub);
        nombre.getStyleClass().add("tarjeta-cuenta-nombre");
        nombre.setMaxWidth(Double.MAX_VALUE);
        Label tipo = new Label("gasto".equals(c.getTipo()) ? "Gasto" : "Ingreso");
        tipo.getStyleClass().add("tarjeta-cuenta-tipo");
        VBox textos = new VBox(2, nombre, tipo);
        textos.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(textos, Priority.ALWAYS);

        Button puntos = new Button("···");
        puntos.getStyleClass().add("boton-puntos");
        MenuItem editar = new MenuItem("Editar");
        editar.setOnAction(e -> {
            if (CategoriaController.mostrar(raiz.getScene().getWindow(), usuario, c)) {
                pintarCategorias();
            }
        });
        MenuItem eliminar = new MenuItem("Eliminar");
        eliminar.setOnAction(e -> eliminarCategoria(c));
        ContextMenu menu = new ContextMenu(editar, eliminar);
        puntos.setOnAction(e -> menu.show(puntos, Side.BOTTOM, 0, 0));

        HBox fila = new HBox(10, icono(IconosCategoria.para(c.getNombre())), textos, puntos);
        fila.setAlignment(Pos.CENTER_LEFT);
        fila.getStyleClass().add("categoria-fila");
        return fila;
    }

    /**
     * Elimina una categoría después de confirmarlo. Si tiene movimientos no se puede; si tiene
     * presupuestos, se eliminan con ella.
     * @param c: categoría a eliminar
     */
    private void eliminarCategoria(Categoria c) {
        ocultarMensajes();
        Usos usos = categoriaService.usos(c);
        if (usos.movimientos() > 0) {
            Alert aviso = new Alert(Alert.AlertType.WARNING, "Se usa en " + usos.movimientos()
                    + (usos.movimientos() == 1 ? " movimiento" : " movimientos")
                    + ". Una categoría con movimientos no se puede eliminar para no perder sus datos.");
            aviso.setTitle("MisCuentas");
            aviso.setHeaderText("No se puede eliminar " + c.getNombre());
            prepararDialogo(aviso);
            aviso.showAndWait();
            return;
        }
        String detalle = usos.presupuestos() > 0
                ? "También se eliminará su presupuesto" + (usos.presupuestos() == 1 ? "" : "s") + " (" + usos.presupuestos() + "). Tus movimientos no cambian."
                : "No tiene movimientos ni presupuestos.";
        ButtonType eliminar = new ButtonType("Eliminar", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelar = new ButtonType("Cancelar", javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION, detalle, eliminar, cancelar);
        confirmacion.setTitle("MisCuentas");
        confirmacion.setHeaderText("¿Eliminar " + c.getNombre() + "?");
        confirmacion.setGraphic(null);
        prepararDialogo(confirmacion);
        if (confirmacion.showAndWait().filter(b -> b == eliminar).isPresent()) {
            try {
                categoriaService.eliminar(c);
            } catch (service.DatoInvalido e) {
                mostrarError(e.getMessage());
            }
            pintarCategorias();
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
