-- =====================================================================
--  EDIFICIO LA CRUCETA - Sistema de cobro de cuotas de mantenimiento
--  Esquema basado en el diagrama de clases / BD del proyecto.
--  (Se ejecuta automaticamente al iniciar la aplicacion)
-- =====================================================================

CREATE TABLE IF NOT EXISTS rol (
    id_rol      INT AUTO_INCREMENT PRIMARY KEY,
    nombre_rol  VARCHAR(40) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS unidad (
    id_unidad      INT AUTO_INCREMENT PRIMARY KEY,
    bloque         VARCHAR(20) NOT NULL,
    numero_unidad  VARCHAR(20) NOT NULL,
    estado         VARCHAR(20) NOT NULL DEFAULT 'ACTIVA',
    CONSTRAINT uq_unidad UNIQUE (bloque, numero_unidad)
);

-- Cada departamento (unidad) tiene su propio duenio (usuario con rol PROPIETARIO).
-- Se agrega la columna "contrasena" (hash SHA-256) para poder iniciar sesion (RF-003).
CREATE TABLE IF NOT EXISTS usuario (
    id_usuario        INT AUTO_INCREMENT PRIMARY KEY,
    dni               VARCHAR(15)  NOT NULL UNIQUE,
    nombre            VARCHAR(80)  NOT NULL,
    apellido          VARCHAR(80)  NOT NULL,
    fecha_nacimiento  DATE NULL,
    telefono          VARCHAR(20)  NULL,
    correo            VARCHAR(120) NOT NULL UNIQUE,
    direccion         VARCHAR(150) NULL,
    contrasena        VARCHAR(64)  NOT NULL,
    id_rol            INT NOT NULL,
    id_unidad         INT NULL,
    estado_cuenta     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVO',
    CONSTRAINT fk_usuario_rol    FOREIGN KEY (id_rol)    REFERENCES rol(id_rol),
    CONSTRAINT fk_usuario_unidad FOREIGN KEY (id_unidad) REFERENCES unidad(id_unidad)
);

-- Conceptos de gasto del edificio (vigilancia, limpieza, agua, luz...)
-- monto_base = gasto mensual referencial; cada mes el administrador
-- ingresa lo realmente gastado y eso se reparte entre los departamentos.
CREATE TABLE IF NOT EXISTS concepto_cobro (
    id_concepto      INT AUTO_INCREMENT PRIMARY KEY,
    nombre_concepto  VARCHAR(100) NOT NULL UNIQUE,
    tipo_concepto    VARCHAR(30)  NOT NULL,
    monto_base       DECIMAL(10,2) NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS cuota (
    id_cuota           INT AUTO_INCREMENT PRIMARY KEY,
    id_unidad          INT NOT NULL,
    periodo            VARCHAR(7) NOT NULL,          -- formato AAAA-MM
    fecha_emision      DATE NOT NULL,
    fecha_vencimiento  DATE NOT NULL,
    estado_cuota       VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    CONSTRAINT uq_cuota_periodo UNIQUE (id_unidad, periodo),
    CONSTRAINT fk_cuota_unidad FOREIGN KEY (id_unidad) REFERENCES unidad(id_unidad)
);

CREATE TABLE IF NOT EXISTS detalle_cuota (
    id_cuota        INT NOT NULL,
    id_concepto     INT NOT NULL,
    monto_concepto  DECIMAL(10,2) NOT NULL,
    PRIMARY KEY (id_cuota, id_concepto),
    CONSTRAINT fk_detalle_cuota    FOREIGN KEY (id_cuota)    REFERENCES cuota(id_cuota) ON DELETE CASCADE,
    CONSTRAINT fk_detalle_concepto FOREIGN KEY (id_concepto) REFERENCES concepto_cobro(id_concepto)
);

CREATE TABLE IF NOT EXISTS pago (
    id_pago        INT AUTO_INCREMENT PRIMARY KEY,
    id_cuota       INT NOT NULL,
    id_usuario     INT NOT NULL,
    fecha_pago     DATE NOT NULL,
    monto_pagado   DECIMAL(10,2) NOT NULL,
    metodo_pago    VARCHAR(30) NULL,                  -- opcional (Escenario 3)
    nro_operacion  VARCHAR(50) NULL,
    estado_pago    VARCHAR(20) NOT NULL DEFAULT 'EN_REVISION',
    CONSTRAINT fk_pago_cuota   FOREIGN KEY (id_cuota)   REFERENCES cuota(id_cuota),
    CONSTRAINT fk_pago_usuario FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario)
);

-- Se agrega "observacion" para guardar el motivo de rechazo (RF-020).
CREATE TABLE IF NOT EXISTS comprobante (
    id_comprobante     INT AUTO_INCREMENT PRIMARY KEY,
    id_pago            INT NOT NULL,
    archivo            VARCHAR(255) NOT NULL,
    fecha_carga        DATETIME NOT NULL,
    estado_validacion  VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    observacion        VARCHAR(200) NULL,
    CONSTRAINT fk_comprobante_pago FOREIGN KEY (id_pago) REFERENCES pago(id_pago)
);

CREATE TABLE IF NOT EXISTS recibo (
    id_recibo      INT AUTO_INCREMENT PRIMARY KEY,
    numero_recibo  VARCHAR(30) NOT NULL UNIQUE,
    fecha_emision  DATE NOT NULL,
    id_pago        INT NOT NULL UNIQUE,
    observacion    VARCHAR(200) NULL,
    CONSTRAINT fk_recibo_pago FOREIGN KEY (id_pago) REFERENCES pago(id_pago)
);
