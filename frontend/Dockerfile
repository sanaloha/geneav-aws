# --- build stage ---
FROM node:22-alpine AS build
WORKDIR /app
# Copy the lockfile too and use `npm ci`, so the image installs the exact
# resolved versions rather than re-resolving semver ranges on every build.
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# NEXT_PUBLIC_* vars are inlined into the client bundle at build time.
# Passed from docker-compose.prod.yml; falls back to localhost when unset (local dev).
ARG NEXT_PUBLIC_API_BASE_URL
ENV NEXT_PUBLIC_API_BASE_URL=$NEXT_PUBLIC_API_BASE_URL
# Umami website id. Unset is the normal state until the site has been created in
# the Umami UI; the layout then renders no analytics tag at all.
ARG NEXT_PUBLIC_UMAMI_WEBSITE_ID
ENV NEXT_PUBLIC_UMAMI_WEBSITE_ID=$NEXT_PUBLIC_UMAMI_WEBSITE_ID
# "Sign in with Microsoft". Must match the backend's GENEAV_MICROSOFT_LOGIN_ENABLED:
# anything other than "true" renders the button disabled, because a click would
# otherwise land on an OAuth2 route the backend has not registered.
ARG NEXT_PUBLIC_MICROSOFT_LOGIN_ENABLED
ENV NEXT_PUBLIC_MICROSOFT_LOGIN_ENABLED=$NEXT_PUBLIC_MICROSOFT_LOGIN_ENABLED
RUN npm run build

# --- run stage ---
FROM node:22-alpine
WORKDIR /app
ENV NODE_ENV=production
COPY --from=build /app/package.json ./package.json
COPY --from=build /app/node_modules ./node_modules
COPY --from=build /app/.next ./.next
COPY --from=build /app/public ./public
COPY --from=build /app/next.config.mjs ./next.config.mjs
EXPOSE 3000
CMD ["npm", "run", "start"]
