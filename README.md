# Documentación de Cambios: Semana 6 - Solución Cloud Native (Transportes)

Este documento registra todas las modificaciones realizadas en el código fuente para cumplir con los requerimientos de la **Semana 6**, enfocándose en la integración de seguridad con **Azure AD B2C** mediante el patrón de **OAuth2 Resource Server** y la asignación de permisos por roles.

---

## 0. Cumplimiento Exacto del Requerimiento Principal
La rúbrica exige textualmente: *"Azure AD B2C: Toda la autenticación del backend springboot y del API Gateway, deben estar integrados con Azure AD. Además, deben crear 2 roles, uno que permita solo usar el endpoint de Descargar guías y el otro que permita el uso del resto de endpoints."*

Esta arquitectura da cumplimiento perfecto a dicho párrafo de la siguiente forma:
1. **"Toda la autenticación del backend springboot..."**: Logrado implementando el patrón `OAuth2 Resource Server` (vía `spring-boot-starter-oauth2-resource-server`) y configurando estáticamente el `AZURE_ISSUER_URI` de Microsoft Entra ID.
2. **"...y del API Gateway deben estar integrados con Azure AD"**: Logrado mediante la delegación perimetral utilizando un **JWT Authorizer** en AWS API Gateway, configurado con las mismas credenciales (Issuer y Audience) del inquilino B2C.
3. **"Además, deben crear 2 roles..."**: Logrado mediante la extracción inteligente de Claims personalizados (vía `JwtAuthenticationConverter`), el cual lee el atributo `extension_Role` inyectado en el token por Azure.
4. **"...uno que permita solo usar el endpoint de Descargar guías..."**: Logrado explícitamente en la capa de filtros (`SecurityConfig.java`) mediante la regla: `.requestMatchers(HttpMethod.GET, "/api/transportes/guias/descargar").hasAuthority("ROLE_DESCARGA")`.
5. **"...y el otro que permita el uso del resto de endpoints."**: Logrado aplicando la política comodín posterior: `.requestMatchers("/api/transportes/**").hasAuthority("ROLE_ADMIN")`, blindando todas las operaciones (crear, modificar, buscar, eliminar) tras el rol más alto.

---

## 1. Archivo Modificado: `pom.xml`
**Objetivo:** Incorporar las librerías necesarias para interceptar peticiones y validar tokens JWT.

**Cambios realizados:**
- Se agregó la dependencia `spring-boot-starter-security` (el "guardia de seguridad" base de Spring).
- Se agregó la dependencia `spring-boot-starter-oauth2-resource-server` (capacita al guardia para entender y validar Tokens JWT emitidos por Azure).
- Se añadieron **comentarios didácticos** explicando la función de cada bloque de dependencias (Web, Base de Datos, AWS S3, Seguridad y Herramientas).

---

## 2. Archivo Creado: `src/.../config/SecurityConfig.java`
**Objetivo:** Centralizar las reglas de autorización y definir quién puede acceder a qué endpoints.

**Cambios realizados:**
- Se deshabilitó **CSRF** (Cross-Site Request Forgery) debido a que se trata de una API REST (Stateless).
- Se configuró la aplicación como **Stateless** (Sin estado), obligando a que toda petición incluya un Token válido ("carnet").
- **Reglas de Acceso (Roles):**
  - **REGLA 1:** El endpoint de descarga (`GET /api/transportes/guias/descargar`) permite acceso si el token posee estrictamente el rol `ROLE_DESCARGA` (el admin no puede usar esta ruta, asegurando el cumplimiento de exclusividad).
  - **REGLA 2:** Todos los demás endpoints (Crear, Subir, Buscar, etc.) requieren estrictamente el rol `ROLE_ADMIN`.
  - **REGLA 3:** Cualquier otra ruta no especificada requiere autenticación básica.
- **Traductor de Claims (JwtAuthenticationConverter):** Se implementó un método que lee el token de Azure, busca un atributo personalizado llamado `extension_Role` y le antepone el prefijo `ROLE_` para que Spring Boot lo reconozca nativamente.
- Se llenó el archivo con **comentarios analógicos ("guardia", "carnet", "traductor")** para facilitar su defensa técnica.
- **Configuración de JwtDecoder:** Se implementó el bean `JwtDecoder` (utilizando `NimbusJwtDecoder`) y se configuró manualmente su `jwkSetUri` apuntando a la URL exacta con el ID del tenant (`76b75f28-e0f9-4305-9b31-f6f69d880cfe`) para asegurar la descarga correcta de las llaves.
- **Correcciones aplicadas:** Se solucionó un error de sintaxis y se agregaron las importaciones correspondientes para el correcto funcionamiento de Spring Security y Azure B2C.

---

## 3. Archivo Modificado: `src/main/resources/application.properties`
**Objetivo:** Conectar el entorno de seguridad de Spring Boot con el Proveedor de Identidad en la Nube (Azure AD B2C).

