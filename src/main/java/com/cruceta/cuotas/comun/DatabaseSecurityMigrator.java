package com.cruceta.cuotas.comun;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Asegura de forma idempotente que las columnas de seguridad de la tabla usuario
 * existan incluso en bases de datos MySQL creadas con versiones anteriores.
 */
@Component
public class DatabaseSecurityMigrator {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSecurityMigrator.class);
    private final JdbcTemplate jdbc;

    public DatabaseSecurityMigrator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void migrarEsquemaSeguridad() {
        try {
            Integer len = jdbc.queryForObject("""
                    SELECT CHARACTER_MAXIMUM_LENGTH
                    FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'usuario' AND COLUMN_NAME = 'contrasena'
                    """, Integer.class);
            if (len != null && len < 100) {
                jdbc.execute("ALTER TABLE usuario MODIFY COLUMN contrasena VARCHAR(100) NOT NULL");
            }
            agregarColumnaSiFalta("intentos_fallidos", "INT NOT NULL DEFAULT 0");
            agregarColumnaSiFalta("bloqueado_hasta", "DATETIME NULL");
            agregarColumnaSiFalta("debe_cambiar_contrasena", "TINYINT(1) NOT NULL DEFAULT 0");
            jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS solicitud_recuperacion (
                        id_solicitud      INT AUTO_INCREMENT PRIMARY KEY,
                        id_usuario        INT NOT NULL,
                        correo            VARCHAR(120) NOT NULL,
                        archivo_nombre    VARCHAR(255) NOT NULL,
                        archivo_tipo      VARCHAR(80)  NOT NULL,
                        archivo_datos     MEDIUMBLOB   NOT NULL,
                        estado            VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE',
                        observacion       VARCHAR(255) NULL,
                        fecha_solicitud   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        fecha_resolucion  DATETIME     NULL,
                        resuelto_por      INT          NULL,
                        CONSTRAINT fk_sol_rec_usuario FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario)
                    )""");
        } catch (Exception e) {
            log.warn("No se pudo verificar/migrar el esquema de seguridad automaticamente: {}", e.getMessage());
        }
    }

    private void agregarColumnaSiFalta(String columna, String definicion) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'usuario' AND COLUMN_NAME = ?
                """, Integer.class, columna);
        if (count == null || count == 0) {
            jdbc.execute("ALTER TABLE usuario ADD COLUMN " + columna + " " + definicion);
        }
    }
}
