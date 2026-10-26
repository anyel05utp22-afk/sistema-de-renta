-- =====================================================================
--  DATOS REALES: RESIDENCIAL LA CRUCETA - BLOCK 73 (SURCO)
--  - 20 departamentos (5 pisos x 4 departamentos: 101..104 a 501..504)
--  - 20 propietarios (1 por departamento) + 1 Administrador de La Directiva
--  - Conceptos de pago reales segun hoja de compromisos (sin ascensor):
--      * Gastos generales divididos entre los 20 departamentos
--      * Servicio de vigilancia y seguridad: fijo S/. 10.00 solo para 15 dptos
--        (NO pagan vigilancia: 101, 301, 401, 402 y 502)
--  - Metodos de pago: solo YAPE o EFECTIVO
--  - Numeracion de recibos: correlativo tipo 004148
-- =====================================================================

INSERT IGNORE INTO rol (id_rol, nombre_rol) VALUES
 (1, 'ADMINISTRADOR'),
 (2, 'PROPIETARIO'),
 (3, 'RESIDENTE');

-- 20 departamentos del Block 73 (5 pisos x 4 departamentos por piso)
INSERT IGNORE INTO unidad (id_unidad, bloque, numero_unidad, estado) VALUES
 (1,  'Block 73', '101', 'ACTIVA'),
 (2,  'Block 73', '102', 'ACTIVA'),
 (3,  'Block 73', '103', 'ACTIVA'),
 (4,  'Block 73', '104', 'ACTIVA'),
 (5,  'Block 73', '201', 'ACTIVA'),
 (6,  'Block 73', '202', 'ACTIVA'),
 (7,  'Block 73', '203', 'ACTIVA'),
 (8,  'Block 73', '204', 'ACTIVA'),
 (9,  'Block 73', '301', 'ACTIVA'),
 (10, 'Block 73', '302', 'ACTIVA'),
 (11, 'Block 73', '303', 'ACTIVA'),
 (12, 'Block 73', '304', 'ACTIVA'),
 (13, 'Block 73', '401', 'ACTIVA'),
 (14, 'Block 73', '402', 'ACTIVA'),
 (15, 'Block 73', '403', 'ACTIVA'),
 (16, 'Block 73', '404', 'ACTIVA'),
 (17, 'Block 73', '501', 'ACTIVA'),
 (18, 'Block 73', '502', 'ACTIVA'),
 (19, 'Block 73', '503', 'ACTIVA'),
 (20, 'Block 73', '504', 'ACTIVA');

