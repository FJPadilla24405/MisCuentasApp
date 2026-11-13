package service.exportar;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Label;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.transform.Scale;
import service.exportar.PdfWriter.Pagina;
import service.exportar.Tabla.Dinero;
import service.exportar.Tabla.Porcentaje;
import service.exportar.Tabla.Seccion;
import service.resumen.GraficasService.FilaMes;
import service.resumen.GraficasService.Porcion;
import service.resumen.GraficasService.Semana;
import util.Formato;
import util.IconosCategoria;
import util.graphics.GraficoBarras;
import util.graphics.GraficoDonut;
import util.graphics.GraficoLinea;
import util.graphics.Paleta;

/**
 * Genera el informe en PDF. Cada página se construye con nodos de JavaFX (las mismas gráficas, tarjetas y tablas
 * de la aplicación, con application.css), se fotografía a doble resolución y las fotos se juntan en un PDF con
 * PdfWriter. Las páginas son: el resumen (como el ejemplo de la maqueta), las gráficas y la tabla de movimientos,
 * que se reparte en todas las páginas que hagan falta. Hay que llamarlo desde el hilo de JavaFX.
 *
 * @author Fco Padilla
 */
public final class ExportadorPdf {

    // Tamaño de la página en píxeles (un A4 a 96 puntos por pulgada) y resolución de la foto (2 = el doble)
    private static final double ANCHO = 794;
    private static final double ALTO = 1123;
    private static final double ESCALA = 2;
    // Márgenes de la página: tienen que coincidir con el padding de .pdf-pagina en application.css
    private static final double MARGEN_LATERAL = 48;
    private static final double MARGEN_ARRIBA = 40;
    private static final double MARGEN_ABAJO = 28;
    private static final double ANCHO_UTIL = ANCHO - 2 * MARGEN_LATERAL;
    // Alturas fijas de las partes de la página, para saber cuántas filas caben en cada una
    private static final double ALTO_CABECERA_DOC = 138;
    private static final double ALTO_CABECERA_SECCION = 86;
    private static final double ALTO_TITULO_SECCION = 40;
    private static final double ALTO_PIE = 46;
    private static final double ALTO_FILA = 28;
    private static final double ALTO_CABECERA_TABLA = 30;
    // Margen de seguridad que se deja libre debajo de la tabla
    private static final double HOLGURA = 10;
    // Máximo de filas que se dibujan en la tabla del resumen (si hay más cuentas, se avisa de cuántas faltan)
    private static final int MAX_FILAS_RESUMEN = 14;
    // Máximo de trozos del reparto del gasto: si hay más categorías, las últimas se agrupan en "Otras"
    private static final int MAX_CATEGORIAS = 6;
    private static final double ESCALA_ICONO = 0.67;
    private static final String CSS = "/screens/styles/application.css";
    // Signo menos de verdad (el guion de teclado queda corto al lado de las cifras)
    private static final String MENOS = "\u2212";
    private static final String PUNTO = " \u00b7 ";

    // Una página por montar: de qué sección es, si continúa una tabla de la página anterior y lo que lleva dentro
    private record Hoja(String seccion, boolean continuacion, List<Node> contenido) {
    }

    // No se instancia
    private ExportadorPdf() {
    }

    /**
     * Crea el PDF
     * @param destino: archivo donde se guarda
     * @param datos: datos del periodo
     * @param secciones: secciones pedidas, ya con sus tablas
     * @throws IOException si no se puede escribir el archivo
     */
    public static void escribir(Path destino, DatosExportacion datos, List<Seccion> secciones) throws IOException {
        List<Hoja> hojas = new ArrayList<>();
        for (Seccion seccion : secciones) {
            switch (seccion.nombre()) {
                case ExportacionService.SECCION_RESUMEN -> hojas.add(hojaResumen(datos, seccion));
                case ExportacionService.SECCION_GRAFICAS -> hojas.add(hojaGraficas(datos));
                case ExportacionService.SECCION_MOVIMIENTOS ->
                        hojas.addAll(hojasMovimientos(seccion.tablas().get(0), hojas.isEmpty(), datos.moneda()));
                default -> { }
            }
        }
        String css = ExportadorPdf.class.getResource(CSS).toExternalForm();
        List<Pagina> paginas = new ArrayList<>();
        for (int i = 0; i < hojas.size(); i++) {
            VBox pagina = montarPagina(hojas.get(i), i, hojas.size(), datos);
            paginas.add(fotografiar(pagina, css));
        }
        try (var salida = Files.newOutputStream(destino)) {
            PdfWriter.escribir(salida, "MisCuentas \u00b7 " + datos.periodo(), paginas);
        }
    }

