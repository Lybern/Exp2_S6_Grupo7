package duoc.sumativa.transportes.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;


@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Este es el "guardia de seguridad" principal de nuestra aplicación.
     * Aquí definimos las reglas de quién puede entrar y a qué lugares.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 1. Apagamos la protección CSRF. 
            // Como esta aplicación es una API y no una página web tradicional con formularios, 
            // no necesitamos esta protección.
            .csrf(csrf -> csrf.disable())
            
            // 2. Configuramos el sistema para que no guarde memoria de los usuarios (Stateless).
            // Esto significa que en cada petición que nos hagan, el usuario debe presentar su "carnet" (el Token de Azure).
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            
            // 3. Aquí definimos las reglas de los permisos (quién entra a dónde)
            .authorizeHttpRequests(authz -> authz
                
                // REGLA 1: Para descargar guías.
                // Si alguien intenta entrar a la ruta "/api/transportes/guias/descargar", 
                // le exigimos que en su token tenga el permiso (rol) de "DESCARGA" estrictamente.
                .requestMatchers(HttpMethod.GET, "/api/transportes/guias/descargar").hasAuthority("ROLE_DESCARGA")
                
                // REGLA 2: Para todo lo demás (crear, buscar, eliminar, etc.).
                // Si intentan usar cualquier otra función, obligatoriamente deben ser "ADMIN".
                .requestMatchers("/api/transportes/**").hasAuthority("ROLE_ADMIN")
                
                // REGLA 3: Por seguridad, cualquier otra ruta que no hayamos mencionado arriba
                // también requerirá que el usuario esté identificado (autenticado).
                .anyRequest().authenticated()
            )
            
            // 4. Le decimos a nuestro sistema que funcionará recibiendo Tokens (tipo JWT).
            // Además, le pasamos nuestro "traductor" personalizado que configuramos más abajo, 
            // el cual sabe cómo leer los roles que vienen desde Azure B2C.
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            );

        // Finalmente, armamos todas las reglas y se las pasamos a Spring Boot.
        return http.build();
    }

 
    /**
     * Este componente es como un "traductor".
     * Cuando llega el Token desde Azure, Azure guarda los roles con un nombre especial.
     * Este traductor se encarga de buscar ese nombre y transformarlo a un formato que Spring Boot entienda.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        
        // Azure B2C normalmente guarda los roles en un campo que empieza con "extension_".
        // Le decimos a nuestro traductor que busque los roles exactamente en el campo "extension_consultaRole".
        // (Si en Azure le pusiste otro nombre al atributo de usuario, debes cambiarlo aquí).
        grantedAuthoritiesConverter.setAuthoritiesClaimName("extension_consultaRole");
        
        // Spring Boot está acostumbrado a que todos los roles empiecen con la palabra "ROLE_".
        // Así que le decimos al traductor que le pegue ese texto al principio (ej: DESCARGA pasará a ser ROLE_DESCARGA).
        grantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
        return jwtAuthenticationConverter;
    }

}
