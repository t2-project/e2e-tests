# ui backend
FROM eclipse-temurin:17-jre
WORKDIR /opt
ENV PORT=8080
EXPOSE 8080
COPY target/*.jar /opt/app.jar
ENTRYPOINT ["java","-jar","app.jar"]
