package com.esprit.microservice.hrbackend;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * Point d'entrée quand l'application est déployée en WAR dans un Tomcat externe.
 * Le démarrage depuis IntelliJ (méthode main de HrBackendApplication) fonctionne toujours.
 */
public class ServletInitializer extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(HrBackendApplication.class);
    }
}