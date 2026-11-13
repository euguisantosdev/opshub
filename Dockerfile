# NÃO VALIDADO LOCALMENTE: o ambiente de desenvolvimento não tinha Docker. Revise antes de usar.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 opshub
WORKDIR /app
COPY --from=build /src/target/opshub-*.jar /app/opshub.jar
USER opshub
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "/app/opshub.jar"]
