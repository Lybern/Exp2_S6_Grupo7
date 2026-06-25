# Documentación de Cambios: Semana 6 - Solución Cloud Native (Transportes)

Este documento registra todas las modificaciones realizadas en el código fuente para cumplir con los requerimientos de la **Semana 6**, enfocándose en la integración de seguridad con **Azure AD B2C** mediante el patrón de **OAuth2 Resource Server** y la asignación de permisos por roles.

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
- Se deshabilitó **CSRF** (Cross-Site Request Forgery) porque somos una API REST (Stateless).
- Se configuró la aplicación como **Stateless** (Sin estado), obligando a que toda petición incluya un Token válido ("carnet").
- **Reglas de Acceso (Roles):**
  - **REGLA 1:** El endpoint de descarga (`GET /api/transportes/guias/descargar`) permite acceso si el token posee el rol `ROLE_DESCARGA` o `ROLE_ADMIN`.
  - **REGLA 2:** Todos los demás endpoints (Crear, Subir, Buscar, etc.) requieren estrictamente el rol `ROLE_ADMIN`.
  - **REGLA 3:** Cualquier otra ruta no especificada requiere autenticación básica.
- **Traductor de Claims (JwtAuthenticationConverter):** Se implementó un método que lee el token de Azure, busca un atributo personalizado llamado `extension_Role` y le antepone el prefijo `ROLE_` para que Spring Boot lo reconozca nativamente.
- Se llenó el archivo con **comentarios analógicos ("guardia", "carnet", "traductor")** para facilitar su defensa técnica.

---

## 3. Archivo Modificado: `src/main/resources/application.properties`
**Objetivo:** Conectar el entorno de seguridad de Spring Boot con el Proveedor de Identidad en la Nube (Azure AD B2C).

**Cambios realizados:**
- Se agregó la propiedad `spring.security.oauth2.resourceserver.jwt.issuer-uri=${AZURE_ISSUER_URI}`.
- Esta propiedad indica a Spring dónde descargar las claves públicas de Azure para validar matemáticamente la firma de los tokens y asegurar que no estén falsificados.
- Se inyectó como variable de entorno (`${AZURE_ISSUER_URI}`) para cumplir con buenas prácticas de seguridad y no quemar credenciales en el código.
- Se añadieron comentarios detallados y explicativos en todas las secciones (Base de datos, S3, Multipart).

---

## 4. Archivo Modificado: `Dockerfile`
**Objetivo:** Documentar el proceso de creación del contenedor para la evaluación grupal.

**Cambios realizados:**
- Se mantuvieron las instrucciones originales de empaquetado (basadas en Java 21).
- Se reescribieron todos los comentarios con un tono altamente pedagógico, explicando línea por línea qué sucede en el sistema operativo Linux virtual (Imagen base, directorio de trabajo, copiado de `.jar`, exposición de puertos y comando de encendido).

---

## 5. Recordatorio de Pasos Pendientes en la Nube
Para que todo este código funcione íntegramente en la arquitectura final, recuerda:

> **💡 TIP DE PRODUCTIVIDAD (REUTILIZACIÓN DEL TENANT):**
> No necesitas crear un Tenant de Azure AD B2C desde cero. **Puedes (y debes) reutilizar el mismo Tenant que creaste en la Semana 5**.
> Solo necesitas hacer dos cosas en ese Tenant existente:
> 1. **Registrar la nueva App:** Crear un nuevo "Registro de aplicación" exclusivo para el Sistema de Transportes (así obtienes un nuevo Client ID).
> 2. **Atributo de Rol:** Asegurarte de crear un "Atributo de usuario" personalizado (ej. `Role`) y marcarlo para que viaje en los *Application Claims* de tu Flujo de Usuario. Ahí es donde le asignarás el valor `DESCARGA` o `ADMIN` a los usuarios.

1. **En AWS EC2 / Docker:** Pasar la variable de entorno `AZURE_ISSUER_URI` al momento de hacer el `docker run`. La URI del Issuer será exactamente la misma que usaste en la Semana 5.
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
| **Descargar guías con validación** | `GET /api/transportes/guias/descargar` | `ROLE_DESCARGA` (o `ROLE_ADMIN`) |
| **Modificar o actualizar guías** | `PUT /api/transportes/guias/actualizar` | `ROLE_ADMIN` |
| **Eliminar guías específicas** | `DELETE /api/transportes/guias/eliminar` | `ROLE_ADMIN` |
| **Consultar guías por transportista/fecha**| `GET /api/transportes/guias/buscar` | `ROLE_ADMIN` |

> **Nota Técnica:** 
> La securitización masiva se logró mediante el comodín de ruta `.requestMatchers("/api/transportes/**").hasAuthority("ROLE_ADMIN")`, el cual intercepta y protege por defecto cualquier operación de creación, modificación, búsqueda o eliminación, garantizando que no existan fugas de seguridad (endpoints expuestos accidentalmente). La única excepción explícita es el endpoint de descarga, el cual otorga acceso al rol menos privilegiado (`ROLE_DESCARGA`).
