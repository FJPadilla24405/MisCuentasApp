package controllers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;
import models.Categoria;
import models.Cuenta;
import models.Usuario;
import service.importar.ArchivoImportado;
import service.importar.ArchivoNoValido;
import service.importar.FilaImportada;
import service.importar.FilaImportada.Estado;
import service.importar.ImportacionService;
import service.importar.ImportacionService.Columnas;
import service.importar.ImportacionService.Formato;
import service.importar.ImportacionService.FormatoFecha;
import service.importar.ImportacionService.Resultado;
import service.importar.ImportacionService.SeparadorDecimal;
import service.importar.ImportacionService.SignoGasto;

/**
 * Controlador de ImportarMovimientosDialog.fxml: segundo paso de importar movimientos. Enseña el archivo elegido, deja
 * escoger qué columna es la fecha, el concepto, el importe y la categoría (las propone sola), configurar el formato de
 * fechas, decimales y signo, y muestra una vista previa con un check por movimiento para no importar los que no se
 * quieran. Los duplicados salen sin marcar y los que tienen errores no se pueden marcar. Se abre con
 * ImportarMovimientosController.mostrar(...), que antes pide el archivo.
 *
 * @author Fco Padilla
 */
public class ImportarMovimientosController {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    // Cuántas filas del archivo se enseñan como opciones de "Fila de títulos"
    private static final int FILAS_PARA_TITULOS = 40;
    private static final double ESCALA_ICONO = 0.55;
    private static final String ICONO_VALIDO = "M12 3 A9 9 0 1 0 12 21 A9 9 0 1 0 12 3 Z M8 12 L11 15 L16 9";
    private static final String ICONO_DUPLICADO = "M12 4 L21 20 H3 Z M12 10 V14 M12 17 V17.5";
    private static final String ICONO_ERROR = "M12 3 A9 9 0 1 0 12 21 A9 9 0 1 0 12 3 Z M9 9 L15 15 M15 9 L9 15";

    // Una opción de las listas de columnas (indice -1 = sin asignar)
    private record OpcionColumna(int indice, String texto) {
        @Override
        public String toString() {
            return texto;
        }
    }

    // Una opción de la lista de fila de títulos (fila -1 = sin títulos)
    private record OpcionTitulos(int fila, String texto) {
        @Override
        public String toString() {
            return texto;
        }
    }

    @FXML private ScrollPane scroll;
    @FXML private Label lblArchivo;
    @FXML private Label lblArchivoDetalle;
    @FXML private Label lblReconocido;
    @FXML private ComboBox<OpcionColumna> cmbFecha;
    @FXML private ComboBox<OpcionColumna> cmbConcepto;
    @FXML private ComboBox<OpcionColumna> cmbImporte;
    @FXML private ComboBox<OpcionColumna> cmbCategoria;
    @FXML private ComboBox<OpcionColumna> cmbSubcategoria;
    @FXML private ComboBox<OpcionColumna> cmbNota;
    @FXML private ComboBox<OpcionColumna> cmbSaldo;
    @FXML private VBox cajaSaldo;
    @FXML private CheckBox chkAjustarSaldo;
    @FXML private Label lblSaldoDetalle;
    @FXML private Label lblFormato;
    @FXML private Hyperlink enlaceFormato;
    @FXML private VBox panelFormato;
    @FXML private ComboBox<OpcionTitulos> cmbTitulos;
    @FXML private ComboBox<FormatoFecha> cmbFormatoFecha;
    @FXML private ComboBox<SeparadorDecimal> cmbDecimal;
    @FXML private ComboBox<SignoGasto> cmbSigno;
    @FXML private Label lblAvisos;
    @FXML private Label pastillaValidos;
    @FXML private Label lblMensaje;
    @FXML private VBox vistaPrevia;
    @FXML private Label lblSeleccion;
    @FXML private Label lblError;
    @FXML private Button btnImportar;

