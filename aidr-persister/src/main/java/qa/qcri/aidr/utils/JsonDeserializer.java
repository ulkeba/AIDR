/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package qa.qcri.aidr.utils;


import net.minidev.json.JSONObject;

import org.supercsv.io.ICsvBeanWriter;
import org.supercsv.io.ICsvMapWriter;

import qa.qcri.aidr.persister.filter.JsonQueryList;
import qa.qcri.aidr.persister.filter.FilterQueryMatcher;
import qa.qcri.aidr.persister.filter.NominalLabel;
import qa.qcri.aidr.utils.Config;

import java.io.*;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import qa.qcri.aidr.utils.Tweet;
import qa.qcri.aidr.io.FileSystemOperations;
import qa.qcri.aidr.io.ReadWriteCSV;
import qa.qcri.aidr.logging.ErrorLog;

import java.nio.charset.Charset;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonNull;
import com.google.gson.stream.JsonReader;


/**
 *
 * @author Imran
 */
public class JsonDeserializer {

	private static Logger logger = Logger.getLogger(JsonDeserializer.class.getName());
	private static ErrorLog elog = new ErrorLog();

	private static final int BUFFER_SIZE = 10 * 1024 * 1024;	// buffer size to use for buffered r/w
	private static final int LIST_BUFFER_SIZE = 50000; 


	//This method generates tweetIds csv from all the jsons of a collection
	public Map<String, Object> generateJson2TweetIdsCSV(String collectionCode, boolean downloadLimited) {
		List<String> fileNames = FileSystemOperations.getAllJSONFileVolumes(collectionCode);
		List<Tweet> tweetsList = new ArrayList<Tweet>(LIST_BUFFER_SIZE);

		ICsvBeanWriter beanWriter = null;
		//String fileName = collectionCode + "_tweetIds" + ".csv";		
		String fileNameforCSVGen = collectionCode + "_tweetIds";
		String fileName = fileNameforCSVGen + ".csv";
		long lastCount = 0;
		long currentCount = 0;
		int totalCount = 0;
		try {
			ReadWriteCSV csv = new ReadWriteCSV();
			BufferedReader br = null;
			String fileToDelete = Config.DEFAULT_PERSISTER_FILE_PATH + collectionCode + "/" + collectionCode + "_tweetIds.csv";
			FileSystemOperations.deleteFile(fileToDelete); // delete if there exist a csv file with same name

			for (String file : fileNames) {
				String fileLocation = Config.DEFAULT_PERSISTER_FILE_PATH + collectionCode + "/" + file;
				try {
					br = new BufferedReader(new FileReader(fileLocation));
					String line;
					if (downloadLimited && totalCount > Config.DEFAULT_TWEETID_VOLUME_LIMIT) {
						break;
					}
					while ((line = br.readLine()) != null) {
						if (downloadLimited && totalCount > Config.DEFAULT_TWEETID_VOLUME_LIMIT) {	
							tweetsList.clear();
							break;
						}
						Tweet tweet = getTweet(line);
						if (tweet != null) {
							if (tweetsList.size() < LIST_BUFFER_SIZE) { //after every 10k write to CSV file
								tweetsList.add(tweet);
							} else {
								int countToWrite;
								if (downloadLimited) {
									countToWrite = Math.min(tweetsList.size(), Config.DEFAULT_TWEETID_VOLUME_LIMIT - totalCount);
								} else {
									countToWrite = tweetsList.size();
								}
								if (countToWrite > 0) {
									beanWriter = csv.writeCollectorTweetIDSCSV(beanWriter, tweetsList.subList(0, countToWrite), collectionCode, fileName);
									totalCount += countToWrite;
									lastCount = currentCount;
									currentCount += tweetsList.size();
									logger.info(collectionCode + ": Writing_tweetIds: " + lastCount + " to " + currentCount);
								}
								tweetsList.clear();
								tweetsList.add(tweet);	
							}
						}
					}
					br.close();
				} catch (FileNotFoundException ex) {
					logger.error(collectionCode + ": couldn't find file = " + fileLocation);
					logger.error(elog.toStringException(ex));
				} catch (IOException ex) {
					logger.error(collectionCode + ": IO Exception for file = " + fileLocation);
					logger.error(elog.toStringException(ex));
				}
			}	// end for
			int countToWrite = tweetsList.size();
			if (downloadLimited) {
				countToWrite = Math.min(tweetsList.size(), Config.DEFAULT_TWEETID_VOLUME_LIMIT - totalCount);
			} 
			if (countToWrite > 0) {
				beanWriter = csv.writeCollectorTweetIDSCSV(beanWriter, tweetsList.subList(0, countToWrite), collectionCode, fileName);
				totalCount += countToWrite;
				tweetsList.clear();
			}
		} finally {
			if (beanWriter != null) {
				try {
					beanWriter.close();
				} catch (IOException ex) {
					logger.error(collectionCode + ": IOException for csv file write ");
					logger.error(elog.toStringException(ex));
				}
			}
		}
		//beanWriter = csv.writeCollectorTweetIDSCSV(beanWriter, tweetsList, collectionCode, collectionCode + "_tweetIds");
		tweetsList.clear();
		return ResultStatus.getUIWrapper("fileName", fileName, "count", totalCount);
	}

	public Map<String, Object> generateClassifiedJson2TweetIdsCSV(String collectionCode, final boolean downloadLimited) {

		ICsvMapWriter writer = null;
		String fileNameforCSVGen = "Classified_" + collectionCode + "_tweetIds";
		String fileName = fileNameforCSVGen + ".csv";
		int totalCount = 0;
		try {
			List<String> fileNames = FileSystemOperations.getClassifiedFileVolumes(collectionCode);
			List<ClassifiedTweet> tweetsList = new ArrayList<ClassifiedTweet>(LIST_BUFFER_SIZE);

			ReadWriteCSV csv = new ReadWriteCSV();
			BufferedReader br = null;
			String fileToDelete = Config.DEFAULT_PERSISTER_FILE_PATH + collectionCode + "/output/" + "Classified_" + collectionCode + "_tweetIds.csv";
			logger.info(collectionCode + ": Deleteing file : " + fileToDelete);
			FileSystemOperations.deleteFile(fileToDelete); // delete if there exist a csv file with same name
			//logger.info(fileNames);

			long lastCount = 0;
			long currentCount = 0;
			for (String file : fileNames) {
				String fileLocation = Config.DEFAULT_PERSISTER_FILE_PATH + collectionCode + "/output/" + file;
				logger.info(collectionCode + ": Reading file " + fileLocation);
				if (downloadLimited && totalCount > Config.DEFAULT_TWEETID_VOLUME_LIMIT) {
					break;
				}
				try {
					br = new BufferedReader(new FileReader(fileLocation));
					String line;
					while ((line = br.readLine()) != null) {
						if (downloadLimited && totalCount >= Config.DEFAULT_TWEETID_VOLUME_LIMIT) {
							tweetsList.clear();
							break;
						}
						// Otherwise process tweet
						ClassifiedTweet tweet = getClassifiedTweet(line);
						if (tweet != null && !tweet.getNominalLabels().isEmpty()) {
							if (tweetsList.size() < LIST_BUFFER_SIZE) { 
								tweetsList.add(tweet);
							} else {
								int countToWrite;
								if (downloadLimited) {
									countToWrite = Math.min(tweetsList.size(), Config.DEFAULT_TWEETID_VOLUME_LIMIT - totalCount);
								} else {
									countToWrite = tweetsList.size();
								}
								if (countToWrite > 0) {
									writer = csv.writeClassifiedTweetIDsCSV(writer, tweetsList.subList(0, countToWrite), collectionCode, fileName);
									lastCount = currentCount;
									currentCount += countToWrite;
									logger.info(collectionCode + ": Writing_tweetIds: " + lastCount + " to " + currentCount);
									totalCount += countToWrite;
								}
								tweetsList.clear();
								tweetsList.add(tweet);
							}
						}
					}
					br.close();

				} catch (FileNotFoundException ex) {
					logger.error(collectionCode + ": couldn't find file = " + fileLocation);
					logger.error(elog.toStringException(ex));
				} catch (IOException ex) {
					logger.error(collectionCode + ": IO Exception for file = " + fileLocation);
					logger.error(elog.toStringException(ex));
				}
			}	// end for
			int countToWrite = tweetsList.size();
			if (downloadLimited) {
				countToWrite = Math.min(tweetsList.size(), Config.DEFAULT_TWEETID_VOLUME_LIMIT - totalCount);
			} 
			if (countToWrite > 0) {
				writer = csv.writeClassifiedTweetIDsCSV(writer, tweetsList.subList(0, countToWrite), collectionCode, fileName);
				totalCount += countToWrite;
				tweetsList.clear();
			}

		} finally {
			if (writer != null) {
				try {
					writer.close();
				} catch (IOException ex) {
					logger.error(collectionCode + ": IOException for csv file write ");
					logger.error(elog.toStringException(ex));
				}
			}
		}
		return ResultStatus.getUIWrapper("fileName", fileName, "count", totalCount);
	}

