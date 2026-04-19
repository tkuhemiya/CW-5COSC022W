# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q -e -DskipTests dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

FROM tomcat:10.1-jre17-temurin-alpine

# Remove default tomcat app
RUN rm -rf /usr/local/tomcat/webapps/*
# Copy to webapps and rename to ROOT.war (available at /)
COPY --from=build /app/target/smartcampus-api.war /usr/local/tomcat/webapps/ROOT.war

EXPOSE 8080
CMD ["catalina.sh", "run"]
