# Place this file in /etc/nginx/sites-available/www.idatag.com
# and enable it from /etc/nginx/sites-enabled/.

server {
    listen 80;
    listen [::]:80;
    server_name www.idatag.com;

    location ^~ /.well-known/acme-challenge/ {
        root /var/www/www.idatag.com;
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
    server_name www.idatag.com;

    ssl_certificate /etc/nginx/ssl/www.idatag.com/fullchain.pem;
    ssl_certificate_key /etc/nginx/ssl/www.idatag.com/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    client_max_body_size 100m;

    # Open the SaaS portal when the domain is accessed without a path.
    location = / {
        return 301 /saas;
    }

    location = /saas {
        return 301 /saas/;
    }

    location ^~ /saas/ {
        alias /var/www/saas/dist/;
        index index.html;
        try_files $uri $uri/ /saas/index.html;
    }

    location = /insurance {
        return 301 /insurance/;
    }

    location ^~ /insurance/ {
        alias /var/www/insurance/dist/;
        index index.html;
        try_files $uri $uri/ /insurance/index.html;
    }

    # Monitor frontend is deployed beside SaaS and insurance under /var/www.
    # The explicit slash redirect keeps Vue Router's history base and relative navigation stable.
    location = /monitor {
        return 301 /monitor/;
    }

    location ^~ /monitor/ {
        alias /var/www/monitor/dist/;
        index index.html;
        try_files $uri $uri/ /monitor/index.html;
    }

    location ^~ /saasback/ {
        if ($request_method = OPTIONS) {
            add_header Access-Control-Allow-Origin "*" always;
            add_header Access-Control-Allow-Methods "GET, POST, PUT, DELETE, OPTIONS" always;
            add_header Access-Control-Allow-Headers "*" always;
            add_header Access-Control-Max-Age 3600 always;
            return 204;
        }

        add_header Access-Control-Allow-Origin "*" always;
        add_header Access-Control-Allow-Methods "GET, POST, PUT, DELETE, OPTIONS" always;
        add_header Access-Control-Allow-Headers "*" always;
        add_header Access-Control-Expose-Headers "new-token" always;

        proxy_pass http://127.0.0.1:8081/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
        proxy_set_header X-Forwarded-Prefix /saasback;
    }

    location ^~ /insuranceback/ {
        if ($request_method = OPTIONS) {
            add_header Access-Control-Allow-Origin "*" always;
            add_header Access-Control-Allow-Methods "GET, POST, PUT, DELETE, OPTIONS" always;
            add_header Access-Control-Allow-Headers "*" always;
            add_header Access-Control-Max-Age 3600 always;
            return 204;
        }

        add_header Access-Control-Allow-Origin "*" always;
        add_header Access-Control-Allow-Methods "GET, POST, PUT, DELETE, OPTIONS" always;
        add_header Access-Control-Allow-Headers "*" always;
        add_header Access-Control-Expose-Headers "new-token" always;

        proxy_pass http://127.0.0.1:8080/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
        proxy_set_header X-Forwarded-Prefix /insuranceback;
    }

    # Keep the browser-facing API prefix stable while mapping it to the monitor backend's /monitor routes.
    # A trailing slash on both sides is required so /api/monitor/auth/login becomes /monitor/auth/login.
    location ^~ /api/monitor/ {
        proxy_pass http://127.0.0.1:8083/monitor/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
        proxy_set_header X-Forwarded-Prefix /api/monitor;
        proxy_hide_header Access-Control-Allow-Origin;
        add_header Access-Control-Expose-Headers "new-token" always;
    }

    location / {
        return 404;
    }
}
