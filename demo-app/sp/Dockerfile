FROM registry.access.redhat.com/ubi9/httpd-24

USER 0

# Install Shibboleth
COPY shibboleth.repo /etc/yum.repos.d/shibboleth.repo
RUN dnf -y update httpd openssl && \
    dnf -y install shibboleth && \
    dnf clean all

# Copy Configs
COPY virtual-host-app.conf /etc/httpd/conf.d/virtual-host-app.conf
COPY shibboleth2.xml /etc/shibboleth/shibboleth2.xml
COPY attribute-map.xml /etc/shibboleth/attribute-map.xml
COPY pem/ /etc/shibboleth/
COPY run-httpd /usr/bin/run-httpd
# index.html removed as it is now in frontend service

# Generate self-signed cert for mod_ssl
RUN mkdir -p /etc/httpd/tls && \
    openssl req -new -newkey rsa:2048 -days 365 -nodes -x509 \
    -keyout /etc/httpd/tls/localhost.key \
    -out /etc/httpd/tls/localhost.crt \
    -subj "/CN=localhost" && \
    chown -R 1001:0 /etc/httpd/tls && \
    chmod 644 /etc/httpd/tls/localhost.crt && \
    chmod 640 /etc/httpd/tls/localhost.key

# Permissions
RUN chmod 640 /etc/shibboleth/*.pem
RUN chown -R 1001:0 /etc/shibboleth/
RUN mkdir -p /var/run/shibboleth
RUN chown -R 1001:0 /var/run/shibboleth
RUN chmod 775 /var/run/shibboleth
RUN chown -R 1001:0 /var/log/shibboleth/
RUN chmod 775 /var/log/shibboleth/
RUN chown 1001:0 /usr/bin/run-httpd
RUN chmod +x /usr/bin/run-httpd
RUN chown -R 1001:0 /var/www/html

USER 1001

CMD ["/usr/bin/run-httpd"]
