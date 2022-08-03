# IDE Run Configurations
## DSM
### IntelliJ IDEA
> Target: IntelliJ IDEA 2021.3.2

#### Run Configuration
Add to a file named `$PROJECT_DIR$/.run/DSM.run.xml`. On reload of the project, a new
option labeled `DSM` will appear in the Run tool window.
```xml
<component name="ProjectRunConfigurationManager">
  <configuration default="false" name="DSM" type="Application" factoryName="Application">
    <option name="ALTERNATIVE_JRE_PATH" value="11" />
    <option name="ALTERNATIVE_JRE_PATH_ENABLED" value="true" />
    <option name="MAIN_CLASS_NAME" value="org.broadinstitute.dsm.DSMServer" />
    <module name="dsm-server" />
    <option name="PROGRAM_PARAMETERS" value="-ea -Dconfig.file=$ProjectFileDir$/dsm-server/src/test/resources/config/default.conf" />
    <method v="2">
      <option name="Make" enabled="true" />
    </method>
  </configuration>
</component>
```

### VSCode
> Target: Extension Pack for Java, v0.25.0

#### Run Configuration
Add to the `.vscode/launch.json#/configurations` array
```json
{
    "type": "java",
    "name": "Launch DSM",
    "request": "launch",
    "mainClass": "org.broadinstitute.dsm.DSMServer",
    "projectName": "dsm-server",
    "env": {},
    "vmArgs": [
        "-Dconfig.file=${workspaceFolder}/dsm-server/src/test/resources/config/default.conf",
        "-ea"
    ],
    "args": ""
}
```