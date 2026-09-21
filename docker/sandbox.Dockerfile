# Build from repository root: docker build -f docker/sandbox.Dockerfile -t codebase-doctor-sandbox:local .
# This is the only network-enabled dependency preparation step. Review all inputs first.
# Pin the base images to reviewed registry digests for a distributed/production release.
FROM gradle:8.14.3-jdk21 AS gradle
FROM maven:3.9.9-eclipse-temurin-21 AS seed
WORKDIR /seed
COPY examples/broken-spring-app/pom.xml /seed/pom.xml
# Only the shipped, trusted example POM is resolved at image-build time. Never copy an imported repository here.
RUN mvn --batch-mode --no-transfer-progress -Dmaven.repo.local=/opt/doctor/m2 dependency:go-offline
# Exercise JUnit provider resolution against a trusted one-test fixture, independent of the deliberately broken example.
RUN mkdir -p src/test/java/dev/codebasedoctor/cache && \
    printf '%s\n' 'package dev.codebasedoctor.cache;' 'import org.junit.jupiter.api.Test;' 'class CacheWarmupTest { @Test void warmup() {} }' > src/test/java/dev/codebasedoctor/cache/CacheWarmupTest.java && \
    mvn --batch-mode --no-transfer-progress -Dmaven.repo.local=/opt/doctor/m2 -Dspring-boot.repackage.skip=true package

FROM maven:3.9.9-eclipse-temurin-21
USER root
RUN apt-get update && apt-get install -y --no-install-recommends python3 coreutils && \
    rm -rf /var/lib/apt/lists/* && \
    groupadd --gid 10001 doctor-build && \
    useradd --uid 10000 --gid 10001 --no-create-home --shell /usr/sbin/nologin doctor-helper && \
    useradd --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin doctor-build && \
    mkdir -p /workspace /cache /state /opt/doctor
COPY --from=gradle /opt/gradle /opt/gradle
COPY --from=seed /opt/doctor/m2 /opt/doctor/m2
COPY docker/guard.py /opt/doctor/guard.py
RUN chown -R root:root /opt/doctor /opt/gradle && \
    chmod 0555 /opt/doctor /opt/doctor/guard.py && \
    chmod -R a-w /opt/doctor/m2 /opt/gradle && \
    chmod 0770 /workspace /cache && chown 10000:10001 /workspace /cache && \
    chmod 0700 /state && chown 10000:10000 /state
LABEL dev.codebasedoctor.guard-version="1"
ENV JAVA_HOME=/opt/java/openjdk
WORKDIR /workspace
USER 10000:10001
ENTRYPOINT ["/usr/bin/sleep"]
CMD ["480"]