    private final ImportacionService servicio = new ImportacionService();
    private Usuario usuario;
    private Cuenta cuenta;
    private ArchivoImportado archivo;
    private int filaTitulos = -1;
    private List<FilaImportada> filas = List.of();
    // Filas marcadas para importar (se comparan por identidad: cada FilaImportada es única)
    private final Set<FilaImportada> marcadas = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<FilaImportada, CheckBox> casillas = new IdentityHashMap<>();
    private final Map<String, List<Categoria>> categoriasPorTipo = new java.util.HashMap<>();
    private CheckBox casillaTodas;
    // Saldo que figura en el archivo (null si no trae) y lo que la cuenta ya suma a esa fecha sin lo que se importa
    private ImportacionService.SaldoArchivo saldoArchivo;
    private BigDecimal saldoBase;
    // true mientras se rellenan las listas: los cambios no recalculan la vista previa hasta el final
    private boolean cargando = false;
    private boolean ajustandoCasillas = false;
    private Optional<YearMonth> resultado = Optional.empty();

    /**
     * Prepara las listas de formato y los avisos de cambio de cada lista
     */
    @FXML
    private void initialize() {
        cmbFormatoFecha.getItems().setAll(FormatoFecha.values());
        cmbFormatoFecha.setConverter(convertidor(FormatoFecha::getEtiqueta));
        cmbDecimal.getItems().setAll(SeparadorDecimal.values());
        cmbDecimal.setConverter(convertidor(SeparadorDecimal::getEtiqueta));
        cmbSigno.getItems().setAll(SignoGasto.values());
        cmbSigno.setConverter(convertidor(SignoGasto::getEtiqueta));

        for (ComboBox<OpcionColumna> combo : List.of(cmbFecha, cmbConcepto, cmbImporte, cmbCategoria, cmbSubcategoria, cmbNota, cmbSaldo)) {
            combo.valueProperty().addListener((obs, antes, ahora) -> recalcular());
        }
        cmbFormatoFecha.valueProperty().addListener((obs, antes, ahora) -> recalcular());
        chkAjustarSaldo.selectedProperty().addListener((obs, antes, ahora) -> actualizarResumen());
        cmbDecimal.valueProperty().addListener((obs, antes, ahora) -> recalcular());
        cmbSigno.valueProperty().addListener((obs, antes, ahora) -> recalcular());
        // Cambiar la fila de títulos cambia los nombres de las columnas, así que se vuelven a proponer
        cmbTitulos.valueProperty().addListener((obs, antes, ahora) -> {
            if (!cargando && ahora != null) {
                filaTitulos = ahora.fila();
                cargando = true;
                poblarColumnas();
                seleccionarColumnas(servicio.sugerirColumnas(archivo, filaTitulos));
                cargando = false;
                lblReconocido.setText("");
                recalcular();
            }
        });
    }

    /**
     * Pide el archivo (primer paso) y, si se elige uno, abre la ventana de columnas y vista previa y espera a que se cierre.
     * @param propietario: ventana desde la que se abre (queda bloqueada hasta que se cierre)
     * @param usuario: usuario de la sesión
     * @param cuenta: cuenta a la que se importa
     * @return Devuelve el mes del movimiento más reciente de los importados, o vacío si no se ha importado nada
     */
    public static Optional<YearMonth> mostrar(Window propietario, Usuario usuario, Cuenta cuenta) {
        Optional<ArchivoImportado> elegido = ImportarArchivoController.elegir(propietario, cuenta);
        if (elegido.isEmpty()) {
            return Optional.empty();
        }
        try {
            FXMLLoader loader = new FXMLLoader(ImportarMovimientosController.class.getResource("/screens/dialogs/ImportarMovimientosDialog.fxml"));
            Parent raiz = loader.load();
            ImportarMovimientosController controlador = loader.getController();
            controlador.preparar(usuario, cuenta, elegido.get());

            Stage ventana = new Stage();
            ventana.initOwner(propietario);
            ventana.initModality(Modality.WINDOW_MODAL);
            ventana.setTitle("MisCuentas · Importar movimientos");
            ventana.setMinWidth(900);
            ventana.setMinHeight(600);
            ventana.setScene(new Scene(raiz));
            ventana.showAndWait();
            return controlador.resultado;
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo abrir la ventana de importar", e);
        }
    }

