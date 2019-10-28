# DSpace Link Extractor
This repository contains a software that extracts links on DSpace bitstream documents (references to external links).

## Prerequisies
Given a file with links to sitemap DSpace.

## Algoritm
For each sitemap, parse DSpace site map.
For each site map entry, download it and on its HTML extract relevant URLs using a regex that matches bitstream links.
For each bitstream URL, download it, extract its links using [tikalinkextract](https://github.com/httpreserve/tikalinkextract "tikalinkextract") software and save each links extracted to a file with same file structure.
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

```bash
java -jar target/dspace-link-extractor-0.1-SNAPSHOT.jar dspace-urls.tsv output
```


