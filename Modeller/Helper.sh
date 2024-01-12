#!/bin/sh
#*******************************************************************************
# Licensed Materials - Property of IBM
# (c) Copyright IBM Corporation 2018, 2019. All Rights Reserved.
#
# Note to U.S. Government Users Restricted Rights:
# Use, duplication or disclosure restricted by GSA ADP Schedule
# Contract with IBM Corp. 
#*******************************************************************************

export DBB_MODELLER_HOME=/u/mdalbin/Modeller/dbb/Modeller
export DBB_MODELLER_APPCONFIGS=$DBB_MODELLER_HOME/work-configs

./1-extractApplications.sh -d DBEHM.MIG.COBOL,DBEHM.MIG.COPY --applicationMapping $DBB_MODELLER_HOME/applicationMappings.yml --repositoryPathsMapping $DBB_MODELLER_HOME/repositoryPathsMapping.yaml --types $DBB_MODELLER_HOME/types.txt -o $DBB_MODELLER_APPCONFIGS

echo "Press ENTER to continue or Ctrl+C to quit..."
read
./2-runMigrations.sh

echo "Press ENTER to continue or Ctrl+C to quit..."
read
./3-classify.sh
