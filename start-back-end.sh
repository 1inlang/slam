#!/bin/bash
cd "$(dirname "$0")/backend"
echo "Starting SLAM Backend..."
mvn spring-boot:run
