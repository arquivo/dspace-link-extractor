# DSpace Link Extractor
This repository contains a software that extracts links on DSpace bitstream documents (references to external links).

## Prerequisies
Given a file with links to sitemap DSpace.

## Algoritm
- For each sitemap, parse DSpace site map.
- For each site map entry, download it and on its HTML extract relevant URLs using a regex that matches bitstream links.
- For each bitstream URL, download it, extract its links using [tikalinkextract](https://github.com/httpreserve/tikalinkextract "tikalinkextract") software and save each links extracted to a file with same file structure.

Like:
- From URL http://repositorio-aberto.up.pt/bitstream/10216/63886/2/90220.pdf
- To file: output/repositorio-aberto.up.pt/bitstream/10216/63886/2/90220.pdf_seeds.txt

## Dependencies
It uses the [tikalinkextract](https://github.com/httpreserve/tikalinkextract "tikalinkextract") software and tika server.

On other shell run:

```bash
java -mx1000m -jar tools/tika-server-1.20.jar --port=9998
```

## Build

```bash
mvn clean package
```

# Run

Run the dspace link extractor on background and redirect to a file:

```bash
java -jar target/dspace-link-extractor-0.1-SNAPSHOT.jar dspace-urls.txt output >> dspace.log 2>&1
```

If you only want the entries that have been changed from a specific date add a date on argument like using format yyyy-MM-dd like:

```bash
java -jar target/dspace-link-extractor-0.1-SNAPSHOT.jar dspace-urls.txt output 2019-01-01 >> dspace.log 2>&1
```

# Finish

When thw crawl has finished you could remove all the 'handle' folders. Because the seeds are on bitsteam folder.

```bash
find output -maxdepth 2 -name handle -exec rm -rf {} \;
```

Concatenate all the seeds on a single file:

```bash
find output/ -type f -name "*_seeds.txt" -exec cat {} \; >> seeds.txt
```

Remove mails and filter duplicates:
```bash
cat seeds.txt | egrep -v "^mail.*" | sort | uniq > seeds_uniq.txt
```

