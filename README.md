# pav-congress-api-bootstrapper

The congress API requires legislative data which is stored as JSON and YAML files in s3.  This code base is responsible
for syncing that data to various datasources so our Congress API can expose it.

## Running Locally

If you wish to sync the data from s3, AWS ACCESS_KEY and SECRET_KEY keys must be provided in the project.clj file.

There is also a dependency on Elasticsearch.  So ensure Elasticsearch is running at http://localhost:9200 and issue the following command to start the job.

    lein run


## License

Copyright © 2015 PlaceAVote

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
