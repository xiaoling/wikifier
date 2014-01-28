// $codepro.audit.disable useCharAtRatherThanStartsWith
package edu.illinois.cs.cogcomp.wikifier.annotation;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.IOUtils;

import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;

import edu.illinois.cs.cogcomp.edison.data.curator.CuratorClient;
import edu.illinois.cs.cogcomp.edison.sentences.EdisonSerializationHelper;
import edu.illinois.cs.cogcomp.edison.sentences.TextAnnotation;
import edu.illinois.cs.cogcomp.edison.sentences.ViewNames;


public class CachingCurator {
	public static final Set<String> supportedViews = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
		ViewNames.NER, ViewNames.SHALLOW_PARSE, ViewNames.POS, ViewNames.COREF
	)));
	public CuratorClient curator = null;
	public FakeCurator fakeCurator = null; // the preference will always be given to the fake curator if it's not null! 
	public Set<String> activeViews = null;
	public String pathToSaveCachedFiles = null;
	
	/**
	 * Convenience delegate method for getting array of views
	 * @param curatorMachine
	 * @param port
	 * @param views
	 * @param pathToSaveCachedFiles
	 * @throws Exception
	 */
    public CachingCurator(String curatorMachine, int port, String[] views, String pathToSaveCachedFiles) throws Exception {
        this(curatorMachine, port, new HashSet<String>(Arrays.asList(views)), pathToSaveCachedFiles);
    }

    /**
     * 
     * @param curatorMachine
     * @param port
     * @param views
     * @param pathToSaveCachedFiles
     * @throws Exception
     */
    public CachingCurator(String curatorMachine, int port, Set<String> views, String pathToSaveCachedFiles) throws Exception {
        this.pathToSaveCachedFiles = pathToSaveCachedFiles;
        curator = new CuratorClient(curatorMachine, port, false);
        activeViews = Sets.intersection(supportedViews, views);//new HashSet<String>();
        if (activeViews.size() < views.size()) {
            throw new Exception("The views " + Sets.difference(views, activeViews)
                    + " are currently not supported in CachingCurator, you have to modify the code to add support for this view");
        }
    }

	
	/**
	 * Creates a standalone annotation internal service that does not rely on external curator
	 * @param views
	 * @param pathToSaveCachedFiles
	 * @param alwaysExpectCachedData
	 * @param neverUseCache
	 * @param useCoref
	 * @throws Exception 
	 */
    public CachingCurator(Set<String> views, String pathToSaveCachedFiles,String pathToNerConfigFile) throws Exception {
        this.pathToSaveCachedFiles = pathToSaveCachedFiles;
        fakeCurator = new FakeCurator(pathToNerConfigFile, views.contains(ViewNames.COREF));
        activeViews = Sets.intersection(supportedViews, views);
    }

	/**
	 * Defaults to use all supported views and fake curator
	 * @param pathToSaveCachedFiles
	 * @param pathToNerConfigFile
	 * @throws Exception
	 */
    public CachingCurator(String pathToSaveCachedFiles,String pathToNerConfigFile) throws Exception{
        this(supportedViews,pathToSaveCachedFiles,pathToNerConfigFile);
    }
    
	public TextAnnotation getTextAnnotation(String text) throws Exception {
		if(pathToSaveCachedFiles == null) {
			if(fakeCurator!=null)
				return fakeCurator.annotate(text);
			else
				return annotate(text);
		}
		String md5sum = getMD5Checksum(text);
		String savePath = pathToSaveCachedFiles+"/"+md5sum+".cached";
		if(new File(savePath).exists()) {
		    try{
    			TextAnnotation ta = read(savePath);
    			Set<String> cachedViews = ta.getAvailableViews();
    			List<String> missingViews= new ArrayList<>();
    			for(String nextView:activeViews) {
    				if(!cachedViews.contains(nextView) && !nextView.equals(ViewNames.COREF))
    					missingViews.add(nextView);
    			}
    			if(ta.getText().equals(text) && missingViews.size() == 0)
    				return ta;
    			else {
                    if (!ta.getText().equals(text)){
                        System.err.println("Looks like a hashing collision! The text \"" + text.substring(0, Math.min(200, text.length()))
                                + "\" was not cached! The md5sum is " + md5sum + " and the key appears in the cache. But the hashed text is:"
                                + ta.getText().substring(0, Math.min(200, ta.getText().length())));
                    }
                    if (missingViews.size() > 0)
                        System.err.println("Some of the required views were not available! The missing views are:" + missingViews);
    			}
			}catch(Exception e){
			    // Need to reannotate
			    System.err.println("Need to reannotate due to: " + e);
			}
		}
		// If we reached here, either the text wasn't hashed, or some of the views were missing, so we need to re-build the annotation.
		return cacheText(text, true);
	}
	

	public TextAnnotation cacheText(String text, Boolean overrideContent) throws Exception {
		String md5sum = getMD5Checksum(text);
		String savePath = pathToSaveCachedFiles+"/"+md5sum+".cached";

        TextAnnotation ta = null;
        boolean isTheSame = new File(savePath).exists();
		if(isTheSame) {
			if(overrideContent) 
				System.out.println("Warning! The file "+savePath+" already exists, the content will be replaced.");
			else{
				System.out.println("Warning! The file "+savePath+" already exists, skipping the current text.");
			}
		}	
		if(overrideContent || !isTheSame) {
			try {
				if(fakeCurator!=null)
					ta = fakeCurator.annotate(text);
				else
					ta = annotate(text);
				write(savePath, ta);
				return ta;
			}catch (Exception e) {
				System.out.println("Failed to annotate the text "+text+" \n\n The exception was:");
				e.printStackTrace();
			}
		}
		return null;
	}
	
	protected TextAnnotation annotate(String text)  throws Exception {
		TextAnnotation ta = curator.getTextAnnotation("","", text, false);
		if(activeViews.contains(ViewNames.NER))
			curator.addNamedEntityView(ta, false);
		if(activeViews.contains(ViewNames.SHALLOW_PARSE))
			curator.addChunkView(ta, false);
		if(activeViews.contains(ViewNames.DEPENDENCY))
			curator.addEasyFirstDependencyView(ta, false);
		if(activeViews.contains(ViewNames.POS))
			curator.addPOSView(ta, false);
		if(activeViews.contains(ViewNames.COREF))
		    curator.addCorefView(ta, false);
		return ta;		
	}

	private static void write(String fileName, TextAnnotation t){
	    try{
    	    OutputStream os = new FileOutputStream(fileName);
    	    if(t.getText().getBytes("UTF-8").length>=65535)
    	        IOUtils.write(EdisonSerializationHelper.serializeToJson(t), os);
    	    else
    	        IOUtils.write(EdisonSerializationHelper.serializeToBytes(t),os);
    	    os.close();
	    }catch(java.io.UTFDataFormatException e){
	        System.err.println("Warning: "+fileName+" too long, can not write.");
	    }catch(Exception e){
	        System.err.println("Warning: "+fileName+" is not cached due to ");
	        e.printStackTrace();
	    }
	}

	private static TextAnnotation read(String fileName) throws Exception {
	    byte[] content = ByteStreams.toByteArray(new FileInputStream(fileName));
	    try{
            return EdisonSerializationHelper.deserializeFromJson(new String(content));
	    }catch(Exception e){
            return EdisonSerializationHelper.deserializeFromBytes(content);
	    }
	}
	
	
