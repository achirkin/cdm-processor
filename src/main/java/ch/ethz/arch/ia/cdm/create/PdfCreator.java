/**
 * 
 */
package ch.ethz.arch.ia.cdm.create;

import static org.bytedeco.javacpp.opencv_core.cvMixChannels;
import static org.bytedeco.javacpp.opencv_core.cvSize;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.bytedeco.javacpp.opencv_core.CvSize;
import org.bytedeco.javacpp.opencv_core.IplImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Image;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.ColumnText;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfPageEventHelper;
import com.itextpdf.text.pdf.PdfWriter;

/**
 * @author achirkin
 *
 */
public class PdfCreator {
	

	public enum GroupBy { LAYERNAMES, MAPNAMES };

	private static final Logger log = LoggerFactory.getLogger(PdfCreator.class);


	public static String leftTop = "Text on left-top corner";
	public static String leftBottom = "Text on left-bottom corner";
	public static String rightTop = "Text on right-top corner";
	public static String rightBottom = "Text on right-bottom corner";
	public static String[] layernames = new String[0];
	
	public static HashMap<String, String> maps = new HashMap<String, String>();
	public static GroupBy groupPages = GroupBy.LAYERNAMES;

	
	// drawing parameters
	private static final float topPlaceSize = 80;
	private static final float bottomPlaceSize = 50;
	private static final float pageMargin = 50;
	
	private static final float mapAspectRatio = 1f;
	private static final float mapMargin = 6;
	
	private static final float mapWidth = PageSize.A4.getWidth() - mapMargin*2 - pageMargin*2;
	private static final float mapHeight = mapWidth / mapAspectRatio;
	
	private static final float toppos = PageSize.A4.getHeight() - topPlaceSize - pageMargin;
	private static final float rightpos = PageSize.A4.getWidth() - pageMargin;
	
	/**
	 * Creates a .pdf file containing all combinations of maps and layers stored in "maps" and "layernames"
	 * @param pdfFileName file name of a file to create
	 * @throws DocumentException
	 * @throws IOException
	 * @throws WriterException
	 */
	public static void createPdf(File pdfFile) throws DocumentException, IOException, WriterException {
		
		// QR code text
		Timestamp creationTime = new Timestamp((new java.util.Date()).getTime());
		
		// PDF
		FileOutputStream outputStream = new FileOutputStream(pdfFile);
		Document document = new Document(PageSize.A4, pageMargin,pageMargin,pageMargin,pageMargin);
		PdfWriter writer = PdfWriter.getInstance(document, outputStream);
		// add custom text on footer and header
		writer.setPageEvent(new AddPageTemplate());
		document.open();
//		document.addAuthor("CreativeDataMining course image processor");
//		document.addCreationDate();
//		document.addKeywords("Chair iA, ETHZ, Creative Data Mining");
//		document.addSubject("Create and process image clustering sheets");
//		document.addTitle("Creative Data Mining course handout");
		switch (groupPages) {
		case LAYERNAMES:
			for (String layer : layernames)
				for (Map.Entry<String, String> map : maps.entrySet()) {
				// start new page
				document.newPage();
				writeContent(writer, document, layer, map.getKey(), map.getValue(), creationTime);
				
			}
			break;
		case MAPNAMES:
			for (Map.Entry<String, String> map : maps.entrySet())
				for (String layer : layernames) {
				// start new page
				document.newPage();
				writeContent(writer, document, layer, map.getKey(), map.getValue(), creationTime);
				
			}
			break;
		default:
			break;
		}
		

	    document.close();
	    outputStream.flush();
	    outputStream.close();
	    
	}
	
