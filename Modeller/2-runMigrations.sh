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
	export DBB_MODELLER_APPLICATIONS=$DBB_MODELLER_HOME/work-applications
	
	if [ -d $DBB_MODELLER_APPLICATIONS ] 
	then
		rm -rf $DBB_MODELLER_APPLICATIONS
	fi
	
	cd $DBB_MODELLER_APPCONFIGS
	for mappingFile in `ls *.mapping`
	do
        application=`echo $mappingFile | awk -F. '{ print $1 }'`
        echo "***** Running the DBB Migration Utility for application $application using file $mappingFile *****"
        mkdir -p $DBB_MODELLER_APPLICATIONS/$application
        CMD="$DBB_HOME/bin/groovyz /u/dbehm/componentization/component-modeller/migrate.groovy -o $DBB_MODELLER_APPCONFIGS/$application.migration-output.txt -l $DBB_MODELLER_APPCONFIGS/$application.migration-log.txt -np info -r $DBB_MODELLER_APPLICATIONS/$application $DBB_MODELLER_APPCONFIGS/$mappingFile"
        $CMD
     done
fi      