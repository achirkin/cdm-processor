package ch.ethz.arch.ia.cdm.gui;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.List;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.scene.Group;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Tab;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Popup;

import org.apache.commons.io.FilenameUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.WriterAppender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.ethz.arch.ia.cdm.MainApp;
import ch.ethz.arch.ia.cdm.create.PdfCreator;
import ch.ethz.arch.ia.cdm.create.PdfCreator.GroupBy;
import ch.ethz.arch.ia.cdm.parse.PdfParser;

import com.codepoetics.protonpack.StreamUtils;
import com.google.zxing.WriterException;
import com.itextpdf.text.DocumentException;

public class MainPageController {
    private static final Logger log = LoggerFactory.getLogger(MainPageController.class);

    @FXML private TextArea layerList;
    @FXML private TextArea mapList;

    @FXML private RadioButton radioGBLayers;
    @FXML private RadioButton radioGBMaps;
    
    @FXML private CheckBox colorCorrectionCheck;
    
    /**
     * sets up fields
     */
    public void initData() {
    	MainApp.configMan.setupCreator();
    	// layers test area
    	String layers = "";
    	for(String layer : PdfCreator.layernames)
    		layers += layer + "\n";
    	layerList.setText(layers);
    	// radio bar
    	radioGBLayers.setSelected(PdfCreator.groupPages == GroupBy.LAYERNAMES);
    	radioGBMaps.setSelected(PdfCreator.groupPages == GroupBy.MAPNAMES);
    	// check box
    	colorCorrectionCheck.setSelected(PdfParser.correctColors);
    	// logger
    	logAreaAppender.setName("logAreaAppender");
    	logAreaAppender.setThreshold(org.apache.log4j.Level.DEBUG);
	}

