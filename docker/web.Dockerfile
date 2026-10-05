# SkyPilot web (Next.js standalone). Build from the repository root:
#   docker build -f docker/web.Dockerfile -t skypilot-web .
FROM node:24-trixie-slim AS build
RUN npm install -g pnpm@12.4.2
WORKDIR /src
COPY package.json pnpm-workspace.yaml pnpm-lock.yaml ./
COPY apps/web/package.json apps/web/package.json
COPY packages/ui/package.json packages/ui/package.json
COPY packages/protocol/ts/package.json packages/protocol/ts/package.json
RUN pnpm install --frozen-lockfile
COPY apps/web apps/web
COPY packages/ui packages/ui
COPY packages/protocol/ts packages/protocol/ts
# Where the Next.js rewrite sends /api when the web container is used without the proxy.
ARG SKYPILOT_API_URL=http://api:8080
ENV SKYPILOT_API_URL=${SKYPILOT_API_URL} \
    NEXT_TELEMETRY_DISABLED=1
RUN pnpm --filter @skypilot/web build

FROM node:24-trixie-slim
WORKDIR /app
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    PORT=3000 \
    HOSTNAME=0.0.0.0
COPY --from=build --chown=node:node /src/apps/web/.next/standalone ./
COPY --from=build --chown=node:node /src/apps/web/.next/static ./apps/web/.next/static
COPY --from=build --chown=node:node /src/apps/web/public ./apps/web/public
USER node
EXPOSE 3000
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=5 CMD ["node", "-e", "fetch('http://127.0.0.1:3000/').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"]
CMD ["node", "apps/web/server.js"]
