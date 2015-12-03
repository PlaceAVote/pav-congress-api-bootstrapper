# pav-congress-api-bootstrapper

The congress API requires legislative data which is stored as JSON and
YAML files in s3. This code base is responsible for syncing that data
to various datasources so our Congress API can expose it.

## Running Locally

If you wish to sync the data from s3, AWS ACCESS_KEY and SECRET_KEY
keys must be provided in the project.clj file.

There is also a dependency on Elasticsearch and Redis; ensure
Elasticsearch is running at http://localhost:9200 and Redis at
redis://localhost:6379. To start the job, issue the following command:

    lein run

## Convox deployment

Before deploying to Convox, make sure to compile the code with `lein
uberjar`. In short, this will do the magic:

    lein uberjar
	convox deploy

## License

Copyright © 2015 PlaceAVote

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
