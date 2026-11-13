package service.exportar;

/**
 * Formatos en los que se puede exportar un resumen: PDF (informe visual), CSV (movimientos) y Excel (datos y resumen).
 *
 * @author Fco Padilla
 */
public enum FormatoArchivo {

    PDF("PDF", "pdf", "Informe visual"),
    CSV("CSV", "csv", "Movimientos"),
    EXCEL("Excel", "xlsx", "Datos y resumen");

    private final String nombre;
    private final String extension;
    private final String descripcion;

    FormatoArchivo(String nombre, String extension, String descripcion) {
        this.nombre = nombre;
        this.extension = extension;
        this.descripcion = descripcion;
    }

    /**
     * @return Devuelve el nombre que se ve en el diálogo (PDF, CSV, Excel)
     */
    public String getNombre() {
        return nombre;
    }

    /**
     * @return Devuelve la extensión del archivo, sin punto (pdf, csv, xlsx)
     */
    public String getExtension() {
        return extension;
    }

    /**
     * @return Devuelve la frase corta que se ve bajo el nombre en el diálogo
     */
    public String getDescripcion() {
        return descripcion;
    }

    /**
     * Quita del nombre de un archivo la extensión de cualquiera de los formatos, si la tiene
     * @param nombre: nombre del archivo, con o sin extensión
     * @return Devuelve el nombre sin la extensión
     */
    public static String sinExtension(String nombre) {
        String minuscula = nombre.toLowerCase();
        for (FormatoArchivo f : values()) {
            String sufijo = "." + f.extension;
            if (minuscula.endsWith(sufijo)) {
                return nombre.substring(0, nombre.length() - sufijo.length());
            }
        }
        return nombre;
    }
}