    // ===== Página: marco =====

    /**
     * Monta una página completa: la cabecera, el contenido y el pie con la numeración
     * @param hoja: contenido de la página
     * @param indice: posición de la página, empezando en 0
     * @param total: número de páginas del informe
     * @param d: datos del periodo
     * @return Devuelve la página, lista para fotografiar
     */
    private static VBox montarPagina(Hoja hoja, int indice, int total, DatosExportacion d) {
        VBox pagina = new VBox();
        pagina.getStyleClass().add("pdf-pagina");
        fijarTamano(pagina, ANCHO, ALTO);

        boolean primera = indice == 0;
        VBox cuerpo = new VBox(18);
        cuerpo.setMinHeight(0);
        VBox.setVgrow(cuerpo, Priority.ALWAYS);
        // En la primera página, las secciones que no son el resumen necesitan su título (el resumen ya lo tiene con el periodo)
        if (primera && !ExportacionService.SECCION_RESUMEN.equals(hoja.seccion())) {
            Label titulo = etiqueta(hoja.seccion(), "pdf-seccion-titulo");
            fijarAlto(titulo, ALTO_TITULO_SECCION - 18);
            cuerpo.getChildren().add(titulo);
        }
        cuerpo.getChildren().addAll(hoja.contenido());

        pagina.getChildren().addAll(primera ? cabeceraDocumento(d) : cabeceraSeccion(hoja, d), cuerpo, pie(d, indice + 1, total));
        return pagina;
    }

    /**
     * @return Devuelve la fila de arriba de todas las páginas: la marca a la izquierda e "INFORME PERSONAL" a la derecha
     */
    private static HBox filaMarca() {
        Region hueco = new Region();
        HBox.setHgrow(hueco, Priority.ALWAYS);
        HBox fila = new HBox(etiqueta("MisCuentas", "pdf-marca"), hueco, etiqueta("INFORME PERSONAL", "pdf-informe"));
        fila.setAlignment(Pos.CENTER_LEFT);
        return fila;
    }

    /**
     * Cabecera de la primera página: marca, periodo en grande y, debajo, usuario, cuentas y moneda
     * @param d: datos del periodo
     * @return Devuelve la cabecera
     */
    private static VBox cabeceraDocumento(DatosExportacion d) {
        Label periodo = etiqueta(d.periodo(), "pdf-periodo");
        VBox.setMargin(periodo, new Insets(18, 0, 0, 0));
        Label subtitulo = etiqueta(d.usuario().getNombre() + PUNTO + d.ambito() + PUNTO + d.moneda(), "pdf-subtitulo");
        VBox cabecera = new VBox(4, filaMarca(), periodo, subtitulo);
        fijarAlto(cabecera, ALTO_CABECERA_DOC);
        return cabecera;
    }

    /**
     * Cabecera de las demás páginas: marca y el nombre de la sección con el periodo
     * @param hoja: página que se monta
     * @param d: datos del periodo
     * @return Devuelve la cabecera
     */
    private static VBox cabeceraSeccion(Hoja hoja, DatosExportacion d) {
        String texto = hoja.seccion() + (hoja.continuacion() ? " (continuaci\u00f3n)" : "") + PUNTO + d.periodo();
        Label titulo = etiqueta(texto, "pdf-seccion-titulo");
        VBox.setMargin(titulo, new Insets(14, 0, 0, 0));
        VBox cabecera = new VBox(4, filaMarca(), titulo);
        fijarAlto(cabecera, ALTO_CABECERA_SECCION);
        return cabecera;
    }

    /**
     * Pie de página: una línea fina, lo que cubre el informe y "página / total"
     * @param d: datos del periodo
     * @param numero: número de esta página, empezando en 1
     * @param total: número de páginas
     * @return Devuelve el pie
     */
    private static VBox pie(DatosExportacion d, int numero, int total) {
        Region linea = new Region();
        linea.getStyleClass().add("pdf-linea");
        fijarAlto(linea, 1);
        int n = d.numMovimientos();
        Label resumen = etiqueta(d.rango() + PUNTO + n + (n == 1 ? " movimiento" : " movimientos"), "pdf-pie");
        Region hueco = new Region();
        HBox.setHgrow(hueco, Priority.ALWAYS);
        HBox texto = new HBox(resumen, hueco, etiqueta(numero + " / " + total, "pdf-pie"));
        texto.setAlignment(Pos.CENTER_LEFT);
        VBox pie = new VBox(10, linea, texto);
        pie.setAlignment(Pos.BOTTOM_LEFT);
        fijarAlto(pie, ALTO_PIE);
        return pie;
    }

