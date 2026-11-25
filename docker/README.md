# Docker build — Maven resolution

Render and other cloud builders can hit **HTTP 429** on `repo.maven.apache.org` when resolving the Spring Boot parent POM.

The multi-stage `Dockerfile` copies `maven-settings.xml` into `/root/.m2/settings.xml` and uses the [Google-hosted Maven Central mirror](https://maven-central.storage-download.googleapis.com/maven2/).

Build order:

1. Copy `pom.xml` and settings.
2. `dependency:go-offline` (cached layer when only `src/` changes).
3. Copy `src/`, then `package`.

Local `mvnw` builds are unchanged and still use default Maven Central (or your user `~/.m2/settings.xml`).