package ch.ethz.arch.ia.cdm.parse;

import static org.bytedeco.javacpp.helper.opencv_core.CV_RGB;
import static org.bytedeco.javacpp.opencv_core.CV_32FC1;
import static org.bytedeco.javacpp.opencv_core.CV_64FC2;
import static org.bytedeco.javacpp.opencv_core.CV_AA;
import static org.bytedeco.javacpp.opencv_core.IPL_DEPTH_8U;
import static org.bytedeco.javacpp.opencv_core.cvCreateMat;
import static org.bytedeco.javacpp.opencv_core.cvGet1D;
import static org.bytedeco.javacpp.opencv_core.cvGetSeqElem;
import static org.bytedeco.javacpp.opencv_core.cvLine;
import static org.bytedeco.javacpp.opencv_core.cvPerspectiveTransform;
import static org.bytedeco.javacpp.opencv_core.cvReleaseData;
import static org.bytedeco.javacpp.opencv_core.cvReleaseMat;
import static org.bytedeco.javacpp.opencv_core.cvSet1D;
import static org.bytedeco.javacpp.opencv_highgui.cvSaveImage;
import static org.bytedeco.javacpp.opencv_imgproc.COLOR_GRAY2RGB;
import static org.bytedeco.javacpp.opencv_imgproc.CV_GAUSSIAN;
import static org.bytedeco.javacpp.opencv_imgproc.CV_HOUGH_PROBABILISTIC;
import static org.bytedeco.javacpp.opencv_imgproc.WARP_INVERSE_MAP;
import static org.bytedeco.javacpp.opencv_imgproc.cvCvtColor;
import static org.bytedeco.javacpp.opencv_imgproc.cvGetPerspectiveTransform;
import static org.bytedeco.javacpp.opencv_imgproc.cvHoughLines2;
import static org.bytedeco.javacpp.opencv_imgproc.cvSmooth;
import static org.bytedeco.javacpp.opencv_imgproc.cvWarpPerspective;

import java.util.Comparator;

import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.opencv_core.CvMat;
import org.bytedeco.javacpp.opencv_core.CvMemStorage;
import org.bytedeco.javacpp.opencv_core.CvPoint;
import org.bytedeco.javacpp.opencv_core.CvScalar;
import org.bytedeco.javacpp.opencv_core.CvSeq;
import org.bytedeco.javacpp.opencv_core.IplImage;
import org.bytedeco.javacpp.helper.opencv_core.CvArr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



public class HoughHelper {

	private static final Logger log = LoggerFactory.getLogger(HoughHelper.class);
	public static final boolean DEBUG = false;
	
	
	public static CvArr pointsByHough(IplImage image, float[] from, float[] to, int width, int height){
		CvMat QRtransform = cvCreateMat(3,3,CV_32FC1);
		cvGetPerspectiveTransform(to, from, QRtransform);
		
		IplImage window = IplImage.create(width, height, IPL_DEPTH_8U, 1);
		cvWarpPerspective(image, window, QRtransform, WARP_INVERSE_MAP, CV_RGB(255,255,255)); //WARP_INVERSE_MAP

		IplImage colored = null;
		if(DEBUG) {
			colored = IplImage.create(width, height, IPL_DEPTH_8U, 3);
			cvCvtColor(window, colored, COLOR_GRAY2RGB);
			cvSaveImage(to[0] + "_" + to[1] + "_" + to[2] + "_" + to[3] + "_bin.png", window);
		}
		
		CvArr r0 =  HoughHelper.pointsByHough(window, colored); // 
		if(r0 == null){
			cvLine(image, new int[]{(int)from[0],(int)from[1]}, new int[]{(int)from[2],(int)from[3]}, CV_RGB(0, 0, 255));
			cvLine(image, new int[]{(int)from[2],(int)from[3]}, new int[]{(int)from[4],(int)from[5]}, CV_RGB(0, 0, 255));
			cvLine(image, new int[]{(int)from[4],(int)from[5]}, new int[]{(int)from[6],(int)from[7]}, CV_RGB(0, 0, 255));
			cvLine(image, new int[]{(int)from[6],(int)from[7]}, new int[]{(int)from[0],(int)from[1]}, CV_RGB(0, 0, 255));
			cvSaveImage("failedPointsByHough.jpg", image);
			return null;
		}
		
		CvArr r1 = cvCreateMat(2, 1, CV_64FC2);
		cvPerspectiveTransform(r0, r1, QRtransform);
		
		if(DEBUG) {
			cvSaveImage(to[0] + "_" + to[1] + "_" + to[2] + "_" + to[3] + "_hough.png", colored);
			colored.release();
		}
		
		window.release();
		cvReleaseMat(QRtransform);
		cvReleaseData(r0);
		return r1;
	}
	
