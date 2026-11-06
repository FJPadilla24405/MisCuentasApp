package service.movimiento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import models.Categoria;
import models.Cuenta;
import models.Movimiento;
import models.Recurrente;
import models.Usuario;
import service.DatoInvalido;
import service.categoria.CategoriaService;
import service.dao.CuentaDAO;
import service.dao.MovimientoDAO;
import service.dao.RecurrenteDAO;
import util.Dinero;

/**
 * Crea movimientos (ingresos y gastos) desde el formulario de añadir movimiento.
 *
 * @author Fco Padilla
 */
public class MovimientoService {

    // Longitudes máximas: descripcion es VARCHAR(100) en el esquema; la nota, VARCHAR(255)
    private static final int MAX_CONCEPTO = 100;
    private static final int MAX_NOTA = 255;

    private final MovimientoDAO movimientoDAO = new MovimientoDAO();
    private final CuentaDAO cuentaDAO = new CuentaDAO();
    private final RecurrenteDAO recurrenteDAO = new RecurrenteDAO();
    private final CategoriaService categoriaService = new CategoriaService();

    /**
     * Cuentas activas donde se puede apuntar un movimiento
     * @param usuario: usuario de la sesión
     * @return Devuelve las cuentas activas
     */
    public List<Cuenta> cuentasDisponibles(Usuario usuario) {
        return cuentaDAO.listarPorUsuario(usuario.getId());
    }

    /**
     * Categorías entre las que se puede elegir según el tipo del movimiento
     * @param usuario: usuario de la sesión
     * @param tipo: "gasto" o "ingreso"
     * @return Devuelve las categorías de ese tipo
     */
    public List<Categoria> categoriasDisponibles(Usuario usuario, String tipo) {
        return categoriaService.categoriasDeTipo(usuario, tipo);
    }

    /**
     * Valida los datos y guarda un movimiento. Si algo no es válido no se guarda nada. El importe se
     * escribe siempre en positivo: que sea un gasto o un ingreso lo dice el tipo.
     * Si se marca como recurrente se crea también su recurrencia mensual (el día del mes de la fecha) y se
     * enlaza con el movimiento; todavía no hay nada que genere los movimientos de los meses siguientes.
     * @param usuario: usuario de la sesión
     * @param cuentaId: id de la cuenta
     * @param categoriaId: id de la categoría (tiene que ser del mismo tipo que el movimiento)
     * @param tipo: "gasto" o "ingreso"
     * @param importe: importe positivo
     * @param fecha: fecha del movimiento
     * @param concepto: concepto, obligatorio
     * @param nota: nota opcional (puede ser null)
     * @param recurrente: true para marcarlo como recurrente
     * @return Devuelve el movimiento guardado
     * @throws DatoInvalido si algún dato no es válido
     */
    public Movimiento guardar(Usuario usuario, Integer cuentaId, Integer categoriaId, String tipo, BigDecimal importe,
            LocalDate fecha, String concepto, String nota, boolean recurrente) {
        if (!"gasto".equals(tipo) && !"ingreso".equals(tipo)) {
            throw new DatoInvalido("tipo", "Elige si es un gasto o un ingreso.");
        }
        if (importe == null || importe.signum() <= 0) {
            throw new DatoInvalido("importe", "El importe tiene que ser mayor que cero.");
        }
        BigDecimal importeNormalizado;
        try {
            importeNormalizado = Dinero.normalizar(importe);
        } catch (IllegalArgumentException e) {
            throw new DatoInvalido("importe", "El importe es demasiado grande.");
        }
        String conceptoLimpio = concepto == null ? "" : concepto.trim();
        if (conceptoLimpio.isEmpty() || conceptoLimpio.length() > MAX_CONCEPTO) {
            throw new DatoInvalido("concepto", conceptoLimpio.isEmpty() ? "Escribe el concepto del movimiento."
                    : "El concepto no puede superar " + MAX_CONCEPTO + " caracteres.");
        }
        Cuenta cuenta = cuentasDisponibles(usuario).stream().filter(c -> cuentaId != null && c.getId() == cuentaId).findFirst().orElse(null);
        if (cuenta == null) {
            throw new DatoInvalido("cuenta", "Elige una cuenta.");
        }
        if (fecha == null) {
            throw new DatoInvalido("fecha", "Escribe una fecha válida (dd/mm/aaaa).");
        }
        Categoria categoria = categoriasDisponibles(usuario, tipo).stream()
                .filter(c -> categoriaId != null && c.getId() == categoriaId).findFirst().orElse(null);
        if (categoria == null) {
            throw new DatoInvalido("categoria", "Elige una categoría de " + tipo + ".");
        }
        String notaLimpia = nota == null || nota.isBlank() ? null : nota.trim();
        if (notaLimpia != null && notaLimpia.length() > MAX_NOTA) {
            throw new DatoInvalido("nota", "La nota no puede superar " + MAX_NOTA + " caracteres.");
        }

        Movimiento m = new Movimiento(0, usuario.getId(), cuenta.getId(), categoria.getId(), importeNormalizado, tipo,
                fecha.toString(), conceptoLimpio, false, 0);
        m.setNota(notaLimpia);
        if (recurrente) {
            Recurrente r = recurrenteDAO.insertar(new Recurrente(0, "mensual", fecha.getDayOfMonth(), true, fecha.toString()));
            m.setRecurrenteId(r.getId());
        }
        return movimientoDAO.insertar(m);
    }
}
