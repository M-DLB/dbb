/*
 * Generate the DBB.xml file from the systemDefinition.xml file.
 * 
 * Limitation: we ignore resourcePrefix and resourceSuffix.  RTC uses
 * this prefix and suffix to append to the language definition name.
 * We could do the same with the name of the script file, but that also
 * requires us to generate the scriptMappings.txt that has the same language
 * definition names matching with what in DBB.xml file.   
 */
@groovy.transform.BaseScript com.ibm.dbb.groovy.ScriptLoader baseScript
import groovy.util.*
import groovy.transform.*
import groovy.time.*
import groovy.xml.*
import groovy.cli.commons.*
import java.nio.file.*
import java.nio.file.attribute.*
import com.ibm.dbb.*

@Field Properties props = new Properties()
def utilities

// Parse arguments from command-line
parseArgs(args)

// Print parms
println("** Script configuration:")
props.each { k,v->
	println "   $k -> $v"
}

//******************************************************************************
//* Check the workspace directory
//******************************************************************************
File workspaceDirectory = new File(props.workspace)
if (!workspaceDirectory.exists()) {
	println("*! Error: the workspace directory doesn't exist. Exiting.");
	System.exit(1);
}
if (!workspaceDirectory.isDirectory()) {
	println("*! Error: the path specified for the workspace doesn't point to a directory. Exiting.");
	System.exit(1);
}


//******************************************************************************
//* Parses the JCL migration config file
//******************************************************************************

if (props.utilitiesFilePath) {
	File utilitiesFile = new File(props.utilitiesFilePath)
	if (!utilitiesFile.exists()) {
		println("*! Error: the Utilities File '${props.utilitiesFilePath}' doesn't exist. Exiting.");
		System.exit(1);
	}
	def yamlSlurper = new groovy.yaml.YamlSlurper()
	utilities = yamlSlurper.parse(utilitiesFile) 
}


//******************************************************************************
//* Main process
//******************************************************************************
println ("** Iterating through the provided members list. ")

props.members.split(',').each() { member ->
	def outputDirectoryPath = props.workspace + '/' + member
	File outputDirectory = new File(outputDirectoryPath)
	outputDirectory.mkdirs()

	def stdout = new File(outputDirectory, "stdout.log")
	def stderr = new File(outputDirectory, "stderr.log")
	
	def sout = new StringBuffer()
	def serr = new StringBuffer()
	String DBB_HOME = System.getenv("DBB_HOME");

	println "Parsing ${props.dataset}($member)"
	def cmd = "$DBB_HOME/lib/dmh4000 -x $props.dataset $member"
	def proc = cmd.execute(null, outputDirectory)
	proc.waitForProcessOutput(sout, serr)
	stdout << sout
	stderr << serr
	proc.waitForOrKill(1000)


	//******************************************************************************
	//* Parses the JCL parser output file
	//******************************************************************************
	def parserOutputFile = new File(outputDirectory, 'DD:ATTRBOUT')
	JCL = new XmlSlurper().parseText(parserOutputFile.text)
	
	
	/*
	 * Find all steps
	 */
	def steps = JCL."**".findAll { node ->
		node.name() == "step"
	}
	
	steps.each { step ->
		println "\t- Step ${step.name} - PGM: ${step.exec.name} - PARM: ${step.parm}"
	}
}



/*  ==== Utilities ====  */

/* parseArgs: parse arguments provided through CLI */
def parseArgs(String[] args) {
	String usage = 'JCL-Analyzer.groovy [options]'
	
	def cli = new CliBuilder(usage:usage)
	cli.d(longOpt:'dataset', args:1, 'Dataset containing the JCL members to analyze (Required)')
	cli.m(longOpt:'members', args:1, 'List of comma-separated JCL member being analyzed (Required)')
	cli.w(longOpt:'workspace', args:1, 'Workspace where temporary files are written (Required)')
	cli.u(longOpt:'utilitiesFilePath', args:1, 'Path to the Utilities File (Optional)')

	def opts = cli.parse(args);
	if (!args || !opts) {
		cli.usage();
		System.exit(1);
	}
	if (opts.d) {
		props.dataset = opts.d;
	} else {
		println("*! Error: a dataset contains the JCL members to analyze ('-d' parameter) must be provided. Exiting.");
		System.exit(1);
	}

	if (opts.m) {
		props.members = opts.m;
	} else {
		println("*! Error: a comma-separated list of JCL members to analyze ('-m' parameter) must be provided. Exiting.");
		System.exit(1);
	}

	if (opts.w) {
		props.workspace = opts.w;
	} else {
		println("*! Error: the path to a workspace to store temporary files ('-w' parameter) must be provided. Exiting.");
		System.exit(1);
	}

	if (opts.u) {
		props.utilitiesFilePath = opts.u;
	}
}
