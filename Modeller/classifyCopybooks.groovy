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
		println "  Files depending on $file :"
		impactedFiles.each { impact ->
			println "    ${impact.getFile()} \t in application context ${impact.getCollection().getName()}"
			referencingCollections.add(impact.getCollection().getName())
		}

		// Asses Unique application usage
		if (referencingCollections.size() == 1) {
			println "    ==> $file is owned by application ${referencingCollections[0]} "

			// If update flag is set
			if (!props.application.equals(referencingCollections[0]) && props.updatedApplicationsConfiguration) {

                // TODO: determine the segment in target directory
                def movedCopybook = copyMemberToApplicationFolder(file, "${referencingCollections[0]}/${props.defaultCopybookFolderName}/")
                shuffleLinesContaining(file, props.application, referencingCollections[0]);

				File targetApplicationDescriptorFile = new File("${referencingCollections[0]}.yaml")
                
                // determine which YAML file to use
                

                if (targetApplicationDescriptorFile.exists()) {
                    // Add the File definition to the target Application Descriptor that owns it              
                    def targetApplicationDescriptor = applicationDescriptorUtils.readApplicationDescriptor(targetApplicationDescriptorFile)
                    Path pFile = Paths.get(file)
                    memberName = pFile.getFileName().toString()
                    applicationDescriptorUtils.appendFileDefinition(targetApplicationDescriptor, props.defaultCopybookFolderName, "none", props.defaultCopybookFileExtension, memberName, "COPYBOOK", "PRIVATE")
                    println "        Adding private copybook $movedCopybook to Application Descriptor " + targetApplicationDescriptorFile.getPath()
                    applicationDescriptorUtils.writeApplicationDescriptor(targetApplicationDescriptorFile, targetApplicationDescriptor)
                    
                    // Remove the File definition from the  source Application Descriptor
                    File sourceApplicationDescriptorFile = new File("${props.application}.yaml")                    
                    def sourceApplicationDescriptor = applicationDescriptorUtils.readApplicationDescriptor(sourceApplicationDescriptorFile)
                    applicationDescriptorUtils.removeFileDefinition(sourceApplicationDescriptor, "copy", memberName) 
                    applicationDescriptorUtils.writeApplicationDescriptor(sourceApplicationDescriptorFile, sourceApplicationDescriptor)
                    
                } else {
                    println "\t No Application Descriptor YAML file found for application ${referencingCollections[0]}" 
                }
				
			}
		} else if (referencingCollections.size() > 1) {
			println "    ==> $file referenced by multiple applications. $referencingCollections "
			
			// document as a external dependency, if update flag is set
			if (props.updatedApplicationsConfiguration) {
                println "        Changing copybook $file to PUBLIC in ${props.application}.yaml"

                File applicationDescriptorFile = new File("${props.application}.yaml")                    
                def applicationDescriptor = applicationDescriptorUtils.readApplicationDescriptor(applicationDescriptorFile)
                // Update the File definition              
                Path pFile = Paths.get(file)
                memberName = pFile.getFileName().toString()
                applicationDescriptorUtils.removeFileDefinition(applicationDescriptor, props.defaultCopybookFolderName, memberName) 
                applicationDescriptorUtils.appendFileDefinition(applicationDescriptor, props.defaultCopybookFolderName, "none", props.defaultCopybookFileExtension, memberName, "COPYBOOK", "PUBLIC")
                applicationDescriptorUtils.writeApplicationDescriptor(applicationDescriptorFile, applicationDescriptor)
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
	cli.u(longOpt:'updatedApplicationsConfiguration', args:0, 'Flag to update Application Descriptors and Mapping files.')


	def opts = cli.parse(args)
	if (!opts) {
		System.exit(1)
	}

	if (opts.w) props.workspace = opts.w
	if (opts.a) props.application = opts.a
	if (opts.u) props.updatedApplicationsConfiguration = "true"

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

def shuffleLinesContaining(String file, String sourceApplication, String targetApplication) {
    File sourceApplicationMappingFile = new File(sourceApplication + ".mapping")
    File targetApplicationMappingFile = new File(targetApplication + ".mapping")
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
                        line = line.replace(sourceApplication, targetApplication) 
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