    private void preparar(Usuario usuario, Cuenta cuenta, ArchivoImportado archivo) {
        this.usuario = usuario;
        this.cuenta = cuenta;
        cargarArchivo(archivo);
    }

    // ===== Archivo y columnas =====

    /**
     * Pone un archivo en la ventana: detecta la fila de títulos, propone las columnas, deja el formato en automático
     * y calcula la vista previa
     * @param nuevo: archivo ya leído
     */
    private void cargarArchivo(ArchivoImportado nuevo) {
        archivo = nuevo;
        lblArchivo.setText(nuevo.nombre());
        filaTitulos = servicio.detectarFilaTitulos(nuevo);

        cargando = true;
        poblarTitulos();
        poblarColumnas();
        Columnas propuesta = servicio.sugerirColumnas(nuevo, filaTitulos);
        seleccionarColumnas(propuesta);
        cmbFormatoFecha.setValue(FormatoFecha.AUTO);
        cmbDecimal.setValue(SeparadorDecimal.AUTO);
        cmbSigno.setValue(SignoGasto.NEGATIVO);
        chkAjustarSaldo.setSelected(false);
        cargando = false;

        lblReconocido.setText(propuesta.completas() ? "Se han reconocido las columnas automáticamente"
                : "No se han podido reconocer todas las columnas: elígelas a mano");
        recalcular();
    }

    /**
     * Rellena la lista de "Fila de títulos" con las primeras filas del archivo
     */
    private void poblarTitulos() {
        List<OpcionTitulos> opciones = new ArrayList<>();
        opciones.add(new OpcionTitulos(-1, "Sin fila de títulos"));
        int limite = Math.min(archivo.filas().size(), FILAS_PARA_TITULOS);
        for (int i = 0; i < limite; i++) {
            StringBuilder resumen = new StringBuilder("Fila " + (i + 1) + ": ");
            int cuantas = 0;
            for (Object celda : archivo.filas().get(i)) {
                if (celda != null && cuantas < 3) {
                    resumen.append(cuantas++ > 0 ? ", " : "").append(celda);
                }
            }
            opciones.add(new OpcionTitulos(i, recortar(resumen.toString(), 48)));
        }
        cmbTitulos.getItems().setAll(opciones);
        cmbTitulos.setValue(opciones.stream().filter(o -> o.fila() == filaTitulos).findFirst().orElse(opciones.get(0)));
    }

    /**
     * Rellena las cuatro listas de columnas con los títulos de las columnas del archivo
     */
    private void poblarColumnas() {
        List<OpcionColumna> opciones = new ArrayList<>();
        opciones.add(new OpcionColumna(-1, "Sin asignar"));
        List<String> nombres = servicio.nombresColumnas(archivo, filaTitulos);
        for (int c = 0; c < nombres.size(); c++) {
            opciones.add(new OpcionColumna(c, recortar(nombres.get(c), 40)));
        }
        for (ComboBox<OpcionColumna> combo : List.of(cmbFecha, cmbConcepto, cmbImporte, cmbCategoria, cmbSubcategoria, cmbNota, cmbSaldo)) {
            combo.getItems().setAll(opciones);
        }
    }

    private void seleccionarColumnas(Columnas columnas) {
        elegir(cmbFecha, columnas.fecha());
        elegir(cmbConcepto, columnas.concepto());
        elegir(cmbImporte, columnas.importe());
        elegir(cmbCategoria, columnas.categoria());
        elegir(cmbSubcategoria, columnas.subcategoria());
        elegir(cmbNota, columnas.nota());
        elegir(cmbSaldo, columnas.saldo());
    }

    private static void elegir(ComboBox<OpcionColumna> combo, int indice) {
        combo.getItems().stream().filter(o -> o.indice() == indice).findFirst().ifPresent(combo::setValue);
    }

    private static int indice(ComboBox<OpcionColumna> combo) {
        return combo.getValue() == null ? -1 : combo.getValue().indice();
    }

    private Columnas columnasElegidas() {
        return new Columnas(indice(cmbFecha), indice(cmbConcepto), indice(cmbImporte), indice(cmbCategoria),
                indice(cmbSubcategoria), indice(cmbNota), indice(cmbSaldo));
    }

