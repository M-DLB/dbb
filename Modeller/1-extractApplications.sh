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

mkdir -p $DBB_MODELLER_WORK
cd $DBB_MODELLER_WORK 

groovyz $DBB_MODELLER_HOME/extractApplications.groovy -d DBEHM.MIG.COBOL,DBEHM.MIG.COPY -mp aaa..... --applicationMapping $DBB_MODELLER_HOME/applicationMappings.yml --repositoryLayoutMapping $DBB_MODELLER_HOME/repositoryLayoutMapping.yaml --types $DBB_MODELLER_HOME/types.txt
