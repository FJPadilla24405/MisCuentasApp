package service.movimiento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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
 * Crea, edita y elimina movimientos (ingresos y gastos) desde los formularios, y desactiva o reactiva sus recurrencias.
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
     * Si se elige una frecuencia (diaria, semanal, mensual o anual) se crea también su regla recurrente y se enlaza
     * con el movimiento; RecurrenteService se encarga de añadir los movimientos siguientes cuando llegue su fecha.
     * @param usuario: usuario de la sesión
     * @param cuentaId: id de la cuenta
     * @param categoriaId: id de la categoría (tiene que ser del mismo tipo que el movimiento)
     * @param tipo: "gasto" o "ingreso"
     * @param importe: importe positivo
     * @param fecha: fecha del movimiento
     * @param concepto: concepto, obligatorio
     * @param nota: nota opcional (puede ser null)
     * @param frecuencia: cada cuánto se repite, o null si es un movimiento puntual
     * @return Devuelve el movimiento guardado
     * @throws DatoInvalido si algún dato no es válido
     */
    public Movimiento guardar(Usuario usuario, Integer cuentaId, Integer categoriaId, String tipo, BigDecimal importe,
            LocalDate fecha, String concepto, String nota, Frecuencia frecuencia) {
        Movimiento m = validar(usuario, cuentaId, categoriaId, tipo, importe, fecha, concepto, nota);
        if (frecuencia != null) {
            Recurrente r = recurrenteDAO.insertar(new Recurrente(0, frecuencia.getCodigo(), frecuencia.diaEjecucion(fecha), true, fecha.toString()));
            m.setRecurrenteId(r.getId());
        }
        return movimientoDAO.insertar(m);
    }

    /**
     * Valida los datos y guarda los cambios de un movimiento que ya existe. Los datos se validan igual que al
     * crearlo. Si el movimiento es recurrente sigue siéndolo: no se cambia su frecuencia. Si es el primero de su
     * recurrencia y se le cambia la fecha, la recurrencia pasa a seguir ese día; y como los movimientos nuevos se
     * copian del último, cambiar el último también cambia los que se generen a partir de ahora.
     * @param usuario: usuario de la sesión
     * @param movimientoId: id del movimiento a cambiar
     * @param cuentaId: id de la cuenta
     * @param categoriaId: id de la categoría (tiene que ser del mismo tipo que el movimiento)
     * @param tipo: "gasto" o "ingreso"
     * @param importe: importe positivo
     * @param fecha: fecha del movimiento
     * @param concepto: concepto, obligatorio
     * @param nota: nota opcional (puede ser null)
     * @return Devuelve el movimiento con sus cambios
     * @throws DatoInvalido si algún dato no es válido o el movimiento ya no existe
     */
    public Movimiento actualizar(Usuario usuario, int movimientoId, Integer cuentaId, Integer categoriaId, String tipo,
            BigDecimal importe, LocalDate fecha, String concepto, String nota) {
        Movimiento existente = propio(usuario, movimientoId);
        Movimiento m = validar(usuario, cuentaId, categoriaId, tipo, importe, fecha, concepto, nota);
        m.setId(existente.getId());
        m.setRecurrenteId(existente.getRecurrenteId());
        boolean cambiaFecha = !existente.getFecha().equals(m.getFecha());
        movimientoDAO.actualizar(m);

        if (cambiaFecha && existente.getRecurrenteId() > 0) {
            // Si es el primero de la recurrencia, su día es el que marca cuándo se repite
            List<Movimiento> deLaRegla = movimientoDAO.listarPorRecurrente(existente.getRecurrenteId());
            Optional<Recurrente> regla = recurrenteDAO.buscarPorId(existente.getRecurrenteId());
            if (regla.isPresent() && !deLaRegla.isEmpty() && deLaRegla.get(0).getId() == m.getId()) {
                Frecuencia frecuencia = Frecuencia.deCodigo(regla.get().getFrecuencia());
                if (frecuencia != null) {
                    regla.get().setDiaEjecucion(frecuencia.diaEjecucion(fecha));
                    recurrenteDAO.actualizar(regla.get());
                }
            }
        }
        return m;
    }

    /**
     * Elimina un movimiento. Si es de una recurrencia, los demás siguen y el borrado no se vuelve a generar. Si era el
     * último movimiento de la recurrencia, esta deja de generar porque ya no tiene nada que copiar.
     * @param usuario: usuario de la sesión
     * @param movimientoId: id del movimiento
     * @throws DatoInvalido si el movimiento ya no existe
     */
    public void eliminar(Usuario usuario, int movimientoId) {
        movimientoDAO.eliminar(propio(usuario, movimientoId).getId());
    }

    /**
     * @param movimiento: movimiento
     * @return Devuelve la regla recurrente de la que nace (esté activa o desactivada), o vacío si es un movimiento puntual
     */
    public Optional<Recurrente> reglaDe(Movimiento movimiento) {
        return movimiento.getRecurrenteId() > 0 ? recurrenteDAO.buscarPorId(movimiento.getRecurrenteId()) : Optional.empty();
    }

    /**
     * Desactiva la recurrencia de un movimiento: no se añaden más movimientos solos. Los que ya existen se quedan.
     * @param usuario: usuario de la sesión
     * @param movimientoId: id de cualquier movimiento de la recurrencia
     * @throws DatoInvalido si el movimiento ya no existe o no es recurrente
     */
    public void desactivarRecurrencia(Usuario usuario, int movimientoId) {
        recurrenteDAO.desactivar(reglaDeMovimiento(usuario, movimientoId).getId());
    }

    /**
     * Vuelve a activar la recurrencia de un movimiento. Se retoma desde hoy: los plazos que pasaron mientras estuvo
     * desactivada no se añaden.
     * @param usuario: usuario de la sesión
     * @param movimientoId: id de cualquier movimiento de la recurrencia
     * @throws DatoInvalido si el movimiento ya no existe o no es recurrente
     */
    public void reactivarRecurrencia(Usuario usuario, int movimientoId) {
        recurrenteDAO.reactivar(reglaDeMovimiento(usuario, movimientoId).getId(), LocalDate.now().toString());
    }

    /**
     * @param usuario: usuario de la sesión
     * @param movimientoId: id del movimiento
     * @return Devuelve la regla del movimiento
     * @throws DatoInvalido si el movimiento no existe o no es recurrente
     */
    private Recurrente reglaDeMovimiento(Usuario usuario, int movimientoId) {
        Movimiento m = propio(usuario, movimientoId);
        return reglaDe(m).orElseThrow(() -> new DatoInvalido("movimiento", "Este movimiento no es recurrente."));
    }

    /**
     * @param usuario: usuario de la sesión
     * @param movimientoId: id del movimiento
     * @return Devuelve el movimiento si existe y es del usuario
     * @throws DatoInvalido si no existe o es de otro usuario
     */
    private Movimiento propio(Usuario usuario, int movimientoId) {
        return movimientoDAO.buscarPorId(movimientoId).filter(m -> m.getUsuarioId() == usuario.getId())
                .orElseThrow(() -> new DatoInvalido("movimiento", "Ese movimiento ya no existe."));
    }

    /**
     * Comprueba todos los datos de un movimiento y crea el objeto (sin guardarlo) con el importe normalizado
     * @return Devuelve el movimiento nuevo, con id 0 y sin recurrencia
     * @throws DatoInvalido si algún dato no es válido
     */
    private Movimiento validar(Usuario usuario, Integer cuentaId, Integer categoriaId, String tipo, BigDecimal importe,
            LocalDate fecha, String concepto, String nota) {
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
        return m;
    }
}