    private Formato formatoElegido() {
        return new Formato(cmbFormatoFecha.getValue() == null ? FormatoFecha.AUTO : cmbFormatoFecha.getValue(),
                cmbDecimal.getValue() == null ? SeparadorDecimal.AUTO : cmbDecimal.getValue(),
                cmbSigno.getValue() == null ? SignoGasto.NEGATIVO : cmbSigno.getValue());
    }

    /**
     * Abre o cierra el panel con las opciones de formato
     */
    @FXML
    private void alternarFormato() {
        boolean abrir = !panelFormato.isVisible();
        panelFormato.setVisible(abrir);
        panelFormato.setManaged(abrir);
        enlaceFormato.setText(abrir ? "Ocultar formato" : "Configurar formato");
    }

    /**
     * Pide otro archivo; si se elige, se sustituye el actual y se vuelve a empezar con él
     */
    @FXML
    private void cambiarArchivo() {
        Optional<ArchivoImportado> otro = ImportarArchivoController.elegir(lblArchivo.getScene().getWindow(), cuenta);
        otro.ifPresent(this::cargarArchivo);
    }

    // ===== Vista previa =====

    /**
     * Vuelve a interpretar el archivo con las columnas y el formato elegidos y pinta la vista previa. Los válidos quedan marcados.
     */
    private void recalcular() {
        if (cargando || archivo == null) {
            return;
        }
        ocultarError();
        Columnas columnas = columnasElegidas();
        Formato formato = formatoElegido();
        marcarCamposObligatorios(columnas);
        lblFormato.setText(resumenFormato(columnas, formato));

        filas = List.of();
        String mensaje = null;
        if (!columnas.completas()) {
            mensaje = "Elige las columnas de la fecha, el concepto y el importe para ver la vista previa.";
        } else {
            try {
                filas = servicio.interpretar(usuario, cuenta, archivo, filaTitulos, columnas, formato);
                if (filas.isEmpty()) {
                    mensaje = "No se ha encontrado ningún movimiento con esas columnas. Prueba a cambiar la fila de títulos o las columnas.";
                }
            } catch (ArchivoNoValido e) {
                mensaje = e.getMessage();
            }
        }
        lblMensaje.setText(mensaje == null ? "" : mensaje);
        lblMensaje.setVisible(mensaje != null);
        lblMensaje.setManaged(mensaje != null);

        marcadas.clear();
        for (FilaImportada fila : filas) {
            if (fila.getEstado() == Estado.VALIDO) {
                marcadas.add(fila);
            }
        }
        // Si el archivo trae saldo, se calcula lo que la cuenta ya suma a esa fecha para poder ofrecer el ajuste
        saldoArchivo = servicio.saldoDelArchivo(filas);
        saldoBase = saldoArchivo == null ? null : servicio.saldoBase(cuenta, saldoArchivo.fecha());
        pintarVistaPrevia();
        actualizarResumen();
    }

    /**
     * Marca en rojo las listas de columnas obligatorias que están sin elegir
     */
    private void marcarCamposObligatorios(Columnas columnas) {
        marcarError(cmbFecha, columnas.fecha() < 0);
        marcarError(cmbConcepto, columnas.concepto() < 0);
        marcarError(cmbImporte, columnas.importe() < 0);
    }

    private static void marcarError(ComboBox<?> combo, boolean error) {
        combo.getStyleClass().remove("combo-error");
        if (error) {
            combo.getStyleClass().add("combo-error");
        }
    }

    /**
     * @return Devuelve la línea bajo las columnas: "Fecha: DD/MM/AAAA · Decimal: coma (,) · Importe negativo = gasto"
     */
    private String resumenFormato(Columnas columnas, Formato formato) {
        FormatoFecha fecha = formato.fecha() == FormatoFecha.AUTO ? servicio.detectarFormatoFecha(archivo, filaTitulos + 1, columnas.fecha()) : formato.fecha();
        SeparadorDecimal decimal = formato.decimal() == SeparadorDecimal.AUTO ? servicio.detectarDecimal(archivo, filaTitulos + 1, columnas.importe()) : formato.decimal();
        return "Fecha: " + fecha.getEtiqueta() + "     Decimal: " + decimal.getCorta() + "     " + formato.signo().getCorta();
    }

