-- =====================================================================
--  DATOS REALES: RESIDENCIAL LA CRUCETA - BLOCK 73 (SURCO)
--  - 20 departamentos (5 pisos x 4 departamentos: 101..104 a 501..504)
--  - 20 propietarios (1 por departamento) + 1 Administrador de La Directiva
--  - Contrasenas almacenadas exclusivamente como hashes BCrypt unicos ($2a$10$...)
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

-- 20 departamentos distribuidos por pisos (5 pisos x 4 departamentos por piso)
INSERT IGNORE INTO unidad (id_unidad, bloque, numero_unidad, estado) VALUES
 (1,  'Piso 1', '101', 'ACTIVA'),
 (2,  'Piso 1', '102', 'ACTIVA'),
 (3,  'Piso 1', '103', 'ACTIVA'),
 (4,  'Piso 1', '104', 'ACTIVA'),
 (5,  'Piso 2', '201', 'ACTIVA'),
 (6,  'Piso 2', '202', 'ACTIVA'),
 (7,  'Piso 2', '203', 'ACTIVA'),
 (8,  'Piso 2', '204', 'ACTIVA'),
 (9,  'Piso 3', '301', 'ACTIVA'),
 (10, 'Piso 3', '302', 'ACTIVA'),
 (11, 'Piso 3', '303', 'ACTIVA'),
 (12, 'Piso 3', '304', 'ACTIVA'),
 (13, 'Piso 4', '401', 'ACTIVA'),
 (14, 'Piso 4', '402', 'ACTIVA'),
 (15, 'Piso 4', '403', 'ACTIVA'),
 (16, 'Piso 4', '404', 'ACTIVA'),
 (17, 'Piso 5', '501', 'ACTIVA'),
 (18, 'Piso 5', '502', 'ACTIVA'),
 (19, 'Piso 5', '503', 'ACTIVA'),
 (20, 'Piso 5', '504', 'ACTIVA');

-- Actualizar unidades existentes si tenian 'Block 73' a distribucion por pisos
UPDATE unidad SET bloque = CONCAT('Piso ', LEFT(numero_unidad, 1)) WHERE bloque LIKE '%Block%';

