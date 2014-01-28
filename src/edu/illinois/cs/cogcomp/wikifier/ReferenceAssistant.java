// $codepro.audit.disable useCharAtRatherThanStartsWith
package edu.illinois.cs.cogcomp.wikifier;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.StringEscapeUtils;

import com.google.common.collect.Lists;

import edu.illinois.cs.cogcomp.edison.sentences.Constituent;
import edu.illinois.cs.cogcomp.edison.sentences.TextAnnotation;
import edu.illinois.cs.cogcomp.edison.sentences.View;
import edu.illinois.cs.cogcomp.edison.sentences.ViewNames;
import edu.illinois.cs.cogcomp.wikifier.common.GlobalParameters;
import edu.illinois.cs.cogcomp.wikifier.evaluation.Evaluator;
import edu.illinois.cs.cogcomp.wikifier.evaluation.ProblemEvaluationResult;
import edu.illinois.cs.cogcomp.wikifier.inference.InferenceEngine;
import edu.illinois.cs.cogcomp.wikifier.models.LinkingProblem;
import edu.illinois.cs.cogcomp.wikifier.models.Mention;
import edu.illinois.cs.cogcomp.wikifier.models.ReferenceInstance;
import edu.illinois.cs.cogcomp.wikifier.models.WikiCandidate;
import edu.illinois.cs.cogcomp.wikifier.utils.io.FileFilters;
import edu.illinois.cs.cogcomp.wikifier.utils.io.InFile;
import edu.illinois.cs.cogcomp.wikifier.utils.io.OutFile;
/**
 * Main class for using the Wikifier through command line
 * @author ratinov2
 * @modified cheng88
 *
 */
public class ReferenceAssistant {