	/**
	 * 
	 * @param collectionCode
	 * @param selectedLabels list of user provided label names for filtering tweets
	 * @return JSON to CSV converted tweet IDs filtered by user selected label name
	 */
	public Map<String, Object> generateClassifiedJson2TweetIdsCSVFiltered(final String collectionCode, 
			final JsonQueryList queryList, final boolean downloadLimited) {
		ICsvMapWriter writer = null;
		//String fileNameforCSVGen = "Classified_" + collectionCode + "_tweetIds_filtered";
		//String fileName = fileNameforCSVGen + ".csv";
		String fileNameforCSVGen = collectionCode + "_tweetIds_filtered";
		String fileName = fileNameforCSVGen + ".csv";
		
		List<ClassifiedTweet> tweetsList = new ArrayList<ClassifiedTweet>(LIST_BUFFER_SIZE);

		int totalCount = 0;
		try {
			//List<String> fileNames = FileSystemOperations.getClassifiedFileVolumes(collectionCode);
			List<String> fileNames = FileSystemOperations.getAllJSONFileVolumes(collectionCode);
			Collections.sort(fileNames);
			Collections.reverse(fileNames);
			
			ReadWriteCSV csv = new ReadWriteCSV();
			BufferedReader br = null;
			//String fileToDelete = Config.DEFAULT_PERSISTER_FILE_PATH + collectionCode + "/output/" + "Classified_" + collectionCode + "_tweetIds_filtered.csv";
			String fileToDelete = Config.DEFAULT_PERSISTER_FILE_PATH + collectionCode + "/" + fileName;
			logger.info(collectionCode + ": Deleteing file : " + fileToDelete);
			FileSystemOperations.deleteFile(fileToDelete); // delete if there exist a csv file with same name

			// Added by koushik - first build the FilterQueryMatcher
			FilterQueryMatcher tweetFilter = new FilterQueryMatcher();
			if (queryList != null) tweetFilter.queryList.setConstraints(queryList);
			tweetFilter.buildMatcherArray();
			
			String lastLine = null;
			for (String file : fileNames) {
				if (downloadLimited && totalCount >= Config.DEFAULT_TWEETID_VOLUME_LIMIT) {
					break;
				}
				//String fileLocation = Config.DEFAULT_PERSISTER_FILE_PATH + collectionCode + "/output/" + file;
				String fileLocation = Config.DEFAULT_PERSISTER_FILE_PATH + collectionCode + "/" + file;
				logger.info(collectionCode + ": Reading file " + fileLocation);
				try {
					br = new BufferedReader(new FileReader(fileLocation));
					String line;
					while ((line = br.readLine()) != null) {
						lastLine = line;
						if (downloadLimited && totalCount >= Config.DEFAULT_TWEETID_VOLUME_LIMIT) {
							tweetsList.clear();
							break;
						}
						// Otherwise add to write buffer
						ClassifiedTweet tweet = getClassifiedTweet(line, collectionCode);
						if (tweet != null && satisfiesFilter(queryList, tweetFilter, tweet)) {
							if (tweetsList.size() < LIST_BUFFER_SIZE) { 								
								tweetsList.add(tweet);
							} else {
								int countToWrite;
								if (downloadLimited) {
									countToWrite = Math.min(tweetsList.size(), Config.DEFAULT_TWEETID_VOLUME_LIMIT - totalCount);
								} else {
									countToWrite = tweetsList.size();
								}
								if (countToWrite > 0) {
									writer = csv.writeClassifiedTweetIDsCSV(writer, tweetsList.subList(0, countToWrite), collectionCode, fileName);
									totalCount += countToWrite;
								}
								tweetsList.clear();
								tweetsList.add(tweet);
							}
						}
					}

					br.close();

				} catch (FileNotFoundException ex) {
					logger.error(collectionCode + ": couldn't find file = " + fileLocation);
					logger.error(elog.toStringException(ex));
				} catch (IOException ex) {
					logger.error(collectionCode + ": IO Exception for file = " + fileLocation);
					logger.error(elog.toStringException(ex));
				}
			}	// end for
			int countToWrite = tweetsList.size();
			if (downloadLimited) {
				countToWrite = Math.min(tweetsList.size(), Config.DEFAULT_TWEETID_VOLUME_LIMIT - totalCount);
			} 
			if (countToWrite > 0 && !tweetsList.isEmpty()) {
				writer = csv.writeClassifiedTweetIDsCSV(writer, tweetsList.subList(0, countToWrite), collectionCode, fileName);
				totalCount += countToWrite;
				tweetsList.clear();
			}
		} finally {
			if (writer != null) {
				try {
					writer.close();
				} catch (IOException ex) {
					logger.error(collectionCode + ": IOException for csv file write ");
					logger.error(elog.toStringException(ex));
				}
			}
		}

		//beanWriter = csv.writeClassifiedTweetIDsCSV(beanWriter, tweetsList, collectionCode, fileNameforCSVGen);
		tweetsList.clear();
		return ResultStatus.getUIWrapper("fileName", fileName, "count", totalCount);
	}



	private final static String getDateTime() {
		DateFormat df = new SimpleDateFormat("yyyyMMdd");  //yyyy-MM-dd_hh:mm:ss
		return df.format(new Date());
	}

	public String generateJSON2CSV_100K_BasedOnTweetCount(String collectionCode, int exportLimit) {
		BufferedReader br = null;
		boolean isCSVGenerated = true;
		String fileName = "";
		ICsvBeanWriter beanWriter = null;

		try {

			String folderLocation = Config.DEFAULT_PERSISTER_FILE_PATH + collectionCode;
			String fileNameforCSVGen = collectionCode + "_last_100k_tweets";
			fileName = fileNameforCSVGen + ".csv";
			FileSystemOperations.deleteFile(Config.DEFAULT_PERSISTER_FILE_PATH + collectionCode + "/" + fileName);

			File folder = new File(folderLocation);
			File[] listOfFiles = folder.listFiles();

			Arrays.sort(listOfFiles, new Comparator<File>() {
				public int compare(File f1, File f2) {
					return Long.valueOf(f1.lastModified()).compareTo(f2.lastModified());
				}
			});

			List<Tweet> tweetsList = new ArrayList<Tweet>();
			ReadWriteCSV csv = new ReadWriteCSV();
			int currentSize = 0;

			createTweetList:
			{
				for (int i = 0; i < listOfFiles.length; i++) {
					File f = listOfFiles[i];
					String currentFileName = f.getName();
					if (currentFileName.endsWith(".json")
							&& currentFileName.contains("vol")) {
						String line;
						logger.info(collectionCode + ": Reading file : " + f.getAbsolutePath());
						InputStream is = new FileInputStream(f.getAbsolutePath());
						br = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
						while ((line = br.readLine()) != null) {
							try {
								Tweet tweet = getTweet(line);
								if (tweet != null) {
									if (tweetsList.size() < LIST_BUFFER_SIZE && currentSize <= exportLimit) {
										//write to arrayList
										tweetsList.add(tweet);
										//logger.info("currentSize  : " + currentSize);
									} else {
										//write csv file
										int countToWrite = Math.min(tweetsList.size(), exportLimit - currentSize);
										if (countToWrite > 0) {
										beanWriter = csv.writeCollectorTweetsCSV(tweetsList.subList(0, countToWrite), collectionCode, fileName, beanWriter);
										currentSize += countToWrite;
										}
										// empty arraylist
										tweetsList.clear();
										tweetsList.add(tweet);
									}
									if (beanWriter != null) {
										// System.out.println("beanWriter  : " +beanWriter.getLineNumber());
										if (currentSize >= exportLimit) {
											// write to the csv file
											break createTweetList;
										}
									}
								}
							} catch (Exception ex) {
								//Logger.getLogger(JsonDeserializer.class.getName()).log(Level.SEVERE, "JSON file parsing exception at line {0}", lineNumber);
							}
						}
						if (!tweetsList.isEmpty()) {
							beanWriter = csv.writeCollectorTweetsCSV(tweetsList, collectionCode, fileName, beanWriter);
							logger.info(collectionCode + ": final beanWriter  : " + beanWriter.getRowNumber());
							tweetsList.clear();
						}
						br.close();
					}
				}
			}
			int countToWrite = Math.min(tweetsList.size(), exportLimit - currentSize);
			if (countToWrite > 0)  {
				beanWriter = csv.writeCollectorTweetsCSV(tweetsList.subList(0, countToWrite), collectionCode, fileName, beanWriter);
				tweetsList.clear();
			}
		} catch (FileNotFoundException ex) {
			logger.error(collectionCode + ": couldn't find file");
			logger.error(elog.toStringException(ex));
			isCSVGenerated = false;
		} catch (IOException ex) {
			logger.error(collectionCode + ": IO Exception for file");
			logger.error(elog.toStringException(ex));
		} finally {
			if (beanWriter != null) {
				try {
					beanWriter.close();
				} catch (IOException ex) {
					logger.error(collectionCode + ": IOException for csv file write ");
					logger.error(elog.toStringException(ex));
				}
			}
		}
		return fileName;
	}

