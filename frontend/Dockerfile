# ============================================================
# Stage 1: Build React Production Bundle
# ============================================================
FROM node:22-alpine AS builder

WORKDIR /app

# Install dependencies with frozen lockfile / clean install
COPY package.json package-lock.json* ./
RUN npm ci

# Copy source code and configuration
COPY . .

# Build static assets (TypeScript compilation + Vite bundler)
RUN npm run build

# ============================================================
# Stage 2: Production Nginx Server
# ============================================================
FROM nginx:1.27-alpine AS runner

# Remove default Nginx website
RUN rm -rf /usr/share/nginx/html/*

# Copy built frontend assets from builder stage
COPY --from=builder /app/dist /usr/share/nginx/html

# Copy custom Nginx configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Expose HTTP port
EXPOSE 80

# Health check probe
HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost/health || exit 1

# Start Nginx in foreground
CMD ["nginx", "-g", "daemon off;"]
