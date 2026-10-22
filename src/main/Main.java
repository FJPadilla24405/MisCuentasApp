package main;

import controllers.AuthController;
import controllers.MainController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;
import models.Usuario;
import service.auth.SesionActual;
import service.connections.DatabaseManager;

public class Main extends Application {

	private Stage ventana;

	/**
	 * Arranca la aplicación en la pantalla de acceso
	 * @param primaryStage: ventana principal
	 */
	@Override
	public void start(Stage primaryStage) {
		try {
			ventana = primaryStage;
			ventana.setTitle("MisCuentas");
			mostrarAcceso();
			ventana.show();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Muestra la pantalla de login y registro
	 * @throws Exception si no se puede cargar el FXML
	 */
	private void mostrarAcceso() throws Exception {
		FXMLLoader loader = new FXMLLoader(getClass().getResource("/screens/AuthScreen.fxml"));
		Parent raiz = loader.load();
		AuthController acceso = loader.getController();
		acceso.setAlAcceder(this::mostrarPrincipal);
		ponerPantalla(raiz, 1000, 680, 560, 560);
	}

	/**
	 * Muestra la pantalla principal (barra lateral y contenido) cuando el login o el registro han ido bien
	 * @param usuario: usuario que ha entrado
	 */
	private void mostrarPrincipal(Usuario usuario) {
		try {
			FXMLLoader loader = new FXMLLoader(getClass().getResource("/screens/MainScreen.fxml"));
			Parent raiz = loader.load();
			MainController principal = loader.getController();
			principal.setAlCerrarSesion(this::cerrarSesion);
			ponerPantalla(raiz, 1280, 800, 980, 620);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Cierra la sesión y vuelve a la pantalla de acceso
	 */
	private void cerrarSesion() {
		try {
			SesionActual.cerrar();
			mostrarAcceso();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Cambia lo que se ve en la ventana y ajusta su tamaño (sin salirse de la pantalla)
	 * @param raiz: contenido nuevo
	 * @param ancho: ancho deseado
	 * @param alto: alto deseado
	 * @param minAncho: ancho mínimo
	 * @param minAlto: alto mínimo
	 */
	private void ponerPantalla(Parent raiz, double ancho, double alto, double minAncho, double minAlto) {
		Rectangle2D pantalla = Screen.getPrimary().getVisualBounds();
		double anchoFinal = Math.min(ancho, pantalla.getWidth());
		double altoFinal = Math.min(alto, pantalla.getHeight());

		if (ventana.getScene() == null) {
			ventana.setScene(new Scene(raiz, anchoFinal, altoFinal));
		} else {
			ventana.getScene().setRoot(raiz);
		}
		ventana.setMinWidth(Math.min(minAncho, anchoFinal));
		ventana.setMinHeight(Math.min(minAlto, altoFinal));
		ventana.setWidth(anchoFinal);
		ventana.setHeight(altoFinal);
		ventana.centerOnScreen();
	}

	/**
	 * Cierra la conexión con la base de datos al salir
	 */
	@Override
	public void stop() {
		DatabaseManager.cerrar();
	}

	public static void main(String[] args) {
		// Abre (o crea) la base de datos local antes de mostrar nada, para que un fallo salga al arrancar
		DatabaseManager.getConnection();
		launch(args);
	}
}
