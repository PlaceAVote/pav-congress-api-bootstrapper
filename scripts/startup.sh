#!/usr/bin/env bash
# Start the cron service in the background. Unfortunately upstart doesnt work yet.
cron -f &

tail -0f /var/log/cron.log
