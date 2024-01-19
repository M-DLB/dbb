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
    CMD="$DBB_HOME/bin/groovyz ./JCL-Analyzer.groovy"
    $CMD "$@"    
fi