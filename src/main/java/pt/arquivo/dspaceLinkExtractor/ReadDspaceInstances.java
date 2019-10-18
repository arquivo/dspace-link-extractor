package pt.arquivo.dspaceLinkExtractor;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import org.apache.commons.io.IOUtils;

import crawlercommons.sitemaps.SiteMap;
import crawlercommons.sitemaps.SiteMapParser;
import crawlercommons.sitemaps.SiteMapURL;
import crawlercommons.sitemaps.UnknownFormatException;

/**
 *
 *
 * @author Ivo Branco
 *
 */
public class ReadDspaceInstances {

	private static String linkExtractorCmd = "./tikalinkextract -seeds -file %s > %s";

	public static void main(String[] args) throws Exception {
		String dspaceInstancesUrlsFilename = args[0];
		String outputDirectory = args[1];
		Collection<String> dspaceInstancesUrls = readFileLines(dspaceInstancesUrlsFilename);

//		FileOutputStream fos;
//		try {
//			fos = new FileOutputStream(outputFilename, true);
//
//		} catch (FileNotFoundException e) {
//			System.err.println("Error creating the output file " + outputFilename);
//			throw e;
//		}
//		final Appendable out = new BufferedWriter(new OutputStreamWriter(fos));
//		final CSVPrinter printer = CSVFormat.TDF.print(out);

		dspaceInstancesUrls.stream().map(d -> {
			try {
				return new URL(d);
			} catch (MalformedURLException e) {
				e.printStackTrace();
				return null;
			}
		}).filter(Objects::nonNull).forEach((URL dspaceSiteMapUrl) -> {

			System.out.println("Starting dspace site map " + dspaceSiteMapUrl);

			Consumer<? super SiteMapURL> consumer = siteMapEntry -> {
				URL url = siteMapEntry.getUrl();
				String path = url.getPath();

				String dspaceHost = dspaceSiteMapUrl.getHost();
				try {
					Files.createDirectories(Paths.get(outputDirectory + "/" + dspaceHost));
				} catch (IOException e) {
					throw new RuntimeException("Error creating dspace site folder " + dspaceHost);
				}

				String fileName = outputDirectory + "/" + getFilenameFromUrl(url);
				File file = new File(fileName);

				boolean fileExists = file.exists();
				if (!fileExists) {
					fileExists = downloadUrlToFile(url, file);
				}

				if (fileExists) {
					getRelevantUrls(dspaceSiteMapUrl, fileName).stream().forEach(bitstream -> {
						System.out.print(bitstream);
						URL bitstreamUrl;
						try {
							bitstreamUrl = new URL(bitstream);
						} catch (MalformedURLException e) {
							System.err.println(" - malformed url");
							return;
						}
						File bitstreamFile = new File(outputDirectory + "/" + getFilenameFromUrl(bitstreamUrl));

						if (!bitstreamFile.exists()) {
							System.out.print(" - downloading");
							downloadUrlToFile(bitstreamUrl, bitstreamFile);
							System.out.println(" - done");
						} else {
							System.out.println(" - already exists");
						}

						String extractedReferencesFilename = bitstreamFile.getAbsolutePath() + "_urls.txt";
						if (!new File(extractedReferencesFilename).exists()) {
							System.out.print("Extracting links ... ");
							Process p;
							try {
								String execCmd = String.format(linkExtractorCmd,
										bitstreamFile.getAbsolutePath(), extractedReferencesFilename);
								System.out.println(execCmd);
								p = Runtime.getRuntime().exec(execCmd);
							} catch (IOException e) {
								System.err.println("Error executing link extractor - original message: " + e.getMessage());
								return;
							}
							try {
								p.waitFor();
							} catch (InterruptedException e) {
								System.err.println("Interrupted exception " + e.getMessage());
							}
							int exitVal = p.exitValue();
							if (exitVal == 0) {
								System.out.println("done");
							} else {
								System.err.println("ups...");
							}
						}
					});

				}
			};

			SiteMap siteMap = getDspaceSiteMap(dspaceSiteMapUrl);
			siteMap.getSiteMapUrls().forEach(consumer);

		});
	}

	private static String getFilenameFromUrl(URL url) {
		return url.toString().substring(url.getProtocol().length() + 3);
	}

	private static SiteMap getDspaceSiteMap(URL dspaceSitemMapUrl) {
		byte[] dspaceSiteMapByteArray;
		try {
			dspaceSiteMapByteArray = IOUtils.toByteArray(openConnection(dspaceSitemMapUrl));
		} catch (Exception e) {
			throw new RuntimeException("Error getting bytes of dspace site map", e);
		}

		// walkSiteMap(dspaceSitemMapUrl, consumer);
		// hack because returned content type http header is incorrect
		SiteMap siteMap;
		try {
			siteMap = (SiteMap) new SiteMapParser(false, true).parseSiteMap("application/xml", dspaceSiteMapByteArray,
					dspaceSitemMapUrl);
		} catch (UnknownFormatException e) {
			throw new RuntimeException("unknow format", e);
		} catch (IOException e) {
			throw new RuntimeException("Error parsing site map", e);
		}
		return siteMap;
	}

	private static List<String> getRelevantUrls(URL dspaceSitemMapUrl, String fileName) {
		List<String> bitstreams = readFileLines(fileName).stream().flatMap(line -> {
			Pattern pattern = Pattern.compile("\"http[^ ]*/bitstream/[^ ]+\"");
			Matcher matcher = pattern.matcher(line);

			List<String> matches = new ArrayList<>();
			while (matcher.find()) {
				// Get the matching string
				String match = matcher.group();
				matches.add(match);
			}
			return matches.stream();
		}).map(m -> m.substring(1, m.length() - 1)).collect(Collectors.toList());
		return bitstreams;
	}

	private static boolean downloadUrlToFile(URL url, File file) {
		Reader reader;
		try {
			reader = openConnection(url);
		} catch (IOException e) {
			System.err.println("Error downloading skipping it: " + url);
			return false;
		}
		try {
			BufferedReader br = new BufferedReader(reader);
			String inputLine;

			if (!file.exists()) {
				file.getParentFile().mkdirs();
				file.createNewFile();
			}

			// use FileWriter to write file
			FileWriter fw = new FileWriter(file.getAbsoluteFile());
			BufferedWriter bw = new BufferedWriter(fw);

			while ((inputLine = br.readLine()) != null) {
				bw.write(inputLine);
			}

			bw.close();
			br.close();
		} catch (IOException ioe) {
			throw new RuntimeException("Error downloading url to file", ioe);
		}
		return true;
	}

	private static Reader openConnection(URL url) throws IOException {
		URLConnection conn = url.openConnection();
		Reader reader = null;
		String contentEncoding = conn.getContentEncoding();
		if ("gzip".equals(contentEncoding)) {
			reader = new InputStreamReader(new GZIPInputStream(conn.getInputStream()));
		} else {
			reader = new InputStreamReader(conn.getInputStream());
		}
		return reader;
	}

	private static Collection<String> readFileLines(String filename) {
		Collection<String> lines = new ArrayList<>();
		BufferedReader reader;
		try {
			reader = new BufferedReader(new FileReader(filename));
			String line = reader.readLine();
			while (line != null) {
				lines.add(line);
				// read next line
				line = reader.readLine();
			}
			reader.close();
		} catch (IOException e) {
			throw new RuntimeException("Error reading lines from file: " + filename, e);
		}

		return lines;
	}

}
