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
    rm -rf work
    mkdir -p work
    for mappingFile in `ls *.mapping`
    do
        application=`echo $mappingFile | awk -F. '{ print $1 }'`
        echo "***** Running the DBB Migration Utility for application $application using file $mappingFile *****"
        $DBB_HOME/migration/bin/migrate.sh -o work/$mappingFile.migration-output.txt -l work/$mappingFile.migration-log.txt -np info -r work/$application $mappingFile
     done
fi      
