When running study server locally, you'll need to supply various command-line flags.
Use the following flags for running DataDonationPlatform locally or running tests:

```
-Dconfig.file=output-build-config/local.conf
-Ditext.license=output-config/itextkey.xml
```

Use a different `config.file` file if running against a shared db or deployed environment:
```
-Dconfig.file=output-config/application.conf
```
