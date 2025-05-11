import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Vector‑space search engine with
 *   • tf‑idf / max‑tf weighting
 *   • cosine similarity
 *   • quoted‑phrase AND filtering
 *   • title‑field boost
 */
public class SearchEngine implements AutoCloseable {
    private static final int MAX_RESULTS        = 300;
    private static final double TITLE_BOOST     = 5.0;

    private final InvertedIndexManager index;
    private final StopStem              stopStem;
    private final int                   totalDocs;
    private final Map<Integer, Double> idfCache     = new HashMap<>();
    private final Map<Integer, Integer> maxTFCache = new HashMap<>();

    // Cache postings in memory for the current query
    Map<Integer, Map<Integer, Integer>> bodyPostingsCache = new HashMap<>();
    Map<Integer, Map<Integer, Integer>> titlePostingsCache = new HashMap<>();

    // ────────────────────────────────────────────────────────────────────────────
    public SearchEngine(String dbName) throws IOException {
        index      = new InvertedIndexManager(dbName);
        stopStem   = new StopStem("stopwords.txt");
        totalDocs  = index.getAllPageIds().size();

        // Precompute max term-frequency for each document
        for (Integer pid : index.getAllPageIds()) {
            maxTFCache.put(pid, index.getMaxTFForPageId(pid));
        }
    }

