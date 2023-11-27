render "cmi-config.json.ctmpl"
render "testboston-config.json.ctmpl"
render "prion-config.json.ctmpl"
render "rgp-config.json.ctmpl"
render "rarex-config.json.ctmpl"
render "circadia-config.json.ctmpl"
render "atcp-config.json.ctmpl"
render "brugada-config.json.ctmpl"
render "singular-config.json.ctmpl"
render "fon-config.json.ctmpl"

if $env == "dev"
  render "basil-config.json.ctmpl"
end
