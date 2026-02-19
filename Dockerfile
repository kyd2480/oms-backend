# Stage 1: Build
FROM gradle:8.5-jdk17 AS builder

WORKDIR /app

# Gradle 캐싱 최적화
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
RUN gradle dependencies --no-daemon

# 소스 복사 및 빌드
COPY src ./src
RUN gradle clean build -x test --no-daemon --parallel

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 비root 사용자 생성 (보안)
RUN addgroup -g 1001 spring && \
    adduser -u 1001 -G spring -s /bin/sh -D spring

# JAR 파일 복사
COPY --from=builder /app/build/libs/*.jar app.jar

# 사용자 전환
USER spring:spring

# 헬스체크
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:${PORT:-8091}/actuator/health || exit 1

# 시작 명령 (안정성 최적화)
ENTRYPOINT ["java", \
  "-Xms256m", \
  "-Xmx512m", \
  "-XX:+UseG1GC", \
  "-XX:MaxGCPauseMillis=100", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Dspring.profiles.active=prod", \
  "-Duser.timezone=Asia/Seoul", \
  "-jar", \
  "app.jar"]