    // ────────────────────────────────────────────────────────────────────────────
    public List<SearchResult> search(String raw) throws IOException {
        if (raw == null || raw.isBlank()) return Collections.emptyList();

        /*──────────────── 1.  Parse query ───────────────*/
        List<String> phrases = new ArrayList<>();
        List<String> terms   = new ArrayList<>();

        Pattern parser = Pattern.compile("\"([^\"]+)\"|(\\S+)");
        Matcher m = parser.matcher(raw);
        while (m.find()) {
            String phrase = m.group(1);
            String token  = m.group(2);
            if (phrase != null) {
                String processed = processPhrase(phrase);
                if (!processed.isEmpty()) {
                    phrases.add(processed);
//                    Collections.addAll(terms, processed.split(" ")); // keep duplicates
                    Collections.addAll(terms, processed); // keep duplicates
                }
            } else if (token != null) {
                addTerm(token, terms);
            }
        }
//        // If no quoted phrase but query contains multiple words, treat the whole query as a phrase
//        if (phrases.isEmpty()) {
//            String defaultPhrase = processPhrase(raw);
//            if (defaultPhrase.contains(" ")) {
//                List<String> words = new ArrayList<>(Arrays.asList(defaultPhrase.split(" ")));
//                List<String> ngram_list = new ArrayList<>();
//                int MAX_NGRAM = 3;
//                for (int n = 2; n <= MAX_NGRAM; n++) {
//                    for (int i = 0; i + n <= words.size(); i++) {
//                        String ngram = String.join(" ", words.subList(i, i + n));
//                        ngram_list.add(ngram);
//                    }
//                }
//                phrases.addAll(ngram_list);
////                phrases.add(defaultPhrase);
//                // Don't add terms again - they were already added during initial tokenization
//                // Collections.addAll(terms, defaultPhrase.split(" "));
//            }
//        }

        System.out.println("Phrases: " + phrases);
        System.out.println("Terms: " + terms);  
        if (phrases.isEmpty() && terms.isEmpty()) return Collections.emptyList();

        Set<Integer> candidates = new HashSet<>(index.getAllPageIds());
        for (String ph : phrases) {
            Integer phId = index.getWordIdIfExists(ph);
            Set<Integer> hits = new HashSet<>();
            if (phId != null) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<Integer,Integer> bodyPostings = (Map<Integer,Integer>) index.getBodyPostings(phId);
                    if (bodyPostings != null) hits.addAll(bodyPostings.keySet());
                    @SuppressWarnings("unchecked")
                    Map<Integer,Integer> titlePostings = (Map<Integer,Integer>) index.getTitlePostings(phId);
                    if (titlePostings != null) hits.addAll(titlePostings.keySet());
                } catch (IOException e) {
                    // If we can't read postings, treat as no matches
                    System.err.println("Error reading postings for phrase '" + ph + "': " + e.getMessage());
                }
            }
            // If no matches found for this phrase, no documents can match the query
            if (hits.isEmpty() && terms.isEmpty()){
                return Collections.emptyList();
            } else if (!hits.isEmpty()){
                candidates.retainAll(hits);
            }
//            if (hits.isEmpty() && terms.isEmpty()) return Collections.emptyList();
//            candidates.retainAll(hits);
            if (candidates.isEmpty() && terms.isEmpty()) return Collections.emptyList();
        }
        System.out.println("Candidates: " + candidates);

        Map<String,Integer> termToId = new HashMap<>();
        Map<String,Integer> df        = new HashMap<>();
        Map<String,Double>  qv        = new HashMap<>();

        // calculate tf of query terms
        int maxNumberOfTerm = 1;
        Map<String, Integer> termMap = new HashMap<>();
        for (String term: terms){
            termMap.merge(term, 1, Integer::sum);
            if (termMap.get(term) > maxNumberOfTerm){
                maxNumberOfTerm = termMap.get(term);
            }
        }
        // filter out duplicated query terms
        Set<String> termSet = new HashSet<>(terms);
        terms = new ArrayList<>(termSet);

        for (String t : terms) {
            int wid;
            if (index.getWordIdIfExists(t) == null){
                continue;
            }
            try { wid = index.getWordIdIfExists(t); }
            catch (IOException e) { continue; }
            if (wid == 0) continue; // Skip if term doesn't exist in index
            termToId.put(t, wid);

            // Compute or retrieve cached IDF with exception handling
            double idf;
            if (idfCache.containsKey(wid)) {
                idf = idfCache.get(wid);
            } else {
                Set<Integer> docs = new HashSet<>();
                try {
                    @SuppressWarnings("unchecked")
                    Map<Integer, Integer> body = (Map<Integer, Integer>) index.getBodyPostings(wid);
                    if (body != null) docs.addAll(body.keySet());
                    @SuppressWarnings("unchecked")
                    Map<Integer, Integer> title = (Map<Integer, Integer>) index.getTitlePostings(wid);
                    if (title != null) docs.addAll(title.keySet());
                } catch (IOException e) {
                    // ignore and treat as no docs
                }
                double computedIdf = docs.isEmpty() ? 0 : Math.log10((double) totalDocs / docs.size());
                idfCache.put(wid, computedIdf);
                idf = computedIdf;
            }

            double tf = (double) termMap.get(t) / maxNumberOfTerm;
            qv.merge(t, tf * idf, Double::sum);              // query tf == 1 each occurrence
        }
        double qMag = Math.sqrt(qv.values().stream().mapToDouble(w -> w*w).sum());
        if (qMag == 0) return Collections.emptyList();

        List<ScoredDoc> scored = new ArrayList<>();

        for (int docId : candidates) {
            Map<String,Integer> tfBody  = new HashMap<>();
            Map<String,Integer> tfTitle = new HashMap<>();
            int maxBody = 1, maxTitle = 1;

            for (String t : terms) {
                Integer widObj = termToId.get(t);
                if (widObj == null) continue; // Skip if term wasn't mapped
                int wid = widObj;
                
                // Load and cache postings just once per term
                if (!bodyPostingsCache.containsKey(wid)) {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<Integer, Integer> postings = (Map<Integer, Integer>) index.getBodyPostings(wid);
                        bodyPostingsCache.put(wid, postings != null ? postings : Collections.emptyMap());
                    } catch (IOException e) {
                        bodyPostingsCache.put(wid, Collections.emptyMap());
                    }
                }
                
                if (!titlePostingsCache.containsKey(wid)) {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<Integer, Integer> postings = (Map<Integer, Integer>) index.getTitlePostings(wid);
                        titlePostingsCache.put(wid, postings != null ? postings : Collections.emptyMap());
                    } catch (IOException e) {
                        titlePostingsCache.put(wid, Collections.emptyMap());
                    }
                }

                // Then use cached postings in your calculation
                int tb = bodyPostingsCache.getOrDefault(wid, Collections.emptyMap()).getOrDefault(docId, 0);
                int tt = titlePostingsCache.getOrDefault(wid, Collections.emptyMap()).getOrDefault(docId, 0);
                tfBody.put(t, tb);
                tfTitle.put(t, tt);
            }

            Map<String,Double> dv = new HashMap<>();
            for (String t : terms) {
                Integer widObj = termToId.get(t);
                if (widObj == null) continue; // Skip if term wasn't mapped
                int wid = widObj;
                
                double idf = idfCache.getOrDefault(wid, 0.0);
                Integer tb = tfBody.get(t);
                Integer tt = tfTitle.get(t);
                
                // Handle potential null values from previous loop
                if (tb == null) tb = 0;
                if (tt == null) tt = 0;

                // Use log-scaled tf for body and title
                double tfBodyWeight = (tb > 0) ? (1.0 + Math.log(tb)) : 0.0;
                double tfTitleWeight = (tt > 0) ? (1.0 + Math.log(tt)) : 0.0;

                if (tfBodyWeight > 0) {
                    dv.merge(t, tfBodyWeight * idf, Double::sum);
                }
                if (tfTitleWeight > 0) {
                    dv.merge(t, tfTitleWeight * idf * TITLE_BOOST, Double::sum);
                }
            }
            if (dv.isEmpty()) continue;

            // sum of all tf-idf weights (with title boost)
            double score = dv.values().stream().mapToDouble(Double::doubleValue).sum();
            if (score > 0) scored.add(new ScoredDoc(docId, score));
        }

        scored.sort(Comparator.comparing(ScoredDoc::score).reversed());

        List<SearchResult> out = new ArrayList<>();
        for (int i=0;i<Math.min(scored.size(),MAX_RESULTS);i++) {
            int id = scored.get(i).docId;
            InvertedIndexManager.PageInfo info = index.getPageInfo(id);
            if (info==null) continue;
            out.add(new SearchResult(id, info.url, info.title, scored.get(i).score, info.lastModifiedDate, info.size));
        }
        return out;
    }

    private int getTf(Object postingsObj, int doc) throws IOException {
        if (postingsObj == null) return 0;
        @SuppressWarnings("unchecked")
        Map<Integer, Integer> postings = (Map<Integer, Integer>) postingsObj;
        return postings.getOrDefault(doc, 0);
    }

    private void addTerm(String raw,List<String> sink){
        raw = raw.toLowerCase();
        if (stopStem.isStopWord(raw)) return;
        String stem = stopStem.stem(raw);
        if (!stem.isEmpty()) sink.add(stem);
    }

    private String processPhrase(String phrase){
        return Arrays.stream(phrase.toLowerCase().split("\\s+"))
                     .filter(w->!stopStem.isStopWord(w))
                     .map(stopStem::stem)
                     .filter(s->!s.isEmpty())
                     .collect(Collectors.joining(" "));
    }


    @Override public void close() { index.close(); }

    private record ScoredDoc(int docId, double score) { }

    
    public static class SearchResult {
        private final int pageId;
        private final String url;
        private final String title;
        private final double score;
        private final long lastModifiedDate;
        private final long size;
        private String lastModifiedStr;
        private String keywordSummary;
        
        public SearchResult(int pageId, String url, String title, double score) {
            this(pageId, url, title, score, 0, 0);
        }
        
        public SearchResult(int pageId, String url, String title, double score, long lastModifiedDate, long size) {
            this.pageId = pageId;
            this.url = url;
            this.title = title;
            this.score = score;
            this.lastModifiedDate = lastModifiedDate;
            this.size = size;
        }
        
        public int getPageId() {
            return pageId;
        }
        
        public String getUrl() {
            return url;
        }
        
        public String getTitle() {
            return title;
        }
        
        public double getScore() {
            return score;
        }
        
        public long getLastModifiedDate() {
            return lastModifiedDate;
        }
        
        public long getSize() {
            return size;
        }

        public void setLastModifiedStr(String s)       { this.lastModifiedStr = s; }
        public String getLastModifiedStr()             { return lastModifiedStr; }

        public void setKeywordSummary(String summary)  { this.keywordSummary = summary; }
        public String getKeywordSummary()              { return keywordSummary; }
        }
}
