package service.perfil;

import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import models.Cuenta;
import models.Usuario;
import service.auth.SesionActual;
import service.dao.CuentaDAO;
import service.dao.UsuarioDAO;
import service.resumen.ResumenService.FilaCuenta;

/**
 * Lógica de la pantalla de editar perfil: datos personales, preferencias y cuentas.
 * Las validaciones lanzan DatoInvalido con un mensaje en español listo para mostrar.
 *
 * @author Fco Padilla
 */
public class PerfilService {

    /**
     * Un dato que no es válido.
     */
    public static class DatoInvalido extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
        private final String campo;

        /**
         * @param campo: campo que falla ("nombre", "email", "moneda" o "tema")
         * @param mensaje: texto para el usuario
         */
        public DatoInvalido(String campo, String mensaje) {
            super(mensaje);
            this.campo = campo;
        }

        /**
         * @return Devuelve el campo que falla
         */
        public String getCampo() {
            return campo;
        }
    }

    // Temas que se pueden elegir (de momento solo se guarda la elección, no cambia el aspecto)
    public static final List<String> TEMAS = List.of("claro", "oscuro", "sistema");
    // Longitud máxima de nombre y email, la misma que los VARCHAR(50) del esquema
    private static final int MAX = 50;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UsuarioDAO usuarioDAO = new UsuarioDAO();
    private final CuentaDAO cuentaDAO = new CuentaDAO();

    /**
     * Guarda los datos personales y las preferencias. Si no es válido nada se guarda ni se cambia.
     * El email solo se puede cambiar en cuentas offline: el de una cuenta online es el de Firebase.
     * Al cambiar la moneda se cambia también la de todas las cuentas del usuario (solo cambia cómo se
     * muestran: los importes no se convierten).
     * @param usuario: usuario a modificar (se cambia el mismo objeto)
     * @param nombre: nombre de usuario
     * @param email: email
     * @param moneda: código de moneda de tres letras
     * @param tema: "claro", "oscuro" o "sistema"
     * @return Devuelve el usuario con los datos nuevos
     * @throws DatoInvalido si algún dato no es válido
     */
    public Usuario guardar(Usuario usuario, String nombre, String email, String moneda, String tema) {
        String nombreLimpio = nombre == null ? "" : nombre.trim();
        if (nombreLimpio.isEmpty() || nombreLimpio.length() > MAX) {
            throw new DatoInvalido("nombre", nombreLimpio.isEmpty() ? "Escribe tu nombre."
                    : "El nombre no puede superar " + MAX + " caracteres.");
        }

        String emailLimpio = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (usuario.isSoloLocal()) {
            if (!EMAIL.matcher(emailLimpio).matches() || emailLimpio.length() > MAX) {
                throw new DatoInvalido("email", "Escribe un email válido (máximo " + MAX + " caracteres).");
            }
            Optional<Usuario> otro = usuarioDAO.buscarPorEmail(emailLimpio);
            if (otro.isPresent() && otro.get().getId() != usuario.getId()) {
                throw new DatoInvalido("email", "Ya existe otra cuenta con ese email en este equipo.");
            }
        } else if (!emailLimpio.equalsIgnoreCase(usuario.getEmail())) {
            throw new DatoInvalido("email", "El email de una cuenta online no se puede cambiar desde aquí.");
        } else {
            emailLimpio = usuario.getEmail();
        }

        try {
            Currency.getInstance(moneda);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new DatoInvalido("moneda", "Elige una moneda válida.");
        }
        if (!TEMAS.contains(tema)) {
            throw new DatoInvalido("tema", "Elige un tema válido.");
        }

        boolean cambiaMoneda = !moneda.equals(usuario.getMoneda());
        usuario.setNombre(nombreLimpio);
        usuario.setEmail(emailLimpio);
        usuario.setMoneda(moneda);
        usuario.setTema(tema);
        usuarioDAO.actualizar(usuario);
        if (cambiaMoneda) {
            for (Cuenta c : cuentaDAO.listarTodasPorUsuario(usuario.getId())) {
                c.setMoneda(moneda);
                cuentaDAO.actualizar(c);
            }
        }
        SesionActual.actualizarUsuario(usuario);
        return usuario;
    }

    /**
     * Todas las cuentas del usuario con su saldo, también las desactivadas (las activas primero).
     * @param usuario: usuario de la sesión
     * @return Devuelve las cuentas con su saldo
     */
    public List<FilaCuenta> listarCuentas(Usuario usuario) {
        List<FilaCuenta> filas = new ArrayList<>();
        for (Cuenta c : cuentaDAO.listarTodasPorUsuario(usuario.getId())) {
            filas.add(new FilaCuenta(c, cuentaDAO.calcularSaldo(c.getId())));
        }
        return filas;
    }

    /**
     * Cambia el nombre de una cuenta
     * @param usuario: dueño de la cuenta
     * @param cuenta: cuenta a renombrar (se cambia el mismo objeto)
     * @param nombre: nombre nuevo
     * @throws DatoInvalido si está vacío, es demasiado largo o ya hay otra cuenta con ese nombre
     */
    public void renombrarCuenta(Usuario usuario, Cuenta cuenta, String nombre) {
        String limpio = nombre == null ? "" : nombre.trim();
        if (limpio.isEmpty() || limpio.length() > MAX) {
            throw new DatoInvalido("nombre", limpio.isEmpty() ? "Escribe un nombre para la cuenta."
                    : "El nombre no puede superar " + MAX + " caracteres.");
        }
        boolean repetido = cuentaDAO.listarTodasPorUsuario(usuario.getId()).stream()
                .anyMatch(c -> c.getId() != cuenta.getId() && c.getNombre().trim().equalsIgnoreCase(limpio));
        if (repetido) {
            throw new DatoInvalido("nombre", "Ya tienes una cuenta con ese nombre.");
        }
        cuenta.setNombre(limpio);
        cuentaDAO.actualizar(cuenta);
    }

    /**
     * Activa o desactiva una cuenta. Una cuenta desactivada no aparece en la barra lateral ni cuenta en
     * la vista general, pero conserva sus movimientos.
     * @param cuenta: cuenta a cambiar (se cambia el mismo objeto)
     * @param activa: true para activarla, false para desactivarla
     */
    public void cambiarEstadoCuenta(Cuenta cuenta, boolean activa) {
        cuenta.setActiva(activa);
        cuentaDAO.actualizar(cuenta);
    }
}
