# The Care Tasks frontend on nginx (runs as a non-root user, listens on 8080). Nothing to build: the app is
# plain HTML, CSS and JavaScript with Alpine.js vendored under public/vendor.
FROM docker.io/nginxinc/nginx-unprivileged:1.30-alpine

ENV GATEWAY_URL=http://api-gateway:8080 \
    NGINX_ENTRYPOINT_LOCAL_RESOLVERS=1

COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY public/ /usr/share/nginx/html/
