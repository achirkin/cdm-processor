package ch.ethz.arch.ia.cdm.gui;

import javafx.fxml.FXML;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import ch.ethz.arch.ia.cdm.create.PdfCreator;

public class PdfTextController {
	@FXML private TextArea textLT;
	@FXML private TextArea textLB;
	@FXML private TextArea textRT;
	@FXML private TextArea textRB;
	
	public void initData(){
//		MainApp.configMan.setupCreator();
		textLT.setText(PdfCreator.leftTop);
		textLB.setText(PdfCreator.leftBottom);
		textRT.setText(PdfCreator.rightTop);
		textRB.setText(PdfCreator.rightBottom);
	}
	
	public void close(){
		Stage stage = (Stage) textLT.getScene().getWindow();
		stage.close();
	}
	
	public void save(){
		PdfCreator.leftTop = textLT.getText();
		PdfCreator.leftBottom = textLB.getText();
		PdfCreator.rightTop = textRT.getText();
		PdfCreator.rightBottom = textRB.getText();
//		MainApp.configMan.saveCreator();
		close();
	}
}
