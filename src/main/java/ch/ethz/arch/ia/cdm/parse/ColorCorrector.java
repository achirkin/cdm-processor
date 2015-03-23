/**
 * 
 */
package ch.ethz.arch.ia.cdm.parse;

import static org.bytedeco.javacpp.helper.opencv_core.CV_RGB;
import static org.bytedeco.javacpp.helper.opencv_imgproc.cvCreateHist;
import static org.bytedeco.javacpp.opencv_core.CV_HIST_ARRAY;
import static org.bytedeco.javacpp.opencv_core.CV_HIST_UNIFORM;
import static org.bytedeco.javacpp.opencv_core.cvAdd;
import static org.bytedeco.javacpp.opencv_core.cvAddS;
import static org.bytedeco.javacpp.opencv_core.cvConvertScale;
import static org.bytedeco.javacpp.opencv_core.cvGet1D;
import static org.bytedeco.javacpp.opencv_core.cvGetSize;
import static org.bytedeco.javacpp.opencv_core.cvInRangeS;
import static org.bytedeco.javacpp.opencv_core.cvMinMaxLoc;
import static org.bytedeco.javacpp.opencv_core.cvScalar;
import static org.bytedeco.javacpp.opencv_core.cvSub;
import static org.bytedeco.javacpp.opencv_core.cvSubS;
import static org.bytedeco.javacpp.opencv_imgproc.COLOR_GRAY2RGB;
import static org.bytedeco.javacpp.opencv_imgproc.CV_GAUSSIAN;
import static org.bytedeco.javacpp.opencv_imgproc.cvCalcHist;
import static org.bytedeco.javacpp.opencv_imgproc.cvCvtColor;
import static org.bytedeco.javacpp.opencv_imgproc.cvResize;
import static org.bytedeco.javacpp.opencv_imgproc.cvSmooth;

import org.bytedeco.javacpp.opencv_core.CvHistogram;
import org.bytedeco.javacpp.opencv_core.CvSize;
import org.bytedeco.javacpp.opencv_core.IplImage;
import org.bytedeco.javacpp.helper.opencv_core.CvArr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author achirkin
 *
 */
public class ColorCorrector {
	
	private static final Logger log = LoggerFactory.getLogger(ColorCorrector.class);
	
	public static void correctIntensity(IplImage img, IplImage grey) {
		double[] min_val = new double[1],
				 max_val = new double[1];
		cvMinMaxLoc(grey, min_val, max_val);
		
//		CvScalar a = cvScalar(min_val[0], min_val[0], min_val[0], min_val[0]);
//		CvScalar b = cvScalar(255.0/(max_val[0]-min_val[0]), 255.0/(max_val[0]-min_val[0]), 255.0/(max_val[0]-min_val[0]), 255.0/(max_val[0]-min_val[0]));
		double scale = 255.0/(max_val[0]-min_val[0]);
		double shift = -min_val[0]*scale;
		cvConvertScale(img, img, scale, shift);
		cvConvertScale(grey, grey, scale, shift);
//		cvSubS(img, a, img);
//		cvSubS(grey, a, grey);
//		cvScale(arg1, arg2, arg3, arg4);
//		cvMinMaxLoc(grey, min_val, max_val);
		
		cvMinMaxLoc(grey, min_val, max_val);
		
	}
	
