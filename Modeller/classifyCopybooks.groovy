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


@Field BuildProperties props = BuildProperties.getInstance()
@Field MetadataStore metadataStore
@Field def applicationDescriptorUtils = loadScript(new File("utils/applicationDescriptorUtils.groovy"))



/**
 * Processing logic
 */

println ("** Classification Process started. ")

// Initialization
parseArgs(args)
initScriptParameters()

// Print parms
println("** Script configuration:")
props.each { k,v->
	println "   $k -> $v"
}

// create metadatstore
metadataStore = MetadataStoreFactory.createFileMetadataStore("${props.workspace}/.dbb")
println "** File MetadataStore initialized at ${props.workspace}/.dbb"

println ("** Get list of files. ")
Set<String> appFiles = getFileList()

println ("** Analyze impacted applications. ")
assessImpactedFiles(appFiles)


/** Methods **/

/**
 * Get list relative files in the application directory
 */
def getFileList() {
	Set<String> fileSet = new HashSet<String>()

	Files.walk(Paths.get(props.applicationDir)).forEach { filePath ->
		if (Files.isRegularFile(filePath)) {
			relFile = relativizePath(filePath.toString())
			fileSet.add(relFile)
		}
	}

	return fileSet
}

/**
 * 
 */
def assessImpactedFiles(appFiles) {

	appFiles.each { file ->
		Set<String> referencingCollections = new HashSet<String>()

		// Obtain impacts
		println ("** Analyze impacted applications for file $file. ")
		def impactedFiles = findImpactedFiles(props.copybookImpactSearch, file)
		
		// Assess impacted files
		if (impactedFiles.size() > 0) 
			println "  Files depending on $file :"
		
		impactedFiles.each { impact ->
			println "    ${impact.getFile()} \t in application context ${impact.getCollection().getName()}"
			referencingCollections.add(impact.getCollection().getName())
		}

		// Asses Unique application usage
			if (referencingCollections.size() == 1) {
			println "    ==> $file is owned by application ${referencingCollections[0]} "
			
			def movedCopybook
			
			File userAppConfigurationYaml = new File("${props.inputConfigurations}/${referencingCollections[0]}.yaml")
			File updateAppConfigurationYaml = new File("${props.workspace}/${referencingCollections[0]}/${referencingCollections[0]}.yaml")
			
			// determine which YAML file to use
			def applicationDescriptor

			if (updateAppConfigurationYaml.exists()) { // update
				applicationDescriptor = applicationDescriptorUtils.readApplicationDescriptor(updateAppConfigurationYaml)
			}else { // use application yaml provided by user
				applicationDescriptor = applicationDescriptorUtils.readApplicationDescriptor(userAppConfigurationYaml)
			}
			
			// default repository path
			repositoryPath = retrieveTargetRepositoryPath(applicationDescriptor, file)
			

			if (props.copySharedCopybooks) {
				// move copybook		
				movedCopybook = copyMemberToApplicationFolder(file, "${applicationDescriptor.application}/$repositoryPath")

				// Update application mappings
				updateMappingsFiles(props.inputConfigurations, props.application, referencingCollections[0], file);
			}

			// If update flag is set
			if (props.generateUpdatedApplicationConfiguration) {
	
				// append definition
				Path pFile = Paths.get(file)
				memberName = pFile.getFileName().toString().substring(0, pFile.getFileName().toString().indexOf("."))
				applicationDescriptorUtils.appendFileDefinition(applicationDescriptor, props.defaultCopybookFolderName, "none", props.defaultCopybookFileExtension, repositoryPath, memberName, "COPYBOOK", "PRIVATE")

				// update YAML file
				
				def msg = (movedCopybook) ? "        Adding private copybook $movedCopybook to application descriptor " : "        Updating private copybook $file in application descriptor " 
				println "$msg" + updateAppConfigurationYaml.getPath()
				applicationDescriptorUtils.writeApplicationDescriptor(updateAppConfigurationYaml, applicationDescriptor)
				
			}

		} else if (referencingCollections.size() > 1) {
			println "    ==> $file referenced by multiple applications. $referencingCollections "
			
			// document as a external dependency, if update flag is set
			if (props.generateUpdatedApplicationConfiguration && props.application != "Unclassified-Copybooks") {

				File userAppConfigurationYaml = new File("${props.inputConfigurations}/${props.application}.yaml")
				File updateAppConfigurationYaml = new File("${props.workspace}/${props.application}/${props.application}.yaml")
				
				// determine which YAML file to use
				def applicationDescriptor

				if (updateAppConfigurationYaml.exists()) { // update
					applicationDescriptor = applicationDescriptorUtils.readApplicationDescriptor(updateAppConfigurationYaml)
				}else { // use application yaml provided by user
					applicationDescriptor = applicationDescriptorUtils.readApplicationDescriptor(userAppConfigurationYaml)
				}
				
				// default repository path
				repositoryPath = retrieveTargetRepositoryPath(applicationDescriptor, file)
				
				// append definition
				Path pFile = Paths.get(file)
				memberName = pFile.getFileName().toString().substring(0, pFile.getFileName().toString().indexOf("."))
				
				applicationDescriptorUtils.appendFileDefinition(applicationDescriptor, props.defaultCopybookFolderName, "none", props.defaultCopybookFileExtension, repositoryPath, memberName, "COPYBOOK", "SHARED")

				// update YAML file
				println "        Adding/updating shared copybook $file to application descriptor " + updateAppConfigurationYaml.getPath()
				applicationDescriptorUtils.writeApplicationDescriptor(updateAppConfigurationYaml, applicationDescriptor)
				
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

	String usage = 'classifyCopybooks.groovy [options]'

	def cli = new CliBuilder(usage:usage)
	// required sandbox options
	cli.w(longOpt:'workspace', args:1, 'Absolute path to workspace (root) directory containing all required source directories')
	cli.a(longOpt:'application', args:1, required:true, 'Application  name.')
	cli.c(longOpt:'configurations', args:1, required:true, 'Path of application configuration Yaml files.')
	cli.cf(longOpt:'copySharedCopybooks', args:0, 'Flag to indicate if shared copybooks should be moved.')
	cli.g(longOpt:'generateUpdatedApplicationConfiguration', args:0, 'Flag to generate updated application configuration file.')
	cli.r(longOpt:'repositoryLayoutMapping', args:1, 'Path to the Repository Layout file.')
	

	def opts = cli.parse(args)
	if (!opts) {
		System.exit(1)
	}

	if (opts.w) props.workspace = opts.w
	if (opts.a) props.application = opts.a
	if (opts.c) props.inputConfigurations = opts.c
	if (opts.cf) props.copySharedCopybooks = "true"
	if (opts.g) props.generateUpdatedApplicationConfiguration = "true"
	if (opts.r) props.repositoryLayoutMapping = opts.r

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
def copyMemberToApplicationFolder(String src, String trgtDir) {

	Path source = Paths.get("${props.workspace}", src);
	targetFile = source.getFileName().toString()
	targetDir = "${props.workspace}/${trgtDir}"
	if (!(new File(targetDir).exists())) new File(targetDir).mkdirs()

	Path target = Paths.get(targetDir, targetFile);

	if (source.toFile().exists() && source.toString() != target.toString()) {
		println "        Moving ${source.toString()} to ${target.toString()}"
		Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
		return target.toString()
	}
	
	return null
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
		println ("!* Application Diretory $applicationFolder does not exists.")
		System.exit(1)
	}

	// TODO: Customize here
	props.defaultCopybookFileExtension = "cpy"
	props.defaultCopybookFolderName = "copy"
	
	// searchpath
	props.copybookImpactSearch = "search:${props.workspace}/?path=${props.application}/**/*.cpy;**/copy/*.cpy" as String
}

/*
 * relativizePath - converts an absolute path to a relative path from the workspace directory
 */
def relativizePath(String path) {
//	if (!path.startsWith('/'))
//		return path
	String relPath = new File(props.workspace).toURI().relativize(new File(path.trim()).toURI()).getPath()
	// Directories have '/' added to the end.  Lets remove it.
	if (relPath.endsWith('/'))
		relPath = relPath.take(relPath.length()-1)
	return relPath
}

/*
 * Method to extract the repositoryPath
 * Order:
 *  Returns repositoryPath from existing application descriptor, if not present
 *  returns repositoryPath based on the repositoryLayoutMapping file, if not available
 *  return the fileCategory (last segment of the path)
 */
def retrieveTargetRepositoryPath(Object applicationDescriptor, String file) {
	
	def repositoryPath
	
	// get parent folder
	String fileCategory = new File(file).parentFile.name
	
	if (applicationDescriptor) {
		sourceGroup = applicationDescriptor.sources.find { it.name == fileCategory }
		if (sourceGroup != null) {
			repositoryPath = sourceGroup.repositoryPath
			return repositoryPath
		}
	}

	// if repositoryPath not defined, retrieve it from the default repositoryLayoutMapping.yaml
	if (repositoryPath == null) {

		if (props.repositoryLayoutMapping) {
			File repositoryLayoutMappingFile = new File(props.repositoryLayoutMapping)
			if (!repositoryLayoutMappingFile.exists()) {
				println "!* Warning: ${props.repositoryLayoutMapping} not found. Process will exit."
				System.exit(1)
			} else {
				def yamlSlurper = new groovy.yaml.YamlSlurper()
				repositoryLayoutMapping = yamlSlurper.parse(repositoryLayoutMappingFile)
				
				repositoryConfig = repositoryLayoutMapping.repositoryLayout.find {repositoryLayout ->
				repositoryLayout.mvsMapping.datasetLastLevelQualifiers.contains(fileCategory.toUpperCase())
				}
				
				if (repositoryConfig) return repositoryConfig.repositoryPath.replaceAll('\\$application', applicationDescriptor.application)
				
			}
		}
		
	}
	
	return fileCategory
	
}

def updateMappingsFiles(String path,String sourceApplication, String targetApplication, String file) {
	file = new File("${props.workspace}/$sourceApplication").toURI().relativize(new File("${props.workspace}/$file").toURI()).getPath()
    File sourceApplicationMappingFile = new File("$path/" + sourceApplication + ".mapping")
    File targetApplicationMappingFile = new File("$path/" + targetApplication + ".mapping")
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
                    if (line.contains(file)) {
						println "replacing application ($sourceApplication) to ($targetApplication) in $line"
                        line = line.replaceAll(sourceApplication, targetApplication) 
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


