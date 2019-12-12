render "testing-inmemorydb.conf.ctmpl"
copy_secret "itextkey",nil,"itextkey.xml"
render "local-java-props.txt.ctmpl"
copy_file "post_deploy_smoketest.sh"
copy_secret "housekeeping-service-account.json"
copy_file "waf.conf"

# service account keys below here are the ones that will be used by the backend and should have file names
# that are reflected in the database (as just file names, not paths).  We put one such key here for testing.
fc_keys = './fc_keys'
Dir.mkdir(fc_keys)
copy_secret_from_path "secret/pepper/#{$env}/#{$version}/test-firecloud-service-account.json",nil,fc_keys + "/ddp-testing.json"
