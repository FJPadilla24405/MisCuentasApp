package models;

/**
 * Usuario de la aplicación (tabla usuarios): la persona que ha creado una cuenta, ya sea online (con
 * Firebase) o solo local en este equipo. Es el dueño de las cuentas, categorías, movimientos y presupuestos.
 *
 * @author Fco Padilla
 */
public class Usuario {

    /**
     * Las cuentas creadas solo en este equipo no tienen cuenta en Firebase. Como usuarios.firebase_uid es
     * NOT NULL y UNIQUE, se les da un identificador propio que empieza por este prefijo.
     */
    public static final String PREFIJO_UID_LOCAL = "local-";

    // Identificador local (clave primaria autoincremental de SQLite)
    private int id;
    // Identificador del usuario en Firebase Authentication; en las cuentas solo locales empieza por PREFIJO_UID_LOCAL
    private String firebaseUid;
    // Nombre de usuario (máximo 50 caracteres en la base de datos)
    private String nombre;
    // Correo electrónico con el que se inicia sesión (máximo 50 caracteres en la base de datos)
    private String email;
    // Código de moneda de tres letras (EUR, USD...) que usan sus cuentas nuevas
    private String moneda;
    // Tema visual de la interfaz (por defecto "claro")
    private String tema;
    // Huella (hash con sal) de la contraseña, nunca la contraseña. Ver service.auth.ContrasenaLocal. Solo se guarda en local
    private String passwordHash;

    /**
     * Constructor vacío
     */
    public Usuario() {
    }

    /**
     * Crea un usuario sin huella de contraseña (se asigna después con setPasswordHash).
     * @param id: identificador local del usuario
     * @param firebaseUid: identificador en Firebase Authentication (o local-... si la cuenta es solo local)
     * @param nombre: nombre de usuario
     * @param email: correo electrónico
     * @param moneda: código de moneda de tres letras
     * @param tema: tema visual de la interfaz
     */
    public Usuario(int id, String firebaseUid, String nombre, String email, String moneda, String tema) {
        this.id = id;
        this.firebaseUid = firebaseUid;
        this.nombre = nombre;
        this.email = email;
        this.moneda = moneda;
        this.tema = tema;
    }

    /** @return Devuelve el identificador local del usuario */
    public int getId() {
        return id;
    }

    /** @param id: identificador local del usuario */
    public void setId(int id) {
        this.id = id;
    }

    /** @return Devuelve el identificador del usuario en Firebase (o el identificador local si la cuenta es solo local) */
    public String getFirebaseUid() {
        return firebaseUid;
    }

    /** @param firebaseUid: identificador en Firebase o identificador local */
    public void setFirebaseUid(String firebaseUid) {
        this.firebaseUid = firebaseUid;
    }

    /** @return true si es una cuenta creada solo en este equipo (sin Firebase: no se puede sincronizar). */
    public boolean isSoloLocal() {
        return firebaseUid != null && firebaseUid.startsWith(PREFIJO_UID_LOCAL);
    }

    /** @return Devuelve el nombre de usuario */
    public String getNombre() {
        return nombre;
    }

    /** @param nombre: nombre de usuario */
    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    /** @return Devuelve el correo electrónico del usuario */
    public String getEmail() {
        return email;
    }

    /** @param email: correo electrónico del usuario */
    public void setEmail(String email) {
        this.email = email;
    }

    /** @return Devuelve el código de moneda del usuario (tres letras) */
    public String getMoneda() {
        return moneda;
    }

    /** @param moneda: código de moneda de tres letras */
    public void setMoneda(String moneda) {
        this.moneda = moneda;
    }

    /** @return Devuelve el tema visual elegido por el usuario */
    public String getTema() {
        return tema;
    }

    /** @param tema: tema visual de la interfaz */
    public void setTema(String tema) {
        this.tema = tema;
    }

    /**
     * @return Devuelve la huella de la contraseña (null si la cuenta es anterior a las contraseñas locales)
     */
    public String getPasswordHash() {
        return passwordHash;
    }

    /**
     * @param passwordHash: huella de la contraseña calculada con ContrasenaLocal.hashear
     */
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /**
     * @return Devuelve un texto corto con el id, el nombre y el email (nunca la contraseña ni su huella)
     */
    @Override
    public String toString() {
        return "Usuario{id=" + id + ", nombre='" + nombre + "', email='" + email + "'}";
    }
}
