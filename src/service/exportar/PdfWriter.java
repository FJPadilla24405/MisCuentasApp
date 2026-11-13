package service.exportar;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Escribe un PDF sin necesidad de ninguna librería. Cada página del PDF es una imagen a pantalla completa (la
 * captura de una página dibujada con JavaFX), así que el PDF se ve exactamente como la pantalla y las gráficas
 * salen idénticas a las de la aplicación. Las imágenes se guardan sin pérdida y comprimidas con Flate.
 *
 * @author Fco Padilla
 */
public final class PdfWriter {

    // Tamaño de una página A4 en puntos (1 punto = 1/72 de pulgada)
    public static final double A4_ANCHO = 595.28;
    public static final double A4_ALTO = 841.89;

    /**
     * Una página: la imagen ya comprimida (los píxeles RGB de 8 bits, uno detrás de otro, por filas).
     * @param ancho: ancho de la imagen en píxeles
     * @param alto: alto de la imagen en píxeles
     * @param comprimido: píxeles comprimidos con Flate (zlib)
     */
    public record Pagina(int ancho, int alto, byte[] comprimido) {

        /**
         * Comprime los píxeles de una imagen para que ocupe poco mientras se espera a escribir el resto de páginas
         * @param ancho: ancho en píxeles
         * @param alto: alto en píxeles
         * @param rgb: 3 bytes por píxel (rojo, verde, azul), por filas de arriba abajo
         * @return Devuelve la página lista para escribir
         * @throws IOException si falla la compresión
         */
        public static Pagina desdeRgb(int ancho, int alto, byte[] rgb) throws IOException {
            ByteArrayOutputStream salida = new ByteArrayOutputStream(Math.max(1024, rgb.length / 20));
            Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION);
            try (DeflaterOutputStream flate = new DeflaterOutputStream(salida, deflater)) {
                flate.write(rgb);
            } finally {
                deflater.end();
            }
            return new Pagina(ancho, alto, salida.toByteArray());
        }
    }

    // No se instancia
    private PdfWriter() {
    }

    /**
     * Escribe el PDF
     * @param salida: dónde se escribe (se cierra al terminar)
     * @param titulo: título del documento
     * @param paginas: páginas, al menos una
     * @throws IOException si falla la escritura
     */
    public static void escribir(OutputStream salida, String titulo, List<Pagina> paginas) throws IOException {
        try (Escritor e = new Escritor(salida)) {
            int n = paginas.size();
            // Objetos: 1 catálogo, 2 páginas, 3 información; después, por cada página: la página, su contenido y su imagen
            e.escribirCabecera();
            e.objeto(1, "<< /Type /Catalog /Pages 2 0 R >>");
            StringBuilder hijos = new StringBuilder();
            for (int i = 0; i < n; i++) {
                hijos.append(4 + 3 * i).append(" 0 R ");
            }
            e.objeto(2, "<< /Type /Pages /Kids [" + hijos + "] /Count " + n + " >>");
            e.objeto(3, "<< /Title " + cadena(titulo) + " /Creator " + cadena("MisCuentas") + " /Producer " + cadena("MisCuentas") + " >>");

            for (int i = 0; i < n; i++) {
                Pagina p = paginas.get(i);
                int idPagina = 4 + 3 * i;
                int idContenido = 5 + 3 * i;
                int idImagen = 6 + 3 * i;
                e.objeto(idPagina, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + num(A4_ANCHO) + " " + num(A4_ALTO)
                        + "] /Resources << /XObject << /Im0 " + idImagen + " 0 R >> >> /Contents " + idContenido + " 0 R >>");
                byte[] contenido = ("q " + num(A4_ANCHO) + " 0 0 " + num(A4_ALTO) + " 0 0 cm /Im0 Do Q").getBytes(StandardCharsets.US_ASCII);
                e.objetoConFlujo(idContenido, "<< /Length " + contenido.length + " >>", contenido);
                e.objetoConFlujo(idImagen, "<< /Type /XObject /Subtype /Image /Width " + p.ancho() + " /Height " + p.alto()
                        + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /FlateDecode /Length " + p.comprimido().length + " >>",
                        p.comprimido());
            }
            e.terminar(3 + 3 * n, 1, 3);
        }
    }

    /**
     * @param valor: número
     * @return Devuelve el número con punto decimal y dos decimales, como exige el PDF
     */
    private static String num(double valor) {
        return String.format(Locale.ROOT, "%.2f", valor);
    }

    /**
     * @param texto: texto del documento
     * @return Devuelve el texto como cadena de PDF en UTF-16 (admite tildes y la ñ)
     */
    private static String cadena(String texto) {
        StringBuilder x = new StringBuilder("<FEFF");
        for (char c : texto.toCharArray()) {
            x.append(String.format("%04X", (int) c));
        }
        return x.append('>').toString();
    }

    // Va contando los bytes escritos para anotar dónde empieza cada objeto (tabla xref)
    private static final class Escritor implements AutoCloseable {
        private final OutputStream salida;
        private final List<Long> posiciones = new ArrayList<>();
        private long escritos = 0;

        Escritor(OutputStream salida) {
            this.salida = salida;
        }

        void escribirCabecera() throws IOException {
            // Los cuatro bytes altos avisan a los programas de que el archivo es binario
            escribir("%PDF-1.4\n%\u00e2\u00e3\u00cf\u00d3\n");
        }

        void objeto(int id, String contenido) throws IOException {
            anotar(id);
            escribir(id + " 0 obj\n" + contenido + "\nendobj\n");
        }

        void objetoConFlujo(int id, String diccionario, byte[] datos) throws IOException {
            anotar(id);
            escribir(id + " 0 obj\n" + diccionario + "\nstream\n");
            escribir(datos);
            escribir("\nendstream\nendobj\n");
        }

        void terminar(int numObjetos, int raiz, int info) throws IOException {
            long inicio = escritos;
            StringBuilder x = new StringBuilder("xref\n0 ").append(numObjetos + 1).append("\n0000000000 65535 f \n");
            for (long pos : posiciones) {
                x.append(String.format(Locale.ROOT, "%010d 00000 n \n", pos));
            }
            x.append("trailer\n<< /Size ").append(numObjetos + 1).append(" /Root ").append(raiz).append(" 0 R /Info ")
                    .append(info).append(" 0 R >>\nstartxref\n").append(inicio).append("\n%%EOF\n");
            escribir(x.toString());
        }

        private void anotar(int id) {
            // Los objetos se escriben en orden: el id n va en la posición n - 1
            if (posiciones.size() != id - 1) {
                throw new IllegalStateException("Objetos del PDF escritos en desorden");
            }
            posiciones.add(escritos);
        }

        private void escribir(String texto) throws IOException {
            escribir(texto.getBytes(StandardCharsets.ISO_8859_1));
        }

        private void escribir(byte[] datos) throws IOException {
            salida.write(datos);
            escritos += datos.length;
        }

        @Override
        public void close() throws IOException {
            salida.close();
        }
    }
}
