# ---------- build stage ----------
FROM maven:3.8-openjdk-17 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline            # 依存解決だけ
COPY src ./src
RUN mvn -Dmaven.test.skip=true package      # WAR ビルド
# Db2 ドライバーとライセンス Jar をまとめて取得
RUN mvn dependency:copy \
      -Dartifact=com.ibm.db2:jcc:11.5.8.0:jar \
      -DoutputDirectory=target/db2 && \
    mvn dependency:copy \
      -Dartifact=com.ibm.db2:db2jcc_license_cu:11.5.8.0:jar \
      -DoutputDirectory=target/db2

# ---------- runtime stage ----------
FROM icr.io/appcafe/open-liberty:kernel-slim-java17-openj9-ubi
# ★ ファイル名が jcc-* なのでワイルドカード変更
COPY --chown=1001:0 --from=builder /app/target/db2/*.jar \
     /opt/ol/wlp/usr/shared/resources/db2/

COPY --chown=1001:0 src/main/liberty/config/ /config/
RUN features.sh
COPY --chown=1001:0 --from=builder /app/target/*.war /config/apps/
RUN configure.sh