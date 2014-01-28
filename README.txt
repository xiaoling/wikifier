Version 2.0 by Xiao Cheng
***** Overview *****
This version of the Wikifier was rewritten to incorporate relational
inference, modularize components and enhance parallel processing. Various
improvements have been made for thread safety, I/O and memory efficiency.

This is the system used to obtain results in:

Relational Inference for Wikification
Xiao Cheng and Dan Roth
EMNLP 2013

If you're using this system, please cite the paper.

This system was also used as the underlying system for:
TAC English Entity-Linking 2013 for team UI_CCG

***** Directories *****

---src/
---test/
Source java files

---dist/
Contains a single executable for running the Wikifier, all dependencies
included.

---data/
All data files that the system uses. All wikification training and evaluation
data. TAC Entity Linking data is excluded due to license requirement.

---configs/
Predefined configurations used to run Wikifier. 

---configs/STAND_ALONE_NO_INFERENCE.xml
Use this configuration if you do not have Gurobi and wish to run Wikifier for
baseline performance.

---configs/STAND_ALONE_GUROBI.xml
Use this configuration if you have installed Gurobi for inference.

---lib
Non-maven dependency libraries

---pom.xml
Maven configuration

---README.txt
This file

---runSimpleTest.sh
A simple example for running the Wikifier from command line. The output files
are at data/testSample/sampleOutput/

---ablation.sh
Run ablation study using the system on all 4 wikification datasets used in
Ratinov et al. 2011, Cheng and Roth 2013. WARNING: This run requires at least
40GB RAM. You could modify the script to run the system sequentially but
it will take a very long time.

***** System Requirement *****
Memory:
In addition to the original memory heavy way of running Wikfier, we
now interface Wikipedia statistics using "Wiki Access Provider".
While memory-mapped access achieves the highest performance, you 
also have the option to run the Wikifier backed by MongoDB or other
key-value pair stores that best suits your system.

Minimum RAM: 8GB for memory-mapped access (Lucene)
Recommended RAM: 16GB

This package requires Java 1.7 or later.
***** Installation *****
1. To install the package to your local maven repository, use

mvn install

Note that the installation requires Gurobi to be installed first and 
there is a Gurobi test case for checking a properly installed Gurobi.

2. To build a single jar that contains all dependencies, use

mvn clean compile assembly:single


***** Configuration *****
In this version we switch from Java property files to XML for
better readability and automatic XML to POJO parsing.

Caveats:
1. External dependency: you need Gurobi to reproduce the result
 with relational inference. ( or use other ILP package,
but you have to implement 
LBJ2.infer.ILPSolver 
interface and add it to 
edu.illinois.cs.cogcomp.wikifier.inference.RelationSolver.SolverType)

Gurobi is free for academic uses as of today (Sep.22 2013)

2. Coreference is optional for the Wikifier. NER and Chunking are
required for sensible performance.

3. We are working on multiple key-value pair store backed Wiki Access
Provider. Feel free to extend the 
edu.illinois.cs.cogcomp.wikifier.wiki.access.WikiAccess
for your own needs ( custom trade-off between memory and speed )

***** Usage *****
1. New programmatical access

// Initialize global parameters and objects
GlobalParameters.loadConfig(configFileName)

InferenceEngine engine = new InferenceEngine(false);

// Now for each document represented as String
String text = ...;

// Define the mentions of interest by their offsets into the document
//
// These instances will contain pointers to annotation result after
// inference.
List<ReferenceInstance> instances = ReferenceInstance.loadReferenceProblem(filename).instances;

// Annotate the text with POS, Chunking and NER etc., as defined in config
TextAnnotation ta = GlobalParameters.curator.getTextAnnotation(text);

// Construct the problem for Wikifier
LinkingProblem problem = new LinkingProblem(documentId,ta,instances);

// Actually annotate the text with Wiki links
engine.annotate(problem,instances,true,true,0.0);

// Now you can collect results from the variables "instances" or "problem"

2. Command line access
Then main class was named

CommonSenseWikifier.ReferenceAssistant

and is now

edu.cs.illinois.cogcomp.wikifier.ReferenceAssistant

The arguments are unchanged, please refer to previous version's documentation

***** Contact *****

For questions regarding Wikifier, please use the mailing list:

illinois-ml-nlp-users@cs.uiuc.edu





Version 1.0 by Lev Ratinov
***** Overview: ***** 
The Wikifier is a system which identifies "important expressions" 
in text and disambiguates them to Wikipedia.
Besides annotating the input text with Wikipedia information,
the system annotates the data also with named entities, shallow parsing
and part of speech tagging.

This is the system used to obtain the results reported in:
  	Local and Global Algorithms for Disambiguation to Wikipedia
  	L. Ratinov and D. Roth and D. Downey and M. Anderson
	ACL 2011

