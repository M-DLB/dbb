@groovy.transform.BaseScript com.ibm.dbb.groovy.ScriptLoader baseScript
import com.ibm.dbb.metadata.*
import com.ibm.dbb.dependency.*
import com.ibm.dbb.build.*
import groovy.transform.*
import com.ibm.dbb.build.report.*
import com.ibm.dbb.build.report.records.*
import groovy.yaml.YamlSlurper
import groovy.lang.GroovyShell
import groovy.util.*
import java.nio.file.*
import groovy.cli.commons.*
import static java.nio.file.StandardCopyOption.*


@Field BuildProperties props = BuildProperties.getInstance()
@Field MetadataStore metadataStore
@Field def applicationDescriptorUtils = loadScript(new File("utils/applicationDescriptorUtils.groovy"))
@Field File originalApplicationDescriptorFile //Original Application Descriptor file in CONFIGS
@Field File updatedApplicationDescriptorFile  //Updated Application Descriptor file in APPLICATIONS
@Field def applicationDescriptor


/**
 * Processing logic
 */

//println ("** Classification Process started. ")

// Initialization
parseArgs(args)
initScriptParameters()

// Print parms
println("** Script configuration:")
props.each { k,v->
	println "   $k -> $v"
}

// create metadatastore
metadataStore = MetadataStoreFactory.createFileMetadataStore("${props.workspace}/.dbb")
if (!metadataStore) {
	println("!* Error: Failed to initialize the DBB File Metatadastore at ${props.workspace}/.dbb");
} 
//println "** File MetadataStore initialized at ${props.workspace}/.dbb"

println("** Getting the list of files of 'Include File' type.")
HashMap<String, ArrayList<String>> includesFiles = getIncludeFilesFromApplicationDescriptor()

if (includesFiles && includesFiles.size() > 0) {
	println("** Analyzing impacted applications.")
	assessImpactedFiles(includesFiles)
} else {
	println("*** No source found with 'Include File' type.")
}


/** Methods **/

def getIncludeFilesFromApplicationDescriptor() {
	HashMap<String, ArrayList<String>> files = new HashMap<String, ArrayList<String>>()

	def matchingSources = applicationDescriptor.sources.find { source ->
		source.artifactsType.equalsIgnoreCase("Include File") 
	}
	if (matchingSources) {
		matchingSources.files.each() { file ->
			def impactSearchRule = 	"search:${props.workspace}/?path=${props.application}/${matchingSources.repositoryPath}/*." + matchingSources.fileExtension + ";**/${matchingSources.repositoryPath}/*."  + matchingSources.fileExtension as String
			ArrayList<String> properties = new ArrayList<String>()
			properties.add(impactSearchRule)
			properties.add(matchingSources.repositoryPath)
			properties.add(matchingSources.fileExtension)
			properties.add(matchingSources.artifactsType)
			properties.add(matchingSources.name) 
			files.put(file.name, properties)
		}
	}

	return files
}

/**
 * 
 */
