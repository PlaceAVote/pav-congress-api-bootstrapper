FROM clojure

RUN echo "Building Image"

WORKDIR /app

COPY . /app

RUN lein uberjar

RUN cp target/uberjar/pav-congress-api-bootstrapper.jar pav-congress-api-bootstrapper.jar

#Run Job
RUN ls -ltr
CMD java -jar pav-congress-api-bootstrapper.jar