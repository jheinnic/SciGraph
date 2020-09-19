FROM maven:3.6.0-jdk-8-alpine

VOLUME /data
WORKDIR /scigraph

ADD . /scigraph
ADD docker/scripts /scigraph/
ADD docker/owl /scigraph/

# Directory for configuration files
RUN mkdir -p      /.m2/repository && \
    mkdir -p      /scigraph/conf && \
    chgrp -R 0    /.m2 && \
    chmod -R 2575 /.m2 && \
    cd /scigraph & mvn install -DskipTests -DskipITs

ENV PATH="/scigraph/scripts/:$PATH"

ENTRYPOINT ["/bin/bash"]
