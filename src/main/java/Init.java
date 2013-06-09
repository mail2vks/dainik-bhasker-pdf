import org.apache.commons.lang3.StringUtils; import org.apache.pdfbox.exceptions.COSVisitorException;
import org.apache.pdfbox.util.PDFMergerUtility;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Init {

	private static final DateTimeFormatter fmt2 = DateTimeFormat
			.forPattern("d");
	private static final Logger log = Logger.getLogger(Init.class.getName());
	private static final String BACK_SLASH = "/";
	private static final String DASH = "-";
	private static final String EMPTY = "";
	private static final int MAIN_SECTION = 1;
	private static final int DBSTAR_SECTION = 2;
	private static final int CITY_SECTION = 3;
	private static final String NAME_CONFIG_SUFFIX = ".name";
	private static final String MAIN_PAPER_CONFIG_SUFFIX = ".main.code";
	private static final String CITY_PAPER_CONFIG_SUFFIX = ".city.code";
	private static final String CITY_PAPER_CONFIG_START_INDEX = ".city.start";
	private static final String SUPPLEMENT_PAPER_CONFIG_SUFFIX = ".supplement.code";
	private static final String URL_CONFIG_SUFFIX = ".urlPrefix";
	private static final DateTime now = new DateTime();
	private static final DateTimeFormatter fmt = DateTimeFormat
			.forPattern("ddMMyyyy");
	private static final Scanner scanner = new Scanner(System.in);
	private static Integer fileIndex = 1;
	private static Properties configuration;
	private static Map<String, String> cityToConfigMap = new HashMap<>();

	/**
	 * @param args
	 * @throws IOException
	 * @throws COSVisitorException
	 */
	public static void main(String[] args) throws IOException,
			COSVisitorException {

		parseConfig();

		displayAvailableOptions();

	}

	private static void displayAvailableOptions() throws IOException, COSVisitorException {

		System.out.println("\nAvailable Cities are :\n");

		for (String city : cityToConfigMap.keySet()) {
			System.out.println(StringUtils.capitalize(StringUtils.lowerCase(city)));
		}

		System.out.println("\nChoose city by typing city name (case insensitive) or type 'exit' to exit.");

		String chosenCity = scanner.next();

		if (StringUtils.equalsIgnoreCase(chosenCity, "exit"))
			return;

		if (!cityToConfigMap.containsKey(StringUtils.upperCase(chosenCity))) {
			System.out.println("\nYou chose a wrong option.");
		} else {
			generatePDFForCity(cityToConfigMap.get(StringUtils.upperCase(chosenCity)));
		}

		displayAvailableOptions();
	}

	private static void generatePDFForCity(String chosenCityCode) throws IOException, COSVisitorException {
		logInfo(chosenCityCode);

		String directoryName = now.toString(fmt) + DASH + configuration.get(chosenCityCode + NAME_CONFIG_SUFFIX).toString();

		new File(directoryName).mkdir();
		//getAllPages(MAIN_SECTION, chosenCityCode);
		getAllPages(CITY_SECTION, chosenCityCode);
		//getAllPages(DBSTAR_SECTION, chosenCityCode);

		mergeAllPages(directoryName);

	}

	private static void parseConfig() {

		configuration = new Properties();
		try {
			configuration.load(Init.class.getClassLoader().getResourceAsStream("config.properties"));
		} catch (IOException e) {
			e.printStackTrace();
		}

		String[] supportedCities = StringUtils.split(configuration.getProperty("supported.cities"), ',');
		logInfo(Arrays.toString(supportedCities));

		for (String city : supportedCities) {
			cityToConfigMap.put(StringUtils.upperCase(configuration.get(city + NAME_CONFIG_SUFFIX).toString()), city);
		}
	}

	private static void mergeAllPages(final String directoryName) throws IOException,
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
		logInfo("Starting merge at : " + directoryName + BACK_SLASH
				+ "merged.pdf");
		ut.mergeDocuments();
		logInfo("Created merged pdf at : " + directoryName
				+ BACK_SLASH + "merged.pdf");

	}

	private static void getAllPages(int section, String cityCode) {
		boolean isPaperUploaded = true;
		Integer index = 1;
		if(configuration.get(cityCode + CITY_PAPER_CONFIG_START_INDEX) != null)
		{
			index = Integer.valueOf(configuration.get(cityCode + CITY_PAPER_CONFIG_START_INDEX).toString());
		}
		while (isPaperUploaded) {
			String url = getTodaysURLPrefix(section, cityCode) + "$index$"
					+ getTodaysURLSuffix(section);
			url = url.replace("$index$", index.toString());
			logInfo("URL is " + url);

			try {
				getFile(url, section, cityCode);
			} catch (Exception e) {
				e.printStackTrace();
				isPaperUploaded = false;
			}
			index++;
		}
	}

	private static String getTodaysURLPrefix(int paperSection, String cityCode) {

		return configuration.get(cityCode + URL_CONFIG_SUFFIX)
				+ now.toString(fmt) + BACK_SLASH
				+ now.minusDays(1).toString(fmt2) + getCityCode(paperSection, cityCode)
				+ DASH + getPageCode(paperSection);
	}

	private static String getTodaysURLSuffix(int paperSection) {
		return DASH + (paperSection != MAIN_SECTION ? "0.pdf" : "0.PDF");
	}

	private static String getCityCode(final int paperSection, final String cityCode) {
		switch (paperSection) {
			case MAIN_SECTION:
				return configuration.get(cityCode + MAIN_PAPER_CONFIG_SUFFIX).toString();
			case DBSTAR_SECTION:
				return configuration.get(cityCode + SUPPLEMENT_PAPER_CONFIG_SUFFIX).toString();
			case CITY_SECTION:
				return configuration.get(cityCode + CITY_PAPER_CONFIG_SUFFIX).toString();
			default:
				return EMPTY;
		}
	}

	private static String getPageCode(int paperSection) {
		return (paperSection != MAIN_SECTION ? "pg" : "PG");
	}

	private static void getFile(String url, int section, String cityCode) throws Exception {

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
					now.toString(fmt) + DASH + configuration.get(cityCode + NAME_CONFIG_SUFFIX) + BACK_SLASH + fileName)));

			byte[] buffer = new byte[8192];

			long countKBRead = 0;
			int count = 0;
			while ((count = is.read(buffer)) != -1) {
				countKBRead += count;
				bos.write(buffer, 0, count);
				System.out.print(".");
			}
			logInfo("Read " + countKBRead / 1024 + " KB");
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

	private static void logInfo(final String text) {
		log.log(Level.INFO, text);
	}
}
