FROM mysql:5.7.13

RUN apt-get update && apt-get install -y netcat-traditional
RUN mkdir additional-scripts
COPY replication.cnf /etc/mysql/conf.d

COPY 0.activate-additional-scripts.sh /docker-entrypoint-initdb.d
COPY 1.initialize-database.sql /docker-entrypoint-initdb.d
COPY 2.initialize-database.sql /docker-entrypoint-initdb.d
COPY 3.initialize-database-json.sql /additional-scripts
COPY 4.initialize-database-db-id.sql /additional-scripts
COPY 9.initialization-completed.sh /docker-entrypoint-initdb.d
RUN touch /docker-entrypoint-initdb.d/3.initialize-database-json.sql
RUN touch /docker-entrypoint-initdb.d/4.initialize-database-db-id.sql

COPY mysql-healthcheck.sh /
RUN chmod +x /mysql-healthcheck.sh /docker-entrypoint-initdb.d/9.initialization-completed.sh && chown mysql -R /docker-entrypoint-initdb.d /additional-scripts
HEALTHCHECK --start-period=30s --interval=5s CMD /mysql-healthcheck.sh
