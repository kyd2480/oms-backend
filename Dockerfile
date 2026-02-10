# Multi-stage build for Spring Boot
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# Gradle wrapper와 설정 파일 복사
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# 소스 코드 복사
COPY src src

# Gradle wrapper 실행 권한 부여
RUN chmod +x gradlew

# 빌드 (테스트 제외)
RUN ./gradlew clean build -x test --no-daemon

# Runtime stage
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 빌드된 JAR 복사
COPY --from=builder /app/build/libs/*.jar app.jar

# 포트 노출
EXPOSE 8091

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8091/actuator/health || exit 1

# 애플리케이션 실행
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=prod", "app.jar"]
