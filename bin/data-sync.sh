#!/bin/sh

echo "Starting S3 sync at: $(date)"

rsync -av --delete --delete-excluded  --exclude **/text-versions/ --exclude **data.xml --exclude **text-versions.json --stats govtrack.us::govtrackdata/congress-legislators share/
rsync -av --delete --delete-excluded  --exclude **/text-versions/ --exclude **data.xml --exclude **text-versions.json --stats govtrack.us::govtrackdata/congress/113 share/congress/
rsync -av --delete --delete-excluded  --exclude **/text-versions/ --exclude **data.xml --exclude **text-versions.json --stats govtrack.us::govtrackdata/congress/114 share/congress/
rsync -av --delete --delete-excluded --exclude archive/** --exclude archive1/** --exclude **.txt --stats govtrack.us::govtrackdata/photos share/

echo "Completed S3 sync at: $(date)"
