#!/usr/bin/env bash
#Run Job
cd /root/code
ls -ltr
lein with-profile prod run
