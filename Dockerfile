FROM eclipse-temurin:18-jdk-alpine
WORKDIR /app
COPY . .
RUN mkdir -p bin && javac -cp "h2.jar:gson.jar" -d bin $(find src -name "*.java")
EXPOSE 8080
CMD ["java", "-cp", "bin:h2.jar:gson.jar", "com.dataprocessor.App", "8080"]
