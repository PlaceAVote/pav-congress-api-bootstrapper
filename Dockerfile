FROM ubuntu:14.04

MAINTAINER john@placeavote.com

RUN apt-get update

RUN apt-get -y install software-properties-common

# Install Java.
RUN \
  echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | debconf-set-selections && \
  add-apt-repository -y ppa:webupd8team/java && \
  apt-get update && \
  apt-get install -y oracle-java8-installer && \
  rm -rf /var/lib/apt/lists/* && \
  rm -rf /var/cache/oracle-jdk8-installer

# Define commonly used JAVA_HOME variable
ENV JAVA_HOME /usr/lib/jvm/java-8-oracle

# Install Leiningen 2.5.0 and make executable
RUN apt-get install wget

# Leiningen
ENV LEIN_ROOT true

RUN wget -q -O /usr/bin/lein \
    https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein \
    && chmod +x /usr/bin/lein

RUN lein

# Install the cron service
RUN apt-get install cron -y

#Add code
ADD . /root/code

RUN chmod -R 700 /root/code/scripts/run.sh

RUN chown -R root /root/code

RUN mkdir -p /var/log && touch /var/log/cron.log

RUN echo "Building Image 3"

#Use the crontab file
RUN crontab /root/code/scripts/crontab

#Run Job
CMD ["/bin/bash", "/root/code/scripts/startup.sh", "tail -0f /var/log/cron.log"]