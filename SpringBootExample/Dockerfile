ARG java_version
ARG maven_version
 
FROM maven:${maven_version}-openjdk-${java_version}-slim
 
RUN groupadd -r appuser && useradd -r -g appuser appuser
 
RUN mkdir /app && chown appuser:appuser /app
 
USER appuser
 
COPY --chown=appuser:appuser target/*.jar /app/
 
WORKDIR /app
 
ENTRYPOINT ["java", "-jar", "/app/*.jar"]
