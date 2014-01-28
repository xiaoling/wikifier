package edu.illinois.cs.cogcomp.wikifier.wiki.importing;

import java.util.EnumSet;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;

import edu.illinois.cs.cogcomp.wikifier.utils.datastructure.DiskArray.ByteArrayIndexer;
import edu.illinois.cs.cogcomp.wikifier.utils.lucene.Lucene;
import edu.illinois.cs.cogcomp.wikifier.wiki.access.WikiAccess.SurfaceData;
import edu.illinois.cs.cogcomp.wikifier.wiki.access.WikiAccess.SurfaceFormFields;
import edu.illinois.cs.cogcomp.wikifier.wiki.access.WikiAccess.WikiData;
import edu.illinois.cs.cogcomp.wikifier.wiki.access.WikiAccess.WikiDataFields;

public class WikiAccessIndexer {
    
    protected static void migrateIndex(String pathToMainWikiDataProtobuffersIndex, String pathToSurfaceFormsDataIndex) throws Exception {
        IndexReader completeIndexReader = Lucene.reader(pathToMainWikiDataProtobuffersIndex);

        EnumSet<WikiDataFields> binaryFields = EnumSet.complementOf(EnumSet.of(WikiDataFields.TitleID, WikiDataFields.TitleName));

        IndexWriter writer = Lucene.storeOnlyWriter("../Data/ArrayIndex/WikiData");
        for (int docId = 0; docId < completeIndexReader.numDocs(); docId++) {

            Document oldDoc = completeIndexReader.document(docId);
            int titleId = Integer.parseInt(oldDoc.getBinaryValue(WikiDataFields.TitleID.name()).utf8ToString());

            Document doc = new Document();
            doc.add(new StoredField(WikiDataFields.TitleID.name(), titleId));
            for (WikiDataFields binaryField : binaryFields) {
                doc.add(new StoredField(binaryField.name(), oldDoc.getBinaryValue(binaryField.name()).bytes));
            }
            writer.addDocument(doc);
            if (docId % 10000 == 0)
                System.out.println(docId);
        }
        writer.close();
        completeIndexReader.close();

        writer = Lucene.storeOnlyWriter("../Data/Lucene4Index/SurfaceFormsInfo");
        IndexReader surfaceFormReader = Lucene.reader(pathToSurfaceFormsDataIndex);
        for (int docId = 0; docId < surfaceFormReader.numDocs(); docId++) {

            Document oldDoc = surfaceFormReader.document(docId);
            String s = oldDoc.getBinaryValue(SurfaceFormFields.SurfaceForm.name()).utf8ToString();
            byte[] surfaceFormInfo = oldDoc.getBinaryValue(SurfaceFormFields.SurfaceFormSummaryProto.name()).bytes;
            Document doc = new Document();
            doc.add(new StoredField(SurfaceFormFields.SurfaceForm.name(), s));
            doc.add(new StoredField(SurfaceFormFields.SurfaceFormSummaryProto.name(), surfaceFormInfo));
            writer.addDocument(doc);
            if (docId % 10000 == 0)
                System.out.println(docId);
        }
        writer.forceMerge(1);
        writer.commit();
        writer.close();
        surfaceFormReader.close();
    }
    
    protected static void migrateFileBasedIndex(String pathToMainWikiDataProtobuffersIndex, String pathToSurfaceFormsDataIndex) throws Exception {
        IndexReader completeIndexReader = Lucene.reader(pathToMainWikiDataProtobuffersIndex);

        ByteArrayIndexer indexer = new ByteArrayIndexer("../Data/ArrayIndex2/WikiData");
        for (int docId = 0; docId < completeIndexReader.numDocs(); docId++) {

            Document oldDoc = completeIndexReader.document(docId);
            byte[] basicInfo = oldDoc.getBinaryValue(WikiDataFields.BasicInfo.name()).bytes;
            byte[] lexInfo = oldDoc.getBinaryValue(WikiDataFields.LexicalInfo.name()).bytes;
            byte[] semanticInfo = oldDoc.getBinaryValue(WikiDataFields.SemanticInfo.name()).bytes;
            WikiData data = new WikiData(basicInfo,lexInfo,semanticInfo);
            indexer.add(data.serialize());
            if (docId % 10000 == 0)
                System.out.println(docId);
        }
        indexer.close();
        completeIndexReader.close();


        indexer = new ByteArrayIndexer("../Data/ArrayIndex2/Surfaces");
        IndexReader surfaceFormReader = Lucene.reader(pathToSurfaceFormsDataIndex);
        for (int docId = 0; docId < surfaceFormReader.numDocs(); docId++) {

            Document oldDoc = surfaceFormReader.document(docId);
            String s = oldDoc.getBinaryValue(SurfaceFormFields.SurfaceForm.name()).utf8ToString();
            byte[] surfaceFormInfo = oldDoc.getBinaryValue(SurfaceFormFields.SurfaceFormSummaryProto.name()).bytes;
            indexer.add(new SurfaceData(s,surfaceFormInfo).serialize());

            if (docId % 10000 == 0)
                System.out.println(docId);
        }

        indexer.close();
        surfaceFormReader.close();
    }
}
