#################################
# Script to process YAML files and setup the work directory
# and scans application contexts
#################################

applicationsDir="work"

if [  "$DBB_HOME" = "" ]
then
    echo "Environment variable DBB_HOME is not set. Exiting..."
else
    for mappingFile in `ls *.mapping`
    do
        application=`echo $mappingFile | awk -F. '{ print $1 }'`
        echo "*******************************************************************"
        echo "Scan application directory work/$application"
        echo "*******************************************************************"
        $DBB_HOME/bin/groovyz scanApplication.groovy -w work -a $application
     done


    for mappingFile in `ls *.mapping`
    do
        application=`echo $mappingFile | awk -F. '{ print $1 }'`
        echo "*******************************************************************"
        echo "Assess Include files usages for $application"
        echo "*******************************************************************"
        $DBB_HOME/bin/groovyz classifyCopybooks.groovy --workspace work --application $application --updatedApplicationsConfiguration 
     done
    
fi