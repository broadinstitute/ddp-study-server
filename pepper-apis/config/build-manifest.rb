render "local.conf.ctmpl"
copy_secret "housekeeping-service-account.json"
copy_file "waf.conf"
