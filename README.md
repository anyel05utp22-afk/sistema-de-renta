# Sistema Web "Edificio La Cruceta" — Cobro de Cuotas de Mantenimiento

Aplicación web completa desarrollada con **Java 17 (Spring Boot)**, **React 18**, **HTML/CSS** y **MySQL**, compatible con **Visual Studio Code**.

## Modelo de negocio
- El edificio **La Cruceta** cuenta con departamentos (**Unidades**) en distintos pisos, y cada departamento tiene su propio dueño (**Propietario**).
- Cada mes la administración ingresa cuánto gastó el edificio en cada concepto (**Agua, Luz, Limpieza, Impresión y fotocopia, Pasajes, Materiales, Vigilancia y seguridad**).
- El sistema suma los gastos del mes y los divide entre los departamentos activos para calcular y emitir la **cuota mensual** (`CUOTA` + `DETALLE_CUOTA`).
- Los propietarios consultan su estado de cuenta, descargan su aviso de cobro y **registran sus pagos** adjuntando su comprobante (`PAGO` + `COMPROBANTE`), el cual queda guardado en MySQL.
- El administrador valida o rechaza los comprobantes; al validar se emite automáticamente el **Recibo / Constancia de Pago** (`RECIBO`) y la cuota pasa a `PAGADA`.

---

## Estructura de la Base de Datos (`edificio_cruceta`)
1. `rol` (`id_rol`, `nombre_rol`)
2. `unidad` (`id_unidad`, `bloque`, `numero_unidad`, `estado`)
3. `usuario` (`id_usuario`, `dni`, `nombre`, `apellido`, `fecha_nacimiento`, `telefono`, `correo`, `direccion`, `contrasena`, `id_rol`, `id_unidad`, `estado_cuenta`, `intentos_fallidos`, `bloqueado_hasta`, `debe_cambiar_contrasena`)
4. `concepto_cobro` (`id_concepto`, `nombre_concepto`, `tipo_concepto`, `monto_base`)
5. `cuota` (`id_cuota`, `id_unidad`, `periodo`, `fecha_emision`, `fecha_vencimiento`, `estado_cuota`)
6. `detalle_cuota` (`id_cuota`, `id_concepto`, `monto_concepto`)
7. `pago` (`id_pago`, `id_cuota`, `id_usuario`, `fecha_pago`, `monto_pagado`, `metodo_pago`, `nro_operacion`, `estado_pago`)
8. `comprobante` (`id_comprobante`, `id_pago`, `archivo`, `fecha_carga`, `estado_validacion`, `observacion`)
9. `recibo` (`id_recibo`, `numero_recibo`, `fecha_emision`, `id_pago`, `observacion`)

> **Creación automática:** Al iniciar el proyecto, Spring Boot se conecta a MySQL usando las variables de entorno configuradas, crea la base de datos `edificio_cruceta` si no existe, crea las 9 tablas ([`schema.sql`](src/main/resources/schema.sql)) y carga los registros iniciales ([`data.sql`](src/main/resources/data.sql)) con contraseñas cifradas mediante **BCrypt**.

---

## Seguridad implementada
- **Cifrado de contraseñas:** Todas las contraseñas se almacenan con **BCrypt** (sal aleatoria única por usuario y factor de costo 10), con migración transparente de cuentas heredadas.
- **Protección contra fuerza bruta:** Bloqueo temporal de 15 minutos tras 5 intentos fallidos consecutivos de inicio de sesión, manteniendo siempre un mensaje de error genérico.
- **Gestión de sesiones:** Tokens criptográficos aleatorios con expiración automática tras 30 minutos de inactividad, transmitidos exclusivamente mediante la cabecera `Authorization: Bearer` (incluyendo la descarga de comprobantes).
- **Política de contraseñas:** Longitud mínima de 8 caracteres. Al registrar usuarios sin contraseña manual, se genera una contraseña temporal aleatoria que debe cambiarse obligatoriamente en el primer inicio de sesión.
- **Validación de archivos y acceso:** Verificación de extensión y firma binaria real (magic bytes) para PDF, JPG y PNG; restricción CORS por orígenes configurables; y aislamiento por unidad para propietarios.

---

## Cómo ejecutar en Visual Studio Code

1. Abre la carpeta del proyecto en **VS Code**.
2. Asegúrate de que el servicio **MySQL** esté activo y configura tus variables de entorno (o copia [`.env.example`](.env.example) a `.env`, el cual está ignorado por Git):
   - `MYSQLHOST` (por defecto `localhost`)
   - `MYSQLPORT` (por defecto `3306`)
   - `MYSQLDATABASE` (por defecto `edificio_cruceta`)
   - `MYSQLUSER` (por defecto `root`)
   - `MYSQLPASSWORD` (contraseña de tu instancia MySQL)
3. Tienes 3 formas de iniciarlo:
   - **Con F5 / Run Java:** presiona `F5` (usa [`.vscode/launch.json`](.vscode/launch.json)).
   - **Desde la terminal de VS Code:**
     ```powershell
     .\mvnw.cmd spring-boot:run
     ```
   - **Doble clic** en [`iniciar.bat`](iniciar.bat).
4. Abre en el navegador: **http://localhost:8082**

> **Nota:** Por seguridad, las credenciales de acceso no se almacenan en texto plano ni se publican en la documentación del repositorio. Solicítalas al administrador del sistema.
