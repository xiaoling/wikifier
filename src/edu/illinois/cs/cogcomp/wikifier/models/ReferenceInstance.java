/** 
 * 
 */
package edu.illinois.cs.cogcomp.wikifier.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import edu.illinois.cs.cogcomp.wikifier.common.GlobalParameters;
import edu.illinois.cs.cogcomp.wikifier.utils.io.InFile;
import edu.illinois.cs.cogcomp.wikifier.utils.io.OutFile;
import edu.illinois.cs.cogcomp.wikifier.wiki.access.TitleNameNormalizer;
import edu.illinois.cs.cogcomp.wikifier.wiki.indexing.TitleNameIndexer;



public class ReferenceInstance extends TextSpan implements Serializable{
    
	/**
     * 
     */
    private static final long serialVersionUID = -2907090784848486278L;
    public String rawTextFilename;//The path to the file containing the entity.
	public String chosenAnnotation = "UNKNOWN";//in case several annotators disagree
    public List<String> annotations = new ArrayList<String>();
    public List<String> annotatorIds = new ArrayList<String>();
	//records all the annotators and their annotations.
	private Mention assignedEntity;//we'll be looking for an answer here...
	
    public String comments = null;
	
	
    public ReferenceInstance() {
    }

    public ReferenceInstance(String surface, int charStart, int end) {
        this(surface, charStart, end, "UNKNOWN");
    }

    public ReferenceInstance(String surface, int charStart, int end, String goldAnnotation) {
        this.surfaceForm = surface;
        this.charStart = charStart;
        this.charLength = end - charStart;
        this.chosenAnnotation = goldAnnotation;
    }
	
	public String surfaceFormDetails() {
		return "("+surfaceForm+":"+charStart+"-"+(charStart+charLength)+")";
	}
	
	
	public static List<ReferenceInstance> loadReferenceProblem(String filename) throws Exception {
		List<ReferenceInstance> res=new ArrayList<ReferenceInstance>();
		InFile in=new InFile(filename);
		String line=in.readLine();// <ReferenceProblem>
		line=in.readLine();//<ReferenceFileName>
		String referencedFilename=in.readLine().replace("\t", "");//referencedTextFile
		line=in.readLine();//</ReferenceFileName>
		line=in.readLine();//<ReferenceInstance>
		while(line!=null&&line.equals("<ReferenceInstance>")){
			ReferenceInstance ri=new ReferenceInstance();
			line=in.readLine();//<SurfaceForm>
			ri.surfaceForm=in.readLine().trim();
			line=in.readLine();//</SurfaceForm>
			line=in.readLine();//<Offset>
			ri.charStart=Integer.parseInt(in.readLine().replace(" ", "").replace("\t", ""));
			line=in.readLine();//</Offset>
			line=in.readLine();//<Length>
			ri.charLength=Integer.parseInt(in.readLine().replace(" ", "").replace("\t", ""));
			line=in.readLine();//</Length>
			line=in.readLine();//<ChosenAnnotation>
			// normalizing is super critical because some problems have typos like:
			//	<ChosenAnnotation>
			//	http://en.wikipedia.org/wiki/http://en.wikipedia.org/wiki/Dorothy_Lamour
			//	</ChosenAnnotation>
			//The normalization will strip the  "wikipedia.org/wiki/" part
			ri.chosenAnnotation=normalize(in.readLine().replace(" ", "").replace("\t", ""));
			line=in.readLine();//</ChosenAnnotation>
			line=in.readLine();//<NumAnnotators>
			int numAnnorations=Integer.parseInt(in.readLine().replace(" ", "").replace("\t", ""));
			line=in.readLine();//</NumAnnotators>
			for(int i=0;i<numAnnorations;i++){
				line=in.readLine();//<AnnotatorId>
				ri.annotatorIds.add(in.readLine().replace(" ", "").replace("\t", ""));
				line=in.readLine();//</AnnotatorId>
				line=in.readLine();//<Annotation>
				ri.annotations.add(normalize(in.readLine().trim().replace("\t", "").replace(" ", "_")));
				line=in.readLine();//</Annotation>
			}
			line=in.readLine();//"</ReferenceInstance>"
			line=in.readLine();//either "</ReferenceProblem>" or "<ReferenceInstance>"
			ri.rawTextFilename=referencedFilename;
			res.add(ri);
		}		
		in.close();
		return res;
	}
	
