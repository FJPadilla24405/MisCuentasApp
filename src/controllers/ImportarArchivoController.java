package controllers;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import models.Cuenta;
import service.importar.ArchivoImportado;
import service.importar.ArchivoNoValido;
import service.importar.LectorArchivo;

/**
 * Controlador de ImportarArchivoDialog.fxml: primer paso de importar movimientos. Se elige el archivo (arrastrándolo a
 * la ventana o con el explorador de archivos); se lee y se comprueba que se puede importar, y si no, la ventana se
 * queda abierta diciendo el motivo para elegir otro. Se abre con ImportarArchivoController.elegir(...).
 *
 * @author Fco Padilla
 */
public class ImportarArchivoController {

    @FXML private Label lblDestino;
    @FXML private VBox zona;
    @FXML private Label lblZona;
    @FXML private Label lblFormatos;
    @FXML private Button btnSeleccionar;
    @FXML private Label lblError;

    private Optional<ArchivoImportado> resultado = Optional.empty();
    private boolean leyendo = false;

    /**
     * Prepara la zona de arrastrar archivos
     */
    @FXML
    private void initialize() {
        zona.setOnDragOver(e -> {
            if (!leyendo && e.getDragboard().hasFiles()) {
                e.acceptTransferModes(TransferMode.COPY);
            }
            e.consume();
        });
        zona.setOnDragEntered(e -> {
            if (!leyendo && e.getDragboard().hasFiles() && !zona.getStyleClass().contains("zona-archivo-activa")) {
                zona.getStyleClass().add("zona-archivo-activa");
            }
        });
        zona.setOnDragExited(e -> zona.getStyleClass().remove("zona-archivo-activa"));
        zona.setOnDragDropped(this::soltar);
    }

    /**
     * Abre la ventana y espera a que se cierre.
     * @param propietario: ventana desde la que se abre (queda bloqueada hasta que se cierre)
     * @param destino: cuenta a la que se importarán los movimientos
     * @return Devuelve el archivo ya leído y comprobado, o vacío si se canceló
     */
    public static Optional<ArchivoImportado> elegir(Window propietario, Cuenta destino) {
        try {
            FXMLLoader loader = new FXMLLoader(ImportarArchivoController.class.getResource("/screens/dialogs/ImportarArchivoDialog.fxml"));
            Parent raiz = loader.load();
            ImportarArchivoController controlador = loader.getController();
            controlador.lblDestino.setText("Selecciona el archivo CSV o Excel que has descargado de tu banco. "
                    + "Los movimientos se añadirán a la cuenta «" + destino.getNombre() + "».");

            Stage ventana = new Stage();
            ventana.initOwner(propietario);
            ventana.initModality(Modality.WINDOW_MODAL);
            ventana.setTitle("MisCuentas · Importar movimientos");
            ventana.setResizable(false);
            ventana.setScene(new Scene(raiz));
            ventana.showAndWait();
            return controlador.resultado;
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo abrir la ventana de importar", e);
        }
    }

    /**
     * Abre el explorador de archivos para elegir el archivo
     */
    @FXML
    private void seleccionar() {
        if (leyendo) {
            return;
        }
        FileChooser selector = new FileChooser();
        selector.setTitle("Elegir archivo de movimientos");
        selector.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Movimientos (CSV, Excel)", "*.csv", "*.xls", "*.xlsx", "*.txt"),
                new FileChooser.ExtensionFilter("Todos los archivos", "*.*"));
        File carpeta = new File(System.getProperty("user.home", ""), "Downloads");
        if (!carpeta.isDirectory()) {
            carpeta = new File(System.getProperty("user.home", ""), "Descargas");
        }
        if (carpeta.isDirectory()) {
            selector.setInitialDirectory(carpeta);
        }
        File archivo = selector.showOpenDialog(zona.getScene().getWindow());
        if (archivo != null) {
            procesar(archivo);
        }
    }

    /**
     * Se suelta un archivo sobre la zona: se usa el primero
     * @param evento: evento de soltar
     */
    private void soltar(DragEvent evento) {
        List<File> archivos = evento.getDragboard().hasFiles() ? evento.getDragboard().getFiles() : List.of();
        evento.setDropCompleted(!archivos.isEmpty());
        evento.consume();
        zona.getStyleClass().remove("zona-archivo-activa");
        if (!archivos.isEmpty() && !leyendo) {
            procesar(archivos.get(0));
        }
    }

    /**
     * Lee y comprueba el archivo sin bloquear la ventana. Si es válido, se cierra; si no, se cuenta el motivo.
     * @param archivo: archivo elegido
     */
    private void procesar(File archivo) {
        ocultarError();
        ocupado(true);
        Task<ArchivoImportado> tarea = new Task<>() {
            @Override
            protected ArchivoImportado call() throws ArchivoNoValido {
                return LectorArchivo.leer(archivo.toPath());
            }
        };
        tarea.setOnSucceeded(e -> {
            resultado = Optional.of(tarea.getValue());
            cerrar();
        });
        tarea.setOnFailed(e -> {
            ocupado(false);
            Throwable causa = tarea.getException();
            mostrarError(causa instanceof ArchivoNoValido ? causa.getMessage()
                    : "No se ha podido leer «" + archivo.getName() + "». Prueba con otro archivo.");
        });
        Thread hilo = new Thread(tarea, "lector-archivo");
        hilo.setDaemon(true);
        hilo.start();
    }

    /**
     * Cambia la zona mientras se lee el archivo
     * @param ocupado: true mientras se lee
     */
    private void ocupado(boolean ocupado) {
        leyendo = ocupado;
        btnSeleccionar.setDisable(ocupado);
        lblZona.setText(ocupado ? "Leyendo el archivo…" : "Arrastra aquí tu archivo");
        lblFormatos.setVisible(!ocupado);
    }

    /**
     * Cierra la ventana sin elegir archivo
     */
    @FXML
    private void cancelar() {
        resultado = Optional.empty();
        cerrar();
    }

    private void cerrar() {
        ((Stage) zona.getScene().getWindow()).close();
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
