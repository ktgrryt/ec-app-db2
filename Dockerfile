# --- file: Dockerfile
# ---------- build stage ----------
FROM maven:3.8-openjdk-17 AS builder
WORKDIR /app

# pom.xml だけで依存解決
COPY pom.xml .
RUN mvn -q dependency:go-offline

# ソースをコピーしてビルド
COPY src ./src
RUN mvn -Dmaven.test.skip=true package \
 && mvn dependency:copy \
      -Dartifact=com.ibm.db2:jcc:11.5.0.0 \
      -DoutputDirectory=target/db2

# ---------- runtime stage ----------
FROM icr.io/appcafe/open-liberty:kernel-slim-java17-openj9-ubi

# Db2 JDBC ドライバー配置
COPY --chown=1001:0 --from=builder /app/target/db2/db2jcc*.jar \
      /opt/ol/wlp/usr/shared/resources/db2/

# Liberty 設定ファイル
COPY --chown=1001:0 src/main/liberty/config/ /config/

# 必要な Liberty 機能をインストール
RUN features.sh

# アプリ WAR
COPY --chown=1001:0 --from=builder /app/target/*.war /config/apps/

# キャッシュの作成
RUN configure.sh