	public String taggerGenerateJSON2CSV_100K_BasedOnTweetCount(String collectionCode, int exportLimit) {
		BufferedReader br = null;
		String fileName = "";
		ICsvMapWriter writer = null;

		try {

			String folderLocation = Config.DEFAULT_PERSISTER_FILE_PATH + collectionCode + "/output";
			String fileNameforCSVGen = "Classified_" + collectionCode + "_last_100k_tweets";
			fileName = fileNameforCSVGen + ".csv";
			FileSystemOperations.deleteFile(folderLocation + "/" + fileName);

			File folder = new File(folderLocation);
			File[] listOfFiles = folder.listFiles();
			// to get only Tagger's files
			ArrayList<File> taggerFilesList = new ArrayList<File>();
			for (int i = 0; i < listOfFiles.length; i++) {
				if (StringUtils.startsWith(listOfFiles[i].getName(), "Classified_")
						&& StringUtils.containsIgnoreCase(listOfFiles[i].getName(), "vol")) {
					taggerFilesList.add(listOfFiles[i]);
				}
			}

			Object[] objectsArray = taggerFilesList.toArray();
			File[] taggerFiles = Arrays.copyOf(objectsArray, objectsArray.length, File[].class);
			Arrays.sort(taggerFiles, new Comparator<File>() {
				public int compare(File f1, File f2) {
					return Long.valueOf(f2.lastModified()).compareTo(f1.lastModified());	// koushik: changed sort order?
				}
			});

			List<ClassifiedTweet> tweetsList = new ArrayList<ClassifiedTweet>(LIST_BUFFER_SIZE);
			ReadWriteCSV csv = new ReadWriteCSV();
			int currentSize = 0;

			createTweetList:
			{
				for (int i = 0; i < taggerFiles.length; i++) {
					File f = taggerFiles[i];
					String currentFileName = f.getName();
					if (currentFileName.endsWith(".json")) {
						String line;
						logger.info(collectionCode + ": Reading file : " + f.getAbsolutePath());
						InputStream is = new FileInputStream(f.getAbsolutePath());
						br = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
						while ((line = br.readLine()) != null) {
							ClassifiedTweet tweet = getClassifiedTweet(line);
							if (tweet != null && !tweet.getNominalLabels().isEmpty()) {
								if (tweetsList.size() < LIST_BUFFER_SIZE) {
									tweetsList.add(tweet);
									//System.out.println("Added TWEET: " + tweet.getLabelName() + ", " + tweet.getConfidence());
								} else {
									int countToWrite = Math.min(tweetsList.size(), exportLimit - currentSize);
									if (countToWrite > 0) {
										// buffer full, write to csv file
										writer = csv.writeClassifiedTweetsCSV(tweetsList.subList(0, countToWrite), collectionCode, fileName, writer);
										currentSize += countToWrite;
									}
									tweetsList.clear();
									if (currentSize >= exportLimit) {
										break createTweetList;
									}
									// Otherwise add current tweet and continue
									tweetsList.add(tweet);
								}
							}
						}
						/*
						if (!tweetsList.isEmpty()) {
							writer = csv.writeClassifiedTweetsCSV(tweetsList, collectionCode, fileName, writer);
							tweetsList.clear();
						}*/
						br.close();
					}
				}
			}
			int countToWrite = Math.min(tweetsList.size(), exportLimit - currentSize);
			if (countToWrite > 0) {
				writer = csv.writeClassifiedTweetsCSV(tweetsList.subList(0, countToWrite), collectionCode, fileName, writer);
				tweetsList.clear();			
			}
		} catch (FileNotFoundException ex) {
			logger.error(collectionCode + ": File not found.");
			logger.error(elog.toStringException(ex));
		} catch (IOException ex) {
			logger.error(collectionCode + ": IO Exception for file read");
			logger.error(elog.toStringException(ex));
		} finally {
			if (writer != null) {
				try {
					writer.close();
				} catch (IOException ex) {
					logger.error(collectionCode + ": IOException for csv file write ");
					logger.error(elog.toStringException(ex));
				}
			}
		}
		return fileName;
	}

	/**
	 * 
	 * @param collectionCode c
	 * @param exportLimit
	 * @param selectedLabels list of user provided label names for filtering tweets
	 * @return JSON to CSV converted 100K tweets filtered by user selected label name
	 */
	public String taggerGenerateJSON2CSV_100K_BasedOnTweetCountFiltered(String collectionCode, 
			int exportLimit, 
			final JsonQueryList queryList) {
		BufferedReader br = null;
		String fileName = "";
		ICsvMapWriter writer = null;

		try {
			//String folderLocation = Config.DEFAULT_PERSISTER_FILE_PATH + collectionCode + "/output";
			//String fileNameforCSVGen = "Classified_" + collectionCode + "_last_100k_tweets_filtered";
			String folderLocation = Config.DEFAULT_PERSISTER_FILE_PATH + collectionCode;
			String fileNameforCSVGen = collectionCode + "_last_100k_tweets_filtered";
			
			fileName = fileNameforCSVGen + ".csv";
			FileSystemOperations.deleteFile(folderLocation + "/" + fileName);

			File folder = new File(folderLocation);
			File[] listOfFiles = folder.listFiles();
			// to get only Tagger's files
			ArrayList<File> taggerFilesList = new ArrayList<File>();
			for (int i = 0; i < listOfFiles.length; i++) {
				if (StringUtils.startsWith(listOfFiles[i].getName(), (collectionCode + "_"))
						&& StringUtils.containsIgnoreCase(listOfFiles[i].getName(), "vol")) {
					logger.info("Added to list, file: " + listOfFiles[i]);
					taggerFilesList.add(listOfFiles[i]);
				}
			}
			Object[] objectsArray = taggerFilesList.toArray();
			File[] taggerFiles = Arrays.copyOf(objectsArray, objectsArray.length, File[].class);
			
			Arrays.sort(taggerFiles, new Comparator<File>() {
				public int compare(File f1, File f2) {
					return Long.valueOf(f1.lastModified()).compareTo(f2.lastModified());
				}
			});

			List<ClassifiedTweet> tweetsList = new ArrayList<ClassifiedTweet>(LIST_BUFFER_SIZE);
			ReadWriteCSV csv = new ReadWriteCSV();
			int currentSize = 0;
			createTweetList:
			{
				//First build the FilterQueryMatcher
				FilterQueryMatcher tweetFilter = new FilterQueryMatcher();
				if (queryList != null) tweetFilter.queryList.setConstraints(queryList);
				tweetFilter.buildMatcherArray();

				for (int i = taggerFiles.length - 1; i >= 0; i--) {
					File f = taggerFiles[i];
					String currentFileName = f.getName();
					if (currentFileName.endsWith(".json")) {
						String line;
						logger.info("Reading file : " + f.getAbsolutePath());
						InputStream is = new FileInputStream(f.getAbsolutePath());
						br = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
						while ((line = br.readLine()) != null) {
							ClassifiedTweet tweet = getClassifiedTweet(line, collectionCode);
							if (tweet != null && satisfiesFilter(queryList, tweetFilter, tweet)) {
								if (tweetsList.size() < LIST_BUFFER_SIZE) {
									// Apply filter on tweet
									tweetsList.add(tweet);
								} else {
									// write buffer full, write to csv file
									int countToWrite = Math.min(tweetsList.size(), exportLimit - currentSize);
									if (countToWrite > 0) {
										//System.out.println("exportLimit = " + exportLimit + ", currentSize = " + currentSize + ", countToWrite = " + countToWrite);
										writer = csv.writeClassifiedTweetsCSV(tweetsList.subList(0, countToWrite), collectionCode, fileName, writer);
										currentSize += countToWrite;
									}
									// clear contents from tweetsList buffer
									tweetsList.clear();		
									if (currentSize >= exportLimit) {
										break createTweetList;		// we are done
									} 
									// Otherwise add the tweet to fresh buffer and continue 
									tweetsList.add(tweet);
								}
							}
						}	// end while 
					}
					/*
					if (!tweetsList.isEmpty()) {
						writer = csv.writeClassifiedTweetsCSV(tweetsList, collectionCode, fileName,writer);
						tweetsList.clear();
					}*/
					br.close();
					logger.info("Done processing file : " + f.getAbsolutePath());
				}	// end for
			}	// end createTweetList block
			int countToWrite = Math.min(tweetsList.size(), exportLimit - currentSize);
			if (countToWrite > 0) {
				//System.out.println("exportLimit = " + exportLimit + ", currentSize = " + currentSize + ", countToWrite = " + countToWrite);
				writer = csv.writeClassifiedTweetsCSV(tweetsList.subList(0, countToWrite), collectionCode, fileName, writer);
				tweetsList.clear();
			}

		} catch (FileNotFoundException ex) {
			logger.error(collectionCode + ": couldn't find file");
			logger.error(elog.toStringException(ex));
		} catch (IOException ex) {
			logger.error(collectionCode + ": IO Exception for file read");
			logger.error(elog.toStringException(ex));
		} finally {
			if (writer != null) {
				try {
					writer.close();
				} catch (IOException ex) {
					logger.error(collectionCode + ": IOException for csv file write ");
					logger.error(elog.toStringException(ex));
				}
			}
		}
		return fileName;
	}


