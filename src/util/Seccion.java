package util;

/**
 * Apartados de la aplicación que se pueden abrir desde la barra lateral. Cada uno apunta a un FXML en
 * screens/sections/. Para añadir el contenido de un apartado basta con crear ese FXML con el nombre
 * indicado aquí: mientras no exista, se muestra un panel provisional.
 *
 * @author Fco Padilla
 */
public enum Seccion {

    VISTA_GENERAL("Vista general", "VistaGeneral"),
    GRAFICAS("Gráficas y resúmenes", "Graficas"),
    PRESUPUESTOS("Presupuestos", "Presupuestos"),
    PERFIL("Editar perfil", "Perfil");

    // Nombre que se enseña en pantalla
    private final String titulo;
    // Nombre del archivo FXML (sin extensión) dentro de screens/sections/
    private final String archivo;

    /**
     * Crea un apartado
     * @param titulo: nombre que se enseña en pantalla
     * @param archivo: nombre del FXML sin extensión
     */
    Seccion(String titulo, String archivo) {
        this.titulo = titulo;
        this.archivo = archivo;
    }

    /**
     * @return Devuelve el nombre del apartado
     */
    public String getTitulo() {
        return titulo;
    }

    /**
     * @return Devuelve la ruta del FXML de este apartado dentro del classpath
     */
    public String getRutaFxml() {
        return "/screens/sections/" + archivo + ".fxml";
    }
}