If you're using this system, please cite the paper.

***** Requirements: *****
This is a memory - heavy package. You'll need at least 6GB RAM to run it. 
It's recommended that you run it on a machine with at least 8GB RAM.
Otherwise, all you need is Java runtime environment (a version which
supports Java templates, say 1.5 and above).

***** Compiling: *****
Unzip the package. To compile, run the script 
./cleanCompile 
which is located in the folder ./Wikifier_ACL2011_Package/Code
This will generate a jar file in the folder
./Wikifier_ACL2011_Package/Code/dist/Wikifier.jar

***** Annotating data in a command prompt: *****
1) Set up the classpath:

cpath="./dist/Wikifier.jar"
for jarlib in `find ./lib -name '*.jar'`
do
	cpath="${cpath}:${jarlib}"
done

2) Put the input files into some folder, let's assume 
we keep the files in the folder: Wikifier_ACL2011_Package/Data/TextSamples/

3) Prepare an output folder 
e.g: $mkdir Wikifier_ACL2011_Package/Data/SampleOutput/

4) Run the Wikifier. Go to the folder Wikifier_ACL2011_Package/Code
and execute the following command
$java -Xmx8g -classpath $cpath  CommonSenseWikifier.ReferenceAssistant -annotateData ../Data/TextSamples/ ../Data/SampleOutput/ false ../Config/Demo_Config_Deployed

Note that you'll see many warnings. It's ok, the reason is that Wikipedia is 
changing all the time, and while the main indices are built from 2009 dump,
I'm using 2011 redirects. Therefore, some pages end up disambiguation
pages, some pages get merged, some pages get split. I'm removing the pages
that cause trouble with the 2011 redirects. 

The general format is
CommonSenseWikifier.ReferenceAssistant -annotateData <pathToInputdata> <pathToOutputData> <generateDebugInformation> <configFile>

Note that the command above sets generateDebugInformation to false. 
You can generate a lot of debugging information, which might help 
you to understand why the system is not giving you the desired output.
However, this may result in huge output files. If you're annotating a 
very large document colleciton, you might wanna disable the debug files.
However, if you do want to generate this information, please type:
$java -Xmx8g -classpath $cpath  CommonSenseWikifier.ReferenceAssistant -annotateData ../Data/TextSamples/ ../Data/SampleOutput/ true ../Config/Demo_Config_Deployed


*****  Understanding the output: ***** 
For each input file X, the following output files would be generated:
	X.NER.tagged
	X.POS.tagged
	X.ShallowParser.tagged
	X.wikification.tagged.full.xml
	X.wikification.tagged.feature-dumps.txt
	X.wikification.tagged.flat.html
	X.wikification.warnings.log

The first three files are the named entity recognition, part of 
speech tagging and shallow parsing annotations respectively:
	X.NER.tagged
	X.POS.tagged
	X.ShallowParser.tagged
The format of the output is self-explanatory, you just need to look at it to 
understand the regular expression which generates it. Note that some of the
information stored in the files might seem unnecessary, but different tools
use different tokenization schemes, and this potentially creates problems.
Therefore I include the explicit tokenization with token offsets in each 
output file.

The file 
	X.wikification.tagged.full.xml
contains the complete and full Wikipedia annotation. 
For example, for the identified entity "Michael Jordan", we will output
the linker and the ranker scores for each candidate (you'll need to read the
paper to understand what these are), as well as displaying the "attributes"
of the Wikipedia entity, other possible disambiguations etc.

Below is a sample output segment:
<Entity>
	<EntitySurfaceForm>Michael Jordan</EntitySurfaceForm>
	<EntityTextStart>0</EntityTextStart>
	<EntityTextEnd>14</EntityTextEnd>
	<LinkerScore>1.7597052817098662</LinkerScore>
	<TopDisambiguation>
		<WikiTitle>Michael_Jordan</WikiTitle>
		<WikiTitleID>20455</WikiTitleID>
		<RankerScore>3.8139171311499234</RankerScore>
		<Attributes>	winner	champion	american	sportspeople	player	executive	medalist	owner	pick	guard	men	person</Attributes>
	</TopDisambiguation>
		<DisambiguationCandidates>
			<WikiTitle>Michael_Jordan</WikiTitle> <WikiTitleID>20455</WikiTitleID> <RankerScore>3.8139171311499234</RankerScore>
			<WikiTitle>Michael-Hakim_Jordan</WikiTitle> <WikiTitleID>3890370</WikiTitleID> <RankerScore>1.67855674154003</RankerScore>
			<WikiTitle>Michael_I._Jordan</WikiTitle> <WikiTitleID>1513732</WikiTitleID> <RankerScore>-0.2493642097373287</RankerScore>
		</DisambiguationCandidates>
