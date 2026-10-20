package main;

import controllers.AuthController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import models.Usuario;
import service.auth.SesionActual;
import service.connections.DatabaseManager;

public class Main extends Application {

	private Stage ventana;

	@Override
	public void start(Stage primaryStage) {
		try {
			ventana = primaryStage;

			FXMLLoader loader = new FXMLLoader(getClass().getResource("/screens/AuthScreen.fxml"));
			Parent raiz = loader.load();
			AuthController acceso = loader.getController();
			acceso.setAlAcceder(this::mostrarPantallaPrincipal);

			primaryStage.setTitle("MisCuentas");
			primaryStage.setMinWidth(560);
			primaryStage.setMinHeight(560);
			primaryStage.setScene(new Scene(raiz, 1000, 680));
			primaryStage.show();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Se llama cuando el login o el registro han ido bien. De momento solo enseña un saludo:
	 * aquí hay que cargar el FXML de la pantalla general de la aplicación.
	 */
	private void mostrarPantallaPrincipal(Usuario usuario) {
		Label saludo = new Label("Hola, " + usuario.getNombre());
		saludo.getStyleClass().add("panel-titulo");
		Label modo = new Label(SesionActual.isConectado() ? "Conectado a Firebase" : "Sin conexión a Firebase");
		modo.getStyleClass().add("texto-suave");

		VBox provisional = new VBox(8, saludo, modo);
		provisional.setAlignment(Pos.CENTER);
		provisional.getStylesheets().add(getClass().getResource("/screens/styles/application.css").toExternalForm());
		ventana.getScene().setRoot(provisional);
	}

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
