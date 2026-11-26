package service.sync;

import java.time.Instant;

/**
 * Lo que hace falta para decidir si subir o descargar: cuándo se cambiaron por última vez los datos de este equipo y
 * los de Firebase, y si los de un lado se perderían al copiar los del otro.
 *
 * @param local: cuándo se cambiaron por última vez los datos de este equipo (null si no se sabe)
 * @param firebase: cuándo se subieron por última vez los datos a Firebase (null si no hay datos o no llevan fecha)
 * @param hayDatosEnFirebase: true si en Firebase hay datos de este usuario
 * @param cambiosLocalesSinSubir: true si en este equipo hay cambios que no están en Firebase (descargar los perdería)
 * @param firebaseConNovedades: true si en Firebase hay datos que este equipo no ha descargado: porque se subieron desde
 *        otro sitio después de la última sincronización de este equipo, o porque este equipo nunca se ha sincronizado
 *        (subir los perdería)
 * @author Fco Padilla
 */
public record FechasSync(Instant local, Instant firebase, boolean hayDatosEnFirebase, boolean cambiosLocalesSinSubir,
        boolean firebaseConNovedades) {
}
