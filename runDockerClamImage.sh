#!/bin/bash

# Convenience script to start a docker image with pre-installed clamav...time saver!!!
eval "$(docker-machine env default)"

docker pull kpmeen/docker-clamav


docker run -d -p 3310:3310 kpmeen/docker-clamav