	public static CvArr pointsByHough(IplImage image, IplImage colored){ //, IplImage colored
		modifier = 0;
		precisionm = 2;
		oscilating = false;
		return pointsByHough(image, colored, 0); //, colored
	}

	private static final int maxLines = 50;
	private static final int minLines = 10;
	private static double modifier = 0;
	private static double precisionm = 2;
	private static boolean oscilating = false;
	private static CvArr pointsByHough(IplImage image, IplImage colored, int attempt){ //, IplImage colored
		CvMemStorage hstorage = CvMemStorage.create();
		//CvSeq houghlines = cvHoughLines2(image, hstorage, CV_HOUGH_PROBABILISTIC, 1, Math.PI / 180, 400-50*attempt, 1100 - 100*attempt, 5 + 10*attempt);
		CvSeq houghlines = cvHoughLines2(image, hstorage, CV_HOUGH_PROBABILISTIC, 1, Math.PI / 180,
					(int)Math.round(Math.max(300-50*modifier, 1)),
					Math.max(1200 - 100*modifier, 1),
					(10 + 10*modifier)); //Math.PI / 180
		if (houghlines.total() <= Math.max((minLines-attempt/2),1))
		{
			hstorage.release();
			if(modifier < 0 || oscilating){
				precisionm /= 2;
				oscilating = true;
			}
			modifier += precisionm;
			//System.out.println(oscilating + " " + houghlines.total() + " " + precisionm + " " + modifier + " " + attempt + " " + Math.round(Math.max(300-50*modifier, 1)) + " " + (10 + 10*modifier));
			if(attempt > 20)
				return null;

			cvSmooth(image, image, CV_GAUSSIAN, 17, 17, 2.0, 2.0);
			return pointsByHough(image,colored, attempt+1); //,colored
		} else if (houghlines.total() >= maxLines)
		{
			hstorage.release();
			if(modifier > 0 || oscilating){
				precisionm /= 2;
				oscilating = true;
			}
			modifier -= precisionm;
			//System.out.println(oscilating + " " + precisionm + " " + modifier + " " + attempt + " " + Math.round(Math.max(300-50*modifier, 1)) + " " + (10 + 10*modifier));
			if(attempt < 10){
				cvSmooth(image, image, CV_GAUSSIAN, 17, 17, 2.0, 2.0);
				return pointsByHough(image,colored, attempt+1); //,colored
			}
		}
		double m;
		double l;
		double x1 = 0;
		double x2 = image.width();
		int ymax = image.height();
		double[] y1s = new double[houghlines.total()];
		double[] y2s = new double[houghlines.total()];
		double[] ymeans = new double[houghlines.total()];
		double[] xmeans = new double[houghlines.total()];
		double[] lengths = new double[houghlines.total()];
		double[] slopes = new double[houghlines.total()];
		Integer[] ix = new Integer[houghlines.total()];
		for (int i = 0; i < houghlines.total(); i++) {
			Pointer line = cvGetSeqElem(houghlines, i);
            CvPoint pt1  = new CvPoint(line).position(0);
            CvPoint pt2  = new CvPoint(line).position(1);
            l = ParserHelpers.distance(pt1, pt2);
            m = (double)(pt1.y() - pt2.y()) / (double)(pt1.x() - pt2.x());
            y1s[i] = pt1.y() - pt1.x()*m;
            y2s[i] = y1s[i] + x2*m;
            xmeans[i] = (pt1.x() + pt2.x()) / 2.0;
            ymeans[i] = (y1s[i] + y2s[i]) / 2;
            lengths[i] = l;
            slopes[i] = m;
            ix[i] = i;
            if(DEBUG)
            	cvLine(colored, pt1, pt2, CV_RGB(0, 255, 0), 3, CV_AA, 0); ////////////////////////////////////
        }
		
		log.debug("Found several lines on attempt " + attempt + ". Number of lines: " + houghlines.total());
		
		
		java.util.Arrays.sort(ix, new ArrayIndexComparator(ymeans));
        if(DEBUG)
			System.out.println("means, slopes, lengths, y0, coords;");
		double mscore = 0, cs = 0;
		int imax = 0;
		for(int i = 0; i < ix.length; i++){
			cs = 0;
			for(int j = 0; j < ymeans.length; j++){
				cs += (lengths[ix[i]] + xmeans[ix[i]]) * Math.exp(-Math.abs(ymeans[ix[j]]-ymeans[ix[i]]) - Math.abs(slopes[ix[j]]-slopes[ix[i]])*x2/10);
			}
			if(cs > mscore){
				mscore = cs;
				imax = ix[i];
			}
			if(DEBUG)
				System.out.println(ymeans[ix[i]] + ", " + slopes[ix[i]] + "," + lengths[ix[i]] + ", " + y1s[ix[i]] + ", " + cs + ";");
		}
		
		double y1 = 0, y2 = 0;
		double xmin = xmeans[imax], xmax = xmeans[imax];
		int count = 0;
		for(int i = 0; i < ix.length; i++)
			if(Math.abs(ymeans[imax]-ymeans[ix[i]]) <= ymax/100.0 && Math.abs(slopes[imax]-slopes[ix[i]]) <= 0.02){
				Pointer line = cvGetSeqElem(houghlines, ix[i]);
		        CvPoint pt1  = new CvPoint(line).position(0);
		        CvPoint pt2  = new CvPoint(line).position(1);
		        if(pt1.x() < pt2.x() && pt1.x() < xmin){
		        	xmin = pt1.x();
		        	y1 = pt1.y();
		        } else if(pt2.x() < pt1.x() && pt2.x() < xmin){
		        	xmin = pt2.x();
		        	y1 = pt2.y();
		        }
		        if(pt1.x() > pt2.x() && pt1.x() > xmax){
		        	xmax = pt1.x();
		        	y2 = pt1.y();
		        } else if(pt2.x() > pt1.x() && pt2.x() > xmax){
		        	xmax = pt2.x();
		        	y2 = pt2.y();
		        }
		        count++;
			}
		x1 = xmin;
		x2 = xmax;
		log.debug(count + " lines were used in averaging");
		
		CvArr hpoints = cvCreateMat(2, 1, CV_64FC2);
		CvScalar p = new CvScalar(2);
		p.put(x1, y1);
		cvSet1D(hpoints, 0, p);
		p.put(x2, y2);
		cvSet1D(hpoints, 1, p);
		if(DEBUG)
			drawLineFromArr(colored, hpoints);
		hstorage.release();
		return hpoints;
	}
	
	
	public static void drawLineFromArr(CvArr img, CvArr arr){
		CvPoint pt1 = new CvPoint();
		CvPoint pt2 = new CvPoint();
		
		CvScalar p = new CvScalar(2);
		p = cvGet1D(arr, 0);
		pt1.x((int)Math.round(p.get(0)));
		pt1.y((int)Math.round(p.get(1)));
		p = cvGet1D(arr, 1);
		pt2.x((int)Math.round(p.get(0)));
		pt2.y((int)Math.round(p.get(1)));
		
//		System.out.println(pt1);
//		System.out.println(pt2);
		
//		cvLine(rImage, cvPoint((int)Math.round(hlines[0]), (int)Math.round(hlines[1]))
//				, cvPoint((int)Math.round(hlines[2]), (int)Math.round(hlines[3]))
//				, CV_RGB(255, 0, 0), 3, CV_AA, 0);
		

		cvLine(img, pt1,pt2, CV_RGB(255, 0, 0), 2, CV_AA, 0);
	}
	
	
	public static class ArrayIndexComparator
	implements Comparator<Integer>
{
    private final double[] array;

    public ArrayIndexComparator(double[] array)
    {
        this.array = array;
    }

    public Integer[] createIndexArray()
    {
    	Integer[] indexes = new Integer[array.length];
        for (int i = 0; i < array.length; i++)
        {
            indexes[i] = i; // Autoboxing
        }
        return indexes;
    }

    @Override
    public int compare(Integer index1, Integer index2)
    {
         // Autounbox from Integer to int to use as array indexes
        return array[index1] < array[index2] ? (-1) : 
        	(array[index1] == array[index2] ? 0 : 1);
    }
}
}
