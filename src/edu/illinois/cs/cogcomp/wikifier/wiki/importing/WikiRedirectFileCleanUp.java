package edu.illinois.cs.cogcomp.wikifier.wiki.importing;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;

public class WikiRedirectFileCleanUp {

	/**
	 * Note that we do not enforce the uniqueness of children
	 * @author cheng88
	 *
	 */
	public static class DisjointSet {

		private Map<String, String> parents = new HashMap<String, String>();

		private Map<String, List<String>> childrens = new HashMap<String, List<String>>();

		/**
		 * Add all the child of the input to the parent of the redirect
		 * 
		 * @param element
		 * @param setHead
		 */
		public void add(String element, String setHead) {

			if (parents.containsKey(element))
				return;

			String newHead = parents.get(setHead);
			if (newHead == null) {
				newHead = setHead;
				childrens.put(setHead, new ArrayList<String>());
			}

			List<String> children = childrens.get(element);
			// Migrate current children to new parent
			if (children != null) {

				childrens.get(newHead).addAll(children);
				for (String child : children) {
					parents.put(child, newHead);
				}

			} else {

				parents.put(element, newHead);
				childrens.get(newHead).add(element);

			}

		}

		public int size() {
			return parents.size();
		}

		public boolean write(String filename) {
			File output = new File(filename);
			List<String> lines = new ArrayList<String>();
			for (Entry<String, String> line : parents.entrySet()) {
				lines.add(line.getKey() + '\t' + line.getValue());
			}
			try {
				FileUtils.writeLines(output, lines);
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			}
			return true;
		}

	}

	/**
	 * @param args
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {
		if (args.length != 2) {
			System.out.println("Please specify the input and output filenames");
			return;
		}

		// Map<String,Redirect> redirects = new HashMap<String, Redirect>();
		//
		DisjointSet wikiRedirect = new DisjointSet();

		LineIterator iterator = FileUtils.lineIterator(new File(args[0]));
		int processed = 0;
		while (iterator.hasNext()) {
			String line = iterator.nextLine();
			line = StringEscapeUtils.unescapeXml(line).replace(' ', '_');

			String[] redirect = StringUtils.split(line, '\t');
			if (redirect.length == 2) {
				String title = redirect[0];
				String redirectedTitle = redirect[1];
				if (!WikipediaRedirectExtractor.isValidAlias(title, redirectedTitle))
					continue;

				wikiRedirect.add(title, redirectedTitle);

			}
			processed++;
			if (processed % 10000 == 0)
				System.out.println("Processed " + processed + " lines ");
		}

		System.out.println("Writing redirects...");
		if (wikiRedirect.write(args[1])) {
			System.out.println("Redirects saved to " + args[1] + " with " + wikiRedirect.size() + " lines");
		}

	}

}
