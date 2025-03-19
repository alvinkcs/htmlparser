import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;
import java.io.FileWriter;
import java.io.BufferedWriter;

public class HtmlParser {

    public static StopStem stopStem = new StopStem("stopwords.txt");
    private static final String OUTPUT_FILE = "spider_result.txt";
    private static final int MAX_KEYWORDS_TO_DISPLAY = 10;
    private static final int MAX_LINKS_TO_DISPLAY = 10;

    public static class Page {
        public int pageID;
        public String url;
        public String title;
        public long lastModifiedDate;
        public long sizeOfPage;
        public Page parentPage;
        public List<Page> childPage = new ArrayList<>();
        public int numOfChildPages;
        public Dictionary<String, Dictionary<Integer, Integer>> titleStem = new Hashtable<>();
        public Dictionary<String, Dictionary<Integer, Integer>> bodyStem = new Hashtable<>();
        
        public Page(String url, int pageID) {
            this.url = url;
            this.pageID = pageID;
            try{
                URL url_date = new URL(url);
                URLConnection connection = url_date.openConnection();
                this.lastModifiedDate = connection.getLastModified();
                this.sizeOfPage = connection.getContentLengthLong();
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        public void addChildPage(Page childPage){
            this.childPage.add(childPage);
            numOfChildPages=numOfChildPages+1;
        }
    }

    public static void spider(InvertedIndexManager indexManager, Page page, int doc_max, int[] doc_num, int[] track, List<Page> db){
        try{
            System.out.println("Crawling page: " + page.url);
            Document doc = Jsoup.connect(page.url)
                    .timeout(10000)
                    .get();
            
            page.title = doc.title();
            
            int dbPageId = indexManager.addPage(page.url, page.title, page.lastModifiedDate, page.sizeOfPage);
            System.out.println("Added page to DB with ID: " + dbPageId + ", Title: " + page.title);
            
            StringTokenizer st = new StringTokenizer(doc.body().text(), " (),.?:/!");
            Map<String, Integer> bodyWordFreq = new HashMap<>();
            
            while (st.hasMoreTokens()) {
                String nextToken = st.nextToken().toLowerCase();
                if (!stopStem.isStopWord(nextToken)){
                    String token_word = stopStem.stem(nextToken);
                    
                    if (page.bodyStem.get(token_word) != null){
                        page.bodyStem.get(token_word).put(page.pageID, page.bodyStem.get(token_word).get(page.pageID)+1);
                    } else {
                        Hashtable<Integer, Integer> tmpDict = new Hashtable<>();
                        tmpDict.put(page.pageID, 1);
                        page.bodyStem.put(token_word, tmpDict);
                    }
                    
                    bodyWordFreq.put(token_word, bodyWordFreq.getOrDefault(token_word, 0) + 1);
                }
            }
            
            for (Map.Entry<String, Integer> entry : bodyWordFreq.entrySet()) {
                indexManager.addWordToBody(dbPageId, entry.getKey(), entry.getValue());
            }
            System.out.println("Added " + bodyWordFreq.size() + " body keywords for page ID: " + dbPageId);
            
            st = new StringTokenizer(doc.head().text(), " (),.?:/!");
            Map<String, Integer> titleWordFreq = new HashMap<>();
            
            while (st.hasMoreTokens()) {
                String nextToken = st.nextToken().toLowerCase();
                if (!stopStem.isStopWord(nextToken)){
                    String token_word = stopStem.stem(nextToken);
                    
                    if (page.titleStem.get(token_word) != null){
                        page.titleStem.get(token_word).put(page.pageID, page.titleStem.get(token_word).get(page.pageID)+1);
                    } else {
                        Hashtable<Integer, Integer> tmpDict = new Hashtable<>();
                        tmpDict.put(page.pageID, 1);
                        page.titleStem.put(token_word, tmpDict);
                    }
                    
                    titleWordFreq.put(token_word, titleWordFreq.getOrDefault(token_word, 0) + 1);
                }
            }
            
            for (Map.Entry<String, Integer> entry : titleWordFreq.entrySet()) {
                indexManager.addWordToTitle(dbPageId, entry.getKey(), entry.getValue());
            }

            Elements links = doc.select("a");
            for (Element link:links){
                String absHref = link.attr("abs:href");
                
                if (absHref.isEmpty()) continue;

                boolean existed = false;
                for (Page p : db) {
                    if (p.url.equals(absHref)) {
                        URL url_check_date = new URL(page.url);
                        URLConnection connection_check_date = url_check_date.openConnection();
                        if (!(connection_check_date.getLastModified() > p.lastModifiedDate)){
                            existed = true;
                            break;
                        }
                    }
                }
                
                if (!existed && doc_num[0] < doc_max){
                    Page childPage = new Page(absHref, ++doc_num[0]);
                    childPage.parentPage = page;
                    page.addChildPage(childPage);
                    db.add(childPage);
                    
                    int childPageId = indexManager.addPage(absHref, "", childPage.lastModifiedDate, childPage.sizeOfPage);
                    indexManager.addChildPage(dbPageId, childPageId);
                }
            }
            
            ++track[0];
            while (track[0] <= doc_num[0]){
                try {
                    spider(indexManager, db.get(track[0]), doc_max, doc_num, track, db);
                } catch (Exception e) {
                    System.err.println("Error crawling page " + db.get(track[0]).url + ": " + e.getMessage());
                    ++track[0];
                }
            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        String url = "https://comp4321-hkust.github.io/testpages/testpage.htm";
        String backupUrl = "https://comp4321-hkust.github.io/testpages/testpage.htm";
        String finalUrl = url;
        try {
            Jsoup.connect(url).timeout(5000).get();
        } catch (Exception e) {
            System.out.println("Primary URL failed, trying backup URL...");
            finalUrl = backupUrl;
        }
        
        try (InvertedIndexManager indexManager = new InvertedIndexManager("spider_index")) {
            
            List<Page> db = new ArrayList<>();
            Page firstPage = new Page(finalUrl, 0);
            db.add(firstPage);
            int doc_max = 30;
            int[] doc_num = new int[1];
            int[] track = new int[1];
            
            System.out.println("Starting web crawling...");
            spider(indexManager, db.getFirst(), doc_max, doc_num, track, db);
            System.out.println("Finished web crawling. Total pages indexed: " + doc_num[0]);
            
            generateResultFile(indexManager);
            
            System.out.println("Spider result file generated: " + OUTPUT_FILE);
        } catch (Exception e) {
            System.err.println("Error during crawling: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Generates the spider result text file
     */
    private static void generateResultFile(InvertedIndexManager indexManager) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(OUTPUT_FILE))) {
            List<Integer> pageIds = indexManager.getAllPageIds();
            
            for (Integer pageId : pageIds) {
                InvertedIndexManager.PageInfo pageInfo = indexManager.getPageInfo(pageId);
                
                if (pageInfo != null) {
                    writer.write(pageInfo.title);
                    writer.newLine();
                    
                    writer.write(pageInfo.url);
                    writer.newLine();
                    
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    String dateStr = pageInfo.lastModifiedDate > 0 ? 
                                     dateFormat.format(new Date(pageInfo.lastModifiedDate)) : 
                                     "Unknown";
                    writer.write(dateStr + ", " + pageInfo.size + " bytes");
                    writer.newLine();
                    
                    Map<String, Integer> keywords = indexManager.getTopKeywords(pageId, MAX_KEYWORDS_TO_DISPLAY);
                    if (!keywords.isEmpty()) {
                        StringBuilder keywordStr = new StringBuilder();
                        for (Map.Entry<String, Integer> entry : keywords.entrySet()) {
                            if (keywordStr.length() > 0) {
                                keywordStr.append("; ");
                            }
                            keywordStr.append(entry.getKey()).append(" ").append(entry.getValue());
                        }
                        writer.write(keywordStr.toString());
                        writer.newLine();
                    } else {
                        writer.write("No keywords");
                        writer.newLine();
                    }
                    
                    int linkCount = 0;
                    for (Integer childId : pageInfo.childPageIds) {
                        if (linkCount >= MAX_LINKS_TO_DISPLAY) break;
                        
                        InvertedIndexManager.PageInfo childInfo = indexManager.getPageInfo(childId);
                        if (childInfo != null) {
                            writer.write(childInfo.url);
                            writer.newLine();
                            linkCount++;
                        }
                    }
                    
                    writer.write("----------------------------------------");
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            System.err.println("Error generating result file: " + e.getMessage());
            e.printStackTrace();
        }
    }
}