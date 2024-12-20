# Package Build Outputs in TAR format

## Summary

This sample shows how to create a TAR file with the build outputs based on the DBB Build Report after a successful build.

The package can be uploaded to an artifact repository and used in a scripted deployment. Another area, where this script is beneficial as a sample, is to adapt this script in publishing shared copybooks to an artifact repository and to pull them into the build process. The `ArtifactRepositoryHelpers.groovy` allow you to upload and download packages from Artifactory. 
The `ArtifactRepositoryHelpers` script is a very simple implementation sufficient for a show case, **_we recommend_** to use the Artifactory publishers which are available with your CI pipeline coordinator.

This sample Groovy script to package build outputs:

- Extracts information about the build outputs from the Dependency Based Build (DBB) `BuildReport.json`. The script is able to take a single DBB build report or multiple build reports to build a cumulative package across multiple incremental builds. 
  - It processes the MVSExec, CopyToPDS and the USS_Record types
- (Optionally) generates the [Wazi Deploy application manifest](https://www.ibm.com/docs/en/developer-for-zos/16.0?topic=files-application-manifest-file) file.
- Copies outputs to a temporary directory on Unix System Services and creates a TAR file based on the temporary directory.

The support for zFS files in the packaging process is performed through the use of an `USS_RECORD` type record in the DBB BuildReport. 

## Package Build Outputs Process - High-level Processing Flow

This section provides a more detailed explanation of how the PackageBuildOutputs script works and what it does.

1. **Initialization**
   1. Read [command line parameters](#command-line-options-summary---packagebuildoutputs).
   1. Read the properties file that is passed via `--packagingPropertiesFile`.

2. **Process the DBB build report(s)**
   1. If one or multiple DBB build reports are passed to the script via either `--buildReportOrder` or `--buildReportOrderFile`, the script loops through the provided DBB build reports. If no build report is specified, the script reads DBB's `BuildReport.json` file from the pipeline work directory specified by the `--workDir` parameter. For each build report, the following steps are performed:
      1. Parse and extract build output information for records of type *ExecuteRecord* and *CopyToPDSRecord*.
      2. Remove output entries that have no `deployType` set and remove unwanted outputs such as outputs with the `deployType` equal to `ZUNIT-TESTCASE`.
   2. If processing multiple build reports, a cumulative hashmap of output records is created to be able to combine outputs from multiple pipeline builds into a single TAR file.
   	  1. The key of the map, used in the calculation of the artifacts to be deployed, is the combination of the member name and the deploy type.
   	  2. Artifacts having the same member name and the same deploy type will be present only once in the generated package, taking the last occurrence of the artifact, as found in the ordered list of Build Reports passed as parameters.

3. **(Optionally) Generate Software-Bill-Of-Material (SBOM) file**
   1. Based on the collected build outputs information, an SBOM file following the [CycloneDX](https://cyclonedx.org/) specification is created.  
   More details can be found [in this section](#software-bill-of-material-sbom-generation).

4. **(Optionally) Generate Wazi Deploy application manifest**
   1. Based on the collected build outputs information, the [Wazi Deploy application manifest](https://www.ibm.com/docs/en/developer-for-zos/16.0?topic=files-application-manifest-file) is generated and saved as wazideploy_manifest.yml.

5. **Create TAR file**
    1. It then invokes CopyToHFS API to copy the outputs from the libraries to a temporary directory on zFS. It will set the file tags based on the ZLANG setting (Note: A workaround is implemented to tag files as binary); all files require to be tagged. Please check the COPYMODE list, which maps last level qualifiers to the copymode of CopyToHFS. When specifying the option `--addExtension`, the `deployType` will be appended as the file extension to the file.
    2. It packages these load files into a TAR file, and adds the BuildReport.json and optionally other build logs from the build workspace.

6. **(Optional) Publish to Artifact Repository such as JFrog Artifactory or Sonartype Nexus**
    1. Publishes the TAR file to the artifact repository based on the given configuration using the ArtifactRepositoryHelpers script. Consider a Nexus RAW, or a Artifactory Generic as the repository type. **Please note**: The ArtifactRepositoryHelpers script is updated for DBB 2.0 and requires to run on JAVA 11. The publishing can be configured to pass in the artifact repository information as well as the path within the repository `directory/[versionName|buildLabel]/tarFileName` via the cli.

7. **(Optional) Generate IBM Concert Build manifest**
   1. Based on the collected build outputs information, the IBM Concert Build Manifest file is generated and saved as concert_build_manifest.yaml. This is a feeder file to publish build information into IBM Concert. It will only be generated if both sbom and packaging options are in effect. 

Notes: 
* The script doesn't manage the deletions of artifacts. Although they are reported in the DBB Build Reports, deletions are not handled by this script.

## Invocation samples 

### Package
```
groovyz /var/pipeline/PackageBuildOutputs/PackageBuildOutputs.groovy \ 
        --workDir /u/gitlab/gitlab-runner/zos/builds/dbb-zappbuild/BUILD-5949
```

<details>
  <summary>Console log</summary>

PackageBuildOutputs console output

```
** PackageBuildOutputs start at 20220901.025517.055
** Properties at startup:
   verbose -> false
   copyModeMap -> ["COPYBOOK": "TEXT", "COPY": "TEXT", "DBRM": "BINARY", "LOAD": "LOAD"]
   packagingPropertiesFile -> /var/pipeline/PackageBuildOutputs/packageBuildOutputs.properties
   startTime -> 20220901.025517.055
   publish -> false
   workDir -> /u/gitlab/gitlab-runner/zos/builds/dbb-zappbuild/BUILD-5949
   buildReportOrder -> [/u/gitlab/gitlab-runner/zos/builds/dbb-zappbuild/BUILD-5949/BuildReport.json]
   addExtension -> false
** Read build report data from /u/gitlab/gitlab-runner/zos/builds/dbb-zappbuild/BUILD-5949/BuildReport.json
** Removing Output Records without deployType or with deployType=ZUNIT-TESTCASE
** Files detected in /u/gitlab/gitlab-runner/zos/builds/dbb-zappbuild/BUILD-5949/BuildReport.json
   GITLAB.ZAPP.CLEAN.MAIN.LOAD(EPSMORT), MAPLOAD
   GITLAB.ZAPP.CLEAN.MAIN.LOAD(EPSMLIS), MAPLOAD
   GITLAB.ZAPP.CLEAN.MAIN.LOAD(EPSCSMRT), CICSLOAD
   GITLAB.ZAPP.CLEAN.MAIN.LOAD(EPSMPMT), LOAD
   GITLAB.ZAPP.CLEAN.MAIN.DBRM(EPSCMORT), DBRM
   GITLAB.ZAPP.CLEAN.MAIN.LOAD(EPSCMORT), CICSLOAD
   GITLAB.ZAPP.CLEAN.MAIN.LOAD(EPSCSMRD), CICSLOAD
   GITLAB.ZAPP.CLEAN.MAIN.LOAD(EPSMLIST), CICSLOAD
*** Number of build outputs to package: 8
** Copying BuildOutputs to temporary package dir.
     Copying GITLAB.ZAPP.CLEAN.MAIN.LOAD(EPSCSMRT) to /u/gitlab/gitlab-runner/zos/builds/dbb-zappbuild/BUILD-5949/tempPackageDir/GITLAB.ZAPP.CLEAN.MAIN.LOAD/EPSCSMRT with DBB Copymode LOAD
     Copying GITLAB.ZAPP.CLEAN.MAIN.LOAD(EPSCSMRD) to /u/gitlab/gitlab-runner/zos/builds/dbb-zappbuild/BUILD-5949/tempPackageDir/GITLAB.ZAPP.CLEAN.MAIN.LOAD/EPSCSMRD with DBB Copymode LOAD
     Copying GITLAB.ZAPP.CLEAN.MAIN.LOAD(EPSMORT) to /u/gitlab/gitlab-runner/zos/builds/dbb-zappbuild/BUILD-5949/tempPackageDir/GITLAB.ZAPP.CLEAN.MAIN.LOAD/EPSMORT with DBB Copymode LOAD
     Copying GITLAB.ZAPP.CLEAN.MAIN.LOAD(EPSMPMT) to /u/gitlab/gitlab-runner/zos/builds/dbb-zappbuild/BUILD-5949/tempPackageDir/GITLAB.ZAPP.CLEAN.MAIN.LOAD/EPSMPMT with DBB Copymode LOAD
     Copying GITLAB.ZAPP.CLEAN.MAIN.LOAD(EPSMLIST) to /u/gitlab/gitlab-runner/zos/builds/dbb-zappbuild/BUILD-5949/tempPackageDir/GITLAB.ZAPP.CLEAN.MAIN.LOAD/EPSMLIST with DBB Copymode LOAD
     Copying GITLAB.ZAPP.CLEAN.MAIN.LOAD(EPSMLIS) to /u/gitlab/gitlab-runner/zos/builds/dbb-zappbuild/BUILD-5949/tempPackageDir/GITLAB.ZAPP.CLEAN.MAIN.LOAD/EPSMLIS with DBB Copymode LOAD
     Copying GITLAB.ZAPP.CLEAN.MAIN.LOAD(EPSCMORT) to /u/gitlab/gitlab-runner/zos/builds/dbb-zappbuild/BUILD-5949/tempPackageDir/GITLAB.ZAPP.CLEAN.MAIN.LOAD/EPSCMORT with DBB Copymode LOAD
     Copying GITLAB.ZAPP.CLEAN.MAIN.DBRM(EPSCMORT) to /u/gitlab/gitlab-runner/zos/builds/dbb-zappbuild/BUILD-5949/tempPackageDir/GITLAB.ZAPP.CLEAN.MAIN.DBRM/EPSCMORT with DBB Copymode BINARY
** Copying /u/gitlab/gitlab-runner/zos/builds/dbb-zappbuild/BUILD-5949/BuildReport.json to temporary package dir.
** Copying /var/pipeline/PackageBuildOutputs/packageBuildOutputs.properties to temporary package dir.
** Creating tar file at /u/gitlab/gitlab-runner/zos/builds/dbb-zappbuild/BUILD-5949/build.20220614.084654.046.tar.
** Package successfully created at /u/gitlab/gitlab-runner/zos/builds/dbb-zappbuild/BUILD-5949/build.20220614.084654.046.tar.

```
</details>

### Package with capturing the deployType attribute as file extension

Adding `--addExtension` is mandatory, when you plan to use Wazi Deploy as the deployment engine.

```
+ groovyz /var/pipeline/PackageBuildOutputs/PackageBuildOutputs.groovy \
        --workDir /u/gitlab/gitlab-runner/zos/builds/dbb-zappbuild/BUILD-5949 \
        --addExtension
```

<details>
  <summary>Console log</summary>

PackageBuildOutputs console output

```

** PackageBuildOutputs start at 20220901.025846.058
** Properties at startup:
   verbose -> false
   copyModeMap -> ["COPYBOOK": "TEXT", "COPY": "TEXT", "DBRM": "BINARY", "LOAD": "LOAD"]
   packagingPropertiesFile -> /var/pipeline/PackageBuildOutputs/packageBuildOutputs.properties
   startTime -> 20220901.025846.058
   publish -> false
   workDir -> /u/gitlab/gitlab-runner/zos/builds/dbb-zappbuild/BUILD-5949
   buildReportOrder -> [/u/gitlab/gitlab-runner/zos/builds/dbb-zappbuild/BUILD-5949/BuildReport.json]
   addExtension -> true
** Read build report data from /u/gitlab/gitlab-runner/zos/builds/dbb-zappbuild/BUILD-5949/BuildReport.json
** Removing Output Records without deployType or with deployType=ZUNIT-TESTCASE
** Files detected in /u/gitlab/gitlab-runner/zos/builds/dbb-zappbuild/BUILD-5949/BuildReport.json
   GITLAB.ZAPP.CLEAN.MAIN.LOAD(EPSMORT), MAPLOAD
   GITLAB.ZAPP.CLEAN.MAIN.LOAD(EPSMLIS), MAPLOAD
   GITLAB.ZAPP.CLEAN.MAIN.LOAD(EPSCSMRT), CICSLOAD
   GITLAB.ZAPP.CLEAN.MAIN.LOAD(EPSMPMT), LOAD
   GITLAB.ZAPP.CLEAN.MAIN.DBRM(EPSCMORT), DBRM
   GITLAB.ZAPP.CLEAN.MAIN.LOAD(EPSCMORT), CICSLOAD
   GITLAB.ZAPP.CLEAN.MAIN.LOAD(EPSCSMRD), CICSLOAD
   GITLAB.ZAPP.CLEAN.MAIN.LOAD(EPSMLIST), CICSLOAD
*** Number of build outputs to package: 8
** Copying BuildOutputs to temporary package dir.
     Copying GITLAB.ZAPP.CLEAN.MAIN.LOAD(EPSCSMRT) to /u/gitlab/gitlab-runner/zos/builds/dbb-zappbuild/BUILD-5949/tempPackageDir/GITLAB.ZAPP.CLEAN.MAIN.LOAD/EPSCSMRT.CICSLOAD with DBB Copymode LOAD
     Copying GITLAB.ZAPP.CLEAN.MAIN.LOAD(EPSCSMRD) to /u/gitlab/gitlab-runner/zos/builds/dbb-zappbuild/BUILD-5949/tempPackageDir/GITLAB.ZAPP.CLEAN.MAIN.LOAD/EPSCSMRD.CICSLOAD with DBB Copymode LOAD
     Copying GITLAB.ZAPP.CLEAN.MAIN.LOAD(EPSMORT) to /u/gitlab/gitlab-runner/zos/builds/dbb-zappbuild/BUILD-5949/tempPackageDir/GITLAB.ZAPP.CLEAN.MAIN.LOAD/EPSMORT.MAPLOAD with DBB Copymode LOAD
     Copying GITLAB.ZAPP.CLEAN.MAIN.LOAD(EPSMPMT) to /u/gitlab/gitlab-runner/zos/builds/dbb-zappbuild/BUILD-5949/tempPackageDir/GITLAB.ZAPP.CLEAN.MAIN.LOAD/EPSMPMT.LOAD with DBB Copymode LOAD
     Copying GITLAB.ZAPP.CLEAN.MAIN.LOAD(EPSMLIST) to /u/gitlab/gitlab-runner/zos/builds/dbb-zappbuild/BUILD-5949/tempPackageDir/GITLAB.ZAPP.CLEAN.MAIN.LOAD/EPSMLIST.CICSLOAD with DBB Copymode LOAD
     Copying GITLAB.ZAPP.CLEAN.MAIN.LOAD(EPSMLIS) to /u/gitlab/gitlab-runner/zos/builds/dbb-zappbuild/BUILD-5949/tempPackageDir/GITLAB.ZAPP.CLEAN.MAIN.LOAD/EPSMLIS.MAPLOAD with DBB Copymode LOAD
     Copying GITLAB.ZAPP.CLEAN.MAIN.LOAD(EPSCMORT) to /u/gitlab/gitlab-runner/zos/builds/dbb-zappbuild/BUILD-5949/tempPackageDir/GITLAB.ZAPP.CLEAN.MAIN.LOAD/EPSCMORT.CICSLOAD with DBB Copymode LOAD
     Copying GITLAB.ZAPP.CLEAN.MAIN.DBRM(EPSCMORT) to /u/gitlab/gitlab-runner/zos/builds/dbb-zappbuild/BUILD-5949/tempPackageDir/GITLAB.ZAPP.CLEAN.MAIN.DBRM/EPSCMORT.DBRM with DBB Copymode BINARY
** Copying /u/gitlab/gitlab-runner/zos/builds/dbb-zappbuild/BUILD-5949/BuildReport.json to temporary package dir.
** Copying /var/pipeline/PackageBuildOutputs/packageBuildOutputs.properties to temporary package dir.
** Creating tar file at /u/gitlab/gitlab-runner/zos/builds/dbb-zappbuild/BUILD-5949/build.20220614.084654.046.tar.
** Package successfully created at /u/gitlab/gitlab-runner/zos/builds/dbb-zappbuild/BUILD-5949/build.20220614.084654.046.tar.

```
</details>

### Package to deploy with Wazi Deploy

When deploying with Wazi Deploy, and generating the Wazi Deploy Application Manifest file make the following options mandatory:

* `--generateWaziDeployAppManifest`
* `--addExtension`
* `--branch`
* `--versionName` (recommended)

```
+ groovyz /var/pipeline/PackageBuildOutputs/PackageBuildOutputs.groovy \
      --workDir /u/ado/workspace/retirementCalculator/main/build-20240215.5/logs  \
      --tarFileName retirementCalculator.tar  \
      --application retirementCalculator  \
      --addExtension \
      --branch main \
      --generateWaziDeployAppManifest  \
      --includeLogs "*.log"  \
      --versionName rel-2.0.0 \
      --verbose
```

<details>
  <summary>Console log</summary>

PackageBuildOutputs console output

```
** PackageBuildOutputs start at 20240304.015055.050
** Properties at startup:
   addExtension -> true
   application -> retirementCalculator
   branch -> main
   buildReportOrder -> [/u/ado/workspace/retirementCalculator/main/build-20240215.5/logs/BuildReport.json]
   copyModeMap -> ["COPYBOOK": "TEXT", "COPY": "TEXT", "DBRM": "BINARY", "LOAD": "LOAD", "JCL": "TEXT", "EQALANGX" : "BINARY"]
   generateWaziDeployAppManifest -> true
   includeLogs -> *.log
   packagingPropertiesFile -> /u/pipeline/git/dbb/Pipeline/PackageBuildOutputs/packageBuildOutputs.properties
   publish -> false
   startTime -> 20240304.015055.050
   tarFileName -> retirementCalculator.tar
   verbose -> true
   versionName -> rel-2.0.0
   workDir -> /u/ado/workspace/retirementCalculator/main/build-20240215.5/logs
** Read build report data from /u/ado/workspace/retirementCalculator/main/build-20240215.5/logs/BuildReport.json.
** Removing output records w/o deployType or with deployType=ZUNIT-TESTCASE
** Deployable files detected in /u/ado/workspace/retirementCalculator/main/build-20240215.5/logs/BuildReport.json
   ADO.RETIREME.MAIN.BLD.LOAD(EBUD01), LOAD
*** Number of build outputs to package: 1
** Copying build outputs to temporary package directory /u/ado/workspace/retirementCalculator/main/build-20240215.5/logs/tempPackageDir
   Copy ADO.RETIREME.MAIN.BLD.LOAD(EBUD01) to /u/ado/workspace/retirementCalculator/main/build-20240215.5/logs/tempPackageDir/ADO.RETIREME.MAIN.BLD.LOAD/EBUD01.LOAD with DBB Copymode LOAD
** Generate Wazi Deploy Application Manifest file to /u/ado/workspace/retirementCalculator/main/build-20240215.5/logs/tempPackageDir/wazideploy_manifest.yml
---
apiVersion: "wazideploy.ibm.com/v1"
kind: "ManifestState"
metadata:
  name: "retirementCalculator"
  description: "retirementCalculator"
  version: "rel-2.0.0"
  annotations:
    creationTimestamp: "20240304.015055.050"
    scmInfo:
      type: "git"
      uri: "git@ssh.dev.azure.com:v3/IBM-DAT/retirementCalculator/retirementCalculator"
      branch: "main"
      shortCommit: "cac60e95685575fab15a583d2a3966a087b58b38"
    packageInfo: null
artifacts:
- name: "EBUD01"
  description: "retirementCalculator/cobol/EBUD01.cbl"
  properties:
  - key: "githash"
    value: "cac60e95685575fab15a583d2a3966a087b58b38"
  - key: "giturl"
    value: "git@ssh.dev.azure.com:v3/IBM-DAT/retirementCalculator/retirementCalculator"
  type: "LOAD"
  hash: "cac60e95685575fab15a583d2a3966a087b58b38"

** Generate package build report order file to /u/ado/workspace/retirementCalculator/main/build-20240215.5/logs/tempPackageDir/buildReportOrder.txt
** Copy packaging properties config file to /u/ado/workspace/retirementCalculator/main/build-20240215.5/logs/tempPackageDir/packageBuildOutputs.properties
** Creating tar file at /u/ado/workspace/retirementCalculator/main/build-20240215.5/logs/retirementCalculator.tar
   Executing [sh, -c, tar cUXf /u/ado/workspace/retirementCalculator/main/build-20240215.5/logs/retirementCalculator.tar *]
** Package successfully created at /u/ado/workspace/retirementCalculator/main/build-20240215.5/logs/retirementCalculator.tar
** Adding files with file pattern *.log from /u/ado/workspace/retirementCalculator/main/build-20240215.5/logs to /u/ado/workspace/retirementCalculator/main/build-20240215.5/logs/retirementCalculator.tar
   Executing [sh, -c, tar rUXf /u/ado/workspace/retirementCalculator/main/build-20240215.5/logs/retirementCalculator.tar *.log]
** List package contents.
   Executing [sh, -c, tar tvf /u/ado/workspace/retirementCalculator/main/build-20240215.5/logs/retirementCalculator.tar]
drwxr-xr-x   1 BPXROOT  TIVUSR         0 Mar  4 13:50 ADO.RETIREME.MAIN.BLD.LOAD/
-rwxr-xr-x   1 BPXROOT  TIVUSR     49152 Mar  4 13:50 ADO.RETIREME.MAIN.BLD.LOAD/EBUD01.LOAD
-rw-r--r--   1 ADO      TIVUSR      4239 Feb 15 10:19 BuildReport.json
-rw-r--r--   1 BPXROOT  TIVUSR        97 Mar  4 13:50 buildReportOrder.txt
-rw-r--r--   1 BPXROOT  TIVUSR      1515 Mar  1 17:54 packageBuildOutputs.properties
-rw-r--r--   1 BPXROOT  TIVUSR       790 Mar  4 13:50 wazideploy_manifest.yml
-rw-r--r--   1 ADO      TIVUSR    121483 Feb 15 10:19 EBUD01.cobol.log
-rw-r--r--   1 ADO      TIVUSR       115 Feb 15 10:19 externalImpacts_MortgageApplication-feature%2FconsumeRetirementCalculatorService.log

** PackageBuildOutputs.groovy completed successfully
** Build finished
```
</details>

### Package using multiple build reports

```
+ groovyz /var/pipeline/PackageBuildOutputs/PackageBuildOutputs.groovy \
        --workDir /var/pipeline/work \
        --buildReportOrder /var/pipeline/retirementCalculator/BuildReport_1.json,/var/pipeline/retirementCalculator/BuildReport_2.json \
        --tarFileName rel-1.0.0.tar \
        --packagingPropertiesFile /var/pipeline/PackageBuildOutputs/packageBuildOutputs.properties \
        --verbose
```

<details>
  <summary>Console log</summary>

PackageBuildOutputs console output

```

** PackageBuildOutputs start at 20220901.030431.004
** Properties at startup:
   workDir -> /var/pipeline/work
   startTime -> 20220901.030431.004
   publish -> false
   verbose -> true
   copyModeMap -> ["COPYBOOK": "TEXT", "COPY": "TEXT", "DBRM": "BINARY", "LOAD": "LOAD", "COBOL":"TEXT"]
   packagingPropertiesFile -> /var/pipeline/PackageBuildOutputs/packageBuildOutputs.properties
   buildReportOrder -> [/var/pipeline/retirementCalculator/BuildReport_1.json, /var/pipeline/retirementCalculator/BuildReport_2.json]
   addExtension -> false
   tarFileName -> rel-1.0.0.tar
** Read build report data from /var/pipeline/retirementCalculator/BuildReport_1.json
** Removing Output Records without deployType or with deployType=ZUNIT-TESTCASE
**  Files detected in /var/pipeline/retirementCalculator/BuildReport_1.json
   JENKINS.ZDAT.RETIRE.LOAD(EBUD01), LOAD
   JENKINS.ZDAT.RETIRE.LOAD(EBUD03), LOAD
   JENKINS.ZDAT.RETIRE.LOAD(EBUD0RUN), LOAD
   JENKINS.ZDAT.RETIRE.LOAD(EBUD02), LOAD
** Read build report data from /var/pipeline/retirementCalculator/BuildReport_2.json
** Removing Output Records without deployType or with deployType=ZUNIT-TESTCASE
**  Files detected in /var/pipeline/retirementCalculator/BuildReport_2.json
   JENKINS.ZDAT.RETIRE.COPY(LINPUT), COPY
   JENKINS.ZDAT.RETIRE.LOAD(EBUD02), CICSLOAD
   JENKINS.ZDAT.RETIRE.COBOL(EBUD02), COBOL
*** Number of build outputs to package: 6
** Copying BuildOutputs to temporary package dir.
     Copying JENKINS.ZDAT.RETIRE.LOAD(EBUD0RUN) to /var/pipeline/work/tempPackageDir/JENKINS.ZDAT.RETIRE.LOAD/EBUD0RUN with DBB Copymode LOAD
     Copying JENKINS.ZDAT.RETIRE.LOAD(EBUD02) to /var/pipeline/work/tempPackageDir/JENKINS.ZDAT.RETIRE.LOAD/EBUD02 with DBB Copymode LOAD
     Copying JENKINS.ZDAT.RETIRE.LOAD(EBUD03) to /var/pipeline/work/tempPackageDir/JENKINS.ZDAT.RETIRE.LOAD/EBUD03 with DBB Copymode LOAD
     Copying JENKINS.ZDAT.RETIRE.LOAD(EBUD01) to /var/pipeline/work/tempPackageDir/JENKINS.ZDAT.RETIRE.LOAD/EBUD01 with DBB Copymode LOAD
     Copying JENKINS.ZDAT.RETIRE.COBOL(EBUD02) to /var/pipeline/work/tempPackageDir/JENKINS.ZDAT.RETIRE.COBOL/EBUD02 with DBB Copymode TEXT
     Copying JENKINS.ZDAT.RETIRE.COPY(LINPUT) to /var/pipeline/work/tempPackageDir/JENKINS.ZDAT.RETIRE.COPY/LINPUT with DBB Copymode TEXT
** Copying /var/pipeline/retirementCalculator/BuildReport_1.json to temporary package dir as 001_BuildReport_1.json. Executing [sh, -c, cp /var/pipeline/retirementCalculator/BuildReport_1.json /var/pipeline/work/tempPackageDir/001_BuildReport_1.json]:
** Copying /var/pipeline/retirementCalculator/BuildReport_2.json to temporary package dir as 002_BuildReport_2.json. Executing [sh, -c, cp /var/pipeline/retirementCalculator/BuildReport_2.json /var/pipeline/work/tempPackageDir/002_BuildReport_2.json]:
** Copying /var/pipeline/PackageBuildOutputs/packageBuildOutputs.properties to temporary package dir.executing [sh, -c, cp /var/pipeline/PackageBuildOutputs/packageBuildOutputs.properties /var/pipeline/work/tempPackageDir]:
** Creating tar file at /var/pipeline/work/rel-1.0.0.tar.
executing [sh, -c, tar cUXf /var/pipeline/work/rel-1.0.0.tar *]:

** Package successfully created at /var/pipeline/work/rel-1.0.0.tar.
**   List package contents.
executing [sh, -c, tar tvf /var/pipeline/work/rel-1.0.0.tar]:
-rw-------   1 BPXROOT  TIVUSR     22489 Sep  1 16:04 001_BuildReport_1.json
-rw-------   1 BPXROOT  TIVUSR     18708 Sep  1 16:04 002_BuildReport_2.json
drwxr-xr-x   1 BPXROOT  TIVUSR         0 Sep  1 16:04 JENKINS.ZDAT.RETIRE.COBOL/
-rw-r--r--   1 BPXROOT  TIVUSR      2107 Sep  1 16:04 JENKINS.ZDAT.RETIRE.COBOL/EBUD02
drwxr-xr-x   1 BPXROOT  TIVUSR         0 Sep  1 16:04 JENKINS.ZDAT.RETIRE.COPY/
-rw-r--r--   1 BPXROOT  TIVUSR       333 Sep  1 16:04 JENKINS.ZDAT.RETIRE.COPY/LINPUT
drwxr-xr-x   1 BPXROOT  TIVUSR         0 Sep  1 16:04 JENKINS.ZDAT.RETIRE.LOAD/
-rwxr-xr-x   1 BPXROOT  TIVUSR     49152 Sep  1 16:04 JENKINS.ZDAT.RETIRE.LOAD/EBUD0RUN
-rwxr-xr-x   1 BPXROOT  TIVUSR     49152 Sep  1 16:04 JENKINS.ZDAT.RETIRE.LOAD/EBUD02
-rwxr-xr-x   1 BPXROOT  TIVUSR     86016 Sep  1 16:04 JENKINS.ZDAT.RETIRE.LOAD/EBUD03
-rwxr-xr-x   1 BPXROOT  TIVUSR     49152 Sep  1 16:04 JENKINS.ZDAT.RETIRE.LOAD/EBUD01
-rw-r--r--   1 BPXROOT  TIVUSR        38 Sep  1 16:04 buildReportOrder.txt
-rw-------   1 BPXROOT  TIVUSR      1015 Sep  1 16:04 packageBuildOutputs.properties

** Build finished

```
</details>


### Package and Publish to Artifactory / Nexus

Overview of the various ways to specify the structure within the repository: 

* When no version or directory name is provided, the package will be uploaded into `(buildLabel)/(tarFileName)`
* Specifying `version (-v)` uploads the package into  `(version)/(tarFileName)`
* Specifying `directory (-ad)` uploads the package into `(directory)/(buildLabel)/(tarFileName)`
* Specifying `version (-v)` and `directory (-ad)` uploads the package into `(directory)/(versionName)/(tarFileName)`

The password for the artifact repository can also represent the APIKey. It is recommended to store that inside the secret store of your pipeline orchestrator.

```
groovyz /var/jenkins/pipeline/PackageBuildOutputs.groovy \
        --workDir /var/jenkins/workspace/App-EPSM/outputs/build.20221206.032531.025 \
        -p \
        -aprop appArtifactRepository.properties \
        -au http://10.3.20.231:8081/artifactory \
        -ar example-repo-local \
        -aU admin \
        -aP xxxxxxxxxxx
```

<details>
  <summary>Console log</summary>

PackageBuildOutputs console output

```

PackageBuildOutputs.groovy --workDir /var/jenkins/workspace/App-EPSM/outputs/build.20221206.032531.025 -p -aprop appArtifactRepository.properties -au http://10.3.20.96:28081/repository -ar testMD -aU admin -aP nexusadmin
** PackageBuildOutputs start at 20221207.050641.006
** Properties at startup:
   addExtension -> false
   artifactRepository.directory ->
   artifactRepository.password -> xxxxxx
   artifactRepository.repo -> testMD
   artifactRepository.url -> http://10.3.20.96:28081/repository
   artifactRepository.user -> admin
   buildReportOrder -> [/var/jenkins/workspace/App-EPSM/outputs/build.20221206.032531.025/BuildReport.json]
   copyModeMap -> ["COPYBOOK": "TEXT", "COPY": "TEXT", "DBRM": "BINARY", "LOAD": "LOAD", "JCL": "TEXT"]
   packagingPropertiesFile -> /u/ibmuser/groovy/PublishingScript/packageBuildOutputs.properties
   publish -> true
   startTime -> 20221207.050641.006
   verbose -> false
   workDir -> /var/jenkins/workspace/App-EPSM/outputs/build.20221206.032531.025
** Read build report data from /var/jenkins/workspace/App-EPSM/outputs/build.20221206.032531.025/BuildReport.json
** Removing Output Records without deployType or with deployType=ZUNIT-TESTCASE
** Files detected in /var/jenkins/workspace/App-EPSM/outputs/build.20221206.032531.025/BuildReport.json
   GITLAB.PROG.EPSM.LOAD(EPSMLIS), LOAD
   GITLAB.PROG.EPSM.LOAD(EPSMPMT), LOAD
   GITLAB.PROG.EPSM.LOAD(EPSMLIST), LOAD
*** Number of build outputs to package: 3
** Copying BuildOutputs to temporary package dir.
     Copying GITLAB.PROG.EPSM.LOAD(EPSMLIS) to /var/jenkins/workspace/App-EPSM/outputs/build.20221206.032531.025/tempPackageDir/GITLAB.PROG.EPSM.LOAD/EPSMLIS with DBB Copymode LOAD
     Copying GITLAB.PROG.EPSM.LOAD(EPSMPMT) to /var/jenkins/workspace/App-EPSM/outputs/build.20221206.032531.025/tempPackageDir/GITLAB.PROG.EPSM.LOAD/EPSMPMT with DBB Copymode LOAD
     Copying GITLAB.PROG.EPSM.LOAD(EPSMLIST) to /var/jenkins/workspace/App-EPSM/outputs/build.20221206.032531.025/tempPackageDir/GITLAB.PROG.EPSM.LOAD/EPSMLIST with DBB Copymode LOAD
** Copying /var/jenkins/workspace/App-EPSM/outputs/build.20221206.032531.025/BuildReport.json to temporary package dir as BuildReport.json.
** Copying /u/ibmuser/groovy/PublishingScript/packageBuildOutputs.properties to temporary package dir.
** Creating tar file at /var/jenkins/workspace/App-EPSM/outputs/build.20221206.032531.025/build.20221206.032531.025.tar.
** Package successfully created at /var/jenkins/workspace/App-EPSM/outputs/build.20221206.032531.025/build.20221206.032531.025.tar.
** Uploading package to Artifact Repository http://10.3.20.96:28081/repository/testMD/build.20221206.032531.025/build.20221206.032531.025.tar.
** ArtifactRepositoryHelper started for upload of /var/jenkins/workspace/App-EPSM/outputs/build.20221206.032531.025/build.20221206.032531.025.tar to http://10.3.20.96:28081/repository/testMD/build.20221206.032531.025/build.20221206.032531.025.tar
** Uploading /var/jenkins/workspace/App-EPSM/outputs/build.20221206.032531.025/build.20221206.032531.025.tar to http://10.3.20.96:28081/repository/testMD/build.20221206.032531.025/build.20221206.032531.025.tar
** Upload completed
** Build finished
```
</details>

## Configuration through properties files

Limiting the cli options can be a desired strategy to maintain files under version control or implement central control mechanisms to enforce standards. 

The PackageBuildOutputs script can be configured using the [packageBuildOutputs.properties](packageBuildOutputs.properties) file.
In any case, the `packageBuildOutputs.properties` is loaded, because it at least specifies the `copyModeMap`. 

The properties file allows you to control the following settings:


Parameter | Description
---------- | ----------------------------------------------------------------------------------------
`copyModeMap` | configures the mapping of last level qualifier and the necessary copymode from PDS to USS.
`deployTypesFilter` | to limit the scope of DBB deployTypes that are added to the package
`addExtension` | Boolean flag to append the DBB deployType as the file extension to provide information about the deployment 
`generateWaziDeployAppManifest` |  Boolean flag to indicate if the Wazi Deploy Application Manifest file should be created
`includeLogs` | List of file patterns from the workDir, that should be addded to the package, such as build logs
`fileEncoding` | File encoding for files generated as part of the process 

Additionally, the ArtifactRepositoryHelpers accept a properties like [appArtifactRepository.properties](appArtifactRepository.properties) file to define:

Parameter | Description
---------- | ----------------------------------------------------------------------------------------
`artifactRepository.url` | URL to the Artifact server, e.q. https://your-artifactory-url/artifactory
`artifactRepository.repo` | Artifact repository name to store the build, e.q. sys-zos-application-local
`artifactRepository.directory` | Artifact repository directory to distinguish between prelimiary versions and release candidates, e.q. rel-1.0.0
`artifactRepository.user` | User name
`artifactRepository.password` | Password, Personal Access Token
`artifactRepository.httpClientVersion` | HttpClient.Version setting to override the HTTP protocol version (Optional)


## Command Line Options Summary - PackageBuildOutputs

```
  usage: PackageBuildOutputs.groovy [options]
 
  -w,--workDir <dir>                             Absolute path to the DBB build
                                                 output directory
 
  -properties,--packagingPropertiesFile <file>   Absolute path of a property file
                                                 containing application specific
                                                 packaging details. 
                                                                                                                                          
  Optional:

  -boFile,--buildReportOrderFile <file>          Name of the buildReportOrder file, used to specify
                                                 buildReport.json files to be processed.

  -bO,--buildReportOrder <buildReports>          Additional build reports to be processed. If -boFile and -bO 
                                                 are used together, the build reports from -bO are 
                                                 appended to the build reports from -boFile.

  -t,--tarFileName <filename>                    Name of the package tar file.
                                                 (Optional unless using --buildReportOrder or --buildReportOrderFile)

  -d,--deployTypes <deployTypes>                 Comma-seperated list of deployTypes
                                                 to filter on the scope of the tar
                                                 file. (Optional)

  -verb,--verbose                                Flag to provide more log output. (Optional)

  -il,--includeLogs                              Comma-separated list of files/patterns
                                                 from the USS build workspace

  -ae,--addExtension                             Flag to add the deploy type extension to the member
                                                 in the package tar file. (Optional)                                                                                              

  -wd,--generateWaziDeployAppManifest            Flag indicating to generate and add the Wazi Deploy Application Manifest file
  
  -s,--sbom                                      Flag to control the generation of SBOM
  
  -sa,--sbomAuthor <sbomAuthor>                  Author of the SBOM, in form "Name <email>"

  -h,--help                                      Prints this message

  Optional Artifact Repsository upload opts:
 
  -p,--publish
                     Flag to indicate package upload to
                     the provided Artifactory server.
                     (Optional)
 
  -v,--versionName <versionName>                 
                     Name of the Artifactory version. (Optional)

  -ad,--artifactRepositoryDirectory <repoDirectory>
                     Directory path in the repository to store the build . (Optional)
 
  -aprop,--artifactRepositoryPropertyFile <propertyFile>
                     Path of a property file containing application specific artifact
                     repository details. (Optional)

  -ar,--artifactRepositoryName <repoName>
                     Artifact repository name to store the build. (Optional)
 
  -au,--artifactRepositoryUrl <url>
                     URL to the Artifact repository server. (Optional)

  -aU,--artifactRepositoryUser <user>
                     User to connect to the Artifact repository server. (Optional)
  
  -aP,--artifactRepositoryPassword <password>
                     Password to connect to the Artifact repository server. (Optional)
  
  -ah,--artifactRepositoryHttpClientProtocolVersion <protocolVersion>
                     HttpClient.Version setting to override the HTTP protocol version. (Optional)
```

## Command Line Options Summary - ArtifactRepositoryHelpers

```
usage: ArtifactRepositoryHelpers.groovy [options]

 -fD,--fileToDownload <arg>   The full path of the file to download
 -fU,--fileToUpload <arg>     The full path of the file to upload
 -h,--help                    Prints this message
 -P,--password <arg>          Artifactory password
 -u,--url <arg>               Artifactory file uri location
 -U,--user <arg>              Artifactory user id
 -v,--verbose                 Flag to turn on script trace
 -ht,--httpClientVersion      Http Client Protocol Version (Optional)
```


## Software-Bill-Of-Material (SBOM) generation

This `PackageBuildOutputs.groovy` script is able to generate an SBOM file based on the information contained in the DBB Build Report.
It will process the different records of the DBB Build Report, to collect each deployable artifact's required properties and dependencies when documenting a valid SBOM file.

This sample script is using the [CycloneDX](https://cyclonedx.org/) specification to document the necessary elements of the SBOM file.
The output file is written in JSON, following the [Cyclone DX 1.5](https://cyclonedx.org/docs/1.5/json/) schema.

To implement the correct objects when generating an SBOM file, the script uses the [CycloneDX Java library](https://github.com/CycloneDX/cyclonedx-core-java).
This library makes use of other libraries like [Jackson](https://github.com/FasterXML/jackson), which also comes with dependencies.
The list of required libraries are:

- cyclonedx-core-java (8.x version)
- jackson-core (tested with 2.16.1 version)
- jackson-annotations (tested with 2.16.1 version)
- jackson-databind (tested with 2.16.1 version)
- jackson-dataformat-xml (tested with 2.16.1 version)
- json-schema-validator (tested with 1.2.0 version)
- packageurl-java (tested with 1.5.0 version)         

These libraries (available as JAR files) must be made available on z/OS Unix System Services.
The easiest way it to download the JAR packages manually (or through maven) and upload them to a specific location on z/OS, where the script can use them.

Also, these libraries must be available in the Java CLASSPATH.
A convenient way is to specify the paths to these libraries through the `-cp` flag when invoking DBB.

To enable the generation of the SBOM file, the `-s/--sbom` flag must be passed.
It is recommended to specify an author for the SBOM, even when generated through the pipeline, through the`-sa/--sbomAuthor` parameter.
For instance, it could be the Release Manager or the Application Owner of the application for which the pipeline is running. 

As an example, you can invoke the SBOM generation with the following command:

~~~~
/usr/lpp/dbb/v2r0/bin/groovyz -cp /u/mdalbin/SBOM/cyclonedx-core-java-8.0.3.jar:/u/mdalbin/SBOM/jackson-annotations-2.16.1.jar:/u/mdalbin/SBOM/jackson-core-2.16.1.jar:/u/mdalbin/SBOM/jackson-databind-2.16.1.jar:/u/mdalbin/SBOM/jackson-dataformat-xml-2.16.1.jar:/u/mdalbin/SBOM/json-schema-validator-1.2.0.jar:/u/mdalbin/SBOM/packageurl-java-1.5.0.jar /u/mdalbin/SBOM/dbb/Pipeline/PackageBuildOutputs/PackageBuildOutputsWithSBOM.groovy --workDir /u/ado/workspace/MortgageApplication/feature/consumeRetirementCalculatorServiceImpacts/build-20240312.1/logs --tarFileName MortgageApplication.tar --addExtension -s -sa "David Gilmour <david.gilmour@pinkfloyd.com>"
~~~~ 

By default, the SBOM file is generated in the `tempPackageDir` and named `<buildnumber>_sbom.json`.
This way, it is automatically packaged in the TAR file that is created by the script, ensuring the package and its content are not tampered and correctly documented. 

## IBM Concert Build manifest generation

This `PackageBuildOutputs.groovy` script is able to generate an IBM Concert Build manifest based on the information contained in the DBB Build Report and the published package information. The output is a YAML file that adheres to IBM Concert Build specification YAML format. The generation of the CycloneDX SBOM is a pre-requisite as the IBM Concert Build manifest will reference the CycloneDX SBOM file for detailed information about the build outputs.

To enable the generation of the IBM Concert Build manifest, the `-ic/--generateConcertBuildManifest` flag must be passed.

As an example, you can invoke the generation of an IBM Concert Build manifest with the following command:

~~~~
/usr/lpp/dbb/v2r0/bin/groovyz -cp /u/mdalbin/SBOM/cyclonedx-core-java-8.0.3.jar:/u/mdalbin/SBOM/jackson-annotations-2.16.1.jar:/u/mdalbin/SBOM/jackson-core-2.16.1.jar:/u/mdalbin/SBOM/jackson-databind-2.16.1.jar:/u/mdalbin/SBOM/jackson-dataformat-xml-2.16.1.jar:/u/mdalbin/SBOM/json-schema-validator-1.2.0.jar:/u/mdalbin/SBOM/packageurl-java-1.5.0.jar /u/mdalbin/SBOM/dbb/Pipeline/PackageBuildOutputs/PackageBuildOutputsWithSBOM.groovy --workDir /u/ado/workspace/MortgageApplication/feature/consumeRetirementCalculatorServiceImpacts/build-20240312.1/logs --tarFileName MortgageApplication.tar --addExtension -s -sa "David Gilmour <david.gilmour@pinkfloyd.com>" -ic
~~~~ 

By default, the IBM Concert Build manifest file is generated in the `tempPackageDir` and named `concert_build_manifest.yaml`. 


## Useful reference material

#### TAR on USS 

This sample implementation makes use of tar on USS. Please see IBM Docs for further details on [tar](https://www.ibm.com/docs/en/zos/2.4.0?topic=scd-tar-manipulate-tar-archive-files-copy-back-up-file)

The implementation preserves the file tags for further processing.

```
tar -tvf justloads.jar -L T
USTAR Version 00
                    drwxr-xr-x   1 BPXROOT  DB2USR         0 Jul 28 13:47 JENKINS.DBB.SAMP.BUILD.LOAD/
b binary      T=off -rwxr-xr-x   1 BPXROOT  DB2USR     32768 Jul 28 13:47 JENKINS.DBB.SAMP.BUILD.LOAD/EPSMPMT
t UTF-8       T=on  -rw-r--r--   1 BPXROOT  DB2USR     18326 Jul 28 13:47 BuildReport.json
```

#### Jenkins Integration  

As mentioned in the introductions of this sample, we recommend to use the existing plugins of your binary artifact repository manager. You find useful material at:
- [Documentation Jenkins Artifactory Plug-in](https://www.jfrog.com/confluence/display/JFROG/Jenkins+Artifactory+Plug-in)
- [Configuring Jenkins Artifactory Plug-in](https://www.jfrog.com/confluence/display/JFROG/Configuring+Jenkins+Artifactory+Plug-in)
- [Jenkins Sample provided by JFrog](https://github.com/jfrog/project-examples/tree/master/jenkins-examples/pipeline-examples)


Below is a snippet to use PackageBuildOutputs.groovy along with the Jenkins Artifactory plugins, which provides the Build Info in Artifactory

```
stage("Package & Upload to Artifactory") {
		sh "${groovyz}  $pipelineScripts/PackageBuildOutputs.groovy --workDir ${WORKSPACE}/BUILD-${BUILD_NUMBER}"	

        artifactoryServer.credentialsId = artifactoryCredentialsId
        def buildInfo = Artifactory.newBuildInfo()
        buildInfo.name = buildName // tbd - for example {application-branch}

        // Upload DBB build outputs to Artifactory
        artifactoryServer.upload buildInfo: buildInfo, spec:
        """{
                "files": [
                    {
                        "pattern": "${WORKSPACE}/BUILD-${BUILD_NUMBER}/*.tar",
                        "target": "${artifactoryRepository}/${buildName}/${env.BUILD_NUMBER}/"
                    }
                ]
            }"""

        // Publish the build
        artifactoryServer.publishBuildInfo buildInfo      
	}  
```

 
