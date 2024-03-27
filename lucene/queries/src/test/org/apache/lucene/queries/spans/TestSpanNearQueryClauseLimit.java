package org.apache.lucene.queries.spans;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

import java.util.LinkedList;
import java.util.UUID;

public class TestSpanNearQueryClauseLimit extends LuceneTestCase {

    private static final String FIELD_NAME = "field";
    private static final int NUM_DOCUMENTS = 5000;

    /**
     * Creates an index with NUM_DOCUMENTS documents. Each document has a text field in the form of "abc test foo_".
     */
    private Directory createIndex() throws Exception {
        Directory dir = newDirectory();
        try (IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig())) {
            for (int i = 0; i < NUM_DOCUMENTS; i++) {
                Document doc = new Document();
                doc.add(new TextField("field", "abc test foo_" + UUID.randomUUID(), Field.Store.YES));
                writer.addDocument(doc);
            }
            writer.commit();
        }
        return dir;
    }

    public void testSpanNearQueryClauseLimit() throws Exception {
        Directory dir = createIndex();

        // Find documents that match "test foo.*", which should match all documents.
        try (IndexReader reader = DirectoryReader.open(dir)) {
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
            TopDocs docs = new IndexSearcher(reader).search(query, 10);
            System.out.println(docs.totalHits);
        }

        dir.close();
    }

    public void testMultiPhraseQuery() throws Exception {
        Directory dir = createIndex();

        // Find documents that match "test foo.*", which should match all documents.
        try (IndexReader reader = DirectoryReader.open(dir)) {
            IndexSearcher searcher = newSearcher(reader);
            MultiPhraseQuery.Builder querybuilder = new MultiPhraseQuery.Builder();

            // Add the first term "test"
            querybuilder.add(new Term(FIELD_NAME, "test"));

            LinkedList<Term> termsWithPrefix = new LinkedList<>();

            // Find all terms that start with the second term "foo"
            String prefix = "foo";
            TermsEnum termsEnum = MultiTerms.getTerms(reader, FIELD_NAME).iterator();
            termsEnum.seekCeil(new BytesRef(prefix));
            do {
                String s = termsEnum.term().utf8ToString();
                if (s.startsWith(prefix)) {
                    termsWithPrefix.add(new Term(FIELD_NAME, s));
                } else {
                    break;
                }
            } while (termsEnum.next() != null);

            querybuilder.add(termsWithPrefix.toArray(new Term[0]));
            MultiPhraseQuery query = querybuilder.build();

            TopDocs topDocs = searcher.search(query, 10000);
            System.out.println(topDocs.totalHits);
        }

        dir.close();
    }
}