	private static String normalize(String wikiLink) throws Exception {
	    
		if(wikiLink.contains("wikipedia.org/wiki/"))
			wikiLink = StringUtils.substringAfterLast(wikiLink, "wikipedia.org/wiki/");
		
		wikiLink = wikiLink.trim();
		if(wikiLink.endsWith("\"") && StringUtils.countMatches(wikiLink, "\"")==1)
			wikiLink = StringUtils.stripEnd(wikiLink, "\"");

		wikiLink = TitleNameNormalizer.normalize(wikiLink);

		if(GlobalParameters.params.UNINDEXED_AS_NULL){
		    // WARNING: using the new index directly will cause significant
		    // mismatches since many pages have changed and may have become redirected
		    // to other pages
    		if(TitleNameIndexer.normalize(wikiLink) == null)
    		    wikiLink = "*null*";
		}
		
		return wikiLink;
	}

	public static void saveReferenceProblem(List<ReferenceInstance> v,String outfilename,String referencedTextFile){
		OutFile out=new OutFile(outfilename);
		out.println("<ReferenceProblem>");
		out.println("\t<ReferenceFileName>\n\t\t"+referencedTextFile+"\n\t</ReferenceFileName>");
		for(int j=0;j<v.size();j++){
			ReferenceInstance ri=v.get(j);
			out.println("<ReferenceInstance>");
			out.println("\t<SurfaceForm>\n\t\t"+ri.surfaceForm+"\n\t</SurfaceForm>");
			out.println("\t<Offset>\n\t\t"+ri.charStart+"\n\t</Offset>");
			out.println("\t<Length>\n\t\t"+ri.charLength+"\n\t</Length>");
			out.println("\t<ChosenAnnotation>\n\t\t"+ri.chosenAnnotation+"\n\t</ChosenAnnotation>");
			out.println("\t<NumAnnotators>\n\t\t"+ri.annotations.size()+"\n\t</NumAnnotators>");
			for(int i=0;i<ri.annotations.size();i++){
				out.println("\t\t<AnnotatorId>\n\t\t\t"+ri.annotatorIds.get(i)+"\n\t\t</AnnotatorId>");
				out.println("\t\t<Annotation>\n\t\t\t"+ri.annotations.get(i)+"\n\t\t</Annotation>");
			}			
			out.println("</ReferenceInstance>");			
		}
		out.println("</ReferenceProblem>");		
		out.close();
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + charLength;
		result = prime * result + charStart;
		result = prime * result + ((chosenAnnotation == null) ? 0 : chosenAnnotation.hashCode());
		result = prime * result + ((surfaceForm == null) ? 0 : surfaceForm.hashCode());
		return result;
	}


	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ReferenceInstance other = (ReferenceInstance) obj;
		if (charLength != other.charLength)
			return false;
		if (charStart != other.charStart)
			return false;
		if (chosenAnnotation == null) {
			if (other.chosenAnnotation != null)
				return false;
		} else if (!chosenAnnotation.equals(other.chosenAnnotation))
			return false;
		if (surfaceForm == null) {
			if (other.surfaceForm != null)
				return false;
		} else if (!surfaceForm.equals(other.surfaceForm))
			return false;
		return true;
	}


	@Override
	public String toString() {
		return "surfaceForm=" + surfaceForm + ", chosenAnnotation=" + chosenAnnotation;
	}


    public Mention getAssignedEntity() {
        return assignedEntity;
    }


    public void setAssignedEntity(Mention assignedEntity) {
//        if(assignedEntity==null)
            this.assignedEntity = assignedEntity;
//        else
//            this.assignedEntity = WikifiableEntity.shallowCopy(assignedEntity);
    }


}