	public static void main(String[] args) throws Exception{
		System.out.println("Params:"+Arrays.toString(args));
		System.out.println("Usage: either");
		System.out.println("\t$java ReferenceAssistant -trainSvmModelsOnly <pathToConfigFile>");
		System.out.println("or");
		System.out.println("\t$java ReferenceAssistant -buildTrainingDataAndTrain <pathToProblems> <pathToRawTexts> <pathToConfigFile>");
		System.out.println("or");
		System.out.println("\t$java ReferenceAssistant -annotateData <inputPath> <outputPath> <generateFeatureDumps>  <pathToConfigFile> ");
		System.out.println("or");
		System.out.println("\t$java ReferenceAssistant -referenceAssistant <pathToProblemFileOrFolder> <pathToRawTextFilesFolder> <pathToExplanations>  <pathToConfigFile> ");
		
        if (args.length <= 1)
            return;
		GlobalParameters.loadConfig(args[args.length-1]);

		if(args[0].equalsIgnoreCase("-annotateData")){
            String inPath = args[1];
            String outPath = args[2];
            boolean generateFeatureDumps = Boolean.parseBoolean(args[3]);
            File f = new File(inPath);
            List<String> texts = new ArrayList<String>();
            List<String> infileNames = new ArrayList<String>();
            List<String> infilePaths = new ArrayList<String>();
            List<String> outputPaths = new ArrayList<String>();

			// Load problems
			if(f.isDirectory()){
                for (String file : f.list(FileFilters.viewableFiles)) {
                    texts.add(InFile.readFileText(inPath + "/" + file));
                    infilePaths.add(inPath + "/" + file);
                    infileNames.add(file);
                    outputPaths.add(outPath + "/" + file);
                }
			}
			else{
				texts.add(InFile.readFileText(inPath));
				infilePaths.add(inPath);
				infileNames.add(f.getName());
				outputPaths.add(outPath + f.getName());
			}
			
			//
            InferenceEngine inference = new InferenceEngine(false);
            for (int i = 0; i < texts.size(); i++)
				if(!StringUtils.containsOnly(texts.get(i), " \n\r")) {
					System.out.println("Processing the file : "+infilePaths.get(i));
					System.out.println("Constructing the problem...");
					TextAnnotation ta = GlobalParameters.curator.getTextAnnotation(texts.get(i)); 
					LinkingProblem problem=new LinkingProblem(infileNames.get(i), 
							ta, new ArrayList<ReferenceInstance>());
					System.out.println("Done constructing the problem; running the inference");
					inference.annotate(problem, null, false, false, 0);
					System.out.println("Done  running the inference");

					System.out.println("Saving the simplest-form no nested entities Wikification output in html format");
					String wikificationString = problem.wikificationString(false);
					OutFile out=new OutFile(outputPaths.get(i)+".wikification.tagged.flat.html");
					out.println(wikificationString);
					out.close();
					System.out.println("Saving the full annotation in XML");
					out=new OutFile(outputPaths.get(i)+".wikification.tagged.full.xml");
					out.println(getWikifierOutput(problem));
					out.close();

					System.out.println("Saving the NER output");
					out = new OutFile(outputPaths.get(i)+".NER.tagged");
					out.println("<InputFilename>"+problem.sourceFilename+"</InputFilename>");
					out.println("<Text>"+problem.text+"</Text>");
					out.println("<Annotation>\n"+view2Str(ta,  ta.getView(ViewNames.NER))+"</Annotation>");
					out.close();
                    if (generateFeatureDumps) {
                        System.out.println("Saving the feature dumps");
                        out = new OutFile(outputPaths.get(i) + ".wikification.tagged.feature-dumps.txt");
                        out.println(getExplanation(problem, false));
                        out.close();
                    }
//					System.out.println("Saving the POS output");
//					out = new OutFile(outputPaths.get(i)+".POS.tagged");
//					out.println("<InputFilename>"+problem.sourceFilename+"</InputFilename>");
//					out.println("<Text>"+problem.text+"</Text>");
//					out.println("<Annotation>\n"+view2Str(ta,  ta.getView(ViewNames.POS))+"</Annotation>");
//					out.close();
//					System.out.println("Saving the ShallowParser output");
//					out = new OutFile(outputPaths.get(i)+".ShallowParser.tagged");
//					out.println("<InputFilename>"+problem.sourceFilename+"</InputFilename>");
//					out.println("<Text>"+problem.text+"</Text>");
//					out.println("<Annotation>\n"+view2Str(ta,  ta.getView(ViewNames.SHALLOW_PARSE))+"</Annotation>");
//					out.close();
				}
		}
		if(args[0].equalsIgnoreCase("-referenceAssistant")){
			String pathToProblems=args[1];
			String rawFilesPath=args[2];
			String outPath=args[3];
			System.out.println("\n\n********************************************\n\n" +
					"Solving problems with user-specified entities." +
					"\n\t pathToProblems="+pathToProblems+
					"\n\t rawFilesPath="+rawFilesPath+
					"\n\t outPath="+outPath+
					"\n\n********************************************\n\n");
			File f=new File(pathToProblems);
            List<List<ReferenceInstance>> problems = Lists.newArrayList();
            List<String> infiles = Lists.newArrayList();
            List<String> outputReferenceInstanceFiles = Lists.newArrayList();
            List<String> outputExplanations = Lists.newArrayList();
            List<String> outputHtmlFiles = Lists.newArrayList();
            List<String> outputXmlFiles = Lists.newArrayList();
			if(f.isDirectory()){
				String[] files=f.list();
				for(int i=0;i<files.length;i++)
					if(!files[i].startsWith(".")) {
						problems.add(ReferenceInstance.loadReferenceProblem(pathToProblems+"/"+files[i]));
						//System.out.println("Problem size="+ReferenceInstance.loadReferenceProblem(inPath+"/"+files[i]).size());
						infiles.add(pathToProblems+"/"+files[i]);
						outputReferenceInstanceFiles.add(outPath+"/"+files[i]+".referenceInstance.tagged");
						outputExplanations.add(outPath+"/"+files[i]+".annotationDetails.tagged");
						outputHtmlFiles.add(outPath+"/"+files[i]+".tagged.html");
						outputXmlFiles.add(outPath+"/"+files[i]+".tagged.full.xml");
					}
			}
			else{
				problems.add(ReferenceInstance.loadReferenceProblem(pathToProblems));
				outputReferenceInstanceFiles.add(outPath+".referenceInstance.tagged");
				outputExplanations.add(outPath+".annotationDetails.tagged");
				outputHtmlFiles.add(outPath+".tagged.html");
				
				
				outputXmlFiles.add(outPath+".tagged.full.xml");
				
				infiles.add(pathToProblems);
			}
			InferenceEngine inference=new InferenceEngine(false);
			inference.refreshPerformanceCounters();
			/*
			 *  This part does the precision recall curve
			 *
			double[] linkerThresholdScores = { 0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0};
			for(int ltsi=0;ltsi<linkerThresholdScores.length;ltsi++) {
				inference.refreshPerformanceCounters();
				for(int i=0;i<problems.size();i++) {
					if(problems.get(i).size()>0) {
						String text=InFile.readFileText(rawFilesPath+"/"+problems.get(i).get(0).rawTextFilename);
						List<ReferenceInstance> goldProblem=problems.get(i);
						DisambiguationProblem problem = new DisambiguationProblem(problems.get(i).get(0).rawTextFilename, text, goldProblem);
						inference.annotate(problem, goldProblem, true, true, linkerThresholdScores[ltsi]);
					}
				}
				System.out.println("Performance with linking threshold of : "+linkerThresholdScores[ltsi]);
				inference.printPerformanceByInferenceLevel();
			}*/
			ProblemEvaluationResult performance = new ProblemEvaluationResult();
			inference.refreshPerformanceCounters();
			long totalInferenceTime = 0;
			for(int i=0;i<problems.size();i++){				
				if(problems.get(i).size()>0){
					System.out.println("Processing the reference problem : "+infiles.get(i));
					String text=InFile.readFileText(rawFilesPath+"/"+problems.get(i).get(0).rawTextFilename);
					if(text.replace(" ", "").replace("\n", "").replace("\r", "").length()>0){
						List<ReferenceInstance> goldProblem=problems.get(i);
						LinkingProblem problem = new LinkingProblem(problems.get(i).get(0).rawTextFilename, 
								GlobalParameters.curator.getTextAnnotation(text), goldProblem);
						long lastTime = System.currentTimeMillis();
						inference.annotate(problem, goldProblem, true, false, 0);
						totalInferenceTime += System.currentTimeMillis() - lastTime;
						List<ReferenceInstance> solution=new ArrayList<ReferenceInstance>();
						for(int j=0;j<goldProblem.size();j++){
							ReferenceInstance output=new ReferenceInstance();
							Mention disambiguation=goldProblem.get(j).getAssignedEntity();
							String link="*null*";
							if(disambiguation!=null && disambiguation.finalCandidate!=null&&disambiguation.finalCandidate.titleName!=null)
								link="http://en.wikipedia.org/wiki/"+disambiguation.finalCandidate.titleName;
							output.annotations.add(link);
							output.annotatorIds.add("CogcompWikifier");
							output.charLength=goldProblem.get(j).charLength;
							output.charStart=goldProblem.get(j).charStart;
							output.chosenAnnotation=link;
							output.surfaceForm=goldProblem.get(j).surfaceForm;
							output.rawTextFilename=goldProblem.get(j).rawTextFilename;
							solution.add(output);
						}
						Evaluator.markWikificationCorrectness(problem, goldProblem, performance, "Final System Output:");
						ReferenceInstance.saveReferenceProblem(solution, outputReferenceInstanceFiles.get(i), problems.get(i).get(0).rawTextFilename);
						String wikificationString = problem.wikificationString(false);
						OutFile out=new OutFile(outputHtmlFiles.get(i));
						out.println("<html> <body> \n"+wikificationString+"\n</html> </body>");
						out.close();
						String res= "<ANNOTATION>\n"+wikificationString+"\n</ANNOTATION>\n"+
										problem.mappedEntitiesAsString()+"\n";
						// if(provideDecisionLogsAndErrorAnalysis) {
						//	res+="Warnings and errors:\t"+ParametersAndGlobalVariables.warningsLog.toString();
							res+="Output Explanation:\n";
							res+=getExplanation(problem,false);
						// }
						out=new OutFile(outputExplanations.get(i));
						out.println(res);
						out.close();
						
						System.out.println("Saving the full wikification output to "+outputXmlFiles.get(i));
						out = new OutFile(outputXmlFiles.get(i));
						out.println(getWikifierOutput(problem));
						out.close();
						
					} else{
						System.out.println("Processing the reference problem : "+infiles.get(i));
						System.out.println("Empty problem - no text!!!");						
					}
				}
				else{
					System.out.println("Processing the reference problem : "+infiles.get(i));
					System.out.println("Empty problem - no reference instances!!!");
				}
			}
			System.out.println("************  Performance by inference level: ****************");
			inference.printPerformanceByInferenceLevel();
			System.out.println("************  Final Performance  **************");
			performance.printPerformance();
			System.out.println("************  Running time breakdown  **************");
			System.out.println("- Time constructing the disambiguation candidates "+ LinkingProblem.timeConsumedConstructingProblems);
			System.out.println("- Feature extraction times:");
			for(int i=0;i<inference.featureExtractors.size();i++)
				System.out.println("\t"+inference.featureExtractors.get(i).extractorName+" - "+inference.featureExtractors.get(i).featureExtractionTime);
			System.out.println("- Ranking time: "+InferenceEngine.timeConsumedOnRanking);
			System.out.println("- Linking time: "+InferenceEngine.timeConsumedOnLinking);
			System.out.println("Total Inference time: "+totalInferenceTime+" milliseconds");
		}	
	}
	
