# ============================================================
# STAGE 1 — DEPENDENCY CACHE
# Tách riêng bước download deps để tận dụng Docker layer cache.
# Chỉ re-download khi pom.xml thay đổi, không phải mỗi lần build.
# ============================================================
FROM maven:3.9-eclipse-temurin-21-alpine AS deps
WORKDIR /build

COPY pom.xml .
# Tải toàn bộ dependencies về offline — layer này được cache lại
RUN mvn dependency:go-offline -B --no-transfer-progress


# ============================================================
# STAGE 2 — BUILD & EXTRACT LAYERS
# Build jar rồi dùng Spring Boot Layertools để tách thành
# nhiều layer riêng biệt → Docker cache hiệu quả hơn ở stage sau.
# ============================================================
FROM deps AS builder

COPY src ./src

# Build, bỏ qua tests (test nên chạy trong CI pipeline riêng)
RUN mvn clean package -DskipTests -B --no-transfer-progress

# Trích xuất layered jar → tối ưu Docker image layers
# Thứ tự: dependencies → loader → snapshot-deps → application code
RUN java -Djarmode=layertools \
    -jar target/*.jar extract \
    --destination target/extracted


# ============================================================
# STAGE 3 — RUNTIME IMAGE (image cuối cùng, nhỏ gọn nhất)
# Chỉ dùng JRE (không cần JDK), base image Alpine (~80MB).
# ============================================================
FROM eclipse-temurin:21-jre-alpine AS runtime

# --- Security: không chạy với root ---
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy từng layer theo đúng thứ tự (layer ít thay đổi nhất lên trước)
# → Docker chỉ rebuild layer bị thay đổi, các layer trên được cache
COPY --from=builder --chown=appuser:appgroup /build/target/extracted/dependencies/          ./
COPY --from=builder --chown=appuser:appgroup /build/target/extracted/spring-boot-loader/    ./
COPY --from=builder --chown=appuser:appgroup /build/target/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=appuser:appgroup /build/target/extracted/application/           ./

USER appuser

EXPOSE 8080

# --- JVM Tuning cho môi trường container ---
# UseContainerSupport : JVM đọc memory limit từ cgroup (không phải RAM vật lý)
# MaxRAMPercentage    : Dùng tối đa 75% RAM được cấp cho container
# UseG1GC             : GC phù hợp cho ứng dụng có heap lớn, latency thấp
# TieredStopAtLevel=1 : Tắt JIT compilation nặng → startup nhanh hơn (hữu ích với Kubernetes)
# egd                 : Tăng tốc SecureRandom — tránh block I/O do entropy
ENV JAVA_OPTS="\
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -XX:+UseG1GC \
  -XX:TieredStopAtLevel=1 \
  -Djava.security.egd=file:/dev/./urandom \
  -Dspring.backgroundpreinitializer.ignore=true"

# --- Health Check tích hợp sẵn ---
# Yêu cầu thêm spring-boot-starter-actuator vào pom.xml
# start-period: cho app thời gian khởi động trước khi bị đánh fail
HEALTHCHECK \
  --interval=30s \
  --timeout=10s \
  --start-period=60s \
  --retries=3 \
  CMD wget --quiet --tries=1 --spider \
      http://localhost:8080/actuator/health || exit 1

# Dùng exec form (không qua shell) → nhận SIGTERM trực tiếp → graceful shutdown
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
