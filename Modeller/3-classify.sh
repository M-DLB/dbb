#################################
# Script to process YAML files and setup the work directory
# and scans application contexts
#################################

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
        echo "*******************************************************************"
        echo "Scan application directory $DBB_MODELLER_REPODIR/$application"
        echo "*******************************************************************"
        $DBB_HOME/bin/groovyz $DBB_MODELLER_HOME/scanApplication.groovy \
           -w $DBB_MODELLER_REPODIR \
           -a $application
     done


    for mappingFile in `ls *.mapping`
    do
        application=`echo $mappingFile | awk -F. '{ print $1 }'`
        echo "*******************************************************************"
        echo "Assess Include files usages for $application"
        echo "*******************************************************************"
        $DBB_HOME/bin/groovyz $DBB_MODELLER_HOME/classifyCopybooks.groovy \
           --workspace $DBB_MODELLER_REPODIR \
           --application $application \
           --configurations $DBB_MODELLER_WORK \
           --copySharedCopybooks \
           --generateUpdatedApplicationConfiguration \
           --repositoryLayoutMapping $DBB_MODELLER_HOME/repositoryLayoutMapping.yaml
    done
    
fi