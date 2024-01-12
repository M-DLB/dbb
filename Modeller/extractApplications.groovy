@groovy.transform.BaseScript com.ibm.dbb.groovy.ScriptLoader baseScript
import groovy.transform.*
import groovy.util.*
import groovy.time.*
import groovy.cli.commons.*
import groovy.yaml.YamlSlurper
import com.ibm.jzos.CatalogSearch;
import com.ibm.jzos.CatalogSearchField;
import com.ibm.jzos.Format1DSCB;
import com.ibm.jzos.RcException;
import com.ibm.jzos.PdsDirectory;
import com.ibm.jzos.PdsDirectory.MemberInfo;
import com.ibm.jzos.PdsDirectory.MemberInfo.Statistics;
import com.ibm.jzos.ZFile;
import com.ibm.jzos.ZFileConstants;
import com.ibm.jzos.ZUtil;
import java.util.Properties;

@Field def applicationDescriptorUtils = loadScript(new File("utils/applicationDescriptorUtils.groovy"))
@Field def applicationsMappingUtils = loadScript(new File("utils/applicationsMappingUtils.groovy"))

@Field HashMap<String, HashSet<String>> applicationMappingToDatasetMembers = new HashMap<String, HashSet<String>>()
// build type per member
@Field HashMap<String, String> types
// script properties
@Field Properties props = new Properties()
@Field repositoryPathsMapping

String applicationFilterInDSN = ''     // Will contain the pattern to apply on DSN to extract the application name
String applicationFilterInMember = ''  // Will contain the pattern to apply on members to extract the application name
String typeFilterInDSN = ''            // Will contain the pattern to apply on DSN to extract the type
String typeFilterInMember = ''         // Will contain the pattern to apply on members to extract the type



/**
 * Processing logic
 */

println ("** Extraction process started. ")

// Parse arguments from command-line
parseArgs(args)

// Print parms
println("** Script configuration:")
props.each { k,v->
	println "   $k -> $v"
}

// Read the repository layout mapping file
println ("** Reading the Repository Layout Mapping definition. ")
if (props.repositoryPathsMappingFilePath) {
	File repositoryPathsMappingFile = new File(props.repositoryPathsMappingFilePath)
	if (!repositoryPathsMappingFile.exists()) {
		println "!* Warning: File ${props.repositoryPathsMappingFilePath} not found. Process will exit."
		System.exit(1)
	} else {
		def yamlSlurper = new groovy.yaml.YamlSlurper()
		repositoryPathsMapping = yamlSlurper.parse(repositoryPathsMappingFile)
	}
}

// Applications Mapping read from YAML file (expected to be in applicationsMapping.yml so far)
println ("** Reading the Application Mapping definition. ")
@Field applicationsMapping
if (props.applicationsMappingFilePath) {
	def applicationsMappingFile = new File(props.applicationsMappingFilePath)
	if (!applicationsMappingFile.exists()) {
		println "!* Warning: File ${props.applicationsMappingFilePath} not found. All artifacts will be unassigned."
	} else {
		applicationsMapping = applicationsMappingUtils.readApplicationsMapping(applicationsMappingFile)
	}
} else {
	println "!* Warning: no Applications Mapping File defined. All artifacts will be unassigned."
}

// Read the Types from file
println ("** Reading the Type Mapping definition. ")
if (props.typesFilePath) {
	def typesFile = new File(props.typesFilePath)
	if (!typesFile.exists()) {
		println "!* Warning: File ${props.typesFilePath} not found in the current working directory. All artifacts will use the 'UNKNOWN' type."
	} else {
		types = loadMapFromFile(props.typesFilePath)
	}
} else {
	println "!* Warning: no Types File defined. All artifacts will use the 'UNKNOWN' type."
}

println ("** Iterating through the provided datasets. ")

