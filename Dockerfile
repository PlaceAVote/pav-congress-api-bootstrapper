FROM clojure

RUN echo "Preparing environment"

# AWS client. clojure image is based on debian/ubuntu
RUN apt-get update && \
	apt-get install -y rsync curl python unzip && \
	rm -rf /var/lib/apt/lists/*

RUN curl "https://s3.amazonaws.com/aws-cli/awscli-bundle.zip" -o "awscli-bundle.zip" && \
	unzip awscli-bundle.zip && \
	./awscli-bundle/install -i /usr/local/aws -b /usr/local/bin/aws

COPY bin .
COPY target/uberjar/pav-congress-api-bootstrapper.jar pav-congress-api-bootstrapper.jar
CMD java -jar pav-congress-api-bootstrapper.jar