    // ===== Página: resumen =====

    /**
     * Página del resumen, como el ejemplo de la maqueta: saldo total, ingresos, gastos y balance, distribución del
     * gasto y, debajo, la tabla cuenta por cuenta (mes) o mes a mes (año).
     * @param d: datos del periodo
     * @param seccion: sección del resumen (su última tabla es la de cuentas o meses)
     * @return Devuelve la página
     */
    private static Hoja hojaResumen(DatosExportacion d, Seccion seccion) {
        List<Node> contenido = new ArrayList<>();

        VBox saldo = new VBox(6, etiqueta("SALDO TOTAL", "pdf-saldo-etiqueta"),
                etiqueta(Formato.moneda(d.saldoTotal(), d.moneda()), "pdf-saldo-valor"));
        saldo.getStyleClass().add("pdf-saldo");
        contenido.add(saldo);

        BigDecimal balance = d.balance();
        Label valorBalance = etiqueta(Formato.monedaConSigno(balance, d.moneda()), "pdf-kpi-valor");
        if (balance.signum() != 0) {
            valorBalance.getStyleClass().add(balance.signum() > 0 ? "pdf-kpi-positivo" : "pdf-kpi-negativo");
        }
        GridPane cifras = new GridPane();
        for (int i = 0; i < 3; i++) {
            ColumnConstraints columna = new ColumnConstraints();
            columna.setPercentWidth(100.0 / 3);
            columna.setHgrow(Priority.ALWAYS);
            cifras.getColumnConstraints().add(columna);
        }
        cifras.add(cifra("Ingresos", etiqueta(Formato.moneda(d.ingresos(), d.moneda()), "pdf-kpi-valor")), 0, 0);
        cifras.add(cifra("Gastos", etiqueta(Formato.moneda(d.gastos(), d.moneda()), "pdf-kpi-valor")), 1, 0);
        cifras.add(cifra("Balance", valorBalance), 2, 0);
        contenido.add(cifras);

        contenido.add(linea());
        contenido.add(distribucionDelGasto(d));
        contenido.add(linea());

        // La última tabla del resumen es la de las cuentas (mes) o la de los meses (año)
        Tabla tabla = seccion.tablas().get(seccion.tablas().size() - 1);
        VBox bloque = new VBox(10, etiqueta(tabla.titulo(), "pdf-seccion"));
        int filas = Math.min(tabla.filas().size(), MAX_FILAS_RESUMEN);
        // Si se recorta, se conserva la fila del total, que es la última
        boolean recortada = filas < tabla.filas().size();
        bloque.getChildren().add(tablaNodo(tabla, 0, recortada ? filas - 1 : filas, recortada, d.moneda()));
        if (recortada) {
            bloque.getChildren().add(etiqueta("Y " + (tabla.filas().size() - filas) + " m\u00e1s. Los detalles est\u00e1n en el Excel o en el CSV.", "texto-suave"));
        }
        contenido.add(bloque);
        return new Hoja(ExportacionService.SECCION_RESUMEN, false, contenido);
    }

    /**
     * @param titulo: nombre de la cifra (Ingresos, Gastos, Balance)
     * @param valor: etiqueta con el importe
     * @return Devuelve la columna con el nombre arriba y el importe debajo
     */
    private static VBox cifra(String titulo, Label valor) {
        return new VBox(4, etiqueta(titulo, "pdf-kpi-etiqueta"), valor);
    }

