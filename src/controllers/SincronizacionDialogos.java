package controllers;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import models.Usuario;
import service.auth.AuthService;
import service.auth.SesionActual;
import service.dao.UsuarioDAO;
import service.sync.FirebaseAuthClient.AuthException;
import service.sync.SincronizacionService;

/**
 * Los diálogos y el trabajo en segundo plano de la sincronización con Firebase: conectar, subir datos, descargarlos
 * y el aviso al cerrar la aplicación con cambios sin subir. Lo usan la barra lateral (botones) y Main (cierre).
 * Mientras se habla con Firebase la ventana queda bloqueada, para que nadie toque la base de datos a medias.
 *
 * @author Fco Padilla
 */
public final class SincronizacionDialogos {

    private static final String CSS = "/screens/styles/application.css";

    // No se instancia
    private SincronizacionDialogos() {
    }

    /**
     * Conecta con Firebase una cuenta que entró sin conexión: pide la contraseña, inicia sesión en Firebase y,
     * si va bien, deja la sesión conectada. En una cuenta solo local explica que no se puede.
     * @param owner: ventana sobre la que se muestran los diálogos
     * @param alTerminar: se ejecuta cuando se ha conectado, para que la pantalla se actualice
     */
    public static void conectar(Window owner, Runnable alTerminar) {
        Usuario usuario = SesionActual.getUsuario();
        if (usuario == null) {
            return;
        }
        if (usuario.isSoloLocal()) {
            informar(owner, "Cuenta solo local", "Esta cuenta solo existe en este equipo y no tiene cuenta en Firebase, "
                    + "así que no se puede conectar ni sincronizar.");
            return;
        }
        Optional<String> contrasena = pedirContrasena(owner, usuario);
        if (contrasena.isEmpty()) {
            return;
        }
        ejecutar(owner, tarea(() -> new AuthService().iniciarSesion(usuario.getEmail(), contrasena.get(), false)), resultado -> {
            switch (resultado.estado()) {
                case ONLINE -> {
                    SesionActual.iniciar(resultado.usuario(), resultado.sesion());
                    alTerminar.run();
                }
                case SIN_VERIFICAR -> error(owner, "No se pudo conectar", resultado.detalle() != null ? resultado.detalle()
                        : "No hay conexión con Firebase. Comprueba tu conexión a internet y vuelve a intentarlo.");
                case RECHAZADA_POR_FIREBASE -> error(owner, "No se pudo conectar",
                        "Firebase no acepta esa contraseña (puede que se cambiara en otro sitio). Cierra sesión e inicia sesión con la nueva.");
                case CREDENCIALES_INCORRECTAS -> error(owner, "No se pudo conectar", "La contraseña no es correcta.");
                default -> error(owner, "No se pudo conectar", resultado.detalle() != null ? resultado.detalle() : "Error desconocido.");
            }
        });
    }

    /**
     * Sube los datos locales a Firebase después de pedir confirmación
     * @param owner: ventana sobre la que se muestran los diálogos
     * @param alTerminar: se ejecuta cuando se han subido los datos
     */
    public static void subir(Window owner, Runnable alTerminar) {
        Usuario usuario = SesionActual.getUsuario();
        if (usuario == null || !SesionActual.isConectado()) {
            return;
        }
        if (!confirmar(owner, "¿Subir tus datos a Firebase?",
                "Los datos de este equipo reemplazarán a los que hay ahora en Firebase.", "Subir")) {
            return;
        }
        ejecutar(owner, tarea(() -> {
            new SincronizacionService().subir(usuario);
            return Boolean.TRUE;
        }), ok -> {
            informar(owner, "Datos subidos", "Tus datos se han subido a Firebase.");
            alTerminar.run();
        });
    }

    /**
     * Descarga los datos de Firebase después de pedir confirmación. Si en este equipo hay cambios que no se han
     * subido (es decir, lo que hay en Firebase está desactualizado), pide una segunda confirmación.
     * @param owner: ventana sobre la que se muestran los diálogos
     * @param alTerminar: se ejecuta cuando se han descargado los datos, para que la pantalla se actualice
     */
    public static void descargar(Window owner, Runnable alTerminar) {
        Usuario usuario = SesionActual.getUsuario();
        if (usuario == null || !SesionActual.isConectado()) {
            return;
        }
        if (!confirmar(owner, "¿Descargar los datos de Firebase?",
                "Los datos de Firebase reemplazarán a los de este equipo.", "Descargar")) {
            return;
        }
        boolean desactualizado;
        try {
            desactualizado = new SincronizacionService().hayCambiosSinSubir(usuario);
        } catch (RuntimeException e) {
            desactualizado = false;
        }
        if (desactualizado && !confirmar(owner, "Los datos de Firebase están desactualizados",
                "En este equipo hay cambios que no se han subido a Firebase. Si descargas igualmente, "
                        + "esos cambios se perderán.", "Descargar igualmente")) {
            return;
        }
        ejecutar(owner, tarea(() -> new SincronizacionService().descargar(usuario)), hayDatos -> {
            if (!hayDatos) {
                informar(owner, "No hay datos en Firebase", "Todavía no has subido datos a Firebase, así que no hay nada que descargar. "
                        + "No se ha cambiado nada en este equipo.");
                return;
            }
            // La descarga puede cambiar el nombre, la moneda o el tema: se vuelve a leer el usuario
            new UsuarioDAO().buscarPorId(usuario.getId()).ifPresent(SesionActual::actualizarUsuario);
            informar(owner, "Datos descargados", "Los datos de Firebase ya están en este equipo.");
            alTerminar.run();
        });
    }

