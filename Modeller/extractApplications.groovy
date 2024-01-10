@groovy.transform.BaseScript com.ibm.dbb.groovy.ScriptLoader baseScript
import groovy.transform.*
import groovy.util.*
import groovy.time.*
import groovy.cli.commons.*
import groovy.yaml.YamlSlurper
import com.ibm.dmh.scan.classify.Dmh5210;
import com.ibm.dmh.scan.classify.ScanProperties;
import com.ibm.teamz.classify.ClassifyFileContent;
import com.ibm.dmh.scan.classify.IncludedFileMetaData;
import com.ibm.dmh.scan.classify.SingleFilesMetadata;
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
@Field repositoryLayoutMapping

// ArrayList<String> datasets        // Will be populated to contain the list of datasets to process and scan
// boolean necessaryScanning		// not in use
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

// Applications Mapping read from YAML file (expected to be in applicationsMapping.yml so far)
println ("** Read Application Mapping definition. ")
@Field applicationsMapping
if (props.applicationsMappingFilePath) {
	def applicationsMappingFile = new File(props.applicationsMappingFilePath)
	if (!applicationsMappingFile.exists()) {
		println "!* Warning: File $props.applicationsMappingFilePath not found. All artifacts will be unassigned."
	} else {
		applicationsMapping = applicationsMappingUtils.readApplicationsMapping(applicationsMappingFile)
	}
} else {
	println "!* Warning: no Applications Mapping File defined. All artifacts will be unassigned."
}

// Read the Types from file types.txt
println ("** Read Type Mapping definition. ")
if (props.typesFilePath) {
	def typesFile = new File(props.typesFilePath)
	if (!typesFile.exists()) {
		println "!* Warning: File types.txt not found in the current working directory. All artifacts will use the 'UNKNOWN' type."
	} else {
		types = loadMapFromFile(props.typesFilePath)
	}
} else {
	println "!* Warning: no Types File defined. All artifacts will use the 'UNKNOWN' type."
}

// Read the repository layout mapping file
println ("** Read Repository Layout Mapping definition. ")
if (props.repositoryLayoutMapping) {
	File repositoryLayoutMappingFile = new File(props.repositoryLayoutMapping)
	if (!repositoryLayoutMappingFile.exists()) {
		println "!* Warning: ${props.repositoryLayoutMapping} not found. Process will exit."
		System.exit(1)
	} else {
		def yamlSlurper = new groovy.yaml.YamlSlurper()
		repositoryLayoutMapping = yamlSlurper.parse(repositoryLayoutMappingFile)
	}
}

// Process the provided memberPattern
if (props.memberPattern) {
	if (props.memberPattern.length() == 8) {
		println("Member Pattern is $props.memberPattern");
	}
	if (props.memberPattern.length() < 8) {
		print("Member Pattern $props.memberPattern is less than 8 characters. Filling with no-meaning chars... ");
		StringBuilder sb = new StringBuilder(props.memberPattern);
		while (sb.length() < 8) {
			sb.append('.');
		}
		props.memberPattern = sb.toString();
		println("Member Pattern is now $props.memberPattern");
	}
	if (props.memberPattern.length() > 8) {
		print("Member Pattern $props.memberPattern is more than 8 characters. Using only the first 8 characters... ");
		props.memberPattern = props.memberPattern.substring(0, 8);
		println("Member Pattern is now $props.memberPattern");
	}
	for (int i = 0; i < 8; i++) {
		applicationFilterInMember += (props.memberPattern[i] == 'a' ? 'a' : '.');
	}
	for (int i = 0; i < 8; i++) {
		typeFilterInMember += (props.memberPattern[i] == 't' ? 't' : '.');
	}
}

if (props.datasetPattern) {
	println("Dataset pattern is $props.datasetPattern");
	String[] qualifiers = props.datasetPattern.split("\\.");
	//	println("Qualifiers: " + qualifiers.toString());
	qualifiers = processQualifiers(qualifiers);
	//	println("Qualifiers: " + qualifiers.toString());
	for (int i = 0; i < qualifiers.size(); i++) {
		if (qualifiers[i].equals("a")) {
			applicationFilterInDSN += "a."
		} else if (qualifiers[i].equals("**")) {
			applicationFilterInDSN += "**."
		} else {
			applicationFilterInDSN += "*."
		}
	}
	applicationFilterInDSN = applicationFilterInDSN.substring(0, applicationFilterInDSN.length() - 1);
	for (int i = 0; i < qualifiers.size(); i++) {
		if (qualifiers[i].equals("t")) {
			typeFilterInDSN += "t."
		} else if (qualifiers[i].equals("**")) {
			typeFilterInDSN += "**."
		} else {
			typeFilterInDSN += "*."
		}
	}
	typeFilterInDSN = typeFilterInDSN.substring(0, typeFilterInDSN.length() - 1);
}