def assessImpactedFiles(HashMap<String, ArrayList<String>> includeFiles) {

	includeFiles.each { includeFile ->
		def file = includeFile.getKey()
		def properties = includeFile.getValue()
		def impactSearchRule = properties[0]
		def repositoryPath = properties[1]
		def fileExtension = properties[2]
		def artifactsType = properties[3]
		def sourceGroupName = properties[4]
		def qualifiedFile = repositoryPath + '/' + file + '.' + fileExtension
		
		Set<String> referencingCollections = new HashSet<String>()

		// Obtain impacts
		println ("** Analyzing impacted applications for file ${props.application}/${qualifiedFile}.")
		def impactedFiles = findImpactedFiles(impactSearchRule, props.application + '/' + qualifiedFile)
		
		// Assess impacted files
		if (impactedFiles.size() > 0) 
			println "  Files depending on ${repositoryPath}/${file}.${fileExtension} :"
		
		impactedFiles.each { impact ->
			println "    ${impact.getFile()} \t in application context ${impact.getCollection().getName()}"
			referencingCollections.add(impact.getCollection().getName())
		}

		// Assess usage when only 1 application reference the file
		if (referencingCollections.size() == 1) {
			println "\t==> $qualifiedFile is owned by application ${referencingCollections[0]} "
			
			// If update flag is set
			if (props.updatedApplicationConfiguration) {
		
				// If Include File belongs to the scanned application
				if (props.application.equals(referencingCollections[0])) {
					// Just update the usage to PRIVATE
					applicationDescriptorUtils.appendFileDefinition(applicationDescriptor, sourceGroupName, "none", artifactsType, fileExtension, repositoryPath, file, "Include File", "PRIVATE")
					println "\t==> Updating usage of Include File '$file' to PRIVATE in Application Descriptor ${updatedApplicationDescriptorFile.getPath()}."
				} else { // Only an other application references this Include File, so move it
					// Start by removing the file for the application
					applicationDescriptorUtils.removeFileDefinition(applicationDescriptor, sourceGroupName, file)
					// Update the target Application Descriptor 
					originalTargetApplicationDescriptorFile = new File("${props.configurationsDirectory}/${referencingCollections[0]}.yaml")
					updatedTargetApplicationDescriptorFile = new File("${props.workspace}/${referencingCollections[0]}/${referencingCollections[0]}.yaml")
					def targetApplicationDescriptor
					// determine which YAML file to use
					if (updatedTargetApplicationDescriptorFile.exists()) { // update the Application Descriptor that already exists in the Application repository
						targetApplicationDescriptor = applicationDescriptorUtils.readApplicationDescriptor(updatedTargetApplicationDescriptorFile)
					} else { // Start from the original Application Descriptor created by the extraction phase
						if (originalTargetApplicationDescriptorFile.exists()) {
							Files.copy(originalTargetApplicationDescriptorFile.toPath(), updatedTargetApplicationDescriptorFile.toPath(), REPLACE_EXISTING)
							targetApplicationDescriptor = applicationDescriptorUtils.readApplicationDescriptor(updatedTargetApplicationDescriptorFile)
						} else {
							println ("!* Application Descriptor file ${originalTargetApplicationDescriptorFile.getPath()} was not found. Skipping the configuration update for Include File ${file}.")
						}
					}
					// Target Application Descriptor file has been found and can be updated
					if (targetApplicationDescriptor) {
						targetRepositoryPath = computeTargetFilePath(repositoryPath, props.application, referencingCollections[0])
						applicationDescriptorUtils.appendFileDefinition(targetApplicationDescriptor, sourceGroupName, "none", artifactsType, fileExtension, targetRepositoryPath, file, "Include File", "PRIVATE")
						applicationDescriptorUtils.writeApplicationDescriptor(updatedTargetApplicationDescriptorFile, targetApplicationDescriptor)
						copyFileToApplicationFolder(props.application + '/' + qualifiedFile, props.application, referencingCollections[0])
						// Update application mappings
						updateMappingFiles(props.configurationsDirectory, props.application, referencingCollections[0], qualifiedFile);
						println "\t==> Moving Include File '$file' with usage 'PRIVATE' to Application '${referencingCollections[0]}' described in Application Descriptor ${updatedTargetApplicationDescriptorFile.getPath()}."
					}
				}
				applicationDescriptorUtils.writeApplicationDescriptor(updatedApplicationDescriptorFile, applicationDescriptor)
			}

		} else if (referencingCollections.size() > 1) {
			println "    ==> $file referenced by multiple applications - $referencingCollections "
			
			if (props.updatedApplicationConfiguration) {
				// just modify the scope as PUBLIC or SHARED
				if (props.application.equals("UNASSIGNED")) {
					applicationDescriptorUtils.appendFileDefinition(applicationDescriptor, sourceGroupName, "none", artifactsType, fileExtension, repositoryPath, file, "Include File", "SHARED")
					println "\t==> Updating usage of Include File '$file' to SHARED in Application Descriptor ${updatedApplicationDescriptorFile.getPath()}."
				} else {
					applicationDescriptorUtils.appendFileDefinition(applicationDescriptor, sourceGroupName, "none", artifactsType, fileExtension, repositoryPath, file, "Include File", "PUBLIC")
					println "\t==> Updating usage of Include File '$file' to PUBLIC in Application Descriptor ${updatedApplicationDescriptorFile.getPath()}."
				}
				applicationDescriptorUtils.writeApplicationDescriptor(updatedApplicationDescriptorFile, applicationDescriptor)
			}
			
		} else {
			println "\t The Include File $file is not used at all."
		}
	}
}