-- 1 Administrador (La Directiva) + 20 Propietarios (uno por cada departamento)
INSERT IGNORE INTO usuario (id_usuario, dni, nombre, apellido, fecha_nacimiento, telefono, correo, direccion, contrasena, id_rol, id_unidad, estado_cuenta) VALUES
 (1,  '40000001', 'Rosa',           'Quispe Huaman',    '1980-03-12', '987000001', 'admin@cruceta.com',           'Residencial La Cruceta Block 73 - Directiva', SHA2('admin123',256), 1, NULL, 'ACTIVO'),
 (2,  '41000101', 'Luis',           'Torres Vega',      '1975-06-02', '987100101', 'luis.torres@email.com',       'Block 73 - Dpto. 101', SHA2('123456',256), 2, 1,  'ACTIVO'),
 (3,  '41000102', 'Maria',          'Flores Rojas',     '1982-11-21', '987100102', 'maria.flores@email.com',      'Block 73 - Dpto. 102', SHA2('123456',256), 2, 2,  'ACTIVO'),
 (4,  '41000103', 'Jorge',          'Ramirez Paz',      '1979-01-15', '987100103', 'jorge.ramirez@email.com',     'Block 73 - Dpto. 103', SHA2('123456',256), 2, 3,  'ACTIVO'),
 (5,  '41000104', 'Ana',            'Castillo Soto',    '1990-08-30', '987100104', 'ana.castillo@email.com',      'Block 73 - Dpto. 104', SHA2('123456',256), 2, 4,  'ACTIVO'),
 (6,  '41000201', 'Pedro',          'Salazar Leon',     '1968-04-09', '987100201', 'pedro.salazar@email.com',     'Block 73 - Dpto. 201', SHA2('123456',256), 2, 5,  'ACTIVO'),
 (7,  '41000202', 'Lucia',          'Herrera Diaz',     '1985-12-01', '987100202', 'lucia.herrera@email.com',     'Block 73 - Dpto. 202', SHA2('123456',256), 2, 6,  'ACTIVO'),
 (8,  '41000203', 'Miguel',         'Chavez Rios',      '1977-02-18', '987100203', 'miguel.chavez@email.com',     'Block 73 - Dpto. 203', SHA2('123456',256), 2, 7,  'ACTIVO'),
 (9,  '41000204', 'Carmen',         'Vargas Medina',    '1988-07-07', '987100204', 'carmen.vargas@email.com',     'Block 73 - Dpto. 204', SHA2('123456',256), 2, 8,  'ACTIVO'),
 (10, '41000301', 'Ricardo',        'Mejia Campos',     '1972-09-25', '987100301', 'ricardo.mejia@email.com',     'Block 73 - Dpto. 301', SHA2('123456',256), 2, 9,  'ACTIVO'),
 (11, '41000302', 'Carlos Alberto', 'Mendoza Ruiz',     '1984-02-27', '991234567', 'carlos.mendoza@email.com',    'Block 73 - Dpto. 302', SHA2('123456',256), 2, 10, 'ACTIVO'),
 (12, '41000303', 'Patricia',       'Nunez Ortiz',      '1993-05-14', '987100303', 'patricia.nunez@email.com',    'Block 73 - Dpto. 303', SHA2('123456',256), 2, 11, 'ACTIVO'),
 (13, '41000304', 'Fernando',       'Rojas Aguilar',    '1981-10-03', '987100304', 'fernando.rojas@email.com',    'Block 73 - Dpto. 304', SHA2('123456',256), 2, 12, 'ACTIVO'),
 (14, '41000401', 'Elena',          'Morales Pineda',   '1976-04-19', '987100401', 'elena.morales@email.com',     'Block 73 - Dpto. 401', SHA2('123456',256), 2, 13, 'ACTIVO'),
 (15, '41000402', 'Roberto',        'Silva Paredes',    '1983-09-11', '987100402', 'roberto.silva@email.com',     'Block 73 - Dpto. 402', SHA2('123456',256), 2, 14, 'ACTIVO'),
 (16, '41000403', 'Gabriela',       'Castro Luna',      '1991-01-28', '987100403', 'gabriela.castro@email.com',   'Block 73 - Dpto. 403', SHA2('123456',256), 2, 15, 'ACTIVO'),
 (17, '41000404', 'Hugo',           'Benites Espinoza', '1970-12-08', '987100404', 'hugo.benites@email.com',      'Block 73 - Dpto. 404', SHA2('123456',256), 2, 16, 'ACTIVO'),
 (18, '41000501', 'Maria',          'Lozano',           '1978-05-25', '987100501', 'maria.lozano@email.com',      'Block 73 - Dpto. 501', SHA2('123456',256), 2, 17, 'ACTIVO'),
 (19, '41000502', 'Raul',           'Paredes Gomez',    '1986-08-14', '987100502', 'raul.paredes@email.com',      'Block 73 - Dpto. 502', SHA2('123456',256), 2, 18, 'ACTIVO'),
 (20, '41000503', 'Silvia',         'Aguilar Ramos',    '1989-11-04', '987100503', 'silvia.aguilar@email.com',    'Block 73 - Dpto. 503', SHA2('123456',256), 2, 19, 'ACTIVO'),
 (21, '41000504', 'Oscar',          'Delgado Vera',     '1974-03-22', '987100504', 'oscar.delgado@email.com',     'Block 73 - Dpto. 504', SHA2('123456',256), 2, 20, 'ACTIVO');