    /**
     * Barra con un trozo de color por categoría (del tamaño de lo gastado) y, debajo, cada categoría con su importe
     * @param d: datos del periodo
     * @return Devuelve el bloque "Distribución del gasto"
     */
    private static VBox distribucionDelGasto(DatosExportacion d) {
        VBox bloque = new VBox(10, etiqueta("Distribuci\u00f3n del gasto", "pdf-seccion"));
        if (d.gastos().signum() <= 0 || d.categorias().isEmpty()) {
            bloque.getChildren().add(etiqueta("No hay gastos en este periodo.", "texto-suave"));
            return bloque;
        }
        List<Porcion> trozos = d.categoriasAgrupadas(MAX_CATEGORIAS);
        boolean agrupado = d.categorias().size() > MAX_CATEGORIAS;
        double separacion = 2;
        double disponible = ANCHO_UTIL - separacion * (trozos.size() - 1);
        double total = d.gastos().doubleValue();

        HBox barra = new HBox(separacion);
        barra.setMinSize(ANCHO_UTIL, 14);
        barra.setPrefSize(ANCHO_UTIL, 14);
        barra.setMaxSize(ANCHO_UTIL, 14);
        FlowPane leyenda = new FlowPane(18, 6);
        leyenda.setPrefWrapLength(ANCHO_UTIL);
        for (int i = 0; i < trozos.size(); i++) {
            Porcion p = trozos.get(i);
            Color color = agrupado && i == trozos.size() - 1 ? Paleta.OTRAS : Paleta.categoria(i);
            double ancho = Math.max(3, disponible * p.importe().doubleValue() / total);
            Region trozo = new Region();
            trozo.setMinSize(ancho, 14);
            trozo.setPrefSize(ancho, 14);
            trozo.setMaxSize(ancho, 14);
            trozo.setBackground(new Background(new BackgroundFill(color, CornerRadii.EMPTY, Insets.EMPTY)));
            barra.getChildren().add(trozo);

            HBox item = new HBox(6, new Circle(4, color), etiqueta(p.categoria() + " " + Formato.monedaCorta(p.importe(), d.moneda()), "pdf-leyenda"));
            item.setAlignment(Pos.CENTER_LEFT);
            leyenda.getChildren().add(item);
        }
        // Los extremos de la barra, redondeados
        Rectangle recorte = new Rectangle(ANCHO_UTIL, 14);
        recorte.setArcWidth(14);
        recorte.setArcHeight(14);
        barra.setClip(recorte);
        bloque.getChildren().addAll(barra, leyenda);
        return bloque;
    }

    // ===== Página: gráficas =====

    /**
     * Página de las gráficas, con las mismas gráficas y tarjetas que la pantalla Gráficas y resúmenes: ingresos y
     * gastos (por semana o por mes) y, según sea mes o año, el reparto del gasto o el balance acumulado.
     * @param d: datos del periodo
     * @return Devuelve la página
     */
    private static Hoja hojaGraficas(DatosExportacion d) {
        List<Node> contenido = new ArrayList<>();

        // Ingresos y gastos
        List<String> etiquetas = new ArrayList<>();
        List<BigDecimal> ingresos = new ArrayList<>();
        List<BigDecimal> gastos = new ArrayList<>();
        String subtitulo;
        int resaltado = -1;
        if (d.anual()) {
            for (FilaMes f : d.datosAnio().meses()) {
                etiquetas.add(Formato.mesCortoMayuscula(f.mes().getMonthValue()));
                ingresos.add(f.ingresos());
                gastos.add(f.gastos());
            }
            subtitulo = "Por mes \u00b7 " + d.datosAnio().anio();
            YearMonth hoy = YearMonth.now();
            resaltado = d.datosAnio().anio() == hoy.getYear() ? hoy.getMonthValue() - 1 : -1;
        } else {
            for (Semana s : d.mensual().semanas()) {
                etiquetas.add(s.etiqueta());
                ingresos.add(s.ingresos());
                gastos.add(s.gastos());
            }
            subtitulo = "Por semana \u00b7 " + Formato.mesConAnio(d.mensual().mes());
        }
        GraficoBarras barras = new GraficoBarras();
        barras.setDatos(etiquetas, ingresos, gastos, resaltado, d.moneda());
        StackPane huecoBarras = new StackPane(barras);
        fijarAlto(huecoBarras, 240);

        HBox leyendaBarras = new HBox(14, puntoLeyenda(Paleta.INGRESOS, "Ingresos"), puntoLeyenda(Paleta.GASTOS, "Gastos"));
        leyendaBarras.setAlignment(Pos.CENTER_RIGHT);
        VBox titulos = new VBox(2, etiqueta("Ingresos y gastos", "tarjeta-titulo"), etiqueta(subtitulo, "texto-suave-pequeno"));
        HBox.setHgrow(titulos, Priority.ALWAYS);
        HBox cabeceraBarras = new HBox(10, titulos, leyendaBarras);
        cabeceraBarras.setAlignment(Pos.TOP_LEFT);
        VBox tarjetaBarras = new VBox(12, cabeceraBarras, huecoBarras);
        tarjetaBarras.getStyleClass().addAll("tarjeta", "grafico-tarjeta");
        contenido.add(tarjetaBarras);

        contenido.add(d.anual() ? tarjetaAcumulado(d) : tarjetaCategorias(d));
        return new Hoja(ExportacionService.SECCION_GRAFICAS, false, contenido);
    }

