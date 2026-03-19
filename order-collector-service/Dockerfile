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

# wget 설치 (헬스체크용)
RUN apk add --no-cache wget

# 비root 사용자 생성 (보안)
RUN addgroup -g 1001 spring && \
    adduser -u 1001 -G spring -s /bin/sh -D spring

# JAR 파일 복사
COPY --from=builder /app/build/libs/*.jar app.jar

# 사용자 전환
USER spring:spring

# PORT 환경변수 노출
ENV PORT=8091

# Railway는 자체 healthcheck를 수행하므로 제거
# HEALTHCHECK 제거됨

# 시작 명령 (안정성 최적화)
CMD ["sh", "-c", "java -Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=100 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom -Dspring.profiles.active=prod -Dserver.port=${PORT} -Duser.timezone=Asia/Seoul -jar app.jar"]
