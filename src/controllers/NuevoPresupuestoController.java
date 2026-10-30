package controllers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;
import models.Categoria;
import models.Cuenta;
import models.Presupuesto;
import models.Usuario;
import service.presupuestos.PresupuestoService;
import service.presupuestos.PresupuestoService.DatoInvalido;
import util.Campos;
import util.Formato;
import util.Importes;

/**
 * Controlador de NuevoPresupuestoDialog.fxml: ventana para crear un presupuesto (nombre, categoría, importe
 * límite, mes y año, y qué cuentas cuentan). Se abre con NuevoPresupuestoController.mostrar(...). Las reglas
 * (un solo presupuesto por categoría y mes, límite mayor que cero...) las comprueba PresupuestoService.
 *
 * @author Fco Padilla
 */
public class NuevoPresupuestoController {

    // Años que se pueden elegir antes y después del actual
    private static final int ANIOS_ATRAS = 2;
    private static final int ANIOS_ADELANTE = 2;

    @FXML private HBox cajaNombre;
    @FXML private HBox cajaImporte;
    @FXML private TextField txtNombre;
    @FXML private TextField txtImporte;
    @FXML private Label lblMoneda;
    @FXML private Label lblPeriodo;
    @FXML private Label lblAlcance;
    @FXML private Label lblError;
    @FXML private ComboBox<Categoria> cmbCategoria;
    @FXML private ComboBox<Integer> cmbMes;
    @FXML private ComboBox<Integer> cmbAnio;
    @FXML private ToggleGroup grupoAlcance;
    @FXML private ToggleButton tglTodas;
    @FXML private ToggleButton tglBanco;
    @FXML private ToggleButton tglEfectivo;

    private final PresupuestoService servicio = new PresupuestoService();
    private Usuario usuario;
    private Presupuesto creado;
    // Cuentas que entran en cada alcance, para el texto de ayuda
    private List<Cuenta> cuentasTodas = List.of();
    private List<Cuenta> cuentasBanco = List.of();
    private List<Cuenta> cuentasEfectivo = List.of();
    // Último nombre que se rellenó solo al elegir categoría; si el usuario lo cambia, ya no se vuelve a tocar
    private String nombreAutomatico = "";

    /**
     * Prepara los campos: textos de las listas, resaltado de las cajas y avisos que se actualizan al cambiar algo
     */
    @FXML
    private void initialize() {
        cmbCategoria.setConverter(new StringConverter<>() {
            @Override
            public String toString(Categoria c) {
                if (c == null) {
                    return "";
                }
                String sub = c.getSubcategoria();
                return sub == null || sub.isBlank() ? c.getNombre() : c.getNombre() + " · " + sub;
            }

            @Override
            public Categoria fromString(String texto) {
                return null;
            }
        });
        cmbMes.setConverter(new StringConverter<>() {
            @Override
            public String toString(Integer mes) {
                return mes == null ? "" : nombreMesConMayuscula(mes);
            }

            @Override
            public Integer fromString(String texto) {
                return null;
            }
        });
        for (int m = 1; m <= 12; m++) {
            cmbMes.getItems().add(m);
        }

        Campos.configurar(cajaNombre, txtNombre);
        Campos.configurar(cajaImporte, txtImporte);
        cmbCategoria.valueProperty().addListener((obs, antes, ahora) -> {
            cmbCategoria.getStyleClass().remove("error");
            sugerirNombre(ahora);
        });
        cmbMes.valueProperty().addListener((obs, antes, ahora) -> actualizarPeriodo());
        cmbAnio.valueProperty().addListener((obs, antes, ahora) -> actualizarPeriodo());
        // Siempre tiene que haber una opción de alcance elegida
        grupoAlcance.selectedToggleProperty().addListener((obs, antes, ahora) -> {
            if (ahora == null) {
                grupoAlcance.selectToggle(antes);
            } else {
                actualizarAlcance();
            }
        });
        Platform.runLater(txtNombre::requestFocus);
    }