    /**
     * Dibuja la cabecera de la tabla y una fila por cada movimiento
     */
    private void pintarVistaPrevia() {
        vistaPrevia.getChildren().clear();
        casillas.clear();
        if (filas.isEmpty()) {
            return;
        }
        vistaPrevia.getChildren().add(cabecera());
        for (FilaImportada fila : filas) {
            vistaPrevia.getChildren().add(filaDe(fila));
        }
    }

    private GridPane nuevaFila(String clase) {
        GridPane fila = new GridPane();
        fila.getStyleClass().add(clase);
        double[] anchos = { 5, 12, 34, 15, 19, 15 };
        for (double ancho : anchos) {
            ColumnConstraints columna = new ColumnConstraints();
            columna.setPercentWidth(ancho);
            columna.setHgrow(Priority.ALWAYS);
            fila.getColumnConstraints().add(columna);
        }
        return fila;
    }

    /**
     * @return Devuelve la fila de títulos de la tabla; su casilla marca o desmarca todos los movimientos que se pueden importar
     */
    private GridPane cabecera() {
        GridPane fila = nuevaFila("tabla-cabecera");
        casillaTodas = new CheckBox();
        casillaTodas.setOnAction(e -> {
            boolean marcar = casillaTodas.isSelected();
            ajustandoCasillas = true;
            for (FilaImportada f : filas) {
                if (f.getEstado() != Estado.ERROR) {
                    CheckBox c = casillas.get(f);
                    if (c != null) {
                        c.setSelected(marcar);
                    }
                    if (marcar) {
                        marcadas.add(f);
                    } else {
                        marcadas.remove(f);
                    }
                }
            }
            ajustandoCasillas = false;
            actualizarResumen();
        });
        fila.add(casillaTodas, 0, 0);
        fila.add(titulo("FECHA", false), 1, 0);
        fila.add(titulo("CONCEPTO", false), 2, 0);
        fila.add(titulo("IMPORTE", true), 3, 0);
        fila.add(titulo("CATEGORÍA", false), 4, 0);
        fila.add(titulo("ESTADO", false), 5, 0);
        return fila;
    }

