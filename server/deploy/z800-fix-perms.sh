#!/bin/sh
chgrp archerypal /etc/archerypal
chmod 750 /etc/archerypal
ls -la /etc/ | grep archerypal
runuser -u archerypal -- cat /etc/archerypal/shoot-sources.yaml
