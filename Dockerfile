
FROM eclipse-temurin:21-jdk-jammy


WORKDIR /app

COPY .mvn .mvn
COPY mvnw .
COPY mvnw.cmd .
COPY pom.xml .

RUN chmod +x ./mvnw

COPY src ./src

RUN ./mvnw clean package -DskipTests -B

RUN ls -la target/

EXPOSE 8080

CMD ["sh", "-c", "java -jar target/*.jar"]