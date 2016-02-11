/**
 * 
 */
package ch.ethz.arch.ia.cdm.parse;

import org.bytedeco.javacpp.opencv_core.*;

import static org.bytedeco.javacpp.helper.opencv_core.CV_RGB;
import static org.bytedeco.javacpp.opencv_core.CV_32FC1;
import static org.bytedeco.javacpp.opencv_core.IPL_DEPTH_8U;
import static org.bytedeco.javacpp.opencv_core.cvCopy;
import static org.bytedeco.javacpp.opencv_core.cvCreateMat;
import static org.bytedeco.javacpp.opencv_core.cvGetSize;
import static org.bytedeco.javacpp.opencv_core.cvInRangeS;
import static org.bytedeco.javacpp.opencv_core.cvMixChannels;
import static org.bytedeco.javacpp.opencv_core.cvReleaseData;
import static org.bytedeco.javacpp.opencv_core.cvSize;
import static org.bytedeco.javacpp.opencv_core.*;
import static org.bytedeco.javacpp.opencv_imgproc.*;
import static org.bytedeco.javacpp.opencv_imgcodecs.*;
import static org.bytedeco.javacpp.opencv_highgui.*;
import static org.bytedeco.javacpp.opencv_imgproc.COLOR_RGB2GRAY;
import static org.bytedeco.javacpp.opencv_imgproc.cvCvtColor;
import static org.bytedeco.javacpp.opencv_imgproc.cvGetPerspectiveTransform;
import static org.bytedeco.javacpp.opencv_imgproc.cvWarpPerspective;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.commons.io.FilenameUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.bytedeco.javacpp.opencv_core.CvMat;
import org.bytedeco.javacpp.opencv_core.CvPoint;
import org.bytedeco.javacpp.opencv_core.CvSize;
import org.bytedeco.javacpp.opencv_core.IplImage;
import org.bytedeco.javacpp.helper.opencv_core.CvArr;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.ethz.arch.ia.cdm.MainApp;

import com.google.zxing.ChecksumException;
import com.google.zxing.FormatException;
import com.google.zxing.NotFoundException;
import com.itextpdf.text.pdf.PdfReader;

/**
 * @author achirkin
 *
 */
public class PdfParser {
	public static String imgExtension = ".png";
	public static int imgSize = 2000;
	public static int imgMargin = 10;
	public static boolean correctColors = false;
	
	public static String outputFolder = "parseout";
	
	private static String dirPath;
	
	public static abstract class ParsingEventListener {
		public abstract void parsedPage(boolean success);
		/**
		 * 
		 * @param progress in [0,1];
		 */
		public abstract void parsingProgress(double progress);
	}
	private static List<ParsingEventListener> parsingListeners = new LinkedList<ParsingEventListener>();
	/**
	 * Adds an event Listener which says something each time page parsed
	 * @param l
	 */
	public static void AddParsingEventListener(ParsingEventListener l){
		parsingListeners.add(l);
	}
	/**
	 * Removes an event listener
	 * @param l
	 */
	public static void RemoveParsingEventListener(ParsingEventListener l){
		parsingListeners.remove(l);
	}
	
	/**
	 * Fires parsing event on all listeners
	 * @param success
	 */
	private static void parsedPage(boolean success){
		parsingListeners.forEach(l -> l.parsedPage(success));
	}
	
	private static void parsingProgress(double progress){
		parsingListeners.forEach(l -> l.parsingProgress(progress));
	}
	
	private static final Logger log = LoggerFactory.getLogger(PdfParser.class);
	public static final boolean DEBUG = false;
	
	/**
	 * Count number of pages if it is a pdf. Otherwise return 1
	 * @param file
	 * @return
	 */
	public static int countPages(File file){
		if (!file.getName().toLowerCase().endsWith(".pdf"))
			return 1;
		else {
			try {
				if(file.exists() && !file.isDirectory()){
					PdfReader r = new PdfReader(file.getPath());
					return r.getNumberOfPages();
				} else
					return 0;
			} catch (IOException e) {
				log.error("Could not load pdf file.", e);
				return 0;
			}
		}
	}
	
	public static void createOutDirIfNotExists(){
		File d = new File(MainApp.configMan.getFullPath(outputFolder));
		if(d.exists() && d.isFile())
			d.delete();
		if(!d.exists());
			d.mkdir();
		dirPath = d.getPath();
	}
	