    /**
     * Tarjeta "¿En qué se va tu dinero?": el anillo con el gasto por categoría, su leyenda y la categoría que más pesa
     * @param d: datos del mes
     * @return Devuelve la tarjeta
     */
    private static VBox tarjetaCategorias(DatosExportacion d) {
        List<Porcion> trozos = d.categoriasAgrupadas(MAX_CATEGORIAS);
        boolean agrupado = d.categorias().size() > MAX_CATEGORIAS;
        List<String> nombres = new ArrayList<>();
        List<BigDecimal> valores = new ArrayList<>();
        List<Color> colores = new ArrayList<>();
        VBox leyenda = new VBox(9);
        leyenda.setAlignment(Pos.CENTER_LEFT);
        for (int i = 0; i < trozos.size(); i++) {
            Porcion t = trozos.get(i);
            Color color = agrupado && i == trozos.size() - 1 ? Paleta.OTRAS : Paleta.categoria(i);
            nombres.add(t.categoria());
            valores.add(t.importe());
            colores.add(color);
            Label nombre = etiqueta(t.categoria(), "leyenda-nombre");
            nombre.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(nombre, Priority.ALWAYS);
            Label importe = etiqueta(Formato.monedaCorta(t.importe(), d.moneda()), "leyenda-importe");
            importe.setMinWidth(Region.USE_PREF_SIZE);
            HBox fila = new HBox(8, new Circle(5, color), nombre, importe);
            fila.setAlignment(Pos.CENTER_LEFT);
            leyenda.getChildren().add(fila);
        }
        GraficoDonut donut = new GraficoDonut();
        donut.setDatos(nombres, valores, colores, Formato.monedaCorta(d.gastos(), d.moneda()), "GASTO TOTAL", d.moneda());
        StackPane huecoDonut = new StackPane(donut);
        fijarTamano(huecoDonut, 150, 150);

        HBox cuerpo = new HBox(24, huecoDonut, leyenda);
        cuerpo.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(leyenda, Priority.ALWAYS);

        Region hueco = new Region();
        HBox.setHgrow(hueco, Priority.ALWAYS);
        HBox titulo = new HBox(10, etiqueta("\u00bfEn qu\u00e9 se va tu dinero?", "tarjeta-titulo"), hueco,
                etiqueta(Formato.nombreMesMayuscula(d.mensual().mes()), "texto-suave-pequeno"));
        titulo.setAlignment(Pos.CENTER_LEFT);
        VBox tarjeta = new VBox(14, titulo, cuerpo);
        tarjeta.getStyleClass().addAll("tarjeta", "grafico-tarjeta");

        if (d.gastos().signum() > 0 && !d.categorias().isEmpty()) {
            Porcion mayor = d.categorias().get(0);
            BigDecimal porcentaje = d.porcentajeDelGasto(mayor);
            StackPane icono = new StackPane(icono(IconosCategoria.para(mayor.categoria())));
            Label frase = etiqueta(mayor.categoria() + " representa el " + Formato.porcentajeCorto(porcentaje) + " de tus gastos.", "texto-suave");
            frase.setWrapText(true);
            HBox nota = new HBox(8, icono, frase);
            nota.setAlignment(Pos.CENTER_LEFT);
            nota.getStyleClass().add("nota-insight");
            tarjeta.getChildren().add(nota);
        } else {
            leyenda.getChildren().add(etiqueta("No hay gastos en " + Formato.nombreMes(d.mensual().mes()) + ".", "texto-suave"));
        }
        return tarjeta;
    }

    /**
     * Tarjeta "El esfuerzo que suma.": el balance acumulado mes a mes, como en la vista anual
     * @param d: datos del año
     * @return Devuelve la tarjeta
     */
    private static VBox tarjetaAcumulado(DatosExportacion d) {
        List<String> etiquetas = new ArrayList<>();
        for (FilaMes f : d.datosAnio().meses()) {
            etiquetas.add(Formato.mesCortoMayuscula(f.mes().getMonthValue()));
        }
        BigDecimal balance = d.datosAnio().balance();
        Label valor = etiqueta(balance.signum() < 0 ? Formato.monedaConSigno(balance, d.moneda()) : Formato.moneda(balance, d.moneda()), "kpi-valor");
        Label detalle = etiqueta("Balance acumulado \u00b7 Saldo inicial anual: " + Formato.monedaCorta(d.datosAnio().saldoInicial(), d.moneda()),
                "texto-suave-pequeno");
        detalle.setWrapText(true);
        GraficoLinea linea = new GraficoLinea();
        linea.setDatos(etiquetas, d.datosAnio().acumulado(), d.moneda());
        StackPane huecoLinea = new StackPane(linea);
        fijarAlto(huecoLinea, 170);

        VBox tarjeta = new VBox(4, etiqueta("El esfuerzo que suma.", "tarjeta-titulo"), valor, detalle, huecoLinea);
        tarjeta.getStyleClass().addAll("tarjeta", "grafico-tarjeta");
        BigDecimal tasa = d.tasaAhorro();
        if (tasa != null) {
            Label pastilla = etiqueta(tasa.signum() >= 0 ? Formato.porcentaje(tasa) + " de tus ingresos conservados"
                    : "Has gastado m\u00e1s de lo que has ingresado", "pastilla");
            HBox caja = new HBox(pastilla);
            caja.setAlignment(Pos.CENTER_LEFT);
            tarjeta.getChildren().add(caja);
        }
        return tarjeta;
    }