    /**
     * Se llama al intentar cerrar la aplicación. Si hay cambios sin subir a Firebase, pregunta si se quieren subir
     * antes de cerrar o cerrar sin subirlos (o cancelar el cierre); si no, cierra directamente.
     * @param owner: ventana de la aplicación
     * @param cerrar: lo que hay que hacer para cerrar de verdad
     */
    public static void confirmarSalida(Window owner, Runnable cerrar) {
        Usuario usuario = SesionActual.getUsuario();
        if (usuario == null || usuario.isSoloLocal() || !SesionActual.isConectado()) {
            cerrar.run();
            return;
        }
        boolean pendiente;
        try {
            pendiente = new SincronizacionService().hayCambiosSinSubir(usuario);
        } catch (RuntimeException e) {
            pendiente = false;
        }
        if (!pendiente) {
            cerrar.run();
            return;
        }
        ButtonType subir = new ButtonType("Subir y cerrar", ButtonBar.ButtonData.OK_DONE);
        ButtonType sinSubir = new ButtonType("Cerrar sin subir", ButtonBar.ButtonData.OTHER);
        ButtonType cancelar = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert aviso = new Alert(Alert.AlertType.WARNING, "Has hecho cambios que todavía no están en Firebase. "
                + "Si cierras sin subirlos, solo estarán en este equipo.", subir, sinSubir, cancelar);
        aviso.setTitle("MisCuentas");
        aviso.setHeaderText("Tienes datos sin subir a Firebase");
        preparar(aviso, owner);
        aviso.getDialogPane().lookupButton(subir).getStyleClass().add("boton-primario");
        aviso.getDialogPane().lookupButton(sinSubir).getStyleClass().add("boton-secundario");
        aviso.getDialogPane().lookupButton(cancelar).getStyleClass().add("boton-secundario");

        Optional<ButtonType> elegido = aviso.showAndWait();
        if (elegido.isEmpty() || elegido.get() == cancelar) {
            return;
        }
        if (elegido.get() == sinSubir) {
            cerrar.run();
            return;
        }
        // Si la subida falla, se avisa y la aplicación sigue abierta para poder reintentar o cerrar sin subir
        ejecutar(owner, tarea(() -> {
            new SincronizacionService().subir(usuario);
            return Boolean.TRUE;
        }), ok -> cerrar.run());
    }

    // ===== Piezas comunes =====

    /**
     * Crea una tarea que ejecuta algo que tarda
     * @param trabajo: lo que hay que hacer en segundo plano
     * @return Devuelve la tarea
     */
    private static <T> Task<T> tarea(Callable<T> trabajo) {
        return new Task<>() {
            @Override
            protected T call() throws Exception {
                return trabajo.call();
            }
        };
    }

    /**
     * Ejecuta una tarea en segundo plano con la ventana bloqueada y el cursor de espera. Al acabar bien se llama a
     * alTerminar con el resultado; si falla, se enseña el motivo.
     * @param owner: ventana de la aplicación
     * @param tarea: tarea a ejecutar
     * @param alTerminar: qué hacer con el resultado
     */
    private static <T> void ejecutar(Window owner, Task<T> tarea, Consumer<T> alTerminar) {
        Parent raiz = owner.getScene().getRoot();
        raiz.setDisable(true);
        owner.getScene().setCursor(Cursor.WAIT);
        tarea.setOnSucceeded(e -> {
            liberar(owner, raiz);
            alTerminar.accept(tarea.getValue());
        });
        tarea.setOnFailed(e -> {
            liberar(owner, raiz);
            error(owner, "No se pudo completar", motivo(tarea.getException()));
        });
        Thread hilo = new Thread(tarea, "sincronizacion");
        hilo.setDaemon(true);
        hilo.start();
    }

    /**
     * Desbloquea la ventana
     * @param owner: ventana de la aplicación
     * @param raiz: contenido que se bloqueó
     */
    private static void liberar(Window owner, Parent raiz) {
        raiz.setDisable(false);
        owner.getScene().setCursor(Cursor.DEFAULT);
    }

