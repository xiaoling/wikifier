package edu.illinois.cs.cogcomp.wikifier.common;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBException;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.concurrent.BackgroundInitializer;
import org.apache.commons.lang3.concurrent.ConcurrentException;

import edu.illinois.cs.cogcomp.entityComparison.core.EntityComparison;
import edu.illinois.cs.cogcomp.wikifier.annotation.CachingCurator;
import edu.illinois.cs.cogcomp.wikifier.apps.MemoryMonitor;
import edu.illinois.cs.cogcomp.wikifier.inference.entailment.PathFinder;
import edu.illinois.cs.cogcomp.wikifier.utils.XmlModel;
import edu.illinois.cs.cogcomp.wikifier.utils.datastructure.StringMap;
import edu.illinois.cs.cogcomp.wikifier.utils.io.CompressionUtils;
import edu.illinois.cs.cogcomp.wikifier.utils.io.StopWords;
import edu.illinois.cs.cogcomp.wikifier.wiki.access.TitleNameNormalizer;
import edu.illinois.cs.cogcomp.wikifier.wiki.access.WikiAccess;


public class GlobalParameters {

	public static int THREAD_NUM = Math.min(8, Runtime.getRuntime().availableProcessors());
	
	public static volatile int numberOfRankerFeatures = -1;

    public static LevWordNetManager wordnet = null;
	
	/**
	 * Curator parameters
	 */
	public static CachingCurator curator = null;
	
    /**********************************************************************
     * End of ablation study section
     ***********************************************************************/
	
	public static WikiAccess wikiAccess = null;
		
    public static StopWords stops = null;

    public static EntityComparison NESimMetric/* = new EntityComparison()*/;

    public static PathFinder wnsim;
    
    public static GlobalPaths paths = GlobalPaths.defaultInstance();
    
    public static WikifierParameters params = WikifierParameters.defaultInstance();
    
    public static SystemSettings settings = SystemSettings.defaultInstance();
    
    private static BackgroundInitializer<Map<String,String[]>> titleToCategory = null;
    
    private static volatile boolean initialized = false;
    
    /**
     * The settings rely on the variable names. Please be careful while
     * refractoring the variable names.
     * @author cheng88
     *
     */
    @XmlRootElement(name="wikifierConfigurations")
    public static class SettingManager{
        public GlobalPaths paths;
        public WikifierParameters parameters;
        public SystemSettings settings;
        // Default parameters
        public SettingManager(){
            parameters = WikifierParameters.defaultInstance();
            paths = GlobalPaths.defaultInstance();
            settings = SystemSettings.defaultInstance();
        }
        public SettingManager(Object loadGlobal){
            parameters = GlobalParameters.params;
            paths = GlobalParameters.paths;
            settings = GlobalParameters.settings;
        }
    }

    public static synchronized void loadConfig(String settingFile) throws Exception{
        // Only load once
        if(initialized)
            return;
        long startTime = System.currentTimeMillis();
        SettingManager manager = XmlModel.load(SettingManager.class, settingFile);
        loadSettings(manager);
        long endTime = System.currentTimeMillis();
        System.out.println("Done initializing the system: "+(endTime-startTime)+" milliseconds elapsed");
        System.out.println("Memory usage : "+ (MemoryMonitor.usedMemory()>>20)+" MB");
        initialized = true;
    }
    
    public static synchronized void loadSettings(SettingManager manager) throws Exception{
        
        if (params.preset != null)
            params.preset.apply();
        // Loads config xml overrides default
        paths = manager.paths;
        params = manager.parameters;
        settings = manager.settings;
        
        // Initialize APIs
        stops = new StopWords(paths.stopwords);
        wnsim = new PathFinder(paths.wordNetDictionaryPath);
        NESimMetric = new EntityComparison(paths.neSimPath);
        titleToCategory = title2CategoryInitializer(new File(GlobalParameters.paths.protobufferAccessDir,"TitleSummaryData.txt"));
        titleToCategory.start();
        curator = manager.settings.createCurator(
                paths.curatorCache,
                paths.nerConfig);
        wikiAccess = manager.settings.wikiAccessProvider.getAccess(
                getWikiSummaryPath(),
                paths.protobufferAccessDir);
        wordnet = LevWordNetManager.getInstance(paths.wordnetConfig);
        try{
            int overrideThreadCount = Integer.parseInt(settings.featureExtractionThreadCount);
            if (overrideThreadCount > 0)
                THREAD_NUM = overrideThreadCount;
        }catch(NumberFormatException e){
            // Use default settings when we can't get a positive integer as threadCount
        }

    }
    
	protected static String getWikiSummaryPath(){
	    return Paths.get(GlobalParameters.paths.protobufferAccessDir,"WikiSummary.proto.save").toString();
	}
	
	/**
	 * TODO: Replace this parsing code with auto-parsing xml
	 * @param path
	 * @return
	 */
    private static BackgroundInitializer<Map<String, String[]>> title2CategoryInitializer(final File path) {
        return new BackgroundInitializer<Map<String, String[]>>() {
            @Override
            protected Map<String, String[]> initialize() throws Exception {
                Map<String, String[]> titleToCategory = new StringMap<>();
                try {
                    InputStream is = CompressionUtils.mmapedStream(path);
                    LineIterator it = IOUtils.lineIterator(is, StandardCharsets.UTF_8);
                    while (it.hasNext()) {
                        String line = it.next();
                        String wikiTitle = StringUtils.substringBetween(line, "title=", "\t");
                        wikiTitle = TitleNameNormalizer.normalize(wikiTitle);
                        it.next();// skip the nationalities
                        it.next();// skip the coarse category types.
                        String[] cats = StringUtils.split(it.next(), '\t');
                        String[] storeCats = new String[cats.length - 1];
                        for (int i = 1; i < cats.length; i++) {
                            storeCats[i - 1] = cats[i].intern();
                        }
                        it.next();// skip an empty line
                        titleToCategory.put(wikiTitle, storeCats);
                    }
                    it.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    System.exit(0);
                }
                return titleToCategory;
            }
        };
    }

    public static String[] getCategories(String title){
        String[] cats;
        try {
            cats = titleToCategory.get().get(title);
            if (cats != null)
                return cats;
        } catch (ConcurrentException e) {
            e.printStackTrace();
        }
        return ArrayUtils.EMPTY_STRING_ARRAY;
    }
    
    public static boolean hasTitle(String url){
        try {
            return titleToCategory.get().containsKey(url);
        } catch (ConcurrentException e) {
            e.printStackTrace();
        }
        return false;
    }
    
    public static void dumpConfigXmls(String dir) throws JAXBException, IOException{
        new File(dir).mkdirs();

        for(ParameterPresets preset:ParameterPresets.values()){
            preset.apply();
//            params.preset = preset;
            File f = new File(dir,preset.name()+".xml");
            XmlModel.write(new SettingManager(null), f.getPath());
            List<String> lines = FileUtils.readLines(f);
            lines.add(1, "<!-- This is an auto-generated configuration file. Manually set options will override\n" +
            		"the parameter presets. To set a parameter to null, simply delete the correspoding xml tags.\n" +
            		"Pre-populating the configuration with preset can reduce the cluttering in this file but\n" +
            		"please make sure you understand what each preset does.-->");
            FileUtils.writeLines(f, lines);
        }
    }
	
    /**
     * Writes default configs
     * @param args
     * @throws Exception
     */
	public static void main(String[] args) throws Exception{
//		loadConfig("configs/DEFAULT.xml");
	    dumpConfigXmls("configs");
	}

}

