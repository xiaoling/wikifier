package edu.illinois.cs.cogcomp.wikifier.wiki.access;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;

import edu.illinois.cs.cogcomp.core.datastructures.Pair;
import edu.illinois.cs.cogcomp.wikifier.common.GlobalParameters;
import edu.illinois.cs.cogcomp.wikifier.models.ReferenceInstance;
import edu.illinois.cs.cogcomp.wikifier.utils.TimeAndMemMonitor;
import edu.illinois.cs.cogcomp.wikifier.utils.datastructure.LRUCache;
import edu.illinois.cs.cogcomp.wikifier.utils.datastructure.StringMap;
import edu.illinois.cs.cogcomp.wikifier.utils.io.CompressionUtils;
import edu.illinois.cs.cogcomp.wikifier.wiki.indexing.TitleNameIndexer;

public class TitleNameNormalizer {

	public static boolean showInitProgress = true;

	public static final int ESTIMATED_REDIRECTS = 6000000;
	private Map<String, String> redirects;

	private BloomFilter<CharSequence> redirectFilter;

	private static boolean useBloomFilter = false;// GlobalParameters.systemPriority
													// == Priority.MEMORY;

	private static TitleNameNormalizer evaluatorInstance = null;

	public static TitleNameNormalizer getInstance() {

		if (evaluatorInstance == null) {
			synchronized (TitleNameNormalizer.class) {
				if (evaluatorInstance == null)
					try {
						evaluatorInstance = new TitleNameNormalizer(
								GlobalParameters.paths.compressedRedirects);
					} catch (IOException e) {
						e.printStackTrace();
						System.exit(0);
					}
			}
		}
		return evaluatorInstance;
	}

	private TitleNameNormalizer() {
	}

	private TitleNameNormalizer(String pathToEvaluationRedirectsData)
			throws IOException {

		if (useBloomFilter) {
			redirectFilter = BloomFilter.create(Funnels.stringFunnel(),
					ESTIMATED_REDIRECTS);
			redirects = new LRUCache<String, String>(5000) {
				protected String loadValue(String src) {
					String normalized = TitleNameIndexer.normalize(src);
					if (normalized == null)
						return src;
					return TitleNameIndexer.normalize(src);
				}
			};
		} else
			redirects = new StringMap<String>();
		if (showInitProgress)
			System.out
					.println("Loading the most recent redirect pages from Wikipedia to normalize the output links to the latest version");
		if (pathToEvaluationRedirectsData != null) {
			InputStream is = CompressionUtils
					.readSnappyCompressed(pathToEvaluationRedirectsData);
			LineIterator iterator = IOUtils.lineIterator(is,
					StandardCharsets.UTF_8);

			long linecount = 0;
			while (iterator.hasNext()) {
				String line = iterator.nextLine();
				if (showInitProgress && linecount++ % 100000 == 0)
					System.out
							.println("loading the latest redirects; linecount="
									+ linecount);
				String[] parts = StringUtils.split(line, '\t');

				String src = parts[0].trim().replace(' ', '_');
				String trg = parts[1].trim().replace(' ', '_');
				if (useBloomFilter)
					redirectFilter.put(src);
				else
					redirects.put(src, trg);
			}
			iterator.close();
		}
		redirects = Collections.unmodifiableMap(redirects);
		if (showInitProgress)
			System.out
					.println("Done  - Loading the most recent redirect pages from Wikipedia to normalize the output links to the latest version");
	}

	private TitleNameNormalizer(Map<String, String> redirectMap) {
		redirects = redirectMap;
	}

