package edu.illinois.cs.cogcomp.wikifier.utils;

import java.util.ArrayList;
import java.util.List;

import javatools.parsers.NounGroup;

import org.apache.commons.lang3.StringUtils;

import edu.illinois.cs.cogcomp.wikifier.common.GlobalParameters;
import edu.illinois.cs.cogcomp.wikifier.wiki.access.WikiAccess;
import edu.illinois.cs.cogcomp.wikifier.wiki.indexing.TitleNameIndexer;

/**
 * We want to capture the obvious relations contained in a wikipedia title such as People's National
 * Party (Ghana) has to have relation with Ghana
 * 
 * @author cheng88
 * 
 */
public class WikiTitleUtils {

    static WikiAccess wiki = GlobalParameters.wikiAccess;

    private WikiTitleUtils() {
    }

    // Converts title to their natural mentions
    public static String getCanonicalName(String title) {

        String naturalString = title.replaceAll("_", " ");
        if (naturalString.contains("("))
            naturalString = StringUtils.substringBefore(naturalString, "(");
        String trimmed = naturalString.trim();

        return trimmed;
    }

    public static String stripCommaSpace(String title) {

        String naturalString = title.replaceAll("_", " ");
        // if(naturalString.contains("("))
        // naturalString = StringUtils.substringBefore(naturalString, "(");
        if (naturalString.contains(","))
            naturalString = StringUtils.substringBefore(naturalString, ",");
        String trimmed = naturalString.trim();

        return trimmed;
    }

    /**
     * William_Clinton_(disambiguation) => William Clinton
     * 
     * @param title
     * @return
     */
    public static String getCanonicalTitle(String title) {
        String naturalString = stripCommaSpace(title);
        return StringUtils.substringBefore(naturalString, "(").trim();
    }

    // Add wordnet test ?
    public static String getSecondaryEntity(String title) {
        String potentialEntity = null;

        // e.g. National_Party_(South_Africa)
        if (title.contains("(") && title.endsWith(")")) {
            potentialEntity = StringUtils.substringBetween(title, "(", ")");
            String[] parts = potentialEntity.split(",");

            // e.g. National_Party_(UK,_1976)
            if (parts.length > 1 && StringUtils.containsOnly(parts[1], "_0123456789")) {
                potentialEntity = parts[0];
            }
        }

        // e.g. Chicago,_Illinois
        else if (title.contains(",")) {
            potentialEntity = StringUtils.substringAfterLast(title, ",");
        }

        // e.g. New_Progressive_Party_of_Puerto_Rico
        // Might need to be careful
        else if (title.contains("of")) {
            potentialEntity = StringUtils.substringAfterLast(title, "of");
            if (title.startsWith("List_of")) {
                String[] tokens = potentialEntity.split("_");
                int capPos = 0;
                StringBuilder sb = new StringBuilder();
                while (capPos < tokens.length
                        && (StringUtils.isEmpty(tokens[capPos]) || WordFeatures
                                .isCapitalized(tokens[capPos]))) {
                    sb.append(tokens[capPos]).append('_');
                    capPos++;
                }
                potentialEntity = sb.toString();
            }
        }

        // Removes extra chars
        if (potentialEntity != null)
            potentialEntity = potentialEntity.replace('_', ' ').trim().replace(' ', '_');

        try {
            if (wiki == null || wiki.getTitleIdOf(potentialEntity) >= 0)
                return potentialEntity;
        } catch (Exception e) {
        }

        return null;
    }

    public static String getAcronym(String title) {
        List<Character> acronym = new ArrayList<Character>();
        for (int i = 0; i < title.length(); i++) {
            char c = title.charAt(i);
            if (Character.isUpperCase(c)) {
                String sub = title.substring(i);
                if (sub.length() <= 5
                        && (sub.endsWith(".") || sub.contains("Inc") || sub.contains("Corp") || sub
                                .contains("Ltd"))) {
                    continue;
                }
                acronym.add(c);
            }
        }

        String joinedAcronym = StringUtils.join(acronym, "");
        return joinedAcronym;
    }

    public static String getAcronymAllLetters(String title) {
        String cleaned = title.replace('_', ' ');
        String[] tokens = StringUtils.split(cleaned, ' ');
        StringBuilder acro = new StringBuilder();
        for (String token : tokens) {
            if (token.length() > 0)
                acro.append(token.charAt(0));
        }
        return acro.toString();
    }

    public static String getHead(String url) {
        return new NounGroup(getCanonicalTitle(url).replace('_', ' ')).head();
    }

    /**
     * Removes this title from disambiguation candidates such as list or disambiguation page
     * 
     * @param title
     * @return
     */
    public static boolean filterTitle(String title) {
        if (title.endsWith("(disambiguation)"))
            return true;
        if (GlobalParameters.params.EXCLUDE_DISAMBIGUATION_PAGE
                && TitleNameIndexer.isDisambiguationPage(title))
            return true;
        return false;
    }

}