props.datasetsList.split(',').each() { dataset ->
	String qdsn = constructPDSForZFileOperation(dataset)
	if (ZFile.dsExists(qdsn)) {
		println("*** Found $dataset");
		try {
			PdsDirectory directoryList = new PdsDirectory(qdsn)
			Iterator directoryListIterator = directoryList.iterator();
			while (directoryListIterator.hasNext()) {
				PdsDirectory.MemberInfo memberInfo = (PdsDirectory.MemberInfo) directoryListIterator.next();
				String member = (memberInfo.getName());
				def mappedApplication = findMappedApplicationFromMemberName(member)
				println("**** '$dataset($member)' - Mapped Application: " + mappedApplication);
				addDatasetMemberToApplication(mappedApplication, "$dataset($member)")
			}
			directoryList.close();
		}
		catch (java.io.IOException exception) {
			println("**** Error: Is $qdsn a valid dataset?");
		}
	}
	else {
		println("**** Error: dataset $qdsn does not exist.");
	}
}

println "** Generating Applications Configurations files. "

Iterator applicationIterator = applicationMappingToDatasetMembers.entrySet().iterator();
while (applicationIterator.hasNext()) {
	Map.Entry pair = (Map.Entry) applicationIterator.next();
	def application = pair.getKey() as String
	println "** Generating Configuration files for application $application. "
	generateMappingFile(pair.getKey())
	generateApplicationDescriptorFile(pair.getKey())
}



/*  ==== Utilities ====  */


/* parseArgs: parse arguments provided through CLI */
def parseArgs(String[] args) {
	String usage = 'extractApplications.groovy [options]'
	String header = 'options:'
	def cli = new CliBuilder(usage:usage,header:header);
	cli.d(longOpt:'datasets', args:1, 'List of comma-separated datasets to scan');
	cli.o(longOpt:'output', args:1, 'Output folder where Configurations files are written');
	cli.a(longOpt:'applicationMapping', args:1, 'Path to the Applications Mapping file')
	cli.r(longOpt:'repositoryPathsMapping', args:1, 'Path to the Repository Paths Mapping file.')
	cli.t(longOpt:'types', args:1, 'Path to the Types file')
	def opts = cli.parse(args);
	if (!args || !opts) {
		cli.usage();
		System.exit(1);
	}
	if (opts.d) {
		props.datasetsList = opts.d;
	} else {
		println("*! Error: a list of comma-separated datasets ('-d' parameter) to scan must be provided. Exiting.");
		System.exit(1);
	}
	
	if (opts.o) {
		props.outputDirectory = opts.o;
	} else {
		println("*! Error: an output directory ('-o' parameter) must be specified. Exiting.");
		System.exit(1);
	}

	if (opts.e) {
		props.encoding = opts.e
	} else {
		props.encoding = "IBM-1047"
	}

	if (opts.r) {
		props.repositoryPathsMappingFilePath = opts.r
	} else {
		println("*! Error: the path to the Repository Paths mapping file ('-r' parameter) must be specified. Exiting.");
		System.exit(1);
	}

	if (opts.a) {
		props.applicationsMappingFilePath = opts.a
	}
	
	if (opts.t) {
		props.typesFilePath = opts.t
	}
}

def constructPDSForZFileOperation(String PDS) {
	return "//'${PDS}'"
}

def constructDatasetForZFileOperation(String PDS, String member) {
	return "//'${PDS}($member)'"
}

def isFilterOnMemberMatching(String memberName, String filter) {
	StringBuilder expandedMemberNameStringBuilder = new StringBuilder(memberName);
	while (expandedMemberNameStringBuilder.length() < 8) {
		expandedMemberNameStringBuilder.append('.');
	}
	String expandedMemberName = expandedMemberNameStringBuilder.toString();

	StringBuilder expandedFilterStringBuilder = new StringBuilder(filter);
	while (expandedFilterStringBuilder.length() < 8) {
		expandedFilterStringBuilder.append('.');
	}
	String expandedFilter = expandedFilterStringBuilder.toString();
	
	StringBuilder result = new StringBuilder();
	int i = 0;
	while (i < expandedMemberName.length() && i < 8) {
		if (expandedFilter[i] != '.') {
			result.append(expandedMemberName[i])
		} else {
			result.append('.')
		}
		i++;
	}
	return result.toString().equalsIgnoreCase(filter);
}

