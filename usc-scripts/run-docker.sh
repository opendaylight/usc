#!/bin/sh -v
docker run --rm -t -p 50022:22 -t -p 58383:8383 dockeruser/netopeer &

ssh-copy-id -p 50022 root@localhost

ssh -p 50022 root@localhost 'bash -s' < docker-script.sh

