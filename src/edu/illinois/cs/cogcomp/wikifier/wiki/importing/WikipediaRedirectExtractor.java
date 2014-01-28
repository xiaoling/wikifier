/*
 * Copyright 2011 Carnegie Mellon University
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, 
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package edu.illinois.cs.cogcomp.wikifier.wiki.importing;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.itadaki.bzip2.BZip2InputStream;

/**
 * Extracts wikipedia redirect information and serializes the data.
 * 
 * @author Hideki Shima
 * @modified Xiao Cheng
 * 
 */
public class WikipediaRedirectExtractor {

	public static final boolean REMOVE_DISAMBIGUATION = true;
	public static final boolean SAVE_COMPLETE_TITLE_LIST = true;

	private static String titlePattern = "    <title>";
	private static String redirectPattern = "    <redirect";
	private static String idPattern = "    <id>";
	
	public String run(File inputFile, File outputFile) throws Exception {
		int invalidCount = 0;
		long t0 = System.currentTimeMillis();
		InputStream fis = new FileInputStream(inputFile);
		if (inputFile.getName().endsWith(".bz2"))
			fis = new BZip2InputStream(fis, false);

		BufferedReader dumpReader = new BufferedReader(new InputStreamReader(fis, "utf-8"));

		BufferedWriter redirectWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFile), "utf-8"));

		String titleIdFile = outputFile + "-title-id.txt";
		BufferedWriter titleIdWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(titleIdFile),	"utf-8"));

		int count = 0;
		String title = null;
		String line = null;
		while ((line = dumpReader.readLine()) != null) {
			if (line.startsWith(titlePattern)) {
				title = cleanupTitle(line);
				continue;
			}
			if (line.startsWith(redirectPattern)) {
				String[] splits = StringUtils.substringsBetween(line, "<redirect title=\"", "\" />");
				if(splits == null || splits.length!=1){
					invalidCount++;
					continue;
				}
				String redirectedTitle = splits[0];
				redirectedTitle = cleanupTitle(redirectedTitle);
				if (isValidAlias(title, redirectedTitle)) {
					redirectWriter.write(title + "\t" + redirectedTitle);
					redirectWriter.newLine();
					count++;
				} else {
					invalidCount++;
					System.out.println("Discarded redirect from "+ title+" to " + redirectedTitle);
				}
				if(count % 100000 == 0)
					System.out.println("Processed " + (count+invalidCount) + " titles ");
			}

			if (SAVE_COMPLETE_TITLE_LIST && line.startsWith(idPattern)) {
				String[] splits = StringUtils.substringsBetween(line, "<id>", "</id>");
				if(splits == null || splits.length!=1){
					invalidCount++;
					continue;
				}
				titleIdWriter.write(splits[0] + '\t' + title);
				titleIdWriter.newLine();
			}

		}

		dumpReader.close();
		fis.close();

		redirectWriter.close();
		titleIdWriter.close();

		System.out.println("---- Wikipedia redirect extraction done ----");
		long t1 = System.currentTimeMillis();
		// IOUtil.save( map );
		System.out.println("Discarded " + invalidCount + " redirects to wikipedia meta articles.");
		System.out.println("Extracted " + count + " redirects.");
		System.out.println("Saved output: " + outputFile.getAbsolutePath());
		System.out.println("Done in " + ((t1 - t0) / 1000) + " sec.");
		return titleIdFile;
	}

	private String cleanupTitle(String title) {
		int end = title.indexOf("</title>");
		String titleString = end != -1 ? title.substring(titlePattern.length(), end) : title;
		titleString = StringEscapeUtils.unescapeXml(titleString).replace(' ', '_');
		return titleString;
	}

	private static boolean containsASCII(String s) {
		if (s == null)
			return false;
		for (int i = 0; i < s.length(); i++) {
			if (0 <= s.charAt(i) && s.charAt(i) <= 127)
				return true;
		}
		return false;
	}

	/**
	 * Identifies if the redirection is valid. Currently, we only check if the
	 * redirection is related to a special Wikipedia page or not.
	 * 
	 * 
	 * @param title
	 *            source title
	 * @param redirectedTitle
	 *            target title
	 * @return validity
	 */
	public static boolean isValidAlias(String title, String redirectedTitle) {

		if(StringUtils.isEmpty(title) || StringUtils.isEmpty(redirectedTitle))
			return false;
		
		if (title.startsWith("Wikipedia:") || title.startsWith("Template:") || title.startsWith("Category:")
				|| title.startsWith(":Category:") || title.startsWith("Help:") || title.startsWith("Portal:")
				|| title.startsWith("List of ") || title.length() == 1 || !containsASCII(title)) {
			return false;
		}

		if (redirectedTitle.startsWith("Wikipedia:") || redirectedTitle.startsWith("Template:") || redirectedTitle.startsWith("Category:")
				|| redirectedTitle.startsWith(":Category:") || redirectedTitle.startsWith("Help:") || redirectedTitle.startsWith("Portal:")
				|| (REMOVE_DISAMBIGUATION && redirectedTitle.endsWith("(disambiguation)"))) {
			return false;
		}

		return true;
	}
	
	/**
	 * Extracts from a wikipedia dump
	 * @param dumpFile
	 * @param extractionDir
	 * @return [cleanedRedirectFile,cleanedTitleIdListFile]
	 * @throws Exception
	 */
	public static String[] extract(String dumpFile,String extractionDir) throws Exception {
	    File inputFile = new File(dumpFile);
        if (!inputFile.exists() || inputFile.isDirectory()) {
            System.err.println("ERROR: File not found at " + inputFile.getAbsolutePath());
            System.exit(0);
        }
        String prefix = inputFile.getName().replaceFirst("-.*", "");
        File outputDir = new File(extractionDir);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        File outputFile = new File(outputDir, prefix + "-redirect.txt");
        String titleIdFile = new WikipediaRedirectExtractor().run(inputFile, outputFile);
        System.out.println("Flattening and normalizing redirect data structure...");
        String cleanedRedirectFile = outputFile.getPath()+".cleaned";
        
        WikiRedirectFileCleanUp.main(new String[]{outputFile.getPath(), cleanedRedirectFile});
        
        return new String[]{cleanedRedirectFile,titleIdFile};
	}

	/**
	 * Handles both compressed or uncompressed dumps
	 * run with arguments [WikiDumpFile] [OutputDirectory]
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		if (args.length != 2) {
			System.err.println("ERROR: Please specify the path to the wikipedia article xml file and output filename as the arguments.");
			System.err.println("Tips: enclose the path with double quotes if a space exists in the path.");
			return;
		}
		
		extract(args[0],args[1]);
	}
}