def generateMappingFile(String application) {
	File mappingFile = new File(props.outputDirectory + '/' + application + ".mapping");
	if (mappingFile.exists()) {
		mappingFile.delete()
	}
	mappingFile.createNewFile();
	try {
		boolean append = true
		BufferedWriter writer = new BufferedWriter(new FileWriter(mappingFile, append))
		def datasetMembersCollection = applicationMappingToDatasetMembers.get(application)
		Iterator datasetMembersIterator = datasetMembersCollection.iterator();
		while (datasetMembersIterator.hasNext()) {
			String datasetMember = datasetMembersIterator.next();
			def (dataset,member) = getDatasetAndMember(datasetMember)
			def lastQualifier = getLastQualifier(dataset)
			def memberType = getType(member)
			def matchingRepositoryPath = repositoryPathsMapping.repositoryPaths.find {repositoryPath ->
				repositoryPath.mvsMapping.datasetLastLevelQualifiers.contains(lastQualifier) || repositoryPath.mvsMapping.types.contains(memberType)
			}

			def targetRepositoryPath
			def pdsEncoding
			def fileExtension
			if (matchingRepositoryPath) {
				fileExtension = (matchingRepositoryPath.fileExtension) ? (matchingRepositoryPath.fileExtension) : lastQualifier
				member = member + "." + fileExtension 
				if (matchingRepositoryPath.toLowerCase && matchingRepositoryPath.toLowerCase.toBoolean()) {
					member = member.toLowerCase()
					lastQualifier = lastQualifier.toLowerCase()
					fileExtension = fileExtension.toLowerCase()				
				}
				targetRepositoryPath = (matchingRepositoryPath.repositoryPath) ? matchingRepositoryPath.repositoryPath.replaceAll('\\$application',application) : "$application/$lastQualifier"
				pdsEncoding = (matchingRepositoryPath.encoding) ? (matchingRepositoryPath.encoding) : "IBM-1047"
			} else {
				member = member.toLowerCase()
				lastQualifier = lastQualifier.toLowerCase()
				fileExtension = lastQualifier				
				member = member + "." + fileExtension
				targetRepositoryPath = "$application/$lastQualifier"
				pdsEncoding = "IBM-1047"
			}
			writer.write("$datasetMember $targetRepositoryPath/$member pdsEncoding=$pdsEncoding\n");
		}
		writer.close();
	}
	catch (IOException e) {
		e.printStackTrace();
	}
	println("\tCreated DBB Migration Utility mapping file " + mappingFile.getAbsolutePath());
}

