import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;

public class HtmlParser {

    public static StopStem stopStem = new StopStem("stopwords.txt");

    public static class Page {
        public int pageID;
        public String url;
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
//                this.lastModifiedDate = LocalDate.parse(connection.getHeaderField("Last-Modified"));
                this.lastModifiedDate = connection.getLastModified();
//                connection.setRequestMethod("HEAD");
                this.sizeOfPage = connection.getContentLengthLong();
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        }
        public void addChildPage(Page childPage){
            this.childPage.add(childPage);
            ++numOfChildPages;
        }
    }

    public static void spider(Page page, int doc_max, int[] doc_num, int[] track, List<Page> db){
        try{
            Document doc = Jsoup.connect(page.url).get();

            StringTokenizer st = new StringTokenizer(doc.body().text(), " (),.?:/!");
            while (st.hasMoreTokens()) {
                String nextToken = st.nextToken();
                if (!stopStem.isStopWord(nextToken)){
                    String token_word = stopStem.stem(nextToken);
//                    System.out.println(token_word);
                    if (page.bodyStem.get(token_word) != null){
                        page.bodyStem.get(token_word).put(page.pageID, page.bodyStem.get(token_word).get(page.pageID)+1);
                    } else {
                        Hashtable<Integer, Integer> tmpDict = new Hashtable<>();
                        tmpDict.put(page.pageID, 1);
                        page.bodyStem.put(token_word, tmpDict);
                    }
                }
            }
            st = new StringTokenizer(doc.head().text(), " (),.?:/!");
            while (st.hasMoreTokens()) {
                String nextToken = st.nextToken();
                if (!stopStem.isStopWord(nextToken)){
                    String token_word = stopStem.stem(nextToken);
//                    System.out.println(token_word);
                    if (page.titleStem.get(token_word) != null){
                        page.titleStem.get(token_word).put(page.pageID, page.titleStem.get(token_word).get(page.pageID)+1);
                    } else {
                        Hashtable<Integer, Integer> tmpDict = new Hashtable<>();
                        tmpDict.put(page.pageID, 1);
                        page.titleStem.put(token_word, tmpDict);
                    }
                }
            }

            Elements links = doc.select("a");
            for (Element link:links){
                String relHref = link.attr("href");
                String absHref = link.attr("abs:href");

//                System.out.println("relHerf: " + relHref);
//                System.out.println("absHref: " + absHref);
                boolean existed = false;
                for (Page p : db) {
                    if (p.url.equals(absHref)) {
                        URL url_check_date = new URL(page.url);
                        URLConnection connection_check_date = url_check_date.openConnection();
                        if (!(connection_check_date.getLastModified() > p.lastModifiedDate)){
                            existed = true;
                            break;
                        }
//                        existed = true;
//                        break;
                    }
                }
                if (!existed && doc_num[0] < doc_max){
                    Page childPage = new Page(absHref, ++doc_num[0]);
                    childPage.parentPage = page;
                    page.addChildPage(childPage);
                    db.add(childPage);
                }
            }
            ++track[0];
            while (track[0] <= doc_num[0]){
                spider(db.get(track[0]), doc_max, doc_num, track, db);
            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        String url = "https://www.cse.ust.hk/~kwtleung/COMP4321/testpage.htm";
        List<Page> db = new ArrayList<>();
        Page firstPage = new Page(url, 0);
        db.add(firstPage);
        int doc_num = 30;
        int[] test_int = new int[1];
        int[] track = new int[1];
        spider(db.getFirst(), doc_num, test_int, track, db);
        db.removeFirst();
        for (Page p: db){
            System.out.println(p.url);
        }
        System.out.println(db.size());
    }
}