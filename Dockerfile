FROM clojure
RUN echo "Building Image"

# pull aws client
#RUN apk add --update rsync curl python unzip && rm -rf /var/cache/apk/*
#RUN curl "https://s3.amazonaws.com/aws-cli/awscli-bundle.zip" -o "awscli-bundle.zip" && unzip awscli-bundle.zip && ./awscli-bundle/install -i /usr/local/aws -b /usr/local/bin/aws
#RUN mkdir -p /work/share
#WORKDIR /work
#add . /work
#RUN ls -ltr
#CMD sh ./run.sh

WORKDIR /app

COPY . /app

RUN lein uberjar

RUN cp target/uberjar/pav-congress-api-bootstrapper.jar pav-congress-api-bootstrapper.jar

#Run Job
RUN ls -ltr
CMD java -jar pav-congress-api-bootstrapper.jar