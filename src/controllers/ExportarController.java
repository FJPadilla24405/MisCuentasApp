package controllers;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Group;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import models.Cuenta;
import models.Usuario;
import service.exportar.DatosExportacion;
import service.exportar.ExportacionService;
import service.exportar.ExportadorExcel;
import service.exportar.ExportadorPdf;
import service.exportar.FormatoArchivo;
import service.exportar.OpcionesExportacion;
import service.exportar.Tabla.Seccion;
import service.resumen.ExportadorCsv;
import util.Campos;
import util.Formato;

/**
 * Controlador de ExportarDialog.fxml: la ventana de exportar que abren los botones de Resumen y de Gráficas y
 * resúmenes. Es la misma para las dos: se elige la cuenta, el periodo (mes o año), el formato (PDF, CSV o Excel), qué
 * partes entran en el archivo y su nombre. Lo único que cambia es lo que viene activado al abrirla. Se abre con
 * ExportarController.mostrar(...); los datos los prepara ExportacionService y cada formato lo escribe su exportador.
 *
 * @author Fco Padilla
 */
public class ExportarController {

    // Para que Excel reconozca que el CSV está en UTF-8
    private static final String BOM = "\uFEFF";
    // Trazos de los iconos (cuadrícula de 24x24)
    private static final String ICONO_DOCUMENTO = "M14 3 H7 A2 2 0 0 0 5 5 V19 A2 2 0 0 0 7 21 H17 A2 2 0 0 0 19 19 V8 Z M14 3 V8 H19 M9 13 H15 M9 17 H13";
    private static final String ICONO_TABLA = "M4 5 H20 V19 H4 Z M4 10 H20 M4 15 H20 M10 5 V19";
    private static final String ICONO_VISTO = "M12 3 A9 9 0 1 0 12 21 A9 9 0 1 0 12 3 Z M8 12 L11 15 L16 9";
    private static final double ESCALA_ICONO = 0.67;

    // Una cuenta del desplegable con el texto que se ve (cuenta null = todas)
    private record OpcionCuenta(Cuenta cuenta, String texto) {
        @Override
        public String toString() {
            return texto;
        }
    }

    @FXML private ToggleGroup grupoPeriodo;
    @FXML private ToggleButton tglMes;
    @FXML private ToggleButton tglAnio;
    @FXML private ComboBox<OpcionCuenta> cmbCuenta;
    @FXML private Label lblPeriodoEtiqueta;
    @FXML private Label lblPeriodo;
    @FXML private Label lblError;
    @FXML private HBox cajaPeriodo;
    @FXML private HBox cajaFormatos;
    @FXML private HBox cajaNombre;
    @FXML private CheckBox chkResumen;
    @FXML private CheckBox chkGraficas;
    @FXML private CheckBox chkMovimientos;
    @FXML private CheckBox chkNotas;
    @FXML private TextField txtNombre;
    @FXML private Button btnDescargar;

    private final ExportacionService servicio = new ExportacionService();
    private final ToggleGroup grupoFormato = new ToggleGroup();
    private final ContextMenu menuPeriodo = new ContextMenu();
    private Usuario usuario;
    private List<YearMonth> meses = List.of();
    private List<Integer> anios = List.of();
    private YearMonth mes = YearMonth.now();
    private int anio = YearMonth.now().getYear();
    private boolean anual = false;
    private FormatoArchivo formato = FormatoArchivo.PDF;
    // true mientras se rellena la ventana: los cambios no recalculan nada hasta el final
    private boolean preparando = true;
    // Nombre que propone la ventana y si el usuario lo ha escrito a mano (entonces ya no se cambia solo)
    private String nombreAutomatico = "";
    private boolean nombreManual = false;
    private boolean ajustandoNombre = false;

