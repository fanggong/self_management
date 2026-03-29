FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN sh -lc 'for attempt in 1 2 3 4 5; do \
  mvn -B -ntp -Dmaven.test.skip=true dependency:go-offline && exit 0; \
  echo "dependency:go-offline attempt ${attempt} failed; retrying..."; \
  sleep 5; \
done; \
exit 1'
COPY src ./src
RUN sh -lc 'for attempt in 1 2 3 4 5; do \
  mvn -B -ntp -Dmaven.test.skip=true package && exit 0; \
  echo "package attempt ${attempt} failed; retrying..."; \
  sleep 5; \
done; \
exit 1'

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/target/admin-api-0.1.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