	/**
	 * 
	 * @param filename - pdf or image file
	 * @throws IOException 
	 * @throws Exception 
	 */
	@SuppressWarnings("unchecked")
	public static void parseDocument(String filename) throws IOException
	{
		createOutDirIfNotExists();
		
		if (filename.toLowerCase().endsWith(".pdf")) {
			
			PDDocument document;
			try {
				File f = new File(filename);
				if(f.exists() && !f.isDirectory())
					document = PDDocument.load(filename);
				else
					throw new FileNotFoundException("file " + filename + " is not found.");
			} catch (IOException e) {
				throw new IOException("Could not load pdf file.", e);
			} finally {
				parsedPage(false);
			}
			
			List<PDPage> pages;
			try {
				pages = (List<PDPage>)document.getDocumentCatalog().getAllPages();
			} catch (ClassCastException e)
			{
				throw e;
			}

	        int pagesSize = pages.size();
	        CvSize size = cvSize(1, 1);
	        for (int i = 0; i < pagesSize; i++)
	        {
	            PDPage page = pages.get(i);
	            BufferedImage image;
				try {
					image = page.convertToImage(BufferedImage.TYPE_4BYTE_ABGR, 300);
				} catch (IOException e) {
					log.warn("Could not read page " + i + ": " + e.getMessage());
					parsedPage(false);
					continue;
				}
	            size.width(image.getWidth());
	            size.height(image.getHeight());
	            // parse each page in a .pdf
//	            IplImage oimg = IplImage.createFrom(image);
	            OpenCVFrameConverter.ToIplImage converter1 = new OpenCVFrameConverter.ToIplImage();
	            Java2DFrameConverter converter2 = new Java2DFrameConverter();
	            
	            IplImage oimg = converter1.convert(converter2.convert(image));
	    		IplImage im = IplImage.create(size, 8, 3);
	    		
	    		int from_to[] = { 1,0, 2,1, 3,2};
	    		
//	    		ImageIO.write(image, "png", new File("imgFromPdf.png"));
	    		cvMixChannels(oimg, 1, im, 1, from_to, 3);
	    		oimg.release();
	            try {
					parsePicture(im);
					parsedPage(true);
				} catch (IOException e) {
					parsedPage(false);
					log.warn("Could not process image on page " + i + ": " + e.getMessage());
				}
	            image.flush();
	    		im.release();
	            //String fileName = "picture" + (i + 1) + ".png";
	            //ImageIOUtil.writeImage(image, fileName, 300);
	            System.gc();
	            System.runFinalization();
	        }
		} else
		{
//			BufferedImage rimg = ImageIO.read(new File(filename));
//			BufferedImage image = new BufferedImage(rimg.getWidth(), rimg.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
//			image.getGraphics().drawImage(rimg, 0, 0, null);
		    // parse
			try {
				parsePicture(cvLoadImage(filename, CV_LOAD_IMAGE_COLOR));
				parsedPage(true);
			} catch (IOException e) {
				parsedPage(false);
				log.warn("Could not process image from file " + filename + ": " + e.getMessage());
			}
		}
	}
	

