package ch.ethz.arch.ia.cdm;

import java.io.IOException;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.ethz.arch.ia.cdm.gui.MainPageController;

public class MainApp extends Application {

    private static final Logger log = LoggerFactory.getLogger(MainApp.class);
    
    public static final ConfigManager configMan = new ConfigManager();

    public static void main(String[] args){
        launch(args);
    }
    
    public static Stage mainWindow = null;

    @Override
    public void start(Stage stage) throws IOException{
    	
    	mainWindow = stage;
    	stage.getIcons().add(new Image(getClass().getResourceAsStream("/images/cdm-processor.png")));

        log.info("Starting Hello JavaFX and Maven demonstration application");

        String fxmlFile = "/fxml/MainPage.fxml";

        log.debug("Loading FXML for main view from: {}", fxmlFile);
        FXMLLoader loader = new FXMLLoader();
        Parent rootNode = (Parent) loader.load(getClass().getResourceAsStream(fxmlFile));
        
        
        log.debug("Showing JFX scene");
        Scene scene = new Scene(rootNode);
        scene.getStylesheets().add("/styles/styles.css");

        stage.setTitle("cdm-processor");
        stage.setScene(scene);

        MainPageController c = loader.<MainPageController>getController();
        c.initData();
        
        stage.show();

        stage.setMinWidth(stage.getWidth());
        stage.setMinHeight(stage.getHeight());
        
    }
}