/**
 * Parse CLI config
 */
def parseArgs(String[] args) {

	String usage = 'classifyIncludeFiles.groovy [options]'

	def cli = new CliBuilder(usage:usage)
	// required sandbox options
	cli.w(longOpt:'workspace', args:1, 'Absolute path to workspace (root) directory containing all required source directories')
	cli.a(longOpt:'application', args:1, required:true, 'Application  name.')
	cli.c(longOpt:'configurations', args:1, required:true, 'Path of the directory containing Application Configurations YAML files.')
	cli.u(longOpt:'updatedApplicationConfiguration', args:0, 'Flag to generate updated Application Configuration files.')

	def opts = cli.parse(args)
	if (!opts) {
		System.exit(1)
	}

	if (opts.w) props.workspace = opts.w
	if (opts.a) props.application = opts.a
	if (opts.c) props.configurationsDirectory = opts.c
	if (opts.u) {
		props.updatedApplicationConfiguration = "true"
	} else {
		props.updatedApplicationConfiguration = "false"
	}
}

/*
 * findImpactedFiles -
 *  method to configure and invoke SearchPathImpactFinder
 *
 *  @return list of impacted files
 *
 */
def findImpactedFiles(String impactSearch, String file) {

	List<String> collections = new ArrayList<String>()
	metadataStore.getCollections().each{ collection ->
		collections.add(collection.getName())
	}

	println ("*** Creating SearchPathImpactFinder with collections " + collections + " and impactSearch configuration " + impactSearch)

	def finder = new SearchPathImpactFinder(impactSearch, collections)

	// Find all files impacted by the changed file
	impacts = finder.findImpactedFiles(file, props.workspace)
	return impacts
}

/**
 * Copies a relative source member to the relative target directory.
 *  
 */
def copyFileToApplicationFolder(String file, String sourceApplication, String targetApplication) {
	targetFilePath = computeTargetFilePath(file, sourceApplication, targetApplication)
	Path source = Paths.get("${props.workspace}", file)
	def target = Paths.get("${props.workspace}", targetFilePath)
	def targetDir = target.getParent()
	File targetDirFile = new File(targetDir.toString())
	if (!targetDirFile.exists()) targetDirFile.mkdirs()
	if (source.toFile().exists() && source.toString() != target.toString()) {
		Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
		return target.toString()
	}
}

/* 
 * init additional parameters
 */
def initScriptParameters() {
	// Settings

	String applicationFolder = "${props.workspace}/${props.application}"

	// application folder
	if (new File(applicationFolder).exists()){
		props.applicationDir = applicationFolder
	} else {
		println ("!* Application Directory $applicationFolder does not exist.")
		System.exit(1)
	}
	
	originalApplicationDescriptorFile = new File("${props.configurationsDirectory}/${props.application}.yaml")
	updatedApplicationDescriptorFile = new File("${props.workspace}/${props.application}/${props.application}.yaml")
	// determine which YAML file to use
	if (updatedApplicationDescriptorFile.exists()) { // update the Application Descriptor that already exists in the Application repository
		applicationDescriptor = applicationDescriptorUtils.readApplicationDescriptor(updatedApplicationDescriptorFile)
	} else { // Start from the original Application Descriptor created by the extraction phase
		if (originalApplicationDescriptorFile.exists()) {
			Files.copy(originalApplicationDescriptorFile.toPath(), updatedApplicationDescriptorFile.toPath(), REPLACE_EXISTING)
			applicationDescriptor = applicationDescriptorUtils.readApplicationDescriptor(updatedApplicationDescriptorFile)
		} else {
			println ("!* Application Descriptor file ${originalApplicationDescriptorFile.getPath()} was not found. Exiting.")
			System.exit(1)
		}
	}
}

/*
 * Method to extract the repositoryPath
 * Order:
 *  Returns repositoryPath from existing application descriptor, if not present
 *  returns repositoryPath based on the repositoryPathsMapping file, if not available
 *  return the fileCategory (last segment of the path)
 */