    @FXML private TextArea logArea;
    private WriterAppender logAreaAppender = new WriterAppender(
    		new PatternLayout("%p %C{1} - %m%n"),
    		new OutputStream() {
    			@Override
    			public void write(int b) throws IOException {
    				Platform.runLater(() -> logArea.appendText(String.valueOf((char) b)));
    			}
    		});
    
    
    /**
     * Generates PDF - main method
     */
    public void generatePDF(){
    	saveCreator();
    	FileChooser fc = new FileChooser();
    	fc.setTitle("Select one or several files");
		fc.setInitialDirectory(new File(System.getProperty("user.dir")));
		fc.setInitialFileName("task_" + (System.currentTimeMillis() % 10000000) + ".pdf");
		File createdPDF = fc.showSaveDialog(MainApp.mainWindow);
		
    	

    	final Stage popupStage = new Stage();
    	String family = "Helvetica";
    	double size = 40;
    	double w = MainApp.mainWindow.getWidth()/2;
    	double h = MainApp.mainWindow.getHeight()/4;
    	double x = MainApp.mainWindow.getX() + MainApp.mainWindow.getWidth()/4;
    	double y = MainApp.mainWindow.getY() + MainApp.mainWindow.getHeight()/4;
    	TextFlow textFlow = new TextFlow();
    	textFlow.setTextAlignment(TextAlignment.CENTER);
    	Text text1 = new Text("Please wait...");
    	text1.setTextAlignment(TextAlignment.CENTER);
    	text1.setFont(Font.font(family, size));
    	textFlow.getChildren().addAll(text1);
    	Group group = new Group(textFlow);
    	Scene scene = new Scene(group, w, h, Color.color(0.95, 0.95, 0.95));
        popupStage.setWidth(w);
        popupStage.setHeight(h);
    	popupStage.setTitle("Program is in progress");
    	popupStage.setScene(scene);
    	popupStage.setResizable(false);    	
    	textFlow.setLayoutY((h-size)/2);
    	textFlow.setPrefWidth(w);
    	textFlow.setMinWidth(w);
    	textFlow.setMaxWidth(w);
    	
		MainApp.mainWindow.hide();
    	log.debug("Starting PdfCreator...");
    	
    	try {
            popupStage.show();
        	popupStage.sizeToScene();
        	popupStage.centerOnScreen();
			PdfCreator.createPdf(createdPDF);
	    	log.debug("Finished creation of a document.");
            popupStage.hide();
            popupStage.close();
	    	MainApp.mainWindow.show();
	    	Thread t = new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						java.awt.Desktop.getDesktop().open(createdPDF);
				    } catch (Exception ex) {
				    	log.warn("Could not open document: " + ex.getMessage());
				    }
				}
			});
	    	t.start();
	    	new Thread(new Runnable() {
				@Override
				public void run() {
					try{
						
						for(int i = 0; i < 20 && t.isAlive(); i++)
							Thread.sleep(500);
						if (t.isAlive())
							t.interrupt();
					} catch(Exception ex) {
						
					}
				}
			}).start();
	    	
		} catch (DocumentException | IOException | WriterException e) {
            popupStage.hide();
            popupStage.close();
			MainApp.mainWindow.show();
			log.error("Failed to create document: " + e.getMessage(), e);
		}
    }
    
	public void parseFiles(){
    	saveParser();
        org.apache.log4j.Logger loga = LogManager.getRootLogger();
        loga.addAppender(logAreaAppender);
		log.debug("Parsing documents");
		
		parseProgress = 0;
		PdfParser.ParsingEventListener pel = new PdfParser.ParsingEventListener() {
			@Override
			public void parsedPage(boolean success) {
				parseProgress++;
			}
			
			@Override
			public void parsingProgress(double progress) {
	    		progressBar.setProgress(((double)parseProgress+progress)/(double)nump);
			}
		};
		PdfParser.AddParsingEventListener(pel);

		progressBar.setDisable(false);
		progressBar.setVisible(true);
		buttonPickIm.setDisable(true);
		tabCreate.setDisable(true);
		colorCorrectionCheck.setDisable(true);
		parseButton.setDisable(true);
		
		Thread t = new Thread() {
			@Override
			public void run() {
				for(File f : sheets)
			    	try {
						PdfParser.parseDocument(f.getPath());
			    	} catch (IOException e) {
			    		log.error("Failed to parse file " + f.getName() + ": " + e.getMessage());
			    	}
				progressBar.setProgress(1.0);
			    log.debug("Finished parsing.");
			    loga.removeAppender(logAreaAppender);
			    
			    buttonPickIm.setDisable(false);
				tabCreate.setDisable(false);
				colorCorrectionCheck.setDisable(false);
				sheets = null;
				numf = 0;
				nump = 0;
				PdfParser.RemoveParsingEventListener(pel);
			}
		};
		t.start();
		
    }
    @FXML private ProgressBar progressBar;
    @FXML private Button buttonPickIm;
    @FXML private Tab tabCreate;
    private int parseProgress;

    
    
    /**
     * applies and saves configuration of PdfCreator
     */
    public void saveCreator(){
    	applyConfigForCreator();
    	MainApp.configMan.saveCreator();
    }
    
    /**
     * applies and saves configuration of PdfCreator
     */
    public void saveParser(){
    	applyConfigForParser();
    	MainApp.configMan.saveParser();
    }
    
    /**
     * checks if provided information is enough to generate PDF
     * @return if enough
     */
    public boolean checkFillState(){
    	if(files == null)
    		return false;
    	if(files.size() < 1)
    		return false;
    	if(layerList.getText().split("\\s*\n\\s*").length < 1)
    		return false;
    	if(mapList.getText().split("\\s*\n\\s*").length < 1)
    		return false;
    	return true;
    }
    @FXML private Button submitButton;
    /**
     * changes the state of submit button
     */
    public void submitButtonState(){
    	if(checkFillState())
    		submitButton.setDisable(false);
    	else
    		submitButton.setDisable(true);
    }
    @FXML private Button parseButton;
    public void parseButtonState(){
    	if(sheets == null || sheets.isEmpty())
    		parseButton.setDisable(true);
    	else
    		parseButton.setDisable(false);
    }
    
    /**
     * saves current input
     */
    public void applyConfigForCreator(){
    	// save grouping policy
    	if(radioGBLayers.selectedProperty().get())
    		PdfCreator.groupPages = PdfCreator.GroupBy.LAYERNAMES;
    	else if(radioGBMaps.selectedProperty().get())
    		PdfCreator.groupPages = PdfCreator.GroupBy.MAPNAMES;
    	// save maps
    	PdfCreator.maps.clear();
    	StreamUtils.zip(
    			Arrays.stream(mapList.getText().split("\\s*\n\\s*")),
    			files.stream(),
    			(mapname, file) -> new AbstractMap.SimpleEntry<String, String>(mapname, file.getPath())
    			).forEach(kv -> PdfCreator.maps.put(kv.getKey(), kv.getValue()));
    	// save layernames
    	PdfCreator.layernames = layerList.getText().split("\\s*\n\\s*");
    }
    public void applyConfigForParser(){
    	// check box
    	PdfParser.correctColors = colorCorrectionCheck.selectedProperty().get();
    }
    
    @FXML private Label selectedFilesLabel;
    private List<File> files = null;
    /**
     * runs file selector
     */
    public void pickFilesForCreator(){
    	FileChooser fc = new FileChooser();
    	fc.setTitle("Select one or several files");
//    	log.debug("Selecting files");
		fc.setInitialDirectory(new File(System.getProperty("user.dir")));
		
    	files = fc.showOpenMultipleDialog(MainApp.mainWindow);
    	if(files == null || files.isEmpty()){
    		selectedFilesLabel.setText("");
    		submitButtonState();
			return;
    	}
    	
    	int numf = files.size();
    	switch (numf) {
		case 0:
			selectedFilesLabel.setText("");
			break;
		case 1:
			selectedFilesLabel.setText(files.get(0).getName());
			break;
		default:
			selectedFilesLabel.setText("" + numf + " files in " + files.get(0).getParentFile());
			break;
		}
    	if (numf > 0){
    		String maps = files.stream()
    				.map(f -> FilenameUtils.getBaseName(f.getName()))
    				.reduce((a,b) -> a + "\n" + b).get();
    		mapList.setText(maps);
    	}
    	submitButtonState();
    }
    
    /**
     * runs editor for PDF template text
     */
    public void runPdfTextEditor(){
    	Parent root;
        try {
        	FXMLLoader loader = new FXMLLoader();
            root = loader.load(getClass().getResourceAsStream("/fxml/PdfTextEditor.fxml"));
            Stage stage = new Stage();
            stage.getIcons().add(new Image(getClass().getResourceAsStream("/images/cdm-processor.png")));
            stage.setTitle("PDF header & footer template");
            stage.setScene(new Scene(root));
            stage.show();
            

            stage.setMinWidth(stage.getWidth());
            stage.setMinHeight(stage.getHeight());
            stage.setMaxWidth(stage.getWidth());
            stage.setMaxHeight(stage.getHeight());
            
            PdfTextController c = loader.<PdfTextController>getController();
            c.initData();

            //hide this current window (if this is whant you want
//            ((Node)(event.getSource())).getScene().getWindow().hide();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    ////////////////////////////////////////////////////////////////////////////////////////////////////
    
    @FXML private Label selectedSheetsLabel;
    private List<File> sheets = null;
    private int nump, numf;
    /**
     * runs file selector
     */
    public void pickFilesForParser(){
    	FileChooser fc = new FileChooser();
    	fc.setTitle("Select one or several documents");
		fc.setInitialDirectory(new File(System.getProperty("user.dir")));

		logArea.setText("");
		sheets = fc.showOpenMultipleDialog(MainApp.mainWindow);
		if(sheets == null || sheets.isEmpty()){
			selectedSheetsLabel.setText("");
    		progressBar.setProgress(0);
    		parseProgress = 0;
    		progressBar.setDisable(true);
    		progressBar.setVisible(false);
    		numf = 0;
    		nump = 0;
    		parseButtonState();
			return;
    	}
		
    	numf = sheets.size();
    	nump = sheets.stream().reduce(0, (c,f) -> c + PdfParser.countPages(f), (a,b) -> a+b).intValue();
    	switch (numf) {
		case 0:
			selectedSheetsLabel.setText("");
			break;
		default:
			selectedSheetsLabel.setText("" + numf + " file" + (numf == 1 ? "" : "s") + " (" + 
					nump + " page" + (nump == 1 ? "" : "s") + ")");
			break;
		}
    	parseButtonState();
		progressBar.setProgress(0);
		parseProgress = 0;
		progressBar.setDisable(true);
		progressBar.setVisible(false);
    }
}