	@Deprecated
	public List<ClassifiedTweet> getNClassifiedTweetsJSON(String collectionCode, int exportLimit) {
		List<ClassifiedTweet> tweetsList = new ArrayList();
		BufferedReader br = null;
		try {

			String folderLocation = Config.DEFAULT_PERSISTER_FILE_PATH + collectionCode;
			File folder = new File(folderLocation);
			File[] listOfFiles = folder.listFiles();
			// to get only Tagger's files
			ArrayList<File> taggerFilesList = new ArrayList();
			for (int i = 0; i < listOfFiles.length; i++) {
				if (StringUtils.startsWith(listOfFiles[i].getName(), "Classified_")
						&& StringUtils.containsIgnoreCase(listOfFiles[i].getName(), "vol")) {
					taggerFilesList.add(listOfFiles[i]);
				}
			}
			Object[] objectsArray = taggerFilesList.toArray();
			File[] taggerFiles = Arrays.copyOf(objectsArray, objectsArray.length, File[].class);
			Arrays.sort(taggerFiles, new Comparator<File>() {
				public int compare(File f1, File f2) {
					return Long.valueOf(f1.lastModified()).compareTo(f2.lastModified());
				}
			});

			for (int i = 0; i < taggerFiles.length; i++) {
				File f = taggerFiles[i];
				String currentFileName = f.getName();
				if (currentFileName.endsWith(".json")) {
					String line;
					System.out.println("Reading file : " + f.getAbsolutePath());
					InputStream is = new FileInputStream(f.getAbsolutePath());
					br = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
					while ((line = br.readLine()) != null) {
						ClassifiedTweet tweet = getClassifiedTweet(line);
						if (tweetsList.size() < exportLimit) {
							tweetsList.add(tweet);
						} else {
							break;
						}
					}
				}
			}

		} catch (FileNotFoundException ex) {
			logger.error(collectionCode + ": couldn't find file");
			logger.error(elog.toStringException(ex));
		} catch (IOException ex) {
			logger.error(collectionCode + ": IO Exception for file read");
			logger.error(elog.toStringException(ex));
		} 
		return tweetsList;
	}

	/**
	 * 
	 * @param collectionCode
	 * @param exportLimit
	 * @param selectedLabels list of user provided label names for filtering tweets
	 * @return JSON format classified tweets filtered by user selected label name
	 */
	@Deprecated
	public List<ClassifiedTweet> getNClassifiedTweetsJSONFiltered(String collectionCode, 
			int exportLimit,
			final JsonQueryList queryList) {
		List<ClassifiedTweet> tweetsList = new ArrayList<ClassifiedTweet>();
		BufferedReader br = null;
		try {

			String folderLocation = Config.DEFAULT_PERSISTER_FILE_PATH + collectionCode;
			File folder = new File(folderLocation);
			File[] listOfFiles = folder.listFiles();
			// to get only Tagger's files
			ArrayList<File> taggerFilesList = new ArrayList<File>();
			for (int i = 0; i < listOfFiles.length; i++) {
				if (StringUtils.startsWith(listOfFiles[i].getName(), "Classified_")
						&& StringUtils.containsIgnoreCase(listOfFiles[i].getName(), "vol")) {
					taggerFilesList.add(listOfFiles[i]);
				}
			}
			Object[] objectsArray = taggerFilesList.toArray();
			File[] taggerFiles = Arrays.copyOf(objectsArray, objectsArray.length, File[].class);
			Arrays.sort(taggerFiles, new Comparator<File>() {
				public int compare(File f1, File f2) {
					return Long.valueOf(f1.lastModified()).compareTo(f2.lastModified());
				}
			});

			// Added by koushik - first build the FilterQueryMatcher
			FilterQueryMatcher tweetFilter = new FilterQueryMatcher();
			tweetFilter.queryList.setConstraints(queryList);
			tweetFilter.buildMatcherArray();

			for (int i = 0; i < taggerFiles.length; i++) {
				File f = taggerFiles[i];
				String currentFileName = f.getName();
				if (currentFileName.endsWith(".json")) {
					String line;
					System.out.println("Reading file : " + f.getAbsolutePath());
					InputStream is = new FileInputStream(f.getAbsolutePath());
					br = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
					while ((line = br.readLine()) != null) {
						ClassifiedTweet tweet = getClassifiedTweet(line);
						// Apply filter on tweet
						if (tweetsList.size() < exportLimit) {
							if (tweetFilter.getMatcherResult(tweet)) {
								//write to arrayList
								tweetsList.add(tweet);
							}
						} else {
							break;
						}
					}
				}
			}

		} catch (FileNotFoundException ex) {
			logger.error(collectionCode + ": couldn't find file");
			logger.error(elog.toStringException(ex));
		} catch (IOException ex) {
			logger.error(collectionCode + ": IO Exception for file read");
			logger.error(elog.toStringException(ex));
		} 
		return tweetsList;
	}


	public static void main(String[] args) {
		//JsonDeserializer jc = new JsonDeserializer();
		//String fileName = jc.generateClassifiedJson2TweetIdsCSV("2014-04-chile_earthquake_2014", false);
		//String fileName = jc.taggerGenerateJSON2CSV_100K_BasedOnTweetCount("2014-04-chile_earthquake_2014", 10);
		//String fileName = jc.generateJson2TweetIdsCSV("prism_nsa");
		//System.out.println("File name: " + fileName);

		//testFilterAPIs();
		//jc.generateJson2TweetIdsCSV("syria_en");
		//FileSystemOperations.deleteFile("CandFlood2013_20130922_vol-1.json.csv");
	}


	private Tweet getTweet(String line) {
		Tweet tweet = new Tweet();
		try {
			Gson jsonObject = new GsonBuilder().serializeNulls().disableHtmlEscaping()
					.serializeSpecialFloatingPointValues()	//.setPrettyPrinting()
					.create();
			JsonParser parser = new JsonParser();
			JsonObject jsonObj = (JsonObject) parser.parse(line);


			if (jsonObj.get("id") != null) {
				tweet.setTweetID(jsonObj.get("id").getAsString());
			} 

			if (jsonObj.get("text") != null) {
				tweet.setMessage(jsonObj.get("text").getAsString());
			}

			if (jsonObj.get("created_at") != null) {
				tweet.setCreatedAt(jsonObj.get("created_at").getAsString());
			}


			JsonObject jsonUserObj = null;
			if (jsonObj.get("user") != null) {				
				jsonUserObj = jsonObj.get("user").getAsJsonObject();
				if (jsonUserObj.get("id") != null) {
					tweet.setUserID(jsonUserObj.get("id").getAsString());
				}

				if (jsonUserObj.get("screen_name") != null) {
					tweet.setUserName(jsonUserObj.get("screen_name").getAsString());
					tweet.setTweetURL("https://twitter.com/" + tweet.getUserName() + "/status/" + tweet.getTweetID());
				}
				if (jsonUserObj.get("url") != null) {
					tweet.setUserURL(jsonUserObj.get("url").toString());
				}
			}
		} catch (Exception ex) {
			//Logger.getLogger(JsonDeserializer.class.getName()).log(Level.SEVERE, null, ex);
			//System.out.println("[getTweet] Offending tweet: " + line);
			return null;
		}
		return tweet;
	}

