
# Background

The [language server protocol](https://github.com/Microsoft/language-server-protocol) has several implementations for various technologies. Their communication protocols are socket to socket, stdin/stdout, etc.

For cloud Web based IDE these implementations should be accessible using HTTP protocol. In our case using websocket.
This server is a wrapper that among other responsibilities transforms the low level communication protocols to websocket.

# Installation

The server is designed to run in a container dedicated for a single user workspace. Specific language servers implementations are configured via environemnt variables.

A buildpack for running the server on CloudFoundry can be found in https://github.com/SAP/cf-language-server-buildpack.


# Responsibilities
- Converting socket/stdin/stdout/etc. into websocket
- Start and stop LSP specific implementations per project/language
- Sync workspace changes and call LSP protocol relevant notifications accordingly
- Manage security tokens life cycle 

# Development Environment

## Debug in CF

- Find your IP
- Update environemnt variables in cf: `JAVA-OPTS` (correct the IP) and `JBP_CONFIG_DEBUG` according to https://github.com/SAP/cloud-language-servers-container/blob/master/manifest.yaml#L12-L13. If push is done from another server populate these environment variables using the debugger
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
  
## Run server locally
  mvn jetty:run -Pintegration-test

# CI
This project is using [Travis CI](TODO).
