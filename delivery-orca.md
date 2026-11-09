# Reporte de Entrega: Migración Java 21 → Java 25 (Proyecto Logitrack)

---

## 1. Resumen Ejecutivo

Se ejecutó y validó la migración de **Java 21 a Java 25** en los 4 archivos correspondientes a los repositorios `chatbot-service` y `deploy` dentro de `logitrack-repos`.

* **Estándar del proyecto:** Java 25 (Spring Boot 3.5.16, Spring Cloud 2025.0.3).
* **Distribución Java en CI (GitHub Actions):** Eclipse Temurin (`temurin`).
* **Imágenes Docker runtime:** `eclipse-temurin:25-jre@sha256:dbb43d81099eec91e0800226ed2296d03146ee7857165b95112372ad0d67e87f`.
* **Cumplimiento de restricciones:**
  - No se alteró lógica de negocio, dependencias, versiones de Spring ni pruebas.
  - No se ejecutaron builds pesados locales (`mvn verify` / `mvn clean install`).
  - Se mantuvieron intactos los digests estándar del workspace.

---

## 2. Detalle de Archivos y Trazabilidad Git

### Repositorio: `logitrack-repos/chatbot-service`
* **Estado:** Merged en `develop` vía [PR #4](https://github.com/Logitrack-Sa/chatbot-service/pull/4) (`commit 4629728`). Rama local y remota `fix-observaciones-bot-97-98-99` sincronizadas.
* **Archivos afectados:**
  1. `.github/workflows/ci.yml`: `java-version: '25'`, nombre actualizado a Java 25.
  2. `pom.xml`: `<java.version>25</java.version>`.
  3. `Dockerfile`: `eclipse-temurin:25-jre@sha256:dbb43d81099eec91e0800226ed2296d03146ee7857165b95112372ad0d67e87f`.

### Repositorio: `logitrack-repos/deploy`
* **Estado:** Commit estructurado `96c3c05`, push a `origin/GonzaCarrillo/feat-000-verificar-githubbot-version-java` y apertura de [PR #4](https://github.com/Logitrack-Sa/deploy/pull/4).
* **Archivo afectado:**
  4. `docker/Dockerfile.chatbot`: `eclipse-temurin:25-jre@sha256:dbb43d81099eec91e0800226ed2296d03146ee7857165b95112372ad0d67e87f`.

---

## 3. Matriz de Validación Post-Cambio

| Control | Comando de Verificación | Resultado Esperado | Resultado Obtenido | Estado |
| :--- | :--- | :--- | :--- | :--- |
| **No `java-version: '21'`** | `grep -rn "java-version: '21'" /home/gonzalocar/orca/workspaces/Trabajo\ Final/` | 0 coincidencias | 0 coincidencias (exit code 1) | **CONFIRMADO** |
| **No `eclipse-temurin:21`** | `grep -rn "eclipse-temurin:21" /home/gonzalocar/orca/workspaces/Trabajo\ Final/` | 0 coincidencias | 0 coincidencias (exit code 1) | **CONFIRMADO** |
| **Java 25 en `pom.xml`** | `grep -n "<java.version>" chatbot-service/pom.xml` | `<java.version>25</java.version>` | `<java.version>25</java.version>` | **CONFIRMADO** |
| **Workflows `setup-java`** | Inspección de `.github/workflows/*.yml` en todos los repositorios | Solo Java 25 (`'25'`) | Todos los workflows usan `'25'` | **CONFIRMADO** |
| **PRs asociados** | `gh pr list` en `chatbot-service` y `deploy` | PRs de estandarización Java 25 trazables | chatbot-service #4 (MERGED), deploy #4 (OPEN) | **CONFIRMADO** |

---

## 4. `git diff` Consolidado

### `logitrack-repos/chatbot-service` (Commit `4629728` en PR #4)
```diff
diff --git a/.github/workflows/ci.yml b/.github/workflows/ci.yml
index 74c434e..b0f82ea 100644
--- a/.github/workflows/ci.yml
+++ b/.github/workflows/ci.yml
@@ -1,4 +1,4 @@
-name: CI chatbot-service (Java 21)
+name: CI chatbot-service (Java 25)
 on:
   pull_request:
   push:
@@ -20,7 +20,7 @@ jobs:
       - uses: actions/setup-java@b843ebf9b913146b38da2eaa7aeddf76074ae1ad
         with:
           distribution: temurin
-          java-version: '21'
+          java-version: '25'
           cache: maven
       - name: Compilar y verificar propagación JWT, Feign, Eureka y Prometheus con fixtures locales
         run: ./mvnw -B --no-transfer-progress verify
diff --git a/Dockerfile b/Dockerfile
index 4e63038..d4d6583 100644
--- a/Dockerfile
+++ b/Dockerfile
@@ -1,4 +1,4 @@
-FROM eclipse-temurin:21-jre@sha256:0b99b3376fbb50dbf9c0af8535fdd5b8eb0d3fdfc8b1e4967f383b6919f6901a
+FROM eclipse-temurin:25-jre@sha256:dbb43d81099eec91e0800226ed2296d03146ee7857165b95112372ad0d67e87f
 WORKDIR /app
 RUN groupadd --gid 10001 spring && useradd --uid 10001 --gid spring --no-create-home spring && mkdir /secrets
 COPY target/chatbot-service-*.jar /app/app.jar
diff --git a/pom.xml b/pom.xml
index f83e919..19b810d 100644
--- a/pom.xml
+++ b/pom.xml
@@ -4,7 +4,7 @@
   <parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>3.5.16</version><relativePath/></parent>
   <groupId>com.logitrack</groupId><artifactId>chatbot-service</artifactId><version>0.0.1-SNAPSHOT</version>
   <name>chatbot-service</name>
-  <properties><java.version>21</java.version><spring-cloud.version>2025.0.3</spring-cloud.version><spring-ai.version>1.1.8</spring-ai.version></properties>
+  <properties><java.version>25</java.version><spring-cloud.version>2025.0.3</spring-cloud.version><spring-ai.version>1.1.8</spring-ai.version></properties>
   <dependencyManagement><dependencies>
     <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-dependencies</artifactId><version>${spring-cloud.version}</version><type>pom</type><scope>import</scope></dependency>
     <dependency><groupId>org.springframework.ai</groupId><artifactId>spring-ai-bom</artifactId><version>${spring-ai.version}</version><type>pom</type><scope>import</scope></dependency>
```

### `logitrack-repos/deploy` (Commit `96c3c05` en PR #4)
```diff
diff --git a/docker/Dockerfile.chatbot b/docker/Dockerfile.chatbot
index a26a6db..0ac837b 100644
--- a/docker/Dockerfile.chatbot
+++ b/docker/Dockerfile.chatbot
@@ -1,4 +1,4 @@
-FROM eclipse-temurin:21-jre@sha256:0b99b3376fbb50dbf9c0af8535fdd5b8eb0d3fdfc8b1e4967f383b6919f6901a
+FROM eclipse-temurin:25-jre@sha256:dbb43d81099eec91e0800226ed2296d03146ee7857165b95112372ad0d67e87f
 WORKDIR /app
 RUN groupadd --gid 10001 spring && useradd --uid 10001 --gid spring --no-create-home spring && mkdir /secrets
 COPY chatbot-service/target/chatbot-service-*.jar /app/app.jar
```