	public ClassifiedTweet getClassifiedTweet(String line) {
		return getClassifiedTweet(line, null);
	}

	// getClassifiedTweet method using Gson library for parsing JSON string
	public ClassifiedTweet getClassifiedTweet(String line, String collectionCode) {
		//System.out.println("Tweet to PARSE: " + line);

		ClassifiedTweet tweet = new ClassifiedTweet();
		try {
			StringReader reader = new StringReader(line.trim());
			JsonReader jsonReader = new JsonReader(reader);
		    jsonReader.setLenient(true);
			Gson jsonObject = new GsonBuilder().serializeNulls().disableHtmlEscaping()
					.serializeSpecialFloatingPointValues()	//.setPrettyPrinting()
					.create();
			JsonParser parser = new JsonParser();
			JsonObject jsonObj = (JsonObject) parser.parse(jsonReader);

			//System.out.println("Unparsed tweet data: " + jsonObj.get("id") + ", " + jsonObj.get("created_at") + ", " + jsonObj.get("user")  + ", " + jsonObj.get("aidr"));
			
			if (!jsonObj.get("id").isJsonNull()) {
				tweet.setTweetID(jsonObj.get("id").getAsString());
			} 

			if (!jsonObj.get("text").isJsonNull()) {
				tweet.setMessage(jsonObj.get("text").getAsString());
			}

			if (!jsonObj.get("created_at").isJsonNull()) {
				tweet.setCreatedAt(jsonObj.get("created_at").getAsString());
			}

			JsonObject jsonUserObj = null;
			if (!jsonObj.get("user").isJsonNull()) {				
				jsonUserObj = jsonObj.get("user").getAsJsonObject();
				if (jsonUserObj.get("id") != null) {
					tweet.setUserID(jsonUserObj.get("id").getAsString());
				}

				if (!jsonUserObj.get("screen_name").isJsonNull()) {
					tweet.setUserName(jsonUserObj.get("screen_name").getAsString());
					tweet.setTweetURL("https://twitter.com/" + tweet.getUserName() + "/status/" + tweet.getTweetID());
				}
				if (jsonUserObj.get("url") != null) {
					tweet.setUserURL(jsonUserObj.get("url").toString());
				}
			}

			JsonObject aidrObject = null;
			if (!jsonObj.get("aidr").isJsonNull()) {
				aidrObject = jsonObj.get("aidr").getAsJsonObject();
				if (!aidrObject.get("crisis_name").isJsonNull()) {
					tweet.setCrisisName(aidrObject.get("crisis_name").getAsString());
				}
				if (!aidrObject.get("crisis_code").isJsonNull()) {
					tweet.setCrisisCode(aidrObject.get("crisis_code").getAsString());
				}
				if (!aidrObject.get("nominal_labels").isJsonNull()) {
					//JSONArray nominalLabels = (JSONArray) aidrObject.get("nominal_labels");
					JsonArray nominalLabels = aidrObject.get("nominal_labels").getAsJsonArray();
					StringBuffer allAttributeNames = new StringBuffer();
					StringBuffer allAttributeCodes = new StringBuffer();
					StringBuffer allLabelNames = new StringBuffer();
					StringBuffer allLabelCodes = new StringBuffer();
					StringBuffer allLabelDescriptions = new StringBuffer();
					StringBuffer allConfidences = new StringBuffer();
					StringBuffer humanLabeled = new StringBuffer();
					for (int i = 0; i < nominalLabels.size(); i++) {
						//JSONObject label = (JSONObject) nominalLabels.get(i);
						JsonObject label = nominalLabels.get(i).getAsJsonObject();
						allAttributeNames.append(!label.get("attribute_name").isJsonNull() ? label.get("attribute_name").getAsString() : "null");
						allAttributeCodes.append(!label.get("attribute_code").isJsonNull() ? label.get("attribute_code").getAsString() : "null");
						allLabelNames.append(!label.get("label_name").isJsonNull() ? label.get("label_name").getAsString() : "null");
						allLabelCodes.append(!label.get("label_code").isJsonNull() ? label.get("label_code").getAsString() : "null");
						allLabelDescriptions.append(!label.get("label_description").isJsonNull() ? label.get("label_description").getAsString() : "null");
						allConfidences.append(!label.get("confidence").isJsonNull() ? label.get("confidence").getAsFloat() : 0);
						humanLabeled.append(!label.get("from_human").isJsonNull() ? label.get("from_human").getAsBoolean() : false);

						// Added by koushik
						NominalLabel nLabel = new NominalLabel();
						nLabel.attribute_code = label.get("attribute_code").isJsonNull() ? "null" : label.get("attribute_code").getAsString();
						nLabel.label_code = label.get("label_code").isJsonNull() ? "null" : label.get("label_code").getAsString();
						nLabel.confidence = label.get("confidence").isJsonNull() ? 0 : Float.parseFloat(label.get("confidence").getAsString());

						nLabel.attribute_name = label.get("attribute_name").isJsonNull() ? "null" : label.get("attribute_name").getAsString();
						nLabel.label_name = label.get("label_name").isJsonNull() ? "null" : label.get("label_name").getAsString();
						nLabel.attribute_description = label.get("attribute_description").isJsonNull() ? "null" : label.get("attribute_description").getAsString();
						nLabel.label_description = label.get("label_description").isJsonNull() ? "null" : label.get("label_description").getAsString();
						nLabel.from_human = label.get("from_human").isJsonNull() ? false : Boolean.parseBoolean(label.get("from_human").getAsString());

						tweet.nominal_labels.add(nLabel);

						// remove the ugly ';' from end-of-list
						if (i < nominalLabels.size() - 1) {
							allAttributeNames.append(";");
							allAttributeCodes.append(";");
							allLabelNames.append(";");
							allLabelDescriptions.append(";");
							allConfidences.append(";");
							humanLabeled.append(";");
						}
					}

					tweet.setAttributeName_1(allAttributeNames.toString());
					tweet.setAttributeCode_1(allAttributeCodes.toString());
					tweet.setLabelName_1(allLabelNames.toString());
					tweet.setLabelDescription_1(allLabelDescriptions.toString());
					tweet.setConfidence_1(allConfidences.toString());
					tweet.setHumanLabeled_1(humanLabeled.toString());
				} else {
					tweet.createDummyNominalLabels(collectionCode);        	
				}
			} else {
				tweet.createDummyNominalLabels(collectionCode);
			}

		} catch (Exception ex) {
			return null;
		}
		return tweet;
	}

	public String generateJSON2JSON_100K_BasedOnTweetCount(String collectionCode, DownloadJsonType jsonType) {
		BufferedReader br = null;
		String fileName = "";
		BufferedWriter beanWriter = null;
		String extension = DownloadJsonType.getSuffixString(jsonType);
		if (null == extension) {
			extension = DownloadJsonType.defaultSuffix();
		}
		boolean jsonObjectClosed = false;
		try {

			String folderLocation = Config.DEFAULT_PERSISTER_FILE_PATH + collectionCode;
			String fileNameforJsonGen = collectionCode + "_last_100k_tweets";
			fileName = fileNameforJsonGen + extension;
			FileSystemOperations.deleteFile(Config.DEFAULT_PERSISTER_FILE_PATH + collectionCode + "/" + fileNameforJsonGen + extension);

			File folder = new File(folderLocation);
			File[] listOfFiles = folder.listFiles();

			Arrays.sort(listOfFiles, new Comparator<File>() {
				public int compare(File f1, File f2) {
					return Long.valueOf(f1.lastModified()).compareTo(f2.lastModified());
				}
			});

			long currentSize = 0;
			StringBuffer outputFile = new StringBuffer().append(folderLocation)
					.append("/")
					.append(fileName);
			beanWriter = new BufferedWriter(new FileWriter(outputFile.toString()), BUFFER_SIZE);
			if (DownloadJsonType.JSON_OBJECT.equals(jsonType)) {
				beanWriter.write("[");
			}
			boolean isDone = false;
			for (int i = 0; i < listOfFiles.length; i++) {
				File f = listOfFiles[i];
				String currentFileName = f.getName();
				if (currentFileName.endsWith(".json")
						&& currentFileName.contains("vol")) {
					String line;
					logger.info("Reading file : " + f.getAbsolutePath());
					InputStream is = new FileInputStream(f.getAbsolutePath());
					br = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
					while ((line = br.readLine()) != null) {
						try {
							if (currentSize <= Config.TWEETS_EXPORT_LIMIT_100K) {
								//write to file
								beanWriter.write(line);
								if (DownloadJsonType.JSON_OBJECT.equals(jsonType)
										&& currentSize < Config.TWEETS_EXPORT_LIMIT_100K) {
									beanWriter.write(", ");	// do not append for last item
								}
								beanWriter.newLine();
								++currentSize;
								// System.out.println("currentSize  : " + currentSize);
							} else {
								if (DownloadJsonType.JSON_OBJECT.equals(jsonType) && !jsonObjectClosed) {
									beanWriter.write("]");
									jsonObjectClosed = true;
								}
								beanWriter.flush();
								isDone = true;
								break;
							}
						} catch (Exception ex) {
							//Logger.getLogger(JsonDeserializer.class.getName()).log(Level.SEVERE, "JSON file parsing exception at line {0}", lineNumber);
						}
					}	// end while
					br.close();
					if (isDone) {
						beanWriter.close();
						break;
					}
				}
			}	// end for		

		} catch (FileNotFoundException ex) {
			logger.error(collectionCode + ": couldn't find file");
			logger.error(elog.toStringException(ex));
		} catch (IOException ex) {
			logger.error(collectionCode + ": IO Exception for file read");
			logger.error(elog.toStringException(ex));
		} finally {
			if (beanWriter != null) {
				try {
					if (DownloadJsonType.JSON_OBJECT.equals(jsonType) && !jsonObjectClosed) {
						beanWriter.write("]");
						jsonObjectClosed = true;
					}
					beanWriter.close();
				} catch (IOException ex) {
					logger.error(collectionCode + ": IOException for JSON file write ");
					logger.error(elog.toStringException(ex));
				}
			}
		}
		return fileName;
	}