    /**
     * Abre la ventana y espera a que se cierre.
     * @param propietario: ventana desde la que se abre (queda bloqueada hasta que se cierre)
     * @param usuario: dueño del presupuesto que se va a crear
     * @param mesInicial: mes que aparece elegido al abrirla
     * @return Devuelve el presupuesto creado, o vacío si se canceló
     */
    public static Optional<Presupuesto> mostrar(Window propietario, Usuario usuario, YearMonth mesInicial) {
        try {
            FXMLLoader loader = new FXMLLoader(NuevoPresupuestoController.class.getResource("/screens/dialogs/NuevoPresupuestoDialog.fxml"));
            Parent raiz = loader.load();
            NuevoPresupuestoController controlador = loader.getController();
            controlador.preparar(usuario, mesInicial);

            Stage ventana = new Stage();
            ventana.initOwner(propietario);
            ventana.initModality(Modality.WINDOW_MODAL);
            ventana.setTitle("MisCuentas · Añadir presupuesto");
            ventana.setResizable(false);
            ventana.setScene(new Scene(raiz));
            ventana.showAndWait();
            return Optional.ofNullable(controlador.creado);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo abrir la ventana de nuevo presupuesto", e);
        }
    }

    /**
     * Rellena la ventana con los datos del usuario: categorías, moneda, mes elegido y cuentas de cada alcance
     * @param usuario: dueño del presupuesto
     * @param mesInicial: mes que aparece elegido
     */
    private void preparar(Usuario usuario, YearMonth mesInicial) {
        this.usuario = usuario;
        lblMoneda.setText(usuario.getMoneda() + " " + Formato.simbolo(usuario.getMoneda()));
        cmbCategoria.getItems().setAll(servicio.categoriasDeGasto(usuario));

        int actual = YearMonth.now().getYear();
        int desde = Math.min(actual - ANIOS_ATRAS, mesInicial.getYear());
        int hasta = Math.max(actual + ANIOS_ADELANTE, mesInicial.getYear());
        for (int a = desde; a <= hasta; a++) {
            cmbAnio.getItems().add(a);
        }
        cmbMes.setValue(mesInicial.getMonthValue());
        cmbAnio.setValue(mesInicial.getYear());

        cuentasTodas = servicio.cuentasDelAlcance(usuario, Presupuesto.ALCANCE_TODAS);
        cuentasBanco = servicio.cuentasDelAlcance(usuario, Presupuesto.ALCANCE_BANCO);
        cuentasEfectivo = servicio.cuentasDelAlcance(usuario, Presupuesto.ALCANCE_EFECTIVO);
        // No se puede elegir un tipo de cuenta que el usuario no tiene
        tglBanco.setDisable(cuentasBanco.isEmpty());
        tglEfectivo.setDisable(cuentasEfectivo.isEmpty());
        actualizarAlcance();
    }

    /**
     * Valida los datos y crea el presupuesto
     */
    @FXML
    private void guardar() {
        limpiarErrores();

        // El importe se lee aquí porque puede estar mal escrito antes de que el servicio llegue a mirarlo
        BigDecimal limite = null;
        String errorImporte = null;
        if (txtImporte.getText().isBlank()) {
            errorImporte = "Escribe el importe límite.";
        } else {
            try {
                limite = Importes.leer(txtImporte.getText());
            } catch (NumberFormatException e) {
                errorImporte = "El importe límite no es un número válido. Ejemplo: 350,00";
            }
        }

        try {
            YearMonth mes = YearMonth.of(cmbAnio.getValue(), cmbMes.getValue());
            creado = servicio.crear(usuario, txtNombre.getText(), cmbCategoria.getValue(), limite, mes, alcanceElegido());
            cerrar();
        } catch (DatoInvalido e) {
            String mensaje = e.getMessage();
            switch (e.getCampo()) {
                case "nombre" -> Campos.marcarError(cajaNombre, true);
                case "categoria" -> cmbCategoria.getStyleClass().add("error");
                default -> {
                    Campos.marcarError(cajaImporte, true);
                    if (errorImporte != null) {
                        mensaje = errorImporte;
                    }
                }
            }
            mostrarError(mensaje);
        }
    }

