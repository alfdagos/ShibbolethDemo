FROM registry.access.redhat.com/ubi9/nginx-120

# Copy static content
COPY index.html /opt/app-root/src/index.html

# Nginx 1.20 on UBI9 runs as non-root by default (1001)
# and serves from /opt/app-root/src

EXPOSE 8080

CMD ["nginx", "-g", "daemon off;"]
