FROM maven:3.8-jdk-11-slim AS build

WORKDIR /build

COPY pom.xml pom.xml
RUN mvn -f pom.xml --batch-mode dependency:go-offline

COPY src src
COPY checkstyle.xml checkstyle.xml

# Would like to run these with --offline, but the checkstyle
# plugin breaks things.
RUN mvn -f pom.xml --batch-mode clean install
RUN mvn -f pom.xml --batch-mode dependency:copy \
    -DoutputDirectory="/app" \
    -Dartifact='org.broadinstitute.ddp.cf:cf-file-scanner:1.0-SNAPSHOT'
RUN mvn -f pom.xml --batch-mode dependency:copy \
    -Dartifact='com.google.cloud.functions.invoker:java-function-invoker:1.1.0' \
    -DoutputDirectory=/app

FROM openjdk:11-jdk AS runtime

ENV GCP_PROJECT=""
ENV RESULT_TOPIC=""
ENV DDP_CLAMAV_SERVER="localhost:3310"

COPY --from=build /app /app

WORKDIR /app

CMD java \
    -jar java-function-invoker-1.1.0.jar \
    --classpath cf-file-scanner-1.0-SNAPSHOT.jar \
    --target org.broadinstitute.ddp.cf.FileScanner