// datasets = new ArrayList<String>();

// if (props.datasetsList) {
// 	String[] datasetsListExploded = props.datasetsList.split(',');
// 	datasets = new ArrayList<String>(datasetsListExploded.size());
// 	for (int i = 0; i < datasetsListExploded.size(); i++) {
// 		datasets.add(datasetsListExploded[i]);
// 	}
// }
// if (props.hlq) {
// 	if (!props.hlq.endsWith(".**")) {
// 		props.filter = props.hlq + ".**";
// 	} else {
// 		props.filter = props.hlq
// 	}
// 	buildDatasetsList(datasets, props.filter);
// }

// Initialize the DBB scanner
/* ScanProperties scanProperties = new ScanProperties();
 scanProperties.setCodePage(encoding);
 Dmh5210 dmh5210 = new Dmh5210();
 dmh5210.init(scanProperties); */

println ("** Iterate through provided datasets. ")

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
				//Statistics stats = memberInfo.getStatistics(); // not used

				String extractedApplication = extractInfo(dataset, member, applicationFilterInDSN, applicationFilterInMember, true);
				// String extractedType = extractInfo(dataset, member, typeFilterInDSN, typeFilterInMember, true); // not used
				// Search for the name of the application in the ApplicationsMapping and add it
				def mappedApplication = findMappedApplicationFromNamingConvention(extractedApplication)
				println("**** '$dataset($member)' - Naming Convention: " + extractedApplication + " - Mapped Application: " + mappedApplication);
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

println "** Generating DBB Migration Mapping file. "

Iterator applicationIterator = applicationMappingToDatasetMembers.entrySet().iterator();
while (applicationIterator.hasNext()) {
	Map.Entry pair = (Map.Entry) applicationIterator.next();
	def application = pair.getKey() as String
	println "** Generating DBB Migration Mapping file. "
	generateMappingFile(pair.getKey())
	println "** Generating Application Descriptor file. "
	generateApplicationDescriptorFile(pair.getKey())
}



/*  ==== Utilities ====  */


