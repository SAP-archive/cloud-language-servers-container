# LSPServerCF
The LSPServerCF is socket wrapper for LSP Server

## Local Dev in Window
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