**Cambios realizados:**
- Se actualizó la propiedad `spring.security.oauth2.resourceserver.jwt.issuer-uri` reemplazando la variable de entorno por el valor exacto del emisor de Azure B2C: `https://duocazurecloudn.b2clogin.com/tfp/76b75f28-e0f9-4305-9b31-f6f69d880cfe/b2c_1_duocdemoazure_registro_login/v2.0/`.
- Esta propiedad indica a Spring dónde descargar las claves públicas de Azure para validar matemáticamente la firma de los tokens y asegurar que la fuente del token coincida exactamente con la aplicación en Azure.
- Se configuró la URL usando el identificador único del Tenant (Tenant ID) en lugar del nombre de dominio, previniendo así errores de validación de Claims del JWT ("iss" claim mismatch).
- Se añadieron comentarios detallados y explicativos en todas las secciones (Base de datos, S3, Multipart).

---

## 4. Archivo Modificado: `Dockerfile`
**Objetivo:** Documentar el proceso de creación del contenedor para la evaluación grupal.

**Cambios realizados:**
- Se mantuvieron las instrucciones originales de empaquetado (basadas en Java 21).
- Se reescribieron todos los comentarios con un tono altamente pedagógico, explicando línea por línea qué sucede en el sistema operativo Linux virtual (Imagen base, directorio de trabajo, copiado de `.jar`, exposición de puertos y comando de encendido).

---

## 5. Recordatorio de Pasos Pendientes en la Nube
Para que todo este código funcione íntegramente en la arquitectura final, es necesario recordar lo siguiente:

> **💡 TIP DE PRODUCTIVIDAD (REUTILIZACIÓN DEL TENANT):**
> No es necesario crear un Tenant de Azure AD B2C desde cero. **Se puede (y se debe) reutilizar el mismo Tenant que se creó en la Semana 5**.
> Solo es necesario realizar dos acciones en ese Tenant existente:
> 1. **Registrar la nueva App:** Crear un nuevo "Registro de aplicación" exclusivo para el Sistema de Transportes (así se obtiene un nuevo Client ID).
> 2. **Atributo de Rol:** Asegurarse de crear un "Atributo de usuario" personalizado (ej. `Role`) y marcarlo para que viaje en los *Application Claims* del Flujo de Usuario. Ahí es donde se asignará el valor `DESCARGA` o `ADMIN` a los usuarios.

1. **En AWS EC2 / Docker:** Pasar la variable de entorno `AZURE_ISSUER_URI` al momento de hacer el `docker run`. La URI del Issuer será exactamente la misma que se utilizó en la Semana 5.
2. **En AWS API Gateway:** Crear el *JWT Authorizer* utilizando la misma URI de emisor (`iss`) y el **nuevo** Client ID (`aud`) del registro de aplicación de Transportes.

---

## 6. Evolución de la Arquitectura (Semana 5 vs Semana 6)
Considerando la integración de IDaaS lograda en la Semana 5, este nuevo sprint (Semana 6) introduce tres mejoras clave en el backend:

1. **De "Autenticado" a "Control por Roles (RBAC)":**
   En la iteración anterior bastaba con tener un token válido (`.anyRequest().authenticated()`). Ahora el servidor de recursos aplica Control de Acceso Basado en Roles (RBAC). Si el usuario no tiene el rol requerido (`ROLE_DESCARGA` o `ROLE_ADMIN`), el acceso a los métodos restringidos del controlador será denegado con un `403 Forbidden`, sin importar que el token de Azure sea válido.
2. **El "Traductor" de Claims (Claims Converter):**
   Se desarrolló un conversor de autenticación (`JwtGrantedAuthoritiesConverter`) capaz de extraer el atributo personalizado de Azure B2C (ej. `extension_Role`) y transformarlo al formato estándar nativo de Spring Security añadiéndole el prefijo `ROLE_`.
3. **Simplificación de Credenciales (Puro Resource Server):**
   A diferencia del flujo OAuth2 Cliente donde se requiere el `Client Secret`, esta aplicación actúa como un **Resource Server puro**. La validación asimétrica se logra delegando la confianza únicamente en la URI del emisor (`issuer-uri`), lo que permite a Spring descargar las llaves públicas (JWKS) sin necesidad de transportar secretos de cliente desde GitHub hacia AWS.

---

## 7. Asociación y Almacenamiento con Amazon S3
La arquitectura implementa un modelo distribuido perfecto donde se separa el almacenamiento de datos estructurados de los archivos binarios (Blobs):

1. **La Conexión (`S3Config.java`):**
   Spring Cloud AWS (SDK v2) utiliza el patrón de inyección de propiedades para construir un `S3Client` seguro usando el `access-key`, `secret-key` y `session-token` de AWS proveídos en el entorno.