def generateApplicationDescriptorFile(String application) {
	File applicationDescriptorFile = new File(props.outputDirectory + '/' + application + ".yaml")
	if (applicationDescriptorFile.exists()) {
		applicationDescriptorFile.delete()
	}
	
	def applicationDescriptor = applicationDescriptorUtils.createEmptyApplicationDescriptor()
	mappedApplication = findMappedApplication(application)
	if (mappedApplication != null) {
		applicationDescriptor.application = mappedApplication.application
		applicationDescriptor.description = mappedApplication.description
		applicationDescriptor.owner = mappedApplication.owner
	} else {
		applicationDescriptor.application = "UNASSIGNED"
		applicationDescriptor.description = "Unassigned components"
		applicationDescriptor.owner = "None"
	}

	def datasetMembersCollection = applicationMappingToDatasetMembers.get(application)
	Iterator datasetMembersIterator = datasetMembersCollection.iterator();
	while (datasetMembersIterator.hasNext()) {

		String datasetMember = datasetMembersIterator.next();
		def (dataset,member) = getDatasetAndMember(datasetMember)
		def lastQualifier = getLastQualifier(dataset)
		def memberType = getType(member)
		def matchingRepositoryPath = repositoryPathsMapping.repositoryPaths.find {repositoryPath ->
			repositoryPath.mvsMapping.datasetLastLevelQualifiers.contains(lastQualifier) || repositoryPath.mvsMapping.types.contains(memberType)
		}

		def targetRepositoryPath
		def pdsEncoding
		def fileExtension
		def artifactsType
		if (matchingRepositoryPath) {
			fileExtension = (matchingRepositoryPath.fileExtension) ? (matchingRepositoryPath.fileExtension) : lastQualifier
			if (matchingRepositoryPath.toLowerCase && matchingRepositoryPath.toLowerCase.toBoolean()) {
				member = member.toLowerCase()
				lastQualifier = lastQualifier.toLowerCase()
				fileExtension = fileExtension.toLowerCase()
			}
			artifactsType = (matchingRepositoryPath.artifactsType) ? (matchingRepositoryPath.artifactsType) : lastQualifier
			targetRepositoryPath = (matchingRepositoryPath.repositoryPath) ? matchingRepositoryPath.repositoryPath.replaceAll('\\$application',application) : "$application/$lastQualifier"
			pdsEncoding = (matchingRepositoryPath.encoding) ? (matchingRepositoryPath.encoding) : "IBM-1047"
		} else {
			member = member.toLowerCase()
			lastQualifier = lastQualifier.toLowerCase()
			targetRepositoryPath = "$application/$lastQualifier"
			pdsEncoding = "IBM-1047"
			artifactsType = lastQualifier
			fileExtension = lastQualifier
		}
		applicationDescriptorUtils.appendFileDefinition(applicationDescriptor, lastQualifier, lastQualifier + ".groovy", artifactsType, fileExtension, targetRepositoryPath, member, memberType, "undefined")
	}
	applicationDescriptorUtils.writeApplicationDescriptor(applicationDescriptorFile, applicationDescriptor)
	println("\tCreated Application Description file " + applicationDescriptorFile.getAbsolutePath());
}


def addDatasetMemberToApplication(String application, String datasetMember) {
	def applicationMap = applicationMappingToDatasetMembers.get(application)

	if (applicationMap == null) {
		applicationMap = new HashSet<String>();
		applicationMappingToDatasetMembers.put(application, applicationMap);
	} else {
		applicationMap.add(datasetMember);
	}
}

def findMappedApplicationFromMemberName(String memberName) {
	if (!applicationsMapping) {
		return "UNASSIGNED"
	} else {
		def mappedApplication = applicationsMapping.applications.find { application ->
			application.namingConventions.find { namingConvention ->
				isFilterOnMemberMatching(memberName, namingConvention) 
			}
		}
		if (mappedApplication) {
			return mappedApplication.application
		} else {
			return "UNASSIGNED"
		}
	}
}

def findMappedApplication(String applicationName) {
	if (!applicationsMapping) {
		return null
	} else {
		def mappedApplication = applicationsMapping.applications.find { application -> application.application.equals(applicationName) }
		if (mappedApplication) {
			return mappedApplication
		} else {
			return null
		}
	}
}


/**
 * Parse the fullname of a qualified dataset and member name
 * Returns its dataset name and member name)  
 * For instance: BLD.LOAD(PGM1)     --> [BLD.LOAD, PGM1]
 */
def getDatasetAndMember(String fullname) {
	def ds,member;
	def elements =  fullname.split("[\\(\\)]");
	ds = elements[0];
	member = elements.size()>1? elements[1] : "";
	return [ds, member];
}

def getLastQualifier(String dataset) {
	def qualifiers =  dataset.split("\\.");
	return qualifiers.last()
}

// Reads a HashMap from a file with comma separator (',')
def loadMapFromFile(String filePath) {
	HashMap<String, String> map = new HashMap<>();
	String line;
	try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
		while ((line = reader.readLine()) != null) {
			String[] keyValuePair = line.split(",", 2);
			if (keyValuePair.length > 1) {
				String key = keyValuePair[0].trim();
				String value = keyValuePair[1].trim();
				map.put(key, value);
			}
		}
	} catch (IOException e) {
		e.printStackTrace();
	}
	return map;
}

def getType(String member) {
	if (!types) {
		return "UNKNOWN"
	} else {
		def type = types.get(member)
		if (type) {
			return type
		} else {
			return "UNKNOWN"
		}
	}
}