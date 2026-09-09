FROM solr:7.3.1

ADD --chmod=444 https://repo1.maven.org/maven2/org/locationtech/jts/jts-core/1.19.0/jts-core-1.19.0.jar /opt/solr/server/solr-webapp/webapp/WEB-INF/lib/

USER root

ADD schema.xml ./
ADD solrconfig.xml ./
RUN cp -r server/solr/configsets/_default server/solr/collection1 \
    && mv schema.xml server/solr/collection1/conf/ \
    && mv solrconfig.xml server/solr/collection1/conf/ \
    && echo "Configured collection1"

ADD ror/schema.xml ./
ADD ror/solrconfig.xml ./
RUN cp -r server/solr/configsets/_default server/solr/rorSuggestions \
    && mv schema.xml server/solr/rorSuggestions/conf/ \
    && mv solrconfig.xml server/solr/rorSuggestions/conf/ \
    && echo "Configured rorSuggestions"

ADD geonames/schema.xml ./
ADD geonames/solrconfig.xml ./    
RUN cp -r server/solr/configsets/_default server/solr/geonames \
    && mv schema.xml server/solr/geonames/conf/ \
    && mv solrconfig.xml server/solr/geonames/conf/ \
    && echo "Configured geonames"  
    
ADD periodo/schema.xml ./
ADD periodo/solrconfig.xml ./    
RUN cp -r server/solr/configsets/_default server/solr/periodo \
    && mv schema.xml server/solr/periodo/conf/ \
    && mv solrconfig.xml server/solr/periodo/conf/ \
    && echo "Configured periodo"  
   
RUN chmod 777 /opt/solr/server/solr/collection1 -R
RUN chmod 777 /opt/solr/server/solr/rorSuggestions -R
RUN chmod 777 /opt/solr/server/solr/geonames -R
RUN chmod 777 /opt/solr/server/solr/periodo -R

USER solr

RUN ./bin/solr start \
    && ./bin/solr create_core -c collection1 -d server/solr/collection1/conf \
    && ./bin/solr create_core -c rorSuggestions -d server/solr/rorSuggestions/conf \
    && ./bin/solr create_core -c geonames -d server/solr/geonames/conf \
    && ./bin/solr create_core -c periodo -d server/solr/periodo/conf \
    && echo "Done creating collections"

