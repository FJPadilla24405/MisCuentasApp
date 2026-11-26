package service.perfil;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import models.Usuario;
import service.perfil.PerfilService.DatoInvalido;
import util.tema.ColoresTema;
import util.tema.Tema;

/**
 * Colores del tema personalizado. No van en la base de datos: se guardan en un archivo de texto de la carpeta
 * de la aplicación (~/.miscuentas/tema-personal-ID.properties), uno por usuario de este equipo, así que no se
 * suben a Firebase. En la base de datos solo queda qué tema se eligió (usuarios.tema).
 *
 * @author Fco Padilla
 */
public class TemaService {

    // La misma carpeta que la base de datos (ver DatabaseManager)
    private static final Path CARPETA = Path.of(System.getProperty("user.home"), ".miscuentas");

    /**
     * Lee los colores personalizados del usuario. Si no hay archivo, o no se puede leer, devuelve los del tema claro.
     * @param usuario: usuario de la sesión
     * @return Devuelve todos los colores, completos y válidos
     */
    public Map<String, String> cargarColores(Usuario usuario) {
        Map<String, String> leidos = new HashMap<>();
        if (usuario != null && Files.isRegularFile(archivo(usuario))) {
            Properties p = new Properties();
            try (Reader lector = Files.newBufferedReader(archivo(usuario), StandardCharsets.UTF_8)) {
                p.load(lector);
                for (String clave : p.stringPropertyNames()) {
                    leidos.put(clave, p.getProperty(clave));
                }
            } catch (IOException | IllegalArgumentException e) {
                leidos.clear();
            }
        }
        return ColoresTema.completar(leidos);
    }

    /**
     * Guarda los colores personalizados del usuario en este equipo
     * @param usuario: usuario de la sesión
     * @param colores: colores a guardar (los que falten o no sean válidos se guardan como los del tema claro)
     * @throws DatoInvalido si no se puede escribir el archivo
     */
    public void guardarColores(Usuario usuario, Map<String, String> colores) {
        Properties p = new Properties();
        p.putAll(ColoresTema.completar(colores));
        try {
            Files.createDirectories(CARPETA);
            try (Writer escritor = Files.newBufferedWriter(archivo(usuario), StandardCharsets.UTF_8)) {
                p.store(escritor, "Colores del tema personalizado de MisCuentas");
            }
        } catch (IOException e) {
            throw new DatoInvalido("tema", "No se pudieron guardar los colores personalizados en este equipo.");
        }
    }

    /**
     * Pone en toda la aplicación el tema guardado del usuario (o el claro si no hay usuario)
     * @param usuario: usuario de la sesión, o null
     */
    public static void aplicar(Usuario usuario) {
        if (usuario == null) {
            Tema.usarClaro();
            return;
        }
        String tema = Tema.normalizar(usuario.getTema());
        Tema.usar(tema, Tema.PERSONAL.equals(tema) ? new TemaService().cargarColores(usuario) : null);
    }

    private static Path archivo(Usuario usuario) {
        return CARPETA.resolve("tema-personal-" + usuario.getId() + ".properties");
    }
}
