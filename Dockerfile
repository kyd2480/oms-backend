# Stage 1: Build
FROM gradle:8.5-jdk17 AS builder

WORKDIR /app

# Gradle 캐싱 최적화
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
RUN gradle dependencies --no-daemon

# 소스 복사 및 빌드
COPY src ./src
RUN gradle clean build -x test --no-daemon --no-build-cache

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# wget 설치 (헬스체크용), su-exec 설치 (권한 조정 후 비root 실행)
RUN apk add --no-cache wget su-exec

# 비root 사용자 생성 (보안)
RUN addgroup -g 1001 spring && \
    adduser -u 1001 -G spring -s /bin/sh -D spring

# JAR 파일 복사
COPY --from=builder /app/build/libs/*.jar app.jar
COPY docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod +x /app/docker-entrypoint.sh

# PORT 환경변수 노출
ENV PORT=8091

# Railway는 자체 healthcheck를 수행하므로 제거
# HEALTHCHECK 제거됨

# 시작 명령 (Volume 권한 조정 후 비root 앱 실행)
ENTRYPOINT ["/app/docker-entrypoint.sh"]