	private static void writeContent(PdfWriter writer, Document document, String layername, String mapname, String imgFilePath, Timestamp creationTime) throws WriterException, IOException, DocumentException{
		// map and layer name
		placeChunck(mapname, 22, pageMargin, toppos, writer);
		placeChunck(layername, 18, pageMargin, toppos - 20, writer);
		
		// get barcode and write it into the pdf
		String qrtext = mapname + "|" + layername + "|" + creationTime;
		// QR code generation
	    BufferedImage barcodeImage = MatrixToImageWriter.toBufferedImage(
	    		new QRCodeWriter()
	    		.encode(
	    				qrtext
	    	    		, com.google.zxing.BarcodeFormat.QR_CODE, 600, 600)
	    		);
		Image bcimg = Image.getInstance(writer, barcodeImage, 1);
		bcimg.setDpi(300, 300);
		bcimg.scaleAbsolute(144, 144);
		bcimg.setAbsolutePosition(rightpos - 128, toppos - 105);
		document.add(bcimg);
		
		// add image
		Image img;
		if (imgFilePath.toLowerCase().endsWith(".pdf")) {
			
			PDDocument imgDoc;
			try {
				File f = new File(imgFilePath);
				if(f.exists() && !f.isDirectory())
					imgDoc = PDDocument.load(imgFilePath);
				else
					throw new FileNotFoundException("image " + imgFilePath + " is not found.");
			} catch (IOException e) {
				throw new IOException("Could not load pdf file.", e);
			}
			
			List<PDPage> pages;
			try {
				pages = (List<PDPage>)imgDoc.getDocumentCatalog().getAllPages();
			} catch (ClassCastException e)
			{
				imgDoc.close();
				throw e;
			}

	        int pagesSize = pages.size();
	        if (pagesSize < 1){
	        	imgDoc.close();
	        	throw new IOException("Empty PDF: " + imgFilePath);
	        	}
	        PDPage page = pages.get(0);
			try {
				BufferedImage image = page.convertToImage(BufferedImage.TYPE_4BYTE_ABGR, 100);
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				ImageIO.write(image, "png", baos);
				img = Image.getInstance(baos.toByteArray());
			} catch (IOException e) {
				imgDoc.close();
				throw new IOException("Could not read first page of pdf: " + e.getMessage(), e);
			}
			imgDoc.close();
		}
		else img = Image.getInstance(imgFilePath);
		
		
		
		
		
		img.scaleToFit(mapWidth, mapHeight);
		float a = img.getWidth() / img.getHeight();
		
		img.setAbsolutePosition(pageMargin + (a < mapAspectRatio ? (mapWidth - a*mapHeight)/2 : 0 ) + mapMargin,
				pageMargin + bottomPlaceSize + (a > mapAspectRatio ? (mapHeight - mapWidth/a)/2 : 0 ) + mapMargin);
		document.add(img);
	}
	
	
	private static void placeChunck(String text, int size, float x, float y, PdfWriter writer) {
		PdfContentByte canvas = writer.getDirectContent();
		BaseFont font;
		try {
			font = BaseFont.createFont(BaseFont.HELVETICA,
					BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
		} catch (DocumentException | IOException e) {
			log.error("Could not create font: " + e.getMessage());
			return;
		}
		canvas.saveState();
		canvas.beginText();
		canvas.moveText(x, y);
		canvas.setFontAndSize(font, size);
		canvas.showText(text);
		canvas.endText();
		canvas.restoreState();
	}
	
	protected static class AddPageTemplate extends PdfPageEventHelper {

		private Paragraph leftTopPar, leftBottomPar, rightTopPar, rightBottomPar;
		
		public AddPageTemplate() {
	    	// add custom text on footer and header
			leftTopPar = new Paragraph(leftTop);
			leftTopPar.setLeading(0, 1.2f);
			leftTopPar.getFont().setSize(10);
			leftTopPar.setAlignment(Element.ALIGN_LEFT);
			leftBottomPar = new Paragraph(leftBottom);
			leftBottomPar.setLeading(0, 1.2f);
			leftBottomPar.getFont().setSize(10);
			leftBottomPar.setAlignment(Element.ALIGN_LEFT);
			rightTopPar = new Paragraph(rightTop);
			rightTopPar.setLeading(0, 1.2f);
			rightTopPar.getFont().setSize(10);
			rightTopPar.setAlignment(Element.ALIGN_RIGHT);
			rightBottomPar = new Paragraph(rightBottom);
			rightBottomPar.setLeading(0, 1.2f);
			rightBottomPar.getFont().setSize(10);
			rightBottomPar.setAlignment(Element.ALIGN_RIGHT);
			
		}
		
		

		
		@Override
		public void onEndPage(PdfWriter writer, Document document) {
			
			ColumnText ctlt = new ColumnText(writer.getDirectContent());
			ctlt.setLeading(0, 1.2f);
			ctlt.addText(leftTopPar);
			ctlt.setSimpleColumn(
					pageMargin,
					toppos,
					document.getPageSize().getWidth()/2,
					toppos + topPlaceSize);
			try {
				ctlt.go();
			} catch (DocumentException e) {
				log.warn("Could not add column text to a page: " + e.getMessage());
			}
			
			ColumnText ctlb = new ColumnText(writer.getDirectContent());
			ctlb.setLeading(0, 1.2f);
			ctlb.addText(leftBottomPar);
			ctlb.setSimpleColumn(
					pageMargin,
					pageMargin,
					document.getPageSize().getWidth()/2,
					pageMargin + bottomPlaceSize);
			try {
				ctlb.go(true);
			} catch (DocumentException e) {
				log.warn("Could not add column text to a page: " + e.getMessage());
			}
			float dy = pageMargin + bottomPlaceSize - ctlb.getYLine();
			ctlb.addText(leftBottomPar);
			ctlb.setSimpleColumn(
					pageMargin,
					pageMargin,
					document.getPageSize().getWidth()/2,
					pageMargin + bottomPlaceSize);
			ctlb.setYLine(pageMargin + dy);
			try {
				ctlb.go();
			} catch (DocumentException e) {
				log.warn("Could not add column text to a page: " + e.getMessage());
			}
			
			ColumnText ctrt = new ColumnText(writer.getDirectContent());
			ctrt.setLeading(0, 1.2f);
			ctrt.addText(rightTopPar);
			ctrt.setSimpleColumn(
					document.getPageSize().getWidth()/2,
					toppos,
					rightpos,
					toppos + topPlaceSize);
			ctrt.setAlignment(Element.ALIGN_RIGHT);
			try {
				ctrt.go();
			} catch (DocumentException e) {
				log.warn("Could not add column text to a page: " + e.getMessage());
			}
			
			ColumnText ctrb = new ColumnText(writer.getDirectContent());
			ctrb.setLeading(0, 1.2f);
			ctrb.addText(rightBottomPar);
			ctrb.setSimpleColumn(
					document.getPageSize().getWidth()/2,
					pageMargin,
					rightpos,
					pageMargin + bottomPlaceSize);
			ctrb.setAlignment(Element.ALIGN_RIGHT);
			try {
				ctrb.go(true);
			} catch (DocumentException e) {
				log.warn("Could not add column text to a page: " + e.getMessage());
			}
			dy = pageMargin + bottomPlaceSize - ctrb.getYLine();
			ctrb.addText(rightBottomPar);
			ctrb.setSimpleColumn(
					document.getPageSize().getWidth()/2,
					pageMargin,
					rightpos,
					pageMargin + bottomPlaceSize);
			ctrb.setAlignment(Element.ALIGN_RIGHT);
			ctrb.setYLine(pageMargin + dy);
			try {
				ctrb.go();
			} catch (DocumentException e) {
				log.warn("Could not add column text to a page: " + e.getMessage());
			}
			
			
			
			
			// add a box to the image
			PdfContentByte cb = writer.getDirectContent();
			cb.saveState();
			cb.setColorStroke(BaseColor.BLACK);
			cb.setLineWidth(2);
			cb.rectangle(pageMargin,pageMargin + bottomPlaceSize,mapWidth+mapMargin*2,mapHeight+mapMargin*2);
			cb.stroke();
			cb.restoreState();
	    }
	}

}
