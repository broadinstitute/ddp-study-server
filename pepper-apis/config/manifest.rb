render "docker-compose.yaml.ctmpl"
render "housekeeping-docker-compose.yaml.ctmpl"
render "startup.sh.ctmpl"
render "housekeeping_startup.sh.ctmpl"
render "sqlproxy.env.ctmpl"
render "build_test_angio.sh.ctmpl"
render "backfill_olcs.sh.ctmpl"
copy_secret "sqlproxy-service-account.json"
copy_secret "itextkey",nil,"itextkey.xml"
copy_secret "housekeeping-service-account.json"
copy_secret_from_path "secret/pepper/#{$env}/pepper-cert.key"
copy_secret_from_path "secret/pepper/#{$env}/pepper-cert.crt"
render "ddp.conf.ctmpl"
render "application.conf.ctmpl"
copy_file "post_deploy_smoketest.sh"
copy_file "waf.conf"
fc_keys = './fc_keys'
Dir.mkdir(fc_keys)
# at some point managing fc keys this way will become a headache...
# service account keys below here are the ones that will be used by the backend and should have file names
# that are reflected in the database (as just file names, not paths)
copy_secret_from_path "secret/pepper/#{$env}/#{$version}/test-firecloud-service-account.json",nil,fc_keys + "/ddp-testing.json"
