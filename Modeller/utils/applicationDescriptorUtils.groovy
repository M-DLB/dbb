@groovy.transform.BaseScript com.ibm.dbb.groovy.ScriptLoader baseScript
import com.ibm.dbb.metadata.*
import com.ibm.dbb.dependency.*
import com.ibm.dbb.build.*
import groovy.transform.*
import com.ibm.dbb.build.report.*
import com.ibm.dbb.build.report.records.*
import groovy.yaml.YamlSlurper
import groovy.yaml.YamlBuilder
import groovy.lang.GroovyShell
import groovy.util.*
import java.nio.file.*

/**
 * Utilities to read, update or export existing ApplicationDescriptor from/to YAML 
 */

class ApplicationDescriptor {
	String application
	String description
	String owner
	ArrayList<Source> sources
	HashSet<String> dependencies
	HashSet<String> consumers
}

class Source {
	String name
	String repositoryPath
	String languageProcessor
	String fileExtension
	String artifactsType
	ArrayList<FileDef> files
}

class FileDef {
	String name
	String type
	String usage
}


/////// Test
//ApplicationDescriptor applicationDescriptor = readApplicationDescriptor(new File("/u/dbehm/componentization/applicationConfigurations/retirementCalculator.yaml"))
//println applicationDescriptor.application
//println applicationDescriptor.description
//println applicationDescriptor.sources.size()
//
//applicationDescriptor = appendFileDefinition(applicationDescriptor, "copy", "none", "COPY", "COPYBOOK", "PRIVATE")
//
//println applicationDescriptor.application
//println applicationDescriptor.description
//println applicationDescriptor.sources.size()
//
//writeApplicationDescriptor(new File("/u/dbehm/componentization/work/retirementCalculator.yaml"), applicationDescriptor)

/**
 * 
 * Reads an existing application descriptor YAML
 * returns an ApplicationDescriptor Object
 * 
 */
def readApplicationDescriptor(File yamlFile){
	// Internal objects
	def yamlSlurper = new groovy.yaml.YamlSlurper()
	def appConfiguration = new ApplicationDescriptor()
	ApplicationDescriptor appDescriptor = yamlSlurper.parse(yamlFile)
	return appDescriptor
}

/**
 * Write an ApplicationDescriptor Object into a YAML file
 */
def writeApplicationDescriptor(File yamlFile, ApplicationDescriptor applicationDescriptor){
	def yamlBuilder = new YamlBuilder()
	// build updated application descriptor

	yamlBuilder {
		application applicationDescriptor.application
		description applicationDescriptor.description
		owner applicationDescriptor.owner
		sources (applicationDescriptor.sources)
		dependencies applicationDescriptor.dependencies
		consumers applicationDescriptor.consumers
	}

	// write file
	yamlFile.withWriter() { writer ->
		writer.write(yamlBuilder.toString())
	}
}

/**
 * Method to update the Application Descriptor
 * 
 * Appends to an existing source sourceGroupName, if it exists.
 * If the sourceGroupName cannot be found, it creates a new sourceGroup
 * 
 */

def appendFileDefinition(ApplicationDescriptor applicationDescriptor, String sourceGroupName, String languageProcessor, String artifactsType, String fileExtension, String repositoryPath, String name, String type, String usage){

	def sourceGroupRecord

	def fileRecord = new FileDef()
	fileRecord.name = name
	fileRecord.type = type
	fileRecord.usage = usage
	
	if (!applicationDescriptor.sources) {
	   applicationDescriptor.sources = new ArrayList<Source>()
	}

	existingSourceGroup = applicationDescriptor.sources.find(){ source ->
		source.name == sourceGroupName
	}

	if (existingSourceGroup) { // append file record definition to existing sourceGroup
        sourceGroupRecord = existingSourceGroup
		
		// check if the fileRecord already exists, and this is an update

		existingFileRecord = sourceGroupRecord.files.find(){ file ->
			file.name == fileRecord.name
		}

		if (existingFileRecord) { // update existing file record
			existingFileRecord.type = type
			existingFileRecord.usage = usage
		}
		else { // add a new record
			sourceGroupRecord.files.add(fileRecord)
		}

	}
	else {

		// create a new source group entry
		sourceGroupRecord = new Source()
		sourceGroupRecord.name = sourceGroupName
		sourceGroupRecord.languageProcessor = languageProcessor
		sourceGroupRecord.fileExtension = fileExtension
		sourceGroupRecord.artifactsType = artifactsType
		sourceGroupRecord.repositoryPath = repositoryPath

		sourceGroupRecord.files = new ArrayList<FileDef>()
		// append file record
		sourceGroupRecord.files.add(fileRecord)
		applicationDescriptor.sources.add(sourceGroupRecord)
	}
}


/**
 * Method to remove a file from the Application Descriptor
 */

def removeFileDefinition(ApplicationDescriptor applicationDescriptor, String sourceGroupName, String name){

    if (applicationDescriptor.sources) {
        def existingSourceGroup = applicationDescriptor.sources.find() { source ->
            source.name == sourceGroupName
        }
        if (existingSourceGroup) { // Found an existing Source Group that matches
            def existingFileDef = existingSourceGroup.files.find { file ->
                file.name.equals(name)
            }
            if (existingFileDef) {
//                println "Found matching file ${existingFileDef.name}"
                existingSourceGroup.files.remove(existingFileDef)
            }
        }
    }
}

/**
 * Method to add an application dependency 
 */

def addApplicationDependency(ApplicationDescriptor applicationDescriptor, applicationDependency) {
	
	if (!applicationDescriptor.dependencies) {
		applicationDescriptor.dependencies = new HashSet<String>()
	 }
	 
	applicationDescriptor.dependencies.add(applicationDependency)
}

/**
 * Method to add a consumer to list of consumers
 */

def addApplicationConsumer(ApplicationDescriptor applicationDescriptor, applicationDependency) {
	
	// init
	if (!applicationDescriptor.consumers) {
		applicationDescriptor.consumers = new HashSet<String>()
	 }
	 
	// dont add the "owning" application
	if (applicationDescriptor.application != applicationDependency) {
		applicationDescriptor.consumers.add(applicationDependency)
	}
}

def createEmptyApplicationDescriptor(){
    ApplicationDescriptor applicationDescriptor = new ApplicationDescriptor()
    applicationDescriptor.sources = new ArrayList<Source>()
    return applicationDescriptor
}
