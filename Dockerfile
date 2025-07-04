# ---------- build stage ----------
FROM maven:3.8-openjdk-17 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

# Db2 JDBC ドライバーを取得（1 行で OK）
RUN mvn -q dependency:copy \
      -Dartifact=com.ibm.db2:jcc:12.1.2.0:jar \
      -DoutputDirectory=target/db2

# ---------- runtime stage ----------
FROM icr.io/appcafe/open-liberty:kernel-slim-java17-openj9-ubi

# Db2 JDBC ドライバーを shared lib へ
COPY --chown=1001:0 --from=builder /app/target/db2/jcc-*.jar \
     /opt/ol/wlp/usr/shared/resources/db2/

# Liberty 設定ファイル（server.xml など）
COPY --chown=1001:0 src/main/liberty/config/ /config/


# 必要な Liberty 機能をインストール
RUN features.sh

# アプリケーション WAR
COPY --chown=1001:0 --from=builder /app/target/*.war /config/apps/

# キャッシュ作成・最終調整
RUN configure.sh

