# Sistema Web "Edificio La Cruceta" — Cobro de Cuotas de Mantenimiento

Aplicación web completa desarrollada con **Java 17 (Spring Boot)**, **React 18**, **HTML/CSS** y **MySQL**, compatible con **Visual Studio Code**.

## Modelo de negocio
- El edificio **La Cruceta** cuenta con departamentos (**Unidades**) en distintas torres, y cada departamento tiene su propio dueño (**Propietario**).
- Cada mes la administración ingresa cuánto gastó el edificio en cada concepto (**Vigilancia, Limpieza, Agua de áreas comunes, Luz de áreas comunes, Mantenimiento de ascensor, Fondo de reserva**).
- El sistema suma los gastos del mes y los divide entre los departamentos activos para calcular y emitir la **cuota mensual** (`CUOTA` + `DETALLE_CUOTA`).
- Los propietarios consultan su estado de cuenta, descargan su aviso de cobro y **registran sus pagos** adjuntando su comprobante (`PAGO` + `COMPROBANTE`), el cual queda guardado en MySQL.
- El administrador valida o rechaza los comprobantes; al validar se emite automáticamente el **Recibo / Constancia de Pago** (`RECIBO`) y la cuota pasa a `PAGADA`.

---

## Estructura de la Base de Datos (`edificio_cruceta`)
Sigue exactamente el diagrama de clases/BD entregado:
1. `rol` (`id_rol`, `nombre_rol`)
2. `unidad` (`id_unidad`, `bloque`, `numero_unidad`, `estado`)
3. `usuario` (`id_usuario`, `dni`, `nombre`, `apellido`, `fecha_nacimiento`, `telefono`, `correo`, `direccion`, `contrasena`, `id_rol`, `id_unidad`, `estado_cuenta`)
4. `concepto_cobro` (`id_concepto`, `nombre_concepto`, `tipo_concepto`, `monto_base`)
5. `cuota` (`id_cuota`, `id_unidad`, `periodo`, `fecha_emision`, `fecha_vencimiento`, `estado_cuota`)
6. `detalle_cuota` (`id_cuota`, `id_concepto`, `monto_concepto`)
7. `pago` (`id_pago`, `id_cuota`, `id_usuario`, `fecha_pago`, `monto_pagado`, `metodo_pago`, `nro_operacion`, `estado_pago`)
8. `comprobante` (`id_comprobante`, `id_pago`, `archivo`, `fecha_carga`, `estado_validacion`, `observacion`)
9. `recibo` (`id_recibo`, `numero_recibo`, `fecha_emision`, `id_pago`, `observacion`)

> **Creación automática:** Al iniciar el proyecto, Spring Boot se conecta a MySQL (`root` / `admin`), crea la base de datos `edificio_cruceta` si no existe, crea las 9 tablas ([`schema.sql`](src/main/resources/schema.sql)) y carga los registros predefinidos ([`data.sql`](src/main/resources/data.sql)).

---

## Cómo ejecutar en Visual Studio Code

1. Abre la carpeta `c:\tareas\8vo\integrador\grupo 5` en **VS Code**.
2. Asegúrate de que el servicio **MySQL80** esté activo (usuario `root`, contraseña `admin` configurados en [`src/main/resources/application.properties`](src/main/resources/application.properties)).
3. Tienes 3 formas de iniciarlo:
   - **Con F5 / Run Java:** presiona `F5` (usa [`.vscode/launch.json`](.vscode/launch.json)).
   - **Desde la terminal de VS Code:**
     ```powershell
     .\mvnw.cmd spring-boot:run
     ```
   - **Doble clic** en [`iniciar.bat`](iniciar.bat).
4. Abre en el navegador: **http://localhost:8080**

---

## Cuentas predefinidas para pruebas

| Rol | Correo | Contraseña | Departamento |
|---|---|---|---|
| **Administrador** | `admin@cruceta.com` | `admin123` | Administración |
| **Propietario** | `carlos.mendoza@email.com` | `123456` | Torre B - 302 |
| **Propietario (con mora)** | `miguel.chavez@email.com` | `123456` | Torre B - 101 |
| **Otros propietarios** | `luis.torres@email.com`, `maria.flores@email.com`, etc. | `123456` | Torres A y B |