	public static void parsePicture(IplImage image) throws IOException{
		parsingProgress(0);
		//cvSaveImage("initinal.jpg", image);
		
		//image = ParserHelpers.toPortrait(image, true);
		CvSize size = cvGetSize(image);
		
		// greyscale image
		IplImage greyImg = IplImage.create(size, IPL_DEPTH_8U, 1);
		cvCvtColor(image, greyImg, COLOR_RGB2GRAY);
		IplImage greyImg2 = IplImage.create(size, IPL_DEPTH_8U, 1);
		cvCopy(greyImg, greyImg2);
		
		ColorCorrector.correctIntensity(image,greyImg);
		
		// binary Image
		IplImage binImg = IplImage.create(size, IPL_DEPTH_8U, 1);
		//apply thresholding
		int cut_level = 100;
		int change = 20;
		int attempt = 0;

		// QR Code labels
        CvPoint QRcorners = null;
        boolean success = false;
        IplImage binImg2 = null;
        while (!success && cut_level < 255 && cut_level > 30)
		try{
			cvInRangeS(greyImg2, CV_RGB(0, 0, 0), CV_RGB(cut_level, cut_level, cut_level), binImg);
	        
	        if(DEBUG) {
				cvSaveImage("bin.png", binImg);
				cvSaveImage("grey.png", greyImg2);
			}
	        parsingProgress(0.05);
	        {

				binImg2 = IplImage.create(size, IPL_DEPTH_8U, 1);
				cvCopy(binImg, binImg2);
				QRcorners = ParserHelpers.findQRpoints(binImg2);
				binImg2.release();
	        }
	        success = true;
		}catch (Exception ex) {
			cvSmooth(greyImg, greyImg2, CV_GAUSSIAN, 13 + attempt*2, 13 + attempt*2, 1.0 + attempt/2, 1.0 + attempt/2);
			attempt++;
			String binname = "failedCorners-" + cut_level + "_" + (int)(Math.random()*10000000) + ".png";
			cut_level += cut_level <= 100 ? change : (-change);
			change += 10 + (cut_level < 100 ? 10 : 0);
			if(DEBUG)
				cvSaveImage(binname, binImg);
			log.debug("Could not find QR code corners; cutoff level is " + cut_level + ". If in the debug mode, binary image is saved as " + binname + ". " + ex.getMessage());
			if(cut_level >= 255 || cut_level <= 30){
				log.error("We tried hard, sorry. " + ex.getStackTrace());
				parsingProgress(0.0);
		        greyImg2.release();
		        greyImg.release();
		        binImg2.release();
		        binImg.release();
				return;
			}
		}
        greyImg2.release();
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        

        
        CvMat QRtransform1 = cvCreateMat(3,3,CV_32FC1);
		cvGetPerspectiveTransform(new float[]{
				QRcorners.position(0).x(), QRcorners.position(0).y(),
				QRcorners.position(1).x(), QRcorners.position(1).y(),
				QRcorners.position(2).x(), QRcorners.position(2).y(),
				QRcorners.position(3).x(), QRcorners.position(3).y()
		}, new float[]{
				200,200,
				1000,200,
				200,1000,
				1000,1000
		}
				, QRtransform1);
		// Transformation
		CvMat QRtransform = cvCreateMat(3,3,CV_32FC1);
		cvGetPerspectiveTransform( new float[]{
						200,200,
						1000,200,
						200,1000,
						1000,1000
		}, new float[]{
				QRcorners.position(0).x(), QRcorners.position(0).y(),
				QRcorners.position(1).x(), QRcorners.position(1).y(),
				QRcorners.position(2).x(), QRcorners.position(2).y(),
				QRcorners.position(3).x(), QRcorners.position(3).y()
		},QRtransform);
		IplImage Image2 = IplImage.create(1200, 1200, greyImg.depth(), 1);
		cvWarpPerspective(greyImg, Image2, QRtransform1, 0, CV_RGB(255,255,255)); //WARP_INVERSE_MAP

		greyImg2 = IplImage.create(Image2.cvSize(), Image2.depth(), 1);
		IplImage binImg1 = IplImage.create(Image2.cvSize(), IPL_DEPTH_8U, 1);
		
        QRcorners = null;
        success = false;
        binImg2 = null;
        cut_level = 100;
		change = 20;
		attempt = 0;
		cvCopy(Image2, greyImg2);
        while (!success && cut_level < 255 && cut_level > 30)
		try{
			cvInRangeS(greyImg2, CV_RGB(0, 0, 0), CV_RGB(cut_level, cut_level, cut_level), binImg1);
	        
	        if(DEBUG) {
				cvSaveImage("bin2.png", binImg);
				cvSaveImage("grey2.png", greyImg2);
			}
	        parsingProgress(0.05);
	        {

				binImg2 = IplImage.create(Image2.cvSize(), IPL_DEPTH_8U, 1);
				cvCopy(binImg1, binImg2);
				QRcorners = ParserHelpers.findQRpoints(binImg2);
				binImg2.release();
	        }
	        success = true;
		}catch (Exception ex) {
			cvSmooth(Image2, greyImg2, CV_GAUSSIAN, 13 + attempt*2, 13 + attempt*2, 1.0 + attempt/2, 1.0 + attempt/2);
			attempt++;
			String binname = "failedCorners-" + cut_level + "_" + (int)(Math.random()*10000000) + ".png";
			cut_level += cut_level <= 100 ? change : (-change);
			change += 10 + (cut_level < 100 ? 10 : 0);
			if(DEBUG)
				cvSaveImage(binname, binImg1);
			log.debug("Could not find QR code corners; cutoff level is " + cut_level + ". If in the debug mode, binary image is saved as " + binname + ". " + ex.getMessage());
			if(cut_level >= 255 || cut_level <= 30){
				log.error("We tried hard, sorry. Attempt: " + attempt + ". " + ex.getMessage());
				cvSaveImage("triedHardSorry.jpg", greyImg2);
				parsingProgress(0.0);
		        greyImg2.release();
		        Image2.release();
		        binImg2.release();
		        binImg.release();
				return;
			}
		}
        greyImg2.release();
        
        
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        cvInRangeS(greyImg, CV_RGB(0, 0, 0), CV_RGB(50+cut_level/2, 50+cut_level/2, 50+cut_level/2), binImg);

        
        log.info("Found QR code corners; binary cutoff level is " + cut_level);
        parsingProgress(0.2);
//		cvDrawCircle(binImg, QRcorners.position(0), 13, CV_RGB(50,50,50), -1, 8, 0);
//		cvDrawCircle(binImg, QRcorners.position(1), 10, CV_RGB(100,100,100), -1, 8, 0);
//		cvDrawCircle(binImg, QRcorners.position(2), 8, CV_RGB(150,150,150), -1, 8, 0);
//		cvDrawCircle(binImg, QRcorners.position(3), 5, CV_RGB(255,255,255), -1, 8, 0);
		
		
		
		CvMat QRCornersArr = cvCreateMat(4, 1, CV_64FC2);
		QRCornersArr.put(0,0,0,QRcorners.position(0).x());
		QRCornersArr.put(0,0,1,QRcorners.position(0).y());
		QRCornersArr.put(1,0,0,QRcorners.position(1).x());
		QRCornersArr.put(1,0,1,QRcorners.position(1).y());
		QRCornersArr.put(2,0,0,QRcorners.position(2).x());
		QRCornersArr.put(2,0,1,QRcorners.position(2).y());
		QRCornersArr.put(3,0,0,QRcorners.position(3).x());
		QRCornersArr.put(3,0,1,QRcorners.position(3).y());
		cvPerspectiveTransform(QRCornersArr, QRCornersArr, QRtransform);
		QRcorners.position(0).x((int)QRCornersArr.get(0,0,0));
		QRcorners.position(0).y((int)QRCornersArr.get(0,0,1));
		QRcorners.position(1).x((int)QRCornersArr.get(1,0,0));
		QRcorners.position(1).y((int)QRCornersArr.get(1,0,1));
		QRcorners.position(2).x((int)QRCornersArr.get(2,0,0));
		QRcorners.position(2).y((int)QRCornersArr.get(2,0,1));
		QRcorners.position(3).x((int)QRCornersArr.get(3,0,0));
		QRcorners.position(3).y((int)QRCornersArr.get(3,0,1));
		cvGetPerspectiveTransform(new float[]{
				QRcorners.position(0).x(), QRcorners.position(0).y(),
				QRcorners.position(1).x(), QRcorners.position(1).y(),
				QRcorners.position(2).x(), QRcorners.position(2).y(),
				QRcorners.position(3).x(), QRcorners.position(3).y()
		}, new float[]{
				200,200,
				1000,200,
				200,1000,
				1000,1000
		}, QRtransform);
		//cvMatMul(QRtransform, QRtransform1, QRtransform);
		//QRCornersArr.release();
		
		String text = "";
		{
			IplImage qrImage = IplImage.create(1200, 1200, greyImg.depth(), 1);
			cvWarpPerspective(greyImg, qrImage, QRtransform, 0, CV_RGB(255,255,255)); //WARP_INVERSE_MAP
	        cvInRangeS(qrImage, CV_RGB(cut_level, cut_level, cut_level), CV_RGB(255, 255, 255), qrImage);
			
	        OpenCVFrameConverter.ToIplImage converter1 = new OpenCVFrameConverter.ToIplImage();
            Java2DFrameConverter converter2 = new Java2DFrameConverter();
	        
			//BufferedImage bw = ImageIO.read(new ByteArrayInputStream(qrImage.asByteBuffer().array()));
			BufferedImage bw = converter2.convert(converter1.convert(qrImage));
			BufferedImage qrCodeImg = new BufferedImage(bw.getWidth(), bw.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
			qrCodeImg.getGraphics().drawImage(bw, 0, 0, null);
			try {
				text = ZXingHelper.readQRCode(qrCodeImg);
			} catch (NotFoundException | ChecksumException | FormatException e0) {
				log.info("Could not find QR code, try one more time - cropped.");
				try {
					qrCodeImg = qrCodeImg.getSubimage(150, 150, 900, 900);
					text = ZXingHelper.readQRCode(qrCodeImg);
				} catch (NotFoundException | ChecksumException | FormatException e1) {
					String fname = "original0_failedQRCode_" + (System.currentTimeMillis() % 100000000) + ".png";
					//ImageIO.write(qrCodeImg, "png", new File(fname));
					//cvSaveImage(fname, qrImage);
//					qrImage.release();
					log.info("Trying to blur image to get better QR code recognitions.");
//					throw new IOException("Could not find QR code: " + e.getMessage() + ". Failing image area is saved to a file " + fname + ".", e);
					qrImage = IplImage.create(1200, 1200, greyImg.depth(), 1);
					change = 20;
					attempt = 0;
					cut_level = 100;
					success = false;
			        while (!success && cut_level < 255 && cut_level > 30)
					{
					cvWarpPerspective(greyImg, qrImage, QRtransform, 0, CV_RGB(255,255,255)); //WARP_INVERSE_MAP
					//fname = "failedQRCode_" + (System.currentTimeMillis() % 100000000) + ".png";
					//cvSaveImage("original_"+fname, qrImage);
					cvSmooth(qrImage, qrImage, CV_GAUSSIAN, 11 + attempt*2, 11 + attempt*2, 1.0 + attempt, 1.0 + attempt);
					//cvSaveImage("blurred_"+fname, qrImage);
//					GaussianBlur(qrImage, qrImage, cvSize(17, 17), 5.0);
			        cvInRangeS(qrImage, CV_RGB(cut_level, cut_level, cut_level), CV_RGB(255, 255, 255), qrImage);
			        attempt++;
					cut_level += cut_level <= 100 ? change : (-change);
					change += 10 + (cut_level < 100 ? 10 : 0);
					
					bw = ImageIO.read(new ByteArrayInputStream(qrImage.asByteBuffer().array()));
					qrCodeImg = new BufferedImage(bw.getWidth(), bw.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
					qrCodeImg.getGraphics().drawImage(bw, 0, 0, null);
					
					try {
						text = ZXingHelper.readQRCode(qrCodeImg);
					} catch (NotFoundException | ChecksumException | FormatException e2) {
						//log.info("Could not find QR code, try one more time - blurred, binarized back, and cropped!");
						try {
							qrCodeImg = qrCodeImg.getSubimage(150, 150, 900, 900);
							text = ZXingHelper.readQRCode(qrCodeImg);
						} catch (NotFoundException | ChecksumException | FormatException e) {
							if(cut_level < 255 && cut_level > 30 && attempt < 10)
								continue;
							fname = "failedQRCode_" + (System.currentTimeMillis() % 100000000) + ".png";
							ImageIO.write(qrCodeImg, "png", new File(fname));
//							cvSaveImage(fname, qrImage);
							qrImage.release();
							throw new IOException("Could not find QR code: " + e.getMessage() + ". Failing image area is saved to a file " + fname + ".", e);
						}
					}
					success = true;
					}
				}
			} catch (Exception ex) {
				qrImage.release();
				return;
			}
			qrImage.release();
		}

        parsingProgress(0.4); //////////////////////////////////////////////////////////////
		
		int stri = text.indexOf('\u007c');
		String filename;
		if(stri < 0) {
			filename = (System.currentTimeMillis() % 100000000) + "_" + text + "_" + text + imgExtension;
		} else{
			String mapname = text.substring(0, stri);
			text = text.substring(stri+1);
			stri = text.indexOf('\u007c');
			String labelname = text.substring(0, stri);
			filename = (System.currentTimeMillis() % 100000000) + "_" + mapname + "_" + labelname + imgExtension;
		}
		
		
		
	
		// Hough lines
		float[] from = new float[]{
				QRcorners.position(0).x(), QRcorners.position(0).y(),
				QRcorners.position(1).x(), QRcorners.position(1).y(),
				QRcorners.position(2).x(), QRcorners.position(2).y(),
				QRcorners.position(3).x(), QRcorners.position(3).y()
		};
		
		float[] toLeft = new float[]{
				-800,2800,
				-800,3400,
				-200,2800,
				-200,3400
		};
		CvArr lLeft = HoughHelper.pointsByHough(binImg, from, toLeft, 2300, 1000);
		if(lLeft == null){
			log.error("Could not find HoughPoints");
			parsingProgress(0.0);
			return;
		}
		if(DEBUG)
		HoughHelper.drawLineFromArr(image, lLeft);
        parsingProgress(0.5); //////////////////////////////////////////////////////////////
		
		float[] toRight = new float[]{
				-800,0,
				-800,600,
				-200,0,
				-200,600
		};
		CvArr lRight = HoughHelper.pointsByHough(binImg, from, toRight, 2300, 1000);
		if(lRight == null){
			log.error("Could not find HoughPoints");
			parsingProgress(0.0);
			return;
		}
		if(DEBUG)
		HoughHelper.drawLineFromArr(image, lRight);
        parsingProgress(0.6); //////////////////////////////////////////////////////////////

		float[] toTop = new float[]{
				1800,-650,
				2400,-650,
				1800,0,
				2400,0
		};
		CvArr lTop = HoughHelper.pointsByHough(binImg, from, toTop, 2300, 500);
		if(lTop == null){
			log.error("Could not find HoughPoints");
			parsingProgress(0.0);
			return;
		}
		if(DEBUG)
		HoughHelper.drawLineFromArr(image, lTop);
        parsingProgress(0.7); //////////////////////////////////////////////////////////////

		float[] toBottom = new float[]{
				1800,-2800,
				2400,-2800,
				1800,-2200,
				2400,-2200
		};
		CvArr lBottom = HoughHelper.pointsByHough(binImg, from, toBottom, 2300, 1000);
		if(lBottom == null){
			log.error("Could not find HoughPoints");
			parsingProgress(0.0);
			return;
		}
		if(DEBUG)
		HoughHelper.drawLineFromArr(image, lBottom);
        parsingProgress(0.8); //////////////////////////////////////////////////////////////

		double[] lineLeft = ParserHelpers.lineEq(lLeft);
		double[] lineRight = ParserHelpers.lineEq(lRight);
		double[] lineTop = ParserHelpers.lineEq(lTop);
		double[] lineBottom = ParserHelpers.lineEq(lBottom);
		cvReleaseData(lLeft);
		cvReleaseData(lRight);
		cvReleaseData(lTop);
		cvReleaseData(lBottom);

		// final coordinates
		CvPoint lt = new CvPoint();
		CvPoint rt = new CvPoint();
		CvPoint lb = new CvPoint();
		CvPoint rb = new CvPoint();
		ParserHelpers.intersect(lineLeft, lineTop, lt);
		ParserHelpers.intersect(lineRight, lineTop, rt);
		ParserHelpers.intersect(lineLeft, lineBottom, lb);
		ParserHelpers.intersect(lineRight, lineBottom, rb);
		
		
		
		from = new float[]{
				lt.x(), lt.y(),
				rt.x(), rt.y(),
				lb.x(), lb.y(),
				rb.x(), rb.y()
		};
		
		cvGetPerspectiveTransform(from, new float[]{
				-imgMargin, -imgMargin,
				imgSize + imgMargin, -imgMargin,
				-imgMargin, imgSize + imgMargin,
				imgSize + imgMargin, imgSize + imgMargin
		}
				, QRtransform);
		IplImage rImage = IplImage.create(imgSize, imgSize, image.depth(), 3);
		cvWarpPerspective(image, rImage, QRtransform, 0, CV_RGB(0,0,0)); //WARP_INVERSE_MAP
		
        parsingProgress(0.9 + (correctColors ? 0.09 : 0)); //////////////////////////////////////////////////////////////

//		cvDrawCircle(image, lt, 13, CV_RGB(50,150,50), -1, 8, 0);
//		cvDrawCircle(image, rt, 13, CV_RGB(50,150,50), -1, 8, 0);
//		cvDrawCircle(image, lb, 13, CV_RGB(50,150,50), -1, 8, 0);
//		cvDrawCircle(image, rb, 13, CV_RGB(50,150,50), -1, 8, 0);
//		cvEqualizeHist(image, image);
		if(correctColors){
			IplImage rGrey = IplImage.create(imgSize, imgSize, rImage.depth(), 1);
			cvCvtColor(rImage, rGrey, COLOR_RGB2GRAY);
			ColorCorrector.enhanceIntensity(rImage, rGrey);
			rGrey.release();
		}
		cvSaveImage(FilenameUtils.concat(dirPath, filename), rImage);
		if(DEBUG)
			cvSaveImage(FilenameUtils.concat(dirPath, filename + "-borders.jpg"), image);
//		cvSaveImage("qrcode.png", qrImage);
		binImg.release();
		greyImg.release();
		rImage.release();
        parsingProgress(1); //////////////////////////////////////////////////////////////
		log.info("parsed " + filename);
	}
	
	
	

	
}
