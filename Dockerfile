FROM clojure

RUN apt-get update

# Install the cron service
RUN apt-get install cron -y

RUN pwd

WORKDIR /app

COPY . /app

RUN ls -ltr

#Build jar file
RUN lein uberjar

#Copy jar to root of directory
RUN ls -ltr

RUN cp target/uberjar/pav-congress-api-bootstrapper.jar pav-congress-api-bootstrapper.jar

# Make cron log file
RUN mkdir -p /var/log && touch /var/log/cron.log

RUN whoami
RUN chown -R root /app
RUN chmod -R 700 /app/scripts/run.sh

#Use the crontab file
RUN crontab /app/scripts/crontab

#Run Job
CMD ["/bin/bash", "scripts/startup.sh", "tail -0f /var/log/cron.log"]
