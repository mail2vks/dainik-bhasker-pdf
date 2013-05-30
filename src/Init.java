import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Comparator;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.pdfbox.exceptions.COSVisitorException;
import org.apache.pdfbox.util.PDFMergerUtility;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

public class Init {

	private static final Logger log = Logger.getLogger(Init.class.getName());

	private static final String BACK_SLASH = "/";

	private static final String DASH = "-";

	private static final String EMPTY = "";

	private static Integer fileIndex = 1;

	private static final int MAIN_SECTION = 1;

	private static final int DBSTAR_SECTION = 2;

	private static final int CITY_SECTION = 3;

	private static final DateTime now = new DateTime();

	private static final DateTimeFormatter fmt = DateTimeFormat
			.forPattern("ddMMyyyy");

	public static final DateTimeFormatter fmt2 = DateTimeFormat
			.forPattern("dd");

	public static void main(String[] args) throws IOException,
			COSVisitorException {

		new File(now.toString(fmt)).mkdir();
		getAllPages(MAIN_SECTION);
		getAllPages(DBSTAR_SECTION);
		getAllPages(CITY_SECTION);

		mergeAllPages(now.toString(fmt));
	}

	private static void mergeAllPages(String directoryName) throws IOException,
			COSVisitorException {

		File[] files = new File(directoryName).listFiles();

		Arrays.sort(files, new Comparator<File>() {
			public int compare(File f1, File f2) {
				return Long.valueOf(f1.lastModified()).compareTo(
						f2.lastModified());
			}
		});

		PDFMergerUtility ut = new PDFMergerUtility();
		for (File pdfFile : files) {
			ut.addSource(pdfFile);
		}

		ut.setDestinationFileName(directoryName + BACK_SLASH + "merged.pdf");
		log.log(Level.INFO, "Starting merge at : " + directoryName + BACK_SLASH
				+ "merged.pdf");
		ut.mergeDocuments();
		log.log(Level.INFO, "Created merged pdf at : " + directoryName
				+ BACK_SLASH + "merged.pdf");

	}

	private static void getAllPages(int section) {
		boolean isPaperUploaded = true;
		Integer index = 1;
		while (isPaperUploaded) {
			String url = getTodaysURLPrefix(section) + "$index$"
					+ getTodaysURLSuffix(section);
			url = url.replace("$index$", index.toString());
			log.log(Level.INFO, "URL is " + url);

			try {
				getFile(url, section);
			} catch (Exception e) {
				e.printStackTrace();
				isPaperUploaded = false;
			}
			index++;
		}
	}

	private static String getTodaysURLPrefix(int paperSection) {

		return "http://digitalimages.bhaskar.com/mpcg/epaperpdf/"
				+ now.toString(fmt) + BACK_SLASH
				+ now.minusDays(1).toString(fmt2) + getCityCode(paperSection)
				+ DASH + getPageCode(paperSection);

		// http://digitalimages.bhaskar.com/mpcg/epaperpdf/30112012/29BHOPAL%20CITY-PG1-0.PDF
		// http://digitalimages.bhaskar.com/mpcg/epaperpdf/01122012/30BHOPAL%20CITY-PG1-0.PDF

		// DB STAR
		// http://digitalimages.bhaskar.com/mpcg/epaperpdf/16122012/15bhopal%20db%20star-pg1-0.pdf

		// CITY BHASKER
		// http://digitalimages.bhaskar.com/mpcg/epaperpdf/16122012/15bcity%20bhaskar-pg1-0.pdf
	}

	private static String getTodaysURLSuffix(int paperSection) {
		return DASH + (paperSection != MAIN_SECTION ? "0.pdf" : "0.PDF");
	}

	private static String getCityCode(int paperSection) {
		switch (paperSection) {
		case MAIN_SECTION:
			return "BHOPAL%20CITY";
		case DBSTAR_SECTION:
			return "bhopal%20db%20star";
		case CITY_SECTION:
			return "bcity%20bhaskar";
		}

		return EMPTY;
	}

	private static String getPageCode(int paperSection) {
		return (paperSection != MAIN_SECTION ? "pg" : "PG");
	}

	private static void getFile(String url, int section) throws Exception {

		InputStream is = null;
		BufferedOutputStream bos = null;
		try {
			URL pageURL = new URL(url);
			HttpURLConnection huc = (HttpURLConnection) pageURL
					.openConnection();
			huc.setRequestMethod("GET");
			HttpURLConnection.setFollowRedirects(true);
			huc.connect();
			int code = huc.getResponseCode();
			if (code == 300) {
				throw new Exception("File does not exist");
			}

			is = huc.getInputStream();

			String fileName = section + DASH + fileIndex++ + ".pdf";

			bos = new BufferedOutputStream(new FileOutputStream(new File(
					now.toString(fmt) + BACK_SLASH + fileName)));

			byte[] buffer = new byte[4096];

			long countKBRead = 0;
			int count = 0;
			while ((count = is.read(buffer)) != -1) {
				countKBRead += count;
				bos.write(buffer, 0, count);
				System.out.print(".");
			}
			log.log(Level.INFO, "Read " + countKBRead / 1024 + " KB");
			if (countKBRead == 0) {
				throw new Exception("No bytes read from stream");
			}
			bos.flush();
		} finally {
			if (is != null)
				is.close();
			if (bos != null)
				bos.close();
		}
	}
}
