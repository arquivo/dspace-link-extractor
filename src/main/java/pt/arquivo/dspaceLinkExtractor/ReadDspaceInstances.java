package pt.arquivo.dspaceLinkExtractor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.X509TrustManager;
import javax.xml.ws.Holder;

import org.apache.commons.io.FileUtils;
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

	private static final String TIKA_LINK_EXTRACT_EXEC_PATH =
//			FileSystems.getDefault().getPath(".").toAbsolutePath()
//			.toString() + File.separator +
			"tools" + File.separator + "tikalinkextract-linux64";
	private static String linkExtractorCmd = TIKA_LINK_EXTRACT_EXEC_PATH + " -file %s -seeds";

	private static Holder<Integer> handlesDownloadCount = new Holder<>(0);
	private static Holder<Integer> handlesAlreadyDownloadedCount = new Holder<>(0);
	private static Holder<Integer> bitstreamsDownloadCount = new Holder<>(0);
	private static Holder<Integer> seedsCount = new Holder<>(0);

	private static String outputDirectory;

	public static TimerTask printCountersTimerTask = schedulePrintCounters();

	public static void main(String[] args) throws Exception {
		String dspaceInstancesUrlsFilename = args[0];
		outputDirectory = args[1];

		trustEveryone();

		if (!new File(TIKA_LINK_EXTRACT_EXEC_PATH).canExecute()) {
			System.err.println(
					"It isn't possible to run the tikalinkextract software using: " + TIKA_LINK_EXTRACT_EXEC_PATH);
			System.exit(-1);
		}

		try {
			parseDspaceSiteMapsExtractSeeds(dspaceInstancesUrlsFilename);
		} catch (Throwable t) {
			t.printStackTrace();
			System.exit(-1);
		} finally {
			printCountersTimerTask.cancel();
		}
		System.exit(0);
	}

	private static void parseDspaceSiteMapsExtractSeeds(String dspaceInstancesUrlsFilename) {
		readFileLines(dspaceInstancesUrlsFilename).filter(d -> !d.startsWith("#")).map(d -> {
			try {
				return new URL(d);
			} catch (MalformedURLException e) {
				e.printStackTrace();
				return null;
			}
		}).filter(Objects::nonNull).forEach((URL dspaceSiteMapUrl) -> {

			System.out.println("Starting dspace site map " + dspaceSiteMapUrl);

			SiteMap siteMap = getDspaceSiteMap(dspaceSiteMapUrl);
			siteMap.getSiteMapUrls().forEach(getSiteMapEntryConsumer(dspaceSiteMapUrl));
		});
	}

	private static Consumer<? super SiteMapURL> getSiteMapEntryConsumer(URL dspaceSiteMapUrl) {
		return siteMapEntry -> {
			URL url = siteMapEntry.getUrl();

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
				handlesDownloadCount.value++;
			} else {
				handlesAlreadyDownloadedCount.value++;
			}

			if (fileExists) {
				getRelevantUrls(dspaceSiteMapUrl, fileName).stream().forEach(bitstream -> {
					String m = parseDspaceBitstream(bitstream);
					System.out.println(m);
				});
			}
		};
	}

	private static String parseDspaceBitstream(String bitstream) {
		StringBuilder m = new StringBuilder();
		m.append(bitstream);
		URL bitstreamUrl;
		try {
			bitstreamUrl = new URL(bitstream);
		} catch (MalformedURLException e) {
			m.append(" - malformed url");
			return m.toString();
		}
		File bitstreamFile = new File(outputDirectory + "/" + getFilenameFromUrl(bitstreamUrl));

		if (!bitstreamFile.exists()) {
			m.append(" - downloading");
			bitstreamsDownloadCount.value++;
			downloadUrlToFile(bitstreamUrl, bitstreamFile);
			m.append(" - done");
		} else {
			m.append(" - already exists");
		}

		String extractedSeedsFilename = bitstreamFile.getAbsolutePath() + "_seeds.txt";
		File extractedSeedsFile = new File(extractedSeedsFilename);
		if (!extractedSeedsFile.exists()) {
			m.append(extractSeeds(bitstreamFile, extractedSeedsFile));
		}

		bitstreamFile.delete();
		return m.toString();
	}

	private static String extractSeeds(File from, File to) {
		StringBuilder m = new StringBuilder();
		m.append(", extracting links");

		Process p;
		try {
			String cmd = String.format(linkExtractorCmd, from.getAbsolutePath());
//			System.out.println(cmd);

			ProcessBuilder pb = new ProcessBuilder();
			pb.redirectOutput(to);
			pb.command(cmd.split("\\s+"));
			p = pb.start();
		} catch (IOException e) {
			m.append(" Error executing link extractor - original message: " + e.getMessage());
			e.printStackTrace();
			return m.toString();
		}
		try {
			p.waitFor();
			p.waitFor(1, TimeUnit.MINUTES); // let the process run for 1 minute
			p.destroy(); // tell the process to stop
			p.waitFor(2, TimeUnit.SECONDS); // give it a chance to stop
			p.destroyForcibly(); // tell the OS to kill the process
			p.waitFor(); // the process is now dead
		} catch (InterruptedException e) {
			m.append(" Interrupted exception " + e.getMessage());
		}
		int exitVal = p.exitValue();
		if (exitVal == 0) {
			m.append(" done");
			Iterable<String> iter = () -> {
				try {
					return FileUtils.lineIterator(to);
				} catch (IOException e) {
					m.append(" Error counting seeds");
					return new ArrayList<String>().iterator();
				}
			};
			long fileSeedsCount = StreamSupport.stream(iter.spliterator(), false).count();
			seedsCount.value += (int) fileSeedsCount;
			m.append(" found " + fileSeedsCount + " seeds.");

		} else {
			m.append(", ups problem extracting links");
		}
		return m.toString();
	}

	private static TimerTask schedulePrintCounters() {
		TimerTask task = new TimerTask() {
			@Override
			public void run() {
				System.out.println(String.format(
						"handles [downloaded: %d, download previous %d] bitstreams [downloaded: %d] seeds [extracted: %d]",
						handlesDownloadCount.value, handlesAlreadyDownloadedCount.value, bitstreamsDownloadCount.value,
						seedsCount.value));
			}
		};
		Timer timer = new Timer();
		timer.schedule(task, new Date(), 10000);
		return task;
	}

	private static String getFilenameFromUrl(URL url) {
		return url.toString().substring(url.getProtocol().length() + 3);
	}

	private static SiteMap getDspaceSiteMap(URL dspaceSiteMapUrl) {
		File siteMapFile = new File(outputDirectory + File.separator + dspaceSiteMapUrl.getHost() + "_sitemap");
		downloadUrlToFile(dspaceSiteMapUrl, siteMapFile);

		try {
			return (SiteMap) new SiteMapParser(false, true).parseSiteMap("application/xml",
					Files.readAllBytes(siteMapFile.toPath()), dspaceSiteMapUrl);
		} catch (UnknownFormatException e) {
			throw new RuntimeException("unknow format", e);
		} catch (IOException e) {
			throw new RuntimeException("Error parsing site map", e);
		}
	}

	private static List<String> getRelevantUrls(URL dspaceSiteMapUrl, String fileName) {
		List<String> bitstreams = readFileLines(fileName).flatMap(line -> {
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
		try {
//			FileUtils.copyURLToFile(url, file);

			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setReadTimeout(5000);
//			conn.addRequestProperty("Accept-Language", "en-US,en;q=0.8");
//			conn.addRequestProperty("User-Agent", "Mozilla");
//			conn.addRequestProperty("Referer", "google.com");

			boolean redirect = false;

			// normally, 3xx is redirect
			int status = conn.getResponseCode();
			if (status != HttpURLConnection.HTTP_OK) {
				if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM
						|| status == HttpURLConnection.HTTP_SEE_OTHER)
					redirect = true;
			}

			if (redirect) {
				// get redirect url from "location" header field
				String newUrl = conn.getHeaderField("Location");
				downloadUrlToFile(new URL(newUrl), file);
			} else {
				Files.createDirectories(Paths.get(file.getParent()));
				file.createNewFile();

				IOUtils.copy(conn.getInputStream(), new FileOutputStream(file));
				if (GZipUtil.isGZipped(file)) {
					byte[] decompressedData = GZipUtil.decompress(file);
					FileUtils.writeByteArrayToFile(file, decompressedData);
				}
			}
		} catch (IOException e) {
			System.err.println("Error downloading skipping it: " + url + " " + e.getMessage());
//			e.printStackTrace();
			return false;
		}
		return true;
	}

	private static Stream<String> readFileLines(String filename) {
		Iterable<String> iter = () -> {
			try {
				return FileUtils.lineIterator(new File(filename));
			} catch (IOException e) {
				System.err.println("Error reading file lines");
				return new ArrayList<String>().iterator();
			}
		};
		return StreamSupport.stream(iter.spliterator(), false);
	}

	private static void trustEveryone() {
		try {
			HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
				public boolean verify(String hostname, SSLSession session) {
					return true;
				}
			});
			SSLContext context = SSLContext.getInstance("TLS");
			context.init(null, new X509TrustManager[] { new X509TrustManager() {
				public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
				}

				public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
				}

				public X509Certificate[] getAcceptedIssuers() {
					return new X509Certificate[0];
				}
			} }, new SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory());
		} catch (Exception e) { // should never happen
			e.printStackTrace();
		}
	}
}
