package edu.illinois.cs.cogcomp.wikifier.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

/**
 * Accepts JAXB compatible model classes and performs the IO/parsing.
 * All operations are thread-safe. You can choose either use the static method 
 * to load or extend this class to utilize the protected parsing methods.
 * @author cheng88
 *
 */
public abstract class XmlModel {
   
    static ConcurrentMap<Class<?>,JAXBContext> contextCache = new ConcurrentHashMap<>();
    
    private static JAXBContext getContext(Class<?> clazz) throws JAXBException {
        if (!contextCache.containsKey(clazz)){
            contextCache.put(clazz, JAXBContext.newInstance(clazz));
        }
        return contextCache.get(clazz);
    }
    
    private static ThreadLocal<Map<Class<?>,Unmarshaller>> unmarshallers = 
            new ThreadLocal<Map<Class<?>,Unmarshaller>>(){
                @Override
                public Map<Class<?>,Unmarshaller> initialValue(){
                    return new HashMap<Class<?>,Unmarshaller>();
                }
            };
    
    private static Unmarshaller getUnmarshaller(Class<?> clazz) throws JAXBException {
        Map<Class<?>,Unmarshaller> localPool = unmarshallers.get();
        if (!localPool.containsKey(clazz)){
            localPool.put(clazz, getContext(clazz).createUnmarshaller());
        }
        return localPool.get(clazz);
    }
            
            
    protected abstract Class<?> modelClass();

    /**
     * Marshals an object
     * @param o
     * @param filename
     * @throws JAXBException
     */
    public static void write(Object o,String filename) throws JAXBException{
        Marshaller m = getContext(o.getClass()).createMarshaller();
        m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        m.marshal(o, new File(filename));
    }
    
    public static String toXmlString(Object o) throws Exception{
        Marshaller m = getContext(o.getClass()).createMarshaller();
        m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        StringWriter w = new StringWriter();
        m.marshal(o, w);
        return w.toString();
    }
    
    @SuppressWarnings("unchecked")
    public static <T> T load(Class<T> clazz,String filename){
        try{
            return (T) getUnmarshaller(clazz).unmarshal(new InputStreamReader(new FileInputStream(filename),"UTF-8"));
        }catch(Exception e){
            e.printStackTrace();
        }
        return null;
    }
    
    @SuppressWarnings("unchecked")
    protected <T> T parseFrom(String filename){
        try{
            return (T) getUnmarshaller(modelClass()).unmarshal(new InputStreamReader(new FileInputStream(filename),"UTF-8"));
        }catch(Exception e){
            e.printStackTrace();
        }
        return null;
    }

}
