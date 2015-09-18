FROM clojure

RUN echo "Building Image"

WORKDIR /app

COPY . /app

RUN lein uberjar

RUN cp target/uberjar/pav-congress-api-bootstrapper.jar pav-congress-api-bootstrapper.jar

# Install the cron service
RUN apt-get update
RUN apt-get install cron -y

RUN mkdir -p /var/log && touch /var/log/cron.log

#Use the crontab file
RUN crontab scripts/crontab

RUN ls -ltr

#Run Job
CMD java -jar pav-congress-api-bootstrapper.jar
#CMD ["/bin/bash", "scripts/startup.sh", "tail -0f /var/log/cron.log"]