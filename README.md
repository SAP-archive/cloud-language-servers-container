![](https://img.shields.io/badge/STATUS-NOT%20CURRENTLY%20MAINTAINED-red.svg?longCache=true&style=flat)

# Important Notice
This public repository is read-only and no longer maintained.

# Description

The [language server protocol](https://github.com/Microsoft/language-server-protocol) has several implementations for various technologies. Their communication protocols are low level like socket to socket and stdin/stdout. In addition they are targeted at running on the developer machine and not on cloud environment.  cloud-language-servers-container is a wrapper server that can run inside an isolated container. It exposes language servers functionality through web socket and REST APIs. The solution covers exposing the language servers, synchronization of source code, security aspects, isoation, etc.

## Responsibilities
- Converting socket/stdin/stdout/etc. into websocket
- Start and stop LSP specific implementations per project/language
- Sync workspace changes and call LSP protocol relevant notifications accordingly
- Manage security tokens life cycle 

## Requirements
cloud-language-servers-container should run in a JEE container such as [Tomcat](https://tomcat.apache.org/) or [Jetty](http://www.eclipse.org/jetty/).
cloud-language-servers-container integration tests require ruby installation (see below). 
For integration with [CloudFoundry](http://www.cloudfoundry.org) there should be an available end point, org and space in CloudFoundry to deploy there the server as a Java application.

# Download and Installation

The server is designed to run in a container dedicated for a single user workspace. Specific language servers should be installed and configured via environemnt variables. For CloudFoundry there is a dedicated [buildpack](https://github.com/SAP/cf-language-server-buildpack) responsible for this setup with support currently for java LSP.

In order to build and run cloud-language-servers-container in CloudFoundry follow these steps from command line:

* Download [Maven](https://maven.apache.org/download.cgi)
* Install [Cloud Foundry Command Line Interface](http://docs.cloudfoundry.org/cf-cli/)
* Run `mvn clean install`
* Run `cf login` to login to cloud foundry endpoint, org and space
* Run `cf push`
* An application named `lsp` should be created with a running cloud-language-servers-container 

# Development Environment

## Debug the application in CloudFoundry

- Find your IP
- Update environemnt variables in cf: `JAVA-OPTS` (correct the IP) and `JBP_CONFIG_DEBUG` according to https://github.com/SAP/cloud-language-servers-container/blob/master/manifest.yaml#L12-L13. If push is done from another server populate these environment variables using the debugger
- After push to CF run from terminal: `cf ssh -N -T -L 8000:localhost:8000 <app-name>`
- Use your favorite IDE to remote debug localhost with port 8000

## Local Integration Test in Windows
Mocha integration tests can be run from intellij run configuration:

* Run or debug the run configuration `LSPServerCF [jetty:run]`
* Go to one of the suites under `src/test/javascript/test` and run/debug using mocha

## Snapshots deployment job
https://gkemonaco.jaas-gcp.cloud.sap.corp/job/LSPServerCF_Deploy_Snapshot/
  
## Run server locally
  `mvn jetty:run -Pintegration-test`

# Limitations

cloud-language-servers-container is integrated fully in CloudFoundry using [the language server buildpack](https://github.com/SAP/cf-language-server-buildpack). However there is a small gap for full integration on local machine or Docker.

# Known Issues

* Memory footprint for JEE server is substantial when each user workspace gets his own server instance. 
* rsync or websocket interfaces might have better performance than the current HTTP REST (for a setting that require sync)

# How to obtain support
For bugs, questions and ideas for enhancement please open an issue in github.

# To-Do (upcoming changes)

* Setup official releases
* Make integration test LSPTest2.js to run with node instead of ruby
* Easy automated way to install cloud-language-servers-container together with language servers on local machine
* Remove CloudFoundry specific code

# License
Copyright (c) 2017-2021 SAP SE or an SAP affiliate company and cloud-language-servers-container contributors. Please see our [LICENSE](LICENSE) for copyright and license information. Detailed information including third-party components and their licensing/copyright information is available [via the REUSE tool](https://api.reuse.software/info/github.com/SAP/cloud-language-servers-container).
