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
 *
 * Non‑essential heuristics (recency, length, etc.) have been deliberately
 * omitted to match the assignment specification.
 */
public class SearchEngine implements AutoCloseable {
    private static final int MAX_RESULTS        = 50;
    private static final double TITLE_BOOST     = 5.0;

    private final InvertedIndexManager index;
    private final StopStem              stopStem;
    private final int                   totalDocs;

    // ────────────────────────────────────────────────────────────────────────────
    public SearchEngine(String dbName) throws IOException {
        index      = new InvertedIndexManager(dbName);
        stopStem   = new StopStem("stopwords.txt");
        totalDocs  = index.getAllPageIds().size();
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
                    Collections.addAll(terms, processed.split(" ")); // keep duplicates
                }
            } else if (token != null) {
                addTerm(token, terms);
            }
        }
        System.out.println("Phrases: " + phrases);
        System.out.println("Terms: " + terms);  
        if (phrases.isEmpty() && terms.isEmpty()) return Collections.emptyList();

        Set<Integer> candidates = new HashSet<>(index.getAllPageIds());
        for (String ph : phrases) {
            Set<Integer> body  = new HashSet<>(index.searchPhraseInBody(ph));
            Set<Integer> title = new HashSet<>(index.searchPhraseInTitle(ph));
            body.addAll(title);
            candidates.retainAll(body);
            if (candidates.isEmpty()) return Collections.emptyList();
        }
        System.out.println("Candidates: " + candidates);

        Map<String,Integer> termToId = new HashMap<>();
        Map<String,Integer> df        = new HashMap<>();
        Map<String,Double>  qv        = new HashMap<>();

        for (String t : terms) {
            int wid;
            try { wid = index.getWordId(t); }
            catch (IOException e) { continue; }
            termToId.put(t, wid);

            Set<Integer> docs = new HashSet<>();
            @SuppressWarnings("unchecked")
            Map<Integer,Integer> body  = (Map<Integer,Integer>) index.getBodyPostings(wid);
            @SuppressWarnings("unchecked")
            Map<Integer,Integer> title = (Map<Integer,Integer>) index.getTitlePostings(wid);
            if (body  != null) docs.addAll(body.keySet());
            if (title != null) docs.addAll(title.keySet());
            df.put(t, docs.size());

            double idf = docs.isEmpty()? 0 : Math.log10((double) totalDocs / docs.size());
            qv.merge(t, idf, Double::sum);              // query tf == 1 each occurrence
        }
        double qMag = Math.sqrt(qv.values().stream().mapToDouble(w -> w*w).sum());
        if (qMag == 0) return Collections.emptyList();

        List<ScoredDoc> scored = new ArrayList<>();

        for (int docId : candidates) {
            Map<String,Integer> tfBody  = new HashMap<>();
            Map<String,Integer> tfTitle = new HashMap<>();
            int maxBody = 1, maxTitle = 1;

            for (String t : terms) {
                int wid = termToId.get(t);
                int tb  = getTf(index.getBodyPostings(wid),  docId);
                int tt  = getTf(index.getTitlePostings(wid), docId);
                tfBody.put(t, tb);
                tfTitle.put(t, tt);
                if (tb > maxBody)   maxBody  = tb;
                if (tt > maxTitle)  maxTitle = tt;
            }

            Map<String,Double> dv = new HashMap<>();
            for (String t : terms) {
                double idf = df.getOrDefault(t,0)==0? 0 : Math.log10((double) totalDocs / df.get(t));
                int tb = tfBody.get(t);
                int tt = tfTitle.get(t);
                if (tb>0) dv.merge(t, (tb/(double)maxBody)*idf, Double::sum);
                if (tt>0) dv.merge(t, (tt/(double)maxTitle)*idf*TITLE_BOOST, Double::sum);
            }
            if (dv.isEmpty()) continue;

            double dot=0, dMag=0;
            for (Map.Entry<String,Double> e : dv.entrySet()) {
                double dw = e.getValue();
                dMag += dw*dw;
                dot  += dw * qv.getOrDefault(e.getKey(),0.0);
            }
            dMag = Math.sqrt(dMag);
            if (dMag==0) continue;
            double sim = dot / (qMag*dMag);
            if (sim>0) scored.add(new ScoredDoc(docId,sim));
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

    private int getTf(Object postingsObj, int doc) {
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
