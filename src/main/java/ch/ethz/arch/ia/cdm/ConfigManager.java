package ch.ethz.arch.ia.cdm;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.ethz.arch.ia.cdm.create.PdfCreator;
import ch.ethz.arch.ia.cdm.parse.PdfParser;

public class ConfigManager {
	private static final String confFileName = "config.xml";
	private static final String defConfFN = "/config.xml";
	
	private final String userDirectory = System.getProperty("user.dir");
	public String getDirectory(){
		return userDirectory;
	}
	public String getFullPath(String filename) {
		return FilenameUtils.concat(userDirectory, filename);
	}
	
	
	private static final Logger log = LoggerFactory.getLogger(ConfigManager.class);
	
	private XMLConfiguration config;
	
	public ConfigManager(){
		try{
		checkXml();
		try {
			config = new XMLConfiguration(getFullPath(confFileName));
			log.debug("Reading configuration file {}", confFileName);
		} catch (ConfigurationException e) {
			log.error("Failed to read configuration file {}: " + e.getMessage(), confFileName);
			return;
		}
		setupCreator();
		setupParser();
		} catch (Exception ex) {
			log.error("Failed to configure using file {}: " + ex.getMessage(), confFileName);
			System.exit(1);
		}
	}
	
	public void setupCreator(){
		PdfCreator.leftTop = config.getString("pdfText.lefttop", "text on left-top");
		PdfCreator.leftBottom = config.getString("pdfText.leftbottom", "text on left-bottom");
		PdfCreator.rightTop = config.getString("pdfText.righttop", "text on right-top");
		PdfCreator.rightBottom = config.getString("pdfText.rightbottom", "text on right-bottom");
		PdfCreator.layernames = config.getStringArray("defaultLayers.layer");
		try{
			PdfCreator.groupPages = PdfCreator.GroupBy.valueOf(config.getString("groupMapsBy", "LAYERNAMES"));
		} catch (IllegalArgumentException e) {
			log.warn("Could not understand groupBy value: " + e.getMessage() + ". Continue with default page grouping policy.");
		}
	}
	
	public void saveCreator(){
		config.setProperty("pdfText.lefttop", PdfCreator.leftTop);
		config.setProperty("pdfText.leftbottom", PdfCreator.leftBottom);
		config.setProperty("pdfText.righttop", PdfCreator.rightTop);
		config.setProperty("pdfText.rightbottom", PdfCreator.rightBottom);
		config.setProperty("defaultLayers.layer", PdfCreator.layernames);
		config.setProperty("groupMapsBy", PdfCreator.groupPages);
		try {
			config.save();
		} catch (ConfigurationException e) {
			log.error("failed to save configuration of PdfCreator: " + e.getMessage());
		}
	}
	
	public void setupParser(){
		PdfParser.imgExtension = config.getString("parsedImg.extension", ".png");
		PdfParser.imgSize = config.getInt("parsedImg.size", 2000);
		PdfParser.imgMargin = config.getInt("parsedImg.margin", 5);
		PdfParser.correctColors = config.getBoolean("parsedImg.correctColors", true);
	}
	
	public void saveParser(){
		config.setProperty("parsedImg.correctColors", PdfParser.correctColors);
		try {
			config.save();
		} catch (ConfigurationException e) {
			log.error("failed to save configuration of PdfCreator: " + e.getMessage());
		}
	}
	
	private void checkXml(){
		File f = new File(getFullPath(confFileName));
		boolean exists = false;
		try {
			if(f.exists()) {
				if (f.isDirectory()) {
					FileUtils.deleteDirectory(f);
				} else if (f.length() <= 0) {
					f.delete();
				} else {
					try {
						@SuppressWarnings("unused")
						XMLConfiguration c = new XMLConfiguration(f);
						exists = true;
						} catch (ConfigurationException e) {
							f.delete();
						}
				}
			}
		} catch (IOException e) {
			log.warn("Error occured while trying to delete directory " + confFileName + ": " + e.getMessage());
		}
		
		
		if(!exists){
			InputStream defaultConfStream = getClass().getResourceAsStream(defConfFN);
			try {
				FileOutputStream os = 
	                    new FileOutputStream(getFullPath(confFileName));
	 
				int read = 0;
				byte[] bytes = new byte[1024];
	 
				while ((read = defaultConfStream.read(bytes)) != -1) {
					os.write(bytes, 0, read);
				}
				os.close();
			} catch (IOException e) {
				log.warn("Could not copy " + defConfFN + " from jar resources to " + confFileName + " in application folder: " + e.getMessage());
			}
		}

	}
}
