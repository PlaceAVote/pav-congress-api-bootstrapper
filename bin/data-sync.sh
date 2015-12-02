#!/bin/sh

echo "Starting S3 sync at: $(date)"

rsync -av --delete --delete-excluded  --exclude **/text-versions/ --exclude **data.xml --exclude **text-versions.json --stats govtrack.us::govtrackdata/congress-legislators share/
rsync -av --delete --delete-excluded  --exclude **/text-versions/ --exclude **data.xml --exclude **text-versions.json --stats govtrack.us::govtrackdata/congress/113 share/congress/
rsync -av --delete --delete-excluded  --exclude **/text-versions/ --exclude **data.xml --exclude **text-versions.json --stats govtrack.us::govtrackdata/congress/114 share/congress/
rsync -av --delete --delete-excluded --exclude archive/** --exclude archive1/** --exclude **.txt --stats govtrack.us::govtrackdata/photos share/

#aws s3 sync share/ "s3://$S3_BUCKET" --delete
echo "FINISHED RUN AT $(date). SLEEPING FOR 5 HOURS"

echo "Completed S3 sync at: $(date)"
