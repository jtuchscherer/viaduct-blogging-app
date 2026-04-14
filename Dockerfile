# Viaduct Blogging App — Runtime Image
#
# The Viaduct framework uses Airbnb-internal Maven artifacts that are not
# published to Maven Central, so the distribution must be built on the host
# before building the image:
#
#   ./gradlew installDist -x test
#   docker compose up --build
#
# A fully self-contained multi-stage build would be possible if Viaduct
# artifacts are published to an accessible registry (Phase 16 / CI setup).

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY build/install/viaduct-blogging-app/ .

RUN mkdir -p /app/data

# Default values — override via docker-compose or -e flags
ENV APP_ENV=PROD
ENV DATABASE_URL=jdbc:sqlite:/app/data/blog.db
ENV JWT_SECRET=change-me-in-production
ENV CORS_ORIGIN=localhost:5173

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=30s \
  CMD wget --quiet --tries=1 --spider http://localhost:8080/health || exit 1

ENTRYPOINT ["bin/viaduct-blogging-app"]
