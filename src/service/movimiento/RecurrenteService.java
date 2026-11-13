package service.movimiento;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import models.Cuenta;
import models.Movimiento;
import models.Recurrente;
import models.Usuario;
import service.dao.CuentaDAO;
import service.dao.MovimientoDAO;
import service.dao.RecurrenteDAO;

/**
 * Hace que los movimientos recurrentes funcionen solos: cada vez que se cumple el plazo de una regla (cada día, cada
 * semana, cada mes o cada año) crea un movimiento igual al último de esa regla, con la fecha que toca. Funciona igual
 * para ingresos y para gastos.
 *
 * La regla (tabla recurrentes) solo guarda la frecuencia y la fecha de la última repetición; el importe, la cuenta, la
 * categoría, el concepto y la nota se copian del último movimiento enlazado a ella, y la fecha de partida es la del
 * primero. Si pasó más de un plazo sin abrir la aplicación, se crean todos los que faltan, cada uno con su fecha.
 *
 * @author Fco Padilla
 */
public class RecurrenteService {

    // Tope de repeticiones que se miran por regla, para no quedarse dando vueltas con una fecha absurda
    private static final long MAX_REPETICIONES = 40_000;

    private final RecurrenteDAO recurrenteDAO = new RecurrenteDAO();
    private final MovimientoDAO movimientoDAO = new MovimientoDAO();
    private final CuentaDAO cuentaDAO = new CuentaDAO();

    /**
     * Crea los movimientos de todas las repeticiones que ya tocan hasta hoy (incluido hoy)
     * @param usuario: usuario de la sesión
     * @return Devuelve cuántos movimientos se han creado
     */
    public int generarPendientes(Usuario usuario) {
        return generarPendientes(usuario, LocalDate.now());
    }

    /**
     * Crea los movimientos de todas las repeticiones que tocan hasta una fecha
     * @param usuario: usuario de la sesión
     * @param hasta: último día que se tiene en cuenta (normalmente hoy)
     * @return Devuelve cuántos movimientos se han creado
     */
    public int generarPendientes(Usuario usuario, LocalDate hasta) {
        Set<Integer> cuentasActivas = new HashSet<>();
        for (Cuenta c : cuentaDAO.listarPorUsuario(usuario.getId())) {
            cuentasActivas.add(c.getId());
        }
        int creados = 0;
        for (Recurrente regla : recurrenteDAO.listarActivosPorUsuario(usuario.getId())) {
            try {
                creados += generarDeRegla(usuario, regla, hasta, cuentasActivas);
            } catch (RuntimeException e) {
                // Una regla con datos raros (una categoría borrada, una fecha mal escrita) no impide que se generen las demás
                System.err.println("No se pudo generar la recurrencia " + regla.getId() + ": " + e.getMessage());
            }
        }
        return creados;
    }

    /**
     * Crea los movimientos que faltan de una regla
     * @param usuario: usuario de la sesión
     * @param regla: regla recurrente activa
     * @param hasta: último día que se tiene en cuenta
     * @param cuentasActivas: ids de las cuentas activas del usuario (si la cuenta del movimiento ya no está, no se genera nada)
     * @return Devuelve cuántos movimientos se han creado de esta regla
     */
    private int generarDeRegla(Usuario usuario, Recurrente regla, LocalDate hasta, Set<Integer> cuentasActivas) {
        Frecuencia frecuencia = Frecuencia.deCodigo(regla.getFrecuencia());
        List<Movimiento> movimientos = movimientoDAO.listarPorRecurrente(regla.getId());
        if (frecuencia == null || movimientos.isEmpty()) {
            return 0;
        }
        // Ordenados por fecha: el primero marca el día de partida y el último es el modelo que se copia
        Movimiento primero = movimientos.get(0);
        Movimiento modelo = movimientos.get(movimientos.size() - 1);
        if (!cuentasActivas.contains(modelo.getCuentaId())) {
            return 0;
        }
        LocalDate inicio = leerFecha(primero.getFecha());
        if (inicio == null) {
            return 0;
        }
        LocalDate ultima = leerFecha(regla.getUltimaEjecucion());
        if (ultima == null) {
            ultima = inicio;
        }
        Set<String> fechasYaCreadas = new HashSet<>();
        for (Movimiento m : movimientos) {
            fechasYaCreadas.add(m.getFecha());
        }

        int creados = 0;
        LocalDate ultimaRepeticion = null;
        for (long n = 1; n <= MAX_REPETICIONES; n++) {
            LocalDate fecha = frecuencia.fechaDe(inicio, regla.getDiaEjecucion(), n);
            if (fecha.isAfter(hasta)) {
                break;
            }
            if (!fecha.isAfter(ultima)) {
                continue;
            }
            // Si ese día ya hay un movimiento de la regla (por ejemplo, llegado desde otro equipo al sincronizar), no se duplica
            if (!fechasYaCreadas.contains(fecha.toString())) {
                Movimiento nuevo = new Movimiento(0, usuario.getId(), modelo.getCuentaId(), modelo.getCategoriaId(),
                        modelo.getImporte(), modelo.getTipo(), fecha.toString(), modelo.getDescripcion(), false, regla.getId());
                nuevo.setNota(modelo.getNota());
                movimientoDAO.insertar(nuevo);
                creados++;
            }
            ultimaRepeticion = fecha;
        }
        if (ultimaRepeticion != null) {
            recurrenteDAO.marcarEjecutado(regla.getId(), ultimaRepeticion.toString());
        }
        return creados;
    }

    /**
     * @param texto: fecha ISO (aaaa-mm-dd) o null
     * @return Devuelve la fecha, o null si no hay o no es válida
     */
    private static LocalDate leerFecha(String texto) {
        if (texto == null) {
            return null;
        }
        try {
            return LocalDate.parse(texto);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
