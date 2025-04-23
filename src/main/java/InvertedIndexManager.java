import jdbm.RecordManager;
import jdbm.RecordManagerFactory;
import jdbm.helper.FastIterator;
import jdbm.htree.HTree;

import java.io.IOException;
import java.io.Serializable;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;

/**
 * This class manages the JDBM database for storing inverted indexes and related data.
 */
public class InvertedIndexManager implements AutoCloseable {
    private final String dbName;
    private RecordManager recman;

    private HTree pageIdToUrlMap;
    private HTree urlToPageIdMap;
    
    private HTree wordIdToWordMap;
    private HTree wordToWordIdMap;
    
    private HTree pageInfoMap;
    
    private HTree bodyInvertedIndex;
    private HTree titleInvertedIndex;
    
    private HTree pageIdToBodyWordsMap;
    private HTree pageIdToTitleWordsMap;
    
    private HTree counterMap;

    private HTree maxTFForPageId;
    
    /**
     * Class to store information about a page
     */
    public static class PageInfo implements Serializable {
        private static final long serialVersionUID = 1L;
        
        public String url;
        public String title;
        public long lastModifiedDate;
        public long size;
        public List<Integer> childPageIds = new ArrayList<>();
        
        public PageInfo(String url, String title, long lastModifiedDate, long size) {
            this.url = url;
            this.title = title;
            this.lastModifiedDate = lastModifiedDate;
            this.size = size;
        }
    }
    