-- 1 Administrador (La Directiva) + 20 Propietarios (hashes BCrypt con sal aleatoria unica)
INSERT IGNORE INTO usuario (id_usuario, dni, nombre, apellido, fecha_nacimiento, telefono, correo, direccion, contrasena, id_rol, id_unidad, estado_cuenta) VALUES
 (1,  '40000001', 'Rosa',           'Quispe Huaman',    '1980-03-12', '987000001', 'admin@cruceta.com',           'Residencial La Cruceta - Directiva', '$2a$10$qWI6ydf4gH.VS.X5kryilO72RP2x6Qd1wnjORl.dsKnSUw/StIyYW', 1, NULL, 'ACTIVO'),
 (2,  '41000101', 'Luis',           'Torres Vega',      '1975-06-02', '987100101', 'luis.torres@email.com',       'Piso 1 - Dpto. 101',                 '$2a$10$3aEACwu27pJKNiuPEZ9v5.rCfs8xx1u/6vT56WX7q5fvL9uHymBAi', 2, 1,  'ACTIVO'),
 (3,  '41000102', 'Maria',          'Flores Rojas',     '1982-11-21', '987100102', 'maria.flores@email.com',      'Piso 1 - Dpto. 102',                 '$2a$10$NEFTMG7WxTpuMvGTwwsBeu5HnMIKc1hiiFbE7r0Qs0ub6ZAqdXUpu', 2, 2,  'ACTIVO'),
 (4,  '41000103', 'Jorge',          'Ramirez Paz',      '1979-01-15', '987100103', 'jorge.ramirez@email.com',     'Piso 1 - Dpto. 103',                 '$2a$10$Puj4M6liAJWPpU5JlhHHr.2DmomS97x.jJgvME0E.4I7ivBnoft.u', 2, 3,  'ACTIVO'),
 (5,  '41000104', 'Ana',            'Castillo Soto',    '1990-08-30', '987100104', 'ana.castillo@email.com',      'Piso 1 - Dpto. 104',                 '$2a$10$qwjbvHt065ANfLwIeBtj8egIpsX3A/mfn6DGi2w.K/F5Jl4MsaYua', 2, 4,  'ACTIVO'),
 (6,  '41000201', 'Pedro',          'Salazar Leon',     '1968-04-09', '987100201', 'pedro.salazar@email.com',     'Piso 2 - Dpto. 201',                 '$2a$10$VEypyjEBXzbeWMhk7AcSSu9Y1UcVLhF08bYjpuAUdb8LP59cx3V2e', 2, 5,  'ACTIVO'),
 (7,  '41000202', 'Lucia',          'Herrera Diaz',     '1985-12-01', '987100202', 'lucia.herrera@email.com',     'Piso 2 - Dpto. 202',                 '$2a$10$L7St5ZpygJERPOtI1JEeAOwVwhxG.ozKdYizRq8855ntNGMjDpG..', 2, 6,  'ACTIVO'),
 (8,  '41000203', 'Miguel',         'Chavez Rios',      '1977-02-18', '987100203', 'miguel.chavez@email.com',     'Piso 2 - Dpto. 203',                 '$2a$10$JFH2ak.BzGWPTyLtk0NK3OPj0nKp.lI0n.KqiEuQicPDumd6dn4Hq', 2, 7,  'ACTIVO'),
 (9,  '41000204', 'Carmen',         'Vargas Medina',    '1988-07-07', '987100204', 'carmen.vargas@email.com',     'Piso 2 - Dpto. 204',                 '$2a$10$geoOP2nPVJu3azIKpX.vNOtkzH9kbnmnDzT1sZA4mk93BG5WMoGd6', 2, 8,  'ACTIVO'),
 (10, '41000301', 'Ricardo',        'Mejia Campos',     '1972-09-25', '987100301', 'ricardo.mejia@email.com',     'Piso 3 - Dpto. 301',                 '$2a$10$G04Za3v9s60IaDD1D4p0AuWw8oEflAaYW6UcePFtysel.JEo0ymAa', 2, 9,  'ACTIVO'),
 (11, '41000302', 'Carlos Alberto', 'Mendoza Ruiz',     '1984-02-27', '991234567', 'carlos.mendoza@email.com',    'Piso 3 - Dpto. 302',                 '$2a$10$0ILUX9QP914hhDQ0jjddWeLt91zsuY6P6nH3zOTmjPpOSXitU/NJa', 2, 10, 'ACTIVO'),
 (12, '41000303', 'Patricia',       'Nunez Ortiz',      '1993-05-14', '987100303', 'patricia.nunez@email.com',    'Piso 3 - Dpto. 303',                 '$2a$10$SSSsJ0EwHBEHqjL.YyU1yuHjN6RQmbOCpbkvyWUlVeOyH3xVQAmzq', 2, 11, 'ACTIVO'),
 (13, '41000304', 'Fernando',       'Rojas Aguilar',    '1981-10-03', '987100304', 'fernando.rojas@email.com',    'Piso 3 - Dpto. 304',                 '$2a$10$EVvsasY3B8H2Ng80yybbJedF44J9JFOfCA7i9BEuhLsglJ/noya/m', 2, 12, 'ACTIVO'),
 (14, '41000401', 'Elena',          'Morales Pineda',   '1976-04-19', '987100401', 'elena.morales@email.com',     'Piso 4 - Dpto. 401',                 '$2a$10$xyWiMZAD23YkIoN85VzHYO/xp16fZgE31MI7WIgNW07VMjXbPIaci', 2, 13, 'ACTIVO'),
 (15, '41000402', 'Roberto',        'Silva Paredes',    '1983-09-11', '987100402', 'roberto.silva@email.com',     'Piso 4 - Dpto. 402',                 '$2a$10$61yqF.bteBtpc.H8fRYfsu3XzSoTvQxGJtTYPTu08QVYWT1JvfsFG', 2, 14, 'ACTIVO'),
 (16, '41000403', 'Gabriela',       'Castro Luna',      '1991-01-28', '987100403', 'gabriela.castro@email.com',   'Piso 4 - Dpto. 403',                 '$2a$10$wh.cULyRyTRfb/UaX835GuklgFJWRyAU1MQweKKY.FX0ofMUQyUea', 2, 15, 'ACTIVO'),
 (17, '41000404', 'Hugo',           'Benites Espinoza', '1970-12-08', '987100404', 'hugo.benites@email.com',      'Piso 4 - Dpto. 404',                 '$2a$10$qCzFs8cgiQL7xVq8IdvQoefeBbe/gve6G0/wzGuPfdzlrOM5ByOUu', 2, 16, 'ACTIVO'),
 (18, '41000501', 'Maria',          'Lozano',           '1978-05-25', '987100501', 'maria.lozano@email.com',      'Piso 5 - Dpto. 501',                 '$2a$10$4tnhs0rmybZwoWNoTvTgQu4uArfnSzjOUzMRaHqrWm9PEPW.VWbYC', 2, 17, 'ACTIVO'),
 (19, '41000502', 'Raul',           'Paredes Gomez',    '1986-08-14', '987100502', 'raul.paredes@email.com',      'Piso 5 - Dpto. 502',                 '$2a$10$ttQK7QcMf/ubjwxSVcVDKeVe23BECQ5i9PcLj6NIb0ZDXvK/gDyG.', 2, 18, 'ACTIVO'),
 (20, '41000503', 'Silvia',         'Aguilar Ramos',    '1989-11-04', '987100503', 'silvia.aguilar@email.com',    'Piso 5 - Dpto. 503',                 '$2a$10$PJT0mUtEEhrn9a4tLuqLIOpBih1rv40i1RCjZcqk94ivZ9lmUiBNK', 2, 19, 'ACTIVO'),
 (21, '41000504', 'Oscar',          'Delgado Vera',     '1974-03-22', '987100504', 'oscar.delgado@email.com',     'Piso 5 - Dpto. 504',                 '$2a$10$6pu9DfhTHDV05qNtfFKnVuOYqiu.qCij0w.8Pi0HE/f9G4xp7mNwa', 2, 20, 'ACTIVO');