-- Conceptos reales segun el Resumen de Compromisos de Pago de Octubre 2026 (sin ascensor)
INSERT IGNORE INTO concepto_cobro (id_concepto, nombre_concepto, tipo_concepto, monto_base) VALUES
 (1, 'Pago recibo de agua',              'VARIABLE', 1660.10),
 (2, 'Pago recibo de luz',               'VARIABLE',  117.90),
 (3, 'Pago de limpieza del edificio',    'FIJO',      300.00),
 (4, 'Impresion y fotocopia',            'VARIABLE',    3.00),
 (5, 'Pasajes',                          'VARIABLE',    9.00),
 (6, 'Compra de una cinta maskingtape',  'VARIABLE',    4.00),
 (7, 'Servicio de vigilancia y seguridad','FIJO',     150.00);

-- ---------------------------------------------------------------------
-- Cuotas de Agosto, Septiembre y Octubre 2026 para los 20 departamentos
-- ---------------------------------------------------------------------
INSERT IGNORE INTO cuota (id_unidad, periodo, fecha_emision, fecha_vencimiento, estado_cuota)
SELECT id_unidad, '2026-08', '2026-08-01', '2026-08-31',
       CASE WHEN id_unidad = 7 THEN 'VENCIDA' ELSE 'PAGADA' END
FROM unidad;

INSERT IGNORE INTO cuota (id_unidad, periodo, fecha_emision, fecha_vencimiento, estado_cuota)
SELECT id_unidad, '2026-09', '2026-09-01', '2026-09-30',
       CASE WHEN id_unidad IN (7, 11, 15) THEN 'VENCIDA'
            WHEN id_unidad = 10 THEN 'PENDIENTE'
            ELSE 'PAGADA' END
FROM unidad;

INSERT IGNORE INTO cuota (id_unidad, periodo, fecha_emision, fecha_vencimiento, estado_cuota)
SELECT id_unidad, '2026-10', '2026-10-01', '2026-10-31',
       CASE WHEN id_unidad IN (1, 2, 17) THEN 'PAGADA' ELSE 'PENDIENTE' END
FROM unidad;

-- Desglose por departamento:
--   - Conceptos 1..6 (S/. 2,094.00) se dividen entre 20 dptos = S/. 104.70 redondeado a S/. 105.00
--   - Concepto 7 (Vigilancia S/. 150.00): S/. 10.00 fijo para 15 dptos; S/. 0.00 para 101, 301, 401, 402 y 502
--   - Total aporte final: S/. 105.00 (exonerados de vigilancia) o S/. 115.00 (con vigilancia)
INSERT IGNORE INTO detalle_cuota (id_cuota, id_concepto, monto_concepto)
SELECT c.id_cuota, d.id_concepto,
       CASE
           WHEN d.id_concepto = 7 AND u.numero_unidad IN ('101','301','401','402','502') THEN 0.00
           WHEN d.id_concepto = 7 THEN 10.00
           ELSE d.monto_unitario
       END
FROM cuota c
JOIN unidad u ON u.id_unidad = c.id_unidad
CROSS JOIN (
          SELECT 1 AS id_concepto, 83.00 AS monto_unitario
    UNION ALL SELECT 2,  5.90
    UNION ALL SELECT 3, 15.00
    UNION ALL SELECT 4,  0.15
    UNION ALL SELECT 5,  0.45
    UNION ALL SELECT 6,  0.50
    UNION ALL SELECT 7, 10.00
) d;

-- ---------------------------------------------------------------------
-- Pagos (solo YAPE o EFECTIVO)
-- ---------------------------------------------------------------------
-- Agosto: pagaron todos menos Dpto. 203 (id_unidad = 7)
INSERT IGNORE INTO pago (id_pago, id_cuota, id_usuario, fecha_pago, monto_pagado, metodo_pago, nro_operacion, estado_pago)
SELECT 100 + c.id_unidad, c.id_cuota, u.id_usuario, '2026-08-25',
       (SELECT SUM(d.monto_concepto) FROM detalle_cuota d WHERE d.id_cuota = c.id_cuota),
       CASE MOD(c.id_unidad, 2) WHEN 0 THEN 'YAPE' ELSE 'EFECTIVO' END,
       CASE MOD(c.id_unidad, 2) WHEN 0 THEN CONCAT('YAPE-8', LPAD(c.id_unidad, 4, '0')) ELSE CONCAT('0040', LPAD(c.id_unidad, 2, '0')) END,
       'VALIDADO'
