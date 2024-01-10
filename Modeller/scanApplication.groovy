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

parseArgs(args)

initScriptParameters()

println ("** Getting the list of files.")
Set<String> appFiles = getFileList()

println ("** Scanning the files.")
List<LogicalFile> logicalFiles = scanFiles(appFiles)

println ("** Store results in DBB Collection (${props.application}).")
// manage collection
if (!metadataStore.collectionExists(props.application)) {
	// create collection
	metadataStore.createCollection(props.application)
} else {
	// reset collection
	metadataStore.deleteCollection(props.application)
	metadataStore.createCollection(props.application)
}
// store results
metadataStore.getCollection(props.application).addLogicalFiles(logicalFiles)

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
def scanFiles(fileList) {
	List<LogicalFile> logicalFiles = new ArrayList<LogicalFile>()
	fileList.each{ file ->
		DependencyScanner scanner = new DependencyScanner()
		println "\t Scanning file $file "
		logicalFile = scanner.scan(file, props.workspace)
		logicalFiles.add(logicalFile)
	}
	return logicalFiles
}

/**
 * Parse CLI config
 */
def parseArgs(String[] args) {

	String usage = 'scanApplication.groovy [options]'

	def cli = new CliBuilder(usage:usage)
	// required sandbox options
	cli.w(longOpt:'workspace', args:1, 'Absolute path to workspace (root) directory containing all required source directories')
	cli.a(longOpt:'application', args:1, required:true, 'Application  name ')

	def opts = cli.parse(args)
	if (!opts) {
		System.exit(1)
	}

	if (opts.w) props.workspace = opts.w
	if (opts.a) props.application = opts.a

}

/* 
 * init additional parameters
 */
def initScriptParameters() {
	// Settings
	String applicationFolder = "${props.workspace}/${props.application}"
	if (new File(applicationFolder).exists()){
		props.applicationDir = applicationFolder
	} else {
		println ("!* Application Directory ${props.applicationDir} does not exists.")
		System.exit(1)
	}
	
	metadataStore = MetadataStoreFactory.createFileMetadataStore("${props.workspace}/.dbb")
	println "** File MetadataStore initialized at ${props.workspace}/.dbb"
}

/*
 * relativizePath - converts an absolute path to a relative path from the workspace directory
 */
def relativizePath(String path) {
	if (!path.startsWith('/'))
		return path
	String relPath = new File(props.workspace).toURI().relativize(new File(path.trim()).toURI()).getPath()
	// Directories have '/' added to the end.  Lets remove it.
	if (relPath.endsWith('/'))
		relPath = relPath.take(relPath.length()-1)
	return relPath
}