    /**
     * Prepara los controles: las tarjetas de formato, el selector de periodo y todos los avisos de cambio
     */
    @FXML
    private void initialize() {
        Campos.configurar(cajaNombre, txtNombre);

        // Cuenta
        cmbCuenta.valueProperty().addListener((obs, antes, ahora) -> alCambiar());

        // Mes o año: siempre hay uno elegido
        grupoPeriodo.selectedToggleProperty().addListener((obs, antes, ahora) -> {
            if (ahora == null) {
                grupoPeriodo.selectToggle(antes);
                return;
            }
            anual = ahora == tglAnio;
            refrescarPeriodo();
            alCambiar();
        });
        cajaPeriodo.setOnMouseClicked(e -> abrirMenuPeriodo());

        // Formato: tres tarjetas, siempre una elegida
        cajaFormatos.getChildren().addAll(
                tarjetaFormato(FormatoArchivo.PDF, ICONO_DOCUMENTO),
                tarjetaFormato(FormatoArchivo.CSV, ICONO_DOCUMENTO),
                tarjetaFormato(FormatoArchivo.EXCEL, ICONO_TABLA));
        grupoFormato.selectedToggleProperty().addListener((obs, antes, ahora) -> {
            if (ahora == null) {
                grupoFormato.selectToggle(antes);
                return;
            }
            formato = (FormatoArchivo) ahora.getUserData();
            if (nombreManual && !txtNombre.getText().isBlank()) {
                // Un nombre escrito a mano se respeta, pero con la extensión del formato nuevo
                fijarNombre(FormatoArchivo.sinExtension(txtNombre.getText().trim()) + "." + formato.getExtension());
            }
            alCambiar();
        });

        // Contenido: las notas solo tienen sentido con la tabla de movimientos
        chkNotas.disableProperty().bind(chkMovimientos.selectedProperty().not());
        chkMovimientos.selectedProperty().addListener((obs, antes, ahora) -> {
            if (!ahora) {
                chkNotas.setSelected(false);
            }
        });
        chkResumen.selectedProperty().addListener((obs, antes, ahora) -> alCambiar());
        chkGraficas.selectedProperty().addListener((obs, antes, ahora) -> alCambiar());
        chkMovimientos.selectedProperty().addListener((obs, antes, ahora) -> alCambiar());

        // Nombre: si lo escribe el usuario, deja de ser automático (hasta que vuelva a ser igual que el propuesto)
        txtNombre.textProperty().addListener((obs, antes, ahora) -> {
            if (!ajustandoNombre) {
                nombreManual = !ahora.equals(nombreAutomatico);
            }
            actualizarBoton();
        });
    }

    /**
     * Abre la ventana y espera a que se cierre. Al pulsar Descargar se elige dónde guardar y se crea el archivo.
     * @param propietario: ventana desde la que se abre (queda bloqueada hasta que se cierre)
     * @param usuario: usuario de la sesión
     * @param inicial: cuenta, periodo y partes activadas con las que se abre (formato PDF)
     */
    public static void mostrar(Window propietario, Usuario usuario, OpcionesExportacion inicial) {
        try {
            FXMLLoader loader = new FXMLLoader(ExportarController.class.getResource("/screens/dialogs/ExportarDialog.fxml"));
            Parent raiz = loader.load();
            ExportarController controlador = loader.getController();
            controlador.preparar(usuario, inicial);

            Stage ventana = new Stage();
            ventana.initOwner(propietario);
            ventana.initModality(Modality.WINDOW_MODAL);
            ventana.setTitle("MisCuentas · Exportar");
            ventana.setResizable(false);
            ventana.setScene(new Scene(raiz));
            ventana.showAndWait();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo abrir la ventana de exportar", e);
        }
    }

    /**
     * Rellena la ventana con la cuenta, el periodo y las partes con las que se abre
     * @param usuario: usuario de la sesión
     * @param inicial: opciones iniciales
     */
    private void preparar(Usuario usuario, OpcionesExportacion inicial) {
        this.usuario = usuario;
        anual = inicial.anual();
        mes = inicial.mes();
        anio = inicial.anio();
        meses = servicio.mesesDisponibles(usuario, mes);
        TreeSet<Integer> listaAnios = new TreeSet<>(java.util.Comparator.reverseOrder());
        listaAnios.add(anio);
        listaAnios.add(YearMonth.now().getYear());
        for (YearMonth m : meses) {
            listaAnios.add(m.getYear());
        }
        anios = new ArrayList<>(listaAnios);

        // Cuentas: primero "todas", con los nombres de todas las que hay
        List<Cuenta> cuentas = servicio.cuentasDisponibles(usuario);
        List<OpcionCuenta> opciones = new ArrayList<>();
        List<String> nombres = new ArrayList<>();
        for (Cuenta c : cuentas) {
            nombres.add(c.getNombre());
        }
        opciones.add(new OpcionCuenta(null, nombres.isEmpty() ? "Todas las cuentas" : "Todas las cuentas · " + Formato.lista(nombres)));
        for (Cuenta c : cuentas) {
            opciones.add(new OpcionCuenta(c, c.getNombre()));
        }
        cmbCuenta.getItems().setAll(opciones);
        OpcionCuenta elegida = opciones.get(0);
        if (inicial.cuenta() != null) {
            for (OpcionCuenta o : opciones) {
                if (o.cuenta() != null && o.cuenta().getId() == inicial.cuenta().getId()) {
                    elegida = o;
                }
            }
        }
        cmbCuenta.setValue(elegida);

        grupoPeriodo.selectToggle(anual ? tglAnio : tglMes);
        chkResumen.setSelected(inicial.resumen());
        chkGraficas.setSelected(inicial.graficas());
        chkMovimientos.setSelected(inicial.movimientos());
        chkNotas.setSelected(false);
        grupoFormato.selectToggle(grupoFormato.getToggles().get(0));

        preparando = false;
        refrescarPeriodo();
        alCambiar();
    }