	public Map<String, Object> generateJson2TweetIdsJson(String collectionCode, final boolean downloadLimited, DownloadJsonType jsonType) {
		BufferedReader br = null;
		String fileName = "";
		BufferedWriter beanWriter = null;
		int totalCount = 0;
		String extension = DownloadJsonType.getSuffixString(jsonType);
		if (null == extension) {
			extension = DownloadJsonType.defaultSuffix();
		}
		boolean jsonObjectClosed = false;
		try {

			String folderLocation = Config.DEFAULT_PERSISTER_FILE_PATH + collectionCode;
			String fileNameforJsonGen = collectionCode + "_tweetIds";
			fileName = fileNameforJsonGen + extension;
			FileSystemOperations.deleteFile(Config.DEFAULT_PERSISTER_FILE_PATH + collectionCode + "/" + fileNameforJsonGen + extension);

			File folder = new File(folderLocation);
			File[] listOfFiles = folder.listFiles();

			Arrays.sort(listOfFiles, new Comparator<File>() {
				public int compare(File f1, File f2) {
					return Long.valueOf(f1.lastModified()).compareTo(f2.lastModified());
				}
			});

			StringBuffer outputFile = new StringBuffer().append(folderLocation)
					.append("/")
					.append(fileName);
			beanWriter = new BufferedWriter(new FileWriter(outputFile.toString()), BUFFER_SIZE);
			if (DownloadJsonType.JSON_OBJECT.equals(jsonType)) {
				beanWriter.write("[");
			}
			for (int i = 0; i < listOfFiles.length; i++) {
				File f = listOfFiles[i];
				String currentFileName = f.getName();
				if (currentFileName.endsWith(".json") 
						&& currentFileName.contains("vol")) {
					String line;
					logger.info("Reading file : " + f.getAbsolutePath());
					InputStream is = new FileInputStream(f.getAbsolutePath());
					br = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
					if (downloadLimited && totalCount > Config.DEFAULT_TWEETID_VOLUME_LIMIT) {
						break;
					}
					while ((line = br.readLine()) != null) {
						if (downloadLimited && totalCount > Config.DEFAULT_TWEETID_VOLUME_LIMIT) {
							break;
						}
						try {
							Tweet tweet = getTweet(line);
							if (tweet != null && tweet.getTweetID() != null) {
								JSONObject obj = new JSONObject();
								obj.put("id", tweet.getTweetID());
								//write to file
								beanWriter.write(obj.toJSONString());
								if (DownloadJsonType.JSON_OBJECT.equals(jsonType)
										&& totalCount < Config.DEFAULT_TWEETID_VOLUME_LIMIT) {
									beanWriter.write(", ");	// do not append for last item
								}
								beanWriter.newLine();
								++totalCount;
							}
						} catch (Exception ex) {
							//Logger.getLogger(JsonDeserializer.class.getName()).log(Level.SEVERE, "JSON file parsing exception at line {0}", lineNumber);
						}
					}	// end while
					br.close();
				}
			}	// end for		
			if (DownloadJsonType.JSON_OBJECT.equals(jsonType) && !jsonObjectClosed) {
				beanWriter.write("]");
				jsonObjectClosed = true;
			}
			beanWriter.flush();
			beanWriter.close();

		} catch (FileNotFoundException ex) {
			logger.error(collectionCode + ": couldn't find file");
			logger.error(elog.toStringException(ex));
		} catch (IOException ex) {
			logger.error(collectionCode + ": IO Exception for file read");
			logger.error(elog.toStringException(ex));
		} finally {
			if (beanWriter != null) {
				try {
					if (DownloadJsonType.JSON_OBJECT.equals(jsonType) && !jsonObjectClosed) {
						beanWriter.write("]");
						jsonObjectClosed = true;
					}
					beanWriter.close();
				} catch (IOException ex) {
					logger.error(collectionCode + ": IOException for JSON file write ");
					logger.error(elog.toStringException(ex));
				}
			}
		}
		return ResultStatus.getUIWrapper("fileName", fileName, "count", totalCount);
	}


