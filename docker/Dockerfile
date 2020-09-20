FROM maven:3.6.0-jdk-8-alpine

VOLUME /data
WORKDIR /scigraph

ADD . /scigraph

# Directory for configuration files
RUN mkdir -p      /.m2/repository && \
    mkdir -p      /scigraph/conf && \
    chgrp -R 0    /.m2 && \
    chmod -R 2575 /.m2 && \
    cd /scigraph && \
    mv docker/scripts/* . && \
    mvn install -DskipTests -DskipITs

ENV PATH="/scigraph/scripts/:$PATH"

ENTRYPOINT ["/bin/bash"]