    // ===== Piezas de la ventana =====

    /**
     * Crea una tarjeta de formato: el icono, una marca de elegido que solo se ve cuando está seleccionada, el nombre y una frase corta
     * @param tipo: formato que representa
     * @param trazo: trazo SVG del icono
     * @return Devuelve la tarjeta, ya en el grupo de formatos
     */
    private ToggleButton tarjetaFormato(FormatoArchivo tipo, String trazo) {
        ToggleButton tarjeta = new ToggleButton();
        tarjeta.setMnemonicParsing(false);
        tarjeta.getStyleClass().add("formato-opcion");
        tarjeta.setToggleGroup(grupoFormato);
        tarjeta.setUserData(tipo);
        tarjeta.setMinWidth(0);
        tarjeta.setPrefWidth(100);
        tarjeta.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(tarjeta, Priority.ALWAYS);

        Group marca = icono(ICONO_VISTO);
        marca.visibleProperty().bind(tarjeta.selectedProperty());
        Region hueco = new Region();
        HBox.setHgrow(hueco, Priority.ALWAYS);
        HBox arriba = new HBox(icono(trazo), hueco, marca);
        arriba.setAlignment(Pos.CENTER_LEFT);

        Label nombre = new Label(tipo.getNombre());
        nombre.getStyleClass().add("formato-nombre");
        Label detalle = new Label(tipo.getDescripcion());
        detalle.getStyleClass().add("formato-detalle");
        VBox contenido = new VBox(8, arriba, new VBox(2, nombre, detalle));
        contenido.setAlignment(Pos.TOP_LEFT);
        // El botón no estira su contenido: se le da el ancho de la tarjeta (menos el relleno) para que la marca quede a la derecha
        contenido.prefWidthProperty().bind(tarjeta.widthProperty().subtract(30));
        tarjeta.setGraphic(contenido);
        return tarjeta;
    }

    /**
     * @param trazo: trazo SVG
     * @return Devuelve un icono verde de 16 píxeles
     */
    private static Group icono(String trazo) {
        SVGPath forma = new SVGPath();
        forma.setContent(trazo);
        forma.getStyleClass().addAll("icono", "formato-icono");
        forma.setScaleX(ESCALA_ICONO);
        forma.setScaleY(ESCALA_ICONO);
        return new Group(forma);
    }

    /**
     * Abre la lista de meses o de listaAnios (según el periodo elegido) bajo el campo del periodo
     */
    private void abrirMenuPeriodo() {
        menuPeriodo.getItems().clear();
        if (anual) {
            for (int a : anios) {
                MenuItem opcion = new MenuItem(String.valueOf(a));
                opcion.setOnAction(e -> {
                    anio = a;
                    mes = YearMonth.of(a, mes.getMonthValue());
                    refrescarPeriodo();
                    alCambiar();
                });
                menuPeriodo.getItems().add(opcion);
            }
        } else {
            for (YearMonth m : meses) {
                MenuItem opcion = new MenuItem(Formato.mesConAnio(m));
                opcion.setOnAction(e -> {
                    mes = m;
                    anio = m.getYear();
                    refrescarPeriodo();
                    alCambiar();
                });
                menuPeriodo.getItems().add(opcion);
            }
        }
        menuPeriodo.show(cajaPeriodo, Side.BOTTOM, 0, 4);
    }

