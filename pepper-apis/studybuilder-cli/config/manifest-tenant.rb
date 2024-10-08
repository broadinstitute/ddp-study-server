render "cmi-config.json.ctmpl"
render "prion-config.json.ctmpl"
render "rgp-config.json.ctmpl"
render "atcp-config.json.ctmpl"
render "brugada-config.json.ctmpl"

if $env == "dev"
  render "basil-config.json.ctmpl"
end
