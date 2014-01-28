package edu.illinois.cs.cogcomp.wikifier;
/**
 * 
 */


import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import edu.illinois.cs.cogcomp.wikifier.common.GlobalParameters;
import edu.illinois.cs.cogcomp.wikifier.models.ReferenceInstance;
import edu.illinois.cs.cogcomp.wikifier.utils.io.InFile;
import edu.illinois.cs.cogcomp.wikifier.wiki.access.WikiAccess;

/**
 * Generates some colored HTML files for easier error analysis
 * @author Xiao Cheng
 *
 */
public class WikifierPerformanceReport {
	
	public int retrievedCount = 0;
	public int correctCount = 0;// intersection of retrived and goal set
	public int goalCount = 0;
	public String output = "";
	
		
	public void evalResults(String output,String gold,String raw) throws Exception{
		Set<ReferenceInstance> correct = new HashSet<ReferenceInstance>();
		WikiAccess wiki = GlobalParameters.wikiAccess;
		for(ReferenceInstance ref:ReferenceInstance.loadReferenceProblem(gold)){
			if(wiki.getTitleIdFromExternalLink(ref.chosenAnnotation)<0){
				ref.chosenAnnotation = "*null*";
			}
			correct.add(ref);
		}
		
		Set<ReferenceInstance> wikifierOutput = new HashSet<ReferenceInstance>(ReferenceInstance.loadReferenceProblem(output));
		evaluate(wikifierOutput,correct,raw);
		
		// Checks for duplicates
		Map<String,Integer> forms = new HashMap<String,Integer>();
		for(ReferenceInstance r1:correct)
			if(forms.containsKey(r1.surfaceForm))
				forms.put(r1.surfaceForm, forms.get(r1.surfaceForm)+1);
			else
				forms.put(r1.surfaceForm, 1);
		
		for(String form:forms.keySet())
			if(forms.get(form)>1)
				print("Duplicate surface form detected: <font color='red'>"+form+"</font> appeared "+forms.get(form)+" times<br>");
	}
	
	public void evaluate(Set<ReferenceInstance> result, Set<ReferenceInstance> goal,String raw) throws Exception {
		goalCount += goal == null ? 0 : goal.size();
		retrievedCount += result == null ? 0 : result.size();
		
		for (ReferenceInstance element : result)
			if (goal.contains(element))
				correctCount++;
			else{
				print("<p>");
				String text = InFile.readFileText(raw);
				int start = Math.max(0, element.charStart-50);
				int end = Math.min(text.length(), element.charStart+element.charLength+50);
				String sentence = text.substring(start, end).replace(element.surfaceForm, "<font color='red'>"+element.surfaceForm+"</font>");
				print("Sentence: "+ sentence +"<br>");
				print("Wrong answer: "+element.chosenAnnotation+"<br>");
				for(ReferenceInstance cor:goal){
					if(cor.charStart == element.charStart && cor.charLength==element.charLength){
						print("Gold Annotation: "+cor.chosenAnnotation+"<br>");
						if(!element.chosenAnnotation.equals("*null*") && !cor.chosenAnnotation.equals("*null*"))
							print("Prominent mention mistake detected<br>");
						break;
					}
				}
				
				print("</p>");
			}
		goal.removeAll(result);
		//if(goal.size()>0) print("Missed: " + goal);
	}
	
	public void print(Object o){
		output+=o;
		output+="\n";
	}
	
	public void evalAll(String outputDir,String standard,String raw){
		File[] problems = new File(standard).listFiles();
		for(File f:problems){
			if(f.getName().charAt(0)=='.')
				continue;
			File outputFile = new File(outputDir,f.getName()+".referenceInstance.tagged");
			if(!outputFile.exists())
				continue;
			try{
				print("<hr>");
				print("Evaluting file "+outputFile.getAbsolutePath());
				evalResults(outputFile.getPath(),f.getAbsolutePath(),raw+f.getName());
			}catch(Exception e){
				print(f+" is skipped.");
			}
		}

		int misses = StringUtils.countMatches(output, "Wrong answer: *null*");
		int linkerMistakes = StringUtils.countMatches(output, "Gold Annotation: *null*");
		int prominentMentions = StringUtils.countMatches(output, "Prominent mention mistake detected");
		print(String.format("<h2>Missed: %d, Shouldn't link: %d,Wrong link: %d in a total of %d mistakes</h2><br>",
				misses, linkerMistakes, prominentMentions,goalCount-correctCount));
	}
	

	
	public double getRecall() {
		return goalCount == 0 ? 0. : (double) correctCount / goalCount;
	}

	public double getPrecision() {
		return retrievedCount == 0 ? 0. : (double) correctCount / retrievedCount;
	}

	public double getF1() {
		double R = getRecall();
		double P = getPrecision();
		return P + R == 0 ? 0. : 2 * (P * R) / (P + R);
	}

	public String toString() {
		return String.format("Current P: %f ; R: %f; F1: %f", getPrecision(), getRecall(), getF1());
	}
	
	private static class Dataset{
		public static Map<String,Dataset> data = new HashMap<String,Dataset>();
		public String problemPath;
		public String rawTextPath;
		public Dataset(String name,String problemPath,String rawTextPath){
			this.problemPath = problemPath;
			this.rawTextPath = rawTextPath;
			data.put(name, this);
		}
	}

	public static void main(String[] args) throws Exception{
		GlobalParameters.loadConfig("../Config/XiaoConfig");
		//String[] datasets = new String[]{"ACE","MSNBC","AQUAINT","WIKI"};
		new Dataset("ACE","../ACE2004_Relabeled2013/ProblemsNoTranscripts/",
				"../ACE2004_Relabeled2013/RawTextsNoTranscripts/");
		new Dataset("AQUAINT","../Data/WikificationACL2011Data/AQUAINT/Problems/",
				"../Data/WikificationACL2011Data/AQUAINT/RawTexts/");
		new Dataset("MSNBC","../Data/WikificationACL2011Data/MSNBC/Problems/",
				"../Data/WikificationACL2011Data/MSNBC/RawTextsSimpleChars/");
		new Dataset("Wikipedia","../Data/WikificationACL2011Data/WikipediaSample/ProblemsTest/",
				"../Data/WikificationACL2011Data/WikipediaSample/RawTextsTest/");
		
		File dir = new File("../Output/");
		for(File subdir:dir.listFiles()){
			WikifierPerformanceReport report = new WikifierPerformanceReport();
			Dataset currentDataset = Dataset.data.get(subdir.getName());
			System.out.println(currentDataset.problemPath);
			report.evalAll(subdir.getPath(),currentDataset.problemPath,currentDataset.rawTextPath);
			report.print(report);
			
			edu.illinois.cs.cogcomp.wikifier.utils.io.OutFile out = new edu.illinois.cs.cogcomp.wikifier.utils.io.OutFile("../HTMLAnalysis/"+subdir.getName()+".html");
			out.print(report.output);
			out.println(report.goalCount+" instances with "+report.correctCount+" correct");
			out.close();
		}
	}
}
