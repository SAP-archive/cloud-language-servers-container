# LSPServerCF
Responsible for:
- Converting socket/stdin/stdout/etc. into websocket
- Start and stop LSP specific implementations per project/language
- Sync workspace changes from DI + call LSP protocol relevant calls accordingly
- Manage security tokens life cycle 

## Debug in CF

- http://lmgtfy.com/?q=what+is+my+ip
- Update environemnt variables in cf: `JAVA-OPTS` (correct the IP) and `JBP_CONFIG_DEBUG` according to https://github.wdf.sap.corp/DevX/LSPServerCF/blob/master/manifest.yaml#L12-L13. If running from DI populate these environment variables using the debugger (in `LanguageServerInstaller`)
- After push to CF run from terminal: `cf ssh -N -T -L 8000:localhost:8000 <app-name>`
- Use your favorite IDE to remote debug localhost with port 8000


## Local Integration Test in Windows
* Add the following variable to user env variable

  basedir: C:/<project.dir>/src/test/
  
  exec: util/EchoLauncher.bat
  
  workdir: util

* install ruby using [RubyInstaller](https://rubyinstaller.org/downloads/)

* Build
  mvn install
  
## Run LSPServerCF locally
  mvn jetty:run

# CI
This project is using [SAP Travis CI](https://travis-ci.mo.sap.corp/DevX/LSPServerCF).

# Release Build
* Create a pull request from DevX/LSPServer to NAAS4Cloud/LSPServerCF
* Run [Release Job](https://xmake-dev.wdf.sap.corp:8443/job/NAAS4Cloud-LSPServerCF-OD-linuxx86_64_indirectshipment/) 
 *Choose option **Build and Deploy***