    /**
     * @param e: error de la tarea
     * @return Devuelve un texto que se pueda enseñar al usuario
     */
    private static String motivo(Throwable e) {
        if (e instanceof AuthException || (e instanceof IOException && e.getMessage() != null && e.getMessage().startsWith("Firebase respondió"))) {
            return e.getMessage();
        }
        if (e instanceof IOException) {
            return "No se pudo conectar con Firebase. Comprueba tu conexión a internet.";
        }
        return e.getMessage() == null || e.getMessage().isBlank() ? "Error inesperado (" + e.getClass().getSimpleName() + ")." : e.getMessage();
    }

    /**
     * Pregunta si se quiere continuar
     * @param owner: ventana sobre la que se muestra
     * @param titulo: pregunta
     * @param mensaje: explicación
     * @param textoOk: texto del botón que continúa
     * @return Devuelve true si se ha pulsado el botón de continuar
     */
    private static boolean confirmar(Window owner, String titulo, String mensaje, String textoOk) {
        ButtonType ok = new ButtonType(textoOk, ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelar = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert dialogo = new Alert(Alert.AlertType.CONFIRMATION, mensaje, ok, cancelar);
        dialogo.setTitle("MisCuentas");
        dialogo.setHeaderText(titulo);
        preparar(dialogo, owner);
        dialogo.getDialogPane().lookupButton(ok).getStyleClass().add("boton-primario");
        dialogo.getDialogPane().lookupButton(cancelar).getStyleClass().add("boton-secundario");
        return dialogo.showAndWait().filter(b -> b == ok).isPresent();
    }

    /**
     * Pide la contraseña de la cuenta para conectar con Firebase
     * @param owner: ventana sobre la que se muestra
     * @param usuario: usuario que se conecta
     * @return Devuelve la contraseña escrita, o vacío si se cancela o se deja en blanco
     */
    private static Optional<String> pedirContrasena(Window owner, Usuario usuario) {
        ButtonType conectar = new ButtonType("Conectar", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelar = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert dialogo = new Alert(Alert.AlertType.CONFIRMATION, null, conectar, cancelar);
        dialogo.setTitle("MisCuentas");
        dialogo.setHeaderText("Conectar con Firebase");

        Label texto = new Label("Escribe la contraseña de " + usuario.getEmail() + " para conectar y poder subir y descargar tus datos.");
        texto.setWrapText(true);
        PasswordField campo = new PasswordField();
        campo.setPromptText("Contraseña");
        VBox contenido = new VBox(10, texto, campo);
        dialogo.getDialogPane().setContent(contenido);
        preparar(dialogo, owner);
        dialogo.getDialogPane().lookupButton(conectar).getStyleClass().add("boton-primario");
        dialogo.getDialogPane().lookupButton(cancelar).getStyleClass().add("boton-secundario");
        Platform.runLater(campo::requestFocus);

        boolean acepta = dialogo.showAndWait().filter(b -> b == conectar).isPresent();
        return acepta && !campo.getText().isEmpty() ? Optional.of(campo.getText()) : Optional.empty();
    }

    /**
     * Enseña un aviso informativo
     * @param owner: ventana sobre la que se muestra
     * @param titulo: título
     * @param mensaje: texto
     */
    private static void informar(Window owner, String titulo, String mensaje) {
        Alert dialogo = new Alert(Alert.AlertType.INFORMATION, mensaje, ButtonType.OK);
        dialogo.setTitle("MisCuentas");
        dialogo.setHeaderText(titulo);
        preparar(dialogo, owner);
        dialogo.getDialogPane().lookupButton(ButtonType.OK).getStyleClass().add("boton-primario");
        dialogo.showAndWait();
    }

    /**
     * Enseña un error
     * @param owner: ventana sobre la que se muestra
     * @param titulo: título
     * @param mensaje: motivo
     */
    private static void error(Window owner, String titulo, String mensaje) {
        Alert dialogo = new Alert(Alert.AlertType.ERROR, mensaje, ButtonType.OK);
        dialogo.setTitle("MisCuentas");
        dialogo.setHeaderText(titulo);
        preparar(dialogo, owner);
        dialogo.getDialogPane().lookupButton(ButtonType.OK).getStyleClass().add("boton-primario");
        dialogo.showAndWait();
    }

    /**
     * Da a un diálogo el aspecto de la aplicación
     * @param dialogo: diálogo a preparar
     * @param owner: ventana sobre la que se muestra
     */
    private static void preparar(Alert dialogo, Window owner) {
        dialogo.setGraphic(null);
        dialogo.initOwner(owner);
        dialogo.getDialogPane().getStylesheets().add(SincronizacionDialogos.class.getResource(CSS).toExternalForm());
        dialogo.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
    }
}
