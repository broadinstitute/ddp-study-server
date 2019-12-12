render "config.conf.ctmpl"
render "sqlproxy.env.ctmpl"
render "docker-compose.yaml.ctmpl"
copy_secret "sqlproxy-service-account.json"