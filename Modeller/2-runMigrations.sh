#!/bin/sh
#*******************************************************************************
# Licensed Materials - Property of IBM
# (c) Copyright IBM Corporation 2018, 2019. All Rights Reserved.
#
# Note to U.S. Government Users Restricted Rights:
# Use, duplication or disclosure restricted by GSA ADP Schedule
# Contract with IBM Corp. 
#*******************************************************************************

export DBB_MODELLER_HOME=/u/dbehm/git/MDLB/dbb/Modeller
export DBB_MODELLER_WORK=/u/dbehm/git/MDLB/dbb/Modeller/work_app_configs
export DBB_MODELLER_REPODIR=/u/dbehm/git/MDLB/dbb/Modeller/work_repos

if [  "$DBB_HOME" = "" ]
then
    echo "Environment variable DBB_HOME is not set. Exiting..."
else
    cd $DBB_MODELLER_WORK
    for mappingFile in `ls *.mapping`
    do
        application=`echo $mappingFile | awk -F. '{ print $1 }'`
        echo "***** Running the DBB Migration Utility for application $application using file $mappingFile *****"
        mkdir -p $DBB_MODELLER_REPODIR/$application
#        cd $DBB_MODELLER_REPODIR/$application
        CMD="$DBB_HOME/bin/groovyz /u/dbehm/componentization/component-modeller/migrate.groovy -o $DBB_MODELLER_WORK/$application.migration-output.txt -l $DBB_MODELLER_WORK/$application.migration-log.txt -np info -r $DBB_MODELLER_REPODIR/$application $DBB_MODELLER_WORK/$mappingFile"
        #echo $CMD
        $CMD
     done
fi      