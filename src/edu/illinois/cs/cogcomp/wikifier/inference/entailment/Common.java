package edu.illinois.cs.cogcomp.wikifier.inference.entailment;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class Common {

	public static ArrayList<String> readLines(String fileName) {
		BufferedReader reader = openReader(fileName);
		String line;
		ArrayList<String> content = new ArrayList<String>();
		try {
			while ((line = reader.readLine()) != null) {
				content.add(line);
			}

			reader.close();

			return content;
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Unable to read from file " + fileName);
			System.exit(1);
			return null;
		}
	}
	
	public static BufferedReader openReader(String fname) {
		BufferedReader reader;
		try {
			reader = new BufferedReader(new InputStreamReader(
					new FileInputStream(fname), "UTF-8"));
			return reader;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
}