//    private static void write(String fileName, TextAnnotation t) throws Exception {
//        ObjectOutputStream writer = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(fileName)));
//        writer.writeObject(t);
//        writer.close();
//    }
//
//    private static TextAnnotation read(String fileName) throws Exception {
//        ObjectInputStream reader = new ObjectInputStream(new BufferedInputStream(new FileInputStream(fileName)));
//        TextAnnotation t = (TextAnnotation) reader.readObject();
//        reader.close();
//        return t;
//    }
	
	public static String getMD5Checksum(String text){
		MessageDigest complete = null;
        try {
            complete = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
		complete.update(text.getBytes(), 0, text.getBytes().length);
		byte[] b = complete.digest();
		String result = "";
		for (int i=0; i < b.length; i++)
			result += Integer.toString( ( b[i] & 0xff ) + 0x100, 16).substring( 1 );
		return result;
	}
	
	/*
	 * This will cache the datasets I need
	 */
//	public static void main(String[] args) throws Exception{
//		/*String curatorServer = "grandma.cs.uiuc.edu";
//		int curatorPort = 9010;
//		String[] passedViews = new String[]{ViewNames.NER,  ViewNames.SHALLOW_PARSE, ViewNames.POS};
//		CachingCurator curator = new CachingCurator(curatorServer, curatorPort, passedViews, null, false, true);
//		curator.annotate("Lev loves UIUC lalal abrupt");
//		System.out.println("Done!!!");*/
//		
//		
//		System.out.println("Usage: CommonSenseWikifier.MentionExtraction <pathToDataToProcess> <pathToCacheFolder> <boolean force-refresh> <list-of-views-to-cache>");
//		String curatorServer = "trollope.cs.illinois.edu";
//		int curatorPort = 9010;
//		String[] passedViews = new String[args.length-3];
//		for(int i=3;i<args.length;i++) {
//			if(args[i].equals("ViewNames.NER")) 
//				passedViews[i-3]=ViewNames.NER;
//			if(args[i].equals("ViewNames.SHALLOW_PARSE")) 
//				passedViews[i-3]=ViewNames.SHALLOW_PARSE;
//			if(args[i].equals("ViewNames.DEPENDENCY")) 
//				passedViews[i-3]=ViewNames.DEPENDENCY;
//			if(args[i].equals("ViewNames.POS")) 
//				passedViews[i-3]=ViewNames.POS;
//		}
//		CachingCurator curator = new CachingCurator(curatorServer, curatorPort, passedViews, args[1], false, false);
//		String inpath = args[0];
//		String[] files = new File(inpath).list();
//		for(int i=0;i<files.length;i++)
//			if(new File(inpath+"/"+files[i]).isFile()&&!files[i].startsWith(".")) {
//				System.out.println("Processing the file: "+inpath+"/"+files[i]);
//				String text = InFile.readFileText(inpath+"/"+files[i]);
//				if(text.replace(" ", "").replace("\n", "").replace("\t", "").length()>0)
//					curator.cacheText(text, Boolean.parseBoolean(args[2]));
//				System.out.println(i+" files processed of "+files.length);
//			}
//	}
}