-- Migracion idempotente: si una BD existente aun conserva los hashes SHA-256 heredados, se reemplazan por BCrypt
UPDATE usuario SET contrasena = '$2a$10$qWI6ydf4gH.VS.X5kryilO72RP2x6Qd1wnjORl.dsKnSUw/StIyYW' WHERE id_usuario = 1  AND contrasena IN ('240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92');
UPDATE usuario SET contrasena = '$2a$10$3aEACwu27pJKNiuPEZ9v5.rCfs8xx1u/6vT56WX7q5fvL9uHymBAi' WHERE id_usuario = 2  AND contrasena = '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92';
UPDATE usuario SET contrasena = '$2a$10$NEFTMG7WxTpuMvGTwwsBeu5HnMIKc1hiiFbE7r0Qs0ub6ZAqdXUpu' WHERE id_usuario = 3  AND contrasena = '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92';
UPDATE usuario SET contrasena = '$2a$10$Puj4M6liAJWPpU5JlhHHr.2DmomS97x.jJgvME0E.4I7ivBnoft.u' WHERE id_usuario = 4  AND contrasena = '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92';
UPDATE usuario SET contrasena = '$2a$10$qwjbvHt065ANfLwIeBtj8egIpsX3A/mfn6DGi2w.K/F5Jl4MsaYua' WHERE id_usuario = 5  AND contrasena = '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92';
UPDATE usuario SET contrasena = '$2a$10$VEypyjEBXzbeWMhk7AcSSu9Y1UcVLhF08bYjpuAUdb8LP59cx3V2e' WHERE id_usuario = 6  AND contrasena = '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92';
UPDATE usuario SET contrasena = '$2a$10$L7St5ZpygJERPOtI1JEeAOwVwhxG.ozKdYizRq8855ntNGMjDpG..' WHERE id_usuario = 7  AND contrasena = '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92';
UPDATE usuario SET contrasena = '$2a$10$JFH2ak.BzGWPTyLtk0NK3OPj0nKp.lI0n.KqiEuQicPDumd6dn4Hq' WHERE id_usuario = 8  AND contrasena = '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92';
UPDATE usuario SET contrasena = '$2a$10$geoOP2nPVJu3azIKpX.vNOtkzH9kbnmnDzT1sZA4mk93BG5WMoGd6' WHERE id_usuario = 9  AND contrasena = '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92';
UPDATE usuario SET contrasena = '$2a$10$G04Za3v9s60IaDD1D4p0AuWw8oEflAaYW6UcePFtysel.JEo0ymAa' WHERE id_usuario = 10 AND contrasena = '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92';
UPDATE usuario SET contrasena = '$2a$10$0ILUX9QP914hhDQ0jjddWeLt91zsuY6P6nH3zOTmjPpOSXitU/NJa' WHERE id_usuario = 11 AND contrasena = '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92';
UPDATE usuario SET contrasena = '$2a$10$SSSsJ0EwHBEHqjL.YyU1yuHjN6RQmbOCpbkvyWUlVeOyH3xVQAmzq' WHERE id_usuario = 12 AND contrasena = '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92';
UPDATE usuario SET contrasena = '$2a$10$EVvsasY3B8H2Ng80yybbJedF44J9JFOfCA7i9BEuhLsglJ/noya/m' WHERE id_usuario = 13 AND contrasena = '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92';
UPDATE usuario SET contrasena = '$2a$10$xyWiMZAD23YkIoN85VzHYO/xp16fZgE31MI7WIgNW07VMjXbPIaci' WHERE id_usuario = 14 AND contrasena = '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92';
UPDATE usuario SET contrasena = '$2a$10$61yqF.bteBtpc.H8fRYfsu3XzSoTvQxGJtTYPTu08QVYWT1JvfsFG' WHERE id_usuario = 15 AND contrasena = '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92';
UPDATE usuario SET contrasena = '$2a$10$wh.cULyRyTRfb/UaX835GuklgFJWRyAU1MQweKKY.FX0ofMUQyUea' WHERE id_usuario = 16 AND contrasena = '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92';
UPDATE usuario SET contrasena = '$2a$10$qCzFs8cgiQL7xVq8IdvQoefeBbe/gve6G0/wzGuPfdzlrOM5ByOUu' WHERE id_usuario = 17 AND contrasena = '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92';
UPDATE usuario SET contrasena = '$2a$10$4tnhs0rmybZwoWNoTvTgQu4uArfnSzjOUzMRaHqrWm9PEPW.VWbYC' WHERE id_usuario = 18 AND contrasena = '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92';
UPDATE usuario SET contrasena = '$2a$10$ttQK7QcMf/ubjwxSVcVDKeVe23BECQ5i9PcLj6NIb0ZDXvK/gDyG.' WHERE id_usuario = 19 AND contrasena = '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92';
UPDATE usuario SET contrasena = '$2a$10$PJT0mUtEEhrn9a4tLuqLIOpBih1rv40i1RCjZcqk94ivZ9lmUiBNK' WHERE id_usuario = 20 AND contrasena = '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92';
UPDATE usuario SET contrasena = '$2a$10$6pu9DfhTHDV05qNtfFKnVuOYqiu.qCij0w.8Pi0HE/f9G4xp7mNwa' WHERE id_usuario = 21 AND contrasena = '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92';