</Entity>

The file
	X.wikification.tagged.flat.html
will contain a very simple output. For example, 
	<a href="http://en.wikipedia.org/wiki/Michael_Jordan">Michael Jordan</a>   
	was the best player in the history of the  
	<a href="http://en.wikipedia.org/wiki/National_Basketball_Association">NBA</a> .
Note that this file misses several key things:
a) Nested entities are not displayed. For example, "New York Opera" is an entity
which contains 2 sub-entities: "New York" and "Opera". In the full annotation both 
would be part of the ouput while in the flat annotation, none.
b) Ranker scores, attributes etc. are missing here.

Finally, the files 
	X.wikification.tagged.feature-dumps.txt
	X.wikification.warnings.log
are for debugging. these files might be huge. You need to look at them and guess what's the information saying. 

 
*****  Using the system programatically ***** 
 
You can add the files Code/dist/Wikifier.jar and lib/*jar to your 
classpath and use the  Wikifier in yor Java code.

A typical workflow is:

// read the parameters, load the models
ParametersAndGlobalVariables.loadConfig(args[args.length-1]);
ReferenceAssistant.initCategoryAttributesData(ParametersAndGlobalVariables.pathToTitleCategoryKeywordsInfo);
// annotate the input text with NER, POS, shallow pasrer.
TextAnnotation ta = ParametersAndGlobalVariables.curator.getTextAnnotation(text);
// if you don't have any specific mentions to disambiguate, define
Vector<ReferenceInstance> references = new Vector<ReferenceInstance>();
// If you have specific mentions you want to disambiguate, you have
// to load a "reference instance" data structure. This scenario can
// happen in the TAC KBP entity linking challenge, where you're asked
// to tell which entity "Michael Jordan" refers to. To understand
// the format of the input file, you'll want to take a look at the file 
// Data/WikificationACL2011Data/WikipediaSample/ProblemsTrain/2116077
// note that you can specify as little as one reference mention to
// disambiguate, but the system will use many others. 
Vector<ReferenceInstance> references=ReferenceInstance.loadReferenceProblem(pathToProblems+"/"+files[i])
// Prepare some additional data structures:
System.out.println("Constructing the problem...");
DisambiguationProblem problem=new DisambiguationProblem(pathToInputFile, ta, references);
System.out.println("Done constructing the problem");
// Prepare the inference engine - load the models. Pass false when doing inference.
// Pass true when training. Since I'm not telling here how to train, you always want
// to use false.
InferenceEngine inference=new InferenceEngine(false); 
System.out.println("Running the inference");
// run the inference. always use the parameters: , null, false, false, 0
inference.annotate(problem, null, false, false, 0);
System.out.println("Done running the inference");


Now, you have the datastructure problem annotated with the data.
To access the data, I'm providing here this code snippet:

for(int entityId=0;entityId<problem.components.size();entityId++){
	WikifiableEntity entity=problem.components.elementAt(entityId);
	res.append("<Entity>\n");
	res.append("\t<EntitySurfaceForm>"+entity.surfaceForm.replace('\n', ' ')+"</EntitySurfaceForm>\n");
	res.append("\t<EntityTextStart>"+entity.startOffsetCharsInText+"</EntityTextStart>\n");
	res.append("\t<EntityTextEnd>"+(entity.startOffsetCharsInText+entity.entityLengthChars)+"</EntityTextEnd>\n");
	res.append("\t<LinkerScore>"+entity.linkerScore+"</LinkerScore>\n");
	res.append("\t<TopDisambiguation>\n");
	String title = entity.topDisambiguation.normalizedTitleName;
	res.append("\t\t<WikiTitle>"+title+"</WikiTitle>\n");
	res.append("\t\t<WikiTitleID>"+entity.topDisambiguation.wikiData.basicTitleInfo.getTitleId()+"</WikiTitleID>\n");
	res.append("\t\t<RankerScore>"+entity.topDisambiguation.rankerScore+"</RankerScore>\n");
	res.append("\t\t<Attributes>"+getTitleCategories(title)+"</Attributes>\n");
	res.append("\t</TopDisambiguation>\n");
	res.append("\t\t<DisambiguationCandidates>\n");
	for(int i=0;i<entity.getLastPredictionLevel().size();i++) {
		DisambiguationCandidate c = entity.getLastPredictionLevel().elementAt(i);
		res.append("\t\t\t<WikiTitle>"+c.normalizedTitleName+"</WikiTitle> <WikiTitleID>"+
		c.wikiData.basicTitleInfo.getTitleId()+"</WikiTitleID> <RankerScore>"+
		c.rankerScore+"</RankerScore>\n");
	}
	res.append("\t\t</DisambiguationCandidates>\n");	
	res.append("</Entity>\n");
}

