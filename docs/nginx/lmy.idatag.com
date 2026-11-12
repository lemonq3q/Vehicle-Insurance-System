# Deploy as /etc/nginx/sites-available/lmy.idatag.com and enable it in sites-enabled.
# DNS must point lmy.idatag.com at this gateway; install a certificate covering this name.
# Backend ports and URI mappings follow www.idatag.com; the target host is 47.100.210.159.
# This gateway serves test frontend files from /var/www/idatag_dev, independently of production.
# The target runs only the Java backends; it does not need Nginx or an HTTPS listener.
# Use the upstream IP:port as Host for this test; retain the public host in X-Forwarded-Host.

server {
    listen 80;
    listen [::]:80;
    server_name lmy.idatag.com;

    # Local HTTP-01 validation permits issuing/renewing the gateway's own certificate.
    location ^~ /.well-known/acme-challenge/ {
        root /var/www/lmy.idatag.com;
        default_type text/plain;
        try_files $uri =404;
    }

    location / {
        return 301 https://$host$request_uri;
    }
}

server {
    listen 443 ssl;
    listen [::]:443 ssl;
    server_name lmy.idatag.com;

    ssl_certificate /etc/nginx/ssl/lmy.idatag.com/fullchain.pem;
    ssl_certificate_key /etc/nginx/ssl/lmy.idatag.com/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    client_max_body_size 100m;

    proxy_http_version 1.1;

    # Keep the production URL layout while serving a separate test frontend build here.
    location = / {
        return 301 /saas;
    }

    location = /saas {
        return 301 /saas/;
    }

    location ^~ /saas/ {
        alias /var/www/idatag_dev/saas/dist/;
        index index.html;
        try_files $uri $uri/ /saas/index.html;
    }

    location = /insurance {
        return 301 /insurance/;
    }

    location ^~ /insurance/ {
        alias /var/www/idatag_dev/insurance/dist/;
        index index.html;
        try_files $uri $uri/ /insurance/index.html;
    }

    location = /monitor {
        return 301 /monitor/;
    }

    location ^~ /monitor/ {
        alias /var/www/idatag_dev/monitor/dist/;
        index index.html;
        try_files $uri $uri/ /monitor/index.html;
    }

    # Strip /saasback/ before sending to the SaaS application, as in production.
    # All methods, including OPTIONS, reach the target application's existing CORS handling.
    location ^~ /saasback/ {
        proxy_pass http://47.100.210.159:8081/;
        proxy_set_header Host 47.100.210.159:8081;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-Host $host;
        proxy_set_header X-Forwarded-Prefix /saasback;
        add_header Access-Control-Expose-Headers "new-token" always;
    }

    # Strip /insuranceback/ before sending to the vehicle-insurance application.
    location ^~ /insuranceback/ {
        proxy_pass http://47.100.210.159:8080/;
        proxy_set_header Host 47.100.210.159:8080;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-Host $host;
        proxy_set_header X-Forwarded-Prefix /insuranceback;
        add_header Access-Control-Expose-Headers "new-token" always;
    }

    # Preserve the production mapping: /api/monitor/auth/login -> /monitor/auth/login.
    location ^~ /api/monitor/ {
        proxy_pass http://47.100.210.159:8083/monitor/;
        proxy_set_header Host 47.100.210.159:8083;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-Host $host;
        proxy_set_header X-Forwarded-Prefix /api/monitor;
        proxy_hide_header Access-Control-Allow-Origin;
        add_header Access-Control-Expose-Headers "new-token" always;
    }

    location / {
        return 404;
    }
}

