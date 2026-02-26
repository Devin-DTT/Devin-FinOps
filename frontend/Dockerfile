# =============================================================================
# Devin FinOps Dashboard - Frontend Dockerfile
# Multi-stage build: Node.js build + Nginx runtime
# =============================================================================

# --- Stage 1: Build ---
FROM node:18-alpine AS build

WORKDIR /app

# Copy package files first for dependency caching
COPY package.json package-lock.json* ./
RUN npm ci --legacy-peer-deps

# Copy source code
COPY . .

# Build the Angular application for production
RUN npx ng build --configuration production

# --- Stage 2: Runtime ---
FROM nginx:1.25-alpine

# Remove default nginx config
RUN rm /etc/nginx/conf.d/default.conf

# Copy custom nginx config
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy built Angular app from build stage
COPY --from=build /app/dist/devin-dashboard/browser /usr/share/nginx/html

# Expose port 80
EXPOSE 80

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
  CMD wget -q --spider http://localhost:80/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
