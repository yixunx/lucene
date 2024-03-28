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
    private static final int NUM_DOCUMENTS = 1025;

    /**
     * Creates an index with NUM_DOCUMENTS documents. Each document has a text field in the form of "abc foo bar_[UUID]".
     */
    private Directory createIndex() throws Exception {
        Directory dir = newDirectory();
        try (IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig())) {
            for (int i = 0; i < NUM_DOCUMENTS; i++) {
                Document doc = new Document();
                doc.add(new TextField("field", "abc foo bar_" + UUID.randomUUID(), Field.Store.YES));
                writer.addDocument(doc);
            }
            writer.commit();
        }
        return dir;
    }

    // SpanNearQuery hits limit of 1024 clauses
    public void testSpanNearQueryClauseLimit() throws Exception {
        Directory dir = createIndex();

        // Find documents that match "abc <some term> bar.*", which should match all documents.
        try (IndexReader reader = DirectoryReader.open(dir)) {
            Query query = new SpanNearQuery.Builder(FIELD_NAME, true)
                    .setSlop(1)
                    .addClause(new SpanTermQuery(new Term(FIELD_NAME, "abc")))
                    .addClause(new SpanMultiTermQueryWrapper<>(new PrefixQuery(new Term(FIELD_NAME, "bar"))))
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

    // It seems that if slop = 0 (e.g. searching for "foo bar.*") then MultiPhraseQuery can be used instead, and this
    // doesn't hit the clause limit because MultiPhraseQuery doesn't construct one sub-query for each term.
    public void testMultiPhraseQuery() throws Exception {
        Directory dir = createIndex();

        // Find documents that match "foo bar.*", which should match all documents.
        try (IndexReader reader = DirectoryReader.open(dir)) {
            IndexSearcher searcher = newSearcher(reader);
            MultiPhraseQuery.Builder querybuilder = new MultiPhraseQuery.Builder();

            // Add the first term "foo"
            querybuilder.add(new Term(FIELD_NAME, "foo"));

            LinkedList<Term> termsWithPrefix = new LinkedList<>();

            // Find all terms that start with the second term "bar"
            String prefix = "bar";
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
