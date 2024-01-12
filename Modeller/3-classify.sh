#################################
# Script to process YAML files and setup the work directory
# and scans application contexts
#################################

if [  "$DBB_HOME" = "" ]
then
    echo "Environment variable DBB_HOME is not set. Exiting..."
else
	export DBB_MODELLER_HOME=/u/mdalbin/Modeller/dbb/Modeller
	export DBB_MODELLER_APPCONFIGS=$DBB_MODELLER_HOME/work-configs
	export DBB_MODELLER_APPLICATIONS=$DBB_MODELLER_HOME/work-applications
	
    cd $DBB_MODELLER_APPCONFIGS
    for mappingFile in `ls *.mapping`
    do
        application=`echo $mappingFile | awk -F. '{ print $1 }'`
        echo "*******************************************************************"
        echo "Scan application directory $DBB_MODELLER_APPLICATIONS/$application"
        echo "*******************************************************************"
        $DBB_HOME/bin/groovyz $DBB_MODELLER_HOME/scanApplication.groovy \
           -w $DBB_MODELLER_APPLICATIONS \
           -a $application
	done

    cd $DBB_MODELLER_APPCONFIGS
    for mappingFile in `ls *.mapping`
    do
        application=`echo $mappingFile | awk -F. '{ print $1 }'`
        echo "*******************************************************************"
        echo "Assess Include files usage for $application"
        echo "*******************************************************************"
        $DBB_HOME/bin/groovyz $DBB_MODELLER_HOME/classifyIncludeFiles.groovy \
           --workspace $DBB_MODELLER_APPLICATIONS \
           --application $application \
           --configurations $DBB_MODELLER_APPCONFIGS \
           --updatedApplicationConfiguration
    done
    
fi