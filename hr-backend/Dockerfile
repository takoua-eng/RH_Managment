# =========================================================
# Étape 1 : construction du WAR avec Maven
# =========================================================
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Les dépendances d'abord : cette couche est réutilisée tant que pom.xml ne change pas
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# Compilation et empaquetage (les tests sont lancés par Jenkins, dans une étape séparée)
COPY src ./src
RUN mvn -B -q package -DskipTests

# =========================================================
# Étape 2 : Tomcat avec le WAR déployé
# =========================================================
FROM tomcat:10.1-jre17-temurin

# Supprime les applications d'exemple de Tomcat (sécurité et légèreté)
RUN rm -rf /usr/local/tomcat/webapps/* \
    && mkdir -p /data/uploads

# ROOT.war : l'application répond à la racine (/api/...), comme aujourd'hui
COPY --from=build /app/target/hr-backend.war /usr/local/tomcat/webapps/ROOT.war

# Valeurs par défaut, remplaçables par Kubernetes
ENV UPLOAD_DIR=/data/uploads \
    CATALINA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC"

EXPOSE 8080
CMD ["catalina.sh", "run"]