    /**
     * @param color: color del punto
     * @param texto: nombre de la serie
     * @return Devuelve el punto de color con su nombre, como en la leyenda de la gráfica de barras
     */
    private static HBox puntoLeyenda(Color color, String texto) {
        HBox caja = new HBox(6, new Circle(4, color), etiqueta(texto, "texto-suave-pequeno"));
        caja.setAlignment(Pos.CENTER);
        return caja;
    }

    // ===== Página: movimientos =====

    /**
     * Reparte la tabla de movimientos en tantas páginas como hagan falta
     * @param tabla: tabla de movimientos
     * @param primeraDelInforme: true si es la primera página de todo el informe (tiene una cabecera más alta)
     * @param moneda: código de moneda de los importes
     * @return Devuelve las páginas, una por cada tramo de filas que cabe
     */
    private static List<Hoja> hojasMovimientos(Tabla tabla, boolean primeraDelInforme, String moneda) {
        List<Hoja> hojas = new ArrayList<>();
        int total = tabla.filas().size();
        if (total == 0) {
            hojas.add(new Hoja(ExportacionService.SECCION_MOVIMIENTOS, false,
                    List.of(etiqueta("No hay movimientos en este periodo.", "texto-suave"))));
            return hojas;
        }
        int desde = 0;
        boolean primera = primeraDelInforme;
        while (desde < total) {
            double cabecera = primera ? ALTO_CABECERA_DOC + ALTO_TITULO_SECCION : ALTO_CABECERA_SECCION;
            double libre = ALTO - MARGEN_ARRIBA - MARGEN_ABAJO - cabecera - ALTO_PIE - ALTO_CABECERA_TABLA - HOLGURA;
            int caben = Math.max(1, (int) Math.floor(libre / ALTO_FILA));
            int hasta = Math.min(total, desde + caben);
            hojas.add(new Hoja(ExportacionService.SECCION_MOVIMIENTOS, desde > 0, List.of(tablaNodo(tabla, desde, hasta, false, moneda))));
            desde = hasta;
            primera = false;
        }
        return hojas;
    }

    // ===== Tablas =====

    /**
     * Dibuja un tramo de filas de una tabla, con el mismo aspecto que las tablas de la pantalla
     * @param tabla: tabla a dibujar
     * @param desde: primera fila (incluida)
     * @param hasta: última fila (no incluida)
     * @param conTotalAlFinal: true para añadir al final la fila del total de la tabla aunque quede fuera del tramo
     * @param moneda: código de moneda de los importes
     * @return Devuelve la tabla dibujada
     */
    private static VBox tablaNodo(Tabla tabla, int desde, int hasta, boolean conTotalAlFinal, String moneda) {
        VBox caja = new VBox();
        caja.getStyleClass().add("pdf-tabla");
        caja.getChildren().add(cabeceraTabla(tabla));
        for (int i = desde; i < hasta; i++) {
            caja.getChildren().add(filaTabla(tabla, i, moneda));
        }
        if (conTotalAlFinal && tabla.filaTotal() >= 0) {
            caja.getChildren().add(filaTabla(tabla, tabla.filaTotal(), moneda));
        }
        return caja;
    }

    /**
     * @param tabla: tabla a dibujar
     * @return Devuelve la fila de títulos (FECHA, CONCEPTO...)
     */
    private static GridPane cabeceraTabla(Tabla tabla) {
        GridPane fila = nuevaFila(tabla, "tabla-cabecera", ALTO_CABECERA_TABLA);
        for (int c = 0; c < tabla.columnas().size(); c++) {
            // En el PDF los títulos van sin la unidad entre paréntesis, como en la pantalla
            String titulo = tabla.columnas().get(c).replace(" (%)", "").toUpperCase();
            fila.add(celda(titulo, "tabla-titulo", columnaNumerica(tabla, c)), c, 0);
        }
        return fila;
    }

