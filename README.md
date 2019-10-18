mvn clean package && java -jar target/dspace-link-extractor-0.1-SNAPSHOT.jar dspace-urls.tsv urls.txt

# Reference extractor of dspace files
Extracts links on all files on dpace. So they could be crawled directly by a crawler.

## Strategy

Prerequisit: a list of sitemaps of a couple of dspaces.

1. Convert a list of dspace sitemaps to a list of sitemap urls

1.1. read a txt/tsv file with all links to a sitemaps of a list of dspaces.
http://commons.apache.org/proper/commons-csv/
TDF - tab separated columns

1.2. for each dspace sitemap URL parse it and extract each handle.
https://github.com/crawler-commons/crawler-commons/blob/master/src/main/java/crawlercommons/sitemaps/SiteMapParser.java

1.2. for each dspace create a tsv file with:
 - dspace server name
 - last updated date
 - handle url
http://commons.apache.org/proper/commons-csv/

2. Crawl all pages and get all links with a 'bitstream' on its path.

2.1. Read previous produced tsv.
http://commons.apache.org/proper/commons-csv/

2.2. Filter only urls that have been updated after a specific time. 

2.3. Download url

2.4. Extract all links

2.5. Filter links URL that contains the 'bitstream' word

2.6. Export every link to a tsv file with:
 - dspace server name
 - last updated date
 - handle url
 - bitstream url

3. Read previous tsv

3.1. Download bitstream url probabilly a pdf
To a folder structure like: dspace space name / url
Each '/' is mapped to a different folder.

4. Run tikalinkextract to every file downloaded on previous phase
https://github.com/httpreserve/tikalinkextract

5. Crawl every links on a new Arquivo.pt EAWP
 

