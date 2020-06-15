render "application.conf.ctmpl"
render "DataDonationPlatform.yaml.ctmpl"
render "docker-compose.yaml.ctmpl"
render "housekeeping-docker-compose.yaml.ctmpl"
render "startup.sh.ctmpl"
render "housekeeping_startup.sh.ctmpl"
render "sqlproxy.env.ctmpl"
copy_secret "sqlproxy-service-account.json"
copy_secret "itextkey",nil,"itextkey.xml"
copy_secret "housekeeping-service-account.json"
copy_secret_from_path "secret/pepper/#{$env}/pepper-cert.key"
copy_secret_from_path "secret/pepper/#{$env}/pepper-cert.crt"
render "ddp.conf.ctmpl"
copy_file "post_deploy_smoketest.sh"
copy_file "waf.conf"
render "tcell_agent.config.ctmpl"