/* parseArgs: parse arguments provided through CLI */
def parseArgs(String[] args) {
	String usage = 'extractApplications.groovy [options]'
	String header = 'options:'
	def cli = new CliBuilder(usage:usage,header:header);
	//cli.h(longOpt:'hlq', args:1, 'High-Level Qualifier of datasets to scan');
	cli.d(longOpt:'datasets', args:1, 'List of comma-separated datasets to scan');
	cli.dp(longOpt:'datasetPattern', args:1, 'Pattern for datasets names. t-type, a-application, .-no meaning');
	cli.mp(longOpt:'memberPattern', args:1, 'Pattern for member names. t-type, a-application, .-no meaning');
	cli.a(longOpt:'applicationMapping', args:1, 'Path to the Applications Mapping file')
	cli.r(longOpt:'repositoryLayoutMapping', args:1, 'Path to the Repository Layout file.')
	cli.t(longOpt:'types', args:1, 'Path to the Types file')
	def opts = cli.parse(args);
	if (!args || !opts) {
		cli.usage();
		System.exit(1);
	}
	if (!opts.h & !opts.d) {
		println("You need to provide either a HLQ or a list of comma-separated datasets to scan. Exiting.");
		System.exit(1);
	}
	if (opts.h != false && opts.d != false) {
		println("HLQ option and list of datasets options are mutually exclusive. Exiting.");
		System.exit(1);
	}

	if (opts.h) props.hlq = opts.h;
	if (opts.d) props.datasetsList = opts.d;
	if (opts.mp) props.memberPattern = opts.mp;
	if (opts.dp) props.datasetPattern = opts.dp;
	if (opts.e) {
		props.encoding = opts.e
	} else {
		props.encoding = "IBM-1047"
	}

	if(opts.r){
		props.repositoryLayoutMapping = opts.r
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

def buildDatasetsList(ArrayList<String> datasetsList, String filter) {
	CatalogSearch catalogSearch = new CatalogSearch(filter, 64000);
	catalogSearch.addFieldName("ENTNAME");
	catalogSearch.search();
	def datasetCount = 0

	catalogSearch.each { searchEntry ->
		CatalogSearch.Entry catalogEntry = (CatalogSearch.Entry) searchEntry;
		if (catalogEntry.isDatasetEntry()) {
			datasetCount++;
			CatalogSearchField field = catalogEntry.getField("ENTNAME");
			String dsn = field.getFString().trim();
			datasetsList.add(dsn);
		}
	}
}

def applyFilterOnMember(String memberName, String filter) {
	if (memberName == null)
		return "";
	StringBuilder result = new StringBuilder();
	int i = 0;
	while (i < memberName.length() && i < 8) {
		if (filter[i] != '.')
			result.append(memberName[i]);
		i++;
	}
	return result.toString();
}

def applyFilterOnDSN(String datasetName, String filter) {
	if (datasetName == null)
		return "";
	String[] datasetQualifiers = datasetName.split("\\.");
	String[] filterQualifiers = filter.split("\\.");
	StringBuilder prefixResult = new StringBuilder();
	int i = 0;
	while (i < filterQualifiers.size() && !filterQualifiers[i].equals("**")) {
		if ((filterQualifiers[i].equals("a") || filterQualifiers[i].equals("t")) && i <= datasetQualifiers.size()) {
			prefixResult.append(datasetQualifiers[i]);
			prefixResult.append("_");
		}
		i++;
	}
	if (filter.contains("**")) {
		StringBuilder suffixResult = new StringBuilder();
		i = 1;
		while (i < filterQualifiers.size() && !filterQualifiers[filterQualifiers.size() - i].equals("**")) {
			if ((filterQualifiers[filterQualifiers.size() - i].equals("a") || filterQualifiers[filterQualifiers.size() - i].equals("t")) && i <= datasetQualifiers.size()) {
				suffixResult.insert(0, "_");
				suffixResult.insert(0, datasetQualifiers[datasetQualifiers.size() - i]);
			}
			i++;
		}
		if (suffixResult.size() > 0)
			prefixResult.append(suffixResult.toString());
	}
	if (prefixResult.size() > 0)
	{
		return prefixResult.substring(0, prefixResult.size() - 1)
	} else {
		return "";
	}
}

def extractInfo(String datasetName, String memberName, String filterInDSN, String filterInMember, boolean DSNfirst) {
	if (DSNfirst) {
		return trimUnderscore(applyFilterOnDSN(datasetName, filterInDSN) + "_" + applyFilterOnMember(memberName, filterInMember));
	} else {
		return trimUnderscore(applyFilterOnMember(memberName, filterInMember) + "_" + applyFilterOnDSN(datasetName, filterInDSN));
	}
}

def String convertStringArrayToString(String[] array) {
	StringBuilder result = new StringBuilder();
	for (String string : array)
		if (string.size() != 0)
			result.append(string).append("_");
	if (result.length() > 0) {
		return result.substring(0, result.length() - 1);
	} else {
		return ""
	}
}

def trimUnderscore(String input) {
	String[] parts = input.split("_");
	return convertStringArrayToString(parts);
}

def processQualifiers(String[] qualifiers) {
	String result = ""
	boolean alreadyFound = false
	int countDoubleStar = 0
	for (int i = 0; i < qualifiers.size() ; i++) {
		if (qualifiers[i].equals("**")) {
			if (!alreadyFound) {
				result = result + "." + qualifiers[i];
				alreadyFound = true;
				countDoubleStar++;
			}
		} else {
			result = result + "." + qualifiers[i];
			alreadyFound = false;
		}
	}
	if (countDoubleStar >= 2) {
		println("*** Error: the format of the provided dataset filter is not supported (too many double asterisks). Exiting.")
		System.exit(1);
	}
	return result.substring(1, result.length()).split("\\.");
}

def generateMappingFile(String application) {
	File mappingFile = new File(application + ".mapping");
	if (!mappingFile.exists()) {
		mappingFile.createNewFile();
	}
	try {
		boolean append = true
		BufferedWriter writer = new BufferedWriter(new FileWriter(mappingFile, append))
		def datasetMembersCollection = applicationMappingToDatasetMembers.get(application)
		Iterator datasetMembersIterator = datasetMembersCollection.iterator();
		while (datasetMembersIterator.hasNext()) {
			String datasetMember = datasetMembersIterator.next();
			def (dataset,member) = getDatasetAndMember(datasetMember)
			def lastQualifier = getLastQualifier(dataset)
			repositoryConfig = repositoryLayoutMapping.repositoryLayout.find {repositoryLayout ->
				repositoryLayout.mvsMapping.datasetLastLevelQualifiers.contains(lastQualifier)
				//if (types != null && repositoryLayout.mvsMapping.types) repositoryLayout.mvsMapping.types.contains()
			}

			if (repositoryConfig != null){
				member = member + "." + repositoryConfig.fileExtension
				if (repositoryConfig.toLowerCase && repositoryConfig.toLowerCase.toBoolean()) {
					member = member.toLowerCase()
					lastQualifier = lastQualifier.toLowerCase()
				}
			} else {
				member = member + "." + lastQualifier
				member.toLowerCase()
				lastQualifier.toLowerCase()
			}

			def repositoryPath = (repositoryConfig.repositoryPath) ? repositoryConfig.repositoryPath.replaceAll('\\$application',application) : "$application/$lastQualifier"
			def pdsEncoding = (repositoryConfig.encoding) ? (repositoryConfig.encoding) : "IBM-1047"
			writer.write("$datasetMember $repositoryPath/$member pdsEncoding=$pdsEncoding\n");
		}
		writer.close();
	}
	catch (IOException e) {
		e.printStackTrace();
	}
	println("\tCreated file " + mappingFile.getAbsolutePath());
}

def generateApplicationDescriptorFile(String application) {
	File applicationDescriptorFile = new File(application + ".yaml")
	def applicationDescriptor
	if (!applicationDescriptorFile.exists()) {
		applicationDescriptor = applicationDescriptorUtils.createEmptyApplicationDescriptor()
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
	} else {
		applicationDescriptor = applicationDescriptorUtils.readApplicationDescriptor(applicationDescriptorFile)
	}

	def datasetMembersCollection = applicationMappingToDatasetMembers.get(application)
	Iterator datasetMembersIterator = datasetMembersCollection.iterator();
	while (datasetMembersIterator.hasNext()) {
		String datasetMember = datasetMembersIterator.next();
		def (dataset,member) = getDatasetAndMember(datasetMember)
		def lastQualifier = getLastQualifier(dataset)
		def repositoryConfig = repositoryLayoutMapping.repositoryLayout.find {repositoryLayout ->
			repositoryLayout.mvsMapping.datasetLastLevelQualifiers.contains(lastQualifier)
			//if (types != null && repositoryLayout.mvsMapping.types) repositoryLayout.mvsMapping.types.contains()
		}

		if (repositoryConfig != null){
			if (repositoryConfig.toLowerCase && repositoryConfig.toLowerCase.toBoolean()) {
				member = member.toLowerCase()
				lastQualifier = lastQualifier.toLowerCase()
			}
		} else {
			member.toLowerCase()
			lastQualifier.toLowerCase()
		}
		def repositoryPath = (repositoryConfig.repositoryPath) ? repositoryConfig.repositoryPath.replaceAll('\\$application',application) : "$application/$lastQualifier"
		def fileExtension = (repositoryConfig.fileExtension) ? repositoryConfig.fileExtension : "undefined"
		applicationDescriptorUtils.appendFileDefinition(applicationDescriptor, lastQualifier, lastQualifier + ".groovy", fileExtension, repositoryPath, member, getType(member), "undefined")
	}
	applicationDescriptorUtils.writeApplicationDescriptor(applicationDescriptorFile, applicationDescriptor)
	println("\tCreated file " + applicationDescriptorFile.getAbsolutePath());
}


def addDatasetMemberToApplication(String application, String datasetMember) {
	def applicationMap = applicationMappingToDatasetMembers.get(application)

	if ( applicationMap == null) {
		applicationMap = new HashSet<String>();
		applicationMappingToDatasetMembers.put(application, applicationMap);
	} else {
		applicationMap.add(datasetMember);
	}
}

def findMappedApplicationFromNamingConvention(String namingConvention) {
	if (!applicationsMapping) {
		return "UNASSIGNED"
	} else {
		def mappedApplication = applicationsMapping.applications.find { application -> application.namingConventions.contains(namingConvention) }
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