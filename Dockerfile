FROM tomcat:8.5-jre8

# Install Ruby.
RUN \
  apt-get update && \
  apt-get install -y ruby ruby-dev ruby-bundler && \
  rm -rf /var/lib/apt/lists/*

# Define working directory.
WORKDIR /data

RUN mkdir /usr/local/app
COPY target/LSPServerCF-0.0.1-SNAPSHOT.war /usr/local/tomcat/webapps/
COPY src/test/util/* /usr/local/app/echo/

ENV basedir /usr/local/app/
ENV workdir echo/
ENV exec echo/EchoLauncher.sh
ENV IPC "{\"socket\": { \"in\": 8991, \"out\": 8990 }}"  