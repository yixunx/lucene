package org.apache.lucene.queries.spans;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

import java.util.UUID;

public class TestSpanNearQueryClauseLimit extends LuceneTestCase {

    private static final String FIELD_NAME = "field";
    private static final int NUM_DOCUMENTS = 1025;

    public void testSpanNearQueryClauseLimit() throws Exception {
        Directory dir = newDirectory();

        // Create index with 2000 documents. Each document has a text field in the form of "abc test foo_".
        try (IndexWriter iw = new IndexWriter(dir, new IndexWriterConfig())) {
            for (int i = 0; i < NUM_DOCUMENTS; i++) {
                iw.addDocument(doc());
            }
            iw.commit();
        }

        // Find documents that match "test foo.*", which should match all documents.
        try (IndexReader ir = DirectoryReader.open(dir)) {
            Query query = new SpanNearQuery.Builder(FIELD_NAME, true)
                    .setSlop(0)
                    .addClause(new SpanTermQuery(new Term(FIELD_NAME, "test")))
                    .addClause(new SpanMultiTermQueryWrapper<>(new PrefixQuery(new Term(FIELD_NAME, "foo"))))
                    .build();

            // This throws exception if NUM_DOCUMENTS is > 1024.
            // ```
            // org.apache.lucene.search.IndexSearcher$TooManyNestedClauses: Query contains too many nested clauses;
            // maxClauseCount is set to 1024
            // ```
            TopDocs docs = new IndexSearcher(ir).search(query, 10);
            System.out.println(docs.totalHits);
        }

        dir.close();
    }

    private static Document doc() {
        Document doc = new Document();
        doc.add(new TextField("field", "abc test foo_" + UUID.randomUUID(), Field.Store.YES));
        return doc;
    }
}