    /**
     * @param tabla: tabla a dibujar
     * @param indice: posición de la fila en la tabla
     * @param moneda: código de moneda de los importes
     * @return Devuelve la fila con sus celdas
     */
    private static GridPane filaTabla(Tabla tabla, int indice, String moneda) {
        boolean total = indice == tabla.filaTotal();
        GridPane fila = nuevaFila(tabla, "tabla-fila", ALTO_FILA);
        if (total) {
            fila.getStyleClass().add("tabla-total");
        }
        List<Object> valores = tabla.filas().get(indice);
        for (int c = 0; c < valores.size(); c++) {
            Object v = valores.get(c);
            fila.add(celdaDe(tabla, c, v, total, moneda), c, 0);
        }
        return fila;
    }

    /**
     * Crea una fila vacía con los anchos de columna de la tabla (los mismos en la cabecera y en las filas) y una altura fija
     * @param tabla: tabla a dibujar
     * @param clase: clase CSS de la fila
     * @param alto: altura de la fila
     * @return Devuelve la fila
     */
    private static GridPane nuevaFila(Tabla tabla, String clase, double alto) {
        GridPane fila = new GridPane();
        fila.getStyleClass().add(clase);
        for (double ancho : tabla.anchos()) {
            ColumnConstraints columna = new ColumnConstraints();
            columna.setPercentWidth(ancho);
            columna.setHgrow(Priority.ALWAYS);
            fila.getColumnConstraints().add(columna);
        }
        RowConstraints centrada = new RowConstraints();
        centrada.setValignment(VPos.CENTER);
        fila.getRowConstraints().add(centrada);
        fijarAlto(fila, alto);
        return fila;
    }

    /**
     * Crea la celda de una fila de datos con la clase de estilo que le toca según lo que contiene
     * @param tabla: tabla a la que pertenece
     * @param columna: posición de la columna
     * @param valor: contenido
     * @param total: true si es la fila del total
     * @param moneda: código de moneda de los importes
     * @return Devuelve la celda
     */
    private static Label celdaDe(Tabla tabla, int columna, Object valor, boolean total, String moneda) {
        boolean movimientos = "Fecha".equals(tabla.columnas().get(0));
        if (valor instanceof Dinero d) {
            return switch (d.modo()) {
                case NORMAL -> celda(Formato.moneda(d.valor(), moneda), total ? "tabla-numero-fuerte" : "tabla-numero", true);
                case BALANCE -> celda(Formato.monedaConSigno(d.valor(), moneda), claseBalance(d.valor()), true);
                case MOVIMIENTO -> celda(Formato.monedaConSigno(d.valor(), moneda),
                        d.valor().signum() > 0 ? "importe-ingreso" : "importe-gasto", true);
            };
        }
        if (valor instanceof Porcentaje p) {
            String texto = p.valor().signum() < 0 ? MENOS + Formato.porcentaje(p.valor().abs()) : Formato.porcentaje(p.valor());
            return celda(texto, total ? "tabla-numero-fuerte" : "tabla-texto", true);
        }
        if (valor instanceof LocalDate f) {
            return celda(Formato.fechaCorta(f.toString()), "tabla-fecha", false);
        }
        if (valor == null) {
            // Una tasa de ahorro sin ingresos se escribe con una raya, como en la pantalla; las demás celdas vacías quedan en blanco
            boolean tasa = tabla.columnas().get(columna).startsWith("Tasa");
            return celda(tasa ? "\u2014" : "", total ? "tabla-numero-fuerte" : "tabla-texto", tasa);
        }
        String clase;
        if (movimientos) {
            clase = columna == 1 ? "tabla-concepto" : "tabla-texto";
        } else {
            clase = columna == 0 ? "tabla-cuenta" : "tabla-texto";
        }
        return celda(valor.toString(), clase, false);
    }

    /**
     * @param tabla: tabla
     * @param columna: posición de la columna
     * @return Devuelve true si la columna lleva cifras (se alinea a la derecha)
     */
    private static boolean columnaNumerica(Tabla tabla, int columna) {
        for (List<Object> fila : tabla.filas()) {
            Object v = fila.get(columna);
            if (v != null) {
                return v instanceof Dinero || v instanceof Porcentaje;
            }
        }
        return tabla.columnas().get(columna).startsWith("Tasa");
    }

