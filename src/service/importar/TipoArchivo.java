package service.importar;

/**
 * Los tipos de archivo que se pueden importar. Se detectan mirando el contenido del archivo, no su extensión.
 *
 * @author Fco Padilla
 */
public enum TipoArchivo {

    CSV("CSV"),
    EXCEL_XLS("Excel (.xls)"),
    EXCEL_XLSX("Excel (.xlsx)");

    private final String etiqueta;

    TipoArchivo(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    /**
     * @return Devuelve el nombre que se ve en la aplicación
     */
    public String getEtiqueta() {
        return etiqueta;
    }
}
