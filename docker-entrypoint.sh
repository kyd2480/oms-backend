#!/bin/sh
set -eu

storage_dir="${RECORDING_VIDEO_STORAGE_DIR:-}"
if [ -n "$storage_dir" ]; then
  mkdir -p "$storage_dir"
  chown spring:spring "$storage_dir" || true
  chmod u+rwX,g+rwX "$storage_dir" || true
fi

exec su-exec spring:spring sh -c 'java -Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=100 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom -Dspring.profiles.active=prod -Dserver.port=${PORT} -Duser.timezone=Asia/Seoul -jar app.jar'
