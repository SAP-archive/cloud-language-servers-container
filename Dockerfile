FROM tomcat:8-jre8-alpine
ENV INSECURED="true" \
    lspservers="eslint,json,xml,java,cdx" \
    basedir="/lsp/" \
    LSPJAVA_workdir="java" \
    LSPJAVA_exec="java/launcher.sh" \
    LSPJAVA_protocol="stream" \
    LSPCDX_workdir="cdx" \
    LSPCDX_exec="cdx/launcher.sh" \
    LSPCDX_protocol="stream"
COPY /target/LSPServerCF.war /usr/local/tomcat/webapps/LSPServerCF.war

# Download Nodejs, Downdload and extract java + cdx binaries
RUN mkdir /lsp && \
    mkdir /lsp/java && \
    apk --update add ca-certificates wget nodejs nodejs-npm && update-ca-certificates && \
    wget -qO- https://lsp-component-java-0-18-0.cfapps.eu10.hana.ondemand.com/jdt_ls-0.18.0.tar.gz | tar xvz -C /lsp/java --strip-components=1 && \
    mkdir /lsp/cdx && \
    wget https://lsp-component-cds-1-4-8.cfapps.eu10.hana.ondemand.com/lsp.zip -O temp.zip && \
    unzip temp.zip -d /lsp/cdx && \
    rm temp.zip

# Copy launchers
COPY /src/main/resources/java/launcher.sh /lsp/java/launcher.sh
COPY /src/main/resources/cdx/launcher.sh /lsp/cdx/launcher.sh

# Copy projects
COPY /src/main/resources/projects /projects/