UPDATE usuario SET direccion = REPLACE(direccion, 'Residencial La Cruceta Block 73 - Directiva', 'Residencial La Cruceta - Directiva') WHERE direccion LIKE '%Block 73%';
UPDATE usuario SET direccion = REPLACE(direccion, 'Block 73 - ', '') WHERE direccion LIKE '%Block 73%';

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
SELECT id_unidad, '2026-10', '2026-10-01',
       CASE
           WHEN id_unidad IN (16, 19) THEN '2026-10-15'
           WHEN id_unidad = 3 THEN '2026-11-20'
           WHEN id_unidad = 4 THEN '2026-11-17'
           WHEN id_unidad = 6 THEN '2026-11-13'
           ELSE '2026-10-31'
       END,
       CASE WHEN id_unidad IN (1, 2, 17) THEN 'PAGADA' ELSE 'PENDIENTE' END
FROM unidad;

-- Ajuste idempotente de vencimientos de prueba (morosos > 30 dias y deudores con pocos dias de retraso)
UPDATE cuota SET fecha_vencimiento = '2026-10-15', estado_cuota = 'VENCIDA' WHERE periodo = '2026-10' AND id_unidad IN (16, 19) AND estado_cuota <> 'PAGADA';
UPDATE cuota SET fecha_vencimiento = '2026-11-20', estado_cuota = 'VENCIDA' WHERE periodo = '2026-10' AND id_unidad = 3 AND estado_cuota <> 'PAGADA';
UPDATE cuota SET fecha_vencimiento = '2026-11-17', estado_cuota = 'VENCIDA' WHERE periodo = '2026-10' AND id_unidad = 4 AND estado_cuota <> 'PAGADA';
UPDATE cuota SET fecha_vencimiento = '2026-11-13', estado_cuota = 'VENCIDA' WHERE periodo = '2026-10' AND id_unidad = 6 AND estado_cuota <> 'PAGADA';

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

-- Comprobantes (apuntan a la boleta individual de pago de cada propietario)
INSERT IGNORE INTO comprobante (id_comprobante, id_pago, archivo, fecha_carga, estado_validacion, observacion)
SELECT p.id_pago, p.id_pago, CONCAT('seed/voucher_', p.id_pago, '.svg'), TIMESTAMP(p.fecha_pago, '10:30:00'),
       CASE p.estado_pago WHEN 'VALIDADO' THEN 'VALIDADO' WHEN 'RECHAZADO' THEN 'RECHAZADO' ELSE 'PENDIENTE' END,
       NULL
FROM pago p;

UPDATE comprobante SET archivo = CONCAT('seed/voucher_', id_pago, '.svg') WHERE archivo = 'seed/voucher_demo.svg';

-- Recibos emitidos con numeracion correlativa de 6 digitos (ej. 004148 para Maria Lozano - Dpto. 501)
INSERT IGNORE INTO recibo (id_recibo, numero_recibo, fecha_emision, id_pago, observacion)
SELECT p.id_pago,
       CASE WHEN p.id_pago = 317 THEN '004148' ELSE LPAD(4000 + p.id_pago, 6, '0') END,
       p.fecha_pago,
       p.id_pago,
       'Pago de servicios'
FROM pago p WHERE p.estado_pago = 'VALIDADO';