    /**
     * Pone en el campo del periodo el mes o el año elegido y el nombre del campo que le corresponde
     */
    private void refrescarPeriodo() {
        lblPeriodoEtiqueta.setText(anual ? "Año seleccionado" : "Mes seleccionado");
        lblPeriodo.setText(anual ? String.valueOf(anio) : Formato.mesConAnio(mes));
    }

    // ===== Cambios =====

    /**
     * Se llama cada vez que cambia algo que afecta a lo exportado: actualiza el número de movimientos, el nombre propuesto y el botón
     */
    private void alCambiar() {
        if (preparando || usuario == null) {
            return;
        }
        actualizarContador();
        actualizarNombre();
        actualizarBoton();
    }

    /**
     * Escribe en la casilla de la tabla cuántos movimientos tiene el periodo y la cuenta elegidos
     */
    private void actualizarContador() {
        int n = servicio.contarMovimientos(usuario, cuentaElegida(), prefijoFecha());
        String texto;
        if (n == 0) {
            texto = "Tabla detallada de los movimientos (no hay ninguno)";
        } else if (n == 1) {
            texto = "Tabla detallada del único movimiento";
        } else {
            texto = "Tabla detallada de los " + n + " movimientos";
        }
        chkMovimientos.setText(texto);
    }

    /**
     * Propone un nombre de archivo según lo elegido (si el usuario no lo ha escrito a mano)
     */
    private void actualizarNombre() {
        nombreAutomatico = proponerNombre();
        if (!nombreManual) {
            fijarNombre(nombreAutomatico);
        }
    }

    /**
     * Activa el botón de descargar solo si hay algo que exportar y el archivo tiene nombre
     */
    private void actualizarBoton() {
        btnDescargar.setDisable(!hayContenido() || txtNombre.getText() == null || txtNombre.getText().isBlank());
    }

    /**
     * @return Devuelve el nombre que propone la ventana: miscuentas_resumen_septiembre_2026.pdf, con el tipo de informe
     *         (resumen, graficas, movimientos o informe si hay varias partes), el periodo y, si es una sola cuenta, su nombre
     */
    private String proponerNombre() {
        int partes = (chkResumen.isSelected() ? 1 : 0) + (chkGraficas.isSelected() ? 1 : 0) + (chkMovimientos.isSelected() ? 1 : 0);
        String tipo = "informe";
        if (partes == 1) {
            tipo = chkResumen.isSelected() ? "resumen" : chkGraficas.isSelected() ? "graficas" : "movimientos";
        }
        String periodo = anual ? String.valueOf(anio) : Formato.nombreMes(mes) + "_" + mes.getYear();
        Cuenta cuenta = cuentaElegida();
        String sufijo = cuenta == null ? "" : simplificar(cuenta.getNombre());
        return "miscuentas_" + tipo + "_" + periodo + (sufijo.isEmpty() ? "" : "_" + sufijo) + "." + formato.getExtension();
    }

    /**
     * @param texto: texto cualquiera
     * @return Devuelve el texto en minúsculas, sin tildes y con guiones bajos en lugar de lo que no sean letras o números
     */
    private static String simplificar(String texto) {
        String limpio = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        return limpio.replaceAll("^_+|_+$", "");
    }

    /**
     * Cambia el texto del nombre sin que cuente como que lo ha escrito el usuario
     * @param texto: nombre nuevo
     */
    private void fijarNombre(String texto) {
        ajustandoNombre = true;
        txtNombre.setText(texto);
        ajustandoNombre = false;
    }

    // ===== Lo elegido =====

    /**
     * @return Devuelve la cuenta elegida, o null si son todas
     */
    private Cuenta cuentaElegida() {
        OpcionCuenta opcion = cmbCuenta.getValue();
        return opcion == null ? null : opcion.cuenta();
    }

    /**
     * @return Devuelve el comienzo de las fechas del periodo: yyyy-MM para un mes o yyyy para un año
     */
    private String prefijoFecha() {
        return anual ? String.valueOf(anio) : mes.toString();
    }

    /**
     * @return Devuelve true si hay al menos una parte del informe activada
     */
    private boolean hayContenido() {
        return chkResumen.isSelected() || chkGraficas.isSelected() || chkMovimientos.isSelected();
    }

