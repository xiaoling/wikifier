// $codepro.audit.disable useCharAtRatherThanStartsWith
package edu.illinois.cs.cogcomp.wikifier.apps;

import java.io.File;
import java.io.IOException;

import edu.illinois.cs.cogcomp.wikifier.utils.io.InFile;
import edu.illinois.cs.cogcomp.wikifier.utils.io.OutFile;


/*
 * The curator crashes when it sees strange characters. This thing removes them
 */
public class CharacterSetCleaning {
	
	public static String cleanText(String text){
	    StringBuilder clean = new StringBuilder(text.length());
		for(int j=0;j<text.length();j++) {
			char c= text.charAt(j);
			if(Character.isLetterOrDigit(c)|| " \n\t,./?;':\"[]{}\\|`1234567890-=+_)(*&^%$#@!".indexOf(c)>-1)
				clean.append(c);
			else
				clean.append(' ' );
		}
		return clean.toString();
	}
	
	public static void main(String[] args) throws IOException {
		System.out.println("java CharacterSetCleaning <folder with input files> <folder with output files>");
		String inpath = args[0];
		String outpath = args[1];
		String[] files = new File(inpath).list();
		for(int i=0;i<files.length;i++)
			if(!files[i].startsWith(".")){
				OutFile out = new OutFile(outpath+"/"+files[i]);
				out.println(InFile.readFileText(inpath+"/"+files[i]));
				out.close();
			}
	}
}