	public String taggerGenerateJSON2JSON_100K_BasedOnTweetCount(String collectionCode, int exportLimit, DownloadJsonType jsonType) {
		BufferedReader br = null;
		String fileName = "";
		BufferedWriter beanWriter = null;

		String extension = DownloadJsonType.getSuffixString(jsonType);
		if (null == extension) {
			extension = DownloadJsonType.defaultSuffix();
		}
		boolean jsonObjectClosed = false;
		try {

			String folderLocation = Config.DEFAULT_PERSISTER_FILE_PATH + collectionCode + "/output";
			logger.info("For collection: " + collectionCode + ", will create file from folder: " + folderLocation);
			String fileNameforJsonGen = "Classified_" + collectionCode + "_last_100k_tweets";
			fileName = fileNameforJsonGen + extension;

			FileSystemOperations.deleteFile(folderLocation + "/" + fileNameforJsonGen + extension);
			logger.info("Deleted existing file: " + folderLocation + "/" + fileNameforJsonGen + extension);

			File folder = new File(folderLocation);
			File[] listOfFiles = folder.listFiles();
			// to get only Tagger's files
			ArrayList<File> taggerFilesList = new ArrayList();
			for (int i = 0; i < listOfFiles.length; i++) {
				if (StringUtils.startsWith(listOfFiles[i].getName(), "Classified_")
						&& StringUtils.containsIgnoreCase(listOfFiles[i].getName(), "vol")) {
					taggerFilesList.add(listOfFiles[i]);
				}
			}

			Object[] objectsArray = taggerFilesList.toArray();
			File[] taggerFiles = Arrays.copyOf(objectsArray, objectsArray.length, File[].class);
			Arrays.sort(taggerFiles, new Comparator<File>() {
				public int compare(File f1, File f2) {
					return Long.valueOf(f2.lastModified()).compareTo(f1.lastModified());	// koushik: changed sort order?
				}
			});

			StringBuffer outputFile = new StringBuffer().append(folderLocation)
					.append("/")
					.append(fileName);
			beanWriter = new BufferedWriter(new FileWriter(outputFile.toString()), BUFFER_SIZE);
			if (DownloadJsonType.JSON_OBJECT.equals(jsonType)) {
				beanWriter.write("[");
			}
			long currentSize = 0;
			boolean isDone = false;
			for (int i = 0; i < taggerFiles.length; i++) {
				File f = taggerFiles[i];
				String currentFileName = f.getName();
				if (currentFileName.endsWith(".json") 
						&& currentFileName.contains("vol")) {
					String line;
					logger.info("Reading file : " + f.getAbsolutePath());
					InputStream is = new FileInputStream(f.getAbsolutePath());
					br = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));

					while ((line = br.readLine()) != null) {
						try {
							if (currentSize < Config.TWEETS_EXPORT_LIMIT_100K) {
								//write to file
								beanWriter.write(line);
								if (DownloadJsonType.JSON_OBJECT.equals(jsonType)
										&& currentSize < Config.TWEETS_EXPORT_LIMIT_100K) {
									beanWriter.write(", ");
								}
								beanWriter.newLine();
								++currentSize;
								// System.out.println("currentSize  : " + currentSize);
							} else {
								if (DownloadJsonType.JSON_OBJECT.equals(jsonType) && !jsonObjectClosed) {
									beanWriter.write("]");
									jsonObjectClosed = true;
								}
								beanWriter.flush();
								isDone = true;
								break;
							}
						} catch (Exception ex) {
							//Logger.getLogger(JsonDeserializer.class.getName()).log(Level.SEVERE, "JSON file parsing exception at line {0}", lineNumber);
						}
					}	// end while						
					br.close();
					if (isDone) {
						beanWriter.close();
						break;
					}
				}
			}	// end for	
		} catch (FileNotFoundException ex) {
			logger.error(collectionCode + ": couldn't find file");
			logger.error(elog.toStringException(ex));
		} catch (IOException ex) {
			logger.error(collectionCode + ": IO Exception for file read");
			logger.error(elog.toStringException(ex));
		} catch (NullPointerException ex) {
			logger.error(collectionCode + ": empty list of files to read");
			logger.info(elog.toStringException(ex));
		} finally {
			if (beanWriter != null) {
				try {
					if (DownloadJsonType.JSON_OBJECT.equals(jsonType) && !jsonObjectClosed) {
						beanWriter.write("]");
						jsonObjectClosed = true;
					}
					beanWriter.close();
				} catch (IOException ex) {
					logger.error(collectionCode + ": IOException for JSON file write ");
					logger.error(elog.toStringException(ex));
				}
			}
		}
		return fileName;
	}

	public Map<String, Object> generateClassifiedJson2TweetIdsJSON(String collectionCode, final boolean downloadLimited,
			DownloadJsonType jsonType) {
		String extension = DownloadJsonType.getSuffixString(jsonType);
		if (null == extension) {
			extension = DownloadJsonType.defaultSuffix();
		}
		boolean jsonObjectClosed = false;

		BufferedWriter beanWriter = null;
		String folderLocation = Config.DEFAULT_PERSISTER_FILE_PATH + collectionCode + "/output";
		String fileNameforJsonGen = "Classified_" + collectionCode + "_tweetIds";
		String fileName = fileNameforJsonGen + extension;
		int totalCount = 0;

		try {
			List<String> fileNames = FileSystemOperations.getClassifiedFileVolumes(collectionCode);

			BufferedReader br = null;
			String fileToDelete = Config.DEFAULT_PERSISTER_FILE_PATH + collectionCode + "/output/" + "Classified_" + collectionCode + "_tweetIds" + extension;
			System.out.println("Deleteing file : " + fileToDelete);
			FileSystemOperations.deleteFile(fileToDelete); // delete if there exist a csv file with same name
			//System.out.println(fileNames);

			StringBuffer outputFile = new StringBuffer().append(folderLocation)
					.append("/")
					.append(fileName);
			beanWriter = new BufferedWriter(new FileWriter(outputFile.toString()), BUFFER_SIZE);
			if (DownloadJsonType.JSON_OBJECT.equals(jsonType)) {
				beanWriter.write("[");
			}
			for (String file : fileNames) {
				if (downloadLimited && totalCount > Config.DEFAULT_TWEETID_VOLUME_LIMIT) {
					break;
				}
				String fileLocation = Config.DEFAULT_PERSISTER_FILE_PATH + collectionCode + "/output/" + file;
				logger.info("Reading file " + fileLocation);
				try {
					br = new BufferedReader(new FileReader(fileLocation)); 
					String line;
					while ((line = br.readLine()) != null) {
						if (downloadLimited && totalCount >= Config.DEFAULT_TWEETID_VOLUME_LIMIT) {
							break;
						}
						ClassifiedTweet tweet = getClassifiedTweet(line);
						if (tweet != null && tweet.getTweetID() != null) {
							if (!tweet.getNominalLabels().isEmpty()) {
								//write to file
								beanWriter.write(createJsonClassifiedTweetIDString(tweet));
								if (DownloadJsonType.JSON_OBJECT.equals(jsonType)
										&& totalCount < Config.DEFAULT_TWEETID_VOLUME_LIMIT) {
									beanWriter.write(", ");
								}
								beanWriter.newLine();
							}
							++totalCount;
						}  
					}
					br.close();

				} catch (FileNotFoundException ex) {
					logger.error(collectionCode + ": couldn't find file");
					logger.error(elog.toStringException(ex));
				} catch (IOException ex) {
					logger.error(collectionCode + ": IO Exception for file read");
					logger.error(elog.toStringException(ex));
				} 
			}	// end for 
			if (DownloadJsonType.JSON_OBJECT.equals(jsonType) && !jsonObjectClosed) {
				beanWriter.write("]");
				jsonObjectClosed = true;
			}
			beanWriter.flush();
			beanWriter.close();

		} catch (FileNotFoundException ex) {
			logger.error(collectionCode + ": couldn't find file");
			logger.error(elog.toStringException(ex));
		} catch (IOException ex) {
			logger.error(collectionCode + ": IO Exception for file read");
			logger.error(elog.toStringException(ex));
		} finally {
			if (beanWriter != null) {
				try {
					if (DownloadJsonType.JSON_OBJECT.equals(jsonType) && !jsonObjectClosed) {
						beanWriter.write("]");
						jsonObjectClosed = true;
					}
					beanWriter.close();
				} catch (IOException ex) {
					logger.error(collectionCode + ": IOException for JSON file write ");
					logger.error(elog.toStringException(ex));
				}
			}
		}
		return ResultStatus.getUIWrapper("fileName", fileName, "count", totalCount);
	}



	public String taggerGenerateJSON2JSON_100K_BasedOnTweetCountFiltered(
			String collectionCode, int exportLimit, JsonQueryList queryList,
			DownloadJsonType jsonType) {

		BufferedReader br = null;
		String fileName = "";
		BufferedWriter beanWriter = null;
		String extension = DownloadJsonType.getSuffixString(jsonType);
		if (null == extension) {
			extension = DownloadJsonType.defaultSuffix();
		}
		boolean jsonObjectClosed = false;
		try {

			//String folderLocation = Config.DEFAULT_PERSISTER_FILE_PATH + collectionCode + "/output";
			//String fileNameforJsonGen = "Classified_" + collectionCode + "_last_100k_tweets_filtered";
			String folderLocation = Config.DEFAULT_PERSISTER_FILE_PATH + collectionCode + "/";
			String fileNameforJsonGen = collectionCode + "_last_100k_tweets_filtered";
			
			fileName = fileNameforJsonGen + extension;

			FileSystemOperations.deleteFile(folderLocation + "/" + fileNameforJsonGen + extension);
			logger.info("Deleted existing file: " + folderLocation + "/" + fileNameforJsonGen + extension);

			File folder = new File(folderLocation);
			File[] listOfFiles = folder.listFiles();
			// to get only Tagger's files
			ArrayList<File> taggerFilesList = new ArrayList();
			for (int i = 0; i < listOfFiles.length; i++) {
				if (StringUtils.startsWith(listOfFiles[i].getName(), (collectionCode + "_"))
						&& StringUtils.containsIgnoreCase(listOfFiles[i].getName(), "vol")) {
					taggerFilesList.add(listOfFiles[i]);
				}
			}

			Object[] objectsArray = taggerFilesList.toArray();
			File[] taggerFiles = Arrays.copyOf(objectsArray, objectsArray.length, File[].class);
			Arrays.sort(taggerFiles, new Comparator<File>() {
				public int compare(File f1, File f2) {
					return Long.valueOf(f2.lastModified()).compareTo(f1.lastModified());	// koushik: changed sort order?
				}
			});
			
			
			StringBuffer outputFile = new StringBuffer().append(folderLocation).append(fileName);
			beanWriter = new BufferedWriter(new FileWriter(outputFile.toString()), BUFFER_SIZE);
			if (DownloadJsonType.JSON_OBJECT.equals(jsonType)) {
				beanWriter.write("[");
			}
			//First build the FilterQueryMatcher
			FilterQueryMatcher tweetFilter = new FilterQueryMatcher();
			if (queryList != null) tweetFilter.queryList.setConstraints(queryList);
			tweetFilter.buildMatcherArray();

			long currentSize = 0;

			boolean isDone = false;
			for (int i = taggerFiles.length-1; i >= 0; i--) {
				File f = taggerFiles[i];
				String currentFileName = f.getName();
				if (currentFileName.endsWith(".json") 
						&& currentFileName.contains("vol")) {
					String line;
					logger.info("Reading file : " + f.getAbsolutePath());
					InputStream is = new FileInputStream(f.getAbsolutePath());
					br = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
					while ((line = br.readLine()) != null) {
						try {
							ClassifiedTweet tweet = getClassifiedTweet(line);
							if (currentSize < Config.TWEETS_EXPORT_LIMIT_100K) {
								// Apply filter on tweet
								if (satisfiesFilter(queryList, tweetFilter, tweet)) {
									//write to file
									beanWriter.write(line);
									if (DownloadJsonType.JSON_OBJECT.equals(jsonType)
											&& currentSize < Config.TWEETS_EXPORT_LIMIT_100K) {
										beanWriter.write(", ");
									}
									beanWriter.newLine();
									++currentSize;
								}
								// System.out.println("currentSize  : " + currentSize);
							} else {
								if (DownloadJsonType.JSON_OBJECT.equals(jsonType) && !jsonObjectClosed) {
									beanWriter.write("]");
									jsonObjectClosed = true;
								}
								beanWriter.flush();
								isDone = true;
								break;
							}
						} catch (Exception ex) {
							//Logger.getLogger(JsonDeserializer.class.getName()).log(Level.SEVERE, "JSON file parsing exception at line {0}", lineNumber);
						}
					}	// end while						
					br.close();
					if (isDone) {
						beanWriter.close();
						break;
					}
				}
			}	// end for	
		} catch (FileNotFoundException ex) {
			logger.error(collectionCode + ": couldn't find file");
			logger.error(elog.toStringException(ex));
		} catch (IOException ex) {
			logger.error(collectionCode + ": IO Exception for file read");
			logger.error(elog.toStringException(ex));
		} catch (NullPointerException ex) {
			logger.error(collectionCode + ": empty list of files to read");
			logger.info(elog.toStringException(ex));
		} finally {
			if (beanWriter != null) {
				try {
					if (DownloadJsonType.JSON_OBJECT.equals(jsonType) && !jsonObjectClosed) {
						beanWriter.write("]");
						jsonObjectClosed = true;
					}
					beanWriter.close();
				} catch (IOException ex) {
					logger.error(collectionCode + ": IOException for JSON file write ");
					logger.error(elog.toStringException(ex));
				}
			}
		}
		return fileName;
	}


	public Map<String, Object> generateClassifiedJson2TweetIdsJSONFiltered(
			String collectionCode, Boolean downloadLimited,
			JsonQueryList queryList, DownloadJsonType jsonType) {

		String extension = DownloadJsonType.getSuffixString(jsonType);
		if (null == extension) {
			extension = DownloadJsonType.defaultSuffix();
		}
		boolean jsonObjectClosed = false;

		BufferedWriter beanWriter = null;
		//String folderLocation = Config.DEFAULT_PERSISTER_FILE_PATH + collectionCode + "/output";
		//String fileNameforJsonGen = "Classified_" + collectionCode + "_tweetIds_filtered";
		String folderLocation = Config.DEFAULT_PERSISTER_FILE_PATH + collectionCode + "/";
		String fileNameforJsonGen = collectionCode + "_tweetIds_filtered";
		String fileName = fileNameforJsonGen + extension;
		int totalCount = 0;

		try {
			//List<String> fileNames = FileSystemOperations.getClassifiedFileVolumes(collectionCode);
			List<String> fileNames = FileSystemOperations.getAllJSONFileVolumes(collectionCode);
			Collections.sort(fileNames);
			Collections.reverse(fileNames);
			
			BufferedReader br = null;
			//String fileToDelete = Config.DEFAULT_PERSISTER_FILE_PATH + collectionCode + "/output/" + "Classified_" + collectionCode + "_tweetIds_filtered" + extension;
			String fileToDelete = Config.DEFAULT_PERSISTER_FILE_PATH + collectionCode + "/" + fileName;
			System.out.println("Deleteing file : " + fileToDelete);
			FileSystemOperations.deleteFile(fileToDelete); // delete if there exist a csv file with same name
			//System.out.println(fileNames);

			StringBuffer outputFile = new StringBuffer().append(folderLocation).append(fileName);
			beanWriter = new BufferedWriter(new FileWriter(outputFile.toString()), BUFFER_SIZE);
			if (DownloadJsonType.JSON_OBJECT.equals(jsonType)) {
				beanWriter.write("[");
			}
			//First build the FilterQueryMatcher
			FilterQueryMatcher tweetFilter = new FilterQueryMatcher();
			if (queryList != null) tweetFilter.queryList.setConstraints(queryList);
			tweetFilter.buildMatcherArray();

			for (String file : fileNames) {
				if (downloadLimited && totalCount > Config.DEFAULT_TWEETID_VOLUME_LIMIT) {
					break;
				}
				//String fileLocation = Config.DEFAULT_PERSISTER_FILE_PATH + collectionCode + "/output/" + file;
				String fileLocation = Config.DEFAULT_PERSISTER_FILE_PATH + collectionCode + "/" + file;
				logger.info("Reading file " + fileLocation);
				try {
					br = new BufferedReader(new FileReader(fileLocation)); 
					String line;
					while ((line = br.readLine()) != null) {
						if (downloadLimited && totalCount >= Config.DEFAULT_TWEETID_VOLUME_LIMIT) {
							break;
						}
						ClassifiedTweet tweet = getClassifiedTweet(line);
						if (tweet != null && tweet.getTweetID() != null) {
							// Apply filter on tweet
							if (satisfiesFilter(queryList, tweetFilter, tweet)) {								
								//write to file
								beanWriter.write(createJsonClassifiedTweetIDString(tweet));
								if (DownloadJsonType.JSON_OBJECT.equals(jsonType)
										&& totalCount < Config.DEFAULT_TWEETID_VOLUME_LIMIT) {
									beanWriter.write(", ");
								}
								beanWriter.newLine();
								++totalCount;
							}
						}  
					}
					br.close();

				} catch (FileNotFoundException ex) {
					logger.error(collectionCode + ": couldn't find file");
					logger.error(elog.toStringException(ex));
				} catch (IOException ex) {
					logger.error(collectionCode + ": IO Exception for file read");
					logger.error(elog.toStringException(ex));
				} 
			}	// end for 
			if (DownloadJsonType.JSON_OBJECT.equals(jsonType) && !jsonObjectClosed) {
				beanWriter.write("]");
				jsonObjectClosed = true;
			}
			beanWriter.flush();
			beanWriter.close();

		} catch (FileNotFoundException ex) {
			logger.error(collectionCode + ": couldn't find file");
			logger.error(elog.toStringException(ex));
		} catch (IOException ex) {
			logger.error(collectionCode + ": IO Exception for file read");
			logger.error(elog.toStringException(ex));
		} finally {
			if (beanWriter != null) {
				try {
					if (DownloadJsonType.JSON_OBJECT.equals(jsonType) && !jsonObjectClosed) {
						beanWriter.write("]");
						jsonObjectClosed = true;
					}
					beanWriter.close();
				} catch (IOException ex) {
					logger.error(collectionCode + ": IOException for JSON file write ");
					logger.error(elog.toStringException(ex));
				}
			}
		}
		return ResultStatus.getUIWrapper("fileName", fileName, "count", totalCount);
	}


	public String createJsonClassifiedTweetIDString(ClassifiedTweet tweet) {
		JSONObject obj = new JSONObject();

		obj.put("id", tweet.getTweetID());
		obj.put("crisis_name", tweet.getCrisisName());
		if (tweet.getNominalLabels() != null) {
			List<NominalLabel> nbList = tweet.getNominalLabels();
			for (int i = 0;i < nbList.size();i++) {
				NominalLabel nb = nbList.get(i);
				obj.put("attribute_name_"+i, nb.attribute_name);
				obj.put("attribute_code_"+i, nb.attribute_code);
				obj.put("label_name_"+i, nb.label_name);
				obj.put("label_description_"+i, nb.label_description);
				obj.put("label_code_"+i, nb.label_code);
				obj.put("confidence_"+i, nb.confidence);
				obj.put("humanLabeled_"+i, nb.from_human);
			}
		}
		return obj.toJSONString();
	}


	public boolean satisfiesFilter(final JsonQueryList queryList, final FilterQueryMatcher tweetFilter, final ClassifiedTweet tweet) {
		// Apply filter on tweet
		if (null == queryList || queryList.getConstraints().isEmpty()) {
			return true;		// no filtering
		} else { 
			if (!tweet.getNominalLabels().isEmpty()) {
				return tweetFilter.getMatcherResult(tweet);	//satisfies filter
			}
		}
		return false;
	}

}