    /**
     * Crea una celda de texto que se corta con puntos suspensivos si no cabe
     * @param texto: contenido
     * @param clase: clase CSS
     * @param derecha: true para alinear el texto a la derecha
     * @return Devuelve la etiqueta
     */
    private static Label celda(String texto, String clase, boolean derecha) {
        Label etiqueta = etiqueta(texto, clase);
        etiqueta.setMaxWidth(Double.MAX_VALUE);
        etiqueta.setAlignment(derecha ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        GridPane.setHalignment(etiqueta, derecha ? HPos.RIGHT : HPos.LEFT);
        return etiqueta;
    }

    /**
     * @param balance: importe del balance
     * @return Devuelve la clase CSS según sea positivo (verde), negativo (rojo) o cero (gris)
     */
    private static String claseBalance(BigDecimal balance) {
        if (balance.signum() > 0) {
            return "balance-positivo";
        }
        return balance.signum() < 0 ? "balance-negativo" : "balance-cero";
    }

    // ===== Piezas =====

    /**
     * @param texto: contenido
     * @param clase: clase CSS
     * @return Devuelve una etiqueta con esa clase
     */
    private static Label etiqueta(String texto, String clase) {
        Label etiqueta = new Label(texto);
        etiqueta.getStyleClass().add(clase);
        return etiqueta;
    }

    /**
     * @return Devuelve una línea fina horizontal
     */
    private static Region linea() {
        Region linea = new Region();
        linea.getStyleClass().add("pdf-linea");
        fijarAlto(linea, 1);
        return linea;
    }

    /**
     * Crea un icono de 16 píxeles
     * @param contenido: trazo SVG en una cuadrícula de 24x24
     * @return Devuelve el icono
     */
    private static Group icono(String contenido) {
        SVGPath trazo = new SVGPath();
        trazo.setContent(contenido);
        trazo.getStyleClass().add("icono");
        trazo.setScaleX(ESCALA_ICONO);
        trazo.setScaleY(ESCALA_ICONO);
        return new Group(trazo);
    }

    /**
     * Fija la altura de un elemento (mínima, preferida y máxima iguales)
     * @param nodo: elemento
     * @param alto: altura
     */
    private static void fijarAlto(Region nodo, double alto) {
        nodo.setMinHeight(alto);
        nodo.setPrefHeight(alto);
        nodo.setMaxHeight(alto);
    }

    /**
     * Fija el tamaño de un elemento (mínimo, preferido y máximo iguales)
     * @param nodo: elemento
     * @param ancho: ancho
     * @param alto: alto
     */
    private static void fijarTamano(Region nodo, double ancho, double alto) {
        nodo.setMinSize(ancho, alto);
        nodo.setPrefSize(ancho, alto);
        nodo.setMaxSize(ancho, alto);
    }

    // ===== Foto de la página =====

    /**
     * Fotografía una página dibujada fuera de pantalla y la deja comprimida para el PDF
     * @param pagina: página montada
     * @param css: hoja de estilos de la aplicación
     * @return Devuelve la página del PDF
     * @throws IOException si falla la compresión
     */
    private static Pagina fotografiar(VBox pagina, String css) throws IOException {
        // La escena no se muestra: solo hace falta para que se aplique el CSS y se coloque todo
        Scene escena = new Scene(pagina);
        escena.getStylesheets().add(css);
        // El recorte evita que las sombras de las tarjetas ensanchen la foto más allá de la página
        pagina.setClip(new Rectangle(ANCHO, ALTO));
        pagina.applyCss();
        pagina.resize(ANCHO, ALTO);
        pagina.layout();

        SnapshotParameters parametros = new SnapshotParameters();
        parametros.setFill(Color.WHITE);
        parametros.setTransform(new Scale(ESCALA, ESCALA));
        WritableImage imagen = pagina.snapshot(parametros, null);

        int ancho = (int) imagen.getWidth();
        int alto = (int) imagen.getHeight();
        byte[] bgra = new byte[ancho * alto * 4];
        PixelReader lector = imagen.getPixelReader();
        lector.getPixels(0, 0, ancho, alto, PixelFormat.getByteBgraInstance(), bgra, 0, ancho * 4);
        // De BGRA a RGB, mezclando con el blanco lo que sea transparente
        byte[] rgb = new byte[ancho * alto * 3];
        for (int i = 0, o = 0; i < bgra.length; i += 4, o += 3) {
            int a = bgra[i + 3] & 0xFF;
            int b = bgra[i] & 0xFF;
            int g = bgra[i + 1] & 0xFF;
            int r = bgra[i + 2] & 0xFF;
            if (a != 255) {
                r = (r * a + 255 * (255 - a)) / 255;
                g = (g * a + 255 * (255 - a)) / 255;
                b = (b * a + 255 * (255 - a)) / 255;
            }
            rgb[o] = (byte) r;
            rgb[o + 1] = (byte) g;
            rgb[o + 2] = (byte) b;
        }
        return Pagina.desdeRgb(ancho, alto, rgb);
    }
}