	public static void enhanceIntensity(IplImage img, IplImage grey) {
		
		CvSize size = cvGetSize(grey);
		int width = size.width();
		int height = size.height();
		
		IplImage smaller = IplImage.create(10,  10,  grey.depth(), 1);
		cvResize(grey, smaller);
		cvSmooth(smaller, smaller, CV_GAUSSIAN, 7, 7, 6, 6);
//		cvSaveImage("smaller.png", smaller);
		
		IplImage small = IplImage.create(2,  2,  grey.depth(), 1);
		cvResize(smaller, small);
		smaller.release();
		double[] min_val = new double[1],
				 max_val = new double[1];
		cvMinMaxLoc(small, min_val, max_val);
		cvSubS(small, cvScalar(min_val[0]), small);
//		cvSaveImage("small.png", small);
		IplImage gradient = IplImage.create(size,  grey.depth(), 1);
		cvResize(small, gradient);
		cvSub(grey, gradient, grey);
		cvAddS(grey, cvScalar((max_val[0]-min_val[0])/2), grey);
		IplImage cgradient = IplImage.create(size, img.depth(), img.nChannels());
		cvCvtColor(gradient, cgradient, COLOR_GRAY2RGB);
//		cvSaveImage("gradient.png", cgradient);
		cvSub(img, cgradient, img);
		cvAddS(img, cvScalar((max_val[0]-min_val[0])/2), img);
		gradient.release();
		small.release();
		
		
		int dims = 1;
		int[] nbars = new int[]{256};
		float[] minMax = new float[]{0.0f, 255.0f};
		float[][] ranges = new float[][]{minMax};
		CvHistogram hist = cvCreateHist(dims, nbars, CV_HIST_ARRAY, ranges, CV_HIST_UNIFORM);
//		float[] min_val = new float[1],
//				max_val = new float[1];
//		cvSaveImage("hello.png", grey);
		cvCalcHist(grey, hist);

		
//		cvGetMinMaxHistValue(hist, min_val, max_val);
//		cvNormalizeHist(hist, );
	
		CvArr bins = hist.bins();
//		int aaa = bins.arrayDepth();
//		int bbb = bins.arrayChannels();
//		int ccc = bins.arrayWidth();
//		int ddd = bins.arrayHeight();
//		int eee = bins.);
//		ByteBuffer histb = hist.asByteBuffer();
//		DoubleBuffer histb = hist.
//		histb.rewind();
//		java.nio.Buffer b = hist.asBuffer();
		int minPos = nbars[0]-1, maxPos = 0, sumLength = 30;
		double maxVal = 0, t = 0;
		double lowTrashold = (double)(height * width) / 500;
		double[] vals = new double[nbars[0]];
		for(int i = 0; i < nbars[0]; i++){
//			t = histb.get();
			t = cvGet1D(bins, i).get(0);
//			cvGet1D(arr, 0);
//			t = histb.get();
//			t += 10000;
//			if(t < 0)
//				t+=256;
			vals[i] = t;
			if (t > lowTrashold && i < minPos)
				minPos = Math.min(Math.max(i, 10), 255);
			for(int j = Math.max(i-sumLength, 0); j < i; j++)
				t+=vals[i];
			if (t*i > maxVal){
				maxVal = t*i;
				maxPos = Math.max(i-sumLength, 1); //Math.min(i+sumLength, 255);
			}
		}
		int mindiff = 100;
		maxPos = Math.max(maxPos, mindiff+1);
		minPos = Math.min(maxPos-100, minPos);
		log.debug("Clamping image into range (" + minPos + ", " + maxPos + ")");
//		byte[] histvals = histb.array();
//		cvSubS(img, a, img);
//		cvSubS(grey, a, grey);
//		cvScale(arg1, arg2, arg3, arg4);
//		cvMinMaxLoc(grey, min_val, max_val);
		hist.release();
//		cvMinMaxLoc(grey, min_val, max_val);
//		cvInRangeS(src, lower, upper, dst);
		cvInRangeS(grey, CV_RGB(maxPos, maxPos, maxPos), CV_RGB(255,255,255), grey);
		cvCvtColor(grey, cgradient, COLOR_GRAY2RGB);
		cvAdd(img, cgradient, img);
		
		
//		threshold(img.asCvMat(), img.asCvMat(), 160, 255, CV_THRESH_TOZERO_INV);
		double scale = 255.0/(Math.min(maxPos+sumLength*2, 255)-minPos);
		double shift = -minPos*scale;
		cvConvertScale(img, img, scale, shift);
//		cvConvertScale(grey, grey, scale, shift);
//		cvSaveImage("grey.png", grey);
//		cvEqualizeHist(img, img);
//		System.out.println(histb.get());
		cgradient.release();
		
	}
}
