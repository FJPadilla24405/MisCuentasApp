package models;

public class Usuario {

    /**
     * Las cuentas creadas solo en este equipo no tienen cuenta en Firebase. Como usuarios.firebase_uid es
     * NOT NULL y UNIQUE, se les da un identificador propio que empieza por este prefijo.
     */
    public static final String PREFIJO_UID_LOCAL = "local-";

    private int id;
    private String firebaseUid;
    private String nombre;
    private String email;
    private String moneda;
    private String tema;

    public Usuario() {
    }

    public Usuario(int id, String firebaseUid, String nombre, String email, String moneda, String tema) {
        this.id = id;
        this.firebaseUid = firebaseUid;
        this.nombre = nombre;
        this.email = email;
        this.moneda = moneda;
        this.tema = tema;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getFirebaseUid() {
        return firebaseUid;
    }

    public void setFirebaseUid(String firebaseUid) {
        this.firebaseUid = firebaseUid;
    }

    /** @return true si es una cuenta creada solo en este equipo (sin Firebase: no se puede sincronizar). */
    public boolean isSoloLocal() {
        return firebaseUid != null && firebaseUid.startsWith(PREFIJO_UID_LOCAL);
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getMoneda() {
        return moneda;
    }

    public void setMoneda(String moneda) {
        this.moneda = moneda;
    }

    public String getTema() {
        return tema;
    }

    public void setTema(String tema) {
        this.tema = tema;
    }

    @Override
    public String toString() {
        return "Usuario{id=" + id + ", nombre='" + nombre + "', email='" + email + "'}";
    }
}
