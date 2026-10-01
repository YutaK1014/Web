FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -ntp verify

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app app && mkdir -p /app/uploads/images && chown -R app:app /app
COPY --from=build /workspace/target/cashflow-0.0.1-SNAPSHOT.jar /app/app.jar
ENV TZ=Asia/Tokyo
ENV SERVER_ADDRESS=0.0.0.0
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60.0 -Duser.timezone=Asia/Tokyo"
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