    /**
     * @return Devuelve el nombre escrito como nombre de archivo válido (sin caracteres prohibidos y con la extensión
     *         del formato), o null si está vacío
     */
    private String nombreLimpio() {
        String texto = txtNombre.getText() == null ? "" : txtNombre.getText().trim();
        texto = FormatoArchivo.sinExtension(texto).replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return texto.isEmpty() ? null : texto + "." + formato.getExtension();
    }

    // ===== Descargar =====

    /**
     * Pide dónde guardar el archivo y lo crea con lo elegido
     */
    @FXML
    private void descargar() {
        ocultarError();
        if (btnDescargar.isDisable()) {
            return;
        }
        String nombre = nombreLimpio();
        if (nombre == null) {
            Campos.marcarError(cajaNombre, true);
            mostrarError("Escribe un nombre para el archivo.");
            return;
        }
        OpcionesExportacion opciones = new OpcionesExportacion(formato, cuentaElegida(), anual, mes, anio,
                chkResumen.isSelected(), chkGraficas.isSelected(), chkMovimientos.isSelected(),
                chkNotas.isSelected() && chkMovimientos.isSelected(), nombre);

        FileChooser selector = new FileChooser();
        selector.setTitle("Guardar exportación");
        selector.setInitialFileName(nombre);
        selector.getExtensionFilters().add(new FileChooser.ExtensionFilter(formato.getNombre(), "*." + formato.getExtension()));
        File carpeta = carpetaInicial();
        if (carpeta != null) {
            selector.setInitialDirectory(carpeta);
        }
        File destino = selector.showSaveDialog(txtNombre.getScene().getWindow());
        if (destino == null) {
            return;
        }
        if (!destino.getName().toLowerCase().endsWith("." + formato.getExtension())) {
            destino = new File(destino.getParentFile(), destino.getName() + "." + formato.getExtension());
        }

        // El texto "Generando…" se pinta antes de empezar, porque el PDF tarda unos segundos
        bloquear(true);
        File archivo = destino;
        Platform.runLater(() -> generar(opciones, archivo));
    }

    /**
     * Calcula los datos, los escribe en el formato elegido y cierra la ventana. Si algo falla, lo cuenta sin cerrarla.
     * @param opciones: lo elegido
     * @param destino: archivo donde se guarda
     */
    private void generar(OpcionesExportacion opciones, File destino) {
        try {
            DatosExportacion datos = servicio.preparar(usuario, opciones);
            List<Seccion> secciones = servicio.secciones(datos, opciones, opciones.formato() == FormatoArchivo.PDF);
            Path ruta = destino.toPath();
            switch (opciones.formato()) {
                case CSV -> Files.writeString(ruta, BOM + ExportadorCsv.generar(secciones), StandardCharsets.UTF_8);
                case EXCEL -> ExportadorExcel.escribir(ruta, secciones, datos.moneda());
                case PDF -> ExportadorPdf.escribir(ruta, datos, secciones);
            }
            cerrar();
        } catch (IOException | RuntimeException e) {
            bloquear(false);
            mostrarError("No se pudo guardar el archivo: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    /**
     * @return Devuelve la carpeta de Descargas del usuario si existe (o su carpeta personal), o null si no hay ninguna
     */
    private static File carpetaInicial() {
        File inicio = new File(System.getProperty("user.home", ""));
        for (String nombre : new String[] { "Downloads", "Descargas" }) {
            File carpeta = new File(inicio, nombre);
            if (carpeta.isDirectory()) {
                return carpeta;
            }
        }
        return inicio.isDirectory() ? inicio : null;
    }

    /**
     * Cambia el botón mientras se genera el archivo
     * @param generando: true mientras se genera
     */
    private void bloquear(boolean generando) {
        btnDescargar.setText(generando ? "Generando…" : "Descargar");
        if (generando) {
            btnDescargar.setDisable(true);
        } else {
            actualizarBoton();
        }
    }

    /**
     * Cierra la ventana sin hacer nada
     */
    @FXML
    private void cancelar() {
        cerrar();
    }

    /**
     * Cierra la ventana
     */
    private void cerrar() {
        ((Stage) txtNombre.getScene().getWindow()).close();
    }

    /**
     * Muestra el texto del error
     * @param texto: error
     */
    private void mostrarError(String texto) {
        lblError.setText(texto);
        lblError.setVisible(true);
        lblError.setManaged(true);
    }

    /**
     * Oculta el texto del error
     */
    private void ocultarError() {
        lblError.setText("");
        lblError.setVisible(false);
        lblError.setManaged(false);
    }
}