/* def retrieveTargetRepositoryPath(Object applicationDescriptor, String file) {
	
	def targetRepositoryPath
	
	// get parent folder
	String fileCategory = new File(file).parentFile.name
	
	if (applicationDescriptor) {
		sourceGroup = applicationDescriptor.sources.find { it.name == fileCategory }
		if (sourceGroup != null) {
			targetRepositoryPath = sourceGroup.repositoryPath
			return targetRepositoryPath
		}
	}

	// if repositoryPath not defined, retrieve it from the default repositoryLayoutMapping.yaml
	if (targetRepositoryPath == null) {

		if (props.repositoryPathsMappingFilePath) {
			File repositoryPathsMappingFile = new File(props.repositoryPathsMappingFilePath)
			if (!repositoryPathsMappingFile.exists()) {
				println "!* Warning: ${props.repositoryPathsMappingFilePath} not found. Process will exit."
				System.exit(1)
			} else {
				def yamlSlurper = new groovy.yaml.YamlSlurper()
				repositoryPathsMapping = yamlSlurper.parse(repositoryPathsMappingFile)
				
				matchingRepositoryPath = repositoryPathsMapping.repositoryPaths.find {repositoryPath ->
					repositoryPath.mvsMapping.datasetLastLevelQualifiers.contains(fileCategory.toUpperCase())
				}
				
				if (matchingRepositoryPath) return matchingRepositoryPath.repositoryPath.replaceAll('\\$application', applicationDescriptor.application)
				
			}
		}
		
	}
	
	return fileCategory
	
} */

def computeTargetFilePath(String file, String sourceApplication, String targetApplication) {
	def filenameSegments = file.split('/')
	ArrayList<String> targetFilename = new ArrayList<String>()
	filenameSegments.each() { filenameSegment ->
		if (filenameSegment.equals(sourceApplication)) {
			targetFilename.add(targetApplication)
		} else {
			targetFilename.add(filenameSegment)
		}
	}
	return targetFilename.join('/')
}

def updateMappingFiles(String configurationsDirectory, String sourceApplication, String targetApplication, String file) {
//	file = new File("${props.workspace}/$sourceApplication").toURI().relativize(new File("${props.workspace}/$file").toURI()).getPath()
    File sourceApplicationMappingFile = new File("${configurationsDirectory}/${sourceApplication}.mapping")
    File targetApplicationMappingFile = new File("${configurationsDirectory}/${targetApplication}.mapping")
    if (!sourceApplicationMappingFile.exists()) {
        println "!* Error: couldn't find the mapping file called '${sourceApplication}.mapping'" 
    } else {
        if (!targetApplicationMappingFile.exists()) {
            println "!* Error: couldn't find the mapping file called '${targetApplication}.mapping'" 
        } else {    
            try {
                File newSourceApplicationMappingFile = new File(sourceApplication + ".mapping.new")
                newSourceApplicationMappingFile.createNewFile()
                boolean append = true
                BufferedReader sourceApplicationMappingReader = new BufferedReader(new FileReader(sourceApplicationMappingFile))
                BufferedWriter targetApplicationMappingWriter = new BufferedWriter(new FileWriter(targetApplicationMappingFile, append))
                BufferedWriter newSourceApplicationMappingWriter = new BufferedWriter(new FileWriter(newSourceApplicationMappingFile, append))
                String line;
                while((line = sourceApplicationMappingReader.readLine()) != null) {
					def lineSegments = line.split(' ')
                    if (lineSegments[1].equals(file)) {
						//println "replacing application ($sourceApplication) to ($targetApplication) in $line"
						lineSegments[1] = computeTargetFilePath(lineSegments[1], sourceApplication, targetApplication)
						line = String.join(' ', lineSegments)
                        targetApplicationMappingWriter.write(line + "\n")
                    } else {
                        newSourceApplicationMappingWriter.write(line + "\n")
                    }
                }
                targetApplicationMappingWriter.close()
                newSourceApplicationMappingWriter.close()
                sourceApplicationMappingReader.close()
                sourceApplicationMappingFile.delete()
                Files.move(newSourceApplicationMappingFile.toPath(), sourceApplicationMappingFile.toPath())
            }
            catch (IOException e) {
                e.printStackTrace()
            }
        }
    }
}


