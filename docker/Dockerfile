FROM maven:3.6.0-jdk-8-alpine

VOLUME /data
WORKDIR /scigraph

ADD . /scigraph/src

# Directory for configuration files
RUN mkdir -p      /.m2/repository && \
    mkdir -p      /scigraph/conf && \
    chgrp -R 0    /.m2 && \
    chmod -R 2575 /.m2 && \
    cd /scigraph/src && \
    mv docker/scripts .. && \
    mvn install -DskipTests -DskipITs && \
    mv ./SciGraph-loader/target/scigraph-loader-*.jar /scigraph/loader.jar && \
    unzip ./SciGraph-services/target/scigraph-services-*.zip && \
    mv ./scigraph-services-*/lib /scigraph/lib && \
    rm ./scigraph-services-*/scigraph-services-*sources.jar && \
    mv ./scigraph-services-*/scigraph-services-*.jar /scigraph/services.jar && \
    rm -rf /scigraph/src

ENV PATH="/scigraph/scripts/:$PATH"

ENTRYPOINT ["/bin/bash"]
