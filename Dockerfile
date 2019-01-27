FROM tomcat:8-jre8-alpine

ENV WORKSPACE_ROOT /projects
ENV LS_ROOT /ls

RUN apk --no-cache add nodejs nodejs-npm

RUN mkdir $WORKSPACE_ROOT $LS_ROOT

# Copy launcher.sh files
COPY ls $LS_ROOT

# TODO make bin url as build-arg

# Prepare java ls
RUN wget -qO- http://nexus.wdf.sap.corp:8081/nexus/content/groups/build.releases/com/sap/devx/jdt/jdt_ls/0.30.0/jdt_ls-0.30.0.tar.gz | \
    tar xvz -C $LS_ROOT/java --strip-components=1
ENV LSPJAVA_protocol stream

# Prepare eslint ls
RUN tmp_file=eslint.zip && \
    wget http://nexus.wdf.sap.corp:8081/nexus/content/groups/build.releases/com/sap/devx/lsp/eslint/eslint/0.0.2/eslint-0.0.2.zip -O $tmp_file && \
    unzip -d $LS_ROOT/eslint $tmp_file && \
    rm $tmp_file
ENV LSPESLINT_protocol stream

# Prepare json ls
RUN tmp_file=json.zip && \
    wget http://nexus.wdf.sap.corp:8081/nexus/content/groups/build.releases/com/sap/devx/lsp/json/json/0.1.4/json-0.1.4.zip -O $tmp_file && \
    unzip -d $LS_ROOT/json $tmp_file && \
    rm $tmp_file
ENV LSPJSON_protocol stream

# Prepare xml ls
RUN tmp_file=xml.zip && \
    wget http://nexus.wdf.sap.corp:8081/nexus/content/groups/build.releases/com/sap/devx/lsp/xml/xml/0.0.5/xml-0.0.5.zip -O $tmp_file && \
    unzip -d $LS_ROOT/xml $tmp_file && \
    rm $tmp_file
ENV LSPXML_protocol stream

COPY docker-entrypoint.sh .

RUN rm -rf /usr/local/tomcat/webapps/*
COPY target/LSPServerCF.war /usr/local/tomcat/webapps/ROOT.war

ENTRYPOINT ["/bin/sh", "docker-entrypoint.sh"]
