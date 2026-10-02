package main;
	
import javafx.application.Application;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;

import connections.DatabaseManager;
import dao.UsuarioDAO;
import dao.CuentaDAO;
import dao.CategoriaDAO;
import dao.MovimientoDAO;
import models.Usuario;
import models.Cuenta;
import models.Categoria;
import models.Movimiento;

import java.util.Optional;


public class Main extends Application {
	@Override
	public void start(Stage primaryStage) {
		try {
			BorderPane root = new BorderPane();
			Scene scene = new Scene(root,400,400);
			scene.getStylesheets().add(getClass().getResource("../screens/styles/application.css").toExternalForm());
			primaryStage.setScene(scene);
			primaryStage.show();
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) {
		// Prueba de conexión a la base de datos (quitar cuando ya confíes en ella)
		DatabaseManager.getConnection();
		System.out.println("Conexión a la base de datos establecida en ~/.miscuentas/miscuentas.db");

		probarDAOs();

		launch(args);
	}

	/**
	 * Prueba de extremo a extremo: crea un usuario de ejemplo (si no existe ya),
	 * una cuenta, una categoría y un movimiento, y muestra el saldo resultante.
	 * Quitar esta llamada cuando ya tengas pantallas reales que hagan esto.
	 */
	private static void probarDAOs() {
		UsuarioDAO usuarioDAO = new UsuarioDAO();
		CuentaDAO cuentaDAO = new CuentaDAO();
		CategoriaDAO categoriaDAO = new CategoriaDAO();
		MovimientoDAO movimientoDAO = new MovimientoDAO();

		String uidPrueba = "usuario-prueba-local";
		Optional<Usuario> existente = usuarioDAO.buscarPorFirebaseUid(uidPrueba);

		Usuario usuario = existente.orElseGet(() ->
				usuarioDAO.insertar(new Usuario(0, uidPrueba, "Usuario Prueba", "prueba@ejemplo.com", "EUR", "claro"))
		);
		System.out.println("Usuario: " + usuario);

		Cuenta cuenta = cuentaDAO.insertar(new Cuenta(0, usuario.getId(), "Efectivo", "efectivo", 100.0, "EUR", true));
		System.out.println("Cuenta creada: " + cuenta);

		Categoria categoria = categoriaDAO.insertar(new Categoria(0, usuario.getId(), "Comida", "gasto", null, false));
		System.out.println("Categoría creada: " + categoria);

		Movimiento movimiento = movimientoDAO.insertar(
				new Movimiento(0, usuario.getId(), cuenta.getId(), categoria.getId(), 15.5, "gasto", "2026-09-30", "Almuerzo", false)
		);
		System.out.println("Movimiento creado: " + movimiento);

		double saldo = cuentaDAO.calcularSaldo(cuenta.getId());
		System.out.println("Saldo de la cuenta '" + cuenta.getNombre() + "': " + saldo + " " + cuenta.getMoneda());
	}
}