2. **La Subida (`GuiaServiceImpl.java`):**
   A través de la abstracción `S3Template`, el código toma el `MultipartFile` y lo transmite por secuencias directamente al Bucket de S3 de la cuenta (`s3Template.upload(...)`), luego de haber creado una copia local en el EFS de AWS.
3. **La Asociación Lógica en Base de Datos:**
   El documento físico no ingresa jamás a Oracle. En su lugar, el servicio captura la ruta final en la nube (ej. `20266/Transportista/guia.pdf`) y la persiste en el registro de `GuiaDespacho` mediante JPA. La base de datos relacional opera así como un catálogo de punteros altamente eficiente hacia el Object Storage de Amazon.

---

## 8. Matriz de Endpoints y Securitización
Para dar cumplimiento estricto a los requerimientos de la actividad, absolutamente todos los endpoints del backend se encuentran blindados por Spring Security con los siguientes niveles de acceso:

| Requerimiento (Rúbrica) | Método & Endpoint (API REST) | Permiso Exigido (Rol) |
| :--- | :--- | :--- |
| **Crear y subir guías a S3** | `POST /api/transportes/guias/subir` | `ROLE_ADMIN` |
| **Descargar guías con validación** | `GET /api/transportes/guias/descargar` | `ROLE_DESCARGA` |
| **Modificar o actualizar guías** | `PUT /api/transportes/guias/actualizar` | `ROLE_ADMIN` |
| **Eliminar guías específicas** | `DELETE /api/transportes/guias/eliminar` | `ROLE_ADMIN` |
| **Consultar guías por transportista/fecha**| `GET /api/transportes/guias/buscar` | `ROLE_ADMIN` |

> **Nota Técnica:** 
> La securitización masiva se logró mediante el comodín de ruta `.requestMatchers("/api/transportes/**").hasAuthority("ROLE_ADMIN")`, el cual intercepta y protege por defecto cualquier operación de creación, modificación, búsqueda o eliminación, garantizando que no existan fugas de seguridad (endpoints expuestos accidentalmente). La única excepción explícita es el endpoint de descarga, el cual otorga acceso al rol menos privilegiado (`ROLE_DESCARGA`).

---

## 9. Guía de Ejecución y Pruebas (Checklist Final)
Para desplegar y validar toda esta arquitectura en la nube, se debe seguir el siguiente flujo operativo:

### Fase 1: Despliegue (GitHub Actions)
1. Hacer un `git push` de los cambios hacia la rama principal.
2. Asegurarse de que en los **Secrets** del repositorio de GitHub exista la variable `AZURE_ISSUER_URI` con el valor exacto del Issuer obtenido en Azure B2C (ej. `https://<tenant>.b2clogin.com/...`).
3. Esperar a que el pipeline de GitHub Actions finalice y despliegue la nueva imagen de Docker en la máquina EC2.

### Fase 2: Configuración de Roles en Azure AD B2C
1. **Atributo Personalizado:** En el portal de Azure, dentro de Azure AD B2C, ir a *Atributos de usuario* y crear un nuevo atributo llamado `Role` (tipo String).
2. **Inyección en el Token:** Ir a *Flujos de usuario*, seleccionar el flujo activo, entrar a *Notificaciones de la aplicación* (Application claims) y marcar el atributo `Role`.
3. **Asignación a Usuarios:** En la sección de *Usuarios*, se configuraron las siguientes cuentas de prueba con sus respectivos roles en el campo de perfil:
   - Cuenta **`nii.furude`**: Se le asignó el rol **`ADMIN`** (acceso total a la API).
   - Cuenta **`carolina.lybernn@gmaill.com`**: Se le asignó el rol **`DESCARGA`** (acceso restringido solo para el endpoint de descargas).

### Fase 3: Pruebas de Seguridad en Postman (IP EC2)
Para demostrar la efectividad del Resource Server (Spring Security) independientemente del API Gateway:
1. Obtener un token de Azure iniciando sesión con el usuario de rol **`DESCARGA`**.
2. Realizar una petición `GET` a `http://<IP_EC2>:8080/api/transportes/guias/descargar` inyectando el token. El resultado debe ser exitoso (`200 OK`).
3. Con el mismo token, intentar un `POST` a `/api/transportes/guias/subir`. El sistema debe rechazar la petición con un contundente **`403 Forbidden`**.
4. Repetir la prueba con el usuario **`ADMIN`** para corroborar que este sí posee el acceso total.

### Fase 4: Integración con AWS API Gateway
Para cumplir el ciclo completo de la arquitectura perimetral:
1. Ir a AWS API Gateway y editar la API de la semana anterior.
2. Registrar las nuevas rutas de `/transportes` (`/subir`, `/descargar`, etc.) apuntándolas hacia la IP de la instancia EC2.
3. Asegurarse de que el **JWT Authorizer** esté activo en estas rutas.
4. Desplegar la API y realizar la validación final demostrando que el tráfico fluye desde el Gateway, es validado en su rol por la EC2, y finalmente el documento es procesado y almacenado en Amazon S3.
