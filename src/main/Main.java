package main;
	
import javafx.application.Application;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import models.Usuario;
import service.connections.DatabaseManager;
import service.dao.CategoriaDAO;
import service.dao.CuentaDAO;
import service.dao.MovimientoDAO;
import service.dao.UsuarioDAO;
import models.Cuenta;
import models.Categoria;
import models.Movimiento;

import service.sync.FirebaseAuthClient;
import service.sync.FirebaseClient;
import service.sync.SyncService;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;


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
		probarSubidaFirebase();

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

		Cuenta cuenta = cuentaDAO.insertar(new Cuenta(0, usuario.getId(), "Efectivo", "efectivo", new BigDecimal(100), "EUR", true));
		System.out.println("Cuenta creada: " + cuenta);

		Categoria categoria = categoriaDAO.insertar(new Categoria(0, usuario.getId(), "Comida", "gasto", null, false));
		System.out.println("Categoría creada: " + categoria);

		Movimiento movimiento = movimientoDAO.insertar(
				new Movimiento(0, usuario.getId(), cuenta.getId(), categoria.getId(), new BigDecimal("15.50"), "gasto", "2026-09-30", "Almuerzo", false)
		);
		System.out.println("Movimiento creado: " + movimiento);

		BigDecimal saldo = cuentaDAO.calcularSaldo(cuenta.getId());
		System.out.println("Saldo de la cuenta '" + cuenta.getNombre() + "': " + saldo + " " + cuenta.getMoneda());
	}

	/**
	 * Prueba de subida a Firebase: inicia sesión, sube los datos locales del usuario y vuelve a leerlos
	 * de Firebase para comprobar que han llegado. Si no existe el fichero de configuración, se omite.
	 */
	private static void probarSubidaFirebase() {
		Path config = Path.of(System.getProperty("user.home"), ".miscuentas", "firebase.properties");
		if (!Files.exists(config)) {
			System.out.println("[Firebase] Prueba omitida: no existe " + config);
			return;
		}
		try {
			Properties p = new Properties();
			try (InputStream in = Files.newInputStream(config)) {
				p.load(in);
			}
			for (String clave : new String[] { "apiKey", "dbUrl", "email", "password" }) {
				if (p.getProperty(clave, "").isBlank()) {
					System.out.println("[Firebase] Prueba omitida: falta '" + clave + "' en " + config);
					return;
				}
			}

			FirebaseAuthClient.Sesion sesion = new FirebaseAuthClient(p.getProperty("apiKey").trim())
					.iniciarSesion(p.getProperty("email").trim(), p.getProperty("password"));
			System.out.println("[Firebase] Sesión iniciada. uid=" + sesion.uid());

			FirebaseClient firebase = new FirebaseClient(p.getProperty("dbUrl").trim(), sesion.idToken());
			subirYComprobar(sesion.uid(), p.getProperty("email").trim(), firebase);
		} catch (Exception e) {
			// La prueba no debe impedir que arranque la aplicación
			System.out.println("[Firebase] La prueba ha fallado: " + e);
			e.printStackTrace();
		}
	}

	/** Parte de la prueba que no depende del login: prepara datos locales, los sube y los lee de vuelta. */
	private static void subirYComprobar(String uid, String email, FirebaseClient firebase) throws Exception {
		UsuarioDAO usuarioDAO = new UsuarioDAO();
		CuentaDAO cuentaDAO = new CuentaDAO();
		CategoriaDAO categoriaDAO = new CategoriaDAO();
		MovimientoDAO movimientoDAO = new MovimientoDAO();

		// El usuario local debe llevar el uid REAL de Firebase: las reglas de la base de datos solo
		// dejan escribir en Users/{uid} a ese mismo usuario.
		Usuario usuario = usuarioDAO.buscarPorFirebaseUid(uid).orElseGet(
				() -> usuarioDAO.insertar(new Usuario(0, uid, "Usuario Firebase", email, "EUR", "claro")));

		// Datos de ejemplo solo si todavía no tiene cuentas, para no duplicarlos en cada arranque
		if (cuentaDAO.listarPorUsuario(usuario.getId()).isEmpty()) {
			Cuenta cuenta = cuentaDAO.insertar(new Cuenta(0, usuario.getId(), "Efectivo", "efectivo", new BigDecimal("100.00"), "EUR", true));
			Categoria categoria = categoriaDAO.insertar(new Categoria(0, usuario.getId(), "Comida", "gasto", null, false));
			movimientoDAO.insertar(new Movimiento(0, usuario.getId(), cuenta.getId(), categoria.getId(), new BigDecimal("15.50"),
					"gasto", "2026-09-30", "Almuerzo", false));
		}

		new SyncService(firebase).subir(usuario);
		System.out.println("[Firebase] Datos subidos a Users/" + uid);

		Object remoto = firebase.leer("Users/" + uid);
		if (!(remoto instanceof Map<?, ?> raiz)) {
			throw new IllegalStateException("Tras subir, Users/" + uid + " está vacío en Firebase");
		}
		for (String seccion : new String[] { "cuentas", "categorias", "recurrentes", "presupuestos", "movimientos" }) {
			Object datos = raiz.get(seccion);
			int n = datos instanceof Map<?, ?> m ? m.size() : datos instanceof java.util.List<?> l ? l.size() : 0;
			System.out.println("[Firebase]   " + seccion + ": " + n);
		}
		System.out.println("[Firebase] Comprobación correcta (última sincronización: " + raiz.get("ultimaSync") + ")");
	}
}
