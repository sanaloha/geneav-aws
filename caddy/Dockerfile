# Caddy built with the per-IP rate-limiting plugin baked in.
# Used by the `caddy` service in docker-compose.prod.yml.
FROM caddy:2-builder AS build
RUN xcaddy build --with github.com/mholt/caddy-ratelimit

FROM caddy:2
COPY --from=build /usr/bin/caddy /usr/bin/caddy