    /**
     * Cierra la ventana sin crear nada
     */
    @FXML
    private void cancelar() {
        cerrar();
    }

    /**
     * Al elegir una categoría, propone el nombre "Categoría del mes" si el usuario no ha escrito otro
     * @param categoria: categoría elegida
     */
    private void sugerirNombre(Categoria categoria) {
        if (categoria == null) {
            return;
        }
        String actual = txtNombre.getText();
        if (actual == null || actual.isBlank() || actual.equals(nombreAutomatico)) {
            nombreAutomatico = categoria.getNombre() + " del mes";
            txtNombre.setText(nombreAutomatico);
        }
    }

    /**
     * Escribe bajo el mes y el año los días que abarca el presupuesto: "Del 1 al 30 de septiembre de 2026."
     */
    private void actualizarPeriodo() {
        if (cmbMes.getValue() == null || cmbAnio.getValue() == null) {
            lblPeriodo.setText("");
            return;
        }
        YearMonth mes = YearMonth.of(cmbAnio.getValue(), cmbMes.getValue());
        lblPeriodo.setText("Del 1 al " + mes.lengthOfMonth() + " de " + Formato.nombreMes(mes) + " de " + mes.getYear() + ".");
    }

    /**
     * Escribe bajo las opciones de alcance qué cuentas cuentan: "Incluye los gastos de BBVA, Santander y Efectivo en esta categoría."
     */
    private void actualizarAlcance() {
        List<Cuenta> cuentas = switch (alcanceElegido()) {
            case Presupuesto.ALCANCE_BANCO -> cuentasBanco;
            case Presupuesto.ALCANCE_EFECTIVO -> cuentasEfectivo;
            default -> cuentasTodas;
        };
        if (cuentas.isEmpty()) {
            lblAlcance.setText("Aún no tienes cuentas. El presupuesto no contará ningún gasto hasta que añadas una.");
            return;
        }
        lblAlcance.setText("Incluye los gastos de " + Formato.enumerar(cuentas.stream().map(Cuenta::getNombre).toList())
                + " en esta categoría.");
    }

    /**
     * @return Devuelve el alcance marcado: "todas", "banco" o "efectivo"
     */
    private String alcanceElegido() {
        Object marcado = grupoAlcance.getSelectedToggle();
        if (marcado == tglBanco) {
            return Presupuesto.ALCANCE_BANCO;
        }
        if (marcado == tglEfectivo) {
            return Presupuesto.ALCANCE_EFECTIVO;
        }
        return Presupuesto.ALCANCE_TODAS;
    }

    /**
     * @param mes: número de mes (1 a 12)
     * @return Devuelve el nombre del mes con la primera letra en mayúscula: Septiembre
     */
    private static String nombreMesConMayuscula(int mes) {
        String nombre = Formato.nombreMes(YearMonth.of(2000, mes));
        return nombre.substring(0, 1).toUpperCase(Locale.forLanguageTag("es-ES")) + nombre.substring(1);
    }

    /**
     * Cierra la ventana
     */
    private void cerrar() {
        ((Stage) txtNombre.getScene().getWindow()).close();
    }

    /**
     * Quita el texto y los bordes rojos de un intento anterior
     */
    private void limpiarErrores() {
        lblError.setText("");
        lblError.setVisible(false);
        lblError.setManaged(false);
        Campos.marcarError(cajaNombre, false);
        Campos.marcarError(cajaImporte, false);
        cmbCategoria.getStyleClass().remove("error");
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
}
