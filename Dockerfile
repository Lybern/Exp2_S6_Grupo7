# 1. IMAGEN BASE (EL SISTEMA OPERATIVO)
# "eclipse-temurin:21-jdk-jammy" es como decir: "Instálame un Linux súper ligero 
# que ya venga con Java 21 configurado y listo para usar".
FROM eclipse-temurin:21-jdk-jammy

# 2. DIRECTORIO DE TRABAJO
# Aquí creamos una carpeta llamada "/app" dentro del contenedor (el Linux virtual)
# y le decimos a Docker que a partir de ahora, todo se ejecute dentro de ella.
WORKDIR /app

# 3. EMPAQUETADO DEL CÓDIGO
# Toma el archivo ".jar" (que es nuestra aplicación ya compilada y comprimida por Maven)
# desde la carpeta "target/" de nuestra computadora, y lo copia dentro del Linux 
# virtual, renombrándolo como "app.jar" para que sea más fácil de llamar.
COPY target/*.jar app.jar

# 4. EXPOSICIÓN DE PUERTOS
# Le avisa al sistema que nuestra aplicación va a estar atendiendo peticiones
# a través de la puerta número 8080 (el puerto estándar de Spring Boot).
EXPOSE 8080

# 5. COMANDO DE ENCENDIDO
# Estas son las instrucciones exactas que Docker ejecutará justo cuando prendamos el contenedor.
# En palabras simples, abre la terminal y escribe: "java -jar app.jar" para arrancar el servidor.
ENTRYPOINT ["java", "-jar", "app.jar"]