FROM cuota c JOIN usuario u ON u.id_unidad = c.id_unidad AND u.id_rol = 2
WHERE c.periodo = '2026-08' AND c.id_unidad <> 7;

-- Septiembre: validados la mayoria; Dpto. 302 (id_unidad = 10) en revision; 7, 11, 15 vencidos
INSERT IGNORE INTO pago (id_pago, id_cuota, id_usuario, fecha_pago, monto_pagado, metodo_pago, nro_operacion, estado_pago)
SELECT 200 + c.id_unidad, c.id_cuota, u.id_usuario, '2026-09-25',
       (SELECT SUM(d.monto_concepto) FROM detalle_cuota d WHERE d.id_cuota = c.id_cuota),
       CASE MOD(c.id_unidad, 2) WHEN 0 THEN 'YAPE' ELSE 'EFECTIVO' END,
       CASE MOD(c.id_unidad, 2) WHEN 0 THEN CONCAT('YAPE-9', LPAD(c.id_unidad, 4, '0')) ELSE CONCAT('0041', LPAD(c.id_unidad, 2, '0')) END,
       CASE WHEN c.id_unidad = 10 THEN 'EN_REVISION' ELSE 'VALIDADO' END
FROM cuota c JOIN usuario u ON u.id_unidad = c.id_unidad AND u.id_rol = 2
WHERE c.periodo = '2026-09' AND c.id_unidad NOT IN (7, 11, 15);

-- Octubre: Dptos. 101 (1), 102 (2) y 501 Maria Lozano (17) validados; Dpto. 201 (5) en revision
INSERT IGNORE INTO pago (id_pago, id_cuota, id_usuario, fecha_pago, monto_pagado, metodo_pago, nro_operacion, estado_pago)
SELECT 300 + c.id_unidad, c.id_cuota, u.id_usuario, '2026-10-25',
       (SELECT SUM(d.monto_concepto) FROM detalle_cuota d WHERE d.id_cuota = c.id_cuota),
       CASE WHEN c.id_unidad = 17 THEN 'EFECTIVO' WHEN MOD(c.id_unidad, 2) = 0 THEN 'YAPE' ELSE 'EFECTIVO' END,
       CASE WHEN c.id_unidad = 17 THEN '004148'
            WHEN MOD(c.id_unidad, 2) = 0 THEN CONCAT('YAPE-10', LPAD(c.id_unidad, 3, '0'))
            ELSE CONCAT('0041', LPAD(30 + c.id_unidad, 2, '0')) END,
       CASE WHEN c.id_unidad = 5 THEN 'EN_REVISION' ELSE 'VALIDADO' END
FROM cuota c JOIN usuario u ON u.id_unidad = c.id_unidad AND u.id_rol = 2
WHERE c.periodo = '2026-10' AND c.id_unidad IN (1, 2, 5, 17);

-- Comprobantes (apuntan a la boleta de pago de La Directiva)
INSERT IGNORE INTO comprobante (id_comprobante, id_pago, archivo, fecha_carga, estado_validacion, observacion)
SELECT p.id_pago, p.id_pago, 'seed/voucher_demo.svg', TIMESTAMP(p.fecha_pago, '10:30:00'),
       CASE p.estado_pago WHEN 'VALIDADO' THEN 'VALIDADO' WHEN 'RECHAZADO' THEN 'RECHAZADO' ELSE 'PENDIENTE' END,
       NULL
FROM pago p;

-- Recibos emitidos con numeracion correlativa de 6 digitos (ej. 004148 para Maria Lozano - Dpto. 501)
INSERT IGNORE INTO recibo (id_recibo, numero_recibo, fecha_emision, id_pago, observacion)
SELECT p.id_pago,
       CASE WHEN p.id_pago = 317 THEN '004148' ELSE LPAD(4000 + p.id_pago, 6, '0') END,
       p.fecha_pago,
       p.id_pago,
       'Pago de servicios'
FROM pago p WHERE p.estado_pago = 'VALIDADO';
