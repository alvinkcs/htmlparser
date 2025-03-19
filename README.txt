COMP4321 - Search Engine Project
============================

This project implements a web crawler and indexer that:
1. Crawls web pages starting from a seed URL
2. Removes stop words and applies Porter stemming
3. Indexes the content in JDBM databases
4. Supports phrase search in page titles and bodies

Database Schema Design
---------------------
The project uses JDBM for persistent storage with the following schema:

1. Forward Indexes:
   - pageIdToUrl: Maps page IDs to their URLs
   - pageInfo: Stores page information (title, URL, last modified date, size, child links)
   - wordIdToWord: Maps word IDs to stemmed words

2. Inverted Indexes:
   - bodyInvertedIndex: Maps word IDs to lists of page IDs with frequency counts for body content
   - titleInvertedIndex: Maps word IDs to lists of page IDs with frequency counts for title content

3. Mapping Tables:
   - urlToPageId: Maps URLs to page IDs
   - wordToWordId: Maps stemmed words to word IDs

4. Phrase Search Support:
   - pageIdToBodyWords: Maps page IDs to lists of words in body (for phrase search)
   - pageIdToTitleWords: Maps page IDs to lists of words in title (for phrase search)

5. Counters:
   - counter: Stores counters for generating page IDs and word IDs

Build Instructions
-----------------
This project uses Maven for dependency management and building:

1. Make sure you have Java JDK 21 and Maven installed
2. Navigate to the project directory
3. Run: mvn clean compile

Running the Spider
-----------------
To run the spider and generate both the database and the output file:

1. Run: mvn exec:java -Dexec.mainClass="HtmlParser"

This will:
- Start the crawler from the seed URL
- Index 30 pages
- Generate the spider_index.db database file
- Generate a spider_result.txt file with the crawled data

Running the Test Program
-----------------------
To run just the test program to generate the output file from an existing database:

1. Run: mvn exec:java -Dexec.mainClass="TestProgram" -Dexec.args="spider_index"

This will:
- Read from the specified database (default is "spider_index" if not provided)
- Generate a new spider_result.txt file with the data from the database

Files Included
-------------
1. HtmlParser.java - Main crawler implementation
2. InvertedIndexManager.java - JDBM database management
3. StopStem.java - Stop word removal and stemming
4. IRUtilities/Porter.java - Porter stemmer implementation
5. TestProgram.java - Test program to read from database and generate output
6. stopwords.txt - List of stop words to be ignored
7. pom.xml - Maven project file with dependencies
8. spider_index.db - Generated database file (after running)
9. spider_result.txt - Generated output file (after running)

Output Format
------------
The spider_result.txt file contains the crawled data in the following format:

Page title
URL
Last modification date, size of page
Keyword1 freq1; Keyword2 freq2; Keyword3 freq3; ... (up to 10 keywords)
Child Link1
Child Link2 ... (up to 10 child links)
---------------------------------------- (separator)
[Next page follows the same format] 