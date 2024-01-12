#!/bin/sh
#*******************************************************************************
# Licensed Materials - Property of IBM
# (c) Copyright IBM Corporation 2018, 2019. All Rights Reserved.
#
# Note to U.S. Government Users Restricted Rights:
# Use, duplication or disclosure restricted by GSA ADP Schedule
# Contract with IBM Corp. 
#*******************************************************************************

if [  "$DBB_HOME" = "" ]
then
   echo "Environment variable DBB_HOME is not set. Exiting..."
else
    export DBB_MODELLER_HOME=/u/mdalbin/Modeller/dbb/Modeller
	export DBB_MODELLER_APPCONFIGS=$DBB_MODELLER_HOME/work-configs
	if [ -d $DBB_MODELLER_APPCONFIGS ] 
	then
		rm -rf $DBB_MODELLER_APPCONFIGS
	fi
	mkdir -p $DBB_MODELLER_APPCONFIGS
    CMD="$DBB_HOME/bin/groovyz ./extractApplications.groovy"
    $CMD "$@"
fi