	public static String getScoresString(WikiCandidate c) throws Exception{
	    StringBuilder res=new StringBuilder();
		res.append("["+c.getTid()+"("+c.titleName+")"+"/"+c.rankerScore+"] - [");
		double[] features=c.getRankerFeatures();
		res.append(features[0]);
		for(int i=1;i<features.length;i++)
			res.append(","+features[i]);
		res.append(']');
		return res.toString();
	}
	
    public static String getTitleCategories(String title) {
        return StringUtils.join(GlobalParameters.getCategories(title),'\t');
    }
			
	public static String getWikifierOutput(LinkingProblem problem) {
	    StringBuilder res = new StringBuilder();
		res.append("<WikifierOutput>\n");
		res.append("<InputFilename>\n"+problem.sourceFilename+"\n</InputFilename>\n");
		res.append("<InputText>\n"+StringEscapeUtils.escapeXml(problem.text)+"\n</InputText>\n");
		res.append("<WikifiedEntities>\n");
		for(Mention entity : problem.components){
			if(entity.topCandidate == null)
				continue;
			res.append("<Entity>\n");
			String escapedSurface = StringEscapeUtils.escapeXml(entity.surfaceForm.replace('\n', ' '));
			res.append("\t<EntitySurfaceForm>"+escapedSurface+"</EntitySurfaceForm>\n");
			res.append("\t<EntityTextStart>"+entity.charStart+"</EntityTextStart>\n");
			res.append("\t<EntityTextEnd>"+(entity.charStart+entity.charLength)+"</EntityTextEnd>\n");
			res.append("\t<LinkerScore>"+entity.linkerScore+"</LinkerScore>\n");
			res.append("\t<TopDisambiguation>\n");
			String title = entity.topCandidate.titleName;
			res.append("\t\t<WikiTitle>"+StringEscapeUtils.escapeXml(title)+"</WikiTitle>\n");
			res.append("\t\t<WikiTitleID>"+entity.topCandidate.getTid()+"</WikiTitleID>\n");
			res.append("\t\t<RankerScore>"+entity.topCandidate.rankerScore+"</RankerScore>\n");
			res.append("\t\t<Attributes>"+getTitleCategories(title)+"</Attributes>\n");
			res.append("\t</TopDisambiguation>\n");

		
			res.append("\t\t<DisambiguationCandidates>\n");
			for(int i=0;i<entity.candidates.get(0).size();i++) {
				WikiCandidate c = entity.candidates.get(0).get(i);
				res.append("\t\t\t<Candidate>\n");
				res.append("\t\t\t\t<WikiTitle>"+StringEscapeUtils.escapeXml(c.titleName)+"</WikiTitle> <WikiTitleID>"+
					c.getTid()+"</WikiTitleID> <RankerScore>"+
					c.rankerScore+"</RankerScore>\n");
				res.append("\t\t\t</Candidate>\n");
			}
			res.append("\t\t</DisambiguationCandidates>\n");			
			res.append("</Entity>\n");
		}
		res.append("</WikifiedEntities>\n");
		res.append("</WikifierOutput>\n");
		return res.toString();
	}
	