    private static Label titulo(String texto, boolean derecha) {
        Label etiqueta = new Label(texto);
        etiqueta.getStyleClass().add("tabla-titulo");
        etiqueta.setMaxWidth(Double.MAX_VALUE);
        etiqueta.setAlignment(derecha ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        GridPane.setHalignment(etiqueta, derecha ? HPos.RIGHT : HPos.LEFT);
        return etiqueta;
    }

    /**
     * Dibuja la fila de un movimiento: casilla, fecha, concepto, importe, categoría (que se puede cambiar) y estado
     * @param f: movimiento interpretado
     * @return Devuelve la fila
     */
    private GridPane filaDe(FilaImportada f) {
        GridPane fila = nuevaFila("tabla-fila");
        boolean error = f.getEstado() == Estado.ERROR;

        CheckBox casilla = new CheckBox();
        casilla.setSelected(marcadas.contains(f));
        casilla.setDisable(error);
        casilla.selectedProperty().addListener((obs, antes, ahora) -> {
            if (ajustandoCasillas) {
                return;
            }
            if (ahora) {
                marcadas.add(f);
            } else {
                marcadas.remove(f);
            }
            actualizarResumen();
        });
        casillas.put(f, casilla);
        fila.add(casilla, 0, 0);

        fila.add(celda(f.getFecha() == null ? "—" : FECHA.format(f.getFecha()), "tabla-fecha", false), 1, 0);
        fila.add(celdaConcepto(f), 2, 0);
        if (f.getImporte() == null) {
            fila.add(celda("—", "tabla-texto", true), 3, 0);
        } else {
            fila.add(celda(util.Formato.monedaConSigno(f.getImporteConSigno(), usuario.getMoneda()),
                    "ingreso".equals(f.getTipo()) ? "importe-ingreso" : "importe-gasto", true), 3, 0);
        }
        fila.add(error ? celda("—", "tabla-texto", false) : celdaCategoria(f), 4, 0);
        fila.add(estado(f), 5, 0);
        return fila;
    }

    /**
     * @return Devuelve el concepto y, debajo y más pequeña, la nota si el movimiento la tiene
     */
    private static VBox celdaConcepto(FilaImportada f) {
        Label concepto = new Label(f.getConcepto());
        concepto.getStyleClass().add("tabla-concepto");
        concepto.setMaxWidth(Double.MAX_VALUE);
        VBox caja = new VBox(2, concepto);
        caja.setAlignment(Pos.CENTER_LEFT);
        if (f.getNota() != null) {
            Label nota = new Label(f.getNota());
            nota.getStyleClass().add("texto-suave-pequeno");
            nota.setMaxWidth(Double.MAX_VALUE);
            nota.setTooltip(new Tooltip(f.getNota()));
            caja.getChildren().add(nota);
        }
        return caja;
    }

    private static Label celda(String texto, String clase, boolean derecha) {
        Label etiqueta = new Label(texto);
        etiqueta.getStyleClass().add(clase);
        etiqueta.setMaxWidth(Double.MAX_VALUE);
        etiqueta.setAlignment(derecha ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        GridPane.setHalignment(etiqueta, derecha ? HPos.RIGHT : HPos.LEFT);
        return etiqueta;
    }

    /**
     * Crea la categoría de una fila: se ve su nombre (y si se creará nueva) y, debajo, su subcategoría si la tiene. Al
     * pulsarla sale la lista de categorías de su tipo para cambiarla.
     */
    private VBox celdaCategoria(FilaImportada f) {
        Label nombre = new Label();
        nombre.getStyleClass().add("importar-categoria");
        nombre.setMaxWidth(Double.MAX_VALUE);
        Label subcategoria = new Label();
        subcategoria.getStyleClass().add("texto-suave-pequeno");
        subcategoria.setMaxWidth(Double.MAX_VALUE);
        VBox caja = new VBox(2, nombre, subcategoria);
        caja.setAlignment(Pos.CENTER_LEFT);
        caja.setCursor(javafx.scene.Cursor.HAND);
        Tooltip.install(caja, new Tooltip("Pulsa para cambiar la categoría"));
        textoCategoria(nombre, subcategoria, f);
        caja.setOnMouseClicked(e -> {
            ContextMenu menu = new ContextMenu();
            List<Categoria> categorias = categoriasPorTipo.computeIfAbsent(f.getTipo(), t -> servicio.categoriasDeTipo(usuario, t));
            for (Categoria c : categorias) {
                boolean conSub = c.getSubcategoria() != null && !c.getSubcategoria().isBlank();
                MenuItem opcion = new MenuItem(conSub ? c.getNombre() + " · " + c.getSubcategoria() : c.getNombre());
                opcion.setOnAction(a -> {
                    f.setCategoria(c);
                    textoCategoria(nombre, subcategoria, f);
                    actualizarResumen();
                });
                menu.getItems().add(opcion);
            }
            menu.show(caja, javafx.geometry.Side.BOTTOM, 0, 4);
        });
        return caja;
    }

    private static void textoCategoria(Label nombre, Label subcategoria, FilaImportada f) {
        nombre.setText(f.esCategoriaNueva() ? f.getNombreCategoria() + " · nueva" : f.getNombreCategoria());
        nombre.getStyleClass().remove("importar-categoria-nueva");
        if (f.esCategoriaNueva()) {
            nombre.getStyleClass().add("importar-categoria-nueva");
        }
        String sub = f.getSubcategoria();
        subcategoria.setText(sub == null ? "" : sub);
        subcategoria.setVisible(sub != null);
        subcategoria.setManaged(sub != null);
    }

    /**
     * @return Devuelve el estado de la fila: Válido, Duplicado o el motivo del error, con su icono
     */
    private HBox estado(FilaImportada f) {
        String icono;
        String clase;
        String texto;
        switch (f.getEstado()) {
            case VALIDO -> {
                icono = ICONO_VALIDO;
                clase = "valido";
                texto = "Válido";
            }
            case DUPLICADO -> {
                icono = ICONO_DUPLICADO;
                clase = "duplicado";
                texto = "Duplicado";
            }
            default -> {
                icono = ICONO_ERROR;
                clase = "error";
                texto = f.getMotivo();
            }
        }
        SVGPath forma = new SVGPath();
        forma.setContent(icono);
        forma.getStyleClass().addAll("icono", "estado-icono-" + clase);
        forma.setScaleX(ESCALA_ICONO);
        forma.setScaleY(ESCALA_ICONO);
        Label etiqueta = new Label(texto);
        etiqueta.getStyleClass().add("estado-" + clase);
        HBox caja = new HBox(5, new Group(forma), etiqueta);
        caja.setAlignment(Pos.CENTER_LEFT);
        if (f.getEstado() == Estado.DUPLICADO) {
            Tooltip.install(caja, new Tooltip(f.getMotivo() + ". Márcalo si quieres importarlo igualmente."));
        }
        return caja;
    }

    /**
     * Actualiza lo que depende de las filas marcadas: el recuento de válidos, los avisos, el resumen de abajo y el botón de importar
     */
    private void actualizarResumen() {
        long validos = filas.stream().filter(f -> f.getEstado() == Estado.VALIDO).count();
        long duplicados = filas.stream().filter(f -> f.getEstado() == Estado.DUPLICADO).count();
        long errores = filas.stream().filter(f -> f.getEstado() == Estado.ERROR).count();
        pastillaValidos.setText(validos + " de " + filas.size() + " válidos");
        List<String> avisos = new ArrayList<>();
        if (duplicados > 0) {
            avisos.add(duplicados + (duplicados == 1 ? " duplicado" : " duplicados"));
        }
        if (errores > 0) {
            avisos.add(errores + (errores == 1 ? " con error" : " con errores"));
        }
        lblAvisos.setText(String.join(" · ", avisos));

        int n = marcadas.size();
        Set<String> nuevas = new LinkedHashSet<>();
        for (FilaImportada f : filas) {
            if (marcadas.contains(f) && f.esCategoriaNueva()) {
                nuevas.add(f.getSubcategoria() == null ? f.getNombreCategoria() : f.getNombreCategoria() + " · " + f.getSubcategoria());
            }
        }
        String texto = n + (n == 1 ? " movimiento seleccionado" : " movimientos seleccionados");
        if (!nuevas.isEmpty()) {
            texto += " · " + (nuevas.size() == 1 ? "Se creará 1 categoría nueva" : "Se crearán " + nuevas.size() + " categorías nuevas")
                    + " (" + String.join(", ", nuevas) + ")";
        }
        if (chkAjustarSaldo.isSelected()) {
            texto += " · Se ajustará el saldo de la cuenta";
        }
        lblSeleccion.setText(texto);
        btnImportar.setText(n == 0 ? "Importar" : "Importar " + n + (n == 1 ? " movimiento" : " movimientos"));
        btnImportar.setDisable(n == 0);

        actualizarSaldo();
        if (casillaTodas != null) {
            ajustandoCasillas = true;
            long seleccionables = filas.stream().filter(f -> f.getEstado() != Estado.ERROR).count();
            casillaTodas.setSelected(seleccionables > 0 && marcadas.size() == seleccionables);
            ajustandoCasillas = false;
        }
        // Se actualiza la información del archivo con las filas que ha encontrado
        lblArchivoDetalle.setText(tamano(archivo.bytes()) + " · " + archivo.tipo().getEtiqueta()
                + (archivo.hoja() != null ? " · hoja «" + archivo.hoja() + "»" : "")
                + " · " + filas.size() + (filas.size() == 1 ? " fila" : " filas")
                + " · Destino: " + cuenta.getNombre() + " · Aún no importado");
    }

    /**
     * Enseña u oculta la tarjeta del saldo. Solo se ve si el archivo trae saldo. Explica lo que pasaría con la cuenta a la
     * fecha del saldo del archivo según los movimientos marcados, y ofrece ajustar el saldo inicial de la cuenta para que
     * coincida con el del archivo. Si ya coincide, no hace falta y la casilla se desactiva.
     */
    private void actualizarSaldo() {
        boolean hay = saldoArchivo != null && saldoBase != null;
        cajaSaldo.setVisible(hay);
        cajaSaldo.setManaged(hay);
        if (!hay) {
            chkAjustarSaldo.setSelected(false);
            return;
        }
        List<FilaImportada> elegidas = filas.stream().filter(marcadas::contains).toList();
        BigDecimal prevision = saldoBase.add(ImportacionService.sumaHasta(elegidas, saldoArchivo.fecha()));
        BigDecimal diferencia = saldoArchivo.saldo().subtract(prevision);
        String moneda = cuenta.getMoneda();
        LocalDate fecha = saldoArchivo.fecha();
        chkAjustarSaldo.setText("Ajustar el saldo de «" + cuenta.getNombre() + "» al del archivo: "
                + util.Formato.moneda(saldoArchivo.saldo(), moneda) + " (a " + FECHA.format(fecha) + ")");
        if (diferencia.signum() == 0) {
            chkAjustarSaldo.setSelected(false);
            chkAjustarSaldo.setDisable(true);
            lblSaldoDetalle.setText("Con los movimientos marcados, la cuenta ya tendría ese saldo a esa fecha: no hace falta ajustarlo.");
        } else {
            chkAjustarSaldo.setDisable(false);
            lblSaldoDetalle.setText("Con los movimientos marcados, la cuenta tendría " + util.Formato.moneda(prevision, moneda) + " a esa fecha. "
                    + "Si lo ajustas, el saldo inicial de la cuenta cambia en " + util.Formato.monedaConSigno(diferencia, moneda)
                    + " para que sean " + util.Formato.moneda(saldoArchivo.saldo(), moneda) + ". Si no, solo se añaden los movimientos y el saldo se calcula con ellos.");
        }
    }

    // ===== Importar =====

    /**
     * Guarda los movimientos marcados (y crea las categorías nuevas) y cierra la ventana. Si falla, no se guarda ninguno.
     */
    @FXML
    private void importar() {
        ocultarError();
        List<FilaImportada> seleccion = filas.stream().filter(marcadas::contains).toList();
        if (seleccion.isEmpty()) {
            return;
        }
        btnImportar.setDisable(true);
        btnImportar.setText("Importando…");
        try {
            ImportacionService.SaldoArchivo ajuste = chkAjustarSaldo.isSelected() && !chkAjustarSaldo.isDisabled() ? saldoArchivo : null;
            Resultado importado = servicio.importar(usuario, cuenta, seleccion, ajuste);
            resultado = Optional.of(importado.ultimoMes());
            cerrar();
        } catch (RuntimeException e) {
            mostrarError("No se pudo importar: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())
                    + ". No se ha guardado ningún movimiento.");
            actualizarResumen();
        }
    }

    /**
     * Cierra la ventana sin importar nada
     */
    @FXML
    private void cancelar() {
        resultado = Optional.empty();
        cerrar();
    }

    private void cerrar() {
        ((Stage) lblArchivo.getScene().getWindow()).close();
    }

    // ===== Piezas =====

    private static <T> StringConverter<T> convertidor(Function<T, String> texto) {
        return new StringConverter<>() {
            @Override
            public String toString(T valor) {
                return valor == null ? "" : texto.apply(valor);
            }

            @Override
            public T fromString(String cadena) {
                return null;
            }
        };
    }

    private static String recortar(String texto, int max) {
        return texto.length() > max ? texto.substring(0, max - 1) + "…" : texto;
    }

    /**
     * @param bytes: tamaño de un archivo
     * @return Devuelve el tamaño escrito como lo ve el usuario: 812 B, 2,4 KB, 1,3 MB
     */
    private static String tamano(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.forLanguageTag("es-ES"), "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.forLanguageTag("es-ES"), "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private void mostrarError(String texto) {
        lblError.setText(texto);
        lblError.setVisible(true);
        lblError.setManaged(true);
    }

    private void ocultarError() {
        lblError.setText("");
        lblError.setVisible(false);
        lblError.setManaged(false);
    }
}
