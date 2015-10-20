FROM clojure

WORKDIR /app

COPY . /app

RUN lein uberjar

RUN cp target/uberjar/pav-congress-api-bootstrapper.jar pav-congress-api-bootstrapper.jar

#Run Job
CMD java -jar pav-congress-api-bootstrapper.jar