	public static String normalize(String wikiLink) {
		try {
			return getInstance().redirectWithLatestLinks(wikiLink);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return wikiLink;
	}

	protected String redirect(String title) {
		if (useBloomFilter && !redirectFilter.mightContain(title))
			return title;
		String to = redirects.get(title);
		int count = 0;
		while (to != null && !to.equals(title) && count++ <= 50) {
			title = to;
			to = redirects.get(title);
		}

		if (count >= 50) {
			System.out.println("Fixed point reached with title : " + title
					+ " ; stopping to loop in redirects at this moment");
		}
		return title;
	}

	/**
	 * Normalizes a map with wiki titles as keys
	 * 
	 * @param titleMap
	 */
	public static <T> void normalize(Map<String, T> titleMap) {
		for (Entry<String, String> redirect : getInstance().redirects
				.entrySet()) {
			String from = redirect.getKey();
			String to = redirect.getValue();
			if (titleMap.containsKey(from) && titleMap.containsKey(to)) {
				titleMap.put(from, titleMap.get(to));
			}
		}
	}

	private String redirectWithLatestLinks(String wikiLink) {
		if (wikiLink == null)
			return "*null*";
		String title = wikiLink.replace(' ', '_');
		if (wikiLink.indexOf("http://en.wikipedia.org/wiki/") > -1)
			title = wikiLink.substring(
					wikiLink.lastIndexOf("http://en.wikipedia.org/wiki/")
							+ "http://en.wikipedia.org/wiki/".length(),
					wikiLink.length());

		title = redirect(title);

		return StringEscapeUtils.unescapeXml(StringUtils.capitalize(title));
	}

	public static void main(String[] args) throws Exception {
		// createRandomAccessRedirects(ParametersAndGlobalVariables.pathToRedirectHashMap);
		// loads redirect file into disk backed hash map

		// check that the redirects were loaded correctly

		long memoryBeforeLodaingTheTrie = TimeAndMemMonitor.usedMemory();
		// ParametersAndGlobalVariables.pathToEvaluationRedirectsData =
		// "../WikiData/RedirectsForEvaluationData/RedirectsAug2010.txt";
		TitleNameNormalizer normalizer = TitleNameNormalizer.getInstance();
		if (!normalizer
				.redirectWithLatestLinks("List of Legend of Light episodes")
				.equals(normalizer
						.redirectWithLatestLinks("List of Hikari no Densetsu episodes")))
			throw new Exception(
					"Exception1:"
							+ normalizer
									.redirectWithLatestLinks("List of Legend of Light episodes")
							+ "!="
							+ normalizer
									.redirectWithLatestLinks("List of Hikari no Densetsu episodes"));
		if (!normalizer.redirectWithLatestLinks("BabaLevRatinov").equals(
				"BabaLevRatinov"))
			throw new Exception("Exception2");
		if (!normalizer.redirectWithLatestLinks("15 December").equals(
				"December_15"))
			throw new Exception("Exception3");
		System.out
				.println("The usage of the redirect trie is = "
						+ (TimeAndMemMonitor.usedMemory() - memoryBeforeLodaingTheTrie));
		Map<String, String> redirectMap = new HashMap<String, String>();
		// {
		// String dir2 =
		// "/projects/pardosa/data12/xiaoling/workspace/wikifier/data/WikificationACL2011Data/AQUAINT/Problems/";
		// String[] files = new File(dir2).list();
		//
		// for (String file : files) {
		// Map<Pair<Integer, Integer>, String> map = readGoldFromWikifier(dir2
		// + file);
		// for (String value : map.values()) {
		// String normalized = ReferenceInstance.normalize(value);
		// redirectMap.put(value, normalized);
		// }
		// }
		// }
		// {
		// String dir2 =
		// "/projects/pardosa/data12/xiaoling/workspace/wikifier/data/WikificationACL2011Data/WikipediaSample/ProblemsTest/";
		// String[] files = new File(dir2).list();
		//
		// for (String file : files) {
		// Map<Pair<Integer, Integer>, String> map = readGoldFromWikifier(dir2
		// + file);
		// for (String value : map.values()) {
		// String normalized = ReferenceInstance.normalize(value);
		// redirectMap.put(value, normalized);
		// }
		// }
		// }
		// {
		// String dir2 =
		// "/projects/pardosa/data12/xiaoling/workspace/wikifier/data/WikificationACL2011Data/ACE2004_Coref_Turking/Dev/ProblemsNoTranscripts/";
		// String[] files = new File(dir2).list();
		//
		// for (String file : files) {
		// Map<Pair<Integer, Integer>, String> map = readGoldFromWikifier(dir2
		// + file);
		// for (String value : map.values()) {
		// String normalized = ReferenceInstance.normalize(value);
		// redirectMap.put(value, normalized);
		// }
		// }
		// }
		// {
		// String dir2 =
		// "/projects/pardosa/data12/xiaoling/workspace/wikifier/data/WikificationACL2011Data/MSNBC/Problems/";
		// String[] files = new File(dir2).list();
		//
		// for (String file : files) {
		// Map<Pair<Integer, Integer>, String> map = readGoldFromWikifier(dir2
		// + file);
		// for (String value : map.values()) {
		// String normalized = ReferenceInstance.normalize(value);
		// redirectMap.put(value, normalized);
		// }
		// }
		// }
		{
			List<String> lines = FileUtils
					.readLines(
							new File(
									"/projects/pardosa/data12/xiaoling/workspace/aida/aida.pred.entities"),
							"UTF-8");
			for (String line: lines) {
				String normalized = ReferenceInstance.normalize(line.trim());
				 redirectMap.put(line.trim(), normalized);
			}
		}

		StringBuilder sb = new StringBuilder();
		for (String key : redirectMap.keySet()) {
			sb.append(key + "\t" + redirectMap.get(key) + "\n");
		}
		FileUtils.writeStringToFile(new File("redirects.aida.evaluation"),
				sb.toString());
	}

	public static Map<Pair<Integer, Integer>, String> readGoldFromWikifier(
			String filename) {
		List<String> lines = null;
		try {
			lines = FileUtils
					.readLines(new File(filename),
							(filename.contains("Wiki") || filename
									.contains("AQUAINT")) ? "utf-8"
									: "Windows-1252");
		} catch (IOException e) {
			e.printStackTrace();
		}
		int offset = -1, length = -1;
		String label = null;
		int state = 0;
		Map<Pair<Integer, Integer>, String> gold = new HashMap<Pair<Integer, Integer>, String>();
		for (String line : lines) {
			if (state > 0) {
				switch (state) {
				case 1:
					offset = Integer.parseInt(line.trim());
				case 2:
					length = Integer.parseInt(line.trim());
				case 3:
					label = line.trim();
				default:
					state = 0;
				}
			}
			if (line.trim().equals("<Offset>")) {
				state = 1;
			}
			if (line.trim().equals("<Length>")) {
				state = 2;
			}
			if (line.trim().equals("<ChosenAnnotation>")) {
				state = 3;
			}
			if (line.trim().equals("</ReferenceInstance>")) {
				state = 0;
				if (!label.equals("none") && !label.equals("---")) {
					gold.put(new Pair(offset, offset + length), label);
				}
			}
		}
		return gold;
	}

} // class
