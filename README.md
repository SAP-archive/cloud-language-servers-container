# Responsibilities
- Converting socket/stdin/stdout/etc. into websocket
- Start and stop LSP specific implementations per project/language
- Sync workspace changes and call LSP protocol relevant notifications accordingly
- Manage security tokens life cycle 

## Debug in CF

- http://lmgtfy.com/?q=what+is+my+ip
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