	public static String getExplanation(LinkingProblem problem,boolean showJustLastLevel){
		StringBuilder res=new StringBuilder();
		for(int entityId=0;entityId<problem.components.size();entityId++){
			Mention entity=problem.components.get(entityId);
			res.append("Entity : '"+entity.surfaceForm+"' start in text:"+
					entity.charStart+" end in text: "+
					(entity.charStart+entity.charLength)+ "; Entity types: ");
			for( Iterator<Mention.SurfaceType> i = entity.types.iterator();i.hasNext(); res.append(i.next()+","));
			res.append('\n');
			try{
				int levelId=0;
				if(showJustLastLevel)
					levelId=entity.candidates.size()-1;
				for(;levelId<entity.candidates.size();levelId++){
					res.append("\t------>  LEVEL "+levelId+"\n");
					List<WikiCandidate> candidates =entity.candidates.get(levelId);
					for(int i=0;i<candidates.size();i++){
						WikiCandidate c = candidates.get(i);
						res.append("\t\t("+i+")" +getScoresString(c)+ "\n");
					}
				}				
			}
			catch(Exception e){
				res.append("\t\tCould not resolve the candidates for the entity!\n");
			}
		}
		return res.toString();
	}

	public static String view2Str(TextAnnotation ta, View view) {
	    StringBuilder res = new StringBuilder();
		for (Constituent c : view.getConstituents()) {
			res.append("<Form>"+c.getSurfaceString().replace('\n', ' ')+
					"</Form> <Start>"+c.getStartCharOffset()+"</Start> <End>"+
					c.getEndCharOffset()+"</End> <Label>"+c.getLabel()+"</Label>\n");
		}
		return res.toString();
	}
}
