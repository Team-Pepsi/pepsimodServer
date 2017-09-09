#!/bin/bash

# This is the shell startup file for PorkBot.
# Input ./start.sh while in the server directory
# to start the server.

#Change this to "true" to 
#loop PorkBot after restart!

DO_LOOP="true"

###############################
# DO NOT EDIT ANYTHING BELOW! #
###############################

clear

# mvn clean compile

while [ "$DO_LOOP" == "true" ]; do
	mvn exec:java -Dexec.args="/var/lib/jenkins/workspace/pepsimod/build/libs/pepsimod-11.1-full.jar" -Dexec.mainClass="team.pepsi.pepsimod.server.Server" -Dexec.classpathScope=runtime
	echo "Press Ctrl+c to stop"
	sleep 2
done