    /**
     * Constructor - initializes the database and maps
     */
    public InvertedIndexManager(String dbName) {
        this.dbName = dbName;
        
        try {
            initializeDB();
        } catch (IOException e) {
            System.err.println("Error initializing database: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Initialize the database and all required maps
     */
    private void initializeDB() throws IOException {
        // Create or open the database
        recman = RecordManagerFactory.createRecordManager(dbName);
        
        // Initialize maps
        pageIdToUrlMap = loadOrCreateHTree("pageIdToUrl");
        urlToPageIdMap = loadOrCreateHTree("urlToPageId");
        
        wordIdToWordMap = loadOrCreateHTree("wordIdToWord");
        wordToWordIdMap = loadOrCreateHTree("wordToWordId");
        
        pageInfoMap = loadOrCreateHTree("pageInfo");
        
        bodyInvertedIndex = loadOrCreateHTree("bodyInvertedIndex");
        titleInvertedIndex = loadOrCreateHTree("titleInvertedIndex");
        
        pageIdToBodyWordsMap = loadOrCreateHTree("pageIdToBodyWords");
        pageIdToTitleWordsMap = loadOrCreateHTree("pageIdToTitleWords");
        
        counterMap = loadOrCreateHTree("counter");

        maxTFForPageId = loadOrCreateHTree("maxTFForPageId");
        
        // Initialize counters if they don't exist
        if (counterMap.get("pageId") == null) {
            counterMap.put("pageId", 0);
        }
        if (counterMap.get("wordId") == null) {
            counterMap.put("wordId", 0);
        }
        
        recman.commit();
    }
    
    /**
     * Helper method to load or create an HTree
     */
    private HTree loadOrCreateHTree(String name) throws IOException {
        long recid = recman.getNamedObject(name);
        if (recid != 0) {
            return HTree.load(recman, recid);
        } else {
            HTree tree = HTree.createInstance(recman);
            recman.setNamedObject(name, tree.getRecid());
            return tree;
        }
    }
    
    /**
     * Get a new page ID
     */
    public int getNextPageId() throws IOException {
        int nextId = (Integer) counterMap.get("pageId");
        counterMap.put("pageId", nextId + 1);
        recman.commit();
        return nextId;
    }
    
    /**
     * Get a word ID, creating a new one if the word doesn't exist
     */
    public int getWordId(String word) throws IOException {
        if (wordToWordIdMap.get(word) != null) {
            return (Integer) wordToWordIdMap.get(word);
        } else {
            int nextId = (Integer) counterMap.get("wordId");
            counterMap.put("wordId", nextId + 1);
            
            wordToWordIdMap.put(word, nextId);
            wordIdToWordMap.put(nextId, word);
            
            recman.commit();
            return nextId;
        }
    }

    public boolean hasUpdate(String url) throws IOException {
        if (urlToPageIdMap.get(url) != null){
            PageInfo page = (PageInfo) pageInfoMap.get(urlToPageIdMap.get(url));

            URL url_check_date = new URL(url);
            URLConnection connection_check_date = url_check_date.openConnection();
            connection_check_date.getLastModified();
            return (connection_check_date.getLastModified() > page.lastModifiedDate);
        } else {
            return false;
        }
    }

    public boolean hasPage(String url) throws IOException {
        return (urlToPageIdMap.get(url) != null);
    }

    public boolean hasKeyword(String url) throws IOException {
        if (urlToPageIdMap.get(url) != null){
            ArrayList<String> bodyWordList = (ArrayList<String>) pageIdToBodyWordsMap.get(urlToPageIdMap.get(url));
            ArrayList<String> titleWordList = (ArrayList<String>) pageIdToTitleWordsMap.get(urlToPageIdMap.get(url));
            return (!bodyWordList.isEmpty() || !titleWordList.isEmpty());
        } else {
            return false;
        }
    }


    /**
     * Add a page to the database
     */
    public int addPage(String url, String title, long lastModifiedDate, long size) throws IOException {
        // Check if URL already exists
        if (urlToPageIdMap.get(url) != null) {
            // add back title as the page has no title if it's child page
            if (!title.equals("")){
                PageInfo newPageInfo = getPageInfo((Integer) urlToPageIdMap.get(url));
                newPageInfo.title = title;
                pageInfoMap.put(urlToPageIdMap.get(url), newPageInfo);
                recman.commit();
            }
            return (Integer) urlToPageIdMap.get(url);
        }
        
        // Create new page ID
        int pageId = getNextPageId();
        
        // Store mappings
        urlToPageIdMap.put(url, pageId);
        pageIdToUrlMap.put(pageId, url);
        
        // Create page info and store it
        PageInfo pageInfo = new PageInfo(url, title, lastModifiedDate, size);
        pageInfoMap.put(pageId, pageInfo);
        
        // Initialize word lists for phrase search
        pageIdToBodyWordsMap.put(pageId, new ArrayList<String>());
        pageIdToTitleWordsMap.put(pageId, new ArrayList<String>());
        
        // Commit changes
        recman.commit();
        
        return pageId;
    }
    
    /**
     * Add a child page link to a parent page
     */
    public void addChildPage(int parentPageId, int childPageId) throws IOException {
        PageInfo pageInfo = (PageInfo) pageInfoMap.get(parentPageId);
        if (pageInfo != null) {
            if (!pageInfo.childPageIds.contains(childPageId)) {
                pageInfo.childPageIds.add(childPageId);
                pageInfoMap.put(parentPageId, pageInfo);
                recman.commit();
            }
        }
    }
    
    /**
     * Add a word occurrence to the body inverted index
     */
    public void addWordToBody(int pageId, String word, int frequency) throws IOException {
        int wordId = getWordId(word);
        
        // Update inverted index
        HashMap<Integer, Integer> postings = (HashMap<Integer, Integer>) bodyInvertedIndex.get(wordId);
        if (postings == null) {
            postings = new HashMap<>();
        }
        postings.put(pageId, frequency);
        bodyInvertedIndex.put(wordId, postings);
        
        // Add to word list for phrase search
        List<String> words = (List<String>) pageIdToBodyWordsMap.get(pageId);
        words.add(word);
        pageIdToBodyWordsMap.put(pageId, words);
        
        recman.commit();
    }
    
    /**
     * Add a word occurrence to the title inverted index
     */
    public void addWordToTitle(int pageId, String word, int frequency) throws IOException {
        int wordId = getWordId(word);
        
        // Update inverted index
        HashMap<Integer, Integer> postings = (HashMap<Integer, Integer>) titleInvertedIndex.get(wordId);
        if (postings == null) {
            postings = new HashMap<>();
        }
        postings.put(pageId, frequency);
        titleInvertedIndex.put(wordId, postings);
        
        // Add to word list for phrase search
        List<String> words = (List<String>) pageIdToTitleWordsMap.get(pageId);
        words.add(word);
        pageIdToTitleWordsMap.put(pageId, words);
        
        recman.commit();
    }
    
    /**
     * Get page information by page ID
     */
    public PageInfo getPageInfo(int pageId) throws IOException {
        return (PageInfo) pageInfoMap.get(pageId);
    }
    
    /**
     * Get all indexed pages
     */
    public List<Integer> getAllPageIds() throws IOException {
        List<Integer> pageIds = new ArrayList<>();
        FastIterator iter = pageIdToUrlMap.keys();
        Object key;
        
        while ((key = iter.next()) != null) {
            if (key instanceof Integer) {
                pageIds.add((Integer) key);
            }
        }
        
        return pageIds;
    }

    
    /**
     * Get top keywords for a page (by frequency)
     */
    public Map<String, Integer> getTopKeywords(int pageId, int limit) throws IOException {
        Map<String, Integer> keywords = new HashMap<>();
        
        // Collect words from body index
        FastIterator iter = bodyInvertedIndex.keys();
        Object key;
        
        while ((key = iter.next()) != null) {
            if (key instanceof Integer) {
                int wordId = (Integer) key;
                HashMap<Integer, Integer> postings = (HashMap<Integer, Integer>) bodyInvertedIndex.get(wordId);
                
                if (postings != null && postings.containsKey(pageId)) {
                    String word = (String) wordIdToWordMap.get(wordId);
                    keywords.put(word, postings.get(pageId));
                }
            }
        }

        // Collect words from body index
       iter = titleInvertedIndex.keys();

        while ((key = iter.next()) != null) {
            if (key instanceof Integer) {
                int wordId = (Integer) key;
                HashMap<Integer, Integer> postings = (HashMap<Integer, Integer>) titleInvertedIndex.get(wordId);

                if (postings != null && postings.containsKey(pageId)) {
                    String word = (String) wordIdToWordMap.get(wordId);
                    if (keywords.get(word) != null){
                        keywords.put(word, postings.get(pageId) + keywords.get(word));
                    } else {
                        keywords.put(word, postings.get(pageId));
                    }
                }
            }
        }
        
        // Sort by frequency and limit
        return sortByValueAndLimit(keywords, limit);
    }
    
    /**
     * Helper method to sort a map by value and limit the number of entries
     */
    private Map<String, Integer> sortByValueAndLimit(Map<String, Integer> map, int limit) {
        List<Map.Entry<String, Integer>> list = new ArrayList<>(map.entrySet());
        list.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));
        
        Map<String, Integer> result = new LinkedHashMap<>();
        int count = 0;
        for (Map.Entry<String, Integer> entry : list) {
            if (count >= limit) break;
            result.put(entry.getKey(), entry.getValue());
            count++;
        }
        
        return result;
    }
    
    /**
     * Search for a phrase in page body
     */
    public List<Integer> searchPhraseInBody(String phrase) throws IOException {
        String[] words = phrase.toLowerCase().split(" ");
        List<Integer> result = new ArrayList<>();
        
        // For each page, check if the phrase exists
        FastIterator iter = pageIdToBodyWordsMap.keys();
        Object key;
        
        while ((key = iter.next()) != null) {
            if (key instanceof Integer) {
                int pageId = (Integer) key;
                List<String> pageWords = (List<String>) pageIdToBodyWordsMap.get(pageId);
                
                if (containsPhrase(pageWords, words)) {
                    result.add(pageId);
                }
            }
        }
        
        return result;
    }
    
    /**
     * Search for a phrase in page title
     */
    public List<Integer> searchPhraseInTitle(String phrase) throws IOException {
        String[] words = phrase.toLowerCase().split(" ");
        List<Integer> result = new ArrayList<>();
        
        // For each page, check if the phrase exists
        FastIterator iter = pageIdToTitleWordsMap.keys();
        Object key;
        
        while ((key = iter.next()) != null) {
            if (key instanceof Integer) {
                int pageId = (Integer) key;
                List<String> pageWords = (List<String>) pageIdToTitleWordsMap.get(pageId);
                
                if (containsPhrase(pageWords, words)) {
                    result.add(pageId);
                }
            }
        }
        
        return result;
    }
    
    /**
     * Helper method to check if a list of words contains a phrase
     */
    private boolean containsPhrase(List<String> pageWords, String[] phraseWords) {
        if (pageWords.size() < phraseWords.length) {
            return false;
        }
        
        for (int i = 0; i <= pageWords.size() - phraseWords.length; i++) {
            boolean match = true;
            for (int j = 0; j < phraseWords.length; j++) {
                if (!pageWords.get(i + j).equals(phraseWords[j])) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Get postings list for a word from the body inverted index
     */
    public Object getBodyPostings(int wordId) throws IOException {
        return bodyInvertedIndex.get(wordId);
    }
    
    /**
     * Get postings list for a word from the title inverted index
     */
    public Object getTitlePostings(int wordId) throws IOException {
        return titleInvertedIndex.get(wordId);
    }

    public void sortForMaxTFForPageId(int pageId) throws IOException {
        Map<String, Integer> map = getTopKeywords(pageId, 1);
        Map.Entry<String, Integer> entry = map.entrySet().iterator().next();
        System.out.println("maxTF of pageId:"+pageId + " is " + entry.getKey() + " " + entry.getValue());
        maxTFForPageId.put(pageId, map);
    }

    public int getMaxTFForPageId(int pageId) throws IOException {
        Map<String, Integer> map = (Map<String, Integer>) maxTFForPageId.get(pageId);
        if (map != null){
            Map.Entry<String, Integer> entry = map.entrySet().iterator().next();
//            System.out.println(entry.getValue());
            return entry.getValue();
        } else {
            return 1;
        }
    }
    
    /**
     * Close the database
     */
    @Override
    public void close() {
        if (recman != null) {
            try {
                recman.commit();
                recman.close();
            } catch (IOException e) {
                System.err.println("Error closing database: " + e.getMessage());
            }
        }
    }

} 