# SkyPilot API (Rust / Axum). Build from the repository root:
#   docker build -f docker/api.Dockerfile -t skypilot-api .
FROM rust:1.98-slim-trixie AS build
WORKDIR /src
COPY Cargo.toml Cargo.lock ./
COPY .cargo .cargo
COPY packages packages
COPY services services
COPY apps/api apps/api
RUN --mount=type=cache,target=/usr/local/cargo/registry \
    --mount=type=cache,target=/src/target \
    cargo build --release --locked -p skypilot-api \
 && cp target/release/skypilot-api /usr/local/bin/skypilot-api

FROM debian:trixie-slim
RUN apt-get update \
 && apt-get install -y --no-install-recommends ca-certificates \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 10001 --home /app skypilot
WORKDIR /app
COPY --from=build /usr/local/bin/skypilot-api /usr/local/bin/skypilot-api
COPY data/registry data/registry
COPY data/translation data/translation
COPY data/farms data/farms
COPY migrations migrations
# data/cache holds synced Hypixel resources + NEU-REPO and the generated VAPID key (volume).
RUN mkdir -p data/cache && chown -R skypilot:skypilot data/cache
USER skypilot
ENV SKYPILOT_ROOT=/app \
    API_BIND=0.0.0.0:8080 \
    RUST_LOG=info,tower_http=warn,sqlx=warn
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=120s --retries=5 CMD ["skypilot-api", "healthcheck"]
CMD ["skypilot-api"]
